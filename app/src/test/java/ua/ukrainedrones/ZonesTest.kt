package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZonesTest {

    private val zones = TimeZones(redMin = 20, yellowMin = 60)

    @Test
    fun `timeZone assigns the right tier by ETA`() {
        assertEquals(ThreatZone.INNER, timeZone(0.0, zones))
        assertEquals(ThreatZone.INNER, timeZone(20.0, zones)) // boundary inclusive
        assertEquals(ThreatZone.OUTER, timeZone(20.1, zones))
        assertEquals(ThreatZone.OUTER, timeZone(60.0, zones))
        assertNull(timeZone(60.1, zones))
        assertNull(timeZone(120.0, zones))
        assertNull(timeZone(null, zones))
    }

    @Test
    fun `etaMinutes converts distance and speed`() {
        assertEquals(10.0, etaMinutes(30.0, 180.0)!!, 1e-9) // 30 km at 180 km/h = 10 min
        assertEquals(60.0, etaMinutes(60.0, 60.0)!!, 1e-9)
        assertNull(etaMinutes(30.0, null))
        assertNull(etaMinutes(30.0, 0.0))
        assertNull(etaMinutes(30.0, -5.0))
    }

    @Test
    fun `reach caps out-of-range types`() {
        assertEquals(70.0, reachKm(ThreatType.KAB), 1e-9)
        assertEquals(40.0, reachKm(ThreatType.FPV_LOITERING), 1e-9)
        assertEquals(50.0, reachKm(ThreatType.RECON), 1e-9)
        assertEquals(1000.0, reachKm(ThreatType.SHAHED), 1e-9)
        assertEquals(1500.0, reachKm(ThreatType.BALLISTIC), 1e-9)
        assertEquals(1500.0, reachKm(ThreatType.CRUISE_MISSILE), 1e-9)
        assertEquals(1500.0, reachKm(ThreatType.AVIATION), 1e-9)
        assertEquals(1500.0, reachKm(ThreatType.UNKNOWN), 1e-9)
    }

    @Test
    fun `timeTier ignores threats beyond reach`() {
        val shahed = threat(type = ThreatType.SHAHED)
        assertNull(timeTier(shahed, 2000.0, 180.0, zones))
    }

    @Test
    fun `fast objects alert from beyond the drawn circle`() {
        val ballistic = threat(type = ThreatType.BALLISTIC)
        // 100 km away at 3000 km/h = 2 min ETA — a red alert from far out.
        assertEquals(ThreatZone.INNER, timeTier(ballistic, 100.0, 3000.0, zones))
    }

    @Test
    fun `slow objects tier by their own ETA`() {
        val shahed = threat(type = ThreatType.SHAHED)
        assertEquals(ThreatZone.INNER, timeTier(shahed, 30.0, 180.0, zones))   // 10 min
        assertEquals(ThreatZone.OUTER, timeTier(shahed, 120.0, 180.0, zones))  // 40 min
        assertNull(timeTier(shahed, 400.0, 180.0, zones))                      // 133 min
    }

    @Test
    fun `aviation tiering uses ballistic speed regardless of reported speed`() {
        val mig = threat(type = ThreatType.AVIATION)
        // At the plane's own 900 km/h a 100 km approach would be OUTER (~6.7 min is INNER anyway);
        // the override matters at distance — 400 km at 900 km/h = 26.7 min (OUTER), but the
        // Kinzhal covers it instantly, so it must be INNER.
        assertEquals(ThreatZone.INNER, timeTier(mig, 400.0, 900.0, zones))
    }

    @Test
    fun `no speed means no tier`() {
        val shahed = threat(type = ThreatType.SHAHED)
        assertNull(timeTier(shahed, 30.0, null, zones))
    }

    @Test
    fun `zone circle grows with the minute threshold at the reference speed`() {
        assertEquals(60.0, zoneCircleKm(20), 1e-9)  // 20 min * 3 km/min
        assertEquals(180.0, zoneCircleKm(60), 1e-9) // 60 min * 3 km/min
        assertEquals(30.0, zoneCircleKm(10), 1e-9)
    }
}