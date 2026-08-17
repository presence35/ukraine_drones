package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {

    @Test
    fun `no duplicate city names`() {
        val names = Cities.ALL.map { it.nameUa }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `every city has an oblast attribution`() {
        for (c in Cities.ALL) {
            assertTrue("missing cityOblast for ${c.nameUa}", Cities.cityOblast.containsKey(c.nameUa))
        }
    }

    @Test
    fun `en names derive from transliteration and are non-empty`() {
        for (c in Cities.ALL) {
            assertEquals(Transliteration.transliterate(c.nameUa), c.nameEn)
            assertTrue(c.nameEn.isNotBlank())
        }
    }

    @Test
    fun `list is large enough for country-scale context`() {
        val majors = Cities.ALL.count { it.major }
        val minors = Cities.ALL.count { !it.major }
        assertEquals(26, majors)
        assertTrue("expected ~300+ minors, got $minors", minors >= 300)
    }

    @Test
    fun `nearestCity never returns a minor`() {
        // Stand exactly on the minor city Chornomorsk; attribution must still land on Odesa.
        val near = Cities.nearestCity(46.3036, 30.6566)
        assertEquals("Одеса", near?.nameUa)
        assertTrue(near?.major == true)
    }

    @Test
    fun `focusAttribution near a minor city uses the major banner`() {
        val fa = focusAttribution(
            followMe = true,
            userLocation = LatLng(46.3036, 30.6566),
            pinned = null
        )
        assertEquals("Одеськ", fa.token)
        assertEquals("Одеса", fa.bannerCityUa)
        assertEquals("Odesa", fa.bannerCityEn)
    }

    @Test
    fun `focusAttribution without location falls back to no city`() {
        val fa = focusAttribution(
            followMe = true,
            userLocation = null,
            pinned = null
        )
        assertNull(fa.token)
        assertEquals("", fa.bannerCityUa)
        assertEquals("", fa.bannerCityEn)
    }
}