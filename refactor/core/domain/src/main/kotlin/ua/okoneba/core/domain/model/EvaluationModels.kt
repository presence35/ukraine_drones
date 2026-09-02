package ua.okoneba.core.domain.model

enum class AlertTier(val severityRank: Int) {
    OUTSIDE(0),
    YELLOW(1),
    RED(2);

    val isAlert: Boolean
        get() = this != OUTSIDE
}

enum class AlertDeduplicationPolicy {
    ONCE_PER_THREAT,
    EVERY_ZONE_ENTRY
}

enum class SystemHealthState {
    HEALTHY,
    DEGRADED,
    DEGRADED_NO_FEEDS
}

data class ZoneConfiguration(
    val redRadiusKm: Double = DEFAULT_RED_RADIUS_KM,
    val yellowRadiusKm: Double = DEFAULT_YELLOW_RADIUS_KM
) {
    init {
        require(redRadiusKm in MIN_RED_RADIUS_KM..MAX_RED_RADIUS_KM) {
            "redRadiusKm must be between $MIN_RED_RADIUS_KM km and $MAX_RED_RADIUS_KM km (got $redRadiusKm)"
        }
        require(yellowRadiusKm >= redRadiusKm + MIN_YELLOW_EXTRA_KM) {
            "yellowRadiusKm ($yellowRadiusKm) must be at least redRadiusKm + $MIN_YELLOW_EXTRA_KM km (${redRadiusKm + MIN_YELLOW_EXTRA_KM})"
        }
        require(yellowRadiusKm <= MAX_YELLOW_RADIUS_KM) {
            "yellowRadiusKm must be <= $MAX_YELLOW_RADIUS_KM km (got $yellowRadiusKm)"
        }
    }

    companion object {
        const val MIN_RED_RADIUS_KM = 2.0
        const val MAX_RED_RADIUS_KM = 20.0
        const val MIN_YELLOW_EXTRA_KM = 2.0
        const val MAX_YELLOW_RADIUS_KM = 50.0

        const val DEFAULT_RED_RADIUS_KM = 5.0
        const val DEFAULT_YELLOW_RADIUS_KM = 25.0

        fun safeCreate(redKm: Double, yellowKm: Double): ZoneConfiguration {
            val clampedRed = redKm.coerceIn(MIN_RED_RADIUS_KM, MAX_RED_RADIUS_KM)
            val minYellow = clampedRed + MIN_YELLOW_EXTRA_KM
            val clampedYellow = yellowKm.coerceIn(minYellow, MAX_YELLOW_RADIUS_KM)
            return ZoneConfiguration(
                redRadiusKm = clampedRed,
                yellowRadiusKm = clampedYellow
            )
        }
    }
}

data class EvaluatedThreat(
    val threat: NormalizedThreat,
    val targetId: String,
    val distanceKm: Double,
    val tier: AlertTier,
    val evaluatedAt: Long
)

sealed interface AlertEvent {
    val sourceId: String
    val threatId: String
    val targetId: String
    val tier: AlertTier
    val distanceKm: Double
    val timestamp: Long
    val threat: NormalizedThreat

    data class ThreatEnteredZone(
        override val sourceId: String,
        override val threatId: String,
        override val targetId: String,
        override val tier: AlertTier,
        override val distanceKm: Double,
        override val timestamp: Long,
        override val threat: NormalizedThreat
    ) : AlertEvent

    data class ThreatEscalated(
        override val sourceId: String,
        override val threatId: String,
        override val targetId: String,
        val previousTier: AlertTier,
        override val tier: AlertTier,
        override val distanceKm: Double,
        override val timestamp: Long,
        override val threat: NormalizedThreat
    ) : AlertEvent
}

data class OkoNebaSystemState(
    val health: SystemHealthState,
    val authoritativeSourceId: String?,
    val sourceFreshnessMs: Long?,
    val threatSnapshot: List<NormalizedThreat>,
    val monitoredTargets: List<MonitoredTarget>,
    val zoneConfiguration: ZoneConfiguration,
    val followMeLocationState: UserLocationState,
    val activeEvaluations: List<EvaluatedThreat>,
    val activeEpisodesCount: Int,
    val lastEvaluationTimestamp: Long
)
