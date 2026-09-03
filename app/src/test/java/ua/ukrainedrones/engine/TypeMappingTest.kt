package ua.ukrainedrones.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ua.ukrainedrones.Reliability
import ua.ukrainedrones.Threat
import ua.ukrainedrones.ThreatType

class TypeMappingTest {

    @Test
    fun `all ThreatType values map to engine strings`() {
        for (type in ThreatType.entries) {
            val engine = type.toEngineString()
            assertNotNull("Null mapping for $type", engine)
            assertTrue("Empty mapping for $type", engine.isNotEmpty())
        }
    }

    @Test
    fun `round-trip ThreatType to engine string`() {
        assertEquals("shahed", ThreatType.SHAHED.toEngineString())
        assertEquals("fpv", ThreatType.FPV_LOITERING.toEngineString())
        assertEquals("cruise", ThreatType.CRUISE_MISSILE.toEngineString())
        assertEquals("ballistic", ThreatType.BALLISTIC.toEngineString())
        assertEquals("kab", ThreatType.KAB.toEngineString())
        assertEquals("aviation", ThreatType.AVIATION.toEngineString())
        assertEquals("recon", ThreatType.RECON.toEngineString())
        assertEquals("unknown", ThreatType.UNKNOWN.toEngineString())
    }

    @Test
    fun `toNormalizedThreat maps all fields`() {
        val threat = Threat(
            id = "test-1",
            type = ThreatType.SHAHED,
            title = "Test threat",
            region = "Odesa",
            district = "Odeskyi",
            locality = "Odesa",
            lat = 46.48,
            lon = 30.73,
            heading = 45.0,
            bearingDeg = 90.0,
            status = "active",
            advisory = false,
            areaOnly = false,
            confirmations = 3,
            reliability = Reliability.HIGH,
            count = 5,
            explanationShort = "heading toward Kyiv",
            speedKmh = 180.0,
            uncertaintyKm = 2.0,
            positionQuality = "confirmed",
            confirmedAt = "2025-01-01T00:00:00Z",
            confirmedAtMillis = 1000L,
            updatedAt = "2025-01-01T00:01:00Z",
            updatedAtMillis = 2000L,
            trail = emptyList()
        )

        val normalized = threat.toNormalizedThreat()

        assertEquals("test-1", normalized.id)
        assertEquals("shahed", normalized.type)
        assertEquals("Test threat", normalized.title)
        assertEquals("Odesa", normalized.region)
        assertEquals("Odeskyi", normalized.district)
        assertEquals("Odesa", normalized.locality)
        assertEquals(46.48, normalized.lat, 0.001)
        assertEquals(30.73, normalized.lon, 0.001)
        assertEquals(45.0, normalized.heading!!, 0.001)
        assertEquals(90.0, normalized.bearingDeg!!, 0.001)
        assertEquals("active", normalized.status)
        assertEquals(false, normalized.advisory)
        assertEquals(false, normalized.areaOnly)
        assertEquals(3, normalized.confirmations)
        assertEquals("HIGH", normalized.reliability)
        assertEquals(5, normalized.count)
        assertEquals("heading toward Kyiv", normalized.explanationShort)
        assertEquals(180.0, normalized.speedKmh!!, 0.001)
        assertEquals(2.0, normalized.uncertaintyKm!!, 0.001)
        assertEquals("confirmed", normalized.positionQuality)
        assertEquals(1000L, normalized.confirmedAtMillis!!)
        assertEquals(2000L, normalized.updatedAtMillis!!)
        assertEquals(emptyList<TrailPoint>(), normalized.trail)
    }

    @Test
    fun `toNormalizedThreat with null fields`() {
        val threat = Threat(
            id = "test-2",
            type = ThreatType.UNKNOWN,
            title = "",
            region = null,
            district = null,
            locality = null,
            lat = 50.0,
            lon = 30.0,
            heading = null,
            bearingDeg = null,
            status = "stale",
            advisory = true,
            areaOnly = true,
            confirmations = 0,
            reliability = Reliability.UNKNOWN,
            count = 0,
            explanationShort = null,
            speedKmh = null,
            uncertaintyKm = null,
            positionQuality = null,
            confirmedAt = null,
            confirmedAtMillis = null,
            updatedAt = null,
            updatedAtMillis = null,
            trail = emptyList()
        )

        val normalized = threat.toNormalizedThreat()

        assertEquals("unknown", normalized.type)
        assertEquals("stale", normalized.status)
        assertEquals(true, normalized.advisory)
        assertEquals(true, normalized.areaOnly)
        assertEquals(null, normalized.heading)
        assertEquals(null, normalized.bearingDeg)
        assertEquals(null, normalized.confirmedAtMillis)
        assertEquals(null, normalized.updatedAtMillis)
    }

    @Test
    fun `trail maps correctly`() {
        val uaTrail = listOf(
            ua.ukrainedrones.TrailPoint(50.0, 30.0, 1000L),
            ua.ukrainedrones.TrailPoint(50.1, 30.1, 2000L)
        )
        val threat = Threat(
            id = "test-3",
            type = ThreatType.SHAHED,
            title = "",
            region = null, district = null, locality = null,
            lat = 50.0, lon = 30.0, heading = null, bearingDeg = null,
            status = "active", advisory = false, areaOnly = false,
            confirmations = 0, reliability = Reliability.UNKNOWN, count = 0,
            explanationShort = null, speedKmh = null, uncertaintyKm = null,
            positionQuality = null, confirmedAt = null, confirmedAtMillis = null,
            updatedAt = null, updatedAtMillis = null, trail = uaTrail
        )

        val normalized = threat.toNormalizedThreat()
        assertEquals(2, normalized.trail.size)
        assertEquals(50.0, normalized.trail[0].lat, 0.001)
        assertEquals(30.1, normalized.trail[1].lon, 0.001)
        assertEquals(1000L, normalized.trail[0].tMillis!!)
    }

    private fun assertTrue(msg: String, value: Boolean) {
        org.junit.Assert.assertTrue(msg, value)
    }
}
