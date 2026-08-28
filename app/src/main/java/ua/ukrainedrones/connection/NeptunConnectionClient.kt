package ua.ukrainedrones.connection

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import ua.ukrainedrones.OblastAlert
import ua.ukrainedrones.Threat
import ua.ukrainedrones.ThreatRemoved
import ua.ukrainedrones.ThreatType
import ua.ukrainedrones.buildTestMig
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-reliability WebSocket and REST client for the NEPTUN telemetry feed.
 *
 * Architecture Improvements:
 * 1. Strict generation-based WebSocket lifecycle ([connectionGeneration]) to eliminate socket identity races.
 * 2. Unambiguous [ConnectionState] state machine.
 * 3. Prevents watchdog pacification by never resetting [lastFrameAtMs] on UI foreground.
 * 4. Gated reconnect on validated network changes (via [NetworkMonitor]) preventing captive-portal hammer.
 * 5. Deterministic REST snapshot merge with `no-store` cache semantics.
 * 6. Isolated test harness seam for test MiG injection and offline simulation.
 */
class NeptunConnectionClient(
    private val context: Context,
    private val client: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val OFFLINE_GRACE_MS = 5_000L
        const val DEGRADED_STALE_MS = 30_000L
        const val WATCHDOG_STALE_MS = 45_000L
        const val REST_KEEP_ALIVE_STALE_MS = 15_000L
        const val USER_SHOT_GRACE_MS = 3_000L
        const val NO_NETWORK_RECONNECT_MS = 60_000L

        fun calculateBackoffMs(attempt: Int): Long = when {
            attempt <= 1 -> 1000L + (0..2000).random()
            else -> minOf(15_000L, 1000L * (1 shl (attempt - 1))) + (0..400).random()
        }

        private const val WS_URL = "wss://neptun.in.ua/api/v1/stream"
        private const val REST_URL = "https://neptun.in.ua/api/v1/threats"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkMonitor = NetworkMonitor(context)

    // Generation counter for all socket instances
    private val connectionGeneration = AtomicInteger(0)
    private var activeWebSocket: WebSocket? = null

    // State Flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _threats = MutableStateFlow<Map<String, Threat>>(emptyMap())
    val threats: StateFlow<Map<String, Threat>> = _threats.asStateFlow()

    private val _alerts = MutableStateFlow<List<OblastAlert>>(emptyList())
    val alerts: StateFlow<List<OblastAlert>> = _alerts.asStateFlow()

    private val _removedThreats = MutableSharedFlow<ThreatRemoved>(extraBufferCapacity = 16)
    val removedThreats: SharedFlow<ThreatRemoved> = _removedThreats.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Tracking
    private var openedAtMs = 0L
    @Volatile private var lastFrameAtMs = 0L
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var isManuallyStopped = false
    private val restInFlight = AtomicBoolean(false)

    // User-shot grace tracking
    private val userShotAt = ConcurrentHashMap<String, Long>()

    // Outage and pause persistence
    private var persistedReconnectStartMs = 0L
    private var ignoreUntilMs = 0L

    // Test Seam
    val testHarness = TestHarnessImpl()

    fun start(savedReconnectStartMs: Long = 0L, savedIgnoreUntilMs: Long = 0L) {
        if (!scope.isActive) return
        isManuallyStopped = false
        persistedReconnectStartMs = savedReconnectStartMs
        ignoreUntilMs = savedIgnoreUntilMs

        networkMonitor.start(onValidatedReturn = {
            if (!isManuallyStopped && _connectionState.value.isOffline && !isIgnoringPause()) {
                retryNow()
            }
        })

        startKeepAliveAndWatchdog()

        if (isIgnoringPause()) {
            _connectionState.value = ConnectionState.Paused(
                untilMs = ignoreUntilMs,
                since = System.currentTimeMillis(),
                reconnectStartMillis = persistedReconnectStartMs
            )
            schedulePauseExpiry()
        } else {
            connect()
        }
    }

    fun stop() {
        isManuallyStopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        val gen = connectionGeneration.incrementAndGet()
        activeWebSocket?.close(1000, "client stop")
        activeWebSocket = null
        networkMonitor.stop()
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    fun retryNow() {
        if (isManuallyStopped) return
        if (isIgnoringPause()) {
            ignoreUntilMs = 0L
        }
        reconnectJob?.cancel()
        reconnectJob = null

        val oldSocket = activeWebSocket
        activeWebSocket = null
        oldSocket?.close(1001, "manual retry")

        connect()
    }

    fun pauseFor(minutes: Int) {
        ignoreUntilMs = System.currentTimeMillis() + minutes * 60_000L
        reconnectJob?.cancel()
        val since = _connectionState.value.offlineSinceOrNull ?: System.currentTimeMillis()
        val recStart = _connectionState.value.reconnectStartMillisOrZero.takeIf { it > 0L } ?: since

        _connectionState.value = ConnectionState.Paused(
            untilMs = ignoreUntilMs,
            since = since,
            reconnectStartMillis = recStart
        )
        schedulePauseExpiry()
    }

    fun isIgnoringPause(): Boolean = ignoreUntilMs > System.currentTimeMillis()

    /**
     * UI foreground hook: Only checks freshness and triggers REST/reconnect.
     * CRITICAL FIX: Does NOT overwrite [lastFrameAtMs] with current time, preventing watchdog defeat.
     */
    fun onForeground() {
        val now = System.currentTimeMillis()
        val isStale = lastFrameAtMs > 0L && (now - lastFrameAtMs > 5_000L)
        if (isStale && _connectionState.value.isConnected) {
            refreshFromRest()
        }
        if (!isManuallyStopped && _connectionState.value.isOffline && !isIgnoringPause()) {
            retryNow()
        }
    }

    fun markUserShot(id: String) {
        if (id.isNotBlank()) {
            userShotAt[id] = System.currentTimeMillis()
        }
    }

    fun wasUserShotRecently(id: String): Boolean {
        val shotAt = userShotAt[id] ?: return false
        return System.currentTimeMillis() - shotAt <= USER_SHOT_GRACE_MS
    }

    private fun connect() {
        if (isManuallyStopped) return
        val gen = connectionGeneration.incrementAndGet()

        val isNetValidated = networkMonitor.isValidated.value
        val nextRetryAt = System.currentTimeMillis()
        _connectionState.value = ConnectionState.Connecting(
            generation = gen,
            attempt = reconnectAttempt,
            nextRetryAtMs = nextRetryAt,
            networkValidated = isNetValidated
        )

        val request = Request.Builder().url(WS_URL).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (connectionGeneration.get() != gen || isManuallyStopped) {
                    webSocket.close(1000, "superseded")
                    return
                }

                val now = System.currentTimeMillis()
                reconnectAttempt = 0
                openedAtMs = now
                lastFrameAtMs = now
                persistedReconnectStartMs = 0L

                _lastError.value = null
                _connectionState.value = ConnectionState.Connected(
                    generation = gen,
                    openedAtMs = now,
                    lastFrameAtMs = now
                )

                refreshFromRest()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (connectionGeneration.get() != gen) return
                val now = System.currentTimeMillis()
                lastFrameAtMs = now
                if (testHarness.shouldDropFrame()) return
                if (_connectionState.value is ConnectionState.Degraded) {
                    _connectionState.value = ConnectionState.Connected(
                        generation = gen,
                        openedAtMs = openedAtMs,
                        lastFrameAtMs = now
                    )
                }
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (connectionGeneration.get() != gen) return
                handleDisconnect(gen, reason = reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (connectionGeneration.get() != gen) return
                _lastError.value = t.message
                handleDisconnect(gen, reason = t.message)
            }
        })

        activeWebSocket = ws
    }

    private fun handleDisconnect(gen: Int, reason: String?) {
        if (isManuallyStopped || connectionGeneration.get() != gen) return

        val now = System.currentTimeMillis()
        val previousState = _connectionState.value
        val offlineSince = previousState.offlineSinceOrNull ?: now
        val recStart = when {
            persistedReconnectStartMs > 0L -> persistedReconnectStartMs
            previousState.reconnectStartMillisOrZero > 0L -> previousState.reconnectStartMillisOrZero
            else -> now
        }
        persistedReconnectStartMs = recStart

        _connectionState.value = ConnectionState.Offline(
            since = offlineSince,
            reconnectStartMillis = recStart,
            reason = reason,
            attempt = reconnectAttempt
        )

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isManuallyStopped || isIgnoringPause()) return

        if (openedAtMs > 0L && System.currentTimeMillis() - openedAtMs > 10_000L) {
            reconnectAttempt = 0
        }
        reconnectAttempt++

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val isNetValidated = networkMonitor.isValidated.value
            val delayMs = if (isNetValidated) calculateBackoffMs(reconnectAttempt) else NO_NETWORK_RECONNECT_MS

            delay(delayMs)
            if (!isManuallyStopped && !isIgnoringPause()) {
                connect()
            }
        }
    }

    private fun schedulePauseExpiry() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val remaining = ignoreUntilMs - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            if (!isManuallyStopped) {
                reconnectAttempt = 0
                connect()
            }
        }
    }


    fun refreshFromRest() {
        if (isManuallyStopped || !restInFlight.compareAndSet(false, true)) return
        val request = Request.Builder()
            .url(REST_URL)
            .header("Cache-Control", "no-store")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                restInFlight.set(false)
                _lastError.value = e.message
            }

            override fun onResponse(call: Call, response: Response) {
                restInFlight.set(false)
                response.use {
                    if (!it.isSuccessful) {
                        _lastError.value = "REST HTTP ${it.code}"
                        return
                    }
                    val body = it.body?.string() ?: return
                    parseRestPayload(body)
                }
            }
        })
    }

    private fun parseRestPayload(body: String) {
        try {
            val env = JSONObject(body)
            val arr = env.optJSONArray("threats") ?: return
            val currentMap = _threats.value
            val merged = LinkedHashMap<String, Threat>(currentMap)

            for (i in 0 until arr.length()) {
                val t = Threat.fromJson(arr.getJSONObject(i)) ?: continue
                val existing = merged[t.id]
                if (existing == null || (t.updatedAtMillis ?: 0L) >= (existing.updatedAtMillis ?: 0L)) {
                    merged[t.id] = t
                }
            }
            _threats.value = testHarness.mergeTestThreats(merged)
            _lastError.value = null
        } catch (_: Exception) {
            _lastError.value = "Malformed REST payload"
        }
    }

    private fun startKeepAliveAndWatchdog() {
        // Keep-Alive REST check for quiet links
        scope.launch {
            while (isActive) {
                delay(20_000)
                if (isManuallyStopped) return@launch
                val quietFor = System.currentTimeMillis() - lastFrameAtMs
                if (_connectionState.value.isConnected && quietFor > REST_KEEP_ALIVE_STALE_MS) {
                    refreshFromRest()
                }
            }
        }

        // Watchdog & Degraded State transition check
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (isManuallyStopped) return@launch
                val now = System.currentTimeMillis()
                val quietFor = now - lastFrameAtMs

                val currentState = _connectionState.value
                if (currentState is ConnectionState.Connected && lastFrameAtMs > 0L && quietFor >= DEGRADED_STALE_MS) {
                    _connectionState.value = ConnectionState.Degraded(
                        generation = currentState.generation,
                        openedAtMs = currentState.openedAtMs,
                        lastFrameAtMs = lastFrameAtMs,
                        quietDurationMs = quietFor
                    )
                }

                if (currentState.isConnected && lastFrameAtMs > 0L && quietFor > WATCHDOG_STALE_MS) {
                    // Trigger watchdog reconnect
                    activeWebSocket?.close(1001, "watchdog stale")
                }
            }
        }
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
                    val now = System.currentTimeMillis()
                    val prev = _threats.value
                    // Preserve user-shot drones within grace window
                    for (id in prev.keys) {
                        if (id in map) continue
                        val shotAt = userShotAt[id] ?: continue
                        if (now - shotAt <= USER_SHOT_GRACE_MS) {
                            map[id] = prev.getValue(id)
                        }
                    }
                    userShotAt.entries.removeIf { now - it.value > USER_SHOT_GRACE_MS }
                    _threats.value = testHarness.mergeTestThreats(map)
                }
                "upsert" -> {
                    val data = env.optJSONObject("data") ?: return
                    val t = Threat.fromJson(data) ?: return
                    val updated = _threats.value.toMutableMap()
                    if (t.status == "resolved") {
                        _removedThreats.tryEmit(
                            ThreatRemoved(t.id, t.lat, t.lon, t.type, t.courseDeg, t.region, t.district, t.locality)
                        )
                        updated.remove(t.id)
                    } else {
                        updated[t.id] = t
                    }
                    _threats.value = updated
                }
                "remove" -> {
                    val data = env.optJSONObject("data") ?: return
                    val id = data.optString("id")
                    val updated = _threats.value.toMutableMap()
                    updated.remove(id)?.let { gone ->
                        _removedThreats.tryEmit(
                            ThreatRemoved(gone.id, gone.lat, gone.lon, gone.type, gone.courseDeg, gone.region, gone.district, gone.locality)
                        )
                    }
                    _threats.value = updated
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
                    _alerts.value = list
                }
                "heartbeat" -> { /* live keep-alive */ }
            }
        } catch (_: Exception) {
            // Malformed frame ignored
        }
    }

    // =========================================================================
    // Isolated Test Harness implementation
    // =========================================================================
    inner class TestHarnessImpl {
        private var testMigSerial = 0
        private val testMigIds = ConcurrentHashMap.newKeySet<String>()
        private val TEST_MIG_LINGER_MS = 20_000L
        private val TEST_MIG_BASES = listOf(
            49.83 to 36.75, // Chuhuiv area
            46.05 to 38.35, // Primorsko-Akhtarsk area
            48.35 to 42.50, // Morozovsk area
            51.48 to 46.20  // Engels area
        )

        @Volatile var suppressFrames = false
        @Volatile var frameDropPercent = 0

        fun setForceOffline(force: Boolean) {
            if (force) {
                val now = System.currentTimeMillis()
                _connectionState.value = ConnectionState.Offline(
                    since = now,
                    reconnectStartMillis = now,
                    reason = "Test Force Offline",
                    isForceOffline = true
                )
            } else {
                if (_connectionState.value is ConnectionState.Offline) {
                    retryNow()
                }
            }
        }

        fun setNetworkValidated(validated: Boolean) {
            networkMonitor.setTestValidated(validated)
        }

        fun pauseFor(seconds: Int) {
            ignoreUntilMs = System.currentTimeMillis() + seconds * 1000L
            reconnectJob?.cancel()
            reconnectJob = null
            _connectionState.value = ConnectionState.Paused(
                untilMs = ignoreUntilMs,
                since = System.currentTimeMillis(),
                reconnectStartMillis = persistedReconnectStartMs
            )
        }

        fun reset() {
            suppressFrames = false
            frameDropPercent = 0
            ignoreUntilMs = 0L
            networkMonitor.setTestValidated(true)
            if (_connectionState.value !is ConnectionState.Connected) {
                retryNow()
            }
        }

        fun shouldDropFrame(): Boolean {
            if (suppressFrames) return true
            if (frameDropPercent > 0) return (0..99).random() < frameDropPercent
            return false
        }

        fun fireTestMig() {
            testMigSerial++
            val id = "test_mig31k_$testMigSerial"
            testMigIds.add(id)
            _threats.update { mergeTestThreats(it) }

            scope.launch {
                delay(TEST_MIG_LINGER_MS)
                if (testMigIds.remove(id)) {
                    _threats.update { it - id }
                }
            }
        }

        internal fun mergeTestThreats(baseMap: Map<String, Threat>): Map<String, Threat> {
            if (testMigIds.isEmpty()) return baseMap
            val now = System.currentTimeMillis()
            val merged = HashMap(baseMap)
            for (id in testMigIds) {
                val (lat, lon) = TEST_MIG_BASES.random()
                merged[id] = buildTestMig(id, now, lat, lon)
            }
            return merged
        }
    }
}
