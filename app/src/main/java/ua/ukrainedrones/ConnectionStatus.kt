package ua.ukrainedrones

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionStatus(
    neptunDown: Boolean,
    backupActive: Boolean,
    backupUp: Boolean,
    backupSeen: Boolean,
    backupOfflineElapsedSec: Long?,
    forceOffline: Boolean,
    onForceOfflineChange: (Boolean) -> Unit,
    showInfo: Boolean,
    onShowInfoChange: (Boolean) -> Unit,
    s: Strings.StringSet,
    lang: AppLanguage,
    iconSet: ThreatIconSet,
    modifier: Modifier = Modifier
) {
    val online = !neptunDown && !backupActive
    val dotColor = when {
        neptunDown -> Color(0xFFE57373) // red — NEPTUN offline (real or simulated)
        backupActive -> Color(0xFFF9A825) // amber — NEPTUN alive but on the backup source
        else -> Color(0xFF4CAF50)
    }
    val label = when {
        neptunDown -> s.connOffline
        backupActive -> s.connBackup
        else -> s.connOnline
    }
        Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable { onShowInfoChange(true) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(if (online) R.drawable.neptun_green else R.drawable.neptun_red),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = if (backupActive && !neptunDown) ColorFilter.tint(Color(0xFFF9A825)) else null,
            modifier = Modifier.size(width = 14.dp, height = 14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = when {
                online -> Color(0xFF4CAF50)
                neptunDown -> Color(0xFFE57373)
                else -> Color.White
            },
            style = MaterialTheme.typography.labelMedium
        )
    }
    if (showInfo) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { onShowInfoChange(false) },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s.connStatusTitle, modifier = Modifier.weight(1f))
                    Image(
                        painter = painterResource(R.drawable.neptun),
                        contentDescription = s.attributionText,
                        modifier = Modifier.height(26.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "neptun.in.ua",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://neptun.in.ua/")
                                )
                            )
                        }
                    )
                    IconButton(onClick = { onShowInfoChange(false) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s.backButton,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceStatusRow(
                        color = if (neptunDown) Color(0xFFE57373) else Color(0xFF4CAF50),
                        name = s.connNeptunLabel,
                        active = !backupActive || !backupSeen,
                        activeLabel = s.connActiveLabel
                    )
                    SourceStatusRow(
                        color = when {
                            backupUp -> Color(0xFF4CAF50)
                            backupSeen -> Color(0xFFF9A825)
                            else -> Color(0xFFE57373)
                        },
                        name = s.connBackupLabel,
                        active = backupActive && backupSeen,
                        activeLabel = s.connActiveLabel
                    )
                    Text(
                        s.connBackupNoMapDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            s.connForceOfflineTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = forceOffline,
                            onCheckedChange = onForceOfflineChange
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.connUpLine, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE57373))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.connDownLine, style = MaterialTheme.typography.bodyMedium)
                    }
                    ConnectionLogSection(s, lang)
                    AlertHistorySection(s, lang, iconSet)
                }
            }
        }
    }
}

@Composable
internal fun SourceStatusRow(color: Color, name: String, active: Boolean, activeLabel: String) {
    val accent = Color(0xFFF9A825)
    val nameColor = if (active) accent else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (active) 1f else 0.65f)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nameColor)
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(
                activeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Collapsible last-10-statuses log. Row 0 is the live in-progress episode (running duration,
 * ticking once a second while expanded); the completed entries follow, newest first.
 */
@Composable
private fun ConnectionLogSection(s: Strings.StringSet, lang: AppLanguage) {
    var expanded by remember { mutableStateOf(false) }
    val entries by ConnectionLog.entries.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expanded) {
        while (expanded) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.connLogTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            val live = ConnectionLog.currentEpisode(now)
            val rows = (live?.let { listOf(it) } ?: emptyList()) + entries.reversed()
            if (rows.isEmpty()) {
                Text(
                    s.connLogEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rows.take(10).forEach { entry ->
                    ConnectionLogRow(entry, s, lang)
                }
            }
        }
    }
}

@Composable
private fun ConnectionLogRow(entry: ConnLogEntry, s: Strings.StringSet, lang: AppLanguage) {
    val time = remember(entry.atMillis) { formatDateTime(lang, entry.atMillis) }
    val color = when (entry.status) {
        ConnStatus.ONLINE -> Color(0xFF4CAF50)
        ConnStatus.OFFLINE -> Color(0xFFE57373)
        ConnStatus.BACKUP -> Color(0xFFF9A825)
    }
    val label = when (entry.status) {
        ConnStatus.ONLINE -> s.connOnline
        ConnStatus.OFFLINE -> s.connOffline
        ConnStatus.BACKUP -> s.connBackup
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        if (entry.durationSec != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                String.format(s.connLogDurFormat, entry.durationSec / 60, entry.durationSec % 60),
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

/**
 * Collapsible log of the last ~20 fired alerts (zone sirens + official alerts), newest first.
 * Rows carry the threat icon, the tier-colored type label, datetime, locality and distance.
 * Consecutive entries within [WAVE_GAP_MS] cluster into a rough "wave" group.
 */
private val WAVE_GAP_MS = 30L * 60 * 1000

@Composable
private fun AlertHistorySection(s: Strings.StringSet, lang: AppLanguage, iconSet: ThreatIconSet) {
    var expanded by remember { mutableStateOf(false) }
    val entries by AlertHistory.entries.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.alertHistoryTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            if (entries.isEmpty()) {
                Text(
                    s.alertHistoryEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var prevAt = entries.asReversed().first().atMillis
                entries.asReversed().forEach { entry ->
                    if (prevAt - entry.atMillis > WAVE_GAP_MS) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    AlertHistoryRow(entry, s, lang, iconSet)
                    prevAt = entry.atMillis
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { AlertHistory.clear() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(s.alertHistoryClear)
                }
            }
            Text(
                s.alertHistoryAutoClearNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AlertHistoryRow(
    entry: AlertHistoryEntry,
    s: Strings.StringSet,
    lang: AppLanguage,
    iconSet: ThreatIconSet
) {
    val time = remember(entry.atMillis) { formatDateTime(lang, entry.atMillis) }
    val isOfficial = entry.tier == null
    val typeLabel = entry.threatType?.let { type ->
        val info = ThreatTypeCatalog.INFO.getValue(type)
        if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    } ?: s.alertHistoryOfficialLabel
    val titleColor = when (entry.tier) {
        ThreatZone.INNER -> Color(0xFFE57373)
        ThreatZone.OUTER -> Color(0xFFF9A825)
        null -> Color(0xFF64B5F6)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = if (isOfficial && entry.threatType == null) {
                painterResource(R.drawable.ic_trident)
            } else {
                painterResource(IconCatalog.res(entry.threatType ?: ThreatType.UNKNOWN, iconSet))
            },
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                typeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.locality?.let { locality ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lang == AppLanguage.UA) locality
                        else Cities.byUa[locality]?.nameEn ?: Transliteration.transliterate(locality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        entry.distanceKm?.let { km ->
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(s.alertHistoryDistanceFormat, km.roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}