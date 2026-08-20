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
        assertEquals("GPS fix is 1 minute old", preciseGpsAgePhrase(1, AppLanguage.EN))
        assertEquals("GPS fix is 3 minutes old", preciseGpsAgePhrase(3, AppLanguage.EN))
        assertEquals("GPS fix is 21 minutes old", preciseGpsAgePhrase(21, AppLanguage.EN))
        assertEquals("GPS-фікс застарілий на 1 хвилину", preciseGpsAgePhrase(1, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 3 хвилини", preciseGpsAgePhrase(3, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 5 хвилин", preciseGpsAgePhrase(5, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 11 хвилин", preciseGpsAgePhrase(11, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 21 хвилину", preciseGpsAgePhrase(21, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 22 хвилини", preciseGpsAgePhrase(22, AppLanguage.UA))
        assertEquals("GPS-фікс застарілий на 25 хвилин", preciseGpsAgePhrase(25, AppLanguage.UA))
    }
}
