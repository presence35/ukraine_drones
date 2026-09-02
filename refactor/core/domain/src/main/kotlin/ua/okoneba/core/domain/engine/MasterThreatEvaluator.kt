package ua.okoneba.core.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.SystemHealthState

/**
 * Health information for an individual telemetry feed.
 */
data class FeedHealthInfo(
    val sourceId: String,
    val priority: Int, // Lower number = higher priority (e.g. 0 for NEPTUN, 1 for Backup)
    val isConnected: Boolean,
    val lastSuccessfulPacketTime: Long,
    val sourceStalenessMs: Long? = null,
    val consecutiveFailureCount: Int = 0
) {
    val isUsable: Boolean
        get() = isConnected && consecutiveFailureCount < MAX_CONSECUTIVE_FAILURES

    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}

/**
 * Snapshot state of an individual feed.
 */
data class FeedSnapshot(
    val sourceId: String,
    val threats: List<NormalizedThreat>,
    val receivedAt: Long,
    val isMalformed: Boolean = false
)

/**
 * Evaluated authoritative state produced by MasterThreatEvaluator.
 */
data class AuthoritativeThreatState(
    val authoritativeSourceId: String?,
    val threats: List<NormalizedThreat>,
    val systemHealth: SystemHealthState,
    val sourceFreshnessMs: Long?,
    val isRetainedSnapshot: Boolean,
    val lastUpdateTimestamp: Long
)

/**
 * MasterThreatEvaluator coordinates independent telemetry feeds, selects exactly ONE
 * authoritative source based on health, handles failover without cross-feed merging,
 * enforces retention safety limits, and ensures NO FEED is never conflated with NO THREATS.
 */
class MasterThreatEvaluator(
    private val maxRetentionMs: Long = DEFAULT_MAX_RETENTION_MS
) {
    companion object {
        /**
         * Local maximum retention safety limit.
         * Retained telemetry older than this duration without feed confirmation
         * transitions system to DEGRADED_NO_FEEDS and invalidates stale data.
         * Default: 120 seconds (2 minutes).
         */
        const val DEFAULT_MAX_RETENTION_MS = 120_000L
    }

    private val feedHealthMap = mutableMapOf<String, FeedHealthInfo>()
    private val feedLatestSnapshots = mutableMapOf<String, FeedSnapshot>()

    // Retained last valid authoritative snapshot
    private var lastAuthoritativeSnapshot: FeedSnapshot? = null

    private val _authoritativeState = MutableStateFlow(
        AuthoritativeThreatState(
            authoritativeSourceId = null,
            threats = emptyList(),
            systemHealth = SystemHealthState.DEGRADED_NO_FEEDS,
            sourceFreshnessMs = null,
            isRetainedSnapshot = false,
            lastUpdateTimestamp = 0L
        )
    )
    val authoritativeState: StateFlow<AuthoritativeThreatState> = _authoritativeState.asStateFlow()

    /**
     * Updates health status for a specific feed and triggers source evaluation.
     */
    @Synchronized
    fun updateFeedHealth(health: FeedHealthInfo, currentTimeMs: Long = System.currentTimeMillis()) {
        feedHealthMap[health.sourceId] = health
        reevaluateAuthoritativeSource(currentTimeMs)
    }

    /**
     * Ingests a new snapshot from a specific feed.
     * If the packet is malformed, it is rejected, logged via callback/caller,
     * and the previous valid snapshot for this feed is retained without clearing.
     */
    @Synchronized
    fun ingestFeedSnapshot(
        snapshot: FeedSnapshot,
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        if (snapshot.isMalformed) {
            // Malformed packet: do NOT update or clear the valid snapshot for this source
            val existingHealth = feedHealthMap[snapshot.sourceId]
            if (existingHealth != null) {
                feedHealthMap[snapshot.sourceId] = existingHealth.copy(
                    consecutiveFailureCount = existingHealth.consecutiveFailureCount + 1
                )
            }
            reevaluateAuthoritativeSource(currentTimeMs)
            return
        }

        // Valid packet: store snapshot and reset failure count
        feedLatestSnapshots[snapshot.sourceId] = snapshot
        val existingHealth = feedHealthMap[snapshot.sourceId]
        if (existingHealth != null) {
            feedHealthMap[snapshot.sourceId] = existingHealth.copy(
                isConnected = true,
                lastSuccessfulPacketTime = currentTimeMs,
                consecutiveFailureCount = 0
            )
        }

        reevaluateAuthoritativeSource(currentTimeMs)
    }

    /**
     * Selects the single best authoritative source and computes the authoritative threat state.
     */
    @Synchronized
    fun reevaluateAuthoritativeSource(currentTimeMs: Long = System.currentTimeMillis()) {
        // Find the healthiest usable feed sorted by priority (lowest integer first)
        val selectedFeedHealth = feedHealthMap.values
            .filter { it.isUsable }
            .minWithOrNull(compareBy({ it.priority }, { -it.lastSuccessfulPacketTime }))

        if (selectedFeedHealth != null) {
            val snapshot = feedLatestSnapshots[selectedFeedHealth.sourceId]
            if (snapshot != null) {
                // We have a healthy active authoritative source with valid data
                lastAuthoritativeSnapshot = snapshot
                val freshness = currentTimeMs - snapshot.receivedAt

                _authoritativeState.value = AuthoritativeThreatState(
                    authoritativeSourceId = selectedFeedHealth.sourceId,
                    threats = snapshot.threats,
                    systemHealth = SystemHealthState.HEALTHY,
                    sourceFreshnessMs = freshness,
                    isRetainedSnapshot = false,
                    lastUpdateTimestamp = currentTimeMs
                )
                return
            }
        }

        // If no healthy feed is currently active, inspect retained authoritative snapshot
        val retained = lastAuthoritativeSnapshot
        if (retained != null) {
            val ageMs = currentTimeMs - retained.receivedAt
            if (ageMs <= maxRetentionMs) {
                // Telemetry is retained and still within the maximum retention safety limit
                _authoritativeState.value = AuthoritativeThreatState(
                    authoritativeSourceId = retained.sourceId,
                    threats = retained.threats,
                    systemHealth = SystemHealthState.DEGRADED,
                    sourceFreshnessMs = ageMs,
                    isRetainedSnapshot = true,
                    lastUpdateTimestamp = currentTimeMs
                )
                return
            }
        }

        // All feeds down and retained data expired or non-existent
        // Invariant: NO FEED is NEVER represented as NO THREATS (empty list under HEALTHY)
        _authoritativeState.value = AuthoritativeThreatState(
            authoritativeSourceId = null,
            threats = emptyList(),
            systemHealth = SystemHealthState.DEGRADED_NO_FEEDS,
            sourceFreshnessMs = null,
            isRetainedSnapshot = false,
            lastUpdateTimestamp = currentTimeMs
        )
    }
}
