package ua.ukrainedrones.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [NeptunConnectionClient] companion functions and backoff logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NeptunConnectionClientTest {

    // ─────────────────────────────────────────────────────────────
    // Backoff calculation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculateBackoff - attempt 0-1 returns 1-3s`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(attempt = 1)
            assertTrue("attempt 1 should be 1000-3000ms, got $ms", ms in 1000L..3000L)
        }
    }

    @Test
    fun `calculateBackoff - increases with attempt count`() {
        val b1 = NeptunConnectionClient.calculateBackoffMs(attempt = 1)
        val b5 = NeptunConnectionClient.calculateBackoffMs(attempt = 5)
        assertTrue(b5 > b1)
        assertTrue(b5 >= 5_000)
    }

    @Test
    fun `calculateBackoff - capped at 15s max`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(attempt = 100)
            assertTrue("should not exceed 15400ms, got $ms", ms <= 15400L)
        }
    }

    @Test
    fun `calculateBackoff - attempt 0 returns 1-3s`() {
        repeat(10) {
            val ms = NeptunConnectionClient.calculateBackoffMs(attempt = 0)
            assertTrue("attempt 0 should be 1000-3000ms, got $ms", ms in 1000L..3000L)
        }
    }
}
