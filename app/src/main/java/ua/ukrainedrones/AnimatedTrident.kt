package ua.ukrainedrones

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.min

// Trident paths, ported from the designer's SVG (root/trident.tsx) — the same
// geometry as res/drawable/ic_trident.xml, in its raw viewBox space (x -60..60, y 0..230.5).
private val CENTER_PATH_DATA =
    "M5.985561 78.82382a104.079383 104.079383 0 0 0 14.053598 56.017033 55 55 0 0 1-13.218774 70.637179A20 20 0 0 0 0 212.5a20 20 0 0 0-6.820384-7.021968 55 55 0 0 1-13.218774-70.637179A104.079383 104.079383 0 0 0-5.98556 78.82382l-1.599642-45.260519A30.103986 30.103986 0 0 1 0 12.5a30.103986 30.103986 0 0 1 7.585202 21.063301zM5 193.624749a45 45 0 0 0 6.395675-53.75496A114.079383 114.079383 0 0 1 0 112.734179a114.079383 114.079383 0 0 1-11.395675 27.13561A45 45 0 0 0-5 193.624749V162.5H5z"

private val WING_PATH_DATA =
    "M27.779818 75.17546A62.64982 62.64982 0 0 1 60 27.5v145H0l-5-10a22.5 22.5 0 0 1 17.560976-21.95122l14.634147-3.292683a10 10 0 1 0-4.427443-19.503751zm5.998315 34.353887a20 20 0 0 1-4.387889 37.482848l-14.634146 3.292683A12.5 12.5 0 0 0 5 162.5h45V48.265462a52.64982 52.64982 0 0 0-12.283879 28.037802zM42 122.5h10v10H42z"

private val TridentBlue = Color(0xFF0057b7)
private val TridentGold = Color(0xFFffd700)

// Splash timing. A "warm" start (process already alive, activity re-shown) is any launch
// well after the process spawned; there the splash is a near-invisible flicker.
private const val COLD_START_WINDOW_MS = 5000L
private const val WARM_START_MS = 10L
private const val COLD_START_MS = 2500L
private const val FIRST_LAUNCH_MS = 4000L

private const val SPLASH_TAGLINE = "БУДЬ СМІЛИВИМ — БЕРЕЖИ СЕБЕ"

/**
 * The Ukraine trident with the wave-gradient animation from LoadingTrident.tsx:
 * a gold band sweeps left-to-right across the blue trident on a 2.5 s loop.
 */
@Composable
fun AnimatedTrident(modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "tridentSweep").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Restart),
        label = "tridentSweep"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = min(w / 165f, h / 230.5f)
        val cx = w / 2f
        val topPad = (h - 230.5f * s) / 2f
        val brush = Brush.linearGradient(
            colors = listOf(TridentBlue, TridentGold, TridentBlue),
            start = Offset(sweep * w, 0f),
            end = Offset(sweep * w + w, 0f)
        )
        drawPath(transformedPath(CENTER_PATH_DATA, s, cx, topPad, mirror = false), brush)
        drawPath(transformedPath(WING_PATH_DATA, s, cx, topPad, mirror = false), brush)
        drawPath(transformedPath(WING_PATH_DATA, s, cx, topPad, mirror = true), brush)
    }
}

/** Maps a trident path from its viewBox space into canvas space, centring it on [cx]. */
private fun transformedPath(data: String, s: Float, cx: Float, topPad: Float, mirror: Boolean): Path {
    val m = Matrix().apply {
        translate(cx, topPad)
        if (mirror) scale(-1f, 1f)
        scale(s, s)
    }
    return PathParser().parsePathString(data).toPath().apply { transform(m) }
}

/** Full-screen black splash: animated trident, tagline, tap anywhere to skip. */
@Composable
private fun SplashScreen(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedTrident(modifier = Modifier.width(170.dp).height(170.dp * (230.5f / 165f)))
            Spacer(Modifier.height(30.dp))
            Text(
                SPLASH_TAGLINE,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF8A8A8A),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shows the animated trident splash for a short moment, then reveals [content].
 * First launch (guide not yet seen) stays up a bit longer; warm starts flicker past.
 */
@Composable
fun SplashGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ZonePrefs(context.applicationContext) }
    var splashDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val coldStart = SystemClock.elapsedRealtime() - MainActivity.PROCESS_START_MILLIS < COLD_START_WINDOW_MS
        val firstLaunch = try {
            !prefs.tutorialSeen().first()
        } catch (e: Exception) {
            false
        }
        val duration = when {
            !coldStart -> WARM_START_MS
            firstLaunch -> FIRST_LAUNCH_MS
            else -> COLD_START_MS
        }
        delay(duration)
        splashDone = true
    }

    if (splashDone) content() else SplashScreen(onDone = { splashDone = true })
}