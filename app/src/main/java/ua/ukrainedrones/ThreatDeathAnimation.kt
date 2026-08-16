package ua.ukrainedrones

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Total length of the death animation: lead-in ping + 3s countdown + explosion. */
private const val DEATH_DURATION_MS = 5000L

/**
 * A dying threat. For real server removals [icon] is the marker's own drawable, so the icon
 * keeps rendering here for the full [DEATH_DURATION_MS] and is hidden forever the moment the
 * animation completes. Temp test triggers pass [icon] = null (the marker was already removed).
 */
private class ActiveDeath(
    val geo: GeoPoint,
    val start: Long,
    val icon: Drawable?,
    val rotationDeg: Float,
    val alpha: Float
)

/**
 * Playful "neutralized" flourish drawn on the map at a threat's last position: a soft ping,
 * then a 3-2-1 countdown in a dark pill, then a quick explosion. Rendered as an osmdroid
 * overlay so the map's own projection places it exactly at the geo point (tracking pan/zoom)
 * and `draw()` is re-invoked on every invalidate — a per-frame ticker in the map view keeps
 * it animating for [DEATH_DURATION_MS].
 */
class ThreatDeathOverlay : Overlay() {

    private val deaths = mutableListOf<ActiveDeath>()

    val isActive: Boolean get() = deaths.isNotEmpty()

    fun spawn(geo: GeoPoint, icon: Drawable? = null, rotationDeg: Float = 0f, alpha: Float = 1f) {
        if (deaths.size >= 6) return
        deaths.add(ActiveDeath(geo, SystemClock.elapsedRealtime(), icon, rotationDeg, alpha))
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val pillPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(140, 0, 0, 0)
    }
    private val ringPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val flashPaint = Paint().apply { isAntiAlias = true }
    private val sparkPaint = Paint().apply { isAntiAlias = true }
    private val reuse = android.graphics.Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (deaths.isEmpty()) return
        val density = mapView.context.resources.displayMetrics.density
        val now = SystemClock.elapsedRealtime()

        deaths.removeAll { now - it.start > DEATH_DURATION_MS }
        if (deaths.isEmpty()) return

        for (d in deaths) {
            mapView.projection.toPixels(d.geo, reuse)
            val x = reuse.x.toFloat()
            val y = reuse.y.toFloat()
            val t = ((now - d.start).toFloat() / DEATH_DURATION_MS).coerceIn(0f, 1f)

            // The threat's own icon lingers here through the whole 5s and is only hidden the
            // moment the animation ends (the prune above drops this death from the list).
            d.icon?.let { icon ->
                val w = icon.intrinsicWidth.coerceAtLeast(1) / 2f
                val h = icon.intrinsicHeight.coerceAtLeast(1) / 2f
                val fade = if (t >= 0.70f) 1f - ((t - 0.70f) / 0.30f).coerceIn(0f, 1f) else 1f
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

            // Countdown (0.5-3.5s): bold digit in a translucent dark pill, above the point.
            if (t in 0.10f..0.70f) {
                val digit = 3 - ((t - 0.10f) / 0.20f).toInt().coerceIn(0, 2)
                val du = ((t - 0.10f) % 0.20f) / 0.20f
                val scale = 1.2f - 0.2f * du
                val alpha = if (du < 0.15f) (du / 0.15f).coerceAtMost(1f) else 1f
                val pillR = 22f * density * scale
                val lift = 58f * density
                pillPaint.alpha = (140 * alpha).toInt()
                canvas.drawRoundRect(
                    x - pillR, y - lift - pillR, x + pillR, y - lift + pillR,
                    pillR, pillR, pillPaint
                )
                textPaint.alpha = (255 * alpha).toInt()
                textPaint.textSize = 30f * density * scale
                canvas.drawText("$digit", x, y - lift + textPaint.textSize * 0.34f, textPaint)
            }

            // Explosion (3.5-5.0s): radial burst, center flash, shockwave ring, sparks.
            if (t >= 0.70f) {
                val e = ((t - 0.70f) / 0.30f).coerceIn(0f, 1f)
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