package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.ThreatType
import ua.okoneba.core.domain.model.UserLocationState
import ua.okoneba.core.domain.model.ZoneConfiguration

class MultiTargetEvaluationTest {

    private val engine = ZoneEvaluationEngine()

    @Test
    fun `test simultaneous independent multi-target evaluation`() {
        val zoneConfig = ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 30.0)

        // Monitored targets:
        // 1. FollowMe (located in Lviv: 49.8397, 24.0297)
        // 2. Pinned Kyiv (50.4501, 30.5234)
        // 3. Pinned Odesa (46.4825, 30.7233)
        val targets = listOf(
            MonitoredTarget.FollowMe(
                locationState = UserLocationState.Valid(
                    latitude = 49.8397,
                    longitude = 24.0297,
                    accuracyMeters = 50f,
                    timestamp = System.currentTimeMillis()
                )
            ),
            MonitoredTarget.Pinned(
                targetId = "pinned_kyiv",
                latitude = 50.4501,
                longitude = 30.5234,
                label = "Kyiv"
            ),
            MonitoredTarget.Pinned(
                targetId = "pinned_odesa",
                latitude = 46.4825,
                longitude = 30.7233,
                label = "Odesa"
            )
        )

        // Threats:
        // Threat A: 5 km from Kyiv -> RED for Kyiv, OUTSIDE for Lviv and Odesa
        val threatA = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "THREAT_KYIV",
            type = ThreatType.MISSILE,
            latitude = 50.4901,
            longitude = 30.5234,
            timestamp = System.currentTimeMillis()
        )

        // Threat B: 20 km from Odesa -> YELLOW for Odesa, OUTSIDE for Kyiv and Lviv
        val threatB = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "THREAT_ODESA",
            type = ThreatType.DRONE,
            latitude = 46.6625,
            longitude = 30.7233,
            timestamp = System.currentTimeMillis()
        )

        val evaluations = engine.evaluateSnapshot(
            threats = listOf(threatA, threatB),
            targets = targets,
            zoneConfig = zoneConfig
        )

        // Total evaluations: 2 threats * 3 targets = 6
        assertEquals(6, evaluations.size)

        // Verify Kyiv target evaluations
        val kyivThreatA = evaluations.first { it.targetId == "pinned_kyiv" && it.threat.threatId == "THREAT_KYIV" }
        assertEquals(AlertTier.RED, kyivThreatA.tier)

        val kyivThreatB = evaluations.first { it.targetId == "pinned_kyiv" && it.threat.threatId == "THREAT_ODESA" }
        assertEquals(AlertTier.OUTSIDE, kyivThreatB.tier)

        // Verify Odesa target evaluations
        val odesaThreatA = evaluations.first { it.targetId == "pinned_odesa" && it.threat.threatId == "THREAT_KYIV" }
        assertEquals(AlertTier.OUTSIDE, odesaThreatA.tier)

        val odesaThreatB = evaluations.first { it.targetId == "pinned_odesa" && it.threat.threatId == "THREAT_ODESA" }
        assertEquals(AlertTier.YELLOW, odesaThreatB.tier)

        // Verify FollowMe (Lviv) evaluations
        val lvivThreatA = evaluations.first { it.targetId == MonitoredTarget.FollowMe.TARGET_ID_FOLLOW_ME && it.threat.threatId == "THREAT_KYIV" }
        assertEquals(AlertTier.OUTSIDE, lvivThreatA.tier)

        val lvivThreatB = evaluations.first { it.targetId == MonitoredTarget.FollowMe.TARGET_ID_FOLLOW_ME && it.threat.threatId == "THREAT_ODESA" }
        assertEquals(AlertTier.OUTSIDE, lvivThreatB.tier)
    }

    @Test
    fun `test unlocated FollowMe does not prevent pinned target evaluations`() {
        val zoneConfig = ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 30.0)

        val targets = listOf(
            MonitoredTarget.FollowMe(
                locationState = UserLocationState.Unlocated("GPS disabled")
            ),
            MonitoredTarget.Pinned(
                targetId = "pinned_kyiv",
                latitude = 50.4501,
                longitude = 30.5234,
                label = "Kyiv"
            )
        )

        val threat = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "THREAT_1",
            type = ThreatType.MISSILE,
            latitude = 50.4601,
            longitude = 30.5234,
            timestamp = System.currentTimeMillis()
        )

        val evaluations = engine.evaluateSnapshot(
            threats = listOf(threat),
            targets = targets,
            zoneConfig = zoneConfig
        )

        // FollowMe has no usable coordinates so it is skipped from evaluations, Kyiv evaluates normally
        assertEquals(1, evaluations.size)
        assertEquals("pinned_kyiv", evaluations.first().targetId)
        assertEquals(AlertTier.RED, evaluations.first().tier)
    }
}
