package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.FeedHealthInfo
import ua.okoneba.core.domain.engine.FeedSnapshot
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.SystemHealthState
import ua.okoneba.core.domain.model.ThreatType

class FeedFailoverTest {

    @Test
    fun `test authoritative feed selection and clean failover without merging`() {
        val evaluator = MasterThreatEvaluator(maxRetentionMs = 60_000L)
        val now = 1_000_000L

        // 1. Setup NEPTUN (primary, priority 0) and Backup-A (secondary, priority 10)
        val neptunHealth = FeedHealthInfo(
            sourceId = "NEPTUN",
            priority = 0,
            isConnected = true,
            lastSuccessfulPacketTime = now
        )
        val backupHealth = FeedHealthInfo(
            sourceId = "BACKUP_A",
            priority = 10,
            isConnected = true,
            lastSuccessfulPacketTime = now
        )

        evaluator.updateFeedHealth(neptunHealth, now)
        evaluator.updateFeedHealth(backupHealth, now)

        val neptunThreats = listOf(
            NormalizedThreat(
                sourceId = "NEPTUN",
                threatId = "N_101",
                type = ThreatType.MISSILE,
                latitude = 50.0,
                longitude = 30.0,
                timestamp = now
            )
        )
        val backupThreats = listOf(
            NormalizedThreat(
                sourceId = "BACKUP_A",
                threatId = "B_999",
                type = ThreatType.DRONE,
                latitude = 48.0,
                longitude = 35.0,
                timestamp = now
            )
        )

        evaluator.ingestFeedSnapshot(FeedSnapshot("NEPTUN", neptunThreats, now), now)
        evaluator.ingestFeedSnapshot(FeedSnapshot("BACKUP_A", backupThreats, now), now)

        // Verify NEPTUN is selected as authoritative
        val state1 = evaluator.authoritativeState.value
        assertEquals("NEPTUN", state1.authoritativeSourceId)
        assertEquals(SystemHealthState.HEALTHY, state1.systemHealth)
        assertEquals(1, state1.threats.size)
        assertEquals("N_101", state1.threats.first().threatId)
        assertFalse(state1.isRetainedSnapshot)

        // 2. NEPTUN fails (disconnects or consecutive failures)
        val failedNeptunHealth = neptunHealth.copy(
            isConnected = false,
            consecutiveFailureCount = 5
        )
        evaluator.updateFeedHealth(failedNeptunHealth, now + 5000)

        // Verify clean failover to Backup-A
        val state2 = evaluator.authoritativeState.value
        assertEquals("BACKUP_A", state2.authoritativeSourceId)
        assertEquals(SystemHealthState.HEALTHY, state2.systemHealth)
        assertEquals(1, state2.threats.size)
        assertEquals("B_999", state2.threats.first().threatId)
        // Ensure no cross-feed merging occurred
        assertFalse(state2.threats.any { it.sourceId == "NEPTUN" })

        // 3. NEPTUN recovers
        val recoveredNeptunHealth = neptunHealth.copy(
            isConnected = true,
            lastSuccessfulPacketTime = now + 10_000,
            consecutiveFailureCount = 0
        )
        val updatedNeptunThreats = listOf(
            NormalizedThreat(
                sourceId = "NEPTUN",
                threatId = "N_102",
                type = ThreatType.BALLISTIC,
                latitude = 50.5,
                longitude = 30.5,
                timestamp = now + 10_000
            )
        )
        evaluator.updateFeedHealth(recoveredNeptunHealth, now + 10_000)
        evaluator.ingestFeedSnapshot(FeedSnapshot("NEPTUN", updatedNeptunThreats, now + 10_000), now + 10_000)

        // Verify NEPTUN seamlessly becomes authoritative again
        val state3 = evaluator.authoritativeState.value
        assertEquals("NEPTUN", state3.authoritativeSourceId)
        assertEquals(SystemHealthState.HEALTHY, state3.systemHealth)
        assertEquals(1, state3.threats.size)
        assertEquals("N_102", state3.threats.first().threatId)
    }
}
