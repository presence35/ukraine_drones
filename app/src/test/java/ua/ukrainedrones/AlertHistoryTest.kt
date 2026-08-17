package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertHistoryTest {

    private fun entry(
        at: Long,
        end: Long? = null,
        tier: ThreatZone? = ThreatZone.INNER,
        type: ThreatType? = ThreatType.SHAHED,
        locality: String? = "Одеса",
        dist: Double? = 12.5
    ) = AlertHistoryEntry(at, end, tier, type, locality, dist)

    @Test
    fun `full round trip preserves every field`() {
        val src = listOf(
            entry(1_000, null, ThreatZone.INNER, ThreatType.SHAHED, "Одеса", 12.5),
            entry(2_000, 5_400, ThreatZone.OUTER, ThreatType.KAB, null, null),
            entry(3_000, null, null, null, null, null)
        )
        assertEquals(src, parseAlertHistory(serializeAlertHistory(src)))
    }

    @Test
    fun `malformed lines are skipped`() {
        val raw = "garbage\n" + serializeAlertHistory(listOf(entry(1))) + "\nbroken|line"
        assertEquals(listOf(entry(1)), parseAlertHistory(raw))
    }

    @Test
    fun `ring buffer caps at max entries`() {
        val src = (1..30).map { entry(it.toLong()) }
        val parsed = parseAlertHistory(serializeAlertHistory(src), maxEntries = 20)
        assertEquals(20, parsed.size)
        assertEquals(11L, parsed.first().atMillis)
        assertEquals(30L, parsed.last().atMillis)
    }

    @Test
    fun `empty history round trips to empty`() {
        assertEquals("", serializeAlertHistory(emptyList()))
        assertTrue(parseAlertHistory("").isEmpty())
    }
}
