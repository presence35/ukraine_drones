package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.AlertEvent
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.EvaluatedThreat
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.ThreatType
import ua.okoneba.core.domain.repository.EpisodeRecord

class AlertDeduplicationTest {

    private val engine = ZoneEvaluationEngine()

    @Test
    fun `test once-per-threat deduplication cycle`() {
        val threat = NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = "X1",
            type = ThreatType.MISSILE,
            latitude = 50.0,
            longitude = 30.0,
            timestamp = 1000L
        )

        // Step 1: Threat initially outside -> no alert
        val evalOutside = EvaluatedThreat(threat, "target_1", 60.0, AlertTier.OUTSIDE, 1000L)
        val event0 = engine.determineAlertEvent(evalOutside, null)
        assertNull(event0)

        // Step 2: Threat enters YELLOW -> AlertEvent.ThreatEnteredZone
        val evalYellow = EvaluatedThreat(threat, "target_1", 20.0, AlertTier.YELLOW, 2000L)
        val event1 = engine.determineAlertEvent(evalYellow, null)
        assertNotNull(event1)
        assertTrue(event1 is AlertEvent.ThreatEnteredZone)
        assertEquals(AlertTier.YELLOW, event1!!.tier)

        // Record episode at YELLOW
        var ledgerRecord = EpisodeRecord(
            sourceId = "NEPTUN",
            threatId = "X1",
            targetId = "target_1",
            highestAlertTier = AlertTier.YELLOW,
            firstAlertAt = 2000L,
            lastSeenAt = 2000L,
            active = true
        )

        // Step 3: Threat enters RED -> AlertEvent.ThreatEscalated
        val evalRed = EvaluatedThreat(threat, "target_1", 5.0, AlertTier.RED, 3000L)
        val event2 = engine.determineAlertEvent(evalRed, ledgerRecord)
        assertNotNull(event2)
        assertTrue(event2 is AlertEvent.ThreatEscalated)
        assertEquals(AlertTier.RED, (event2 as AlertEvent.ThreatEscalated).tier)
        assertEquals(AlertTier.YELLOW, event2.previousTier)

        // Record escalation to RED in ledger
        ledgerRecord = ledgerRecord.copy(highestAlertTier = AlertTier.RED, lastSeenAt = 3000L)

        // Step 4: Threat remains in RED -> No duplicate alert
        val event3 = engine.determineAlertEvent(evalRed, ledgerRecord)
        assertNull(event3)

        // Step 5: Threat moves back to YELLOW (downgrade) -> No alert
        val event4 = engine.determineAlertEvent(evalYellow, ledgerRecord)
        assertNull(event4)

        // Step 6: Threat moves to RED again while episode is still active -> No alert in ONCE_PER_THREAT mode
        val event5 = engine.determineAlertEvent(evalRed, ledgerRecord, AlertDeduplicationPolicy.ONCE_PER_THREAT)
        assertNull(event5)
    }
}
