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
    val geometry = remember(iconSet) { IconGeometryCache.aviationFor(iconSet, context) }
        ?: fallbackGeometry(iconSet)

    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(show.tick) { Animatable(0f) }
    val fadeProgress = remember { Animatable(1f) }
    val audioPlayer = remember(show.tick) { FlybyAudioPlayer.create(context) }

    androidx.compose.runtime.DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(show.tick) {
        audioPlayer.start()
        progress.snapTo(0f)
        progress.animateTo(1f, tween(show.durationMs.toInt(), easing = LinearEasing))
        // Sound lives through the smoke and dies just before the trail vanishes.
        try {
            fadeProgress.animateTo(0f, tween(2000, easing = LinearEasing))
        } finally {
            try { audioPlayer.stop() } catch (_: Exception) {}
        }
        onFinished(show.threatId)
    }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { canvasPx = it }) {
        if (canvasPx == IntSize.Zero) return@Box
        val planeW = canvasPx.width * 0.6f
        val trailLen = planeW * 1.4f
        val trailStroke = (planeW * 0.02f).coerceIn(6f, 14f)
        val trailDrop = planeW * 0.06f
        val (entry, exit) = AviationFlyby.endpoints(
            show.courseDeg, canvasPx.width.toFloat(), canvasPx.height.toFloat(), planeW
        )
        val x = lerp(entry.first, exit.first, progress.value)
        val y = lerp(entry.second, exit.second, progress.value)
        val (rotZ, flipped) = AviationFlyby.spriteTransform(
            show.courseDeg.toFloat(), geometry.facingDeg
        )
        FlybyHaptics(progress.value)
        Canvas(modifier = Modifier.matchParentSize()) {
            var ox = geometry.anchorXFrac * planeW
            // Per-pack exhaust correction from Pixel 7 screenshot (planeW≈648px, screenshot 1:1):
            // russian 0px (perfect), comic +68px, photo +44px, army +38px — all too high.
            // Stored as frac of planeW so it scales with device (68/648≈0.1049, etc).
            val anchorFixY = when (iconSet) {
                ThreatIconSet.COMIC -> planeW * 0.10494f
                ThreatIconSet.PHOTO -> planeW * 0.06790f
                ThreatIconSet.ARMY -> planeW * 0.05864f
                else -> 0f
            }
            var oy = geometry.anchorYFrac * planeW + anchorFixY
            if (flipped) oy = -oy
            val rad = Math.toRadians(rotZ.toDouble())
            val wx = cos(rad) * ox - sin(rad) * oy
            val wy = sin(rad) * ox + cos(rad) * oy
            val anchorX0 = (x + wx).toFloat()
            val anchorY0 = (y + wy).toFloat()
            // Rendered body axis (mirror→rotate of the art's measured facing) so the trail
            // is a straight continuation of the jet's exhaust — matches the sprite exactly.
            val facingRad = Math.toRadians(geometry.facingDeg.toDouble())
            var nx = sin(facingRad).toFloat()
            var ny = -cos(facingRad).toFloat()
            if (flipped) ny = -ny
            val noseX = (cos(rad) * nx - sin(rad) * ny).toFloat()
            val noseY = (sin(rad) * nx + cos(rad) * ny).toFloat()
            val fade = fadeProgress.value
            val slideBy = trailLen * (1f - fade)
            val anchorX = (anchorX0 + slideBy * noseX).toFloat()
            val anchorY = (anchorY0 + slideBy * noseY + trailDrop).toFloat()
            val effLen = trailLen * fade
            val tailX = (anchorX - effLen * noseX).toFloat()
            val tailY = (anchorY - effLen * noseY).toFloat()
            val contrailAlpha = 0.55f * fade
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = contrailAlpha)
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
