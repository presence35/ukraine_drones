package ua.ukrainedrones

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val neptunAlerts: List<OblastAlert> = emptyList(),
    val connected: Boolean = false,
    val lastError: String? = null,
    val offlineSince: Long? = null,
    /** Epoch millis when reconnection attempts started (after NEPTUN connection drop). */
    val reconnectStartMillis: Long = 0L,
    val lastFrameAt: Long = 0,     // epoch millis of the last frame (any type) from the stream
    val forceOffline: Boolean = false,   // TEMP test toggle — simulate NEPTUN being offline
    /** Epoch millis when each id was last shot down by the user (map long-press). */
    val userShotAt: Map<String, Long> = emptyMap()
) {
    /** Seconds since the stream dropped, or null while NEPTUN appears online. */
    val offlineElapsedSec: Long?
        get() = if (neptunDown) offlineSince?.let { (System.currentTimeMillis() - it) / 1000 }
            ?: (if (forceOffline) 0L else null)
        else null

    /**
     * NEPTUN is offline — the real socket dropped (`!connected`) or the TEMP [forceOffline]
     * test toggle simulates it. This drives the "offline" display.
     */
    val neptunDown: Boolean
        get() = forceOffline || !connected

    /**
     * The official oblast-alert list the UI/notifications read. When the socket is down the
     * last-known list is HELD rather than cleared: an outage must never look like "alert
     * ended" (no fabricated all-clear, no banner flicker) — the truth arrives on reconnect.
     */
    val oblastAlerts: List<OblastAlert>
        get() = neptunAlerts
}

/** A threat just disappeared from the server feed (resolved or a remove frame) — drives the map death animation. */
data class ThreatRemoved(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: ThreatType,
    val courseDeg: Double = 0.0,
    val region: String? = null,
    val district: String? = null,
    val locality: String? = null
)

object NeptunClient {

    /**
     * Shared "off" grace window: drops shorter than this are treated as blips and are invisible
     * everywhere (UI pill, connection log, offline notification). Anything longer is a real
     * outage — logged and alerted (the offline alert re-verifies the drop after the grace, so
     * a blip that recovers inside the window never fires).
     */
    const val OFFLINE_GRACE_MS = 5_000L

    /** How long a user-shot drone stays "remembered": a same-id respawn inside this window
     *  is the same drone coming back (no new alert); after it, a fresh appearance is a new threat. */
    const val USER_SHOT_GRACE_MS = 3_000L

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
    private val connectInFlight = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private var started = false

    private val _state = MutableStateFlow(NeptunState())
    val state: StateFlow<NeptunState> = _state.asStateFlow()

    private val _removedThreats = MutableSharedFlow<ThreatRemoved>(extraBufferCapacity = 16)
    val removedThreats: SharedFlow<ThreatRemoved> = _removedThreats.asSharedFlow()

    fun start() {
        if (started) {
            if (ws == null && !manuallyStopped) connect()
            return
        }
        started = true
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        manuallyStopped = false
        connectInFlight.set(false)
        connect()
        startKeepAliveTasks()
        startConnectionLog()
    }

    fun stop() {
        manuallyStopped = true
        started = false
        reconnectJob?.cancel()
        reconnectJob = null
        ws?.close(1000, "client stop")
        ws = null
        // The dying socket's guarded onClosed/onFailure won't clear the flag; leave it
        // fresh so a later start() can connect.
        connectInFlight.set(false)
        scope.cancel()
    }

    /**
     * TEMP test toggle: force the app to behave as if NEPTUN were offline. Updates the shared
     * can be verified. Updates the shared state so both the UI and AlertService re-derive it.
     */
    fun setForceOffline(force: Boolean) {
        _state.update {
            it.copy(
                forceOffline = force,
                // Mirror a real disconnect's offlineSince so the elapsed-time math (and thus the
                // offline notification/UI text) actually exercises a rising duration under the
                // test toggle instead of being pinned at 0. Only stamp it if not already set by a
                // real drop; only clear it on turn-off if the real socket is actually connected.
                offlineSince = when {
                    force && it.offlineSince == null -> System.currentTimeMillis()
                    !force && it.connected -> null
                    else -> it.offlineSince
                }
            )
        }
        // Turning the toggle back off while the socket is genuinely down must kick a real
        // reconnect — otherwise the toggle looks like it did nothing.
        if (!force && !_state.value.connected && !manuallyStopped) retryNow()
    }

    /** Serial number for the TEMP test MiG, so every fire is a fresh id (fresh siren + flyby). */
    private var testMigSerial = 0

    /** All currently-airborne test jets — several can be in the air at once. */
    private val testMigIds: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    /** How long an injected MiG stays live before it "flies on" and clears itself. */
    private const val TEST_MIG_LINGER_MS = 20_000L

    /**
     * Plausible MiG-31K launch-airbase pins — varied so every run crosses the screen on a
     * different bearing.
     */
    private val TEST_MIG_BASES = listOf(
        49.83 to 36.75, // Chuhuiv area
        46.05 to 38.35, // Primorsko-Akhtarsk area
        48.35 to 42.50, // Morozovsk area
        51.48 to 46.20  // Engels area
    )

    /**
     * TEMP test button: inject a MiG-31K takeoff alert as if NEPTUN itself had sent it. The
     * synthetic threat lives in the shared state, so the whole pipeline runs for real — zone
     * tiering, siren/notification, debug log, widget, map marker and the flyby flourish.
     *
     * One jet per press: a fresh id from a random launch base (so repeats are never deduped
     * as a known zone), then after [TEST_MIG_LINGER_MS] it "flies on" and silently leaves the
     * feed — no shoot-down explosion. Several jets may be airborne at once — each with its own
     * id, alert lifecycle and retire clock, like a real multi-takeoff wave. Session-only:
     * nothing is persisted. Invariant: only the WS `snapshot` handler replaces the threats map
     * wholesale, so that site re-merges every test threat; upsert/remove/REST all copy from
     * the live state and keep them naturally.
     */
    fun fireTestMig() {
        testMigSerial++
        val id = "test_mig31k_$testMigSerial"
        testMigIds.add(id)
        _state.update { withTestMig(it) }
        scope.launch {
            delay(TEST_MIG_LINGER_MS)
            if (testMigIds.remove(id)) {
                _state.update { s -> s.copy(threats = s.threats - id) }
            }
        }
    }

    /** Re-attach every airborne test threat to [state] (no-op when none are). */
    private fun withTestMig(state: NeptunState): NeptunState {
        if (testMigIds.isEmpty()) return state
        val now = System.currentTimeMillis()
        var threats = state.threats
        for (id in testMigIds) {
            val (lat, lon) = TEST_MIG_BASES.random()
            threats = threats + (id to buildTestMig(id, now, lat, lon))
        }
        return state.copy(threats = threats)
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
        // A 20-minute milestone may have stopped the reconnect loop while the app was closed;
        // resuming the socket on the next open matches the notification's promise.
        if (!manuallyStopped && !_state.value.connected) retryNow()
    }

    private val restUrl = "https://neptun.in.ua/api/v1/threats"

    /**
     * Force an immediate reconnect attempt, bypassing the backoff timer. Called by the
     * "Retry" action on the offline notification. Safe to call anytime: `connect()` is
     * guarded by `connectInFlight`, so it's a no-op while a connection is already in flight.
     */
    fun retryNow() {
        if (manuallyStopped) return
        reconnectJob?.cancel()
        reconnectJob = null
        // Drop any half-open socket so a fresh one is created, then connect right away.
        // Null the field BEFORE closing: the old socket's onClosed/onFailure must not clobber
        // the fresh connection connect() is about to create (see the ws-identity guards below).
        val old = ws
        ws = null
        // The superseded socket's guarded callbacks will NOT clear the in-flight flag (they
        // return early), so clear it here — otherwise connect() below would early-return and
        // the client would stay dead forever (only a process restart used to fix it).
        connectInFlight.set(false)
        old?.close(1001, "manual retry")
        connect()
    }

    /** Stop the periodic reconnect loop (fires at the 20-minute milestone). A later
     *  [retryNow] / [onForeground] call restarts it. */
    fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

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
                _state.update { it.copy(lastError = e.message) }
            }

            override fun onResponse(call: Call, response: Response) {
                restInFlight.set(false)
                response.use {
                    if (!it.isSuccessful) {
                        _state.update { s -> s.copy(lastError = "REST HTTP ${it.code}") }
                        return
                    }
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
                            if (existing == null || (t.updatedAtMillis ?: 0L) > (existing.updatedAtMillis ?: 0L)) {
                                merged[t.id] = t
                            }
                        }
                        _state.update { it.copy(threats = merged, lastError = null) }
                    } catch (_: Exception) {
                        // Malformed REST payload — keep current threats, but surface that a refresh failed.
                        _state.update { it.copy(lastError = "Malformed REST payload") }
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

    /** Feed the connection-status log: a coarse 5s tick that reports the current source state. */
    private fun startConnectionLog() {
        scope.launch {
            while (true) {
                delay(5_000)
                if (manuallyStopped) return@launch
                val st = _state.value
                val now = System.currentTimeMillis()
                val status = when {
                    st.neptunDown -> ConnStatus.OFFLINE
                    else -> ConnStatus.ONLINE
                }
                ConnectionLog.observe(status, now)
            }
        }
    }

    private fun connect() {
        if (!connectInFlight.compareAndSet(false, true)) return
        val request = Request.Builder()
            .url("wss://neptun.in.ua/api/v1/stream")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connectInFlight.set(false)
                if (manuallyStopped) {
                    ws = null
                    webSocket.close(1000, "stopped")
                    return
                }
                reconnectAttempt = 0
                openedAt = System.currentTimeMillis()
                lastFrameAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        connected = true, lastError = null, offlineSince = null,
                        lastFrameAt = System.currentTimeMillis(),
                        reconnectStartMillis = 0L
                    )
                }
                refreshFromRest()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastFrameAt = System.currentTimeMillis()
                _state.update { it.copy(lastFrameAt = System.currentTimeMillis()) }
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) {
                    // Superseded by a manual retry / stop. Without a successor socket the
                    // in-flight flag would stay stuck true forever — clear it so a future
                    // connect() can proceed.
                    if (ws == null) connectInFlight.set(false)
                    return
                }
                ws = null
                connectInFlight.set(false)
                _state.update { it.copy(connected = false, offlineSince = System.currentTimeMillis(), reconnectStartMillis = System.currentTimeMillis()) }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (ws !== webSocket) {
                    if (ws == null) connectInFlight.set(false)
                    return
                }
                connectInFlight.set(false)
                _state.update {
                    it.copy(
                        connected = false,
                        lastError = t.message,
                        offlineSince = System.currentTimeMillis(),
                        reconnectStartMillis = System.currentTimeMillis()
                    )
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyStopped) return
        // Mirror the website's reconnect logic: let a long-lived connection earn a backoff
        // reset, then retry — fast on the first attempt (this is an always-on safety app, not
        // a browser tab), with exponential backoff capped at 15s on repeated failures.
        if (openedAt > 0 && System.currentTimeMillis() - openedAt > 10_000) {
            reconnectAttempt = 0
            // reconnectSince = 0L  // no longer tracked here; AlertService uses NeptunState.reconnectStartMillis
        }

        reconnectAttempt++
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs(reconnectAttempt))
            if (!manuallyStopped) connect()
        }
    }

    /**
     * Randomized reconnect delay for the Nth attempt (1 = first attempt after a long-lived
     * connection). Pure so it can be unit-tested: first retry is quick (1-3s), later attempts
     * back off exponentially, capped at 15s with a small jitter.
     */
    internal fun reconnectDelayMs(attempt: Int): Long = when {
        attempt <= 1 -> 1000L + (0..2000).random()
        else -> minOf(15_000L, 1000L * (1 shl (attempt - 1))) + (0..400).random()
    }

    /** Record that the user shot down [id] on the map (long-press fake kill). */
    fun markUserShot(id: String) {
        if (id.isEmpty()) return
        val now = System.currentTimeMillis()
        _state.update { it.copy(userShotAt = it.userShotAt + (id to now)) }
    }

    /** Forget user-shot markers older than the grace window. */
    private fun pruneUserShot(now: Long) {
        val stale = _state.value.userShotAt.filterValues { now - it > USER_SHOT_GRACE_MS }
        if (stale.isNotEmpty()) _state.update { it.copy(userShotAt = it.userShotAt - stale.keys) }
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
                    // A user-shot drone the stream briefly loses is kept alive in memory for
                    // the shot's grace window — it redraws in place on respawn instead of being
                    // removed and re-added as a brand-new threat.
                    val now = System.currentTimeMillis()
                    val prev = _state.value.threats
                    val shot = _state.value.userShotAt
                    for (id in prev.keys) {
                        if (id in map) continue
                        val shotAt = shot[id] ?: continue
                        if (now - shotAt <= USER_SHOT_GRACE_MS) map[id] = prev.getValue(id)
                    }
                    pruneUserShot(now)
                    // Snapshot replaces the map wholesale — the only site that must re-merge
                    // the TEMP test threat; every other write copies the live state.
                    _state.update { withTestMig(it.copy(threats = map)) }
                }
                "upsert" -> {
                    val data = env.optJSONObject("data") ?: return
                    val t = Threat.fromJson(data) ?: return
                    val updated = _state.value.threats.toMutableMap()
                    if (t.status == "resolved") {
                        _removedThreats.tryEmit(
                            ThreatRemoved(t.id, t.lat, t.lon, t.type, t.courseDeg, t.region, t.district, t.locality)
                        )
                        updated.remove(t.id)
                    } else updated[t.id] = t
                    _state.update { it.copy(threats = updated) }
                }
                "remove" -> {
                    val data = env.optJSONObject("data") ?: return
                    val id = data.optString("id")
                    val updated = _state.value.threats.toMutableMap()
                    updated.remove(id)?.let { gone ->
                        _removedThreats.tryEmit(
                            ThreatRemoved(gone.id, gone.lat, gone.lon, gone.type, gone.courseDeg, gone.region, gone.district, gone.locality)
                        )
                    }
                    _state.update { it.copy(threats = updated) }
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
                    _state.update { it.copy(neptunAlerts = list) }
                }
                "heartbeat" -> { /* no-op, connection alive */ }
            }
        } catch (e: Exception) {
            // Malformed frame — ignore and keep listening, don't crash the stream.
        }
    }
}

/**
 * The synthetic MiG-31K takeoff injected by the TEMP [NeptunClient.setTestMig] toggle: a
 * static airbase-style pin (no velocity, like a real takeoff alert), active, not advisory.
 */
internal fun buildTestMig(id: String, now: Long, lat: Double, lon: Double): Threat {
    val nowIso = java.time.Instant.ofEpochMilli(now).toString()
    return Threat(
        id = id,
        type = ThreatType.AVIATION,
        title = "",
        region = null,
        district = null,
        locality = null,
        lat = lat,
        lon = lon,
        heading = null,
        bearingDeg = null,
        status = "active",
        advisory = false,
        areaOnly = false,
        confirmations = 2,
        reliability = Reliability.HIGH,
        count = 0,
        explanationShort = null,
        speedKmh = null,
        uncertaintyKm = null,
        positionQuality = "confirmed",
        confirmedAt = nowIso,
        confirmedAtMillis = now,
        updatedAt = nowIso,
        updatedAtMillis = now
    )
}
