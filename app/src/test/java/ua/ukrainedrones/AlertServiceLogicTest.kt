package ua.ukrainedrones.service

import org.junit.Assert.*
import org.junit.Test
import ua.ukrainedrones.data.Threat
import ua.ukrainedrones.data.ThreatStatus
import ua.ukrainedrones.data.ThreatType

/**
 * Pure-logic tests for [AlertService] alert-decision rules.
 * These test the helper methods without needing Android framework.
 */
class AlertServiceLogicTest {

    // ─────────────────────────────────────────────────────────────
    // Official alert token logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `shouldAnnounceOfficial - new alert triggers`() {
        val lastToken = "old-token"
        val newToken = "new-token"
        assertTrue(AlertService.shouldAnnounceOfficial(newToken, lastToken))
    }

    @Test
    fun `shouldAnnounceOfficial - same token does not re-trigger`() {
        val token = "same-token"
        assertFalse(AlertService.shouldAnnounceOfficial(token, token))
    }

    @Test
    fun `shouldAnnounceOfficial - null last token always triggers`() {
        assertTrue(AlertService.shouldAnnounceOfficial("any-token", null))
    }

    // ─────────────────────────────────────────────────────────────
    // Zone alert cooldown
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `zoneAlertCooldown - within 60 seconds suppresses repeat`() {
        val now = 1_000_000L
        val lastAlert = 999_000L // 1 second ago
        assertTrue(AlertService.isZoneAlertOnCooldown(lastAlert, now))
    }

    @Test
    fun `zoneAlertCooldown - after 60 seconds allows new alert`() {
        val now = 1_000_000L
        val lastAlert = 939_000L // 61 seconds ago
        assertFalse(AlertService.isZoneAlertOnCooldown(lastAlert, now))
    }

    // ─────────────────────────────────────────────────────────────
    // Threat filtering for notifications
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `filterForNotification - excludes stale threats`() {
        val now = System.currentTimeMillis()
        val threats = listOf(
            makeThreat(updatedAt = now - 30_000),      // fresh
            makeThreat(updatedAt = now - 400_000)      // stale
        )
        val filtered = AlertService.filterForNotification(threats, now)
        assertEquals(1, filtered.size)
        assertEquals(now - 30_000, filtered[0].updatedAtMillis)
    }

    @Test
    fun `filterForNotification - excludes ghost threats`() {
        val now = System.currentTimeMillis()
        val threats = listOf(
            makeThreat(status = ThreatStatus.ACTIVE),
            makeThreat(status = ThreatStatus.GHOST)
        )
        val filtered = AlertService.filterForNotification(threats, now)
        assertEquals(1, filtered.size)
        assertEquals(ThreatStatus.ACTIVE, filtered[0].status)
    }

    @Test
    fun `filterForNotification - excludes user-shot threats`() {
        val now = System.currentTimeMillis()
        val threat = makeThreat(id = "shot-1")
        AlertService.markUserShot("shot-1", now)
        val filtered = AlertService.filterForNotification(listOf(threat), now)
        assertTrue(filtered.none { it.id == "shot-1" })
    }

    @Test
    fun `filterForNotification - user-shot expires after 3 seconds`() {
        val t0 = 1_000_000L
        val t3 = 1_003_001L // 3+ seconds later
        val threat = makeThreat(id = "shot-2")
        AlertService.markUserShot("shot-2", t0)
        val filtered = AlertService.filterForNotification(listOf(threat), t3)
        assertEquals(1, filtered.size) // Expired, so included again
    }

    // ─────────────────────────────────────────────────────────────
    // Offline milestone logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `offlineMilestone - 3 minutes triggers first milestone`() {
        val elapsedMs = 3 * 60_000L
        val milestone = AlertService.calculateOfflineMilestone(elapsedMs, lastMilestone = 0)
        assertEquals(3, milestone)
    }

    @Test
    fun `offlineMilestone - 6 minutes triggers next milestone`() {
        val elapsedMs = 6 * 60_000L
        val milestone = AlertService.calculateOfflineMilestone(elapsedMs, lastMilestone = 3)
        assertEquals(6, milestone)
    }

    @Test
    fun `offlineMilestone - same milestone does not re-trigger`() {
        val elapsedMs = 6 * 60_000L
        val milestone = AlertService.calculateOfflineMilestone(elapsedMs, lastMilestone = 6)
        assertNull(milestone)
    }

    @Test
    fun `offlineMilestone - 2 minutes is too early`() {
        val elapsedMs = 2 * 60_000L
        val milestone = AlertService.calculateOfflineMilestone(elapsedMs, lastMilestone = 0)
        assertNull(milestone)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun makeThreat(
        id: String = "test-${System.nanoTime()}",
        updatedAt: Long = System.currentTimeMillis(),
        status: ThreatStatus = ThreatStatus.ACTIVE
    ): Threat = Threat(
        id = id,
        type = ThreatType.UAV,
        lat = 50.0,
        lng = 30.0,
        speedKmh = 100.0,
        course = 180.0,
        updatedAtMillis = updatedAt,
        status = status,
        altitudeMeters = null,
        source = "test",
        region = null,
        etaMinutes = null,
        reliability = 0.5f
    )
}