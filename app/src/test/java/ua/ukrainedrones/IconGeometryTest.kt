package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Test

class IconGeometryTest {

    @Test
    fun `aviation geometry has two exhausts per pack`() {
        for (set in ThreatIconSet.values()) {
            val geo = IconCatalog.aviationGeometry(set)
            assertEquals(2, geo.exhausts.size)
            assertEquals(IconCatalog.baseDeg(ThreatType.AVIATION, set), geo.facingDeg, 0.01f)
        }
    }

    @Test
    fun `russian exhausts match measured PNG coordinates`() {
        val geo = IconCatalog.aviationGeometry(ThreatIconSet.RUSSIAN)
        assertEquals(0.197674f, geo.exhausts[0].anchorXFrac, 0.0001f)
        assertEquals(-0.079281f, geo.exhausts[0].anchorYFrac, 0.0001f)
        assertEquals(0.394292f, geo.exhausts[1].anchorXFrac, 0.0001f)
        assertEquals(-0.026427f, geo.exhausts[1].anchorYFrac, 0.0001f)
    }
}
