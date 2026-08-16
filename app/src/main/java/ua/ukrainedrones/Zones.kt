package ua.ukrainedrones

/** The two alert tiers, most specific first: INNER (urgent siren) then OUTER (warning chime). */
enum class ThreatZone { INNER, OUTER }

/**
 * Time-to-arrival alert thresholds in minutes around the focus point. A threat whose ETA
 * is within [redMin] is INNER (urgent siren), within [yellowMin] is OUTER (warning chime),
 * beyond that is outside both. Defaults: red 20 min, yellow 60 min.
 */
data class TimeZones(val redMin: Int, val yellowMin: Int)

/** Which tier a threat with an ETA of [etaMin] minutes falls in (null = outside both). */
fun timeZone(etaMin: Double?, zones: TimeZones): ThreatZone? = when {
    etaMin == null -> null
    etaMin <= zones.redMin -> ThreatZone.INNER
    etaMin <= zones.yellowMin -> ThreatZone.OUTER
    else -> null
}

/** Ballistic nominal speed (km/h) — also the AVIATION tier speed, since a Kinzhal is country-wide. */
const val BALLISTIC_SPEED_KMH = 3300.0

/**
 * Minutes for an object at [distKm] to reach the focus while flying at [speedKmh].
 * Null when there's no usable speed.
 */
fun etaMinutes(distKm: Double, speedKmh: Double?): Double? {
    if (speedKmh == null || speedKmh <= 0.0) return null
    return distKm / speedKmh * 60.0
}

/**
 * Maximum distance (km) a threat type can physically cover — beyond it the object can't
 * reach the focus point, so it never alerts (kills east-front KAB/FPV noise for distant users).
 */
fun reachKm(type: ThreatType): Double = when (type) {
    ThreatType.KAB -> 70.0
    ThreatType.FPV_LOITERING -> 40.0
    ThreatType.RECON -> 50.0
    ThreatType.SHAHED -> 1000.0
    else -> 1500.0 // ballistic, cruise, aviation, unknown — country-scale
}

/**
 * The alert tier for a threat at [distKm] from the focus with speed [speedKmh] (server →
 * measured → nominal estimate). Honors per-type reach caps and the AVIATION→ballistic speed
 * override (MiG-31K's Kinzhal is country-wide regardless of the plane's 900 km/h). This is
 * the single source of truth for zone tiering — used by both MainViewModel and AlertService.
 */
fun timeTier(t: Threat, distKm: Double, speedKmh: Double?, zones: TimeZones): ThreatZone? {
    if (distKm > reachKm(t.type)) return null
    val speed = if (t.type == ThreatType.AVIATION) BALLISTIC_SPEED_KMH else speedKmh
    return timeZone(etaMinutes(distKm, speed), zones)
}

/**
 * Slow-threat reference speed (km/h) for the map's zone circles — the Shahed nominal. The
 * circles show where a *slow* object becomes urgent, so fast objects legitimately alert from
 * farther out than the drawn circle.
 */
const val ZONE_CIRCLE_REF_KMH = 180.0

/** Radius (km) of the red/yellow zone circle for a [minute] threshold, at the Shahed reference speed. */
fun zoneCircleKm(minutes: Int): Double = minutes * ZONE_CIRCLE_REF_KMH / 60.0