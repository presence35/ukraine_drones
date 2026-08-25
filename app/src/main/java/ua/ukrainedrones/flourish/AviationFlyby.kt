package ua.ukrainedrones

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Full edge-to-edge crossing time for the MiG-31K takeoff flyby. */
const val AVIATION_FLYBY_DURATION_MS = 2200L

/**
 * One screen-space pass of the full-size AVIATION icon across the viewport, played the moment
 * a MiG-31K takeoff alert rings (INNER tier). [courseDeg] is the airbase→focus bearing, so the
 * plane visibly flies "at you"; when the flyby lands the threat card opens on top.
 */
@Immutable
data class AviationFlybyShow(
    val tick: Long,
    val threatId: String,
    val courseDeg: Double,
    val durationMs: Long = AVIATION_FLYBY_DURATION_MS
)

/** Pure policy + geometry for the aviation flyby flourish. */
object AviationFlyby {

    /**
     * The threat whose takeoff should play the flyby next, or null. Only INNER-tier AVIATION
     * qualifies (evaluate() already dropped advisory/type-off entries), each id plays at most
     * once per process, and nothing plays while the map isn't the visible screen.
     */
    fun nextShow(
        innerThreats: List<Threat>,
        playedIds: Set<String>,
        focus: LatLng?,
        mapVisible: Boolean,
        tick: Long
    ): AviationFlybyShow? {
        if (!mapVisible) return null
        val t = innerThreats.firstOrNull { it.type == ThreatType.AVIATION && it.id !in playedIds }
            ?: return null
        val course = if (focus != null) bearingDegrees(t.lat, t.lon, focus.lat, focus.lon) else 90.0
        return AviationFlybyShow(tick, t.id, course)
    }

    /** Unit direction of travel in screen coords (x right, y down) for a compass [courseDeg]. */
    internal fun direction(courseDeg: Double): Pair<Double, Double> {
        val rad = Math.toRadians(courseDeg)
        return sin(rad) to -cos(rad)
    }

    /**
     * Entry/exit points where a line through the viewport center along [courseDeg] crosses the
     * screen edges, inflated by [iconPx]/2 so the icon starts and ends fully off-screen.
     */
    fun endpoints(
        courseDeg: Double,
        widthPx: Float,
        heightPx: Float,
        iconPx: Float
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val (dx, dy) = direction(courseDeg)
        val tx = if (abs(dx) < 1e-9) Double.MAX_VALUE else (widthPx / 2f + iconPx / 2f) / abs(dx)
        val ty = if (abs(dy) < 1e-9) Double.MAX_VALUE else (heightPx / 2f + iconPx / 2f) / abs(dy)
        val t = minOf(tx, ty)
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val exit = (cx + t * dx).toFloat() to (cy + t * dy).toFloat()
        val entry = (cx - t * dx).toFloat() to (cy - t * dy).toFloat()
        return entry to exit
    }
}
