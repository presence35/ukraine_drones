package ua.ukrainedrones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
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
private val CyrillicRegex = Regex("[\\u0400-\\u04FF]")

private fun containsCyrillic(text: String): Boolean = CyrillicRegex.containsMatchIn(text)

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
    proximity: ThreatProximity?,
    pinnedCity: City?,
    threatLevel: Double,
    fastAlertsSooner: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: ThreatCardSize = ThreatCardSize.LARGE,
    interactive: Boolean = true
) {
    val s = Strings.get(lang)
    val typeInfo = ThreatTypeCatalog.INFO.getValue(threat.type)
    val typeLabel = if (lang == AppLanguage.UA) typeInfo.labelUa else typeInfo.labelEn

    val regionText = listOf(threat.locality, threat.district, threat.region)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { s.noRegion }

    // NEPTUN's locality text is Ukrainian; for the EN UI translate it (raw as fallback).
    var regionEn by remember(threat.updatedAtMillis) { mutableStateOf<String?>(null) }
    LaunchedEffect(threat.updatedAtMillis) {
        if (lang == AppLanguage.EN && regionText != s.noRegion) {
            regionEn = Translator.translate(regionText) ?: regionText
        }
    }
    val displayRegion = if (lang == AppLanguage.EN) regionEn ?: regionText else regionText

    val reliabilityText = when (threat.reliability) {
        Reliability.HIGH -> s.reliabilityHigh
        Reliability.MEDIUM -> s.reliabilityMedium
        Reliability.LOW -> s.reliabilityLow
        Reliability.UNKNOWN -> s.reliabilityUnknown
    }
    val confirmations = threat.confirmations.takeIf { it > 0 }

    val band = proximity?.let { p ->
        val d = p.distToUserKm ?: return@let null
        radialZone(d, RadialZones(p.redKm, p.yellowKm))
            ?.let { spatial -> effectiveZone(threat, spatial, fastAlertsSooner) }
    }
    val bandColor = when (band) {
        ThreatZone.INNER -> DistUserRed
        ThreatZone.OUTER -> DistUserAmber
        null -> Color(0xFF9E9E9E)
    }

    Surface(
        modifier = modifier
            .then(if (interactive) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .then(if (interactive) Modifier.clickable(onClick = onDismiss) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        border = BorderStroke(2.dp, bandColor),
        tonalElevation = 8.dp
    ) {
        when (cardSize) {
            // One glanceable line: threat icon + type + distance/ETA, skull next to its bar.
            ThreatCardSize.SMALL -> {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = iconResFor(threat.type)),
                        contentDescription = typeLabel,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            typeLabel,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        SummaryPills(
                            proximity = proximity,
                            pinnedCity = pinnedCity,
                            s = s,
                            lang = lang
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelSkullIcon(level = threatLevel, size = fontAware(18.dp))
                        Spacer(Modifier.width(6.dp))
                        HorizontalLevelBar(level = threatLevel)
                    }
                }
            }

            // Header + distance/ETA + speed, then a reliability/elapsed footer.
            ThreatCardSize.MEDIUM -> {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = iconResFor(threat.type)),
                            contentDescription = typeLabel,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                typeLabel,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    displayRegion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB0B0B0),
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val course = threat.courseDeg
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier
                                        .size(fontAware(12.dp))
                                        .rotate(course.toFloat())
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LevelSkullIcon(level = threatLevel, size = fontAware(24.dp))
                            Spacer(Modifier.height(3.dp))
                            HorizontalLevelBar(level = threatLevel)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SummaryPills(
                        proximity = proximity,
                        pinnedCity = pinnedCity,
                        s = s,
                        lang = lang
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF3A3A3A))
                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = ReliabilityRed.copy(alpha = 0.18f)
                        ) {
                            Text(
                                reliabilityText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                color = ReliabilityRed,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        var now by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1000)
                                now = System.currentTimeMillis()
                            }
                        }
                        Text(
                            formatElapsedMss(threat.updatedAtMillis, now),
                            color = Color(0xFF9E9E9E),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // The full card: everything plus the vertical skull gauge.
            ThreatCardSize.LARGE -> {
                Row(modifier = Modifier.padding(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Header: icon, type + region/course, close.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = iconResFor(threat.type)),
                                contentDescription = typeLabel,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    typeLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        displayRegion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB0B0B0),
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    val course = threat.courseDeg
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = Color(0xFFB0B0B0),
                                        modifier = Modifier
                                            .size(fontAware(12.dp))
                                            .rotate(course.toFloat())
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
                            lang = lang
                        )
                        Spacer(Modifier.height(4.dp))

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF3A3A3A))
                        Spacer(Modifier.height(10.dp))

                        // NEPTUN's course assessment, e.g. "UAV heading toward Chornomorsk"
                        val courseBase = translateCourseAssessment(threat.explanationShort, lang)
                            ?.let { firstSentence(it) }
                        var course by remember(threat.updatedAtMillis) { mutableStateOf(courseBase) }
                        LaunchedEffect(threat.updatedAtMillis) {
                            if (lang == AppLanguage.EN && courseBase != null && containsCyrillic(courseBase)) {
                                course = Translator.translate(courseBase) ?: courseBase
                            }
                        }
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
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = ReliabilityRed.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        reliabilityText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = ReliabilityRed,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
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
                            var now by remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    kotlinx.coroutines.delay(1000)
                                    now = System.currentTimeMillis()
                                }
                            }
                            Text(
                                formatElapsedMss(threat.updatedAtMillis, now),
                                color = Color(0xFF9E9E9E),
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

/** Compact horizontal 0–10 level bar for the small/medium cards. */
@Composable
private fun HorizontalLevelBar(level: Double) {
    val fraction = (level / 10.0).coerceIn(0.0, 1.0)
    val barWidth = fontAware(56.dp)
    val barHeight = fontAware(8.dp)
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF3A3A3A))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width((barWidth * fraction.toFloat()).coerceAtLeast(fontAware(6.dp)))
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(levelColor(level))
        )
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
    lang: AppLanguage
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
        String.format(s.pillDistanceCd, cityName, formatKm(distUser))
    } else null
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        proximity.etaToUserMin?.let { eta ->
            MetricPill(
                number = formatEtaMinutes(eta),
                unit = s.etaUnit
            )
        }
        MetricPill(
            number = formatKm(distUser),
            unit = s.kmUnit,
            contentDescription = distCd
        )
        proximity.speedKmh?.let { speed ->
            MetricPill(
                number = speed.roundToInt().toString(),
                unit = s.speedUnit
            )
        }
    }
}

/** Neutral, low-color pill where the number is the hero and the unit stays muted. */
@Composable
private fun MetricPill(number: String, unit: String, contentDescription: String? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF2A2A2A)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
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

private fun iconResFor(type: ThreatType): Int = when (type) {
    ThreatType.SHAHED -> R.drawable.shahed
    ThreatType.FPV_LOITERING -> R.drawable.ic_threat_fpv
    ThreatType.CRUISE_MISSILE -> R.drawable.ic_threat_cruise
    ThreatType.BALLISTIC -> R.drawable.ic_threat_ballistic
    ThreatType.KAB -> R.drawable.ic_threat_kab
    ThreatType.AVIATION -> R.drawable.ic_threat_aviation
    ThreatType.RECON -> R.drawable.ic_threat_recon
    ThreatType.UNKNOWN -> R.drawable.ic_threat_unknown
}