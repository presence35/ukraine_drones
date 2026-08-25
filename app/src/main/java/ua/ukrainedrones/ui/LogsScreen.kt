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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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

/** Rows shown at once; the double-arrow button reveals [VISIBLE_STEP] more. */
private const val VISIBLE_INITIAL = 25
private const val VISIBLE_STEP = 50

/** Which data source to show. */
private enum class LogsFilter { DECISIONS, CONNECTIONS }

/** How to group decision rows. */
private enum class GroupBy { TIMELINE, PROXIMITY, TYPE }

/** Accent for a group header. */
private enum class GroupAccent { OFFICIAL, RED, YELLOW, OBLAST, LEFT }

/** Rows of a single threat type inside a proximity group. */
private data class TypeSubGroup(
    val type: ThreatType?,
    val entries: List<DebugLogEntry>
)

/** One rendered group of decision rows. [title] null = timeline list, no header. */
private data class LogGroupSpec(
    val id: String,
    val title: String?,
    val accent: GroupAccent?,
    val headerType: ThreatType?,
    val entries: List<DebugLogEntry>,
    /** Threat-type sub-headers inside the group (proximity mode only). */
    val subTypes: Boolean
)

/** A unified row for the log list — any of the data sources. */
private sealed interface LogRow {
    val atMillis: Long
}

private data class DecisionRow(val entry: DebugLogEntry) : LogRow {
    override val atMillis: Long get() = entry.atMillis
}

private data class ConnectionRow(val entry: ConnLogEntry) : LogRow {
    override val atMillis: Long get() = entry.atMillis
}

/**
 * Logs screen: a single card list over the decision audit trail and connection episodes.
 * The Decisions tab offers group-by (Timeline / Proximity / Type), a standard sort-direction
 * toggle (newest or oldest first) and a "shown only" switch (only rows where a notification
 * was actually shown); a double arrow at the bottom reveals more rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    s: Strings.StringSet,
    lang: AppLanguage,
    iconSet: ThreatIconSet,
    onBack: () -> Unit
) {
    val entries by DebugLog.entries.collectAsState()
    val connEntries by ConnectionLog.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var filter by rememberSaveable { mutableStateOf(LogsFilter.DECISIONS) }
    var groupBy by rememberSaveable { mutableStateOf(GroupBy.PROXIMITY) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var shownOnly by rememberSaveable { mutableStateOf(true) }
    var visibleCount by remember { mutableIntStateOf(VISIBLE_INITIAL) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Rolling 24-hour window: decision rows older than a day drop off live.
    val window = entries.filter { now - it.atMillis < DebugLog.AUTO_CLEAR_AGE_MS }
    val isDecisions = filter == LogsFilter.DECISIONS
    val rows: List<LogRow> = buildRows(filter, window, connEntries, now, isDecisions, newestFirst, shownOnly)
    val visible = rows.take(visibleCount)
    val hasMore = visibleCount < rows.size
    val groups = if (isDecisions) buildGroups(visible.filterIsInstance<DecisionRow>().map { it.entry }, groupBy) else emptyList()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.logsTitle) },
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
            item(key = "filters") {
                FilterRow(filter, s) {
                    filter = it
                    visibleCount = VISIBLE_INITIAL
                }
            }
            if (isDecisions) {
                item(key = "viewopts") {
                    ViewOptionsRow(
                        groupBy = groupBy,
                        newestFirst = newestFirst,
                        shownOnly = shownOnly,
                        s = s,
                        onGroupBy = {
                            groupBy = it
                            visibleCount = VISIBLE_INITIAL
                        },
                        onSortToggle = { newestFirst = !newestFirst },
                        onShownOnlyChange = {
                            shownOnly = it
                            visibleCount = VISIBLE_INITIAL
                        }
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        if (filter == LogsFilter.CONNECTIONS) s.logsEmptyConnections else s.debugLogEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    )
                }
            } else if (groups.isNotEmpty()) {
                groups.forEach { group ->
                    if (group.title != null) {
                        item(key = "header-${group.id}") {
                            GroupHeader(group, s)
                        }
                    }
                    if (group.subTypes) {
                        group.entries.groupBy { it.threatType ?: ThreatType.UNKNOWN }
                            .entries
                            .sortedBy { it.key.ordinal }
                            .forEach { (type, subEntries) ->
                                item(key = "sub-${group.id}-$type") {
                                    TypeSubHeader(TypeSubGroup(type, subEntries), lang, iconSet)
                                }
                                items(subEntries, key = { "row-${it.atMillis}-${it.threatId}" }) { entry ->
                                    DecisionCard(entry, s, lang, now, iconSet)
                                }
                            }
                    } else {
                        items(group.entries, key = { "row-${it.atMillis}-${it.threatId}" }) { entry ->
                            DecisionCard(entry, s, lang, now, iconSet)
                        }
                    }
                }
            } else {
                items(visible, key = { "flat-${it.atMillis}-${it::class.simpleName}" }) { row ->
                    LogRowCard(row, s, lang, now, iconSet)
                }
            }
            if (visible.isNotEmpty()) {
                if (hasMore) {
                    item(key = "more") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ShowMoreButton(s) { visibleCount += VISIBLE_STEP }
                        }
                    }
                }
                if (isDecisions) {
                    item(key = "clear") {
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
}

/**
 * Assemble the row list for the active filter, ordered per [newestFirst]. The Decisions view
 * drops rows whose notification was not shown when [shownOnly]; connection rows include the
 * live in-progress episode.
 */
private fun buildRows(
    filter: LogsFilter,
    decisions: List<DebugLogEntry>,
    connEntries: List<ConnLogEntry>,
    now: Long,
    isDecisions: Boolean,
    newestFirst: Boolean,
    shownOnly: Boolean
): List<LogRow> {
    if (!isDecisions) {
        val connRows = (ConnectionLog.currentEpisode(now)?.let { listOf(ConnectionRow(it)) }
            ?: emptyList()) + connEntries.map { ConnectionRow(it) }
        return if (newestFirst) connRows.sortedByDescending { it.atMillis } else connRows.sortedBy { it.atMillis }
    }
    val filtered = if (shownOnly) decisions.filter { it.notified } else decisions
    val rows = filtered.map { DecisionRow(it) }
    return if (newestFirst) rows.sortedByDescending { it.atMillis } else rows.sortedBy { it.atMillis }
}

/**
 * Build the ordered group specs from sorted decision rows. Canonical group order regardless of
 * sort direction: proximity = official / red / yellow / oblast / left; type = official / types /
 * left. Timeline returns a single header-less spec.
 */
private fun buildGroups(rows: List<DebugLogEntry>, groupBy: GroupBy): List<LogGroupSpec> {
    if (rows.isEmpty()) return emptyList()
    return when (groupBy) {
        GroupBy.TIMELINE -> listOf(LogGroupSpec("timeline", null, null, null, rows, subTypes = false))
        GroupBy.PROXIMITY -> {
            val official = rows.filter { it.kind == DebugLogKind.OFFICIAL_ON || it.kind == DebugLogKind.OFFICIAL_OFF }
            val flourish = rows.filter { it.kind == DebugLogKind.FLOURISH }
            val threat = rows.filter {
                it.kind == DebugLogKind.ZONE_ENTER ||
                    it.kind == DebugLogKind.ZONE_EXIT ||
                    it.kind == DebugLogKind.REGION_THREAT
            }
            val left = threat.filter { it.kind == DebugLogKind.ZONE_EXIT || it.distanceKm == null }
            val rest = threat.filter { it.kind != DebugLogKind.ZONE_EXIT && it.distanceKm != null }
            val red = rest.filter { it.tier == ThreatZone.INNER }
            val yellow = rest.filter { it.tier == ThreatZone.OUTER }
            val oblast = rest.filter { it.tier == null }
            buildList {
                if (official.isNotEmpty()) add(LogGroupSpec("official", "official", GroupAccent.OFFICIAL, null, official, subTypes = false))
                if (flourish.isNotEmpty()) add(LogGroupSpec("flourish", "flourish", GroupAccent.LEFT, null, flourish, subTypes = false))
                if (red.isNotEmpty()) add(LogGroupSpec("red", "red", GroupAccent.RED, null, red, subTypes = true))
                if (yellow.isNotEmpty()) add(LogGroupSpec("yellow", "yellow", GroupAccent.YELLOW, null, yellow, subTypes = true))
                if (oblast.isNotEmpty()) add(LogGroupSpec("oblast", "oblast", GroupAccent.OBLAST, null, oblast, subTypes = true))
                if (left.isNotEmpty()) add(LogGroupSpec("left", "left", GroupAccent.LEFT, null, left, subTypes = false))
            }
        }
        GroupBy.TYPE -> {
            val official = rows.filter { it.kind == DebugLogKind.OFFICIAL_ON || it.kind == DebugLogKind.OFFICIAL_OFF }
            val flourish = rows.filter { it.kind == DebugLogKind.FLOURISH }
            val exits = rows.filter {
                it.kind == DebugLogKind.ZONE_EXIT &&
                    it.kind != DebugLogKind.OFFICIAL_ON && it.kind != DebugLogKind.OFFICIAL_OFF
            }
            val typed = rows.filter { it !in official && it !in exits && it.threatType != null }
            buildList {
                if (official.isNotEmpty()) add(LogGroupSpec("official", "official", GroupAccent.OFFICIAL, null, official, subTypes = false))
                if (flourish.isNotEmpty()) add(LogGroupSpec("flourish", "flourish", GroupAccent.LEFT, null, flourish, subTypes = false))
                typed.groupBy { it.threatType!! }
                    .entries
                    .sortedBy { it.key.ordinal }
                    .forEach { (type, groupRows) ->
                        add(LogGroupSpec("type-${type.name}", null, null, type, groupRows, subTypes = false))
                    }
                if (exits.isNotEmpty()) add(LogGroupSpec("left", "left", GroupAccent.LEFT, null, exits, subTypes = false))
            }
        }
    }
}

@Composable
private fun FilterRow(filter: LogsFilter, s: Strings.StringSet, onChange: (LogsFilter) -> Unit) {
    val options = listOf(
        LogsFilter.DECISIONS to s.logsFilterDecisions,
        LogsFilter.CONNECTIONS to s.logsFilterConnections
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = filter == value,
                onClick = { onChange(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ViewOptionsRow(
    groupBy: GroupBy,
    newestFirst: Boolean,
    shownOnly: Boolean,
    s: Strings.StringSet,
    onGroupBy: (GroupBy) -> Unit,
    onSortToggle: () -> Unit,
    onShownOnlyChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val groupOptions = listOf(
                GroupBy.TIMELINE to s.logsGroupTimeline,
                GroupBy.PROXIMITY to s.logsGroupProximity,
                GroupBy.TYPE to s.logsGroupType
            )
            groupOptions.forEach { (value, label) ->
                FilterChip(
                    selected = groupBy == value,
                    onClick = { onGroupBy(value) },
                    label = { Text(label) }
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSortToggle) {
                Icon(
                    imageVector = if (newestFirst) {
                        Icons.Filled.ArrowDownward
                    } else {
                        Icons.Filled.ArrowUpward
                    },
                    contentDescription = s.logsSortDesc
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.logsShownOnly,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = shownOnly,
                onCheckedChange = onShownOnlyChange
            )
        }
    }
}

@Composable
private fun GroupHeader(group: LogGroupSpec, s: Strings.StringSet) {
    val accent = when (group.accent) {
        GroupAccent.OFFICIAL, GroupAccent.RED -> DebugRed
        GroupAccent.YELLOW -> DebugAmber
        GroupAccent.OBLAST -> DebugBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (group.title) {
        "official" -> s.debugGroupOfficial
        "flourish" -> s.debugKindFlourish
        "red" -> s.debugTierRed
        "yellow" -> s.debugTierYellow
        "oblast" -> s.logsProxOblast
        "left" -> s.debugGroupLeft
        else -> group.title ?: ""
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
private fun ShowMoreButton(s: Strings.StringSet, onMore: () -> Unit) {
    TextButton(onClick = onMore) {
        Text(s.logsShowMore)
        Spacer(Modifier.width(2.dp))
        DoubleArrowDown()
    }
}

/** Double-chevron-down glyph for the "show more" control. */
@Composable
private fun DoubleArrowDown() {
    Box {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .offset(x = 0.dp, y = (-4).dp)
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .offset(x = 0.dp, y = 4.dp)
        )
    }
}

@Composable
private fun LogRowCard(
    row: LogRow,
    s: Strings.StringSet,
    lang: AppLanguage,
    now: Long,
    iconSet: ThreatIconSet
) {
    when (row) {
        is DecisionRow -> DecisionCard(row.entry, s, lang, now, iconSet)
        is ConnectionRow -> ConnectionCard(row.entry, s, lang, now)
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
    DebugLogKind.FLOURISH -> Icons.Filled.Star
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
    DebugLogKind.FLOURISH -> s.debugKindFlourish
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
private fun DecisionCard(
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
        DecisionLeadingIcon(entry, accent, lang, iconSet)
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
                        String.format(s.logDistanceFormat, km.roundToInt()),
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
                        s.debugLogShown,
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
private fun DecisionLeadingIcon(entry: DebugLogEntry, accent: Color, lang: AppLanguage, iconSet: ThreatIconSet) {
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
        val info = ThreatTypeCatalog.INFO.getValue(type)
        val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
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

@Composable
private fun ConnectionCard(entry: ConnLogEntry, s: Strings.StringSet, lang: AppLanguage, now: Long) {
    val online = entry.status == ConnStatus.ONLINE
    val accent = if (online) DebugGreen else DebugRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (online) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (online) s.connOnline else s.connOffline,
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
                entry.durationSec?.let { sec ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        String.format(s.connLogDurFormat, sec / 60, sec % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}