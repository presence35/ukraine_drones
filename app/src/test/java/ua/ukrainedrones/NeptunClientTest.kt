package ua.ukrainedrones

import ua.ukrainedrones.connection.ConnectionState
import ua.ukrainedrones.connection.NeptunConnectionClient
import ua.ukrainedrones.connection.buildTestMig
import ua.ukrainedrones.connection.isDegraded
import ua.ukrainedrones.connection.isOffline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeptunClientTest {

    @Test
    fun `first reconnect attempt is fast`() {
        repeat(100) {
            val ms = NeptunConnectionClient.calculateBackoffMs(1)
            assertTrue("first attempt must retry within 1-3s, got $ms", ms in 1000..3000)
        }
    }

    @Test
    fun `repeated failures back off exponentially`() {
        val expected = mapOf(
            2 to 2000L..2400L,
            3 to 4000L..4400L,
            4 to 8000L..8400L
        )
        repeat(100) {
            for ((attempt, range) in expected) {
                val ms = NeptunConnectionClient.calculateBackoffMs(attempt)
                assertTrue("attempt $attempt should be ~$range, got $ms", ms in range)
            }
        }
    }

    @Test
    fun `backoff caps at 15 seconds`() {
        for (attempt in 5..30) {
            val ms = NeptunConnectionClient.calculateBackoffMs(attempt)
            assertTrue("attempt $attempt should cap at ~15s, got $ms", ms in 15000..15400)
        }
    }

    @Test
    fun `test mig is a live non-advisory aviation takeoff pin`() {
        val t = buildTestMig("test_mig31k_1", 1_000_000L, lat = 49.83, lon = 36.75)
        assertEquals(ThreatType.AVIATION, t.type)
        assertEquals("active", t.status)
        assertFalse(t.advisory)
        assertFalse(t.areaOnly)
        // Static airbase-style pin: no velocity, so it never dead-reckons.
        assertNull(t.bearingDeg)
        assertNull(t.speedKmh)
        assertFalse(t.flying)
        assertEquals(49.83, t.lat, 1e-9)
        assertEquals(36.75, t.lon, 1e-9)
        assertEquals(1_000_000L, t.confirmedAtMillis)
    }

    @Test
    fun `stream is degraded when connected but no frame arrived for the threshold`() {
        val now = System.currentTimeMillis()
        val fresh = ConnectionState.Connected(generation = 1, lastFrameAtMs = now - 5_000L, openedAtMs = now)
        assertFalse(fresh.isDegraded)

        // The Degraded state signals a stale link — the client transitions to it
        // when a Connected state has been quiet for >= DEGRADED_STALE_MS.
        val stale = ConnectionState.Degraded(generation = 1, openedAtMs = now, lastFrameAtMs = now - NeptunConnectionClient.DEGRADED_STALE_MS, quietDurationMs = NeptunConnectionClient.DEGRADED_STALE_MS)
        assertTrue(stale.isDegraded)

        // Offline always wins — never degraded once the socket is actually down.
        val down = ConnectionState.Offline(since = now - NeptunConnectionClient.DEGRADED_STALE_MS, reconnectStartMillis = 0L)
        assertFalse(down.isDegraded)
        assertTrue(down.isOffline)

        // No frames yet (lastFrameAt == 0) → not degraded, still green.
        val never = ConnectionState.Connected(generation = 1, lastFrameAtMs = 0L, openedAtMs = 0L)
        assertFalse(never.isDegraded)
    }
}