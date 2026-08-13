package ua.odesa.drones

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val RedZoneColor = Color(0xFFD32F2F)
private val YellowZoneColor = Color(0xFFF9A825)

/**
 * Alert-zone editor shown in a bottom panel over the map, so the sliders can be
 * tuned while the red/yellow circles are live on the map behind.
 */
@Composable
fun ZonesEditContent(
    redKm: Int,
    yellowKm: Int,
    redArmed: Boolean,
    yellowArmed: Boolean,
    lang: AppLanguage,
    onRedZoneChange: (Int) -> Unit,
    onYellowZoneChange: (Int) -> Unit,
    onRedArmedChange: (Boolean) -> Unit,
    onYellowArmedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = Strings.get(lang)
    Column(modifier = modifier) {
        Text(
            s.zonesLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        ZoneSlider(
            label = s.redZoneLabel,
            valueKm = redKm,
            range = 1f..5f,
            steps = 0,
            accent = RedZoneColor,
            armed = redArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onRedArmedChange,
            onCommit = onRedZoneChange
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        ZoneSlider(
            label = s.yellowZoneLabel,
            valueKm = yellowKm,
            range = 6f..20f,
            steps = 0,
            accent = YellowZoneColor,
            armed = yellowArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onYellowArmedChange,
            onCommit = onYellowZoneChange
        )
        Spacer(Modifier.height(10.dp))
        Text(
            s.zoneExplain,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ZoneSlider(
    label: String,
    valueKm: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    armed: Boolean,
    bellDesc: String,
    onArmedChange: (Boolean) -> Unit,
    onCommit: (Int) -> Unit
) {
    var local by remember { mutableStateOf(valueKm.toFloat()) }
    LaunchedEffect(valueKm) { local = valueKm.toFloat() }
    // Expanding, fading ring around the bell so it unmistakably reads as a
    // tap-to-toggle control, not a static status icon.
    val bellTransition = rememberInfiniteTransition(label = "bellRing")
    val ringProgress by bellTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500), RepeatMode.Restart),
        label = "ringProgress"
    )
    val ringAlpha by bellTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500), RepeatMode.Restart),
        label = "ringAlpha"
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        // Per-zone alert bell right next to the label: filled + colored while this
        // zone's alerts are armed, outlined gray when muted.
        IconButton(
            onClick = { onArmedChange(!armed) },
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(20.dp + 16.dp * ringProgress)
                        .drawBehind {
                            drawCircle(
                                color = accent.copy(alpha = ringAlpha),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                )
                Icon(
                    imageVector = if (armed) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = bellDesc,
                    tint = if (armed) accent else Color(0xFF777777),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.16f)) {
            Text(
                "$valueKm ${Strings.get(AppLanguage.EN).kmUnit}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                color = accent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    Slider(
        value = local,
        onValueChange = {
            val v = it.roundToInt()
            local = v.toFloat()
            onCommit(v)
        },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent
        )
    )
}
