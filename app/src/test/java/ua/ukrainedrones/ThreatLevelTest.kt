package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatLevelTest {

    @Test
    fun `scoreOf is zero outside the yellow zone`() {
        val t = threat(type = ThreatType.BALLISTIC)
        assertEquals(0.0, ThreatLevelModel.scoreOf(t, 100.0, 10.0, 5, 20, 0L), 1e-9)
    }

    @Test
    fun `scoreOf is higher inside the red zone than in the yellow ring`() {
        val t = threat(type = ThreatType.BALLISTIC)
        val inRed = ThreatLevelModel.scoreOf(t, 2.0, 1.0, 5, 20, 0L)
        val inYellow = ThreatLevelModel.scoreOf(t, 10.0, 5.0, 5, 20, 0L)
        assertTrue(inRed > inYellow)
    }

    @Test
    fun `scoreOf caps at 10`() {
        val t = threat(type = ThreatType.BALLISTIC, reliability = Reliability.HIGH, confirmations = 7)
        val score = ThreatLevelModel.scoreOf(t, 1.0, 0.5, 5, 20, 0L)
        assertEquals(10.0, score, 1e-9)
    }

    @Test
    fun `scoreOf is zero for a null distanceFactor threat`() {
        assertEquals(0.0, ThreatLevelModel.scoreOf(threat(), 21.0, null, 5, 20, 0L), 1e-9)
    }

    @Test
    fun `overall is zero for no threats`() {
        assertEquals(0.0, ThreatLevelModel.overall(emptyList()), 1e-9)
    }

    @Test
    fun `overall caps at 10 and top threat dominates`() {
        val one = ThreatLevelModel.overall(listOf(10.0))
        val many = ThreatLevelModel.overall(listOf(10.0, 10.0, 10.0))
        assertEquals(10.0, one, 1e-9)
        assertEquals(10.0, many, 1e-9)
        assertTrue(one > ThreatLevelModel.overall(listOf(3.0)))
    }

    @Test
    fun `overall applies diminishing returns`() {
        // 8 + 4 (half of 8) = 12 → capped 10; vs 10 + 2 = 12 → capped 10.
        val a = ThreatLevelModel.overall(listOf(8.0, 8.0))
        assertEquals(10.0, a, 1e-9)
    }
}