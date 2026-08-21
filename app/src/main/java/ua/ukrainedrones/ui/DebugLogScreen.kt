package ua.ukrainedrones

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Debug log screen: an audit trail of every alert/threat decision in the active region —
 * official alerts on/off, threats entering red/yellow zones, and why a notification did or
 * didn't fire (bell muted, already notified, coalesced, type off, advisory, stale, outside
 * zones, notifications off). Every row carries day/night, the effective sound setting and
 * the vibration level that would have been used, with an "ago" timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(s: Strings.StringSet, lang: AppLanguage, onBack: () -> Unit) {
    val entries by DebugLog.entries.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Rolling 24-hour window: rows older than a day drop off live even with no new events.
    val window = entries.filter { now - it.atMillis < DebugLog.AUTO_CLEAR_AGE_MS }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (window.isEmpty()) {
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
                items(window.asReversed()) { entry ->
                    DebugLogRow(entry, s, lang, now)
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { DebugLog.clear() }) {
                            Text(s.debugLogClear)
                        }
                    }
                }
            }
        }
    }
}

private fun DebugLogKind.label(tier: ThreatZone?, s: Strings.StringSet): String = when (this) {
    DebugLogKind.OFFICIAL_ON -> s.debugKindOfficialOn
    DebugLogKind.OFFICIAL_OFF -> s.debugKindOfficialOff
    DebugLogKind.ZONE_ENTER -> when (tier) {
        ThreatZone.INNER -> "${s.debugKindZoneEnter} · ${s.debugTierRed}"
        ThreatZone.OUTER -> "${s.debugKindZoneEnter} · ${s.debugTierYellow}"
        null -> s.debugKindZoneEnter
    }
    DebugLogKind.ZONE_EXIT -> s.debugKindZoneExit
    DebugLogKind.REGION_THREAT -> s.debugKindRegionThreat
}

@Composable
private fun DebugLogKind.titleColor(tier: ThreatZone?): Color = when (this) {
    DebugLogKind.ZONE_ENTER -> when (tier) {
        ThreatZone.INNER -> Color(0xFFE57373)
        ThreatZone.OUTER -> Color(0xFFF9A825)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DebugLogKind.OFFICIAL_ON -> Color(0xFFE57373)
    DebugLogKind.OFFICIAL_OFF -> Color(0xFF64B5F6)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

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

private fun vibrationLabel(level: Int, s: Strings.StringSet): String = when (level) {
    0 -> s.vibrationOff
    1 -> s.vibrationSoft
    2 -> s.vibrationMedium
    3 -> s.vibrationStrong
    else -> s.vibrationUrgent
}

@Composable
private fun DebugLogRow(entry: DebugLogEntry, s: Strings.StringSet, lang: AppLanguage, now: Long) {
    val typeLabel = entry.threatType?.let { type ->
        val info = ThreatTypeCatalog.INFO.getValue(type)
        if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.kind.label(entry.tier, s),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = entry.kind.titleColor(entry.tier),
                modifier = Modifier.weight(1f)
            )
            Text(
                formatAlertAge(now, entry.atMillis, s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            entry.locality?.let { locality ->
                Spacer(Modifier.width(6.dp))
                Text(
                    if (lang == AppLanguage.UA) locality
                    else Cities.byUa[locality]?.nameEn ?: Transliteration.transliterate(locality),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
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
            entry.vibrationLevel?.let { level ->
                Spacer(Modifier.width(8.dp))
                Text(
                    String.format(s.debugLogVibrationFormat, vibrationLabel(level, s)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            if (entry.notified) {
                s.debugLogFired
            } else {
                String.format(s.debugLogSuppressed, entry.reason.label(s))
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (entry.notified) FontWeight.SemiBold else FontWeight.Normal,
            color = if (entry.notified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}