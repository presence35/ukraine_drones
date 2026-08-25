package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AviationFlybyTest {

    private val mig = threat(id = "m1", type = ThreatType.AVIATION)

    @Test
    fun `nextShow picks the first unplayed INNER aviation`() {
        val inner = listOf(
            threat(id = "s1", type = ThreatType.SHAHED),
            mig,
            threat(id = "m2", type = ThreatType.AVIATION)
        )
        val show = AviationFlyby.nextShow(inner, emptySet(), LatLng(46.0, 30.0), true, tick = 1)
        assertNotNull(show)
        assertEquals("m1", show!!.threatId)
    }

    @Test
    fun `nextShow skips already-played ids`() {
        assertNull(AviationFlyby.nextShow(listOf(mig), setOf("m1"), null, true, 1))
    }

    @Test
    fun `nextShow ignores non-aviation tiers`() {
        val inner = listOf(threat(id = "s1", type = ThreatType.SHAHED))
        assertNull(AviationFlyby.nextShow(inner, emptySet(), null, true, 1))
    }

    @Test
    fun `nextShow never plays while the map is hidden`() {
        assertNull(AviationFlyby.nextShow(listOf(mig), emptySet(), null, mapVisible = false, tick = 1))
    }

    @Test
    fun `course falls back to west-to-east without a focus`() {
        val show = AviationFlyby.nextShow(listOf(mig), emptySet(), null, true, 1)
        assertEquals(90.0, show!!.courseDeg, 1e-9)
    }

    @Test
    fun `course points from the airbase toward the focus`() {
        // Airbase due south of the focus → the plane flies north ("at you").
        val show = AviationFlyby.nextShow(
            listOf(threat(id = "m1", type = ThreatType.AVIATION, lat = 45.0, lon = 32.0)),
            emptySet(),
            LatLng(47.0, 32.0),
            true,
            1
        )
        assertEquals(0.0, show!!.courseDeg, 0.5)
    }

    @Test
    fun `direction maps compass bearings to screen vectors`() {
        // North is up (negative y), east right, south down.
        var d = AviationFlyby.direction(0.0)
        assertEquals(0.0, d.first, 1e-9)
        assertEquals(-1.0, d.second, 1e-9)
        d = AviationFlyby.direction(90.0)
        assertEquals(1.0, d.first, 1e-9)
        assertEquals(0.0, d.second, 1e-9)
        d = AviationFlyby.direction(180.0)
        assertEquals(0.0, d.first, 1e-9)
        assertEquals(1.0, d.second, 1e-9)
    }

    @Test
    fun `endpoints enter and exit fully off-screen through the center`() {
        val w = 1000f
        val h = 2000f
        val icon = 100f
        val (entry, exit) = AviationFlyby.endpoints(90.0, w, h, icon)
        // Due east: a horizontal pass through the vertical center, entering half an icon
        // left of the edge and exiting past the right one.
        assertEquals(h / 2f, entry.second, 0.01f)
        assertEquals(h / 2f, exit.second, 0.01f)
        assertEquals(-icon / 2f, entry.first, 0.01f)
        assertEquals(w + icon / 2f, exit.first, 0.01f)

        // For any bearing the path is symmetric about the viewport center.
        val diagonal = AviationFlyby.endpoints(315.0, w, h, icon)
        val cx = (diagonal.first.first + diagonal.second.first) / 2f
        val cy = (diagonal.first.second + diagonal.second.second) / 2f
        assertEquals(w / 2f, cx, 0.01f)
        assertEquals(h / 2f, cy, 0.01f)
    }
}
