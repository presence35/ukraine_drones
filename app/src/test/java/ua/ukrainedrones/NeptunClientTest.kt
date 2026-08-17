package ua.ukrainedrones

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
}