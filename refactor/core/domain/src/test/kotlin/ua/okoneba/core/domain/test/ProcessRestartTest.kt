package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertEvent
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.EvaluatedThreat
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.ThreatType
import ua.okoneba.core.domain.repository.EpisodeRecord

class ProcessRestartTest {

    private val engine = ZoneEvaluationEngine()

    @Test
    fun `test process restart prevents duplicate alert for previously recorded episode`() {
        // Threat X was evaluated and recorded at RED before process death
        val persistedEpisode = EpisodeRecord(
            sourceId = "NEPTUN",
            threatId = "THREAT_X",
            targetId = "target_primary",
            highestAlertTier = AlertTier.RED,
            firstAlertAt = 1000L,
            lastSeenAt = 1500L,
            active = true
        )

        // Process restarts and receives authoritative snapshot containing THREAT_X in RED
        val threatX = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "THREAT_X",
            type = ThreatType.MISSILE,
            latitude = 50.4501,
            longitude = 30.5234,
            timestamp = 5000L
        )
        val evaluationX = EvaluatedThreat(threatX, "target_primary", 3.0, AlertTier.RED, 5000L)

        // Querying ledger returns persistedEpisode
        val alertEvent = engine.determineAlertEvent(evaluationX, persistedEpisode)

        // Invariant 12: Process restart must not create duplicate alerts
        assertNull(alertEvent)
    }

    @Test
    fun `test threat entering zone during process downtime produces alert on restart`() {
        // Threat Y entered RED while process was dead - no prior episode exists in ledger
        val priorEpisode: EpisodeRecord? = null

        val threatY = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "THREAT_Y_NEW",
            type = ThreatType.BALLISTIC,
            latitude = 50.4501,
            longitude = 30.5234,
            timestamp = 6000L
        )
        val evaluationY = EvaluatedThreat(threatY, "target_primary", 2.5, AlertTier.RED, 6000L)

        val alertEvent = engine.determineAlertEvent(evaluationY, priorEpisode)

        // Invariant 13: Threat currently inside zone must be detected after restart even if entry occurred during downtime
        assertNotNull(alertEvent)
        assertTrue(alertEvent is AlertEvent.ThreatEnteredZone)
        assertEquals("THREAT_Y_NEW", alertEvent!!.threatId)
        assertEquals(AlertTier.RED, alertEvent.tier)
    }
}
