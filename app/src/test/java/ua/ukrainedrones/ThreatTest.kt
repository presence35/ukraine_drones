package ua.ukrainedrones

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatTest {

    @Test
    fun `fromApi maps known keys and aliases`() {
        assertEquals(ThreatType.SHAHED, ThreatType.fromApi("shahed"))
        assertEquals(ThreatType.FPV_LOITERING, ThreatType.fromApi("fpv"))
        assertEquals(ThreatType.SHAHED, ThreatType.fromApi("uav"))
        assertEquals(ThreatType.FPV_LOITERING, ThreatType.fromApi("lancet"))
        assertEquals(ThreatType.AVIATION, ThreatType.fromApi("mig31k"))
        assertEquals(ThreatType.UNKNOWN, ThreatType.fromApi("whatever"))
        assertEquals(ThreatType.UNKNOWN, ThreatType.fromApi(null))
    }

    @Test
    fun `fromJson parses a full record`() {
        val json = JSONObject(
            """
            {
              "id": "abc123",
              "type": "cruise",
              "title": "Ракета курсом на Одесу",
              "region": "Одеська",
              "district": "Одеський",
              "locality": "Одеса",
              "lat": 46.48,
              "lon": 30.73,
              "heading": 45.0,
              "velocity": {"speedKmh": 850.0, "bearingDeg": 90.0},
              "status": "active",
              "advisory": false,
              "areaOnly": false,
              "sourceCount": 3,
              "confidenceLevel": "high",
              "count": 4,
              "explanationShort": "Група БпЛА курсом на Київ",
              "uncertaintyKm": 2.5,
              "positionQuality": "approx",
              "confirmedAt": "2026-08-14T10:00:00Z",
              "updatedAt": "2026-08-14T10:05:00Z",
              "trail": [
                {"lat": 46.0, "lon": 30.0, "t": "2026-08-14T09:59:00Z"},
                {"lat": 46.1, "lon": 30.1, "t": "2026-08-14T10:00:00Z"}
              ]
            }
            """.trimIndent()
        )
        val t = Threat.fromJson(json)
        assertNotNull(t)
        t!!
        assertEquals("abc123", t.id)
        assertEquals(ThreatType.CRUISE_MISSILE, t.type)
        assertEquals(850.0, t.speedKmh!!, 1e-9)
        assertEquals(90.0, t.bearingDeg!!, 1e-9)
        assertEquals(3, t.confirmations)
        assertEquals(Reliability.HIGH, t.reliability)
        assertEquals(4, t.count)
        assertTrue(t.flying)
        assertEquals(2, t.trail.size)
        assertNotNull(t.updatedAtMillis)
    }

    @Test
    fun `fromJson returns null without a position`() {
        assertNull(Threat.fromJson(JSONObject("""{"id":"x"}""")))
    }

    @Test
    fun `fromJson strips bare confirmation text from explanationShort`() {
        val t = Threat.fromJson(
            JSONObject("""{"id":"x","lat":46.0,"lon":30.0,"explanationShort":"Підтверджень: 3"}""")
        )
        assertNull(t!!.explanationShort)
    }

    @Test
    fun `fromJson strips trailing confirmation count from explanationShort`() {
        val t = Threat.fromJson(
            JSONObject("""{"id":"x","lat":46.0,"lon":30.0,"explanationShort":"Рій БпЛА барражує над морем. Підтверджень: 6"}""")
        )
        assertEquals("Рій БпЛА барражує над морем", t!!.explanationShort)
    }

    @Test
    fun `fromJson strips a bare trailing count from explanationShort`() {
        val t = Threat.fromJson(
            JSONObject("""{"id":"x","lat":46.0,"lon":30.0,"explanationShort":"Рій БпЛА барражує над морем. : 6"}""")
        )
        assertEquals("Рій БпЛА барражує над морем", t!!.explanationShort)
    }

    @Test
    fun `fromJson keeps course text`() {
        val t = Threat.fromJson(
            JSONObject("""{"id":"x","lat":46.0,"lon":30.0,"explanationShort":"Група БпЛА курсом на Київ"}""")
        )
        assertEquals("Група БпЛА курсом на Київ", t!!.explanationShort)
    }

    @Test
    fun `translateCourseAssessment translates known templates`() {
        assertEquals(
            "Group of UAVs heading toward Kyiv",
            translateCourseAssessment("Група БпЛА курсом на Київ", AppLanguage.EN)
        )
    }

    @Test
    fun `translateCourseAssessment passes through UA`() {
        assertEquals("Група БпЛА курсом на Київ", translateCourseAssessment("Група БпЛА курсом на Київ", AppLanguage.UA))
    }

    @Test
    fun `translateCourseAssessment transliterates unknown text`() {
        val raw = "Деякий невідомий текст"
        assertEquals("Deiakyi nevidomyi tekst", translateCourseAssessment(raw, AppLanguage.EN))
        assertNull(translateCourseAssessment(null, AppLanguage.EN))
        assertNull(translateCourseAssessment("", AppLanguage.EN))
    }

    @Test
    fun `translateCourseAssessment transliterates unknown places`() {
        assertEquals(
            "UAV heading toward Zolote",
            translateCourseAssessment("БпЛА курсом на Золоте", AppLanguage.EN)
        )
        assertEquals(
            "Course toward Zhovti Vody",
            translateCourseAssessment("Курс на Жовті Води", AppLanguage.EN)
        )
    }

    @Test
    fun `translateCourseAssessment translates common words instead of transliterating them`() {
        assertEquals(
            "UAV over the sea",
            translateCourseAssessment("БпЛА над морем", AppLanguage.EN)
        )
        assertEquals(
            "UAV over the sea",
            translateCourseAssessment("БпЛА над морі", AppLanguage.EN)
        )
        assertEquals(
            "UAV heading toward Black Sea",
            translateCourseAssessment("БпЛА курсом на Чорне море", AppLanguage.EN)
        )
        assertEquals(
            "Missile heading toward the water area",
            translateCourseAssessment("Ракета летить у напрямку акваторії", AppLanguage.EN)
        )
    }

    @Test
    fun `fallbackCourse swaps common words and vocabulary before transliterating`() {
        assertEquals(
            "Swarm UAV loiters over the sea",
            translateCourseAssessment("Рій БпЛА барражує над морем", AppLanguage.EN)
        )
    }

    @Test
    fun `fallbackCourse is deterministic per id`() {
        assertEquals(Threat.fallbackCourse("abc"), Threat.fallbackCourse("abc"), 1e-9)
        assertFalse(Threat.fallbackCourse("abc") == Threat.fallbackCourse("def"))
    }

    @Test
    fun `courseDeg prefers velocity bearing while flying`() {
        val t = threat(bearingDeg = 90.0, speedKmh = 180.0, confirmedAtMillis = 1L, heading = 200.0)
        assertEquals(90.0, t.courseDeg, 1e-9)
    }

    @Test
    fun `courseDeg uses velocity bearing even when not flying`() {
        // No speed/confirmedAt — predictPosition still glides along bearingDeg, so the icon
        // must face it too (matches predictPosition's heading resolution).
        val t = threat(bearingDeg = 270.0, heading = 10.0)
        assertFalse(t.flying)
        assertEquals(270.0, t.courseDeg, 1e-9)
    }

    @Test
    fun `courseDeg falls back to heading then pseudo-course`() {
        assertEquals(35.0, threat(heading = 35.0).courseDeg, 1e-9)
        assertEquals(Threat.fallbackCourse("t1"), threat().courseDeg, 1e-9)
    }

    @Test
    fun `courseDeg aims the icon at a city named in the course message`() {
        val t = threat(explanationShort = "Група БпЛА курсом на Київ", lat = 46.48, lon = 30.73)
        assertEquals(
            bearingDegrees(46.48, 30.73, 50.4501, 30.5234),
            t.courseDeg, 1e-9
        )
    }

    @Test
    fun `courseDeg reads the course from the title when the message is empty`() {
        val t = threat(title = "БпЛА курсом на Київ", explanationShort = null, lat = 46.48, lon = 30.73)
        assertEquals(
            bearingDegrees(46.48, 30.73, 50.4501, 30.5234),
            t.courseDeg, 1e-9
        )
    }

    @Test
    fun `courseDeg falls back to pseudo-course when the message names no known place`() {
        val t = threat(explanationShort = "БпЛА курсом на Золоте")
        assertEquals(Threat.fallbackCourse("t1"), t.courseDeg, 1e-9)
    }

    @Test
    fun `courseDeg prefers the velocity bearing over the message`() {
        val t = threat(bearingDeg = 90.0, heading = 200.0, explanationShort = "Курс на Київ")
        assertEquals(90.0, t.courseDeg, 1e-9)
    }

    @Test
    fun `inOblast matches on oblast or name prefix`() {
        val alert = OblastAlert("kyiv", "Київ", "Київська", "2026-08-14")
        assertTrue(alert.inOblast("Київ"))
        assertTrue(alert.inOblast("київськ")) // case-insensitive prefix
        assertFalse(alert.inOblast("Одес"))
    }
}