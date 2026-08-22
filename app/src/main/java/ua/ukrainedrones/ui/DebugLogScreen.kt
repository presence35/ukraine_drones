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

/**
 * Debug log screen: an audit trail of every alert/threat decision in the active region —
 * official alerts on/off, threats entering red/yellow zones, and why a notification did or
 * didn't fire (bell muted, already notified, coalesced, type off, advisory, stale, outside
 * zones, notifications off). Every row is a color-coded card with a leading icon: red trident
 * for an official alert on, green check for all-clear, red/amber warning for red/yellow zone
 * entries, blue pin for a threat in the region (named), gray close for exits — plus day/night,
 * the effective sound setting, the vibration level, and an "ago" timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(s: Strings.StringSet, lang: AppLanguage, onBack: () -> Unit) {
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
                        TextButton(onClick = { scope.launch(Dispatchers.IO) { DebugLog.clear() } }) {
                            Text(s.debugLogClear)
                        }
                    }
                }
            }
        }
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

private fun DebugLogKind.icon(tier: ThreatZone?): ImageVector = when (this) {
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

private fun vibrationLabel(level: Int, s: Strings.StringSet): String = when (level) {
    0 -> s.vibrationOff
    1 -> s.vibrationSoft
    2 -> s.vibrationMedium
    3 -> s.vibrationStrong
    else -> s.vibrationUrgent
}

@Composable
private fun DebugLogRow(entry: DebugLogEntry, s: Strings.StringSet, lang: AppLanguage, now: Long) {
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
        if (entry.kind == DebugLogKind.OFFICIAL_ON) {
            Image(
                painter = painterResource(R.drawable.ic_trident),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent),
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = entry.kind.icon(entry.tier),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
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
                entry.vibrationLevel?.let { level ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        String.format(s.debugLogVibrationFormat, vibrationLabel(level, s)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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