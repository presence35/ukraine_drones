package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PredictionTest {

    @Before
    fun setUp() {
        ThreatSpeedTracker.clear()
    }

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
    fun `isStale is true when server flags stale or fix aged past the window`() {
        val now = 1_000_000L
        assertFalse(threat(updatedAtMillis = now).isStale(now))
        assertTrue(threat(status = "stale", updatedAtMillis = now).isStale(now))
        assertTrue(threat(updatedAtMillis = now - 301_000L).isStale(now))
        assertFalse(threat(updatedAtMillis = null, confirmedAtMillis = null).isStale(now))
    }

    @Test
    fun `aviation never locally expires - only the server flags it stale`() {
        val now = 1_000_000L
        // Way past the 4-minute AVIATION window: a MiG-31K takeoff pin sits at the airbase
        // without fix refreshes, so it must stay live until NEPTUN itself retires it.
        assertFalse(threat(type = ThreatType.AVIATION, updatedAtMillis = now - 3_600_000L).isStale(now))
        assertFalse(
            threat(
                type = ThreatType.AVIATION,
                updatedAtMillis = null,
                confirmedAtMillis = now - 3_600_000L
            ).isStale(now)
        )
        assertTrue(threat(type = ThreatType.AVIATION, status = "stale", updatedAtMillis = now).isStale(now))
    }

    @Test
    fun `aviation ghosts at its own hard cap`() {
        val now = 1_000_000L
        assertFalse(
            threat(
                type = ThreatType.AVIATION,
                updatedAtMillis = now - AVIATION_GHOST_CAP_MS + 60_000L
            ).isGhost(now)
        )
        assertTrue(
            threat(
                type = ThreatType.AVIATION,
                updatedAtMillis = now - AVIATION_GHOST_CAP_MS - 1_000L
            ).isGhost(now)
        )
    }

    @Test
    fun `isGhost requires staleness window plus the hard cap`() {
        val now = 1_000_000L
        val window = staleAfterMs(ThreatType.SHAHED) // 300_000L
        assertFalse(threat(updatedAtMillis = now).isGhost(now))
        // Just past the window but inside the cap: dimmed, still shown.
        assertFalse(threat(updatedAtMillis = now - window - 1_000L).isGhost(now))
        // Past the window plus the cap: gone.
        assertTrue(threat(updatedAtMillis = now - window - STALE_GHOST_CAP_MS - 1_000L).isGhost(now))
        // A short-window type (ballistic) ghosts much sooner.
        assertTrue(
            threat(
                type = ThreatType.BALLISTIC,
                updatedAtMillis = now - staleAfterMs(ThreatType.BALLISTIC) - STALE_GHOST_CAP_MS - 1_000L
            ).isGhost(now)
        )
        assertFalse(threat(updatedAtMillis = null, confirmedAtMillis = null).isGhost(now))
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
    fun `predictPosition does not move on a top-level heading without velocity`() {
        // NEPTUN only dead-reckons tracks with a real velocity (bearingDeg + speedKmh);
        // a bare reported heading must hold the raw fix, exactly like the reference map.
        val t = threat(heading = 90.0, confirmedAtMillis = 0L)
        assertNull(predictPosition(t, 50.0, 1_000L))
    }

    @Test
    fun `predictPosition returns null for an active threat with no heading`() {
        assertNull(predictPosition(threat(confirmedAtMillis = 0L), 50.0, 1_000_000L))
    }

    @Test
    fun `motionHeading prefers the server bearing over measured track`() {
        val tracker = ThreatSpeedTracker
        tracker.clear()
        // Two fixes ~1.1 km apart on a due-north track.
        tracker.record("m1", 0L, 46.48, 30.73)
        tracker.record("m1", 10_000L, 46.49, 30.73)
        val t = threat(id = "m1", bearingDeg = 90.0, speedKmh = 180.0, confirmedAtMillis = 0L)
        // The server's authoritative bearing (east, 90°) wins over our measured north track —
        // facing and glide must match what NEPTUN itself shows.
        assertEquals(90.0, motionHeading(t)!!, 1e-9)
        val p = predictPosition(t, 50.0, 1_000L)
        assertNotNull(p)
        assertTrue(p!!.longitude > t.lon) // glided east, matching the server bearing
        assertEquals(t.lat, p.latitude, 1e-6)
    }

    @Test
    fun `courseDeg matches motionHeading when only a measured track exists`() {
        val tracker = ThreatSpeedTracker
        tracker.clear()
        tracker.record("m2", 0L, 46.48, 30.73)
        tracker.record("m2", 10_000L, 46.49, 30.73)
        val t = threat(id = "m2", lat = 46.48, lon = 30.73,
            status = "active", confirmedAtMillis = 0L)
        // No server bearing: facing falls back to the measured heading (~north). The threat
        // isn't flying (no velocity), so it won't glide — but the icon still faces the track.
        val expected = motionHeading(t)
        assertNotNull(expected)
        assertEquals(expected!!, t.courseDeg, 1e-9)
        assertNull(predictPosition(t, 50.0, 1_000_000L))
    }

    @Test
    fun `motionHeading is null with no fixes and no bearing`() {
        ThreatSpeedTracker.clear()
        assertNull(motionHeading(threat(confirmedAtMillis = 0L)))
        assertNull(predictPosition(threat(confirmedAtMillis = 0L), 50.0, 1_000_000L))
    }

    @Test
    fun `speedTracker prefers server speedKmh as recorded`() {
        val tracker = ThreatSpeedTracker
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
        val tracker = ThreatSpeedTracker
        tracker.record("t1", 0L, 0.0, 0.0)
        tracker.record("t1", 100_000L, 0.1, 0.0) // ~11 km north in 100 s ≈ 111 m/s
        val (speed, source) = tracker.estimateWithSource("t1", threat())!!
        assertEquals(SpeedSource.RECORDED, source)
        assertTrue(speed in 100.0..120.0)
    }

    @Test
    fun `speedTracker falls back to nominal typical`() {
        val tracker = ThreatSpeedTracker
        val (speed, source) = tracker.estimateWithSource("t1", threat())!!
        assertEquals(SpeedSource.TYPICAL, source)
        assertEquals(50.0, speed, 1e-9) // shahed 180 km/h
    }

    @Test
    fun `speedTracker uses trail timestamps`() {
        val tracker = ThreatSpeedTracker
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

    @Test
    fun `etaToCircleEdge direct inbound equals distance over speed`() {
        val center = LatLng(0.0, 0.0)
        val from = LatLng(2000.0 / 110_574.0, 0.0) // 2000 m north of center
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 1000.0, bearingDeg = 180.0, speedMps = 50.0)
        assertNotNull(eta)
        assertEquals((2000.0 - 1000.0) / 50.0 / 60.0, eta!!, 0.05)
    }

    @Test
    fun `etaToCircleEdge is null when already inside the circle`() {
        val center = LatLng(0.0, 0.0)
        val from = LatLng(500.0 / 110_574.0, 0.0) // 500 m north, inside a 1000 m circle
        assertNull(etaToCircleEdgeMinutes(from, center, 1000.0, 180.0, 50.0))
    }

    @Test
    fun `etaToCircleEdge is null when heading away`() {
        val center = LatLng(0.0, 0.0)
        val from = LatLng(2000.0 / 110_574.0, 0.0)
        assertNull(etaToCircleEdgeMinutes(from, center, 1000.0, 0.0, 50.0))
    }

    @Test
    fun `etaToCircleEdge is null with no speed`() {
        val center = LatLng(0.0, 0.0)
        val from = LatLng(2000.0 / 110_574.0, 0.0)
        assertNull(etaToCircleEdgeMinutes(from, center, 1000.0, 180.0, 0.0))
    }
}