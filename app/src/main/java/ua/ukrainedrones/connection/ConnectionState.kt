package ua.ukrainedrones.connection

/**
 * Formal Connection State Machine for the NEPTUN WebSocket telemetry feed.
 *
 * Replaces the fragmented boolean flags (connected, forceOffline, degraded,
 * connectInFlight, reconnectAttempt, etc.) with a single, unambiguous state.
 */
sealed interface ConnectionState {

    /** Default initial state before client is started or after explicitly stopped. */
    object Disconnected : ConnectionState

    /** Active WebSocket connection attempt in flight. */
    data class Connecting(
        val generation: Int,
        val attempt: Int,
        val nextRetryAtMs: Long,
        val networkValidated: Boolean
    ) : ConnectionState

    /** Connected and receiving live telemetry frames normally. */
    data class Connected(
        val generation: Int,
        val openedAtMs: Long,
        val lastFrameAtMs: Long
    ) : ConnectionState

    /**
     * Connected (socket is open), but no frame has arrived for >= 30 seconds.
     * Indicates a degraded link / silent stall before the 45s hard watchdog drops it.
     */
    data class Degraded(
        val generation: Int,
        val openedAtMs: Long,
        val lastFrameAtMs: Long,
        val quietDurationMs: Long
    ) : ConnectionState

    /**
     * Socket is disconnected or unavailable.
     *
     * @property since Epoch millis when this offline episode began.
     * @property reconnectStartMillis Epoch millis when reconnection began (persisted across retries & process restarts).
     * @property reason Human-readable or error message describing the drop.
     * @property attempt Current retry count in the active backoff sequence.
     */
    data class Offline(
        val since: Long,
        val reconnectStartMillis: Long,
        val reason: String? = null,
        val attempt: Int = 0,
        val isForceOffline: Boolean = false
    ) : ConnectionState

    /**
     * User instructed the app to pause retries (e.g., "Ignore for 30 min").
     * Survives process death and only resumes when the timestamp expires or on manual retry.
     */
    data class Paused(
        val untilMs: Long,
        val since: Long,
        val reconnectStartMillis: Long
    ) : ConnectionState
}

/** Convenience extensions to simplify UI and service queries. */
val ConnectionState.isConnected: Boolean
    get() = this is ConnectionState.Connected || this is ConnectionState.Degraded

val ConnectionState.isDegraded: Boolean
    get() = this is ConnectionState.Degraded

val ConnectionState.isOffline: Boolean
    get() = this is ConnectionState.Offline || this is ConnectionState.Paused || this is ConnectionState.Disconnected

val ConnectionState.isPaused: Boolean
    get() = this is ConnectionState.Paused

val ConnectionState.isForceOffline: Boolean
    get() = (this as? ConnectionState.Offline)?.isForceOffline == true

val ConnectionState.offlineSinceOrNull: Long?
    get() = when (this) {
        is ConnectionState.Offline -> since
        is ConnectionState.Paused -> since
        else -> null
    }

val ConnectionState.reconnectStartMillisOrZero: Long
    get() = when (this) {
        is ConnectionState.Offline -> reconnectStartMillis
        is ConnectionState.Paused -> reconnectStartMillis
        else -> 0L
    }
