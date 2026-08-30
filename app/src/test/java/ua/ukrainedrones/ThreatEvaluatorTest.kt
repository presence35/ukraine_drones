package ua.ukrainedrones

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ThreatEvaluator] — the core safety logic that determines
 * zone membership and threat scoring.
 */
class ThreatEvaluatorTest {

    // Kyiv city center as reference point
    private val userLat = 50.4501
    private val userLng = 30.5234

    // Standard zone params
    private val params = ZoneParams(
        slowRedKm = 15,
        slowYellowKm = 40,
        fastRedMin = 2,
        fastYellowMin = 5
    )

    @Before
    fun setUp() {
        ThreatSpeedTracker.clear()
    }

    // ─────────────────────────────────────────────────────────────
    // evaluate() — full evaluation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `evaluate - UAV inside red radius returns INNER`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05, // ~5.5 km north
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(1, result.threatsInner.size)
        assertEquals(0, result.threatsOuter.size)
        assertEquals(ThreatZone.INNER, result.zoneThreats[threat.id])
        assertEquals(ThreatZone.INNER, result.activeZone)
    }

    @Test
    fun `evaluate - UAV between red and yellow returns OUTER`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.20, // ~22 km north
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(0, result.threatsInner.size)
        assertEquals(1, result.threatsOuter.size)
        assertEquals(ThreatZone.OUTER, result.zoneThreats[threat.id])
        assertEquals(ThreatZone.OUTER, result.activeZone)
    }

    @Test
    fun `evaluate - UAV outside yellow returns no zone`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.50, // ~55 km north
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(0, result.threatsInner.size)
        assertEquals(0, result.threatsOuter.size)
        assertNull(result.zoneThreats[threat.id])
        assertNull(result.activeZone)
    }

    @Test
    fun `evaluate - resolved threat is excluded`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            status = "resolved"
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(0, result.mapThreats.size)
    }

    @Test
    fun `evaluate - areaOnly threat is excluded`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            areaOnly = true
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(0, result.mapThreats.size)
    }

    @Test
    fun `evaluate - ghost threat is excluded`() {
        val now = System.currentTimeMillis()
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            updatedAtMillis = now - 3_000_000 // 50 min old -> ghost
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng),
            now = now
        )
        assertEquals(0, result.mapThreats.size)
    }

    @Test
    fun `evaluate - advisory threat excluded from zones but shown on map`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            advisory = true
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(1, result.mapThreats.size) // shown on map
        assertEquals(0, result.threatsInner.size) // but not in zones
        assertEquals(0, result.threatsOuter.size)
    }

    @Test
    fun `evaluate - disabled type excluded`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = LatLng(userLat, userLng),
            mapEnabledTypes = setOf(ThreatType.CRUISE_MISSILE) // SHAHED disabled
        )
        assertEquals(0, result.mapThreats.size)
    }

    @Test
    fun `evaluate - alertEnabledTypes can be subset of mapEnabledTypes`() {
        val shahed = makeThreat(
            id = "shahed-1",
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0
        )
        val cruise = makeThreat(
            id = "cruise-1",
            type = ThreatType.CRUISE_MISSILE,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 850.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(shahed.id to shahed, cruise.id to cruise),
            params = params,
            focusLocation = LatLng(userLat, userLng),
            mapEnabledTypes = ThreatType.entries.toSet(), // all on map
            alertEnabledTypes = setOf(ThreatType.CRUISE_MISSILE) // only cruise alerts
        )
        assertEquals(2, result.mapThreats.size) // both on map
        assertEquals(1, result.threatsInner.size) // only cruise in zone
        assertEquals(ThreatZone.INNER, result.zoneThreats[cruise.id])
        assertNull(result.zoneThreats[shahed.id])
    }

    @Test
    fun `evaluate - null focusLocation returns empty zones`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(threat.id to threat),
            params = params,
            focusLocation = null
        )
        assertEquals(1, result.mapThreats.size) // still on map
        assertEquals(0, result.threatsInner.size)
        assertEquals(0, result.threatsOuter.size)
        assertNull(result.activeZone)
    }

    @Test
    fun `evaluate - multiple threats returns correct activeZone`() {
        val red = makeThreat(
            id = "red",
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0
        )
        val yellow = makeThreat(
            id = "yellow",
            type = ThreatType.SHAHED,
            lat = userLat + 0.20,
            lon = userLng,
            speedKmh = 180.0
        )
        val far = makeThreat(
            id = "far",
            type = ThreatType.SHAHED,
            lat = userLat + 0.50,
            lon = userLng,
            speedKmh = 180.0
        )
        val result = ThreatEvaluator.evaluate(
            threats = mapOf(red.id to red, yellow.id to yellow, far.id to far),
            params = params,
            focusLocation = LatLng(userLat, userLng)
        )
        assertEquals(ThreatZone.INNER, result.activeZone)
        assertEquals(1, result.threatsInner.size)
        assertEquals(1, result.threatsOuter.size)
    }

    // ─────────────────────────────────────────────────────────────
    // zoneThreats() — direct zone mapping
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `zoneThreats - fast threat uses predicted position`() {
        // Ballistic missile far away but heading directly at user
        val threat = makeThreat(
            type = ThreatType.BALLISTIC,
            lat = userLat + 1.8, // ~200 km north
            lon = userLng,
            speedKmh = 3300.0,
            bearingDeg = 180.0, // heading south
            confirmedAtMillis = System.currentTimeMillis() - 30_000 // 30 sec ago
        )
        val zones = ThreatEvaluator.zoneThreats(
            threats = mapOf(threat.id to threat),
            params = params,
            focus = LatLng(userLat, userLng),
            enabled = ThreatType.entries.toSet(),
            now = System.currentTimeMillis()
        )
        // Ballistic at 3300 km/h for 30 sec = ~27.5 km traveled
        // Started at 200 km, now at ~172.5 km, still outside 40 km yellow
        assertNull(zones[threat.id])
    }

    @Test
    fun `zoneThreats - slow threat uses current position not predicted`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            bearingDeg = 180.0
        )
        val zones = ThreatEvaluator.zoneThreats(
            threats = mapOf(threat.id to threat),
            params = params,
            focus = LatLng(userLat, userLng),
            enabled = ThreatType.entries.toSet(),
            now = System.currentTimeMillis()
        )
        assertEquals(ThreatZone.INNER, zones[threat.id])
    }

    // ─────────────────────────────────────────────────────────────
    // inFocusOblast() and inOblast()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `inFocusOblast - matching region returns true`() {
        val threat = makeThreat(region = "Київська область")
        assertTrue(ThreatEvaluator.inFocusOblast(threat, "Київськ"))
    }

    @Test
    fun `inFocusOblast - non-matching region returns false`() {
        val threat = makeThreat(region = "Одеська область")
        assertFalse(ThreatEvaluator.inFocusOblast(threat, "Київськ"))
    }

    @Test
    fun `inFocusOblast - null token returns false`() {
        val threat = makeThreat(region = "Київська область")
        assertFalse(ThreatEvaluator.inFocusOblast(threat, null))
    }

    @Test
    fun `inOblast - district match works`() {
        assertTrue(ThreatEvaluator.inOblast(
            region = "Одеська",
            district = "Київський",
            locality = null,
            token = "Київськ"
        ))
    }

    @Test
    fun `inOblast - locality match works`() {
        assertTrue(ThreatEvaluator.inOblast(
            region = "Одеська",
            district = "Одеський",
            locality = "Київське",
            token = "Київськ"
        ))
    }

    // ─────────────────────────────────────────────────────────────
    // threatBody()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `threatBody - with locality returns label and place`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            locality = "Київ"
        )
        val body = ThreatEvaluator.threatBody(threat, AppLanguage.UA)
        assertEquals("БпЛА — Київ", body)
    }

    @Test
    fun `threatBody - EN translates known city`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            locality = "Київ"
        )
        val body = ThreatEvaluator.threatBody(threat, AppLanguage.EN)
        assertEquals("UAV — Kyiv", body)
    }

    @Test
    fun `threatBody - no locality returns just label`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            locality = null,
            district = null,
            region = null
        )
        val body = ThreatEvaluator.threatBody(threat, AppLanguage.UA)
        assertEquals("БпЛА", body)
    }

    // ─────────────────────────────────────────────────────────────
    // buildOfficialReason()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildOfficialReason - finds best threat in oblast`() {
        val threat = makeThreat(
            id = "reason-test",
            type = ThreatType.SHAHED,
            lat = userLat + 0.05,
            lon = userLng,
            speedKmh = 180.0,
            region = "Київська область",
            explanationShort = "Шахеди курсом на Київ"
        )
        val (reason, id) = ThreatEvaluator.buildOfficialReason(
            threats = mapOf(threat.id to threat),
            token = "Київськ",
            lang = AppLanguage.UA,
            focus = LatLng(userLat, userLng),
            params = params,
            now = System.currentTimeMillis(),
            enabled = ThreatType.entries.toSet(),
            regionFallback = "Київська область"
        )
        assertNotNull(reason)
        assertEquals("reason-test", id)
        assertTrue(reason!!.contains("Київська область"))
    }

    @Test
    fun `buildOfficialReason - no threats returns fallback`() {
        val (reason, id) = ThreatEvaluator.buildOfficialReason(
            threats = emptyMap(),
            token = "Київськ",
            lang = AppLanguage.UA,
            focus = LatLng(userLat, userLng),
            params = params,
            now = System.currentTimeMillis(),
            enabled = ThreatType.entries.toSet(),
            regionFallback = "Київська область"
        )
        assertNotNull(reason)
        assertNull(id)
        assertTrue(reason!!.contains("Київська область"))
    }

    // ─────────────────────────────────────────────────────────────
    // computeProximity()
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `computeProximity - returns distance and ETA`() {
        val threat = makeThreat(
            type = ThreatType.SHAHED,
            lat = userLat + 0.10, // ~11 km
            lon = userLng,
            speedKmh = 180.0,
            bearingDeg = 180.0
        )
        val proximity = ThreatEvaluator.computeProximity(
            threat,
            LatLng(userLat, userLng),
            params,
            System.currentTimeMillis()
        )
        assertNotNull(proximity)
        assertNotNull(proximity!!.distToUserKm)
        assertNotNull(proximity.etaToUserMin)
        assertEquals(11.0, proximity.distToUserKm!!, 2.0)
    }

    @Test
    fun `computeProximity - null threat returns null`() {
        val proximity = ThreatEvaluator.computeProximity(
            null,
            LatLng(userLat, userLng),
            params,
            System.currentTimeMillis()
        )
        assertNull(proximity)
    }

    @Test
    fun `computeProximity - areaOnly threat returns null`() {
        val threat = makeThreat(areaOnly = true)
        val proximity = ThreatEvaluator.computeProximity(
            threat,
            LatLng(userLat, userLng),
            params,
            System.currentTimeMillis()
        )
        assertNull(proximity)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun makeThreat(
        id: String = "test-${System.nanoTime()}",
        type: ThreatType = ThreatType.SHAHED,
        lat: Double = 50.0,
        lon: Double = 30.0,
        speedKmh: Double? = 180.0,
        bearingDeg: Double? = 180.0,
        heading: Double? = null,
        updatedAtMillis: Long = System.currentTimeMillis(),
        confirmedAtMillis: Long? = System.currentTimeMillis() - 60_000,
        status: String = "active",
        advisory: Boolean = false,
        areaOnly: Boolean = false,
        region: String? = "Київська область",
        district: String? = null,
        locality: String? = null,
        explanationShort: String? = null
    ): Threat = Threat(
        id = id,
        type = type,
        title = "Test threat",
        region = region,
        district = district,
        locality = locality,
        lat = lat,
        lon = lon,
        heading = heading,
        bearingDeg = bearingDeg,
        status = status,
        advisory = advisory,
        areaOnly = areaOnly,
        confirmations = 1,
        reliability = Reliability.MEDIUM,
        count = 1,
        explanationShort = explanationShort,
        speedKmh = speedKmh,
        uncertaintyKm = null,
        positionQuality = null,
        confirmedAt = null,
        confirmedAtMillis = confirmedAtMillis,
        updatedAt = null,
        updatedAtMillis = updatedAtMillis,
        trail = emptyList()
    )
}