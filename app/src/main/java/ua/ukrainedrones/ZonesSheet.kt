package ua.ukrainedrones

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        ZoneRow(
            valueKm = redKm,
            range = 1f..20f,
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
        ZoneRow(
            valueKm = yellowKm,
            range = 21f..50f,
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
private fun ZoneRow(
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Per-zone alert bell + switch on the left: filled/colored while armed,
        // outlined gray when muted.
        IconButton(
            onClick = { onArmedChange(!armed) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (armed) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                contentDescription = bellDesc,
                tint = if (armed) accent else Color(0xFF777777),
                modifier = Modifier.size(26.dp)
            )
        }
        Switch(
            checked = armed,
            onCheckedChange = onArmedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.45f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF9E9E9E),
                uncheckedTrackColor = Color(0xFF555555),
                uncheckedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(12.dp))
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
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$valueKm ${Strings.get(AppLanguage.EN).kmUnit}",
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
