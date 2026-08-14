package ua.ukrainedrones

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/** A place name drawn on the map. `major` cities always show; `minor` only at higher zoom. */
data class City(
    val nameEn: String,
    val nameUa: String,
    val lat: Double,
    val lon: Double,
    val major: Boolean
)

/**
 * Curated list of major + minor cities (no hoods/villages) so users get a sense of
 * distance/scale while zooming. Names are drawn in the current UI language, replacing
 * the (Cyrillic, non-English) labels baked into the basemap tiles.
 */
object Cities {
    val ALL: List<City> = listOf(
        // Major national cities
        City("Kyiv", "Київ", 50.4501, 30.5234, true),
        City("Odesa", "Одеса", 46.4825, 30.7233, true),
        City("Lviv", "Львів", 49.8397, 24.0297, true),
        City("Dnipro", "Дніпро", 48.4647, 35.0462, true),
        City("Kharkiv", "Харків", 49.9935, 36.2304, true),
        City("Zaporizhzhia", "Запоріжжя", 47.8388, 35.1396, true),
        City("Vinnytsia", "Вінниця", 49.2331, 28.4682, true),
        City("Mykolaiv", "Миколаїв", 46.9750, 31.9946, true),
        City("Kherson", "Херсон", 46.6354, 32.6169, true),
        City("Kryvyi Rih", "Кривий Ріг", 47.9105, 33.3918, true),
        City("Kropyvnytskyi", "Кропивницький", 48.5079, 32.2603, true),
        City("Poltava", "Полтава", 49.5883, 34.5514, true),
        City("Cherkasy", "Черкаси", 49.4444, 32.0598, true),
        City("Uman", "Умань", 48.7484, 30.2211, true),
        City("Khmelnytskyi", "Хмельницький", 49.4220, 26.9871, true),
        City("Zhytomyr", "Житомир", 50.2546, 28.6587, true),
        City("Rivne", "Рівне", 50.6199, 26.2516, true),
        City("Chernivtsi", "Чернівці", 48.2917, 25.9352, true),
        City("Ivano-Frankivsk", "Івано-Франківськ", 48.9226, 24.7111, true),
        City("Ternopil", "Тернопіль", 49.5535, 25.5948, true),
        City("Sumy", "Суми", 50.9077, 34.7981, true),
        City("Chernihiv", "Чернігів", 51.4982, 31.2893, true),
        City("Donetsk", "Донецьк", 48.0159, 37.8029, true),
        City("Luhansk", "Луганськ", 48.5740, 39.3078, true),
        City("Uzhhorod", "Ужгород", 48.6208, 22.2879, true),
        City("Lutsk", "Луцьк", 50.7472, 25.3254, true),
        // Minor — Odesa region + neighbours, for distance context at higher zoom
        City("Chornomorsk", "Чорноморськ", 46.3036, 30.6566, false),
        City("Yuzhne", "Южне", 46.6226, 31.1014, false),
        City("Bilhorod-Dnistrovskyi", "Білгород-Дністровський", 46.1947, 30.3484, false),
        City("Izmail", "Ізмаїл", 45.3503, 28.8348, false),
        City("Reni", "Рені", 45.4571, 28.2867, false),
        City("Kiliya", "Кілія", 45.4552, 29.2637, false),
        City("Ovidiopol", "Овідіополь", 46.2450, 30.4413, false),
        City("Biliaivka", "Біляївка", 46.4829, 30.1985, false),
        City("Berezivka", "Березівка", 47.2044, 30.9093, false),
        City("Rozdilna", "Роздільна", 46.8493, 30.0833, false),
        City("Artsyz", "Арциз", 45.9919, 29.4183, false),
        City("Tatarbunary", "Татарбунари", 45.8527, 29.6144, false),
        City("Podilsk", "Подільськ", 47.7433, 29.5350, false),
        City("Voznesensk", "Вознесенськ", 47.5653, 31.3311, false),
        City("Pervomaisk", "Первомайськ", 48.0446, 30.8506, false),
        City("Balta", "Балта", 47.9364, 29.6226, false)
    )

    /** Ukrainian → English place-name lookup, used when translating NEPTUN's course text. */
    val uaToEn: Map<String, String> = ALL.associate { it.nameUa to it.nameEn }

    /**
     * City (by Ukrainian name) → its oblast name stem, used to highlight a city label in red
     * while an official air-raid alert is active for that oblast. Matched via `contains`
     * against the alert's oblast/name (e.g. stem "Харківськ" hits "Харківська область").
     */
    val cityOblast: Map<String, String> = mapOf(
        "Київ" to "Київськ",
        "Одеса" to "Одеськ",
        "Львів" to "Львівськ",
        "Дніпро" to "Дніпропетровськ",
        "Харків" to "Харківськ",
        "Запоріжжя" to "Запорізьк",
        "Вінниця" to "Вінницьк",
        "Миколаїв" to "Миколаївськ",
        "Херсон" to "Херсонськ",
        "Кривий Ріг" to "Дніпропетровськ",
        "Кропивницький" to "Кіровоградськ",
        "Полтава" to "Полтавськ",
        "Черкаси" to "Черкаськ",
        "Умань" to "Черкаськ",
        "Хмельницький" to "Хмельницьк",
        "Житомир" to "Житомирськ",
        "Рівне" to "Рівненськ",
        "Чернівці" to "Чернівецьк",
        "Івано-Франківськ" to "Івано-Франківськ",
        "Тернопіль" to "Тернопільськ",
        "Суми" to "Сумськ",
        "Чернігів" to "Чернігівськ",
        "Донецьк" to "Донецьк",
        "Луганськ" to "Луганськ",
        "Ужгород" to "Закарпатськ",
        "Луцьк" to "Волинськ",
        "Чорноморськ" to "Одеськ",
        "Южне" to "Одеськ",
        "Білгород-Дністровський" to "Одеськ",
        "Ізмаїл" to "Одеськ",
        "Рені" to "Одеськ",
        "Кілія" to "Одеськ",
        "Овідіополь" to "Одеськ",
        "Біляївка" to "Одеськ",
        "Березівка" to "Одеськ",
        "Роздільна" to "Одеськ",
        "Арциз" to "Одеськ",
        "Татарбунари" to "Одеськ",
        "Подільськ" to "Одеськ",
        "Балта" to "Одеськ",
        "Вознесенськ" to "Миколаївськ",
        "Первомайськ" to "Миколаївськ"
    )

    /**
     * Nearest listed city within [radiusKm] of a GPS position, used to attribute a follow-me
     * location to an oblast for official-alert matching without depending on a geocoder.
     */
    fun nearestCity(lat: Double, lon: Double, radiusKm: Double = 70.0): City? {
        var best: City? = null
        var bestM = radiusKm * 1000.0
        for (c in ALL) {
            val d = distanceMeters(lat, lon, c.lat, c.lon)
            if (d < bestM) {
                bestM = d
                best = c
            }
        }
        return best
    }
}

/** Oblast attribution + banner city for the focus point: the pinned city, else nearest to GPS. */
data class FocusAttribution(
    val token: String?,
    val bannerCityUa: String,
    val bannerCityEn: String
)

fun focusAttribution(followMe: Boolean, userLocation: LatLng?, pinned: City?): FocusAttribution {
    if (!followMe && pinned != null) {
        return FocusAttribution(
            token = Cities.cityOblast[pinned.nameUa],
            bannerCityUa = pinned.nameUa,
            bannerCityEn = pinned.nameEn
        )
    }
    val gps = userLocation?.let { Cities.nearestCity(it.lat, it.lon) }
    return if (gps != null) {
        FocusAttribution(
            token = Cities.cityOblast[gps.nameUa],
            bannerCityUa = gps.nameUa,
            bannerCityEn = gps.nameEn
        )
    } else {
        FocusAttribution(null, "Одеса", "Odesa")
    }
}

/** Draws city names in the current language, sized to zoom level. */
class CityLabelOverlay(
    context: Context,
    private val lang: AppLanguage,
    private val activeRegionTokens: Set<String> = emptySet(),
    private val cityCounts: Map<String, Int> = emptyMap()
) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint().apply {
        isAntiAlias = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }
    private val reuse = android.graphics.Point()

    private fun name(c: City) = if (lang == AppLanguage.UA) c.nameUa else c.nameEn

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val zoom = mapView.zoomLevelDouble.coerceAtLeast(0.0)
        for (c in Cities.ALL) {
            if (c.major) {
                if (zoom < 4.0) continue
            } else {
                if (zoom < 10.0) continue
            }
            mapView.projection.toPixels(GeoPoint(c.lat, c.lon), reuse)
            if (reuse.x < -240 || reuse.x > canvas.width + 240 ||
                reuse.y < -60 || reuse.y > canvas.height + 60
            ) continue
            paint.textSize = (if (c.major) {
                (10.5 + (zoom - 4.0) * 1.2).coerceIn(10.5, 17.0)
            } else {
                (8.5 + (zoom - 10.0) * 0.7).coerceIn(8.5, 13.0)
            }).toFloat() * density
            val token = Cities.cityOblast[c.nameUa]
            paint.color = if (token != null && token in activeRegionTokens) {
                Color.argb(255, 211, 47, 47)
            } else {
                Color.argb(230, 235, 235, 235)
            }
            val count = cityCounts[c.nameUa] ?: 0
            val label = if (count > 0) "${name(c)} ($count)" else name(c)
            canvas.drawText(label, reuse.x.toFloat(), reuse.y.toFloat() - 6f * density, paint)
        }
    }
}
