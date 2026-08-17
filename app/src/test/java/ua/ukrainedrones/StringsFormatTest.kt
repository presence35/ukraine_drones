package ua.ukrainedrones

import java.time.Instant
import java.time.ZoneId
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
}
