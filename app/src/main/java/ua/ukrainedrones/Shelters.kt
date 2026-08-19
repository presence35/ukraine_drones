package ua.ukrainedrones

import org.json.JSONObject

/** One protective structure from the Odesa city shelter layer (map-shelter, type=36). */
data class Shelter(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
) {
    /** Straight-line distance to a query point, in meters (Haversine). */
    fun distanceMeters(fromLat: Double, fromLon: Double): Double =
        distanceMeters(fromLat, fromLon, lat, lon)
}

/** Walking speed at a brisk adult pace (~5 km/h). */
private const val ADULT_M_PER_MIN = 83.3334
/** Walking speed with small children (~3 km/h). */
private const val KID_M_PER_MIN = 50.0

/** English display name for a Ukrainian shelter name: glossary translation, then transliteration. */
fun shelterNameEn(ua: String): String {
    var s = ua
    SHELTER_NAME_GLOSSARY.entries
        .sortedByDescending { it.key.length }
        .forEach { (uaPart, enPart) ->
            s = s.replace(uaPart, enPart, ignoreCase = true)
        }
    val capitalized = ua.isNotEmpty() && ua[0].isUpperCase() &&
        s.isNotEmpty() && s[0].isLowerCase()
    val out = Transliteration.transliterate(s)
    return if (capitalized) out.replaceFirstChar { it.uppercase() } else out
}

/** A shelter paired with its straight-line distance to a query point. */
data class NearestShelter(
    val shelter: Shelter,
    val distanceMeters: Double
) {
    /** Walking time at a brisk adult pace (~5 km/h). */
    val walkMinutesAdult: Int get() = ceilMinutes(distanceMeters, ADULT_M_PER_MIN)
    /** Walking time with small children (~3 km/h). */
    val walkMinutesKid: Int get() = ceilMinutes(distanceMeters, KID_M_PER_MIN)

    private fun ceilMinutes(meters: Double, speedMetersPerMin: Double): Int =
        if (meters <= 0) 0 else kotlin.math.ceil(meters / speedMetersPerMin - 1e-9).toInt()
}

/**
 * In-memory index of Odesa shelters, parsed from the city's compact
 * [id, "lat,lon", icon, name, flag] JSON array. Immutable once built, so it is
 * safe to share across the UI. Rows with empty or out-of-city coordinates are dropped.
 */
class ShelterIndex private constructor(
    private val shelters: List<Shelter>
) {
    val size: Int get() = shelters.size

    /** Whether a point falls inside the city's shelter coverage. */
    fun withinRegion(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON

    /** Nearest [limit] shelters to the given point, closest first. */
    fun nearest(fromLat: Double, fromLon: Double, limit: Int = 20): List<NearestShelter> =
        shelters
            .map { NearestShelter(it, it.distanceMeters(fromLat, fromLon)) }
            .sortedBy { it.distanceMeters }
            .take(limit.coerceAtLeast(1))

    companion object {
        // Odesa city bounding box, generous around the published dataset extent.
        private const val MIN_LAT = 46.20
        private const val MAX_LAT = 46.70
        private const val MIN_LON = 30.45
        private const val MAX_LON = 30.95

        /** Parses the city's JSON payload; null when unreadable. */
        fun fromJson(json: String): ShelterIndex? = runCatching {
            val data = JSONObject(json).getJSONArray("data")
            val list = ArrayList<Shelter>(data.length())
            for (i in 0 until data.length()) {
                val row = data.getJSONArray(i)
                val coordStr = row.optString(1)
                val comma = coordStr.indexOf(',')
                if (comma <= 0) continue
                val lat = coordStr.substring(0, comma).trim().toDoubleOrNull() ?: continue
                val lon = coordStr.substring(comma + 1).trim().toDoubleOrNull() ?: continue
                if (lat !in MIN_LAT..MAX_LAT || lon !in MIN_LON..MAX_LON) continue
                list += Shelter(row.optString(0), row.optString(3), lat, lon)
            }
            ShelterIndex(list)
        }.getOrNull()
    }
}