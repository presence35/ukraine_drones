package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.FeedHealthInfo
import ua.okoneba.core.domain.engine.FeedSnapshot
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.SystemHealthState
import ua.okoneba.core.domain.model.ThreatType

class FeedFailureTest {

    @Test
    fun `test feed failure preserves retained snapshot and never conflates with all clear`() {
        val maxRetentionMs = 120_000L // 2 minutes
        val evaluator = MasterThreatEvaluator(maxRetentionMs = maxRetentionMs)
        val startTime = 100_000L

        // Ingest valid active threat from NEPTUN
        val neptunHealth = FeedHealthInfo(
            sourceId = "NEPTUN",
            priority = 0,
            isConnected = true,
            lastSuccessfulPacketTime = startTime
        )
        val activeThreats = listOf(
            NormalizedThreat(
                sourceId = "NEPTUN",
                threatId = "THREAT_ACTIVE",
                type = ThreatType.MISSILE,
                latitude = 49.0,
                longitude = 31.0,
                timestamp = startTime
            )
        )
        evaluator.updateFeedHealth(neptunHealth, startTime)
        evaluator.ingestFeedSnapshot(FeedSnapshot("NEPTUN", activeThreats, startTime), startTime)

        // All healthy
        val stateHealthy = evaluator.authoritativeState.value
        assertEquals(SystemHealthState.HEALTHY, stateHealthy.systemHealth)
        assertEquals(1, stateHealthy.threats.size)

        // All feeds suddenly disconnect 30 seconds later
        val disconnectedHealth = neptunHealth.copy(isConnected = false)
        val disconnectTime = startTime + 30_000L
        evaluator.updateFeedHealth(disconnectedHealth, disconnectTime)

        // Verify Invariant 4: NO FEED is NEVER represented as NO THREATS
        val stateRetained = evaluator.authoritativeState.value
        assertEquals(SystemHealthState.DEGRADED, stateRetained.systemHealth)
        assertTrue(stateRetained.isRetainedSnapshot)
        assertEquals(1, stateRetained.threats.size)
        assertEquals("THREAT_ACTIVE", stateRetained.threats.first().threatId)

        // Now advance time past the retention threshold (e.g. 150 seconds later)
        val expiredTime = startTime + 150_000L
        evaluator.reevaluateAuthoritativeSource(expiredTime)

        val stateExpired = evaluator.authoritativeState.value
        assertEquals(SystemHealthState.DEGRADED_NO_FEEDS, stateExpired.systemHealth)
        assertFalse(stateExpired.isRetainedSnapshot)
        assertTrue(stateExpired.threats.isEmpty())
    }
}
