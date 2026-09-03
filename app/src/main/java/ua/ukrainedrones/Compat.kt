package ua.ukrainedrones

import ua.ukrainedrones.engine.NEPTUN_TYPES
import ua.ukrainedrones.engine.ThreatEngine
import ua.ukrainedrones.engine.ThreatZone as EngineThreatZone
import ua.ukrainedrones.engine.ZoneParams as EngineZoneParams

typealias ThreatZone = EngineThreatZone
typealias ZoneParams = EngineZoneParams

val FastThreatTypes: Set<ThreatType> = setOf(
    ThreatType.CRUISE_MISSILE, ThreatType.BALLISTIC, ThreatType.KAB, ThreatType.AVIATION
)

fun reachKm(type: ThreatType): Double =
    NEPTUN_TYPES[type.name.lowercase()]?.reachKm ?: 1500.0

private val compatEngine = ThreatEngine(NEPTUN_TYPES)

fun zoneTier(t: Threat, distKm: Double, speedKmh: Double?, params: ZoneParams): ThreatZone? {
    val props = NEPTUN_TYPES[t.type.name.lowercase()] ?: return null
    return compatEngine.zoneTier(props, distKm, speedKmh, params)
}
