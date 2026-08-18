package ua.ukrainedrones

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    slowRedArmed: Boolean,
    slowYellowArmed: Boolean,
    fastRedArmed: Boolean,
    fastYellowArmed: Boolean,
    lang: AppLanguage,
    nightActive: Boolean = false,
    useNightZones: Boolean = false,
    nightEnabled: Boolean = false,
    daySlowRedKm: Int? = null,
    daySlowYellowKm: Int? = null,
    dayFastRedMin: Int? = null,
    dayFastYellowMin: Int? = null,
    onSlowRedChange: (Int) -> Unit,
    onSlowYellowChange: (Int) -> Unit,
    onFastRedChange: (Int) -> Unit,
    onFastYellowChange: (Int) -> Unit,
    onSlowRedArmedChange: (Boolean) -> Unit,
    onSlowYellowArmedChange: (Boolean) -> Unit,
    onFastRedArmedChange: (Boolean) -> Unit,
    onFastYellowArmedChange: (Boolean) -> Unit,
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
                when {
                    nightActive -> "\uD83C\uDF19 ${s.nightZonesTitle} \uD83C\uDF19"
                    nightEnabled && useNightZones -> s.dayZonesTitle
                    else -> s.alertZonesTitle
                },
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
        ZoneGroup(
            caption = s.slowSectionLabel,
            leading = "\uD83D\uDC22",
            leadingDesc = s.slowGroupIconDesc
        ) {
            ZoneRow(
                value = slowRedKm,
                range = 2f..20f,
                unit = s.kmUnit,
                accent = RedZoneColor,
                armed = slowRedArmed,
                bellDesc = s.alertsBellToggle,
                reference = if (nightActive) daySlowRedKm else null,
                dayLabel = s.dayShortLabel,
                onArmedChange = onSlowRedArmedChange,
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
                armed = slowYellowArmed,
                bellDesc = s.alertsBellToggle,
                reference = if (nightActive) daySlowYellowKm else null,
                dayLabel = s.dayShortLabel,
                onArmedChange = onSlowYellowArmedChange,
                onCommit = onSlowYellowChange
            )
        }
        Spacer(Modifier.height(12.dp))
        ZoneGroup(
            caption = s.fastSectionLabel,
            leading = "\u26A1\uFE0F",
            leadingDesc = s.fastGroupIconDesc
        ) {
            ZoneRow(
                value = fastRedMin,
                range = 2f..5f,
                unit = s.minUnit,
                accent = RedZoneColor,
                armed = fastRedArmed,
                bellDesc = s.alertsBellToggle,
                reference = if (nightActive) dayFastRedMin else null,
                dayLabel = s.dayShortLabel,
                onArmedChange = onFastRedArmedChange,
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
                armed = fastYellowArmed,
                bellDesc = s.alertsBellToggle,
                reference = if (nightActive) dayFastYellowMin else null,
                dayLabel = s.dayShortLabel,
                onArmedChange = onFastYellowArmedChange,
                onCommit = onFastYellowChange
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ZoneGroup(
    caption: String,
    leading: String,
    leadingDesc: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        SectionCaption(caption, leading = leading, leadingDesc = leadingDesc)
        content()
    }
}

@Composable
internal fun SectionCaption(
    text: String,
    leading: String? = null,
    leadingDesc: String? = null
) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .semantics {
                        if (leadingDesc != null) contentDescription = leadingDesc
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(leading, fontSize = 16.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ZoneRow(
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    accent: Color,
    armed: Boolean,
    bellDesc: String,
    reference: Int? = null,
    dayLabel: String? = null,
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
        // A subtle ghost tick on the track marks the day value while night zones are being
        // edited, so the two can be compared on the spot.
        Box(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    val ref = reference ?: return@drawBehind
                    val thumbR = 10.dp.toPx()
                    val fraction =
                        ((ref - range.start) / (range.endInclusive - range.start)).toFloat()
                    val x = thumbR + fraction * (size.width - 2 * thumbR)
                    drawLine(
                        color = Color(0xFFB0BEC5).copy(alpha = 0.45f),
                        start = Offset(x, size.height * 0.25f),
                        end = Offset(x, size.height * 0.75f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
        ) {
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
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (reference != null && dayLabel != null) {
                "$value $unit · $dayLabel $reference $unit"
            } else "$value $unit",
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}