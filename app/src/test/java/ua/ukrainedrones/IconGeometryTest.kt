package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconGeometryTest {

    /**
     * A north-facing arrow silhouette (apex = nose at top-center) drawn into a [w]×[h] canvas,
     * offset by (dx, dy) to emulate a pack's asymmetric padding.
     */
    private fun northArrowMask(
        w: Int,
        h: Int,
        dx: Int,
        dy: Int
    ): (Int, Int) -> Boolean {
        val cx = w / 2 + dx
        val top = h / 4 + dy
        val bottom = 3 * h / 4 + dy
        val halfBody = w / 10
        return { x, y ->
            y in top..bottom && (
                // triangular nose: widens from the apex down to the body band
                (y < bottom - halfBody && kotlin.math.abs(x - cx) <= (y - top) * halfBody / (bottom - halfBody - top).coerceAtLeast(1)) ||
                    (y >= bottom - halfBody && kotlin.math.abs(x - cx) <= halfBody)
                )
        }
    }

    @Test
    fun `facing matches the true art angle not the declaration magnitude`() {
        // Art faces north; declared facing is off by 30° — the scan must still report ~0°,
        // because the declaration only picks WHICH end is the nose.
        val geo = computeIconGeometry(100, 100, northArrowMask(100, 100, 0, 0), declaredFacingDeg = 330f)
        assertNotNull(geo)
        assertTrue("expected ~0°, got ${geo!!.facingDeg}", geo.facingDeg < 15f || geo.facingDeg > 345f)
    }

    @Test
    fun `facing is stable under canvas repadding`() {
        // Same art drawn into a bigger canvas with extra padding: slot fractions legitimately
        // move with the letterboxing, but the measured facing must not budge.
        val centered = computeIconGeometry(100, 100, northArrowMask(100, 100, 0, 0), 0f)
        val padded = computeIconGeometry(140, 140, northArrowMask(140, 140, 20, 20), 0f)
        assertNotNull(centered)
        assertNotNull(padded)
        assertEquals(centered!!.facingDeg, padded!!.facingDeg, 1f)
    }

    @Test
    fun `anchor sits on the exhaust side of the centroid`() {
        val geo = computeIconGeometry(100, 100, northArrowMask(100, 100, 5, 7), 0f)
        assertNotNull(geo)
        // Nose points up → the exhaust anchor must be BELOW the silhouette's mass center,
        // even though the whole plane was drawn off-center (+5, +7).
        assertTrue(geo!!.anchorYFrac > 0.05f)
        assertEquals(0.07f, geo.anchorXFrac, 0.06f)
    }

    @Test
    fun `declaration only chooses which end is the nose`() {
        // Same north-facing art declared as south-facing: the axis is identical but flipped —
        // so facing lands near 180° and the anchor moves to the opposite (nose-side) end.
        val geo = computeIconGeometry(100, 100, northArrowMask(100, 100, 0, 0), declaredFacingDeg = 180f)
        assertNotNull(geo)
        assertTrue("expected ~180°, got ${geo!!.facingDeg}", geo.facingDeg in 165f..195f)
    }

    @Test
    fun `near-empty masks are rejected`() {
        assertNull(computeIconGeometry(50, 50, { _, _ -> false }, 0f))
    }
}
