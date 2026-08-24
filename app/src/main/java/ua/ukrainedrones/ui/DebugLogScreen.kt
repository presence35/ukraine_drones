package ua.ukrainedrones

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DebugRed = Color(0xFFE57373)
private val DebugAmber = Color(0xFFF9A825)
private val DebugGreen = Color(0xFF4CAF50)
private val DebugBlue = Color(0xFF64B5F6)

/** Distance bands (km from the focus point) used to group threat rows. */
private const val BAND_1 = 10
private const val BAND_2 = 30
private const val BAND_3 = 60

/** Top-level groups of debug rows, in display order. */
private enum class LogGroupKind { OFFICIAL, BAND_1, BAND_2, BAND_3, BAND_FAR, LEFT }

/** Rows of a single threat type within a group, or [entries] for a non-type group. */
private data class TypeSubGroup(
    val type: ThreatType?,
    val entries: List<DebugLogEntry>
)

private data class LogGroup(
    val kind: LogGroupKind,
    val entries: List<DebugLogEntry>
) {
    /** Sub-groups by threat type (only meaningful for the band groups). */
    val subGroups: List<TypeSubGroup> = when (kind) {
        LogGroupKind.OFFICIAL, LogGroupKind.LEFT -> listOf(TypeSubGroup(null, entries))
        else -> entries.groupBy { it.threatType ?: ThreatType.UNKNOWN }
            .entries
            .sortedBy { it.key.ordinal }
            .map { (type, rows) -> TypeSubGroup(type, rows) }
    }
}

/**
 * Debug log screen: an audit trail of every alert/threat decision in the active region —
 * official alerts on/off, threats entering red/yellow zones, and why a notification did or
 * didn't fire. Rows group into official alerts, then threats by distance band (from the focus
 * point) and threat type (each showing its own icon), then threats that left the region. Every
 * row carries a color-coded card with day/night, the effective sound setting, and an "ago"
 * timestamp. The vibration level is intentionally not shown (it never varies).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    s: Strings.StringSet,
    lang: AppLanguage,
    iconSet: ThreatIconSet,
    onBack: () -> Unit
) {
    val entries by DebugLog.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Rolling 24-hour window: rows older than a day drop off live even with no new events.
    val window = entries.filter { now - it.atMillis < DebugLog.AUTO_CLEAR_AGE_MS }
    val groups = buildGroups(window)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.debugLogTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backButton)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        s.debugLogEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    )
                }
            } else {
                groups.forEach { group ->
                    item(key = "header-${group.kind}") {
                        GroupHeader(group, s)
                    }
                    group.subGroups.forEach { sub ->
                        if (sub.type != null) {
                            item(key = "sub-${group.kind}-${sub.type}") {
                                TypeSubHeader(sub, lang, iconSet)
                            }
                        }
                        items(
                            sub.entries.asReversed(),
                            key = { "row-${it.atMillis}-${it.threatId}" }
                        ) { entry ->
                            DebugLogRow(entry, s, lang, now, iconSet)
                        }
                    }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { scope.launch(Dispatchers.IO) { DebugLog.clear() } }) {
                            Text(s.debugLogClear)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Build the ordered groups from the flat newest-first window. Official alerts first, then
 * threat rows bucketed by distance band (nearest first), each band sub-grouped by threat type,
 * then a trailing group for threats that left the region / have no distance.
 */
private fun buildGroups(entries: List<DebugLogEntry>): List<LogGroup> {
    val official = entries.filter { it.kind == DebugLogKind.OFFICIAL_ON || it.kind == DebugLogKind.OFFICIAL_OFF }
    val threat = entries.filter {
        it.kind == DebugLogKind.ZONE_ENTER ||
            it.kind == DebugLogKind.ZONE_EXIT ||
            it.kind == DebugLogKind.REGION_THREAT
    }
    val exits = threat.filter { it.kind == DebugLogKind.ZONE_EXIT || it.distanceKm == null }
    val inRegion = threat.filter { it.kind != DebugLogKind.ZONE_EXIT && it.distanceKm != null }

    val groups = mutableListOf<LogGroup>()
    if (official.isNotEmpty()) {
        groups.add(LogGroup(LogGroupKind.OFFICIAL, official))
    }

    fun bandOf(km: Double): LogGroupKind = when {
        km <= BAND_1 -> LogGroupKind.BAND_1
        km <= BAND_2 -> LogGroupKind.BAND_2
        km <= BAND_3 -> LogGroupKind.BAND_3
        else -> LogGroupKind.BAND_FAR
    }
    val order = listOf(LogGroupKind.BAND_1, LogGroupKind.BAND_2, LogGroupKind.BAND_3, LogGroupKind.BAND_FAR)
    order.forEach { kind ->
        val bandEntries = inRegion.filter { bandOf(it.distanceKm!!) == kind }
        if (bandEntries.isNotEmpty()) groups.add(LogGroup(kind, bandEntries))
    }

    if (exits.isNotEmpty()) {
        groups.add(LogGroup(LogGroupKind.LEFT, exits))
    }
    return groups
}

@Composable
private fun GroupHeader(group: LogGroup, s: Strings.StringSet) {
    val accent = when (group.kind) {
        LogGroupKind.OFFICIAL -> DebugRed
        LogGroupKind.LEFT -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> DebugBlue
    }
    val title = when (group.kind) {
        LogGroupKind.OFFICIAL -> s.debugGroupOfficial
        LogGroupKind.LEFT -> s.debugGroupLeft
        LogGroupKind.BAND_1 -> String.format(s.debugBandCloseFormat, BAND_1)
        LogGroupKind.BAND_2 -> String.format(s.debugBandMidFormat, BAND_1, BAND_2)
        LogGroupKind.BAND_3 -> String.format(s.debugBandFarFormat, BAND_2, BAND_3)
        LogGroupKind.BAND_FAR -> String.format(s.debugBandFarthestFormat, BAND_3)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.weight(1f)
        )
        Text(
            String.format(s.debugBandCountFormat, group.entries.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypeSubHeader(sub: TypeSubGroup, lang: AppLanguage, iconSet: ThreatIconSet) {
    val type = sub.type ?: return
    val info = ThreatTypeCatalog.INFO.getValue(type)
    val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThreatIcon(type = type, set = iconSet, size = 16.dp, contentDescription = label)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DebugLogKind.accent(tier: ThreatZone?): Color = when (this) {
    DebugLogKind.OFFICIAL_ON -> DebugRed
    DebugLogKind.OFFICIAL_OFF -> DebugGreen
    DebugLogKind.ZONE_ENTER -> when (tier) {
        ThreatZone.INNER -> DebugRed
        ThreatZone.OUTER -> DebugAmber
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun DebugLogKind.icon(): ImageVector = when (this) {
    DebugLogKind.OFFICIAL_ON -> Icons.Filled.Warning
    DebugLogKind.OFFICIAL_OFF -> Icons.Filled.CheckCircle
    DebugLogKind.ZONE_ENTER -> Icons.Filled.Warning
    DebugLogKind.ZONE_EXIT -> Icons.Filled.Close
    DebugLogKind.REGION_THREAT -> Icons.Filled.Place
}

private fun DebugLogKind.label(
    tier: ThreatZone?,
    locality: String?,
    lang: AppLanguage,
    s: Strings.StringSet
): String = when (this) {
    DebugLogKind.OFFICIAL_ON -> s.debugKindOfficialOn
    DebugLogKind.OFFICIAL_OFF -> s.debugKindOfficialOff
    DebugLogKind.ZONE_ENTER -> when (tier) {
        ThreatZone.INNER -> "${s.debugKindZoneEnter} · ${s.debugTierRed}"
        ThreatZone.OUTER -> "${s.debugKindZoneEnter} · ${s.debugTierYellow}"
        null -> s.debugKindZoneEnter
    }
    DebugLogKind.ZONE_EXIT -> s.debugKindZoneExit
    DebugLogKind.REGION_THREAT -> localityText(locality, lang)?.let {
        String.format(s.debugKindRegionFormat, it)
    } ?: s.debugKindRegionThreat
}

private fun localityText(locality: String?, lang: AppLanguage): String? =
    locality?.let { if (lang == AppLanguage.UA) it else Cities.byUa[it]?.nameEn ?: Transliteration.transliterate(it) }

private fun DebugLogReason.label(s: Strings.StringSet): String = when (this) {
    DebugLogReason.BELL_MUTED -> s.debugReasonBellMuted
    DebugLogReason.ALREADY_NOTIFIED -> s.debugReasonAlreadyNotified
    DebugLogReason.COALESCED -> s.debugReasonCoalesced
    DebugLogReason.TYPE_OFF -> s.debugReasonTypeOff
    DebugLogReason.ADVISORY -> s.debugReasonAdvisory
    DebugLogReason.STALE -> s.debugReasonStale
    DebugLogReason.OUTSIDE_ZONES -> s.debugReasonOutsideZones
    DebugLogReason.TOGGLE_OFF -> s.debugReasonToggleOff
    DebugLogReason.LEFT -> s.debugReasonLeft
    DebugLogReason.FIRED -> ""
}

@Composable
private fun DebugLogRow(
    entry: DebugLogEntry,
    s: Strings.StringSet,
    lang: AppLanguage,
    now: Long,
    iconSet: ThreatIconSet
) {
    val accent = entry.kind.accent(entry.tier)
    val typeLabel = entry.threatType?.let { type ->
        val info = ThreatTypeCatalog.INFO.getValue(type)
        if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        LeadingIcon(entry, accent, lang, iconSet)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.kind.label(entry.tier, entry.locality, lang, s),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatAlertAge(now, entry.atMillis, s),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDateTime(lang, entry.atMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                typeLabel?.let { label ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // The region is already named in the REGION_THREAT title — don't repeat it.
                if (entry.kind != DebugLogKind.REGION_THREAT) {
                    localityText(entry.locality, lang)?.let { locality ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            locality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                entry.distanceKm?.let { km ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        String.format(s.alertHistoryDistanceFormat, km.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.night) s.debugLogNight else s.debugLogDay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (entry.sirenOverride) s.debugLogSoundOverride else s.debugLogSoundFollows,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.notified) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DebugGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        s.debugLogFired,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DebugGreen
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_notifications_off),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(DebugAmber),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        String.format(s.debugLogSuppressed, entry.reason.label(s)),
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun LeadingIcon(entry: DebugLogEntry, accent: Color, lang: AppLanguage, iconSet: ThreatIconSet) {
    if (entry.kind == DebugLogKind.OFFICIAL_ON) {
        Image(
            painter = painterResource(R.drawable.ic_trident),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accent),
            modifier = Modifier.size(22.dp)
        )
        return
    }
    entry.threatType?.let { type ->
        val label = typeLabel(type, lang)
        ThreatIcon(type = type, set = iconSet, size = 22.dp, contentDescription = label)
        return
    }
    Icon(
        imageVector = entry.kind.icon(),
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(22.dp)
    )
}

private fun typeLabel(type: ThreatType, lang: AppLanguage): String {
    val info = ThreatTypeCatalog.INFO.getValue(type)
    return if (lang == AppLanguage.UA) info.labelUa else info.labelEn
}
