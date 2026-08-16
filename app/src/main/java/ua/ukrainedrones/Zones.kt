package ua.ukrainedrones

/** The two alert tiers, most specific first: INNER (urgent siren) then OUTER (warning chime). */
enum class ThreatZone { INNER, OUTER }

/** Fast-moving threats (missiles/bombs) that tier by time-to-arrival; everything else is slow. */
internal val FastThreatTypes = setOf(
    ThreatType.BALLISTIC,
    ThreatType.CRUISE_MISSILE,
    ThreatType.AVIATION,
    ThreatType.KAB
)

/**
 * Alert-zone thresholds. Slow threats tier by distance from the focus point ([slowRedKm] INNER /
 * [slowYellowKm] OUTER); fast threats tier by time-to-arrival ([fastRedMin] INNER /
 * [fastYellowMin] OUTER). Defaults: slow red 60 km, slow yellow 180 km, fast red 10 min,
 * fast yellow 30 min.
 */
data class ZoneParams(
    val slowRedKm: Int,
    val slowYellowKm: Int,
    val fastRedMin: Int,
    val fastYellowMin: Int
)

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
 * override (MiG-31K's Kinzhal is country-wide regardless of the plane's 900 km/h). Fast
 * threats tier by ETA, slow threats by plain distance. This is the single source of truth for
 * zone tiering — used by both MainViewModel and AlertService.
 */
fun zoneTier(t: Threat, distKm: Double, speedKmh: Double?, params: ZoneParams): ThreatZone? {
    if (distKm > reachKm(t.type)) return null
    if (t.type in FastThreatTypes) {
        val speed = if (t.type == ThreatType.AVIATION) BALLISTIC_SPEED_KMH else speedKmh
        val eta = etaMinutes(distKm, speed) ?: return null
        return when {
            eta <= params.fastRedMin -> ThreatZone.INNER
            eta <= params.fastYellowMin -> ThreatZone.OUTER
            else -> null
        }
    }
    return when {
        distKm <= params.slowRedKm -> ThreatZone.INNER
        distKm <= params.slowYellowKm -> ThreatZone.OUTER
        else -> null
    }
}
