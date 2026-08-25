package ua.ukrainedrones

import androidx.compose.runtime.Immutable
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Simple lat/lon value (independent of any mapping library). */
@Immutable
data class LatLng(val lat: Double, val lon: Double)

/** Where a speed estimate came from — affects how it's labelled in the popup. */
enum class SpeedSource { RECORDED, TYPICAL }

/** Approximate meters between two lat/lon points (equirectangular — fine for short distances). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1) * 110_574.0
    val dLon = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
    return sqrt(dLat * dLat + dLon * dLon)
}

/** Initial bearing in degrees clockwise from north (0..360) from (lat1, lon1) toward (lat2, lon2),
 *  on the same equirectangular metre basis as [distanceMeters]. Good enough for course-facing
 *  icons (a UAV "heading toward" a city) over the distances these threats cover. */
fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1) * 110_574.0
    val dLon = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
    val deg = Math.toDegrees(atan2(dLon, dLat))
    return (deg + 360.0) % 360.0
}

/**
 * Time in minutes for a point moving from [from] along [bearingDeg] at [speedMps] to cross the
 * boundary of the circle of radius [radiusM] centered at [center]. Null when the point is already
 * inside the circle, heading away from it, or has no usable speed. Solves the ray–circle
 * intersection: the positive root `d = (v·u) + sqrt((v·u)^2 - (D^2 - R^2))` is the distance to the
 * near boundary along the heading, where `v` = from − center, `u` = unit heading vector,
 * `D` = distance to center. Returns minutes = `d / speed / 60`.
 */
fun etaToCircleEdgeMinutes(
    from: LatLng,
    center: LatLng,
    radiusM: Double,
    bearingDeg: Double,
    speedMps: Double
): Double? {
    if (speedMps <= 0.0) return null
    val vx = from.lat - center.lat
    val vy = from.lon - center.lon
    val dLatM = vx * 110_574.0
    val dLonM = vy * 111_320.0 * cos(Math.toRadians((from.lat + center.lat) / 2.0))
    val dSq = dLatM * dLatM + dLonM * dLonM
    val d = sqrt(dSq)
    if (d <= radiusM) return null // already inside
    val rad = Math.toRadians(bearingDeg)
    // unit heading vector (latitude/longitude metres), same metre basis as the offset above
    val uLat = cos(rad)
    val uLon = sin(rad) * (111_320.0 / 110_574.0) * cos(Math.toRadians(from.lat)).coerceAtLeast(0.01)
    val uNorm = sqrt(uLat * uLat + uLon * uLon)
    val ux = uLat / uNorm
    val uy = uLon / uNorm
    // v·u where v = center - from (threat -> center is negative along an inbound heading)
    val vDotU = -(dLatM * ux + dLonM * uy)
    if (vDotU <= 0.0) return null // heading away from the circle
    val disc = vDotU * vDotU - (dSq - radiusM * radiusM)
    if (disc < 0.0) return null // ray misses the circle entirely
    val dist = vDotU - sqrt(disc)
    if (dist < 0.0) return null
    return dist / speedMps / 60.0
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
 * it's treated as stale: dimmed on the map and dropped from counts/alerts — a missile is
 * never in the region for more than a few minutes, so 10+ minute ghosts are noise.
 */
fun staleAfterMs(type: ThreatType): Long = when (type) {
    ThreatType.BALLISTIC -> 90_000L
    ThreatType.CRUISE_MISSILE, ThreatType.KAB -> 180_000L
    ThreatType.AVIATION -> 240_000L
    ThreatType.SHAHED, ThreatType.FPV_LOITERING, ThreatType.RECON, ThreatType.UNKNOWN -> 300_000L
}

/** True when the threat's last confirmed fix is older than its per-type staleness window. */
fun isExpired(t: Threat, now: Long): Boolean {
    val updated = t.updatedAtMillis ?: t.confirmedAtMillis ?: return false
    return now - updated > staleAfterMs(t.type)
}

/**
 * How long a stale threat stays drawn (dimmed) on the map past its staleness window before
 * it's removed entirely. NEPTUN documents no server-side death timeout, so this is the
 * backstop that guarantees a ghost marker can't linger forever.
 */
const val STALE_GHOST_CAP_MS = 30 * 60 * 1000L

/** Minimum displacement between recorded fixes for a measured heading to be trusted. */
private const val HEADING_MIN_METERS = 100.0

/** True when the threat is past its per-type staleness window or the server flagged it stale. */
fun Threat.isStale(now: Long): Boolean = status == "stale" || isExpired(this, now)

/**
 * True when a stale threat has been on screen for longer than the staleness window plus the
 * ghost cap — it's then removed from the map entirely instead of staying dimmed.
 */
fun Threat.isGhost(now: Long): Boolean {
    val updated = updatedAtMillis ?: confirmedAtMillis ?: return false
    return now - updated > staleAfterMs(type) + STALE_GHOST_CAP_MS
}

/**
 * The heading a threat is actually moving along, used for BOTH dead-reckoning
 * ([predictPosition]) and icon facing ([Threat.courseDeg]) so they always agree. Prefers the
 * server's authoritative velocity bearing, then the top-level reported heading, then our own
 * measured track from recorded fixes. Null when nothing usable is known (a stationary threat
 * that shouldn't glide). The server's bearing comes first so a marker's facing and glide match
 * what NEPTUN itself shows — our measured track is a last-resort fallback, never a correction.
 */
fun motionHeading(t: Threat): Double? =
    t.bearingDeg ?: t.heading ?: ThreatSpeedTracker.measuredHeading(t.id)

/**
 * Predict a threat's current position by advancing from its last confirmed fix along its
 * course at the estimated speed, within NEPTUN's per-type fly horizon. Mirrors the SDK's
 * `predict()`: it dead-reckons **only** tracks that carry a real velocity (the authoritative
 * `bearingDeg` + `speedKmh`, i.e. [Threat.flying]) — anything else returns null so the caller
 * keeps the raw fix. Anchors strictly on `confirmedAtMillis` (the dead-reckon anchor), never
 * on `updatedAtMillis`.
 */
fun predictPosition(t: Threat, speedMps: Double, nowMillis: Long): GeoPoint? {
    if (!t.flying) return null
    val heading = motionHeading(t) ?: return null
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
 *
 * A single shared instance serves every consumer (UI + background service + map preview), so
 * the "same" threat always measures the same speed — the UI and the alert service can never
 * disagree near a zone boundary. Access is synchronized because consumers run on different
 * threads (Main + IO).
 */
object ThreatSpeedTracker {
    private data class Fix(val t: Long, val lat: Double, val lon: Double)
    private val fixes = HashMap<String, ArrayDeque<Fix>>()

    fun record(id: String, t: Long, lat: Double, lon: Double) {
        synchronized(fixes) {
            val q = fixes.getOrPut(id) { ArrayDeque() }
            val last = q.lastOrNull()
            if (last != null && last.t == t) return
            q.addLast(Fix(t, lat, lon))
            while (q.size > 4) q.removeFirst()
        }
    }

    /** Drop all recorded fixes (test isolation only — a fresh start mirrors a fresh install). */
    internal fun clear() {
        synchronized(fixes) { fixes.clear() }
    }

    fun estimate(id: String, t: Threat): Double? =
        estimateWithSource(id, t)?.first

    /** Speed plus where it came from: server/measured fixes = RECORDED, per-type = TYPICAL. */
    fun estimateWithSource(id: String, t: Threat): Pair<Double, SpeedSource>? {
        t.speedKmh?.let { s -> if (s >= 5.0) return s / 3.6 to SpeedSource.RECORDED }
        synchronized(fixes) {
            val q = fixes[id]
            if (q != null && q.size >= 2) {
                val a = q.first()
                val b = q.last()
                val dt = (b.t - a.t) / 1000.0
                if (dt in 2.0..600.0) {
                    val v = distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt
                    if (v >= 5.0) return v to SpeedSource.RECORDED
                }
            }
        }
        if (t.trail.size >= 2) {
            val a = t.trail[t.trail.size - 2]
            val b = t.trail[t.trail.size - 1]
            if (a.tMillis != null && b.tMillis != null) {
                val dt = (b.tMillis - a.tMillis) / 1000.0
                if (dt in 5.0..600.0) {
                    val v = distanceMeters(a.lat, a.lon, b.lat, b.lon) / dt
                    if (v >= 5.0) return v to SpeedSource.RECORDED
                }
            }
        }
        return nominalSpeedMps(t.type)?.let { it to SpeedSource.TYPICAL }
    }

    /**
     * Measured course (deg clockwise from north) from our own recorded fixes — the actual
     * track the marker is seen moving along. Used so a threat's glide and its icon facing
     * always agree (see [motionHeading]). Null when there aren't enough fixes, the span is
     * outside a sane window, or the displacement is too small to beat position jitter.
     */
    fun measuredHeading(id: String): Double? {
        synchronized(fixes) {
            val q = fixes[id] ?: return null
            if (q.size < 2) return null
            val a = q.first()
            val b = q.last()
            val dt = (b.t - a.t) / 1000.0
            if (dt !in 2.0..600.0) return null
            if (distanceMeters(a.lat, a.lon, b.lat, b.lon) < HEADING_MIN_METERS) return null
            return bearingDegrees(a.lat, a.lon, b.lat, b.lon)
        }
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
