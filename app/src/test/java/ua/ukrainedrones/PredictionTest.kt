package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionTest {

    @Test
    fun `distanceMeters is symmetric and reasonable`() {
        val d1 = distanceMeters(46.48, 30.73, 46.53, 30.73) // ~5.5 km north
        val d2 = distanceMeters(46.53, 30.73, 46.48, 30.73)
        assertEquals(d1, d2, 1e-6)
        assertTrue(d1 in 5000.0..6000.0)
        assertEquals(0.0, distanceMeters(1.0, 1.0, 1.0, 1.0), 1e-9)
    }

    @Test
    fun `staleAfterMs matches per-type windows`() {
        assertEquals(90_000L, staleAfterMs(ThreatType.BALLISTIC))
        assertEquals(180_000L, staleAfterMs(ThreatType.CRUISE_MISSILE))
        assertEquals(240_000L, staleAfterMs(ThreatType.AVIATION))
        assertEquals(300_000L, staleAfterMs(ThreatType.SHAHED))
    }

    @Test
    fun `isExpired uses updatedAt with staleness window`() {
        val now = 1_000_000L
        assertFalse(isExpired(threat(updatedAtMillis = now), now))
        assertFalse(isExpired(threat(updatedAtMillis = now - 200_000L), now)) // shahed 5 min window
        assertTrue(isExpired(threat(updatedAtMillis = now - 301_000L), now))
        assertTrue(isExpired(threat(type = ThreatType.BALLISTIC, updatedAtMillis = now - 91_000L), now))
    }

    @Test
    fun `isExpired never expires a threat with no timestamps`() {
        assertFalse(isExpired(threat(updatedAtMillis = null, confirmedAtMillis = null), 1_000_000L))
    }

    @Test
    fun `isExpired falls back to confirmedAtMillis`() {
        val now = 1_000_000L
        assertTrue(isExpired(threat(updatedAtMillis = null, confirmedAtMillis = now - 600_000L), now))
    }

    @Test
    fun `predictPosition returns null for non-flying or no bearing`() {
        assertNull(predictPosition(threat(bearingDeg = null), 50.0, 1_000_000L))
        assertNull(
            predictPosition(
                threat(status = "stale", bearingDeg = 90.0, speedKmh = 180.0, confirmedAtMillis = 1L),
                50.0,
                1_000_000L
            )
        )
    }

    @Test
    fun `predictPosition moves north along bearing 0`() {
        val t = threat(bearingDeg = 0.0, speedKmh = 180.0, confirmedAtMillis = 0L)
        val p = predictPosition(t, 50.0, 1_000L) // 1s at 50 m/s = 50 m north
        assertNotNull(p)
        assertTrue(p!!.latitude > t.lat)
        assertEquals(t.lon, p.longitude, 1e-6)
    }

    @Test
    fun `predictPosition clamps to the fly horizon`() {
        // Shahed horizon = 300 s; request way beyond it.
        val t = threat(bearingDeg = 0.0, speedKmh = 180.0, confirmedAtMillis = 0L)
        val p = predictPosition(t, 50.0, 1_000_000L)
        assertNotNull(p)
        val dist = distanceMeters(t.lat, t.lon, p!!.latitude, p.longitude)
        assertTrue(dist <= 18_000.0 + 1.0) // maxGhostMeters for SHAHED
    }

    @Test
    fun `predictPosition clamps ghost distance`() {
        // Ballistic ghost cap = 20 km, horizon 90 s; 200 m/s would overrun the cap at 90s.
        val t = threat(type = ThreatType.BALLISTIC, bearingDeg = 0.0, speedKmh = 720.0, confirmedAtMillis = 0L)
        val p = predictPosition(t, 200.0, 100_000L)
        assertNotNull(p)
        val dist = distanceMeters(t.lat, t.lon, p!!.latitude, p.longitude)
        assertTrue(dist <= 20_000.0 + 1.0)
    }

    @Test
    fun `speedTracker prefers server speedKmh as recorded`() {
        val tracker = ThreatSpeedTracker()
        tracker.record("t1", 1L, 0.0, 0.0)
        tracker.record("t1", 100L, 0.0, 0.0)
        val (speed, source) = tracker.estimateWithSource(
            "t1", threat(speedKmh = 180.0)
        )!!
        assertEquals(50.0, speed, 1e-9)
        assertEquals(SpeedSource.RECORDED, source)
    }

    @Test
    fun `speedTracker measures from consecutive fixes`() {
        val tracker = ThreatSpeedTracker()
        tracker.record("t1", 0L, 0.0, 0.0)
        tracker.record("t1", 100_000L, 0.1, 0.0) // ~11 km north in 100 s ≈ 111 m/s
        val (speed, source) = tracker.estimateWithSource("t1", threat())!!
        assertEquals(SpeedSource.RECORDED, source)
        assertTrue(speed in 100.0..120.0)
    }

    @Test
    fun `speedTracker falls back to nominal typical`() {
        val tracker = ThreatSpeedTracker()
        val (speed, source) = tracker.estimateWithSource("t1", threat())!!
        assertEquals(SpeedSource.TYPICAL, source)
        assertEquals(50.0, speed, 1e-9) // shahed 180 km/h
    }

    @Test
    fun `speedTracker uses trail timestamps`() {
        val tracker = ThreatSpeedTracker()
        val t = threat(
            trail = listOf(
                TrailPoint(0.0, 0.0, 0L),
                TrailPoint(0.05, 0.0, 60_000L) // ~5.5 km in 60 s ≈ 92 m/s
            )
        )
        val (speed, source) = tracker.estimateWithSource("t1", t)!!
        assertEquals(SpeedSource.RECORDED, source)
        assertTrue(speed in 80.0..100.0)
    }

    @Test
    fun `typicalSpeedKmh exposes nominal speeds`() {
        assertEquals(180.0, typicalSpeedKmh(ThreatType.SHAHED)!!, 1e-9)
        assertNull(typicalSpeedKmh(ThreatType.UNKNOWN))
    }
}