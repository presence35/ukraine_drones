package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeptunClientTest {

    @Test
    fun `first reconnect attempt is fast`() {
        repeat(100) {
            val ms = NeptunClient.reconnectDelayMs(1)
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
                val ms = NeptunClient.reconnectDelayMs(attempt)
                assertTrue("attempt $attempt should be ~$range, got $ms", ms in range)
            }
        }
    }

    @Test
    fun `backoff caps at 15 seconds`() {
        for (attempt in 5..30) {
            val ms = NeptunClient.reconnectDelayMs(attempt)
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
}