package ua.ukrainedrones

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription as semanticsContentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private enum class Screen { MAP, SETTINGS, GUIDE }

private val UkraineBlue = Color(0xFF005BBB)
private val UkraineYellow = Color(0xFFFFD500)
private val AlertRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.MAP) }

    // The settings heart pulses gently until Settings has been opened 10 times.
    val scope = rememberCoroutineScope()
    val prefs = remember { ZonePrefs(context.applicationContext) }
    var settingsHintRemaining by remember { mutableStateOf(0) }
    var guideFeatureId by remember { mutableStateOf<String?>(null) }
    var guideFromSettings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settingsHintRemaining = prefs.settingsHintRemaining().first()
    }

    val onExit: () -> Unit = {
        AlertService.stop(context)
        val activity = context as? Activity
        if (activity != null) activity.finishAffinity()
    }

    // The map stays composed under the Settings overlay so its camera and tiles are never
    // destroyed — returning from Settings used to reset the world into a low-zoom grid.
    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            uiState = uiState,
            settingsOpen = screen == Screen.SETTINGS,
            onOpenSettings = {
                if (settingsHintRemaining > 0) {
                    settingsHintRemaining--
                    scope.launch { prefs.setSettingsHintRemaining(settingsHintRemaining) }
                }
                screen = Screen.SETTINGS
                viewModel.autoCheckForUpdates(allowPopup = false)
            },
            onThreatTapped = { viewModel.selectThreat(it) },
            onDismissPopup = { viewModel.selectThreat(null) },
            onMapTapped = { viewModel.selectThreat(null) },
            onRedZoneChange = { viewModel.setRedZoneKm(it) },
            onYellowZoneChange = { viewModel.setYellowZoneKm(it) },
            onRedArmedChange = { viewModel.setRedArmed(it) },
            onYellowArmedChange = { viewModel.setYellowArmed(it) },
            onForceOfflineChange = viewModel::setForceOffline
        )
        if (screen == Screen.SETTINGS) {
            // Composed after MapScreen, so its handler is checked first on Back.
            BackHandler { screen = Screen.MAP }
            SettingsScreen(
                lang = uiState.language,
                hiddenTypes = uiState.hiddenTypes,
                silencedTypes = uiState.silencedTypes,
                fastAlertsSooner = uiState.fastAlertsSooner,
                officialAlertsEnabled = uiState.officialAlertsEnabled,
                sirenOverride = uiState.sirenOverride,
                disclaimerCollapsed = uiState.disclaimerCollapsed,
                followMe = uiState.followMe,
                pinnedCity = uiState.pinnedCity,
                redCities = uiState.redCities,
                threatCardSize = uiState.threatCardSize,
                showMapScale = uiState.showMapScale,
                versionName = BuildConfig.VERSION_NAME,
                isChecking = uiState.update is UpdateState.Checking,
                latestVersion = uiState.latestVersion,
                onBack = { screen = Screen.MAP },
                onLanguageChange = { viewModel.setLanguage(it) },
                onThreatMapToggle = { type, visible -> viewModel.setThreatMapVisible(type, visible) },
                onThreatAlertToggle = { type, enabled -> viewModel.setThreatAlertsEnabled(type, enabled) },
                onThreatMapToggleAll = { types, visible -> viewModel.setGroupThreatMapVisible(types, visible) },
                onThreatAlertToggleAll = { types, enabled -> viewModel.setGroupThreatAlertsEnabled(types, enabled) },
                onFastAlertsSoonerChange = { viewModel.setFastAlertsSooner(it) },
                onOfficialAlertsChange = { viewModel.setOfficialAlertsEnabled(it) },
                onSirenOverrideChange = { viewModel.setSirenOverride(it) },
                onFollowMeChange = { viewModel.setFollowMe(it) },
                onPinnedCityChange = { viewModel.setPinnedCity(it) },
                onDisclaimerCollapse = { viewModel.setDisclaimerCollapsed(it) },
                onThreatCardSizeChange = { viewModel.setThreatCardSize(it) },
                onShowMapScaleChange = { viewModel.setShowMapScale(it) },
                onExit = onExit,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onOpenGuide = {
                    guideFromSettings = true
                    guideFeatureId = null
                    screen = Screen.GUIDE
                }
            )
        }
        if (screen == Screen.GUIDE) {
            BackHandler { screen = if (guideFromSettings) Screen.SETTINGS else Screen.MAP }
            FeatureGuideScreen(
                s = Strings.get(uiState.language),
                initialFeatureId = guideFeatureId,
                onBack = { screen = if (guideFromSettings) Screen.SETTINGS else Screen.MAP }
            )
        }
    }

    // Auto-launch the installer once the APK is downloaded and permission is granted.
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onInstallResult(result.resultCode != Activity.RESULT_OK)
    }

    val updateState = uiState.update
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Downloaded && !uiState.needsInstallPermission) {
            val intent = viewModel.installIntent()
            if (intent != null) installLauncher.launch(intent)
        }
    }

    UpdateDialog(
        state = updateState,
        needsInstallPermission = uiState.needsInstallPermission,
        lang = uiState.language,
        onDownload = { viewModel.downloadUpdate() },
        onInstall = {
            val intent = viewModel.installIntent()
            if (intent != null) installLauncher.launch(intent)
        },
        onRetry = { viewModel.retryDownload() },
        onLater = { viewModel.dismissUpdate() },
        onOpenSettings = { viewModel.openInstallPermissionSettings() }
    )

    // First-install: a small language picker, dismissable. Choosing or skipping marks it done.
    if (!uiState.languageChosen) {
        LanguageChooseDialog(
            current = uiState.language,
            onChoose = { viewModel.setLanguage(it) },
            onLater = { viewModel.skipLanguageChoose() }
        )
    }
}

@Composable
private fun LanguageChooseDialog(
    current: AppLanguage,
    onChoose: (AppLanguage) -> Unit,
    onLater: () -> Unit
) {
    val s = Strings.get(current)
    AlertDialog(
        onDismissRequest = onLater,
        confirmButton = { TextButton(onClick = onLater) { Text(s.okButton) } },
        title = { Text(s.languageChooseTitle) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LanguageFlag(
                        emoji = "\uD83C\uDDFA\uD83C\uDDE6",
                        label = "Українська",
                        active = current == AppLanguage.UA,
                        onClick = { onChoose(AppLanguage.UA) },
                        modifier = Modifier.weight(1f)
                    )
                    LanguageFlag(
                        emoji = "\uD83C\uDDE8\uD83C\uDDE6",
                        label = "English",
                        active = current == AppLanguage.EN,
                        onClick = { onChoose(AppLanguage.EN) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    s.onboardingTipsTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OnboardingTipRow(
                    iconRes = R.drawable.ic_threat_shahed,
                    iconTint = Color.Unspecified,
                    text = s.onboardingTipTap
                )
                OnboardingTipRow(
                    iconRes = R.drawable.ic_settings_ua,
                    iconTint = Color.Unspecified,
                    text = s.onboardingTipSettings
                )
                OnboardingTipRow(
                    iconRes = R.drawable.ic_volume_up,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = s.onboardingTipSiren
                )
            }
        }
    )
}

@Composable
private fun OnboardingTipRow(iconRes: Int, iconTint: Color, text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    uiState: UiState,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onThreatTapped: (Threat) -> Unit,
    onDismissPopup: () -> Unit,
    onMapTapped: () -> Unit,
    onRedZoneChange: (Int) -> Unit,
    onYellowZoneChange: (Int) -> Unit,
    onRedArmedChange: (Boolean) -> Unit,
    onYellowArmedChange: (Boolean) -> Unit,
    onForceOfflineChange: (Boolean) -> Unit
) {
    val s = Strings.get(uiState.language)
    var recenterTick by remember { mutableStateOf(0) }
    var scaleMpp by remember { mutableStateOf(0.0) }
    var showZonesSheet by remember { mutableStateOf(false) }
    var zoomZone by remember { mutableStateOf<ThreatZone?>(null) }
    var zoomTick by remember { mutableStateOf(0) }
    var fitZonesTick by remember { mutableStateOf(0) }

    // Opening the panel also asks the map to centre + zoom to the full yellow zone.
    val openZonesPanel: () -> Unit = {
        showZonesSheet = true
        fitZonesTick++
    }

    val openSettings: () -> Unit = {
        showZonesSheet = false
        onOpenSettings()
    }

    // Back closes the popup first, then exits — fixes "back stuck on home page".
    BackHandler(enabled = uiState.selectedThreat != null) { onDismissPopup() }
    BackHandler(enabled = showZonesSheet) { showZonesSheet = false }

    Scaffold(
        topBar = {
            val activeZone = uiState.activeZone
            val officialOnly = uiState.focusOblastAlertActive && activeZone == null
            val zoneColor = when (activeZone) {
                ThreatZone.INNER -> AlertRed
                ThreatZone.OUTER -> Color(0xFFF9A825)
                null -> MaterialTheme.colorScheme.surface
            }
            val containerColor = if (activeZone != null) zoneColor else MaterialTheme.colorScheme.surface
            val pinnedCityName = if (uiState.followMe) null else uiState.pinnedCity?.let {
                if (uiState.language == AppLanguage.UA) it.nameUa else it.nameEn
            }
            val alertText = when (activeZone) {
                ThreatZone.INNER -> s.redZoneAlert
                ThreatZone.OUTER -> s.yellowZoneAlert
                null -> when {
                    officialOnly -> String.format(s.alertBannerFormat, uiState.focusBannerCity)
                    pinnedCityName != null -> pinnedCityName
                    else -> s.appTitle
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerColor)
                    .then(
                        if (officialOnly) Modifier.border(2.5.dp, AlertRed)
                        else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UkraineEmblem(
                    active = uiState.focusOblastAlertActive,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = alertText,
                        modifier = Modifier.clickable(onClick = { recenterTick++ }),
                        style = when {
                            activeZone != null -> MaterialTheme.typography.titleMedium.copy(color = Color.White)
                            officialOnly -> MaterialTheme.typography.titleMedium.copy(color = Color(0xFFE57373))
                            else -> MaterialTheme.typography.titleMedium.copy(
                                brush = Brush.linearGradient(
                                    listOf(UkraineBlue, UkraineYellow)
                                )
                            )
                        }
                    )
                }
                ConnectionStatus(
                    neptunDown = uiState.neptunDown,
                    backupActive = uiState.backupActive,
                    backupUp = uiState.backupUp,
                    backupSeen = uiState.backupSeen,
                    backupOfflineElapsedSec = uiState.backupOfflineElapsedSec,
                    offlineElapsedSec = uiState.offlineElapsedSec,
                    forceOffline = uiState.forceOffline,
                    onForceOfflineChange = onForceOfflineChange,
                    s = s,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = openSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_ua),
                        contentDescription = s.settingsButton,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    NeptunMapView(
                        uiState = uiState,
                        lang = uiState.language,
                        onScaleChange = { scaleMpp = it },
                        onThreatTapped = onThreatTapped,
                        onMapTapped = onMapTapped,
                        recenterTick = recenterTick,
                        zoomZone = zoomZone,
                        zoomTick = zoomTick,
                        fitZonesTick = fitZonesTick,
                        paused = settingsOpen,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (uiState.showMapScale) {
                        ScaleIndicator(
                            metersPerPixel = scaleMpp,
                            lang = uiState.language,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 12.dp)
                        )
                    }
                    if (!uiState.followMe) {
                        uiState.pinnedCity?.let { city ->
                            val cityName = if (uiState.language == AppLanguage.UA) city.nameUa else city.nameEn
                            PinnedPill(
                                text = String.format(s.mapPillPinned, cityName),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = 40.dp)
                            )
                        }
                    }
                    ZoneButtons(
                        redArmed = uiState.redArmed,
                        yellowArmed = uiState.yellowArmed,
                        lang = uiState.language,
                        onZoneTap = { zone ->
                            zoomZone = zone
                            zoomTick++
                        },
                        onEditZones = openZonesPanel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }

                Surface(tonalElevation = 2.dp) {
                    val innerCounts = uiState.threatsInner.groupingBy { it.type }.eachCount()
                    val outerCounts = uiState.threatsOuter.groupingBy { it.type }.eachCount()
                    val total = ThreatType.values().sumOf {
                        (innerCounts[it] ?: 0) + (outerCounts[it] ?: 0)
                    }
                    if (total == 0) {
                        Text(
                            s.noThreatsMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThreatType.values().forEach { type ->
                                val count = (innerCounts[type] ?: 0) + (outerCounts[type] ?: 0)
                                val visible = type !in uiState.hiddenTypes
                                val alerting = type !in uiState.silencedTypes
                                if (count > 0 && visible && alerting) {
                                    ThreatStatusCell(
                                        type = type,
                                        count = count,
                                        enabled = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Threat detail popup, overlaid top-center above the zone bar
            uiState.selectedThreat?.let { threat ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    ThreatPopupCard(
                        threat = threat,
                        lang = uiState.language,
                        proximity = uiState.selectedThreatInfo,
                        pinnedCity = if (uiState.followMe) null else uiState.pinnedCity,
                        threatLevel = uiState.threatLevel,
                        fastAlertsSooner = uiState.fastAlertsSooner,
                        cardSize = uiState.threatCardSize,
                        alertsOff = threat.type in uiState.silencedTypes,
                        onDismiss = onDismissPopup,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Alert-zone editor: a non-modal bottom panel over the live map so the
            // red/yellow circles update while you drag, and the map above stays pannable.
            if (showZonesSheet) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val dismissThresholdPx = with(density) { 80.dp.toPx() }
                        var dragAccum by remember { mutableFloatStateOf(0f) }
                        val closeSheet = { showZonesSheet = false }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = closeSheet)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (dragAccum > dismissThresholdPx) closeSheet()
                                            dragAccum = 0f
                                        },
                                        onDragCancel = { dragAccum = 0f }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragAccum += dragAmount
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp, bottom = 2.dp)
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF555555))
                            )
                        }
                        ZonesEditContent(
                            redKm = uiState.redZoneKm,
                            yellowKm = uiState.yellowZoneKm,
                            redArmed = uiState.redArmed,
                            yellowArmed = uiState.yellowArmed,
                            lang = uiState.language,
                            onRedZoneChange = onRedZoneChange,
                            onYellowZoneChange = onYellowZoneChange,
                            onRedArmedChange = onRedArmedChange,
                            onYellowArmedChange = onYellowArmedChange,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UkraineEmblem(active: Boolean, modifier: Modifier = Modifier) {
    val red = AlertRed
    Box(modifier = modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (active) {
            // Soft red halo so the emblem reads as "glowing red" during an official alert.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(listOf(red.copy(alpha = 0.45f), Color.Transparent)),
                        CircleShape
                    )
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_trident),
            contentDescription = null,
            colorFilter = if (active) ColorFilter.tint(red) else null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ScaleIndicator(metersPerPixel: Double, lang: AppLanguage, modifier: Modifier = Modifier) {
    if (metersPerPixel <= 0.0) return
    val s = Strings.get(lang)
    val density = LocalDensity.current
    // Pick the largest "nice" round distance that fits ~84dp, and draw its bar at real scale.
    val candidateMeters = listOf(
        50.0, 100.0, 200.0, 300.0, 500.0, 1000.0,
        2000.0, 5000.0, 10000.0, 20000.0, 50000.0
    )
    val targetPx = with(density) { 84.dp.toPx() }
    val chosen = candidateMeters.lastOrNull { (it / metersPerPixel) <= targetPx }
        ?: candidateMeters.first()
    val barPx = (chosen / metersPerPixel).toFloat().coerceAtMost(targetPx)
    val barDp = with(density) { barPx.toDp() }
    val label = if (chosen >= 1000.0) "${(chosen / 1000.0).roundToInt()} ${s.kmUnit}"
    else "${chosen.roundToInt()} ${s.meterUnit}"

    // Google-Maps-style scale: label above a thin alternating bar. The label is drawn
    // in a bold white font (no background) so it stays readable over the map tiles.
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier
                .border(width = 1.dp, color = Color.White)
                .height(3.dp)
                .width(barDp)
        ) {
            val seg = barDp / 4f
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .width(seg)
                        .height(3.dp)
                        .background(
                            if (i % 2 == 0) Color.Black
                            else Color.White
                        )
                )
            }
        }
    }
}

@Composable
private fun ZoneButtons(
    redArmed: Boolean,
    yellowArmed: Boolean,
    lang: AppLanguage,
    onZoneTap: (ThreatZone) -> Unit,
    onEditZones: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = Strings.get(lang)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!redArmed && !yellowArmed) {
            AllAlertsOffWarning(label = s.allAlertsOffLabel, onClick = onEditZones)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ZoneButton(ThreatZone.INNER, redArmed, s.zoneButtonRed, onZoneTap)
            ZoneButton(ThreatZone.OUTER, yellowArmed, s.zoneButtonYellow, onZoneTap)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .semantics { semanticsContentDescription = s.editZonesLabel }
                    .clickable(onClick = onEditZones),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = s.editZonesLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AllAlertsOffWarning(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Color(0xFF777777),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Color(0xFF777777),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF9A825),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ZoneButton(
    zone: ThreatZone,
    armed: Boolean,
    contentDescription: String,
    onZoneTap: (ThreatZone) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(width = 16.dp, height = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!armed) {
                // Dimmed bell floating above the pill signals this zone's alerts are off.
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        ZonePill(zone, armed, contentDescription, onZoneTap)
    }
}

@Composable
private fun ZonePill(
    zone: ThreatZone,
    armed: Boolean,
    contentDescription: String,
    onZoneTap: (ThreatZone) -> Unit
) {
    val zoneColor = when (zone) {
        ThreatZone.INNER -> AlertRed
        ThreatZone.OUTER -> Color(0xFFF9A825)
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (armed) zoneColor else Color(0xFF2A2A2A))
            .border(2.dp, if (armed) zoneColor else Color(0xFF666666), CircleShape)
            .semantics { semanticsContentDescription = contentDescription }
            .clickable(onClick = { onZoneTap(zone) }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_zoom_in),
            contentDescription = null,
            tint = if (armed) Color.White else Color(0xFF777777),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PinnedPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(UkraineBlue)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConnectionStatus(
    neptunDown: Boolean,
    backupActive: Boolean,
    backupUp: Boolean,
    backupSeen: Boolean,
    backupOfflineElapsedSec: Long?,
    offlineElapsedSec: Long?,
    forceOffline: Boolean,
    onForceOfflineChange: (Boolean) -> Unit,
    s: Strings.StringSet,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        neptunDown -> Color(0xFFE57373) // red — NEPTUN offline (real or simulated)
        backupActive -> Color(0xFFF9A825) // amber — NEPTUN alive but on the backup source
        else -> Color(0xFF4CAF50)
    }
    val label = when {
        neptunDown -> offlineElapsedSec?.let { String.format(s.offlineUiFormat, String.format(s.offlineDurMinFormat, it / 60)) }
            ?: s.connOffline
        backupActive -> s.connBackup
        else -> s.connOnline
    }
    var showInfo by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable { showInfo = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
    if (showInfo) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(s.backButton) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s.connStatusTitle)
                }
            },
            text = {
                val neptunStatus = if (!neptunDown) s.connOnline
                else offlineElapsedSec?.let { String.format(s.offlineUiFormat, String.format(s.offlineDurMinFormat, it / 60)) }
                    ?: s.connOffline
                val backupStatus = if (backupUp) s.connOnline
                else backupOfflineElapsedSec?.let { String.format(s.offlineUiFormat, String.format(s.offlineDurMinFormat, it / 60)) }
                    ?: s.connOffline
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceStatusRow(
                        color = if (neptunDown) Color(0xFFE57373) else Color(0xFF4CAF50),
                        name = s.connNeptunLabel,
                        status = neptunStatus,
                        active = !backupActive,
                        activeLabel = s.connActiveLabel
                    )
                    SourceStatusRow(
                        color = when {
                            backupUp -> Color(0xFF4CAF50)
                            backupSeen -> Color(0xFFF9A825)
                            else -> Color(0xFFE57373)
                        },
                        name = s.connBackupLabel,
                        status = backupStatus,
                        active = backupActive,
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
                    val annotated = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(if (backupActive) s.attributionBackup else s.attributionText)
                        }
                    }
                    ClickableText(text = annotated, onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(if (backupActive) "https://alerts.com.ua/" else "https://neptun.in.ua/")
                            )
                        )
                    })
                }
            }
        )
    }
}

@Composable
private fun SourceStatusRow(color: Color, name: String, status: String, active: Boolean, activeLabel: String) {
    val accent = Color(0xFFF9A825)
    val nameColor = if (active) accent else MaterialTheme.colorScheme.onSurface
    val statusColor = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val statusWeight = if (active) FontWeight.Bold else FontWeight.Normal
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
        Spacer(Modifier.width(6.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = statusWeight, color = statusColor)
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

@Composable
private fun ThreatStatusCell(
    type: ThreatType,
    count: Int,
    enabled: Boolean
) {
    val active = enabled && count > 0
    val lineAlpha by rememberInfiniteTransition(label = "threatLine").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "lineAlpha"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(id = threatIconRes(type)),
            contentDescription = null,
            tint = if (enabled) Color.Unspecified else Color(0xFF9E9E9E),
            modifier = Modifier.size(18.dp)
        )
        if (count > 0) {
            Spacer(Modifier.height(2.dp))
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .height(2.dp)
                .width(18.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
                .alpha(if (active) lineAlpha else 0.4f)
        )
    }
}

private fun threatIconRes(type: ThreatType): Int = when (type) {
    ThreatType.SHAHED -> R.drawable.shahed
    ThreatType.FPV_LOITERING -> R.drawable.ic_threat_fpv
    ThreatType.CRUISE_MISSILE -> R.drawable.ic_threat_cruise
    ThreatType.BALLISTIC -> R.drawable.ic_threat_ballistic
    ThreatType.KAB -> R.drawable.ic_threat_kab
    ThreatType.AVIATION -> R.drawable.ic_threat_aviation
    ThreatType.RECON -> R.drawable.ic_threat_recon
    ThreatType.UNKNOWN -> R.drawable.ic_threat_unknown
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    needsInstallPermission: Boolean,
    lang: AppLanguage,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = Strings.get(lang)
    when (state) {
        is UpdateState.Available -> {
            val notes = if (lang == AppLanguage.UA) state.info.notesUa else state.info.notesEn
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text(s.updateAvailableTitle) },
                text = {
                    Column {
                        Text(
                            "${s.updateVersionLabel}: ${state.info.versionName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(s.updateNotesTitle, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            notes.split('\n').filter { it.isNotBlank() }.forEachIndexed { i, line ->
                                if (i > 0) Spacer(Modifier.height(4.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDownload) { Text(s.updateDownload) } },
                dismissButton = { TextButton(onClick = onLater) { Text(s.updateLater) } }
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(s.updateDownload) },
                text = {
                    Column {
                        Text(
                            String.format(s.updateDownloading, (state.progress * 100).toInt().coerceIn(0, 100)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { state.progress })
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
        is UpdateState.Downloaded -> {
            if (needsInstallPermission) {
                AlertDialog(
                    onDismissRequest = onLater,
                    title = { Text(s.updateInstallPermissionTitle) },
                    text = { Text(s.updateInstallPermissionBody) },
                    confirmButton = { TextButton(onClick = onOpenSettings) { Text(s.updateOpenSettings) } },
                    dismissButton = { TextButton(onClick = onInstall) { Text(s.updateInstall) } }
                )
            } else {
                AlertDialog(
                    onDismissRequest = onLater,
                    title = { Text(s.updateReadyToInstallTitle) },
                    text = { Text(s.updateReadyToInstallBody) },
                    confirmButton = { TextButton(onClick = onInstall) { Text(s.updateInstall) } },
                    dismissButton = { TextButton(onClick = onLater) { Text(s.updateLater) } }
                )
            }
        }
        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text(s.updateFailedTitle) },
                text = { Text(state.message.orEmpty()) },
                confirmButton = { TextButton(onClick = onRetry) { Text(s.updateRetry) } },
                dismissButton = { TextButton(onClick = onLater) { Text(s.updateLater) } }
            )
        }
        else -> Unit
    }
}
