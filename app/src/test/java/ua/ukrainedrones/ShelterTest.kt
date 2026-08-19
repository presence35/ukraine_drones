package ua.ukrainedrones

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelterTest {

    private fun payload(vararg rows: Array<Any?>): String {
        val data = JSONArray()
        rows.forEach { data.put(JSONArray(listOf(it[0], it[1], it[2], it[3], it[4]))) }
        return "{\"result\":\"OK\",\"data\":$data}"
    }

    @Test
    fun `parses the city payload shape and drops bad rows`() {
        val json = payload(
            arrayOf(1, "46.48, 30.73", "icon1", "Вулиця Дерибасівська, 1", false),
            arrayOf(2, "46.50,30.74", "icon2", "ЗСЦЗ №00001", false),
            arrayOf(3, "", "icon3", "no coords", false),                    // dropped: empty
            arrayOf(4, "abc,def", "icon4", "garbage", false),               // dropped: unparsable
            arrayOf(5, "50.45,30.52", "icon5", "Київ", false),              // dropped: outside Odesa bbox
            arrayOf(6, "46.48, 30.73", "icon6", "Дублікат координат", false)
        )
        val index = ShelterIndex.fromJson(json)
        assertNotNull(index)
        assertEquals(3, index!!.size)
        assertEquals("Вулиця Дерибасівська, 1", index.nearest(46.48, 30.73, 1).single().shelter.name)
    }

    @Test
    fun `fromJson returns null on unreadable input`() {
        assertNull(ShelterIndex.fromJson("not json"))
        assertNull(ShelterIndex.fromJson("{\"result\":\"OK\"}"))
    }

    @Test
    fun `nearest ranks by distance and honors the limit`() {
        val index = ShelterIndex.fromJson(
            payload(
                arrayOf(1, "46.50,30.70", "i", "далеко", false),
                arrayOf(2, "46.48,30.73", "i", "близько", false),
                arrayOf(3, "46.50,30.75", "i", "середньо", false)
            )
        )!!
        val top = index.nearest(46.48, 30.73, 2)
        assertEquals(listOf("близько", "середньо"), top.map { it.shelter.name })
        assertTrue(top.first().distanceMeters < top.last().distanceMeters)
        assertEquals(2, top.size)
    }

    @Test
    fun `withinRegion respects the Odesa bbox`() {
        val index = ShelterIndex.fromJson(payload(arrayOf(1, "46.48,30.73", "i", "x", false)))!!
        assertTrue(index.withinRegion(46.48, 30.73))
        assertTrue(!index.withinRegion(50.45, 30.52))
    }

    @Test
    fun `walk times scale with speed and distance`() {
        val near = NearestShelter(Shelter("1", "x", 46.48, 30.73), 500.0)
        // 500 m at ~5 km/h = 6 min; at ~3 km/h = 10 min.
        assertEquals(6, near.walkMinutesAdult)
        assertEquals(10, near.walkMinutesKid)
        // A far shelter is strictly slower than the same distance covered by an adult.
        val far = NearestShelter(Shelter("2", "y", 46.48, 30.73), 5000.0)
        assertTrue(far.walkMinutesKid > far.walkMinutesAdult)
        assertEquals(0, NearestShelter(Shelter("3", "z", 46.48, 30.73), 0.0).walkMinutesAdult)
    }

    @Test
    fun `distance matches the top-level haversine`() {
        val shelter = Shelter("1", "x", 46.53, 30.73)
        val expect = distanceMeters(46.48, 30.73, 46.53, 30.73)
        assertEquals(expect, shelter.distanceMeters(46.48, 30.73), 1e-9)
    }
}
