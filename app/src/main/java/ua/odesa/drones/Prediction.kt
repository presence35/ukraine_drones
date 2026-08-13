package ua.odesa.drones

import org.osmdroid.util.GeoPoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Simple lat/lon value (independent of any mapping library). */
data class LatLng(val lat: Double, val lon: Double)

/** Where a speed estimate came from — affects how it's labelled in the popup. */
enum class SpeedSource { RECORDED, TYPICAL }

/** Approximate meters between two lat/lon points (equirectangular — fine for short distances). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1) * 110_574.0
    val dLon = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
    return sqrt(dLat * dLat + dLon * dLon)
}

/**
 * Per-type dead-reckoning limits, mirroring NEPTUN's SDK `predict()`:
 * [horizonSec] is how long we keep gliding a marker after its last confirmed fix,
 * [maxGhostMeters] caps how far it may run ahead of that fix.
 */
private data class FlyParams(val horizonSec: Double, val maxGhostMeters: Double)

private fun flyParams(type: ThreatType): FlyParams = when (type) {
    ThreatType.SHAHED -> FlyParams(300.0, 18_000.0)          // uav — 5 min / 18 km
    ThreatType.FPV_LOITERING -> FlyParams(300.0, 18_000.0)   // treated as uav
    ThreatType.RECON -> FlyParams(300.0, 12_000.0)
    ThreatType.CRUISE_MISSILE -> FlyParams(180.0, 30_000.0)  // missile — 3 min / 30 km
    ThreatType.KAB -> FlyParams(180.0, 10_000.0)
    ThreatType.BALLISTIC -> FlyParams(90.0, 20_000.0)
    ThreatType.AVIATION -> FlyParams(240.0, 24_000.0)        // mig31k — 4 min / 24 km
    ThreatType.UNKNOWN -> FlyParams(240.0, 10_000.0)
}

/**
 * How long a threat of this type is considered live after its last fix. After this window
 * it's treated as stale and dropped from the map/counts/alerts — a missile is never in the
 * region for more than a few minutes, so 10+ minute ghosts are noise.
 */
fun staleAfterMs(type: ThreatType): Long = when (type) {
    ThreatType.BALLISTIC -> 90_000L
    ThreatType.CRUISE_MISSILE, ThreatType.KAB -> 180_000L
    ThreatType.AVIATION -> 240_000L
    ThreatType.SHAHED, ThreatType.FPV_LOITERING, ThreatType.RECON, ThreatType.UNKNOWN -> 300_000L
}

/** True when the threat's last confirmed fix is older than its per-type staleness window. */
fun isExpired(t: Threat, now: Long): Boolean {
    val updated = t.updatedAtMillis ?: return false
    return now - updated > staleAfterMs(t.type)
}

/**
 * Predict a threat's current position by advancing from its last confirmed fix along its
 * course at the estimated speed, within NEPTUN's per-type fly horizon. Mirrors the SDK's
 * `predict()`: it only dead-reckons tracks that carry a real velocity + confirmed fix and are
 * active — everything else returns null so the caller keeps the raw fix. Anchors strictly on
 * `confirmedAtMillis` (the dead-reckon anchor), never on `updatedAtMillis`.
 */
fun predictPosition(t: Threat, speedMps: Double, nowMillis: Long): GeoPoint? {
    if (!t.flying) return null
    val heading = t.bearingDeg ?: return null
    val confirmedAt = t.confirmedAtMillis ?: return null
    var elapsedSec = (nowMillis - confirmedAt) / 1000.0
    if (elapsedSec < 0) return null
    val params = flyParams(t.type)
    elapsedSec = minOf(elapsedSec, params.horizonSec)
    val dist = minOf(speedMps * elapsedSec, params.maxGhostMeters)
    val rad = Math.toRadians(heading)
    val dLat = dist * cos(rad) / 110_574.0
    val dLon = dist * sin(rad) / (111_320.0 * cos(Math.toRadians(t.lat)).coerceAtLeast(0.01))
    return GeoPoint(t.lat + dLat, t.lon + dLon)
}

/**
 * Tracks consecutive fixes per threat to estimate ground speed for position smoothing.
 * Prefers the server's speedKmh (NEPTUN's SDK predicts purely from velocity.speedKmh),
 * then measured speed (consecutive fixes / trail timestamps), then a per-type nominal.
 */
class ThreatSpeedTracker {
    private data class Fix(val t: Long, val lat: Double, val lon: Double)
    private val fixes = HashMap<String, ArrayDeque<Fix>>()

    fun record(id: String, t: Long, lat: Double, lon: Double) {
        val q = fixes.getOrPut(id) { ArrayDeque() }
        val last = q.lastOrNull()
        if (last != null && last.t == t) return
        q.addLast(Fix(t, lat, lon))
        while (q.size > 4) q.removeFirst()
    }

    fun estimate(id: String, t: Threat): Double? =
        estimateWithSource(id, t)?.first

    /** Speed plus where it came from: server/measured fixes = RECORDED, per-type = TYPICAL. */
    fun estimateWithSource(id: String, t: Threat): Pair<Double, SpeedSource>? {
        t.speedKmh?.let { s -> if (s in 5.0..2000.0) return s / 3.6 to SpeedSource.RECORDED }
        val q = fixes[id]
        if (q != null && q.size >= 2) {
            val a = q[q.size - 2]
            val b = q[q.size - 1]
            val dt = (b.t - a.t) / 1000.0
            if (dt in 5.0..600.0) {
                val v = distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt
                if (v in 5.0..400.0) return v to SpeedSource.RECORDED
            }
        }
        if (t.trail.size >= 2) {
            val a = t.trail[t.trail.size - 2]
            val b = t.trail[t.trail.size - 1]
            if (a.tMillis != null && b.tMillis != null) {
                val dt = (b.tMillis - a.tMillis) / 1000.0
                if (dt in 5.0..600.0) {
                    val v = distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt
                    if (v in 5.0..400.0) return v to SpeedSource.RECORDED
                }
            }
        }
        return nominalSpeedMps(t.type)?.let { it to SpeedSource.TYPICAL }
    }

    private fun nominalSpeedMps(type: ThreatType): Double? = NOMINAL_SPEED_MPS[type]
}

/** Per-type nominal speeds in m/s — the single source for "typical speed" labels and dead-reckoning. */
private val NOMINAL_SPEED_MPS: Map<ThreatType, Double> = mapOf(
    ThreatType.BALLISTIC to 916.67,         // 3300 km/h
    ThreatType.CRUISE_MISSILE to 236.11,    // 850 km/h
    ThreatType.KAB to 250.0,                // 900 km/h
    ThreatType.AVIATION to 250.0,           // 900 km/h
    ThreatType.SHAHED to 50.0,              // 180 km/h
    ThreatType.FPV_LOITERING to 33.33,      // 120 km/h
    ThreatType.RECON to 22.22               // 80 km/h
)

/** Typical cruise speed of a threat type in km/h (null for types with no nominal), used for UI labels. */
fun typicalSpeedKmh(type: ThreatType): Double? = NOMINAL_SPEED_MPS[type]?.times(3.6)
