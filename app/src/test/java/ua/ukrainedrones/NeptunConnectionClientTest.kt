package ua.ukrainedrones.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ua.ukrainedrones.data.Threat
import ua.ukrainedrones.data.ThreatStatus
import ua.ukrainedrones.data.ThreatType

/**
 * Tests for [NeptunConnectionClient] — frame parsing, merge logic,
 * and connection state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NeptunConnectionClientTest {

    // ─────────────────────────────────────────────────────────────
    // Frame parsing
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `parseFrame - valid threat array parses correctly`() {
        val json = """
            [{"id":"shahed-001","type":"UAV","lat":50.0,"lng":30.0,
              "speed_kmh":120,"course":180,"updated_at":"2024-01-15T12:00:00Z",
              "status":"active"}]
        """.trimIndent()

        val threats = NeptunConnectionClient.parseFrame(json)
        assertEquals(1, threats.size)
        assertEquals("shahed-001", threats[0].id)
        assertEquals(ThreatType.UAV, threats[0].type)
        assertEquals(50.0, threats[0].lat, 0.001)
    }

    @Test
    fun `parseFrame - empty array returns empty list`() {
        val threats = NeptunConnectionClient.parseFrame("[]")
        assertTrue(threats.isEmpty())
    }

    @Test
    fun `parseFrame - malformed json returns empty list without crashing`() {
        val threats = NeptunConnectionClient.parseFrame("{invalid")
        assertTrue(threats.isEmpty())
    }

    @Test
    fun `parseFrame - partial invalid entries are filtered out`() {
        val json = """
            [{"id":"valid","type":"UAV","lat":50.0,"lng":30.0,"speed_kmh":100,
              "course":180,"updated_at":"2024-01-15T12:00:00Z","status":"active"},
             {"id":"invalid","type":"UNKNOWN_TYPE"}]
        """.trimIndent()

        val threats = NeptunConnectionClient.parseFrame(json)
        assertEquals(1, threats.size)
        assertEquals("valid", threats[0].id)
    }

    // ─────────────────────────────────────────────────────────────
    // REST/WebSocket merge logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `mergeThreats - websocket newer wins`() {
        val wsThreat = makeThreat(id = "t1", updatedAt = 1_000_000)
        val restThreat = makeThreat(id = "t1", updatedAt = 900_000)
        val merged = NeptunConnectionClient.mergeThreats(
            wsList = listOf(wsThreat),
            restList = listOf(restThreat)
        )
        assertEquals(1, merged.size)
        assertEquals(1_000_000, merged[0].updatedAtMillis)
    }

    @Test
    fun `mergeThreats - rest newer wins when websocket stale`() {
        val wsThreat = makeThreat(id = "t1", updatedAt = 900_000)
        val restThreat = makeThreat(id = "t1", updatedAt = 1_000_000)
        val merged = NeptunConnectionClient.mergeThreats(
            wsList = listOf(wsThreat),
            restList = listOf(restThreat)
        )
        assertEquals(1_000_000, merged[0].updatedAtMillis)
    }

    @Test
    fun `mergeThreats - union of unique ids`() {
        val ws = listOf(makeThreat("a", 1_000_000), makeThreat("b", 1_000_000))
        val rest = listOf(makeThreat("c", 1_000_000))
        val merged = NeptunConnectionClient.mergeThreats(ws, rest)
        assertEquals(3, merged.size)
    }

    @Test
    fun `mergeThreats - empty inputs return empty`() {
        assertTrue(NeptunConnectionClient.mergeThreats(emptyList(), emptyList()).isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // Backoff calculation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculateBackoff - increases with attempt count`() {
        val b0 = NeptunConnectionClient.calculateBackoffMs(attempt = 0)
        val b1 = NeptunConnectionClient.calculateBackoffMs(attempt = 1)
        val b5 = NeptunConnectionClient.calculateBackoffMs(attempt = 5)

        assertTrue(b1 > b0)
        assertTrue(b5 > b1)
        assertTrue(b5 >= 5_000) // At least 5 seconds by attempt 5
    }

    @Test
    fun `calculateBackoff - capped at maximum`() {
        val b100 = NeptunConnectionClient.calculateBackoffMs(attempt = 100)
        assertTrue(b100 <= 60_000) // Should not exceed 60 seconds
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun makeThreat(id: String, updatedAt: Long): Threat = Threat(
        id = id,
        type = ThreatType.UAV,
        lat = 50.0,
        lng = 30.0,
        speedKmh = 100.0,
        course = 180.0,
        updatedAtMillis = updatedAt,
        status = ThreatStatus.ACTIVE,
        altitudeMeters = null,
        source = "test",
        region = null,
        etaMinutes = null,
        reliability = 0.5f
    )
}