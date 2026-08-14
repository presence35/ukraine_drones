package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZonesTest {

    private val zones = RadialZones(redKm = 5, yellowKm = 20)

    @Test
    fun `radialZone assigns the right tier`() {
        assertEquals(ThreatZone.INNER, radialZone(0.0, zones))
        assertEquals(ThreatZone.INNER, radialZone(5.0, zones)) // boundary inclusive
        assertEquals(ThreatZone.OUTER, radialZone(5.1, zones))
        assertEquals(ThreatZone.OUTER, radialZone(20.0, zones))
        assertNull(radialZone(20.1, zones))
        assertNull(radialZone(100.0, zones))
    }

    @Test
    fun `fast objects claim the urgent tier at any zone entry when sooner is on`() {
        val missile = threat(type = ThreatType.BALLISTIC)
        assertEquals(ThreatZone.INNER, effectiveZone(missile, ThreatZone.OUTER, true))
        assertEquals(ThreatZone.INNER, effectiveZone(missile, ThreatZone.INNER, true))
    }

    @Test
    fun `fast objects stay in their spatial tier when sooner is off`() {
        val missile = threat(type = ThreatType.BALLISTIC)
        assertEquals(ThreatZone.OUTER, effectiveZone(missile, ThreatZone.OUTER, false))
    }

    @Test
    fun `slow objects never get promoted`() {
        val shahed = threat(type = ThreatType.SHAHED)
        assertEquals(ThreatZone.OUTER, effectiveZone(shahed, ThreatZone.OUTER, true))
    }
}