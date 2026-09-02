package ua.okoneba.core.domain.engine

import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.AlertEvent
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.Coordinates
import ua.okoneba.core.domain.model.EvaluatedThreat
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.ZoneConfiguration
import ua.okoneba.core.domain.repository.EpisodeLedgerRepository
import ua.okoneba.core.domain.repository.EpisodeRecord
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * Calculates great-circle distance between two points on the Earth using Haversine formula.
     * Returns distance in kilometers.
     */
    fun haversineDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}

class ZoneEvaluationEngine {

    /**
     * Pure evaluation of a single threat against a target coordinate.
     */
    fun evaluateDistanceAndTier(
        threat: NormalizedThreat,
        targetCoordinates: Coordinates,
        zoneConfig: ZoneConfiguration
    ): Pair<Double, AlertTier> {
        val distanceKm = GeoUtils.haversineDistanceKm(
            lat1 = targetCoordinates.latitude,
            lon1 = targetCoordinates.longitude,
            lat2 = threat.latitude,
            lon2 = threat.longitude
        )

        val tier = when {
            distanceKm <= zoneConfig.redRadiusKm -> AlertTier.RED
            distanceKm <= zoneConfig.yellowRadiusKm -> AlertTier.YELLOW
            else -> AlertTier.OUTSIDE
        }

        return Pair(distanceKm, tier)
    }

    /**
     * Evaluates a threat snapshot against all monitored targets.
     */
    fun evaluateSnapshot(
        threats: List<NormalizedThreat>,
        targets: List<MonitoredTarget>,
        zoneConfig: ZoneConfiguration,
        evaluatedAt: Long = System.currentTimeMillis()
    ): List<EvaluatedThreat> {
        if (threats.isEmpty() || targets.isEmpty()) {
            return emptyList()
        }

        val evaluations = ArrayList<EvaluatedThreat>(threats.size * targets.size)

        for (target in targets) {
            val targetCoords = when (target) {
                is MonitoredTarget.FollowMe -> target.locationState.getUsableCoordinates()
                is MonitoredTarget.Pinned -> Coordinates(target.latitude, target.longitude)
            } ?: continue // Target without usable coordinates is skipped from geographic evaluation

            for (threat in threats) {
                val (distanceKm, tier) = evaluateDistanceAndTier(threat, targetCoords, zoneConfig)
                evaluations.add(
                    EvaluatedThreat(
                        threat = threat,
                        targetId = target.targetId,
                        distanceKm = distanceKm,
                        tier = tier,
                        evaluatedAt = evaluatedAt
                    )
                )
            }
        }

        return evaluations
    }

    /**
     * Determines whether an evaluation produces an alert event given the previous episode ledger state.
     */
    fun determineAlertEvent(
        evaluation: EvaluatedThreat,
        previousEpisode: EpisodeRecord?,
        policy: AlertDeduplicationPolicy = AlertDeduplicationPolicy.ONCE_PER_THREAT
    ): AlertEvent? {
        val currentTier = evaluation.tier
        if (currentTier == AlertTier.OUTSIDE) {
            return null
        }

        val threat = evaluation.threat
        val timestamp = evaluation.evaluatedAt

        if (previousEpisode == null) {
            // First time threat enters any alert zone for this target
            return AlertEvent.ThreatEnteredZone(
                sourceId = threat.sourceId,
                threatId = threat.threatId,
                targetId = evaluation.targetId,
                tier = currentTier,
                distanceKm = evaluation.distanceKm,
                timestamp = timestamp,
                threat = threat
            )
        }

        return when (policy) {
            AlertDeduplicationPolicy.ONCE_PER_THREAT -> {
                // In ONCE_PER_THREAT mode:
                // Only alert if currentTier is strictly higher than the highest tier already alerted
                if (currentTier.severityRank > previousEpisode.highestAlertTier.severityRank) {
                    AlertEvent.ThreatEscalated(
                        sourceId = threat.sourceId,
                        threatId = threat.threatId,
                        targetId = evaluation.targetId,
                        previousTier = previousEpisode.highestAlertTier,
                        tier = currentTier,
                        distanceKm = evaluation.distanceKm,
                        timestamp = timestamp,
                        threat = threat
                    )
                } else {
                    null // Already warned at this tier or higher tier
                }
            }
            AlertDeduplicationPolicy.EVERY_ZONE_ENTRY -> {
                // In EVERY_ZONE_ENTRY mode: alert whenever tier escalated or re-entered
                if (currentTier.severityRank > previousEpisode.highestAlertTier.severityRank) {
                    AlertEvent.ThreatEscalated(
                        sourceId = threat.sourceId,
                        threatId = threat.threatId,
                        targetId = evaluation.targetId,
                        previousTier = previousEpisode.highestAlertTier,
                        tier = currentTier,
                        distanceKm = evaluation.distanceKm,
                        timestamp = timestamp,
                        threat = threat
                    )
                } else if (!previousEpisode.active && currentTier.isAlert) {
                    AlertEvent.ThreatEnteredZone(
                        sourceId = threat.sourceId,
                        threatId = threat.threatId,
                        targetId = evaluation.targetId,
                        tier = currentTier,
                        distanceKm = evaluation.distanceKm,
                        timestamp = timestamp,
                        threat = threat
                    )
                } else {
                    null
                }
            }
        }
    }
}
