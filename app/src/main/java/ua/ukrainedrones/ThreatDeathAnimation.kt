package ua.ukrainedrones

import android.graphics.Point
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Total length of the death animation: 3s countdown + lead-in and explosion. */
private const val DEATH_DURATION_MS = 5000

/**
 * Playful "neutralized" flourish at a threat's last position: a small ping, then a
 * 3-2-1 countdown in a dark pill, then a quick explosion. Always re-anchors itself to the
 * geo point every frame, so it tracks pan/zoom, and never intercepts touches.
 */
@Composable
internal fun ThreatDeathFx(
    removed: ThreatRemoved,
    mapViewRef: State<MapView?>,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val canvasSize = 160.dp
    val halfPx = with(density) { canvasSize.toPx() / 2 }

    var pixel by remember { mutableStateOf<IntOffset?>(null) }
    LaunchedEffect(Unit) {
        val pt = Point()
        while (true) {
            mapViewRef.value?.let { mv ->
                mv.projection.toPixels(GeoPoint(removed.lat, removed.lon), pt)
                pixel = IntOffset(pt.x, pt.y)
            }
            withFrameNanos { }
        }
    }

    LaunchedEffect(Unit) {
        delay(DEATH_DURATION_MS.toLong())
        onDone()
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(DEATH_DURATION_MS.toFloat(), tween(DEATH_DURATION_MS))
    }

    val pos = pixel ?: return
    val t = (progress.value / DEATH_DURATION_MS).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(pos.x - halfPx.roundToInt(), pos.y - halfPx.roundToInt()) }
            .size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        // Lead-in ping (0-0.5s) — a soft ring marks the hit point.
        if (t < 0.10f) {
            val ping = t / 0.10f
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFFD500).copy(alpha = 0.8f * (1f - ping)),
                    radius = size.minDimension * ping * 0.35f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Countdown (0.5-3.5s): bold digit in a translucent dark pill, above the point.
        if (t in 0.10f..0.70f) {
            val digit = 3 - ((t - 0.10f) / 0.20f).toInt().coerceIn(0, 2)
            val du = ((t - 0.10f) % 0.20f) / 0.20f
            val scale = 1.25f - 0.25f * du
            val alpha = if (du < 0.15f) du / 0.15f else 1f
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -56.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$digit",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp
                )
            }
        }

        // Explosion (3.5-5.0s): radial burst, shockwave ring, sparks, center flash.
        if (t >= 0.70f) {
            val e = ((t - 0.70f) / 0.30f).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val cx = center.x
                val cy = center.y
                val maxR = 44.dp.toPx()
                val br = maxR * e
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFE57F), Color(0xFFFF9800), Color.Transparent),
                        center = center,
                        radius = br
                    ),
                    radius = br,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - e) * 0.9f),
                    radius = br * 0.35f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFD500).copy(alpha = 1f - e),
                    radius = maxR * (0.5f + 0.9f * e),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                val sparkR = 3.dp.toPx() * (1f - e)
                val sparkDist = maxR * (0.5f + 0.8f * e)
                repeat(8) { i ->
                    val a = 2.0 * PI * i / 8.0 + 0.4
                    drawCircle(
                        color = Color(0xFFFFC107).copy(alpha = 1f - e),
                        radius = sparkR,
                        center = Offset(cx + (cos(a) * sparkDist).toFloat(), cy + (sin(a) * sparkDist).toFloat())
                    )
                }
            }
        }
    }
}
