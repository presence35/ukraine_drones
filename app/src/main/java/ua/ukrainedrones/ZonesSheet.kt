package ua.ukrainedrones

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val RedZoneColor = Color(0xFFD32F2F)
private val YellowZoneColor = Color(0xFFF9A825)

/** The red/yellow accent colors used by zone sliders everywhere (sheet + Settings). */
internal val ZoneRedColor = RedZoneColor
internal val ZoneYellowColor = YellowZoneColor

/**
 * Alert-zone panel shown in the bottom sheet over the map, so the sliders can be
 * tuned while the slow-distance circles are live on the map behind. Slow threats tier
 * by distance (km), fast threats by time-to-arrival (minutes). Everything is visible
 * at once.
 */
@Composable
fun ZonesPanel(
    slowRedKm: Int,
    slowYellowKm: Int,
    fastRedMin: Int,
    fastYellowMin: Int,
    redArmed: Boolean,
    yellowArmed: Boolean,
    lang: AppLanguage,
    nightNote: String? = null,
    onSlowRedChange: (Int) -> Unit,
    onSlowYellowChange: (Int) -> Unit,
    onFastRedChange: (Int) -> Unit,
    onFastYellowChange: (Int) -> Unit,
    onRedArmedChange: (Boolean) -> Unit,
    onYellowArmedChange: (Boolean) -> Unit,
    onOpenThreatSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = Strings.get(lang)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.zonesLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenThreatSettings,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_ua),
                    contentDescription = s.settingsButton,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        nightNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        SectionCaption(s.slowSectionLabel)
        ZoneRow(
            value = slowRedKm,
            range = 2f..20f,
            unit = s.kmUnit,
            accent = RedZoneColor,
            armed = redArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onRedArmedChange,
            onCommit = onSlowRedChange
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        ZoneRow(
            value = slowYellowKm,
            range = 21f..50f,
            unit = s.kmUnit,
            accent = YellowZoneColor,
            armed = yellowArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onYellowArmedChange,
            onCommit = onSlowYellowChange
        )
        Spacer(Modifier.height(14.dp))
        SectionCaption(s.fastSectionLabel)
        ZoneRow(
            value = fastRedMin,
            range = 2f..5f,
            unit = s.minUnit,
            accent = RedZoneColor,
            armed = redArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onRedArmedChange,
            onCommit = onFastRedChange
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        ZoneRow(
            value = fastYellowMin,
            range = 6f..20f,
            unit = s.minUnit,
            accent = YellowZoneColor,
            armed = yellowArmed,
            bellDesc = s.alertsBellToggle,
            onArmedChange = onYellowArmedChange,
            onCommit = onFastYellowChange
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun SectionCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
internal fun ZoneRow(
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    accent: Color,
    armed: Boolean,
    bellDesc: String,
    onArmedChange: (Boolean) -> Unit,
    onCommit: (Int) -> Unit
) {
    var local by remember { mutableStateOf(value.toFloat()) }
    LaunchedEffect(value) { local = value.toFloat() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Per-zone alert bell + switch on the left: filled/colored while armed,
        // red crossed bell when muted.
        IconButton(
            onClick = { onArmedChange(!armed) },
            modifier = Modifier.size(40.dp)
        ) {
            if (armed) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = bellDesc,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                AlertsOffBell(size = 26.dp, contentDescription = bellDesc)
            }
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
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$value $unit",
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}