package ua.ukrainedrones

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slot-local geometry of an icon artwork, measured from its alpha silhouette instead of
 * hand-tuned constants: where the nose really points and where the exhaust sits inside the
 * square slot the sprite is drawn into. Consumed by the aviation flyby (trail anchor + exact
 * nose rotation) so every icon pack lines up with zero per-pack tuning.
 */
data class IconGeometry(
    /** True artwork facing in slot space — compass degrees, clockwise from screen-up. */
    val facingDeg: Float,
    /** Exhaust anchor, fraction of the slot's size right of the slot center (y-down frame). */
    val anchorXFrac: Float,
    /** Exhaust anchor, fraction of the slot's size below the slot center. */
    val anchorYFrac: Float
)

/**
 * Pure analysis of a sampled alpha mask: recovers the silhouette's principal axis (the true
 * art facing, resolved to the end nearest [declaredFacingDeg] — the declaration only picks
 * which end is the nose, any along-axis error self-corrects) plus the exhaust anchor as the
 * centroid of the rearmost band, expressed as fractions of the enclosing slot.
 *
 * Returns null when too little of the canvas is opaque to be a usable silhouette.
 *
 * @param width    canvas width in sampled pixels
 * @param height   canvas height in sampled pixels
 * @param opaque   true when the artwork pixel at (x, y) is visible (alpha above threshold)
 * @param declaredFacingDeg the pack's declared facing ([IconCatalog.baseDeg]) for the caller's type
 */
fun computeIconGeometry(
    width: Int,
    height: Int,
    opaque: (Int, Int) -> Boolean,
    declaredFacingDeg: Float
): IconGeometry? {
    var count = 0
    var mx = 0.0
    var my = 0.0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!opaque(x, y)) continue
            count++
            mx += x
            my += y
        }
    }
    if (count < 25) return null
    mx /= count
    my /= count

    // Covariance-based principal axis of the silhouette (planes are elongated → robust).
    var cxx = 0.0
    var cyy = 0.0
    var cxy = 0.0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!opaque(x, y)) continue
            val dx = x - mx
            val dy = y - my
            cxx += dx * dx
            cyy += dy * dy
            cxy += dx * dy
        }
    }
    cxx /= count
    cyy /= count
    cxy /= count
    val phi = 0.5 * atan2(2 * cxy, cxx - cyy)
    var ux = cos(phi)
    var uy = sin(phi)

    // Orient the axis toward the declared nose so "forward" is unambiguous.
    val declRad = Math.toRadians(declaredFacingDeg.toDouble())
    if (ux * sin(declRad) + uy * -cos(declRad) < 0) {
        ux = -ux
        uy = -uy
    }

    // Project every opaque pixel onto the axis; the rear band around tMin is the tail/exhaust.
    var tMin = Double.MAX_VALUE
    var tMax = -Double.MAX_VALUE
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!opaque(x, y)) continue
            val t = (x - mx) * ux + (y - my) * uy
            if (t < tMin) tMin = t
            if (t > tMax) tMax = t
        }
    }
    val band = 0.15 * (tMax - tMin) + 1.0
    var ax = 0.0
    var ay = 0.0
    var bandCount = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!opaque(x, y)) continue
            val t = (x - mx) * ux + (y - my) * uy
            if (t <= tMin + band) {
                ax += x
                ay += y
                bandCount++
            }
        }
    }
    ax /= bandCount
    ay /= bandCount

    // Slot fractions relative to the canvas center (the sprite letterboxes into a square).
    val maxDim = maxOf(width, height).toDouble()
    var facing = Math.toDegrees(atan2(ux, -uy)).toFloat() % 360f
    if (facing < 0f) facing += 360f
    if (facing >= 360f) facing = 0f
    return IconGeometry(
        facingDeg = facing,
        anchorXFrac = ((ax - width / 2.0) / maxDim).toFloat(),
        anchorYFrac = ((ay - height / 2.0) / maxDim).toFloat()
    )}

/**
 * Android-side cache over [computeIconGeometry]: decodes each pack's AVIATION raster once and
 * remembers its measured geometry. Null results (classic-vector fallback, decode failure) are
 * cached too, so callers fall back to the legacy constants without repeated work.
 */
object IconGeometryCache {
    private val cache = HashMap<ThreatIconSet, IconGeometry?>()

    @Synchronized
    fun aviationFor(set: ThreatIconSet, context: android.content.Context): IconGeometry? {
        if (cache.containsKey(set)) return cache[set]
        val geometry = runCatching {
            val drawable = androidx.core.content.ContextCompat.getDrawable(
                context, IconCatalog.res(ThreatType.AVIATION, set)
            ) ?: return@runCatching null
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap == null || bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                return@runCatching null
            }
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            computeIconGeometry(
                width = w,
                height = h,
                opaque = { x, y -> (pixels[y * w + x] ushr 24) > 64 },
                declaredFacingDeg = IconCatalog.baseDeg(ThreatType.AVIATION, set)
            )
        }.getOrNull()
        cache[set] = geometry
        return geometry
    }
}
