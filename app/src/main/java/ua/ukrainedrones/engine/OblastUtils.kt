package ua.ukrainedrones.engine

import ua.ukrainedrones.AppLanguage
import ua.ukrainedrones.Cities
import ua.ukrainedrones.Threat
import ua.ukrainedrones.ThreatTypeCatalog
import ua.ukrainedrones.Transliteration
import ua.ukrainedrones.FocusCityInfo

fun inOblast(region: String?, district: String?, locality: String?, token: String?): Boolean {
    if (token == null) return false
    return (region != null && inOblastText(region, token)) ||
        (district != null && inOblastText(district, token)) ||
        (locality != null && inOblastText(locality, token))
}

private fun inOblastText(text: String, token: String): Boolean =
    text.startsWith(token, ignoreCase = true) || Cities.cityOblast[text] == token

fun inFocusOblast(t: Threat, token: String?): Boolean {
    if (token == null) return false
    return inOblast(t.region, t.district, t.locality, token)
}

fun threatBody(t: Threat, lang: AppLanguage): String {
    val info = ThreatTypeCatalog.INFO.getValue(t.type)
    val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    val where = t.locality ?: t.district ?: t.region
    val whereText = if (where == null) null else if (lang == AppLanguage.UA) where
    else Cities.byUa[where]?.nameEn ?: Transliteration.transliterate(where)
    return if (whereText != null) "$label — $whereText" else label
}

fun matchOblast(lat: Double, lon: Double): OblastMatch? {
    val city = Cities.nearestCity(lat, lon) ?: return null
    val stem = Cities.cityOblast[city.nameUa] ?: return null
    return OblastMatch(stem, city.nameUa, city.nameEn)
}

fun canonicalToken(region: String): String? {
    if (region.isBlank()) return null
    val trimmed = region.trim()
    val idx = trimmed.indexOf(' ')
    val stem = if (idx > 0) trimmed.substring(0, idx) else trimmed
    return stem.ifBlank { null }
}

fun isCityScopedSuppressed(city: FocusCityInfo, threats: List<Threat>): Boolean {
    if (threats.isEmpty()) return false
    val stem = city.oblastStem ?: return false
    return threats.none { t ->
        t.status == "active" && !t.advisory && !t.areaOnly &&
            (t.region?.contains(stem, ignoreCase = true) == true ||
                t.locality?.contains(city.nameUa, ignoreCase = true) == true)
    }
}

data class OblastMatch(
    val stem: String,
    val nameUa: String,
    val nameEn: String
)
