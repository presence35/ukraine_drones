package ua.ukrainedrones

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription as semanticsContentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val UkraineBlue = Color(0xFF005BBB)

/** Night mode's boxed section inside the Alerts card: a subtle indigo tint + border. */
private val NightSectionBg = Color(0xFF14142A)
private val NightSectionBorder = Color(0xFF2C3A66)

/** Collapse state of the Settings sections, hoisted to MainScreen and saved across switches. */
data class SettingsCollapseState(
    val location: Boolean = true,
    val nightMode: Boolean = true,
    val alerts: Boolean = true,
    val threats: Boolean = true,
    val system: Boolean = true
) {
    companion object {
        val Saver = Saver<SettingsCollapseState, BooleanArray>(
            save = { it.let { s -> BooleanArray(5).apply {
                this[0] = s.location; this[1] = s.nightMode; this[2] = s.alerts
                this[3] = s.threats; this[4] = s.system
            } } },
            restore = { b -> SettingsCollapseState(
                location = b.getOrElse(0) { true },
                nightMode = b.getOrElse(1) { true },
                alerts = b.getOrElse(2) { true },
                threats = b.getOrElse(3) { true },
                system = b.getOrElse(4) { true }
            ) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lang: AppLanguage,
    listState: LazyListState,
    onThreatsScrollHandled: () -> Unit,
    scrollToThreatsTick: Int,
    scrollToNightMode: Boolean,
    collapse: SettingsCollapseState,
    onCollapseChange: (SettingsCollapseState) -> Unit,
    hiddenTypes: Set<ThreatType>,
    silencedTypes: Set<ThreatType>,
    officialAlertsEnabled: Boolean,
    sirenOverride: Boolean,
    fastVibrationLevel: Int,
    slowVibrationLevel: Int,
    nightEnabled: Boolean,
    nightStartMin: Int,
    nightEndMin: Int,
    nightUseCustomZones: Boolean,
    slowRedKm: Int,
    slowYellowKm: Int,
    fastRedMin: Int,
    fastYellowMin: Int,
    nightSlowRedKm: Int,
    nightSlowYellowKm: Int,
    nightFastRedMin: Int,
    nightFastYellowMin: Int,
    nightSlowRedArmed: Boolean,
    nightSlowYellowArmed: Boolean,
    nightFastRedArmed: Boolean,
    nightFastYellowArmed: Boolean,
    nightZoneSirenOverride: Boolean,
    nightOfficialSirenOverride: Boolean,
    nightVibrationEnabled: Boolean,
    nightFastVibrationLevel: Int,
    nightSlowVibrationLevel: Int,
    disclaimerCollapsed: Boolean,
    disclaimerReadCount: Int,
    followMe: Boolean,
    pinnedCity: City?,
    threatCardSize: ThreatCardSize,
    iconSet: ThreatIconSet,
    showMapScale: Boolean,
    showTtaLines: Boolean,
    sheltersEnabled: Boolean,
    periodicGps: Boolean,
    deathAnimationEnabled: Boolean,
    followBullet: Boolean,
    neutralizedTallyEnabled: Boolean,
    fastGroupCollapsed: Boolean,
    slowGroupCollapsed: Boolean,
    versionName: String,
    isChecking: Boolean,
    latestVersion: String?,
    onBack: () -> Unit,
    activeExplainer: Explainer?,
    onExplainerChange: (Explainer?) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThreatMapToggle: (ThreatType, Boolean) -> Unit,
    onThreatAlertToggle: (ThreatType, Boolean) -> Unit,
    onThreatMapToggleAll: (Set<ThreatType>, Boolean) -> Unit,
    onThreatAlertToggleAll: (Set<ThreatType>, Boolean) -> Unit,
    onOfficialAlertsChange: (Boolean) -> Unit,
    onSirenOverrideChange: (Boolean) -> Unit,
    onFastVibrationChange: (Int) -> Unit,
    onSlowVibrationChange: (Int) -> Unit,
    onNightEnabledChange: (Boolean) -> Unit,
    onNightStartChange: (Int) -> Unit,
    onNightEndChange: (Int) -> Unit,
    onNightUseCustomZonesChange: (Boolean) -> Unit,
    onNightSlowRedChange: (Int) -> Unit,
    onNightSlowYellowChange: (Int) -> Unit,
    onNightFastRedChange: (Int) -> Unit,
    onNightFastYellowChange: (Int) -> Unit,
    onNightSlowRedArmedChange: (Boolean) -> Unit,
    onNightSlowYellowArmedChange: (Boolean) -> Unit,
    onNightFastRedArmedChange: (Boolean) -> Unit,
    onNightFastYellowArmedChange: (Boolean) -> Unit,
    onNightZoneSirenOverrideChange: (Boolean) -> Unit,
    onNightOfficialSirenOverrideChange: (Boolean) -> Unit,
    onNightVibrationEnabledChange: (Boolean) -> Unit,
    onNightFastVibrationChange: (Int) -> Unit,
    onNightSlowVibrationChange: (Int) -> Unit,
    onFollowMeChange: (Boolean) -> Unit,
    onPinnedCityChange: (City?) -> Unit,
    onPeriodicGpsChange: (Boolean) -> Unit,
    onDisclaimerCollapse: (Boolean) -> Unit,
    onDisclaimerShown: () -> Unit,
    onThreatCardSizeChange: (ThreatCardSize) -> Unit,
    onIconSetChange: (ThreatIconSet) -> Unit,
    onShowMapScaleChange: (Boolean) -> Unit,
    onShowTtaLinesChange: (Boolean) -> Unit,
    onSheltersEnabledChange: (Boolean) -> Unit,
    onDeathAnimationChange: (Boolean) -> Unit,
    onFollowBulletChange: (Boolean) -> Unit,
    onNeutralizedTallyChange: (Boolean) -> Unit,
    onFastGroupCollapse: (Boolean) -> Unit,
    onSlowGroupCollapse: (Boolean) -> Unit,
    onExit: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGuide: () -> Unit,
    onRelaunchSetup: () -> Unit
) {
    val s = Strings.get(lang)
    val appContext = LocalContext.current
    var batteryOptimized by remember { mutableStateOf(BatteryOptimization.isIgnoringBatteryOptimizations(appContext)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = BatteryOptimization.isIgnoringBatteryOptimizations(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var expandedType by remember { mutableStateOf<ThreatType?>(null) }
    // One-time explainers: shown when an advanced toggle is flipped for the first time.
    val explainerPrefs = remember { ZonePrefs(appContext) }
    val scope = rememberCoroutineScope()
    val explainerList = remember(s) { explainers(s) }
    var seenExplainers by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        seenExplainers = explainerList.map { it.id }
            .filter { explainerPrefs.explainerSeen(it).first() }
            .toSet()
    }
    val showExplainer: (String) -> Unit = { id ->
        explainerList.firstOrNull { it.id == id }?.let { exp ->
            if (exp.id !in seenExplainers) {
                seenExplainers = seenExplainers + exp.id
                scope.launch { explainerPrefs.setExplainerSeen(exp.id, true) }
                onExplainerChange(exp)
            }
        }
    }
    // One-time explainer dismissal: the dialog covers the list, so on close the eye is lost.
    // Snap back to the top of the section the user was tapping and give that row a subtle
    // border pulse so they re-anchor where they were.
    var flashId by remember { mutableStateOf<String?>(null) }
    val sectionOfExplainer: (String) -> Int = { id -> when (id) {
        "followMe" -> 1          // Location & Focus
        "nightMode" -> 2         // Night mode
        "officialAlerts", "sirenOverride" -> 3 // Alerts
        "threatToggles" -> 4     // Threats
        "cardSize" -> 5          // System & Display
        else -> 3
    } }
    val dismissExplainer: () -> Unit = {
        val exp = activeExplainer
        if (exp != null) {
            onExplainerChange(null)
            flashId = exp.id
            scope.launch {
                listState.animateScrollToItem(sectionOfExplainer(exp.id))
                delay(900)
                flashId = null
            }
        }
    }
    // Collapse states are hoisted to MainScreen (rememberSaveable) so they survive screen
    // switches and process death; only the disclaimer card keeps its own remember logic.
    var disclaimerExpanded by remember { mutableStateOf(disclaimerReadCount < 3 || !disclaimerCollapsed) }
    LaunchedEffect(Unit) {
        if (disclaimerReadCount < 3) onDisclaimerShown()
    }
    val onDisclaimerClick: () -> Unit = {
        disclaimerExpanded = !disclaimerExpanded
        onDisclaimerCollapse(!disclaimerExpanded)
    }

    // Scroll to section when requested by external triggers (e.g. ZonesSheet night mode badge).
    LaunchedEffect(scrollToThreatsTick) {
        if (scrollToThreatsTick > 0) {
            listState.animateScrollToItem(if (scrollToNightMode) 2 else 4)
            onThreatsScrollHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backButton)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // "Official signals come first" — first, default expanded, needs two taps to collapse.
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDisclaimerClick)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WarningTriangle()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                s.disclaimerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (disclaimerExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(visible = disclaimerExpanded) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    s.disclaimerBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                CollapsibleSectionCard(
                    title = s.locationSectionTitle,
                    icon = rememberVectorPainter(Icons.Default.LocationOn),
                    expanded = collapse.location,
                    subtitle = s.locationSubtitle(followMe, pinnedCity?.name(lang), periodicGps),
                    onToggle = { onCollapseChange(collapse.copy(location = !collapse.location)) }
                ) {
                    AlertToggleRow(
                        title = s.followMeTitle,
                        description = s.followMeDesc,
                        checked = followMe,
                        onCheckedChange = { v -> showExplainer("followMe"); onFollowMeChange(v) },
                        flash = flashId == "followMe"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PinCityRow(
                        lang = lang,
                        followMe = followMe,
                        pinnedCity = pinnedCity,
                        onChange = onPinnedCityChange
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.periodicGpsTitle,
                        description = s.periodicGpsDesc,
                        checked = periodicGps,
                        onCheckedChange = onPeriodicGpsChange
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    GpsCalibrationRow(
                        lang = lang,
                        s = s
                    )
                }
            }

            item {
                CollapsibleSectionCard(
                    title = s.nightModeLabel,
                    icon = painterResource(R.drawable.ic_moon),
                    expanded = collapse.nightMode,
                    subtitle = s.nightSubtitle(
                        nightEnabled,
                        nightStartMin,
                        nightEndMin,
                        nightZoneSirenOverride || nightOfficialSirenOverride
                    ),
                    onToggle = { onCollapseChange(collapse.copy(nightMode = !collapse.nightMode)) }
                ) {
                    NightModeCard(
                        lang = lang,
                        enabled = nightEnabled,
                        startMin = nightStartMin,
                        endMin = nightEndMin,
                        useCustomZones = nightUseCustomZones,
                        slowRedKm = nightSlowRedKm,
                        slowYellowKm = nightSlowYellowKm,
                        fastRedMin = nightFastRedMin,
                        fastYellowMin = nightFastYellowMin,
                        slowRedArmed = nightSlowRedArmed,
                        slowYellowArmed = nightSlowYellowArmed,
                        fastRedArmed = nightFastRedArmed,
                        fastYellowArmed = nightFastYellowArmed,
                        zoneSirenOverride = nightZoneSirenOverride,
                        officialSirenOverride = nightOfficialSirenOverride,
                        vibrationEnabled = nightVibrationEnabled,
                        fastVibrationLevel = nightFastVibrationLevel,
                        slowVibrationLevel = nightSlowVibrationLevel,
                        daySlowRedKm = slowRedKm,
                        daySlowYellowKm = slowYellowKm,
                        dayFastRedMin = fastRedMin,
                        dayFastYellowMin = fastYellowMin,
                        onEnabledChange = { v -> showExplainer("nightMode"); onNightEnabledChange(v) },
                        onStartChange = onNightStartChange,
                        onEndChange = onNightEndChange,
                        onUseCustomZonesChange = onNightUseCustomZonesChange,
                        onSlowRedChange = onNightSlowRedChange,
                        onSlowYellowChange = onNightSlowYellowChange,
                        onFastRedChange = onNightFastRedChange,
                        onFastYellowChange = onNightFastYellowChange,
                        onSlowRedArmedChange = onNightSlowRedArmedChange,
                        onSlowYellowArmedChange = onNightSlowYellowArmedChange,
                        onFastRedArmedChange = onNightFastRedArmedChange,
                        onFastYellowArmedChange = onNightFastYellowArmedChange,
                        onZoneSirenOverrideChange = onNightZoneSirenOverrideChange,
                        onOfficialSirenOverrideChange = onNightOfficialSirenOverrideChange,
                        onVibrationEnabledChange = onNightVibrationEnabledChange,
                        onFastVibrationChange = onNightFastVibrationChange,
                        onSlowVibrationChange = onNightSlowVibrationChange,
                        flash = flashId == "nightMode"
                    )
                }
            }

            item {
                CollapsibleSectionCard(
                    title = s.alertsLabel,
                    icon = rememberVectorPainter(Icons.Default.Notifications),
                    expanded = collapse.alerts,
                    subtitle = s.alertsSubtitle(officialAlertsEnabled, sirenOverride, sheltersEnabled),
                    onToggle = { onCollapseChange(collapse.copy(alerts = !collapse.alerts)) }
                ) {
                    AlertToggleRow(
                        title = s.officialAlertsTitle,
                        description = s.officialAlertsDesc,
                        checked = officialAlertsEnabled,
                        onCheckedChange = { v -> showExplainer("officialAlerts"); onOfficialAlertsChange(v) },
                        icon = painterResource(R.drawable.ic_trident),
                        note = s.officialAlertsRedTridentNote,
                        flash = flashId == "officialAlerts"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.sirenOverrideTitle,
                        description = s.sirenOverrideDesc,
                        checked = sirenOverride,
                        onCheckedChange = { v -> showExplainer("sirenOverride"); onSirenOverrideChange(v) },
                        icon = painterResource(R.drawable.ic_volume_up),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        flash = flashId == "sirenOverride"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.shelterSettingsTitle,
                        description = s.shelterSettingsDesc,
                        checked = sheltersEnabled,
                        onCheckedChange = onSheltersEnabledChange,
                        icon = painterResource(R.drawable.ic_shelter),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = sheltersEnabled) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AlertToggleRow(
                                title = s.shelterWithKidsTitle,
                                description = s.shelterWithKidsDesc,
                                checked = sheltersWithKids,
                                onCheckedChange = onSheltersWithKidsChange,
                                icon = painterResource(R.drawable.ic_adult_kid),
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.neutralizedTallyTitle,
                        description = s.neutralizedTallyDesc,
                        checked = neutralizedTallyEnabled,
                        onCheckedChange = onNeutralizedTallyChange,
                        icon = rememberVectorPainter(Icons.Default.Notifications),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            s.vibrationTitle,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            s.vibrationDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        VibrationSliderRow(
                            label = s.fastGroupLabel,
                            level = fastVibrationLevel,
                            accent = Color(0xFFE57373),
                            levelName = { vibrationLevelName(s, it) },
                            onLevelChange = onFastVibrationChange
                        )
                        VibrationSliderRow(
                            label = s.slowGroupLabel,
                            level = slowVibrationLevel,
                            accent = Color(0xFFF9A825),
                            levelName = { vibrationLevelName(s, it) },
                            onLevelChange = onSlowVibrationChange
                        )
                    }
                }
            }

            item {
                CollapsibleSectionCard(
                    title = s.threatsLabel,
                    icon = rememberVectorPainter(Icons.Default.Warning),
                    expanded = collapse.threats,
                    subtitle = s.threatsSubtitle(hiddenTypes.size, silencedTypes.size),
                    onToggle = { onCollapseChange(collapse.copy(threats = !collapse.threats)) }
                ) {
                    fastAndSlowGroups(lang).forEachIndexed { index, (groupIcon, groupTitle, types) ->
                        if (index == 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        val groupMapOn = types.none { it in hiddenTypes }
                        val groupAlertsOn = types.none { it in silencedTypes }
                        val groupCollapsed = if (index == 0) fastGroupCollapsed else slowGroupCollapsed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (index == 0) onFastGroupCollapse(!fastGroupCollapsed)
                                    else onSlowGroupCollapse(!slowGroupCollapsed)
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics { semanticsContentDescription = if (groupIcon == "\u26A1\uFE0F") s.fastGroupIconDesc else s.slowGroupIconDesc },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = groupIcon, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                groupTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconToggle(
                                icon = Icons.Filled.Place,
                                contentDescription = s.threatMapLabel,
                                on = groupMapOn,
                                enabled = true,
                                onClick = { onThreatMapToggleAll(types, !groupMapOn) }
                            )
                            IconToggle(
                                icon = Icons.Filled.Notifications,
                                contentDescription = s.threatAlertLabel,
                                on = groupAlertsOn,
                                enabled = true,
                                onClick = { onThreatAlertToggleAll(types, !groupAlertsOn) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (groupCollapsed) Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (!groupCollapsed) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                types.forEach { type ->
                                    ThreatSettingsCard(
                                        type = type,
                                        lang = lang,
                                        iconSet = iconSet,
                                        expanded = expandedType == type,
                                        onExpandChange = { expandedType = if (expandedType == type) null else type },
                                        hiddenTypes = hiddenTypes,
                                        silencedTypes = silencedTypes,
                                        onThreatMapToggle = { t, v -> showExplainer("threatToggles"); onThreatMapToggle(t, v) },
                                        onThreatAlertToggle = { t, v -> showExplainer("threatToggles"); onThreatAlertToggle(t, v) },
                                        flash = flashId == "threatToggles"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                CollapsibleSectionCard(
                    title = s.systemSectionTitle,
                    icon = painterResource(id = R.drawable.ic_language),
                    expanded = collapse.system,
                    subtitle = s.systemSubtitle(lang, threatCardSize, iconSet),
                    onToggle = { onCollapseChange(collapse.copy(system = !collapse.system)) }
                ) {
                    // Language Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LanguageFlag(
                            emoji = "\uD83C\uDDFA\uD83C\uDDE6",
                            active = lang == AppLanguage.UA,
                            onClick = { onLanguageChange(AppLanguage.UA) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageFlag(
                            emoji = "\uD83C\uDDE8\uD83C\uDDE6",
                            active = lang == AppLanguage.EN,
                            onClick = { onLanguageChange(AppLanguage.EN) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Card Size & Detail
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).explainerFlash(flashId == "cardSize")) {
                        Text(
                            s.cardSizeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        ThreatCardSizeSelector(
                            lang = lang,
                            selected = threatCardSize,
                            onChange = { v -> showExplainer("cardSize"); onThreatCardSizeChange(v) }
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skull),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.cardSkullNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.approxNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Icon Style Picker
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            s.iconSetTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        IconSetSelector(
                            lang = lang,
                            selected = iconSet,
                            onChange = onIconSetChange
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Visual map toggles
                    AlertToggleRow(
                        title = s.showMapScaleTitle,
                        description = s.showMapScaleDesc,
                        checked = showMapScale,
                        onCheckedChange = onShowMapScaleChange,
                        icon = painterResource(R.drawable.ic_scale),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.showTtaLinesTitle,
                        description = s.showTtaLinesDesc,
                        checked = showTtaLines,
                        onCheckedChange = onShowTtaLinesChange,
                        icon = painterResource(R.drawable.ic_explosion),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AlertToggleRow(
                        title = s.deathAnimationTitle,
                        description = s.deathAnimationDesc,
                        checked = deathAnimationEnabled,
                        onCheckedChange = onDeathAnimationChange,
                        icon = painterResource(R.drawable.ic_explosion),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = deathAnimationEnabled) {
                        Column(modifier = Modifier.padding(start = 24.dp)) {
                            AlertToggleRow(
                                title = s.followBulletTitle,
                                description = s.followBulletDesc,
                                checked = followBullet,
                                onCheckedChange = onFollowBulletChange,
                                icon = painterResource(R.drawable.ic_explosion),
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Battery Optimization
                    if (batteryOptimized) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    s.batteryGranted,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.batteryBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                s.batteryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.batteryBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { BatteryOptimization.requestExemption(appContext) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(s.batteryAllowButton, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onRelaunchSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(s.relaunchSetupTitle, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenGuide,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.guideSettingsButton, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                if (isChecking) {
                    Button(
                        onClick = onCheckUpdate,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(s.updateButton, fontWeight = FontWeight.SemiBold)
                    }
                } else if (latestVersion != null) {
                    Button(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${s.updateAvailableButton} · v$latestVersion",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = s.checkForUpdates,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(s.updateButton, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(s.exitButton, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Text(
                    "${s.madeBy} · v$versionName",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    activeExplainer?.let { exp ->
        FeatureExplainerDialog(explainer = exp, s = s, onDismiss = dismissExplainer)
    }

    }

private fun timeText(min: Int): String =
    String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightModeCard(
    lang: AppLanguage,
    enabled: Boolean,
    startMin: Int,
    endMin: Int,
    useCustomZones: Boolean,
    slowRedKm: Int,
    slowYellowKm: Int,
    fastRedMin: Int,
    fastYellowMin: Int,
    slowRedArmed: Boolean,
    slowYellowArmed: Boolean,
    fastRedArmed: Boolean,
    fastYellowArmed: Boolean,
    zoneSirenOverride: Boolean,
    officialSirenOverride: Boolean,
    vibrationEnabled: Boolean,
    fastVibrationLevel: Int,
    slowVibrationLevel: Int,
    daySlowRedKm: Int,
    daySlowYellowKm: Int,
    dayFastRedMin: Int,
    dayFastYellowMin: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onUseCustomZonesChange: (Boolean) -> Unit,
    onSlowRedChange: (Int) -> Unit,
    onSlowYellowChange: (Int) -> Unit,
    onFastRedChange: (Int) -> Unit,
    onFastYellowChange: (Int) -> Unit,
    onSlowRedArmedChange: (Boolean) -> Unit,
    onSlowYellowArmedChange: (Boolean) -> Unit,
    onFastRedArmedChange: (Boolean) -> Unit,
    onFastYellowArmedChange: (Boolean) -> Unit,
    onZoneSirenOverrideChange: (Boolean) -> Unit,
    onOfficialSirenOverrideChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onFastVibrationChange: (Int) -> Unit,
    onSlowVibrationChange: (Int) -> Unit,
    flash: Boolean = false
) {
    val s = Strings.get(lang)
    var editing by remember { mutableStateOf<String?>(null) }  // "start" | "end" | null

    Column {
        AlertToggleRow(
            title = s.nightModeLabel,
            description = s.nightModeDesc,
            checked = enabled,
            onCheckedChange = onEnabledChange,
            icon = painterResource(R.drawable.ic_moon),
            flash = flash
        )
        if (enabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NightTimeField(
                    label = s.nightStartTimeLabel,
                    minute = startMin,
                    onClick = { editing = "start" },
                    modifier = Modifier.weight(1f)
                )
                NightTimeField(
                    label = s.nightEndTimeLabel,
                    minute = endMin,
                    onClick = { editing = "end" },
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                SectionCaption(s.nightSoundLabel)
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                AlertToggleRow(
                    title = s.nightZoneSirenOverrideTitle,
                    description = s.nightZoneSirenOverrideDesc,
                    checked = zoneSirenOverride,
                    onCheckedChange = onZoneSirenOverrideChange,
                    icon = painterResource(R.drawable.ic_volume_up),
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AlertToggleRow(
                    title = s.nightOfficialSirenOverrideTitle,
                    description = s.nightOfficialSirenOverrideDesc,
                    checked = officialSirenOverride,
                    onCheckedChange = onOfficialSirenOverrideChange,
                    icon = painterResource(R.drawable.ic_trident)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AlertToggleRow(
                title = s.nightVibrationLabel,
                description = s.nightVibrationDesc,
                checked = vibrationEnabled,
                onCheckedChange = onVibrationEnabledChange
            )
            if (vibrationEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    VibrationSliderRow(
                        label = s.fastGroupLabel,
                        level = fastVibrationLevel,
                        accent = Color(0xFFE57373),
                        levelName = { vibrationLevelName(s, it) },
                        onLevelChange = onFastVibrationChange
                    )
                    VibrationSliderRow(
                        label = s.slowGroupLabel,
                        level = slowVibrationLevel,
                        accent = Color(0xFFF9A825),
                        levelName = { vibrationLevelName(s, it) },
                        onLevelChange = onSlowVibrationChange
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AlertToggleRow(
                title = s.nightCustomZonesTitle,
                description = s.nightCustomZonesDesc,
                checked = useCustomZones,
                onCheckedChange = onUseCustomZonesChange
            )
            if (useCustomZones) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    GroupedZoneSection {
                        SectionCaption(s.slowSectionLabel, leading = "\uD83D\uDC22", leadingDesc = s.slowGroupIconDesc)
                        ZoneRow(
                            value = slowRedKm,
                            range = 2f..20f,
                            unit = s.kmUnit,
                            accent = ZoneRedColor,
                            armed = slowRedArmed,
                            bellDesc = s.alertsBellToggle,
                            reference = daySlowRedKm,
                            dayLabel = s.dayShortLabel,
                            onArmedChange = onSlowRedArmedChange,
                            onCommit = onSlowRedChange
                        )
                        Spacer(Modifier.height(10.dp))
                        ZoneRow(
                            value = slowYellowKm,
                            range = 21f..50f,
                            unit = s.kmUnit,
                            accent = ZoneYellowColor,
                            armed = slowYellowArmed,
                            bellDesc = s.alertsBellToggle,
                            reference = daySlowYellowKm,
                            dayLabel = s.dayShortLabel,
                            onArmedChange = onSlowYellowArmedChange,
                            onCommit = onSlowYellowChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GroupedZoneSection {
                        SectionCaption(s.fastSectionLabel, leading = "\u26A1\uFE0F", leadingDesc = s.fastGroupIconDesc)
                        ZoneRow(
                            value = fastRedMin,
                            range = 2f..5f,
                            unit = s.minUnit,
                            accent = ZoneRedColor,
                            armed = fastRedArmed,
                            bellDesc = s.alertsBellToggle,
                            reference = dayFastRedMin,
                            dayLabel = s.dayShortLabel,
                            onArmedChange = onFastRedArmedChange,
                            onCommit = onFastRedChange
                        )
                        Spacer(Modifier.height(10.dp))
                        ZoneRow(
                            value = fastYellowMin,
                            range = 6f..20f,
                            unit = s.minUnit,
                            accent = ZoneYellowColor,
                            armed = fastYellowArmed,
                            bellDesc = s.alertsBellToggle,
                            reference = dayFastYellowMin,
                            dayLabel = s.dayShortLabel,
                            onArmedChange = onFastYellowArmedChange,
                            onCommit = onFastYellowChange
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!slowRedArmed || !slowYellowArmed || !fastRedArmed || !fastYellowArmed) {
                        Text(
                            s.nightMuteExitNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (editing != null) {
        val initial = if (editing == "start") startMin else endMin
        val timeState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(if (editing == "start") s.nightStartTimeLabel else s.nightEndTimeLabel)
            },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val minute = timeState.hour * 60 + timeState.minute
                    if (editing == "start") onStartChange(minute) else onEndChange(minute)
                    editing = null
                }) { Text(s.okButton) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(s.backButton) }
            }
        )
    }
}

@Composable
private fun NightTimeField(
    label: String,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                timeText(minute),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Painter? = null,
    iconTint: Color? = null,
    emoji: String? = null,
    note: String? = null,
    flash: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .explainerFlash(flash)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null) {
            Text(
                text = emoji,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
        } else {
            icon?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    colorFilter = iconTint?.let { c -> ColorFilter.tint(c) },
                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            note?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun vibrationLevelName(s: Strings.StringSet, level: Int): String = when (level) {
    0 -> s.vibrationOff
    1 -> s.vibrationSoft
    2 -> s.vibrationMedium
    4 -> s.vibrationUrgent
    else -> s.vibrationStrong
}

/** Bordered, rounded box that visually groups a set of zone slider rows (night custom zones). */
@Composable
private fun GroupedZoneSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = content
    )
}

/** One 0–4 vibration-strength slider (Fast/Slow), sized to fit the Alerts card. */
@Composable
private fun VibrationSliderRow(
    label: String,
    level: Int,
    accent: Color,
    levelName: (Int) -> String,
    onLevelChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var lastPreviewed by remember { mutableIntStateOf(level) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Slider(
            value = level.toFloat(),
            onValueChange = { newValue ->
                val newLevel = newValue.roundToInt()
                onLevelChange(newLevel)
                if (newLevel != lastPreviewed) {
                    lastPreviewed = newLevel
                    if (newLevel > 0) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern(newLevel), -1))
                    }
                }
            },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            ),
            modifier = Modifier.width(150.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            levelName(level),
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(76.dp)
        )
    }
}

/** A single threat's settings card: icon + name/desc, compact Map/Alerts switches on the right. */
@Composable
private fun ThreatSettingsCard(
    type: ThreatType,
    lang: AppLanguage,
    iconSet: ThreatIconSet,
    expanded: Boolean,
    onExpandChange: () -> Unit,
    hiddenTypes: Set<ThreatType>,
    silencedTypes: Set<ThreatType>,
    onThreatMapToggle: (ThreatType, Boolean) -> Unit,
    onThreatAlertToggle: (ThreatType, Boolean) -> Unit,
    flash: Boolean = false
) {
    val s = Strings.get(lang)
    val info = ThreatTypeCatalog.INFO.getValue(type)
    val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
    val description = if (lang == AppLanguage.UA) info.descriptionUa else info.descriptionEn
    val details = if (lang == AppLanguage.UA) info.detailsUa else info.detailsEn
    val joke = if (lang == AppLanguage.UA) info.jokeUa else info.jokeEn
    val onMap = type !in hiddenTypes
    val onAlerts = type !in silencedTypes
    val typicalSpeed = typicalSpeedKmh(type)?.roundToInt()

    Card(modifier = Modifier.fillMaxWidth().explainerFlash(flash)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThreatIcon(
                    type = type,
                    set = iconSet,
                    size = 36.dp,
                    contentDescription = label
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpandChange)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = s.moreInfoLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggle(
                        icon = Icons.Filled.Place,
                        contentDescription = s.threatMapLabel,
                        on = onMap,
                        enabled = true,
                        onClick = { onThreatMapToggle(type, !onMap) }
                    )
                    IconToggle(
                        icon = Icons.Filled.Notifications,
                        contentDescription = s.threatAlertLabel,
                        on = onAlerts,
                        enabled = true,
                        onClick = { onThreatAlertToggle(type, !onAlerts) }
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    typicalSpeed?.let {
                        Surface(
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                "~$it ${s.speedUnit}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    joke.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "— $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(
                                if (type == ThreatType.UNKNOWN) R.drawable.ic_unknown_cat
                                else IconCatalog.res(type, iconSet)
                            ),
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinCityRow(
    lang: AppLanguage,
    followMe: Boolean,
    pinnedCity: City?,
    onChange: (City?) -> Unit
) {
    val s = Strings.get(lang)
    val cities = remember(lang) {
        Cities.ALL.filter { it.major }
            .sortedBy { if (lang == AppLanguage.UA) it.nameUa else it.nameEn }
    }
    val label: (City) -> String = { c -> if (lang == AppLanguage.UA) c.nameUa else c.nameEn }
    val selected = pinnedCity?.let { label(it) } ?: ""
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!followMe) expanded = it },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = !followMe,
            label = { Text(s.pinCityTitle) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = { Text(s.pinCityDesc) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(label(city)) },
                    onClick = {
                        onChange(city)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GpsCalibrationRow(
    lang: AppLanguage,
    s: Strings.StringSet
) {
    val context = LocalContext.current
    val lastFixMs by LocationTracker.lastFixAtMs.collectAsState()
    val lastPreciseFixMs by LocationTracker.lastPreciseFixAtMs.collectAsState()
    val isRefreshing by LocationTracker.isRefreshing.collectAsState()
    var localRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(lastFixMs, isRefreshing) {
        if (!isRefreshing) localRefreshing = false
    }

    LaunchedEffect(localRefreshing) {
        if (localRefreshing) {
            delay(10_000)
            localRefreshing = false
        }
    }

    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            localRefreshing = true
            LocationTracker.forceRefresh { localRefreshing = false }
        }
    }
    val forceGps: () -> Unit = {
        if (fineGranted) {
            localRefreshing = true
            LocationTracker.forceRefresh { localRefreshing = false }
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val statusText = if (lastPreciseFixMs != null) {
        val now = System.currentTimeMillis()
        val age = formatAlertAge(now, lastPreciseFixMs, s)
        String.format(s.lastGpsFixFormat, if (age.isBlank()) s.gpsFixJustNow else age)
    } else if (lastFixMs != null) {
        s.networkLocationOnly
    } else {
        s.shelterGpsUnknown
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = if (lastPreciseFixMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                s.gpsStatusTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isRefreshing || localRefreshing) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            OutlinedButton(
                onClick = forceGps,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    s.calibrateGpsNow,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Mock threat + proximity driving the live card-size previews. */
private val PreviewThreat = Threat(
    id = "preview",
    type = ThreatType.SHAHED,
    title = "БпЛА",
    region = "Одеська область",
    district = null,
    locality = "Одеса",
    lat = 46.4825,
    lon = 30.7233,
    heading = null,
    bearingDeg = 210.0,
    status = "active",
    advisory = false,
    areaOnly = false,
    confirmations = 3,
    reliability = Reliability.MEDIUM,
    count = 2,
    explanationShort = "БпЛА курсом на Чорноморськ",
    speedKmh = 180.0,
    uncertaintyKm = 1.5,
    positionQuality = "approx",
    confirmedAt = null,
    confirmedAtMillis = null,
    updatedAt = null,
    updatedAtMillis = null
)

private val PreviewProximity = ThreatProximity(
    predicted = LatLng(46.48, 30.72),
    distToUserKm = 6.0,
    etaToUserMin = 4.5,
    params = ZoneParams(slowRedKm = 20, slowYellowKm = 50, fastRedMin = 5, fastYellowMin = 20),
    speedSource = SpeedSource.RECORDED,
    speedKmh = 180.0
)

/** Two selectable tiles, each a live scaled preview of that card size. */
@Composable
private fun ThreatCardSizeSelector(
    lang: AppLanguage,
    selected: ThreatCardSize,
    onChange: (ThreatCardSize) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThreatCardSize.values().forEach { size ->
            CardSizeTile(
                size = size,
                lang = lang,
                selected = size == selected,
                onClick = { onChange(size) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CardSizeTile(
    size: ThreatCardSize,
    lang: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Draw the real card at a fixed nominal width, then scale it down to the tile.
            // The height follows the scaled card exactly, so there's no dead space around it.
            // The small card is a compact top-left chip on the map, so its preview hugs the
            // tile's top-left corner at ~75% of the tile width instead of filling it.
            val density = LocalDensity.current
            val previewNominal = if (size == ThreatCardSize.SMALL) 300.dp else 340.dp
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val nominalW = with(density) { previewNominal.toPx() }
                val nominalWpx = with(density) { previewNominal.roundToPx() }
                val scale = if (size == ThreatCardSize.SMALL) {
                    constraints.maxWidth * 0.75f / nominalW
                } else {
                    constraints.maxWidth.toFloat() / nominalW
                }
                val cardPlaceable = subcompose("preview-card") {
                    Box(
                        modifier = Modifier
                            .width(previewNominal)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        ThreatPopupCard(
                            threat = PreviewThreat,
                            lang = lang,
                            proximity = PreviewProximity,
                            pinnedCity = null,
                            threatLevel = 7.0,
                            onDismiss = {},
                            cardSize = size,
                            interactive = false
                        )
                    }
                }[0].measure(
                    constraints.copy(
                        minWidth = nominalWpx,
                        maxWidth = nominalWpx,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    )
                )
                val height = (cardPlaceable.height * scale).roundToInt()
                layout(constraints.maxWidth, height) {
                    cardPlaceable.place(0, 0)
                }
            }
        }
    }
}

/** Icon-slot size inside an icon-set tile. */
private val IconTileSlot = 36.dp

/** Icon-style picker: four stacked full-width rows (one per real set — Classic, Photos,
 *  Army, Comic), each showing all seven icons side by side. */
@Composable
internal fun IconSetSelector(
    lang: AppLanguage,
    selected: ThreatIconSet,
    onChange: (ThreatIconSet) -> Unit,
    slot: Dp = IconTileSlot
) {
    val s = Strings.get(lang)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconSetTile(
            set = ThreatIconSet.CLASSIC,
            label = s.iconSetClassicLabel,
            selected = selected == ThreatIconSet.CLASSIC,
            onClick = { onChange(ThreatIconSet.CLASSIC) },
            slot = slot
        )
        IconSetTile(
            set = ThreatIconSet.PHOTO,
            label = s.iconSetPhotoLabel,
            selected = selected == ThreatIconSet.PHOTO,
            onClick = { onChange(ThreatIconSet.PHOTO) },
            slot = slot
        )
        IconSetTile(
            set = ThreatIconSet.ARMY,
            label = s.iconSetArmyLabel,
            selected = selected == ThreatIconSet.ARMY,
            onClick = { onChange(ThreatIconSet.ARMY) },
            slot = slot
        )
        IconSetTile(
            set = ThreatIconSet.COMIC,
            label = s.iconSetComicLabel,
            selected = selected == ThreatIconSet.COMIC,
            onClick = { onChange(ThreatIconSet.COMIC) },
            slot = slot
        )
    }
}

@Composable
internal fun IconSetTile(
    set: ThreatIconSet,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slot: Dp = IconTileSlot
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCatalog.photoTypes().forEach { type ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ThreatIcon(
                        type = type,
                        set = set,
                        size = slot,
                        contentDescription = label
                    )
                }
            }
        }
    }
}

/** Subtle one-shot blue border pulse around the row whose one-time explainer just closed. */
@Composable
private fun Modifier.explainerFlash(active: Boolean): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            alpha.snapTo(0f)
            alpha.animateTo(0.45f, tween(180))
            alpha.animateTo(0f, tween(520))
        }
    }
    return if (active) then(
        border(2.dp, UkraineBlue.copy(alpha = alpha.value), RoundedCornerShape(12.dp))
    ) else this
}

@Composable
private fun CollapsibleSectionCard(
    title: String,
    icon: Painter,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!expanded && !subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

@Composable
private fun WarningTriangle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.95f)
            lineTo(0f, h * 0.95f)
            close()
        }
        drawPath(path, color = Color(0xFFF9A825))
        drawLine(
            color = Color(0xFF3A2B00),
            start = Offset(w / 2f, h * 0.38f),
            end = Offset(w / 2f, h * 0.62f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF3A2B00),
            radius = 1.4f,
            center = Offset(w / 2f, h * 0.8f)
        )
    }
}

@Composable
internal fun LanguageFlag(
    emoji: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (active) Modifier.background(UkraineBlue.copy(alpha = 0.25f)) else Modifier
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                emoji,
                fontSize = 32.sp,
                // Inverted: the flag of the language you'd switch to is the colored one.
                modifier = Modifier.alpha(if (active) 0.3f else 1f)
            )
            if (label != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
