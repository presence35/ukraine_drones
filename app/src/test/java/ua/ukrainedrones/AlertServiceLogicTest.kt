package ua.ukrainedrones

import org.junit.Assert.*
import org.junit.Test
import ua.ukrainedrones.connection.NeptunConnectionClient

/**
 * Pure-logic tests for alert-decision rules and domain functions.
 * No Android framework required.
 */
class AlertServiceLogicTest {

    // ─────────────────────────────────────────────────────────────
    // reachKm — per-type reach caps
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `reachKm - KAB caps at 70`() = assertEquals(70.0, reachKm(ThreatType.KAB), 0.01)

    @Test
    fun `reachKm - FPV caps at 40`() = assertEquals(40.0, reachKm(ThreatType.FPV_LOITERING), 0.01)

    @Test
    fun `reachKm - RECON caps at 50`() = assertEquals(50.0, reachKm(ThreatType.RECON), 0.01)

    @Test
    fun `reachKm - SHAHED caps at 1000`() = assertEquals(1000.0, reachKm(ThreatType.SHAHED), 0.01)

    @Test
    fun `reachKm - AVIATION caps at 9999`() = assertEquals(9999.0, reachKm(ThreatType.AVIATION), 0.01)

    @Test
    fun `reachKm - BALLISTIC defaults to 1500`() = assertEquals(1500.0, reachKm(ThreatType.BALLISTIC), 0.01)

    @Test
    fun `reachKm - CRUISE defaults to 1500`() = assertEquals(1500.0, reachKm(ThreatType.CRUISE_MISSILE), 0.01)

    @Test
    fun `reachKm - UNKNOWN defaults to 1500`() = assertEquals(1500.0, reachKm(ThreatType.UNKNOWN), 0.01)

    // ─────────────────────────────────────────────────────────────
    // etaMinutes
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `etaMinutes - 100km at 100kmh = 60 min`() = assertEquals(60.0, etaMinutes(100.0, 100.0)!!, 0.01)

    @Test
    fun `etaMinutes - null speed returns null`() = assertNull(etaMinutes(100.0, null))

    @Test
    fun `etaMinutes - zero speed returns null`() = assertNull(etaMinutes(100.0, 0.0))

    @Test
    fun `etaMinutes - negative speed returns null`() = assertNull(etaMinutes(100.0, -50.0))

    // ─────────────────────────────────────────────────────────────
    // zoneTier
    // ─────────────────────────────────────────────────────────────

    private val defaultParams = ZoneParams(slowRedKm = 20, slowYellowKm = 50, fastRedMin = 5, fastYellowMin = 20)

    @Test
    fun `zoneTier - beyond reach returns null`() {
        val t = threat(type = ThreatType.FPV_LOITERING)
        assertNull(zoneTier(t, distKm = 41.0, speedKmh = null, params = defaultParams))
    }

    @Test
    fun `zoneTier - AVIATION within reach always INNER`() {
        val t = threat(type = ThreatType.AVIATION)
        assertEquals(ThreatZone.INNER, zoneTier(t, distKm = 100.0, speedKmh = null, params = defaultParams))
    }

    @Test
    fun `zoneTier - slow threat within red zone`() {
        val t = threat(type = ThreatType.SHAHED)
        assertEquals(ThreatZone.INNER, zoneTier(t, distKm = 15.0, speedKmh = null, params = defaultParams))
    }

    @Test
    fun `zoneTier - slow threat within yellow zone`() {
        val t = threat(type = ThreatType.SHAHED)
        assertEquals(ThreatZone.OUTER, zoneTier(t, distKm = 30.0, speedKmh = null, params = defaultParams))
    }

    @Test
    fun `zoneTier - slow threat beyond yellow returns null`() {
        val t = threat(type = ThreatType.SHAHED)
        assertNull(zoneTier(t, distKm = 60.0, speedKmh = null, params = defaultParams))
    }

    @Test
    fun `zoneTier - fast threat ETA within red`() {
        val t = threat(type = ThreatType.BALLISTIC)
        assertEquals(ThreatZone.INNER, zoneTier(t, distKm = 100.0, speedKmh = 2400.0, params = defaultParams))
    }

    @Test
    fun `zoneTier - fast threat ETA within yellow`() {
        val t = threat(type = ThreatType.BALLISTIC)
        assertEquals(ThreatZone.OUTER, zoneTier(t, distKm = 400.0, speedKmh = 2400.0, params = defaultParams))
    }

    @Test
    fun `zoneTier - fast threat ETA beyond yellow returns null`() {
        val t = threat(type = ThreatType.BALLISTIC)
        // ETA = 2000km / 2400kmh * 60 = 50 min, well beyond fastYellowMin=20
        assertNull(zoneTier(t, distKm = 2000.0, speedKmh = 2400.0, params = defaultParams))
    }

    @Test
    fun `zoneTier - fast threat with null speed returns null`() {
        val t = threat(type = ThreatType.CRUISE_MISSILE)
        assertNull(zoneTier(t, distKm = 50.0, speedKmh = null, params = defaultParams))
    }

    // ─────────────────────────────────────────────────────────────
    // distanceMeters / bearingDegrees
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `distanceMeters - same point = 0`() = assertEquals(0.0, distanceMeters(50.0, 30.0, 50.0, 30.0), 0.1)

    @Test
    fun `distanceMeters - 1 degree lat ~ 111km`() {
        val d = distanceMeters(50.0, 30.0, 51.0, 30.0)
        assertEquals(110_574.0, d, 1000.0)
    }

    @Test
    fun `bearingDegrees - due north`() {
        val b = bearingDegrees(50.0, 30.0, 51.0, 30.0)
        assertEquals(0.0, b, 1.0)
    }

    @Test
    fun `bearingDegrees - due east`() {
        val b = bearingDegrees(50.0, 30.0, 50.0, 31.0)
        assertEquals(90.0, b, 5.0)
    }

    // ─────────────────────────────────────────────────────────────
    // calculateBackoffMs
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `backoff attempt 0-1 returns 1-3s`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(1)
            assertTrue("attempt 1 should be 1000-3000ms, got $ms", ms in 1000L..3000L)
        }
    }

    @Test
    fun `backoff attempt 2 returns ~2-2_4s`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(2)
            assertTrue("attempt 2 should be 2000-2400ms, got $ms", ms in 2000L..2400L)
        }
    }

    @Test
    fun `backoff caps at 15s`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(20)
            assertTrue("attempt 20 should be <= 15400ms, got $ms", ms <= 15400L)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // isExpired / isStale
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `isExpired - fresh threat not expired`() {
        val t = threat(updatedAtMillis = System.currentTimeMillis(), type = ThreatType.SHAHED)
        assertFalse(isExpired(t, System.currentTimeMillis()))
    }

    @Test
    fun `isExpired - stale SHAHED expired after 5 min`() {
        val now = System.currentTimeMillis()
        val t = threat(updatedAtMillis = now - 310_000L, type = ThreatType.SHAHED)
        assertTrue(isExpired(t, now))
    }

    @Test
    fun `isStale - server stale flag always stale`() {
        val t = threat(status = "stale")
        assertTrue(t.isStale(System.currentTimeMillis()))
    }

    @Test
    fun `isStale - AVIATION exempt from local expiry`() {
        val now = System.currentTimeMillis()
        val t = threat(type = ThreatType.AVIATION, updatedAtMillis = now - 600_000L)
        assertFalse(t.isStale(now))
    }

    @Test
    fun `isStale - fresh threat not stale`() {
        val t = threat(updatedAtMillis = System.currentTimeMillis())
        assertFalse(t.isStale(System.currentTimeMillis()))
    }
}
