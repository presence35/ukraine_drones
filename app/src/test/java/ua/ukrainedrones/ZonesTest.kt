package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZonesTest {

    private val params = ZoneParams(
        slowRedKm = 60,
        slowYellowKm = 180,
        fastRedMin = 10,
        fastYellowMin = 30
    )

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
        assertEquals(9999.0, reachKm(ThreatType.AVIATION), 1e-9)
        assertEquals(1500.0, reachKm(ThreatType.UNKNOWN), 1e-9)
    }

    @Test
    fun `slow threats tier by distance`() {
        val shahed = threat(type = ThreatType.SHAHED)
        assertEquals(ThreatZone.INNER, zoneTier(shahed, 30.0, 180.0, params))
        assertEquals(ThreatZone.INNER, zoneTier(shahed, 60.0, 180.0, params)) // boundary inclusive
        assertEquals(ThreatZone.OUTER, zoneTier(shahed, 61.0, 180.0, params))
        assertEquals(ThreatZone.OUTER, zoneTier(shahed, 180.0, 180.0, params))
        assertNull(zoneTier(shahed, 181.0, 180.0, params))
        assertNull(zoneTier(shahed, 2000.0, 180.0, params)) // beyond reach
    }

    @Test
    fun `slow threats ignore speed`() {
        val shahed = threat(type = ThreatType.SHAHED)
        // Distance-only tiering: a slow object tiers even without a usable speed.
        assertEquals(ThreatZone.INNER, zoneTier(shahed, 30.0, null, params))
        assertEquals(ThreatZone.OUTER, zoneTier(shahed, 100.0, null, params))
    }

    @Test
    fun `fast threats tier by time to arrival`() {
        val ballistic = threat(type = ThreatType.BALLISTIC)
        val cruise = threat(type = ThreatType.CRUISE_MISSILE)
        // 3300 km/h → 300 km is ~5.5 min (INNER), 700 km is ~12.7 min (OUTER).
        assertEquals(ThreatZone.INNER, zoneTier(ballistic, 300.0, 3300.0, params))
        assertEquals(ThreatZone.OUTER, zoneTier(ballistic, 700.0, 3300.0, params))
        // 1500 km at 3300 km/h = ~27.3 min → still OUTER (within the 30-min yellow).
        assertEquals(ThreatZone.OUTER, zoneTier(ballistic, 1500.0, 3300.0, params))
        // 700 km at 850 km/h = ~49.4 min → beyond the 30-min yellow.
        assertNull(zoneTier(cruise, 700.0, 850.0, params))
    }

    @Test
    fun `fast threat ETA boundary is inclusive`() {
        val cruise = threat(type = ThreatType.CRUISE_MISSILE)
        // 850 km/h × 10 min = 141.7 km exactly at the red boundary.
        assertEquals(ThreatZone.INNER, zoneTier(cruise, 141.666, 850.0, params))
        assertEquals(ThreatZone.OUTER, zoneTier(cruise, 141.7, 850.0, params))
    }

    @Test
    fun `fast threats with no speed never tier`() {
        val ballistic = threat(type = ThreatType.BALLISTIC)
        assertNull(zoneTier(ballistic, 300.0, null, params))
    }

    @Test
    fun `aviation rings INNER country-wide regardless of ETA or zones`() {
        val mig = threat(type = ThreatType.AVIATION)
        // A MiG-31K takeoff is a country-wide Kinzhal warning: any distance within reach rings,
        // whatever the reported speed and however tight the thresholds are.
        assertEquals(ThreatZone.INNER, zoneTier(mig, 400.0, 900.0, params))
        assertEquals(ThreatZone.INNER, zoneTier(mig, 1400.0, null, params))
        val tight = params.copy(fastRedMin = 1, fastYellowMin = 3)
        assertEquals(ThreatZone.INNER, zoneTier(mig, 900.0, 900.0, tight))
    }

    @Test
    fun `aviation beyond reach never tiers`() {
        assertNull(zoneTier(threat(type = ThreatType.AVIATION), 10_000.0, 900.0, params))
    }

    @Test
    fun `reach caps out-of-range threats`() {
        val shahed = threat(type = ThreatType.SHAHED)
        val kab = threat(type = ThreatType.KAB)
        assertNull(zoneTier(shahed, 2000.0, 180.0, params))
        // KAB reach is 70 km — inside the slow yellow (180) but physically impossible.
        assertNull(zoneTier(kab, 100.0, 900.0, params))
    }
}