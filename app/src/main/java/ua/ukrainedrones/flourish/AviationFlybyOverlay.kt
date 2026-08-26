package ua.ukrainedrones

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-size MiG-31K takeoff flourish: a screen-wide jet crosses the viewport on a random
 * bearing with a contrail, then the threat card opens ([onFinished]). Pure overlay — consumes
 * no input, moves no camera.
 *
 * The jet's rotation and the contrail origin both come from the active icon pack's measured
 * silhouette ([IconGeometryCache]), run through the same mirror→rotate chain graphicsLayer
 * applies (scaleY first, then rotationZ) — so nose and exhaust stay glued to the artwork at
 * every bearing, mirrored passes included.
 */
@Composable
fun AviationFlybyOverlay(
    show: AviationFlybyShow,
    iconSet: ThreatIconSet,
    onFinished: (String) -> Unit
) {
    val context = LocalContext.current
    // Measured once per icon set; null → legacy constant fallback (classic vectors etc).
    val geometry = remember(iconSet) { IconGeometryCache.aviationFor(iconSet, context) }
        ?: fallbackGeometry(iconSet)

    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(show.tick) { Animatable(0f) }
    val fadeProgress = remember { Animatable(1f) } // 1 = visible, 0 = faded
    val audioPlayer = remember { FlybyAudioPlayer.create(context) }

    LaunchedEffect(show.tick) {
        audioPlayer.start()
        progress.snapTo(0f)
        progress.animateTo(1f, tween(show.durationMs.toInt(), easing = LinearEasing))
        onFinished(show.threatId)
        // Fade out contrail after jet exits
        fadeProgress.animateTo(0f, tween(2000, easing = LinearEasing))
    }
    // The measuring Box must always compose (the size callback lives on it) — only the
    // contents wait for a real size.
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { canvasPx = it }) {
        if (canvasPx == IntSize.Zero) return@Box
        // A screen-wide event needs a screen-scale plane: ~60% of the viewport width.
        val planeW = canvasPx.width * 0.6f
        val trailLen = planeW * 1.4f
        val trailStroke = (planeW * 0.02f).coerceIn(6f, 14f)
        val (entry, exit) = AviationFlyby.endpoints(
            show.courseDeg, canvasPx.width.toFloat(), canvasPx.height.toFloat(), planeW
        )
        val x = lerp(entry.first, exit.first, progress.value)
        val y = lerp(entry.second, exit.second, progress.value)
        val dir = AviationFlyby.direction(show.courseDeg)
        val (rotZ, flipped) = AviationFlyby.spriteTransform(
            show.courseDeg.toFloat(), geometry.facingDeg
        )
        // Haptics tied to progress (called every frame via composition)
        FlybyHaptics(progress.value)
        Canvas(modifier = Modifier.matchParentSize()) {
            // Exhaust anchor: slot-local fractions → mirror → rotate, exactly like the sprite.
            var ox = geometry.anchorXFrac * planeW
            var oy = geometry.anchorYFrac * planeW
            if (flipped) oy = -oy
            val rad = Math.toRadians(rotZ.toDouble())
            val wx = cos(rad) * ox - sin(rad) * oy
            val wy = sin(rad) * ox + cos(rad) * oy
            val anchorX = (x + wx).toFloat()
            val anchorY = (y + wy).toFloat()
            val tailX = (anchorX - trailLen * dir.first).toFloat()
            val tailY = (anchorY - trailLen * dir.second).toFloat()
            // Contrail alpha fades out after jet exits
            val contrailAlpha = fadeProgress.value
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = 0.55f * contrailAlpha)
                    ),
                    start = Offset(tailX, tailY),
                    end = Offset(anchorX, anchorY)
                ),
                start = Offset(tailX, tailY),
                end = Offset(anchorX, anchorY),
                strokeWidth = trailStroke,
                cap = StrokeCap.Round
            )
        }
        PlaneSprite(planeW, rotZ, flipped, iconSet, alpha = 0.35f, tint = Color.Black,
            modifier = Modifier.offset {
                IntOffset(
                    (x - planeW / 2 + planeW * 0.02f).roundToInt(),
                    (y - planeW / 2 + planeW * 0.03f).roundToInt()
                )
            })
        PlaneSprite(planeW, rotZ, flipped, iconSet,
            modifier = Modifier.offset {
                IntOffset((x - planeW / 2).roundToInt(), (y - planeW / 2).roundToInt())
            })
    }
}

// Legacy fallback when no raster geometry is available: anchor behind the declared facing
// with a small nozzle-side nudge (fractions of slot size), matching the pre-scan behavior.
private const val FALLBACK_BACK_FRAC = 0.42f
private const val FALLBACK_SIDE_FRAC = 0.076f

private fun fallbackGeometry(iconSet: ThreatIconSet): IconGeometry {
    val baseRad = Math.toRadians(IconCatalog.baseDeg(ThreatType.AVIATION, iconSet).toDouble())
    val noseX = sin(baseRad)
    val noseY = -cos(baseRad)
    val perpX = cos(baseRad)
    val perpY = sin(baseRad)
    return IconGeometry(
        facingDeg = IconCatalog.baseDeg(ThreatType.AVIATION, iconSet),
        anchorXFrac = (-FALLBACK_BACK_FRAC * noseX + FALLBACK_SIDE_FRAC * perpX).toFloat(),
        anchorYFrac = (-FALLBACK_BACK_FRAC * noseY + FALLBACK_SIDE_FRAC * perpY).toFloat()
    )
}

/**
 * One copy of the aviation asset in a square slot of [sidePx], optionally a flat shadow.
 * [rotationZDeg] and [flip] come pre-computed from the pack's measured facing so the jet
 * never renders upside-down yet its nose still points along the flight path.
 */
@Composable
private fun PlaneSprite(
    sidePx: Float,
    rotationZDeg: Float,
    flip: Boolean,
    iconSet: ThreatIconSet,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    tint: Color = Color.Unspecified
) {
    val side = with(LocalDensity.current) { sidePx.toDp() }
    Box(
        modifier = modifier
            .size(side)
            .graphicsLayer {
                this.alpha = alpha
                this.rotationZ = rotationZDeg
                this.scaleY = if (flip) -1f else 1f
            }
    ) {
        ThreatIcon(type = ThreatType.AVIATION, set = iconSet, size = side, tint = tint)
    }
}
