package ua.ukrainedrones.engine

import ua.ukrainedrones.Reliability
import ua.ukrainedrones.Threat
import ua.ukrainedrones.ThreatType

fun ThreatType.toEngineString(): String = when (this) {
    ThreatType.SHAHED -> "shahed"
    ThreatType.FPV_LOITERING -> "fpv"
    ThreatType.CRUISE_MISSILE -> "cruise"
    ThreatType.BALLISTIC -> "ballistic"
    ThreatType.KAB -> "kab"
    ThreatType.AVIATION -> "aviation"
    ThreatType.RECON -> "recon"
    ThreatType.UNKNOWN -> "unknown"
}

fun Threat.toNormalizedThreat(): NormalizedThreat = NormalizedThreat(
    id = id,
    type = type.toEngineString(),
    title = title,
    region = region,
    district = district,
    locality = locality,
    lat = lat,
    lon = lon,
    heading = heading,
    bearingDeg = bearingDeg,
    status = status,
    advisory = advisory,
    areaOnly = areaOnly,
    confirmations = confirmations,
    reliability = reliability.name,
    count = count,
    explanationShort = explanationShort,
    speedKmh = speedKmh,
    uncertaintyKm = uncertaintyKm,
    positionQuality = positionQuality,
    confirmedAtMillis = confirmedAtMillis,
    updatedAtMillis = updatedAtMillis,
    trail = trail.map { TrailPoint(it.lat, it.lon, it.tMillis) }
)

fun NormalizedThreat.toThreat(): Threat = Threat(
    id = id,
    type = ThreatType.fromApi(type),
    title = title,
    region = region,
    district = district,
    locality = locality,
    lat = lat,
    lon = lon,
    heading = heading,
    bearingDeg = bearingDeg,
    status = status,
    advisory = advisory,
    areaOnly = areaOnly,
    confirmations = confirmations,
    reliability = runCatching { Reliability.valueOf(reliability) }.getOrDefault(Reliability.UNKNOWN),
    count = count,
    explanationShort = explanationShort,
    speedKmh = speedKmh,
    uncertaintyKm = uncertaintyKm,
    positionQuality = positionQuality,
    confirmedAt = null,
    confirmedAtMillis = confirmedAtMillis,
    updatedAt = null,
    updatedAtMillis = updatedAtMillis,
    trail = trail.map { ua.ukrainedrones.TrailPoint(it.lat, it.lon, it.tMillis) }
)
