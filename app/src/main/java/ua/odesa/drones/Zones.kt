package ua.odesa.drones

/** The two alert tiers, most specific first: INNER (red zone) then OUTER (yellow zone). */
enum class ThreatZone { INNER, OUTER }

/**
 * GPS-centered concentric alert radii in km: the red circle inside the yellow ring.
 * The user sets red 1–5 km and yellow 6–10 km; a threat inside the red circle is INNER,
 * between red and yellow is OUTER, beyond yellow is outside both.
 */
data class RadialZones(val redKm: Int, val yellowKm: Int)

/** Which tier a threat at [distKm] from the user falls in (null = outside both zones). */
fun radialZone(distKm: Double, zones: RadialZones): ThreatZone? = when {
    distKm <= zones.redKm -> ThreatZone.INNER
    distKm <= zones.yellowKm -> ThreatZone.OUTER
    else -> null
}

/**
 * The zone a threat is *presented* as, honoring "fast objects alert sooner": a fast object in
 * the yellow ring claims the urgent tier at once, since its 20-km and 1-km ETAs differ only by
 * seconds. Used everywhere a zone is shown (banner, counts, marker ring, popup, notifications).
 */
fun effectiveZone(t: Threat, spatial: ThreatZone, fastAlertsSooner: Boolean): ThreatZone =
    if (fastAlertsSooner && spatial == ThreatZone.OUTER && t.type in FAST_THREAT_TYPES) ThreatZone.INNER
    else spatial
