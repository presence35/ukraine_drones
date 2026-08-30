package ua.ukrainedrones

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for prediction math, distance calculations, and speed tracking.
 */
class PredictionTest {

    private val userLat = 50.4501
    private val userLng = 30.5234

    @Before
    fun setUp() {
        ThreatSpeedTracker.clear()
    }

    // ─────────────────────────────────────────────────────────────
    // distanceMeters()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `distanceMeters - same point returns 0`() {
        val dist = distanceMeters(userLat, userLng, userLat, userLng)
        assertEquals(0.0, dist, 1.0)
    }

    @Test
    fun `distanceMeters - 1 degree latitude is about 110km`() {
        val dist = distanceMeters(50.0, 30.0, 51.0, 30.0)
        assertEquals(110_574.0, dist, 500.0)
    }

    @Test
    fun `distanceMeters - known distance Kyiv to Odessa`() {
        // Kyiv: 50.4501, 30.5234
        // Odessa: 46.4825, 30.7233
        val dist = distanceMeters(50.4501, 30.5234, 46.4825, 30.7233)
        // Actual distance ~440 km
        assertEquals(440_000.0, dist, 10_000.0)
    }

    // ─────────────────────────────────────────────────────────────
    // bearingDegrees()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `bearingDegrees - north is 0`() {
        val bearing = bearingDegrees(50.0, 30.0, 51.0, 30.0)
        assertEquals(0.0, bearing, 1.0)
    }

    @Test
    fun `bearingDegrees - east is 90`() {
        val bearing = bearingDegrees(50.0, 30.0, 50.0, 31.0)
        assertEquals(90.0, bearing, 1.0)
    }

    @Test
    fun `bearingDegrees - south is 180`() {
        val bearing = bearingDegrees(50.0, 30.0, 49.0, 30.0)
        assertEquals(180.0, bearing, 1.0)
    }

    @Test
    fun `bearingDegrees - west is 270`() {
        val bearing = bearingDegrees(50.0, 30.0, 50.0, 29.0)
        assertEquals(270.0, bearing, 1.0)
    }

    // ─────────────────────────────────────────────────────────────
    // etaToCircleEdgeMinutes()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `etaToCircleEdge - threat heading directly at user`() {
        // Threat 60 km north, heading south at 900 km/h = 250 m/s
        val from = LatLng(userLat + 0.54, userLng)
        val center = LatLng(userLat, userLng)
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 15_000.0, bearingDeg = 180.0, speedMps = 250.0)
        // ~60 km at 250 m/s = 240 sec = 4 min to center. To 15 km edge = ~3 min.
        assertNotNull(eta)
        assertEquals(3.0, eta!!, 0.5)
    }

    @Test
    fun `etaToCircleEdge - threat already inside radius returns null`() {
        val from = LatLng(userLat + 0.05, userLng) // ~5.5 km
        val center = LatLng(userLat, userLng)
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 15_000.0, bearingDeg = 180.0, speedMps = 50.0)
        assertNull(eta)
    }

    @Test
    fun `etaToCircleEdge - threat flying away returns null`() {
        val from = LatLng(userLat + 0.54, userLng)
        val center = LatLng(userLat, userLng)
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 15_000.0, bearingDeg = 0.0, speedMps = 250.0)
        assertNull(eta)
    }

    @Test
    fun `etaToCircleEdge - zero speed returns null`() {
        val from = LatLng(userLat + 0.54, userLng)
        val center = LatLng(userLat, userLng)
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 15_000.0, bearingDeg = 180.0, speedMps = 0.0)
        assertNull(eta)
    }

    @Test
    fun `etaToCircleEdge - tangential approach misses circle`() {
        // Threat due east of center, heading north — will miss the 15 km circle
        val from = LatLng(userLat, userLng + 0.54)
        val center = LatLng(userLat, userLng)
        val eta = etaToCircleEdgeMinutes(from, center, radiusM = 15_000.0, bearingDeg = 0.0, speedMps = 250.0)
        assertNull(eta)
    }

    // ─────────────────────────────────────────────────────────────
    // predictPosition()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `predictPosition - 10 minutes at 600 km_h north`() {
        // 600 km/h = 166.67 m/s, 10 min = 600 sec, distance = 100,000 m
        val threat = makeThreat(
            lat = 50.0,
            lon = 30.0,
            speedKmh = 600.0,
            bearingDeg = 0.0,
            confirmedAtMillis = System.currentTimeMillis() - 60_000 // 1 min ago
        )
        val now = System.currentTimeMillis()
        val pos = predictPosition(threat, speedMps = 600_000.0 / 3600.0, nowMillis = now)
        // Since confirmedAt is 1 min ago, elapsed = 60 sec
        // dist = 166.67 * 60 = 10,000 m = 10 km
        assertNotNull(pos)
        assertEquals(50.09, pos!!.latitude, 0.02)
        assertEquals(30.0, pos.longitude, 0.01)
    }

    @Test
    fun `predictPosition - not flying returns null`() {
        val threat = makeThreat(
            bearingDeg = null, // missing bearing
            speedKmh = 100.0,
            confirmedAtMillis = System.currentTimeMillis() - 60_000,
            status = "active"
        )
        val pos = predictPosition(threat, speedMps = 100.0 / 3.6, nowMillis = System.currentTimeMillis())
        assertNull(pos)
    }

    @Test
    fun `predictPosition - resolved status returns null`() {
        val threat = makeThreat(
            bearingDeg = 180.0,
            speedKmh = 100.0,
            confirmedAtMillis = System.currentTimeMillis() - 60_000,
            status = "resolved"
        )
        val pos = predictPosition(threat, speedMps = 100.0 / 3.6, nowMillis = System.currentTimeMillis())
        assertNull(pos)
    }

    // ─────────────────────────────────────────────────────────────
    // staleAfterMs() per-type
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `staleAfterMs - ballistic is 90 seconds`() {
        assertEquals(90_000L, staleAfterMs(ThreatType.BALLISTIC))
    }

    @Test
    fun `staleAfterMs - cruise missile is 180 seconds`() {
        assertEquals(180_000L, staleAfterMs(ThreatType.CRUISE_MISSILE))
    }

    @Test
    fun `staleAfterMs - SHAHED is 300 seconds`() {
        assertEquals(300_000L, staleAfterMs(ThreatType.SHAHED))
    }

    // ─────────────────────────────────────────────────────────────
    // isExpired()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `isExpired - fresh threat returns false`() {
        val now = System.currentTimeMillis()
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            updatedAtMillis = now - 30_000
        )
        assertFalse(isExpired(threat, now))
    }

    @Test
    fun `isExpired - past stale window returns true`() {
        val now = System.currentTimeMillis()
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            updatedAtMillis = now - 400_000 // > 300_000
        )
        assertTrue(isExpired(threat, now))
    }

    // ─────────────────────────────────────────────────────────────
    // ThreatSpeedTracker
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `speedTracker - records and estimates speed`() {
        val now = System.currentTimeMillis()
        ThreatSpeedTracker.record("t1", now - 10_000, 50.0, 30.0)
        ThreatSpeedTracker.record("t1", now, 50.05, 30.0)

        val threat = makeThreat(id = "t1", speedKmh = null, bearingDeg = null)
        val speed = ThreatSpeedTracker.estimate("t1", threat)
        assertNotNull(speed)
        // 0.05 deg lat ≈ 5.5 km in 10 sec ≈ 550 m/s
        assertTrue(speed!! > 100.0)
    }

    @Test
    fun `speedTracker - prefers server speedKmh over measured`() {
        val now = System.currentTimeMillis()
        ThreatSpeedTracker.record("t2", now - 10_000, 50.0, 30.0)
        ThreatSpeedTracker.record("t2", now, 50.1, 30.0)

        val threat = makeThreat(id = "t2", speedKmh = 180.0, bearingDeg = 90.0)
        val result = ThreatSpeedTracker.estimateWithSource("t2", threat)
        assertNotNull(result)
        assertEquals(180.0 / 3.6, result!!.first, 0.1)
        assertEquals(SpeedSource.RECORDED, result.second)
    }

    @Test
    fun `speedTracker - falls back to nominal when no data`() {
        ThreatSpeedTracker.clear()
        val threat = makeThreat(id = "t3", speedKmh = null, bearingDeg = null)
        val result = ThreatSpeedTracker.estimateWithSource("t3", threat)
        assertNotNull(result)
        assertEquals(SpeedSource.TYPICAL, result!!.second)
    }

    @Test
    fun `speedTracker - clear removes all records`() {
        val now = System.currentTimeMillis()
        ThreatSpeedTracker.record("t4", now, 50.0, 30.0)
        ThreatSpeedTracker.clear()
        val threat = makeThreat(id = "t4", type = ThreatType.UNKNOWN, speedKmh = null, bearingDeg = null)
        assertNull(ThreatSpeedTracker.estimate("t4", threat))
    }

    @Test
    fun `speedTracker - thread safety with concurrent writes`() {
        val threads = (1..10).map { i ->
            Thread {
                repeat(100) { j ->
                    ThreatSpeedTracker.record("concurrent", System.currentTimeMillis() + j, 50.0 + i * 0.001, 30.0)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val threat = makeThreat(id = "concurrent", speedKmh = null, bearingDeg = null)
        val avg = ThreatSpeedTracker.estimate("concurrent", threat)
        assertNotNull(avg)
        assertTrue(avg!! >= 0)
    }

    @Test
    fun `speedTracker - measuredHeading needs 2 fixes`() {
        ThreatSpeedTracker.clear()
        val now = System.currentTimeMillis()
        ThreatSpeedTracker.record("heading-test", now - 10_000, 50.0, 30.0)
        // Only one fix
        assertNull(ThreatSpeedTracker.measuredHeading("heading-test"))

        ThreatSpeedTracker.record("heading-test", now, 50.1, 30.1)
        val heading = ThreatSpeedTracker.measuredHeading("heading-test")
        assertNotNull(heading)
        // Heading from (50,30) to (50.1,30.1) is ~33° (cos(50°) compresses longitude)
        assertEquals(33.0, heading!!, 10.0)
    }

    // ─────────────────────────────────────────────────────────────
    // typicalSpeedKmh()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `typicalSpeedKmh - ballistic is about 3300`() {
        val speed = typicalSpeedKmh(ThreatType.BALLISTIC)
        assertNotNull(speed)
        assertEquals(3300.0, speed!!, 50.0)
    }

    @Test
    fun `typicalSpeedKmh - SHAHED is about 180`() {
        val speed = typicalSpeedKmh(ThreatType.SHAHED)
        assertNotNull(speed)
        assertEquals(180.0, speed!!, 10.0)
    }

    @Test
    fun `typicalSpeedKmh - UNKNOWN returns null`() {
        assertNull(typicalSpeedKmh(ThreatType.UNKNOWN))
    }

    // ─────────────────────────────────────────────────────────────
    // motionHeading()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `motionHeading - prefers bearingDeg`() {
        val threat = makeThreat(bearingDeg = 90.0, heading = 180.0)
        assertEquals(90.0, motionHeading(threat)!!, 0.1)
    }

    @Test
    fun `motionHeading - falls back to heading`() {
        val threat = makeThreat(bearingDeg = null, heading = 180.0)
        assertEquals(180.0, motionHeading(threat)!!, 0.1)
    }

    @Test
    fun `motionHeading - falls back to measured`() {
        ThreatSpeedTracker.clear()
        val now = System.currentTimeMillis()
        ThreatSpeedTracker.record("motion-test", now - 10_000, 50.0, 30.0)
        ThreatSpeedTracker.record("motion-test", now, 50.1, 30.0)

        val threat = makeThreat(id = "motion-test", bearingDeg = null, heading = null)
        val heading = motionHeading(threat)
        assertNotNull(heading)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun makeThreat(
        id: String = "pred-test",
        type: ThreatType = ThreatType.SHAHED,
        lat: Double = 50.0,
        lon: Double = 30.0,
        speedKmh: Double? = 100.0,
        bearingDeg: Double? = 180.0,
        heading: Double? = null,
        confirmedAtMillis: Long? = System.currentTimeMillis() - 60_000,
        updatedAtMillis: Long? = null,
        status: String = "active"
    ): Threat = Threat(
        id = id,
        type = type,
        title = "Test",
        region = null,
        district = null,
        locality = null,
        lat = lat,
        lon = lon,
        heading = heading,
        bearingDeg = bearingDeg,
        status = status,
        advisory = false,
        areaOnly = false,
        confirmations = 1,
        reliability = Reliability.MEDIUM,
        count = 1,
        explanationShort = null,
        speedKmh = speedKmh,
        uncertaintyKm = null,
        positionQuality = null,
        confirmedAt = null,
        confirmedAtMillis = confirmedAtMillis,
        updatedAt = null,
        updatedAtMillis = updatedAtMillis ?: confirmedAtMillis ?: System.currentTimeMillis(),
        trail = emptyList()
    )
}