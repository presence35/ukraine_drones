package ua.ukrainedrones

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsFormatTest {

    @Test
    fun `datetime is formatted per language with the same wall-clock time`() {
        val millis = Instant.parse("2026-08-17T14:30:00Z").toEpochMilli()
        val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val hhmm = "%02d:%02d".format(zoned.hour, zoned.minute)

        val ua = formatDateTime(AppLanguage.UA, millis)
        val en = formatDateTime(AppLanguage.EN, millis)

        assertTrue("UA was: $ua", ua.startsWith("%02d.%02d,".format(zoned.dayOfMonth, zoned.monthValue)))
        assertTrue("UA was: $ua", ua.endsWith(hhmm))
        assertTrue("EN was: $en", en.startsWith("Aug ${zoned.dayOfMonth},"))
        assertTrue("EN was: $en", en.endsWith(hhmm))
    }

    @Test
    fun `alert age buckets into seconds minutes and hours`() {
        val ua = Strings.get(AppLanguage.UA)
        val en = Strings.get(AppLanguage.EN)

        assertEquals("50 сек", formatAlertAge(100_000L, 50_000L, ua))
        assertEquals("15 min", formatAlertAge(1_000_000L, 60_000L, en))
        assertEquals("2 hr", formatAlertAge(100_000_000L, 92_800_000L, en))
        assertEquals("5 сек", formatAlertAge(5_000L, 0L, ua))
        assertEquals("1 min", formatAlertAge(60_000L, 0L, en))
    }

    @Test
    fun `precise gps age uses proper plurals per language`() {
        assertEquals("Precise GPS: 1 min ago", preciseGpsAgePhrase(1, AppLanguage.EN))
        assertEquals("Precise GPS: 3 min ago", preciseGpsAgePhrase(3, AppLanguage.EN))
        assertEquals("Precise GPS: 21 min ago", preciseGpsAgePhrase(21, AppLanguage.EN))
        assertEquals("Точний GPS: 1 хвилину тому", preciseGpsAgePhrase(1, AppLanguage.UA))
        assertEquals("Точний GPS: 3 хвилини тому", preciseGpsAgePhrase(3, AppLanguage.UA))
        assertEquals("Точний GPS: 5 хвилин тому", preciseGpsAgePhrase(5, AppLanguage.UA))
        assertEquals("Точний GPS: 11 хвилин тому", preciseGpsAgePhrase(11, AppLanguage.UA))
        assertEquals("Точний GPS: 21 хвилину тому", preciseGpsAgePhrase(21, AppLanguage.UA))
        assertEquals("Точний GPS: 22 хвилини тому", preciseGpsAgePhrase(22, AppLanguage.UA))
        assertEquals("Точний GPS: 25 хвилин тому", preciseGpsAgePhrase(25, AppLanguage.UA))
    }
}
