package ua.odesa.drones

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class NeptunState(
    val threats: Map<String, Threat> = emptyMap(),
    val oblastAlerts: List<OblastAlert> = emptyList(),
    val connected: Boolean = false,
    val lastError: String? = null
)

object NeptunClient {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // keep socket open indefinitely
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restInFlight = AtomicBoolean(false)

    private var ws: WebSocket? = null
    private var manuallyStopped = false
    private var reconnectAttempt = 0
    private var lastFrameAt = 0L
    private var openedAt = 0L

    private val _state = MutableStateFlow(NeptunState())
    val state: StateFlow<NeptunState> = _state.asStateFlow()

    fun start() {
        if (ws != null) return
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        manuallyStopped = false
        connect()
        startKeepAliveTasks()
    }

    fun stop() {
        manuallyStopped = true
        ws?.close(1000, "client stop")
        ws = null
        scope.cancel()
    }

    /**
     * Called when the UI returns to the foreground. Only pulls from REST when the WebSocket
     * stream itself has gone quiet (>5s since its last frame): the REST snapshot is CDN-cached
     * for a few seconds, so refreshing over a live stream would only regress fresher positions.
     * Resetting the stale window here also stops the keep-alive task from double-refreshing.
     */
    fun onForeground() {
        val now = System.currentTimeMillis()
        val wsStale = now - lastFrameAt > 5_000
        lastFrameAt = now
        if (wsStale) refreshFromRest()
    }

    private val restUrl = "https://neptun.in.ua/api/v1/threats"

    /**
     * Pull the server's current threat list over REST (cache: no-store), mirroring what the
     * website does on connect/reconnect/focus. Feeds the exact same snapshot path as a "snapshot"
     * frame so the map shows fresh coordinates even when the WebSocket telemetry is coarse.
     */
    fun refreshFromRest() {
        if (manuallyStopped || !restInFlight.compareAndSet(false, true)) return
        val request = Request.Builder()
            .url(restUrl)
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                restInFlight.set(false)
            }

            override fun onResponse(call: Call, response: Response) {
                restInFlight.set(false)
                response.use {
                    if (!it.isSuccessful) return
                    val body = it.body?.string() ?: return
                    try {
                        val env = JSONObject(body)
                        val arr = env.optJSONArray("threats") ?: return
                        // Merge, never replace: the REST snapshot is CDN-cached for a few seconds
                        // and can be older than what the live WebSocket already delivered. Keep
                        // the newer record per threat id and keep threats the stream still knows.
                        val merged = LinkedHashMap<String, Threat>(_state.value.threats)
                        for (i in 0 until arr.length()) {
                            val t = Threat.fromJson(arr.getJSONObject(i)) ?: continue
                            val existing = merged[t.id]
                            if (existing == null || (t.updatedAtMillis ?: 0L) >= (existing.updatedAtMillis ?: 0L)) {
                                merged[t.id] = t
                            }
                        }
                        _state.value = _state.value.copy(threats = merged)
                    } catch (_: Exception) {
                        // Malformed REST payload — keep current state.
                    }
                }
            }
        })
    }

    private fun startKeepAliveTasks() {
        scope.launch {
            while (true) {
                delay(20_000)
                if (manuallyStopped) return@launch
                // Safety-net REST refresh while connected (mirrors the site's cadence).
                // Skip it when the socket has been pushing frames — the stream is already fresh.
                if (System.currentTimeMillis() - lastFrameAt > 15_000) {
                    refreshFromRest()
                }
            }
        }
        scope.launch {
            while (true) {
                delay(15_000)
                if (manuallyStopped) return@launch
                // Watchdog: a silently-stale socket must not stay that way — force a reconnect.
                val socket = ws
                if (socket != null && lastFrameAt > 0 && System.currentTimeMillis() - lastFrameAt > 45_000) {
                    lastFrameAt = System.currentTimeMillis()
                    socket.close(1001, "watchdog stale")
                }
            }
        }
    }

    private fun connect() {
        val request = Request.Builder()
            .url("wss://neptun.in.ua/api/v1/stream")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                openedAt = System.currentTimeMillis()
                lastFrameAt = System.currentTimeMillis()
                _state.value = _state.value.copy(connected = true, lastError = null)
                refreshFromRest()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastFrameAt = System.currentTimeMillis()
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = _state.value.copy(connected = false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = _state.value.copy(connected = false, lastError = t.message)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyStopped) return
        // Mirror the website's reconnect logic: let a long-lived connection earn a backoff
        // reset, then pick a randomized first retry and exponential backoff capped at 15s.
        if (openedAt > 0 && System.currentTimeMillis() - openedAt > 10_000) {
            reconnectAttempt = 0
        }
        reconnectAttempt++
        val delayMs =
            if (reconnectAttempt <= 1) {
                5_000L + (0..25_000).random()
            } else {
                minOf(15_000L, 1000L * (1 shl (reconnectAttempt - 1))) + (0..400).random()
            }
        Thread {
            Thread.sleep(delayMs)
            if (!manuallyStopped) connect()
        }.start()
    }

    private fun handleFrame(text: String) {
        try {
            val env = JSONObject(text)
            when (env.optString("type")) {
                "snapshot" -> {
                    val data = env.optJSONObject("data") ?: return
                    val arr = data.optJSONArray("threats") ?: return
                    val map = LinkedHashMap<String, Threat>()
                    for (i in 0 until arr.length()) {
                        val t = Threat.fromJson(arr.getJSONObject(i)) ?: continue
                        map[t.id] = t
                    }
                    _state.value = _state.value.copy(threats = map)
                }
                "upsert" -> {
                    val data = env.optJSONObject("data") ?: return
                    val t = Threat.fromJson(data) ?: return
                    val updated = _state.value.threats.toMutableMap()
                    if (t.status == "resolved") updated.remove(t.id) else updated[t.id] = t
                    _state.value = _state.value.copy(threats = updated)
                }
                "remove" -> {
                    val data = env.optJSONObject("data") ?: return
                    val id = data.optString("id")
                    val updated = _state.value.threats.toMutableMap()
                    updated.remove(id)
                    _state.value = _state.value.copy(threats = updated)
                }
                "alerts" -> {
                    val data = env.optJSONObject("data") ?: return
                    val list = mutableListOf<OblastAlert>()
                    for (arrName in listOf("raions", "oblasts")) {
                        val arr = data.optJSONArray(arrName) ?: continue
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                OblastAlert(
                                    key = o.optString("key"),
                                    name = o.optString("name"),
                                    oblast = o.optString("oblast"),
                                    since = o.optString("since", null)
                                )
                            )
                        }
                    }
                    _state.value = _state.value.copy(oblastAlerts = list)
                }
                "heartbeat" -> { /* no-op, connection alive */ }
            }
        } catch (e: Exception) {
            // Malformed frame — ignore and keep listening, don't crash the stream.
        }
    }
}
