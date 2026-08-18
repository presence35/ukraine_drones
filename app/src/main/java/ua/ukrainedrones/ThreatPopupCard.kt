package ua.ukrainedrones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.min
import kotlin.math.roundToInt

private val ReliabilityRed = Color(0xFFD9737A)
private val UncertaintyEmpty = Color(0xFF3A3A3A)
private val AdvisoryAmber = Color(0xFFFFC107)
private val DistUserRed = Color(0xFFE57373)
private val DistUserAmber = Color(0xFFFFD54F)
private val DistUserGreen = Color(0xFF81C784)
private val GpsDot = Color(0xFF4FC3F7)

/** One stacked metric pill on the small card: number + unit + optional dot/label. */
private data class PillSpec(
    val number: String,
    val unit: String,
    val dotColor: Color?,
    val contentDescription: String?
)

/** Grey bell marking a type whose alerts are switched off in Settings. */
@Composable
internal fun AlertsOffBell(
    size: Dp = 14.dp,
    tint: Color = Color(0xFF9E9E9E),
    contentDescription: String? = null
) {
    Icon(
        imageVector = Icons.Outlined.Notifications,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/** System font scale, capped so extreme accessibility sizes can't break the layout. */
@Composable
private fun fontScale(): Float = min(LocalDensity.current.fontScale, 1.5f)

/** Scale a fixed size by the (capped) system font scale so it grows with the text. */
@Composable
private fun fontAware(dp: Dp): Dp = dp * fontScale()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatPopupCard(
    threat: Threat,
    lang: AppLanguage,
    iconSet: ThreatIconSet = ThreatIconSet.PHOTO,
    proximity: ThreatProximity?,
    pinnedCity: City?,
    threatLevel: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: ThreatCardSize = ThreatCardSize.LARGE,
    interactive: Boolean = true,
    alertsOff: Boolean = false,
    neutralized: Boolean = false,
    neutralizing: Boolean = false
) {
    val s = Strings.get(lang)
    val typeInfo = ThreatTypeCatalog.INFO.getValue(threat.type)
    val typeLabel = if (lang == AppLanguage.UA) typeInfo.labelUa else typeInfo.labelEn

    val regionText = listOf(threat.locality, threat.district, threat.region)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { s.noRegion }

    // NEPTUN's locality text is Ukrainian; for the EN UI transliterate it (place names are
    // romanized, never semantically translated — the romanization is all an EN reader needs).
    val displayRegion =
        if (lang == AppLanguage.EN) Transliteration.transliterate(regionText) else regionText

    // Live 1s clock so the elapsed readout (and the stale "last seen" note) stays fresh.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val stale = threat.isStale(now)
    val elapsedText =
        if (stale) s.lastSeenAgoFormat.format(formatElapsedMss(threat.updatedAtMillis, now))
        else formatElapsedMss(threat.updatedAtMillis, now)

    val confirmations = threat.confirmations.takeIf { it > 0 }

    val band = proximity?.let { p ->
        zoneTier(threat, p.distToUserKm ?: return@let null, p.speedKmh, p.params)
    }
    val bandColor = when (band) {
        ThreatZone.INNER -> DistUserRed
        ThreatZone.OUTER -> DistUserAmber
        null -> Color(0xFF9E9E9E)
    }

    // Neutralized state: a compact, non-interactive card that just announces the resolved
    // threat by its type — no pills, skull, region or close.
    if (neutralized) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            border = BorderStroke(2.dp, Color(0xFF3A3A3A)),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThreatIcon(
                        type = threat.type,
                        set = iconSet,
                        size = 28.dp,
                        contentDescription = typeLabel
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        typeLabel,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (neutralizing) s.neutralizingLabel else s.neutralizedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (neutralizing) s.neutralizingNote else s.neutralizedNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E)
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .then(if (interactive) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .then(if (interactive) Modifier.clickable(onClick = onDismiss) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = if (stale) Color(0xFF151515) else Color(0xFF1E1E1E),
        border = BorderStroke(2.dp, if (stale) Color(0xFF3A3A3A) else bandColor),
        tonalElevation = 8.dp
    ) {
        when (cardSize) {
            // Narrow, top-left card: icon + title with "R"/skull on the title row, the first
            // metric pill flanked by two same-height vertical gauges (reliability + 0-10 level),
            // the remaining metrics stacked below, and "seen ago" in the bottom-left corner.
            ThreatCardSize.SMALL -> {
                val distUser = proximity?.distToUserKm
                if (distUser != null) {
                    val cityName = pinnedCity?.let { if (lang == AppLanguage.UA) it.nameUa else it.nameEn }
                    val distCd = if (cityName != null) {
                        String.format(s.pillDistanceCd, cityName, formatKm(distUser))
                    } else null
                    // The metric trio (ETA / distance / speed) in display order.
                    val pillSpecs = buildList {
                        proximity?.etaToUserMin?.let { eta ->
                            add(PillSpec(formatEtaMinutes(eta), s.etaUnit, GpsDot, null))
                        }
                        add(PillSpec(formatKm(distUser), s.kmUnit, null, distCd))
                        proximity?.takeIf { it.speedSource == SpeedSource.RECORDED }?.speedKmh?.let { speed ->
                            add(PillSpec(speed.roundToInt().toString(), s.speedUnit, null, null))
                        }
                    }
                    // Stacked metrics can't wrap — cap the font scale like the old single-line pills.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = density.density,
                            fontScale = min(density.fontScale, 1.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThreatIcon(
                                    type = threat.type,
                                    set = iconSet,
                                    size = 40.dp,
                                    contentDescription = typeLabel
                                )
                                Spacer(Modifier.width(12.dp))
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        typeLabel,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    if (alertsOff) {
                                        Spacer(Modifier.width(6.dp))
                                        AlertsOffBell(size = fontAware(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    s.reliabilityShort,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF9E9E9E)
                                )
                                Spacer(Modifier.width(4.dp))
                                LevelSkullIcon(level = threatLevel, size = fontAware(22.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            val barHeight = fontAware(56.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                pillSpecs.forEachIndexed { index, p ->
                                    if (index == 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MetricPill(
                                                number = p.number,
                                                unit = p.unit,
                                                contentDescription = p.contentDescription,
                                                dotColor = p.dotColor
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            VerticalReliabilityBar(reliability = threat.reliability, height = barHeight)
                                            Spacer(Modifier.width(6.dp))
                                            VerticalLevelBar(level = threatLevel, height = barHeight)
                                        }
                                    } else {
                                        MetricPill(
                                            number = p.number,
                                            unit = p.unit,
                                            contentDescription = p.contentDescription,
                                            dotColor = p.dotColor
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                elapsedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (stale) AdvisoryAmber else Color(0xFF9E9E9E)
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ThreatIcon(
                                type = threat.type,
                                set = iconSet,
                                size = 40.dp,
                                contentDescription = typeLabel
                            )
                            Spacer(Modifier.width(12.dp))
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    typeLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.reliabilityShort,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF9E9E9E)
                            )
                            Spacer(Modifier.width(4.dp))
                            LevelSkullIcon(level = threatLevel, size = fontAware(22.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.gpsOffLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            elapsedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stale) AdvisoryAmber else Color(0xFF9E9E9E)
                        )
                    }
                }
            }

            // The full card: everything plus the vertical skull gauge.
            ThreatCardSize.LARGE -> {
                Row(modifier = Modifier.padding(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Header: icon, type + region/course, close.
                        Row(verticalAlignment = Alignment.Top) {
                            ThreatIcon(
                                type = threat.type,
                                set = iconSet,
                                size = 40.dp,
                                contentDescription = typeLabel
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        typeLabel,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    if (alertsOff) {
                                    Spacer(Modifier.width(6.dp))
                                    AlertsOffBell(size = fontAware(14.dp))
                                }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        displayRegion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB0B0B0),
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }

                        // Always-visible trio: distance + ETA + speed pills.
                        Spacer(Modifier.height(8.dp))
                        SummaryPills(
                            proximity = proximity,
                            pinnedCity = pinnedCity,
                            s = s,
                            lang = lang,
                            modifier = Modifier.padding(start = 52.dp)
                        )
                        Spacer(Modifier.height(4.dp))

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF3A3A3A))
                        Spacer(Modifier.height(10.dp))

                        // NEPTUN's course assessment, e.g. "UAV heading toward Chornomorsk"
                        val course = translateCourseAssessment(threat.explanationShort, lang)
                            ?.let { firstSentence(it) }
                        course?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))
                            Spacer(Modifier.height(8.dp))
                        }
                        if (threat.advisory) {
                            Surface(shape = RoundedCornerShape(12.dp), color = AdvisoryAmber.copy(alpha = 0.18f)) {
                                Text(
                                    s.advisoryLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = AdvisoryAmber,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        threat.count.takeIf { it > 0 }?.let {
                            Text(
                                "${s.groupLabel}: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9E9E9E)
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        UncertaintyBar(uncertaintyKm = threat.uncertaintyKm, s = s)

                        if (threat.areaOnly) {
                            Spacer(Modifier.height(6.dp))
                            Text(s.areaOnlyLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9E9E9E))
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF3A3A3A))
                        Spacer(Modifier.height(8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReliabilityBar(reliability = threat.reliability, s = s)
                                confirmations?.let { n ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = DistUserAmber.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            "$n ${sourcesWord(n, lang)}",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = DistUserAmber,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                            Text(
                                elapsedText,
                                color = if (stale) AdvisoryAmber else Color(0xFF9E9E9E),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    ThreatLevelGauge(level = threatLevel)
                }
            }
        }
    }
}

/** Threat-level colour shared by the vertical gauge, horizontal bar and skull icons. */
private fun levelColor(level: Double): Color = when {
    level >= 8.0 -> Color(0xFFD32F2F)
    level >= 6.0 -> DistUserRed
    level >= 3.0 -> DistUserAmber
    else -> DistUserGreen
}

/** Keep only the first sentence of NEPTUN's course text. */
private fun firstSentence(text: String): String {
    for (c in text) {
        if (c == '.' || c == '!' || c == '?') return text.substringBefore(c).trim()
    }
    return text
}

/** Small skull icon tinted by the threat level (grey below 3). */
@Composable
private fun LevelSkullIcon(level: Double, size: Dp = 30.dp) {
    Icon(
        painter = painterResource(id = R.drawable.ic_skull),
        contentDescription = null,
        tint = if (level >= 3.0) levelColor(level) else Color(0xFF9E9E9E),
        modifier = Modifier.size(size)
    )
}

/** Compact vertical 0–10 level bar, filled from the bottom (the small card's right gauge). */
@Composable
private fun VerticalLevelBar(level: Double, height: Dp) {
    val fraction = (level / 10.0).coerceIn(0.0, 1.0)
    val barWidth = fontAware(8.dp)
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF3A3A3A))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(barWidth)
                .fillMaxHeight(fraction.toFloat().coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(4.dp))
                .background(levelColor(level))
        )
    }
}

/** Compact vertical reliability bar: 3 segments, filled from the bottom, LOW → HIGH. */
@Composable
private fun VerticalReliabilityBar(reliability: Reliability, height: Dp) {
    val level = when (reliability) {
        Reliability.HIGH -> 3
        Reliability.MEDIUM -> 2
        Reliability.LOW -> 1
        Reliability.UNKNOWN -> 0
    }
    val color = when (reliability) {
        Reliability.HIGH -> DistUserGreen
        Reliability.MEDIUM -> DistUserAmber
        Reliability.LOW -> ReliabilityRed
        Reliability.UNKNOWN -> Color(0xFF9E9E9E)
    }
    val barWidth = fontAware(8.dp)
    Column(
        modifier = Modifier
            .width(barWidth)
            .height(height),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Render top segment first; a segment is filled when `level` reaches up to it.
        repeat(3) { i ->
            val segFromBottom = 2 - i
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (level > segFromBottom) color else UncertaintyEmpty)
            )
        }
    }
}

/** Vertical 0–10 gauge: skull above a bar that fills with the level. */
@Composable
private fun ThreatLevelGauge(level: Double) {
    val fraction = (level / 10.0).coerceIn(0.0, 1.0)
    val color = levelColor(level)
    val skullTint = if (level >= 3.0) color else Color(0xFF9E9E9E)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(id = R.drawable.ic_skull),
            contentDescription = null,
            tint = skullTint,
            modifier = Modifier.size(fontAware(26.dp))
        )
        Spacer(Modifier.height(6.dp))
        val barWidth = fontAware(12.dp)
        val barHeight = fontAware(140.dp)
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF3A3A3A))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(barWidth)
                    .fillMaxHeight(fraction.toFloat().coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryPills(
    proximity: ThreatProximity?,
    pinnedCity: City?,
    s: Strings.StringSet,
    lang: AppLanguage,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val distUser = proximity?.distToUserKm
    if (distUser == null) {
        Text(
            s.gpsOffLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9E9E9E)
        )
        return
    }
    val cityName = pinnedCity?.let { if (lang == AppLanguage.UA) it.nameUa else it.nameEn }
    val distCd = if (cityName != null) {
        String.format(s.pillDistanceCd, cityName, distUser.roundToInt())
    } else null
    if (singleLine) {
        // Single-line pills can't wrap — cap the font scale so extreme accessibility
        // sizes don't push the row off the card.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density,
                fontScale = min(density.fontScale, 1.25f)
            )
        ) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillTrio(proximity = proximity, distUser = distUser, distCd = distCd, s = s)
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PillTrio(proximity = proximity, distUser = distUser, distCd = distCd, s = s)
        }
    }
}

/** The ETA / distance / speed pill trio (no wrapping container of its own). */
@Composable
private fun PillTrio(
    proximity: ThreatProximity?,
    distUser: Double,
    distCd: String?,
    s: Strings.StringSet
) {
    proximity?.etaToUserMin?.let { eta ->
        MetricPill(
            number = formatEtaMinutes(eta),
            unit = s.etaUnit,
            dotColor = GpsDot
        )
    }
    MetricPill(
        number = formatKm(distUser),
        unit = s.kmUnit,
        contentDescription = distCd
    )
    proximity?.takeIf { it.speedSource == SpeedSource.RECORDED }?.speedKmh?.let { speed ->
        MetricPill(
            number = speed.roundToInt().toString(),
            unit = s.speedUnit
        )
    }
}

/** Neutral, low-color pill where the number is the hero and the unit stays muted. */
@Composable
private fun MetricPill(
    number: String,
    unit: String,
    contentDescription: String? = null,
    dotColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF2A2A2A)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(fontAware(14.dp))
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(fontAware(8.dp))
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                number,
                color = Color(0xFFCFCFCF),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                }
            )
            Spacer(Modifier.width(2.dp))
            Text(
                unit,
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatKm(km: Double): String = km.roundToInt().toString()

private fun formatEtaMinutes(min: Double): String =
    min.roundToInt().coerceAtLeast(1).toString()

/** Maps uncertainty km to a 1–5 quality rating (more bars = tighter fix). */
private fun uncertaintyBars(km: Double): Int {
    return when {
        km < 1.0 -> 5
        km < 2.0 -> 4
        km < 4.0 -> 3
        km < 8.0 -> 2
        else -> 1
    }
}

/** Fill colour for the precision bar: green at 5 bars, amber mid-range, red when coarse. */
private fun uncertaintyColor(bars: Int): Color = when (bars) {
    5 -> DistUserGreen
    3, 4 -> DistUserAmber
    else -> DistUserRed
}

/** 5-segment uncertainty indicator with the raw ±km kept as a small caption. */
@Composable
private fun UncertaintyBar(uncertaintyKm: Double?, s: Strings.StringSet) {
    if (uncertaintyKm == null) return
    val bars = uncertaintyBars(uncertaintyKm)
    val color = uncertaintyColor(bars)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.uncertaintyLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9E9E9E))
        Spacer(Modifier.width(8.dp))
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < bars) color else UncertaintyEmpty)
            )
            if (i < 4) Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "±${formatKm(uncertaintyKm)} ${s.kmUnit}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9E9E9E)
        )
    }
}

/** Precision-style reliability indicator: label + 3 segments, LOW left → HIGH right. */
@Composable
private fun ReliabilityBar(
    reliability: Reliability,
    s: Strings.StringSet,
    compact: Boolean = false
) {
    val level = when (reliability) {
        Reliability.HIGH -> 3
        Reliability.MEDIUM -> 2
        Reliability.LOW -> 1
        Reliability.UNKNOWN -> 0
    }
    val color = when (reliability) {
        Reliability.HIGH -> DistUserGreen
        Reliability.MEDIUM -> DistUserAmber
        Reliability.LOW -> ReliabilityRed
        Reliability.UNKNOWN -> Color(0xFF9E9E9E)
    }
    val segmentWidth = if (compact) fontAware(16.dp) else fontAware(22.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            Text(s.reliabilityLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9E9E9E))
            Spacer(Modifier.width(8.dp))
        }
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(width = segmentWidth, height = fontAware(6.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) color else UncertaintyEmpty)
            )
            if (i < 2) Spacer(Modifier.width(2.dp))
        }
    }
}
