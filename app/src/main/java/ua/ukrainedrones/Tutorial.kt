package ua.ukrainedrones

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class TutorialStep(val id: String, val title: String, val body: String)

fun tutorialSteps(s: Strings.StringSet): List<TutorialStep> = listOf(
    TutorialStep("zones", s.tutorialZonesTitle, s.tutorialZonesBody),
    TutorialStep("editZones", s.tutorialEditZonesTitle, s.tutorialEditZonesBody),
    TutorialStep("footer", s.tutorialFooterTitle, s.tutorialFooterBody),
    TutorialStep("settings", s.tutorialSettingsTitle, s.tutorialSettingsBody)
)

/** Drives the spotlight tour: registers target positions and the current step. */
class TutorialController(
    val steps: List<TutorialStep>,
    private val onFinish: () -> Unit
) {
    private val targets = mutableStateMapOf<String, LayoutCoordinates>()
    var currentIndex by mutableStateOf(-1)
        private set

    val isActive: Boolean get() = currentIndex >= 0
    val currentStep: TutorialStep? get() = steps.getOrNull(currentIndex)

    fun registerTarget(id: String, coords: LayoutCoordinates) {
        targets[id] = coords
    }

    fun start() {
        if (steps.isNotEmpty()) currentIndex = 0
    }

    fun next() {
        val i = currentIndex + 1
        if (i < steps.size) currentIndex = i else finish()
    }

    fun skip() = finish()

    private fun finish() {
        currentIndex = -1
        onFinish()
    }

    /** Screen-space bounds of the current step's target, or null while it isn't laid out. */
    fun currentTargetBounds(): Rect? {
        val id = currentStep?.id ?: return null
        val coords = targets[id] ?: return null
        if (!coords.isAttached) return null
        return coords.boundsInRoot()
    }
}

fun Modifier.tutorialTarget(controller: TutorialController, id: String): Modifier =
    onGloballyPositioned { controller.registerTarget(id, it) }

/**
 * Full-screen scrim with a "hole" over the current step's target and a tooltip card.
 * Tapping the scrim advances; the card has Skip / Next (or Done). Never blocks the
 * underlying map from recomposing — targets are registered via onGloballyPositioned.
 */
@Composable
fun TutorialOverlay(
    controller: TutorialController,
    s: Strings.StringSet,
    modifier: Modifier = Modifier
) {
    val step = controller.currentStep
    val rawBounds = controller.currentTargetBounds()
    if (step == null || rawBounds == null) return

    val density = LocalDensity.current
    val padPx = with(density) { 10.dp.toPx() }
    val gapPx = with(density) { 14.dp.toPx() }
    val edgePx = with(density) { 10.dp.toPx() }

    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var tipSize by remember { mutableStateOf(IntSize.Zero) }
    val pulse by rememberInfiniteTransition(label = "tutorialPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "tutorialPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
    ) {
        // Scrim + hole + pulsing ring. Tapping anywhere outside the card advances.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { controller.next() }
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            val hole = Rect(
                rawBounds.left - padPx,
                rawBounds.top - padPx,
                rawBounds.right + padPx,
                rawBounds.bottom + padPx
            )
            drawRoundRect(
                color = Color.Transparent,
                topLeft = hole.topLeft,
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(padPx),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f * pulse),
                topLeft = hole.topLeft,
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(padPx),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Tooltip card, auto-placed above/below the hole and clamped to the screen.
        if (overlaySize.width > 0 && tipSize.width > 0) {
            val below = rawBounds.center.y > overlaySize.height / 2f
            val x = (rawBounds.center.x - tipSize.width / 2f).toInt()
                .coerceIn(edgePx.toInt(), overlaySize.width - tipSize.width - edgePx.toInt())
            val y = (if (below) rawBounds.bottom + gapPx else rawBounds.top - tipSize.height - gapPx).toInt()
                .coerceIn(edgePx.toInt(), overlaySize.height - tipSize.height - edgePx.toInt())
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
                modifier = Modifier
                    .onSizeChanged { tipSize = it }
                    .offset { IntOffset(x, y) }
                    .widthIn(max = 320.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${controller.currentIndex + 1}/${controller.steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { controller.skip() }) {
                            Text(s.tutorialSkip)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { controller.next() }) {
                            Text(
                                if (controller.currentIndex >= controller.steps.size - 1) s.tutorialDone
                                else s.tutorialNext
                            )
                        }
                    }
                }
            }
        }
    }
}