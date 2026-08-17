package ua.ukrainedrones

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Total length of the death animation: lead-in ping + projectile flight + explosion. */
private const val DEATH_DURATION_MS = 5000L

/** The projectile hits and the explosion begins this many ms into the animation. */
const val DEATH_EXPLOSION_START_MS = 3500L

/** Explosion window ends this many ms after it starts (5.0s - 3.5s). */
private const val DEATH_EXPLOSION_LEN_MS = DEATH_DURATION_MS - DEATH_EXPLOSION_START_MS

/**
 * A dying threat. For real server removals [icon] is the marker's own drawable, so the icon
 * keeps rendering here for the full [DEATH_DURATION_MS] and is hidden forever the moment the
 * animation completes. Temp test triggers pass [icon] = the same marker drawable but with
 * [hideAtBoom] = true, so the icon vanishes when the explosion starts instead (the threat
 * re-draws on the next overlay rebuild). [origin] is where the projectile takes off from —
 * your GPS position (or pinned city) at spawn time; null skips the flight visuals.
 */
private class ActiveDeath(
    val geo: GeoPoint,
    val origin: GeoPoint?,
    val start: Long,
    val icon: Drawable?,
    val rotationDeg: Float,
    val alpha: Float,
    val hideAtBoom: Boolean
)

/**
 * Playful "neutralized" flourish drawn on the map at a threat's last position: a soft ping
 * marking the target, then a small projectile flies in — from your GPS position (or pinned
 * city) when it's on screen, else from just outside the screen edge — and explodes on
 * impact. Rendered as an osmdroid overlay so the map's own projection places it exactly at
 * the geo points (tracking pan/zoom) and `draw()` is re-invoked on every invalidate — a
 * per-frame ticker in the map view keeps it animating for [DEATH_DURATION_MS].
 */
class ThreatDeathOverlay : Overlay() {

    private val deaths = mutableListOf<ActiveDeath>()

    val isActive: Boolean get() = deaths.isNotEmpty()

    fun spawn(
        geo: GeoPoint,
        origin: GeoPoint? = null,
        icon: Drawable? = null,
        rotationDeg: Float = 0f,
        alpha: Float = 1f,
        hideAtBoom: Boolean = false
    ) {
        if (deaths.size >= 6) return
        deaths.add(ActiveDeath(geo, origin, SystemClock.elapsedRealtime(), icon, rotationDeg, alpha, hideAtBoom))
    }

    private val ringPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val flashPaint = Paint().apply { isAntiAlias = true }
    private val sparkPaint = Paint().apply { isAntiAlias = true }
    private val bulletPaint = Paint().apply { isAntiAlias = true }
    private val reuse = android.graphics.Point()
    private val reuseOrigin = android.graphics.Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (deaths.isEmpty()) return
        val density = mapView.context.resources.displayMetrics.density
        val now = SystemClock.elapsedRealtime()
        val boomT = DEATH_EXPLOSION_START_MS.toFloat() / DEATH_DURATION_MS
        val boomLenT = DEATH_EXPLOSION_LEN_MS.toFloat() / DEATH_DURATION_MS

        deaths.removeAll { now - it.start > DEATH_DURATION_MS }
        if (deaths.isEmpty()) return

        for (d in deaths) {
            mapView.projection.toPixels(d.geo, reuse)
            val x = reuse.x.toFloat()
            val y = reuse.y.toFloat()
            val t = ((now - d.start).toFloat() / DEATH_DURATION_MS).coerceIn(0f, 1f)

            // The threat's own icon lingers here through the flight. Temp test triggers
            // (hideAtBoom) drop it the instant the explosion starts; real removals keep it
            // until the prune above ends the animation (hidden forever at 5s).
            d.icon?.let { icon ->
                val w = icon.intrinsicWidth.coerceAtLeast(1) / 2f
                val h = icon.intrinsicHeight.coerceAtLeast(1) / 2f
                val fade = when {
                    d.hideAtBoom && t >= boomT -> 0f
                    t >= boomT -> 1f - ((t - boomT) / boomLenT).coerceIn(0f, 1f)
                    else -> 1f
                }
                icon.alpha = (d.alpha * fade * 255).toInt()
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(d.rotationDeg)
                icon.setBounds(-w.toInt(), -h.toInt(), w.toInt(), h.toInt())
                icon.draw(canvas)
                canvas.restore()
            }

            // Lead-in ping (0-0.5s) — a soft ring marks the hit point.
            if (t < 0.10f) {
                val ping = t / 0.10f
                ringPaint.color = Color.argb((0.8f * (1f - ping) * 255).toInt(), 255, 213, 0)
                ringPaint.strokeWidth = 3f * density
                canvas.drawCircle(x, y, 56f * density * ping, ringPaint)
            }

            // Flight (0.5s-impact): a small projectile flies in from the origin (GPS position or
            // pinned city) when it's on screen, else from just outside the screen edge along
            // the line of travel, and detonates on impact — which lands at p=1 exactly when
            // the explosion below starts.
            if (t in 0.10f..boomT && d.origin != null) {
                mapView.projection.toPixels(d.origin, reuseOrigin)
                val ox = reuseOrigin.x.toFloat()
                val oy = reuseOrigin.y.toFloat()
                val dx = x - ox
                val dy = y - oy
                val dist = sqrt(dx * dx + dy * dy)
                val p = ((t - 0.10f) / (boomT - 0.10f)).coerceIn(0f, 1f)
                if (dist > 1f) {
                    val W = mapView.width.toFloat()
                    val H = mapView.height.toFloat()
                    val originVisible = ox in 0f..W && oy in 0f..H
                    var sx = ox
                    var sy = oy
                    var entryFound = true
                    if (!originVisible) {
                        // Slab test: where the ray origin→threat crosses the viewport. Start
                        // the bullet a little outside that edge so it glides in, never popping
                        // up mid-air (which happens when the GPS dot is far off-screen).
                        var tnear = -Float.MAX_VALUE
                        var tfar = Float.MAX_VALUE
                        val t1x = -ox / dx
                        val t2x = (W - ox) / dx
                        tnear = maxOf(tnear, minOf(t1x, t2x))
                        tfar = minOf(tfar, maxOf(t1x, t2x))
                        val t1y = -oy / dy
                        val t2y = (H - oy) / dy
                        tnear = maxOf(tnear, minOf(t1y, t2y))
                        tfar = minOf(tfar, maxOf(t1y, t2y))
                        if (tfar >= tnear && tnear > 0f) {
                            val inv = (10f * density) / dist
                            sx = ox + dx * tnear - dx * inv
                            sy = oy + dy * tnear - dy * inv
                        } else {
                            entryFound = false
                        }
                    }
                    if (entryFound) {
                        val bx = sx + (x - sx) * p
                        val by = sy + (y - sy) * p
                        val headX = x - sx
                        val headY = y - sy
                        // Launch flash at the origin as the shot leaves (only when it's visible).
                        if (originVisible && t < 0.25f) {
                            val lf = ((t - 0.10f) / 0.15f).coerceIn(0f, 1f)
                            flashPaint.shader = RadialGradient(
                                ox, oy, (14f * density * lf).coerceAtLeast(0.01f),
                                intArrayOf(
                                    Color.argb((180 * (1f - lf)).toInt(), 255, 213, 0),
                                    Color.argb(0, 255, 213, 0)
                                ),
                                floatArrayOf(0f, 1f),
                                Shader.TileMode.CLAMP
                            )
                            canvas.drawCircle(ox, oy, 14f * density * lf, flashPaint)
                            flashPaint.shader = null
                        }
                        // Bullet: glowing head + gold tail + white chevron along the heading.
                        canvas.save()
                        canvas.translate(bx, by)
                        canvas.rotate(Math.toDegrees(atan2(headY.toDouble(), headX.toDouble())).toFloat())
                        flashPaint.shader = RadialGradient(
                            0f, 0f, 14f * density,
                            intArrayOf(
                                Color.argb(160, 255, 213, 0),
                                Color.argb(0, 255, 213, 0)
                            ),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(0f, 0f, 14f * density, flashPaint)
                        flashPaint.shader = null
                        bulletPaint.color = Color.rgb(255, 213, 0)
                        canvas.drawCircle(-12f * density, 0f, 2f * density, bulletPaint)
                        canvas.drawCircle(-6f * density, 0f, 3.4f * density, bulletPaint)
                        bulletPaint.color = Color.WHITE
                        val chevron = Path()
                        chevron.moveTo(6f * density, 0f)
                        chevron.lineTo(0f, -3.4f * density)
                        chevron.lineTo(0f, 3.4f * density)
                        chevron.close()
                        canvas.drawPath(chevron, bulletPaint)
                        canvas.restore()
                    }
                }
            }

            // Explosion (impact-5.0s): radial burst, center flash, shockwave ring, sparks.
            if (t >= boomT) {
                val e = ((t - boomT) / boomLenT).coerceIn(0f, 1f)
                val maxR = 46f * density
                val br = maxR * e
                val fade = 1f - e
                flashPaint.shader = RadialGradient(
                    x, y, br.coerceAtLeast(1f),
                    intArrayOf(
                        Color.argb((255 * fade).toInt(), 255, 229, 127),
                        Color.argb((200 * fade).toInt(), 255, 152, 0),
                        Color.argb(0, 255, 152, 0)
                    ),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(x, y, br, flashPaint)
                flashPaint.shader = null
                flashPaint.color = Color.argb((230 * fade).toInt(), 255, 255, 255)
                canvas.drawCircle(x, y, br * 0.3f, flashPaint)
                ringPaint.color = Color.argb((255 * fade).toInt(), 255, 213, 0)
                ringPaint.strokeWidth = 2f * density
                canvas.drawCircle(x, y, maxR * (0.5f + 0.9f * e), ringPaint)
                sparkPaint.color = Color.argb((255 * fade).toInt(), 255, 193, 7)
                val sparkDist = maxR * (0.5f + 0.8f * e)
                val sparkR = 3f * density * fade
                repeat(8) { i ->
                    val a = 2.0 * PI * i / 8.0 + 0.4
                    canvas.drawCircle(
                        x + (cos(a) * sparkDist).toFloat(),
                        y + (sin(a) * sparkDist).toFloat(),
                        sparkR,
                        sparkPaint
                    )
                }
            }
        }
    }
}