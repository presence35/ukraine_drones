package ua.ukrainedrones

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription as semanticsContentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private enum class Screen { MAP, SETTINGS, GUIDE, SHELTERS, LOGS }

private val UkraineBlue = Color(0xFF005BBB)
private val UkraineYellow = Color(0xFFFFD500)
private val AlertRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val now by viewModel.now.collectAsState()
    val lastFrameAt by viewModel.lastFrameAt.collectAsState(initial = 0L)
    val context = LocalContext.current

    var screen by remember { mutableStateOf(Screen.MAP) }
    // The neutralizing card + map death flourish only run while the map is the visible
    // screen — off-map (Settings/Shelters/Guide) the popup just closes silently.
    LaunchedEffect(screen) { viewModel.setMapVisible(screen == Screen.MAP) }
    var showConnectionInfo by remember { mutableStateOf(false) }
    var showZonesSheet by remember { mutableStateOf(false) }
    var activeExplainer by remember { mutableStateOf<Explainer?>(null) }

    // The Settings-open update check surfaces here as a snackbar with a Download action.
    val updateReminderTick by viewModel.updateReminderTick.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateReminderTick) {
        if (updateReminderTick > 0) {
            val s = Strings.get(uiState.language)
            val result = snackbarHostState.showSnackbar(
                message = String.format(s.updateAvailableOnOpen, uiState.latestVersion.orEmpty()),
                actionLabel = s.updateDownload,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.showDownloadScreen()
        }
    }

    // Only one overlay can be up at a time: opening any of them closes the others (and the
    // threat popup), and an arriving update dialog outranks everything.
    LaunchedEffect(showConnectionInfo, showZonesSheet, uiState.update) {
        if (showConnectionInfo || showZonesSheet || uiState.update !is UpdateState.Idle) {
            viewModel.selectThreat(null)
        }
        if (showConnectionInfo) {
            showZonesSheet = false
            activeExplainer = null
        }
        if (showZonesSheet) showConnectionInfo = false
        if (uiState.update !is UpdateState.Idle) {
            showConnectionInfo = false
            showZonesSheet = false
            activeExplainer = null
        }
    }

    // The System-status sheet never lingers over the map: it closes when an alert starts and
    // whenever the user navigates to Settings/Guide, so returning to the map is always clean.
    LaunchedEffect(screen, uiState.activeZone, uiState.focusOblastAlertActive) {
        if (screen != Screen.MAP || uiState.activeZone != null || uiState.focusOblastAlertActive) {
            showConnectionInfo = false
        }
    }

    // The settings heart pulses gently until Settings has been opened 10 times.
    val scope = rememberCoroutineScope()
    val prefs = remember { ZonePrefs(context.applicationContext) }
    var settingsHintRemaining by remember { mutableStateOf(0) }
    var shelterTipStage by remember { mutableStateOf(0) }
    var guideFeatureId by remember { mutableStateOf<String?>(null) }
    var guideFromSettings by remember { mutableStateOf(false) }
    var sheltersFromSettings by remember { mutableStateOf(false) }
    var scrollToThreatsTick by remember { mutableStateOf(0) }
    var wizardOpenedDuringAlert by remember { mutableStateOf(false) }
    var wizardFromSettings by remember { mutableStateOf(false) }
    var headerHeightPx by remember { mutableStateOf(0) }
    var wizardSettleDeadline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(3000); wizardSettleDeadline = true }
    val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var settingsCollapse by rememberSaveable(stateSaver = SettingsCollapseState.Saver) { mutableStateOf(SettingsCollapseState()) }
    // The zones sheet edits whatever the map is currently showing: night settings while the
    // night window is active and separate night zones are enabled, day settings otherwise.
    val editingNight = uiState.nightActive && uiState.nightUseCustomZones
    LaunchedEffect(Unit) {
        settingsHintRemaining = prefs.settingsHintRemaining().first()
        shelterTipStage = prefs.shelterTipStage().first()
    }

    val onExit: () -> Unit = {
        AlertService.stop(context)
        val activity = context as? Activity
        if (activity != null) activity.finishAffinity()
    }

    val openSettings: () -> Unit = {
        if (settingsHintRemaining > 0) {
            settingsHintRemaining--
            scope.launch { prefs.setSettingsHintRemaining(settingsHintRemaining) }
        }
        screen = Screen.SETTINGS
        // Settings always opens fully collapsed — no saved open/closed state across visits.
        settingsCollapse = SettingsCollapseState()
        viewModel.checkForUpdatesOnSettingsOpen()
    }

    // A notification tap reveals a threat on the map: leave Settings and show it.
    LaunchedEffect(uiState.revealRequest?.tick) {
        if (uiState.revealRequest != null && screen != Screen.MAP) screen = Screen.MAP
    }

    // Tally-tap replay flourish: open the map and close every modal so nothing steals focus
    // from the shot-down show (and nothing overlaps it).
    LaunchedEffect(uiState.flourish?.tick) {
        if (uiState.flourish != null) {
            screen = Screen.MAP
            showZonesSheet = false
            showConnectionInfo = false
            activeExplainer = null
            viewModel.selectThreat(null)
        }
    }

    // The map stays composed under the Settings overlay so its camera and tiles are never
    // destroyed — returning from Settings used to reset the world into a low-zoom grid.
    CompositionLocalProvider(LocalHapticsEnabled provides uiState.hapticsEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            uiState = uiState,
            now = now,
            settingsOpen = screen == Screen.SETTINGS,
            mapVisible = screen == Screen.MAP,
            onOpenSettings = openSettings,
            onOpenThreatSettings = {
                openSettings()
                // The shortcut scrolls to the night/threats card: expand it so the landing
                // isn't an empty collapsed header.
                settingsCollapse = if (uiState.nightActive) settingsCollapse.copy(nightMode = true)
                else settingsCollapse.copy(threats = true)
                scrollToThreatsTick++
            },
            onThreatTapped = { showConnectionInfo = false; showZonesSheet = false; viewModel.selectThreat(it) },
            onThreatStripTap = { viewModel.panToThreat(it) },
            onDismissPopup = { viewModel.selectThreat(null) },
            onMapTapped = { viewModel.selectThreat(null) },
            onSlowRedChange = { if (editingNight) viewModel.setNightSlowRedKm(it) else viewModel.setSlowRedKm(it) },
            onSlowYellowChange = { if (editingNight) viewModel.setNightSlowYellowKm(it) else viewModel.setSlowYellowKm(it) },
            onFastRedChange = { if (editingNight) viewModel.setNightFastRedMin(it) else viewModel.setFastRedMin(it) },
            onFastYellowChange = { if (editingNight) viewModel.setNightFastYellowMin(it) else viewModel.setFastYellowMin(it) },
            onSlowRedArmedChange = { if (editingNight) viewModel.setNightSlowRedArmed(it) else viewModel.setSlowRedArmed(it) },
            onSlowYellowArmedChange = { if (editingNight) viewModel.setNightSlowYellowArmed(it) else viewModel.setSlowYellowArmed(it) },
            onFastRedArmedChange = { if (editingNight) viewModel.setNightFastRedArmed(it) else viewModel.setFastRedArmed(it) },
            onFastYellowArmedChange = { if (editingNight) viewModel.setNightFastYellowArmed(it) else viewModel.setFastYellowArmed(it) },
            onThreatCardSizeChange = { viewModel.setThreatCardSize(it) },
            onForceOfflineChange = viewModel::setForceOffline,
            onNeutralize = { id -> viewModel.neutralizeThreat(id) },
            onFlybyFinished = { id -> viewModel.onFlybyFinished(id) },
            showConnectionInfo = showConnectionInfo,
            onShowConnectionInfoChange = { showConnectionInfo = it },
            showZonesSheet = showZonesSheet,
            onShowZonesSheetChange = { showZonesSheet = it },
            onOpenShelters = {
                sheltersFromSettings = false
                screen = Screen.SHELTERS
            },
            onOpenLogs = {
                showConnectionInfo = false
                screen = Screen.LOGS
            },
            onShelterModeChange = { viewModel.setShelterModeActive(it) },
            shelterTipStage = shelterTipStage,
            settingsHintRemaining = settingsHintRemaining,
            onHeaderHeightChange = { headerHeightPx = it },
            onShelterTipAdvance = {
                val next = (shelterTipStage + 1).coerceAtMost(6)
                shelterTipStage = next
                scope.launch { prefs.setShelterTipStage(next) }
            }
        )
        if (screen == Screen.SETTINGS) {
            // Composed after MapScreen, so its handler is checked first on Back.
            BackHandler { screen = Screen.MAP }
            SettingsScreen(
                lang = uiState.language,
                listState = settingsListState,
                onThreatsScrollHandled = { scrollToThreatsTick = 0 },
                scrollToThreatsTick = scrollToThreatsTick,
                scrollToNightMode = uiState.nightActive,
                collapse = settingsCollapse,
                onCollapseChange = { settingsCollapse = it },
                hiddenTypes = uiState.hiddenTypes,
                silencedTypes = uiState.silencedTypes,
                officialAlertsEnabled = uiState.officialAlertsEnabled,
                officialAlertCityScope = uiState.officialAlertCityScope,
                sirenOverride = uiState.sirenOverride,
                nightEnabled = uiState.nightEnabled,
                nightStartMin = uiState.nightStartMin,
                nightEndMin = uiState.nightEndMin,
                nightUseCustomZones = uiState.nightUseCustomZones,
                slowRedKm = uiState.slowRedKm,
                slowYellowKm = uiState.slowYellowKm,
                fastRedMin = uiState.fastRedMin,
                fastYellowMin = uiState.fastYellowMin,
                nightSlowRedKm = uiState.nightSlowRedKm,
                nightSlowYellowKm = uiState.nightSlowYellowKm,
                nightFastRedMin = uiState.nightFastRedMin,
                nightFastYellowMin = uiState.nightFastYellowMin,
                nightSlowRedArmed = uiState.nightSlowRedArmed,
                nightSlowYellowArmed = uiState.nightSlowYellowArmed,
                nightFastRedArmed = uiState.nightFastRedArmed,
                nightFastYellowArmed = uiState.nightFastYellowArmed,
                nightZoneSirenOverride = uiState.nightZoneSirenOverride,
                nightOfficialSirenOverride = uiState.nightOfficialSirenOverride,
                disclaimerCollapsed = uiState.disclaimerCollapsed,
                disclaimerReadCount = uiState.disclaimerReadCount,
                followMe = uiState.followMe,
                pinnedCity = uiState.pinnedCity,
                threatCardSize = uiState.threatCardSize,
                iconSet = uiState.iconSet,
                showMapScale = uiState.showMapScale,
                showMediumCities = uiState.showMediumCities,
                showSmallCities = uiState.showSmallCities,
                sheltersEnabled = uiState.sheltersEnabled,
                periodicGps = uiState.periodicGps,
                calmMessagesEnabled = uiState.calmMessagesEnabled,
                hapticsEnabled = uiState.hapticsEnabled,
                deathAnimationEnabled = uiState.deathAnimationEnabled,
                followBullet = uiState.followBullet,
                neutralizedTallyEnabled = uiState.neutralizedTallyEnabled,
                neutralizedTallyAllUkraine = uiState.neutralizedTallyAllUkraine,
                fastGroupCollapsed = uiState.fastGroupCollapsed,
                slowGroupCollapsed = uiState.slowGroupCollapsed,
                versionName = BuildConfig.VERSION_NAME,
                isChecking = uiState.update is UpdateState.Checking,
                latestVersion = uiState.latestVersion,
                onBack = { screen = Screen.MAP },
                activeExplainer = activeExplainer,
                onExplainerChange = { activeExplainer = it },
                onLanguageChange = { viewModel.setLanguage(it) },
                onThreatMapToggle = { type, visible -> viewModel.setThreatMapVisible(type, visible) },
                onThreatAlertToggle = { type, enabled -> viewModel.setThreatAlertsEnabled(type, enabled) },
                onThreatMapToggleAll = { types, visible -> viewModel.setGroupThreatMapVisible(types, visible) },
                onThreatAlertToggleAll = { types, enabled -> viewModel.setGroupThreatAlertsEnabled(types, enabled) },
                onOfficialAlertsChange = { viewModel.setOfficialAlertsEnabled(it) },
                onOfficialAlertCityScopeChange = { viewModel.setOfficialAlertCityScope(it) },
                onSirenOverrideChange = { viewModel.setSirenOverride(it) },
                onNightEnabledChange = { viewModel.setNightEnabled(it) },
                onNightStartChange = { viewModel.setNightStartMin(it) },
                onNightEndChange = { viewModel.setNightEndMin(it) },
                onNightUseCustomZonesChange = { viewModel.setNightUseCustomZones(it) },
                onNightSlowRedChange = { viewModel.setNightSlowRedKm(it) },
                onNightSlowYellowChange = { viewModel.setNightSlowYellowKm(it) },
                onNightFastRedChange = { viewModel.setNightFastRedMin(it) },
                onNightFastYellowChange = { viewModel.setNightFastYellowMin(it) },
                onNightSlowRedArmedChange = { viewModel.setNightSlowRedArmed(it) },
                onNightSlowYellowArmedChange = { viewModel.setNightSlowYellowArmed(it) },
                onNightFastRedArmedChange = { viewModel.setNightFastRedArmed(it) },
                onNightFastYellowArmedChange = { viewModel.setNightFastYellowArmed(it) },
                onNightZoneSirenOverrideChange = { viewModel.setNightZoneSirenOverride(it) },
                onNightOfficialSirenOverrideChange = { viewModel.setNightOfficialSirenOverride(it) },
                onFollowMeChange = { viewModel.setFollowMe(it) },
                onPinnedCityChange = { viewModel.setPinnedCity(it) },
                onPeriodicGpsChange = { viewModel.setPeriodicGps(it) },
                onCalmMessagesChange = { viewModel.setCalmMessagesEnabled(it) },
                onHapticsEnabledChange = { viewModel.setHapticsEnabled(it) },
                onDisclaimerCollapse = { viewModel.setDisclaimerCollapsed(it) },
                onDisclaimerShown = { viewModel.onDisclaimerShown() },
                onThreatCardSizeChange = { viewModel.setThreatCardSize(it) },
                onIconSetChange = { viewModel.setThreatIconSet(it) },
                onShowMapScaleChange = { viewModel.setShowMapScale(it) },
                onShowMediumCitiesChange = { viewModel.setShowMediumCities(it) },
                onShowSmallCitiesChange = { viewModel.setShowSmallCities(it) },
                onSheltersEnabledChange = { viewModel.setSheltersEnabled(it) },
                onOpenShelterList = {
                sheltersFromSettings = true
                screen = Screen.SHELTERS
            },
                onDeathAnimationChange = { viewModel.setDeathAnimationEnabled(it) },
                onFollowBulletChange = { viewModel.setFollowBullet(it) },
                onNeutralizedTallyChange = { viewModel.setNeutralizedTallyEnabled(it) },
                onNeutralizedTallyAllUkraineChange = { viewModel.setNeutralizedTallyAllUkraine(it) },
                onFastGroupCollapse = { viewModel.setFastGroupCollapsed(it) },
                onSlowGroupCollapse = { viewModel.setSlowGroupCollapsed(it) },
                onExit = onExit,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onRelaunchSetup = {
                    viewModel.relaunchSetup()
                    wizardOpenedDuringAlert = true
                    wizardFromSettings = true
                },
                onResetTips = {
                    viewModel.resetAllTips()
                    // Re-arm the in-memory hint counters so the gear re-spins immediately.
                    settingsHintRemaining = 10
                    showToast(context, Strings.get(uiState.language).tipsResetToast, cardVisible = false)
                },
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
        if (screen == Screen.SHELTERS) {
            BackHandler { screen = if (sheltersFromSettings) Screen.SETTINGS else Screen.MAP }
            ShelterScreen(
                lang = uiState.language,
                focus = uiState.focusLocation,
                index = uiState.shelterIndex,
                withKids = uiState.sheltersWithKids,
                onWithKidsChange = { viewModel.setSheltersWithKidsEnabled(it) },
                now = now,
                onBack = { screen = if (sheltersFromSettings) Screen.SETTINGS else Screen.MAP }
            )
        }
        if (screen == Screen.LOGS) {
            BackHandler { screen = Screen.MAP }
            LogsScreen(
                s = Strings.get(uiState.language),
                lang = uiState.language,
                iconSet = uiState.iconSet,
                onBack = { screen = Screen.MAP }
            )
        }
        SwipeableSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
        ToastHost(topInset = with(LocalDensity.current) { headerHeightPx.toDp() })
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

    // First-install: a full-screen wizard — language (+tips), icon pack, which threats matter,
    // then a feature preview. It force-closes without saving the moment an alert goes live
    // (alerts outrank onboarding); the wizard returns once the alert clears. Opening it via
    // "Replay first launch" is the user's explicit choice and works even while an alert is
    // already active (wizardOpenedDuringAlert overrides the gate until the alert clears).
    LaunchedEffect(uiState.alertActive) {
        if (!uiState.alertActive) wizardOpenedDuringAlert = false
    }
    if (!uiState.languageChosen &&
        (lastFrameAt > 0 || wizardSettleDeadline) &&
        (!uiState.alertActive || wizardOpenedDuringAlert)
    ) {
        FirstLaunchWizard(
            current = uiState.language,
            iconSet = uiState.iconSet,
            hiddenTypes = uiState.hiddenTypes,
            silencedTypes = uiState.silencedTypes,
            onChoose = { viewModel.setLanguage(it) },
            onIconSetChange = { viewModel.setThreatIconSet(it) },
            onThreatEnabledToggle = { type, enabled -> viewModel.setThreatEnabled(type, enabled) },
            onComplete = {
                viewModel.skipLanguageChoose()
                if (wizardFromSettings) {
                    wizardFromSettings = false
                    screen = Screen.MAP
                }
            },
            onLater = {
                viewModel.laterLanguageChoose()
                if (wizardFromSettings) {
                    wizardFromSettings = false
                    screen = Screen.MAP
                }
            }
        )
    }

    // First-run battery prompt (also once for pre-onboarding installs): shown after the language
    // picker, only while the OS still throttles the app. Already-exempt users are skipped
    // silently so MainActivity's deferred permission requests can proceed.
    val batteryExempt = remember { BatteryOptimization.isIgnoringBatteryOptimizations(context) }
    LaunchedEffect(uiState.languageChosen, uiState.batteryOnboardShown, batteryExempt) {
        if (uiState.languageChosen && !uiState.batteryOnboardShown && batteryExempt) {
            viewModel.setBatteryOnboardShown(true)
        }
    }
    if (uiState.languageChosen && !uiState.batteryOnboardShown && !batteryExempt) {
        BatteryOnboardingDialog(
            s = Strings.get(uiState.language),
            onAllow = {
                viewModel.setBatteryOnboardShown(true)
                BatteryOptimization.requestExemption(context)
            },
            onLater = { viewModel.setBatteryOnboardShown(true) }
        )
    }
}

@Composable
private fun FirstLaunchWizard(
    current: AppLanguage,
    iconSet: ThreatIconSet,
    hiddenTypes: Set<ThreatType>,
    silencedTypes: Set<ThreatType>,
    onChoose: (AppLanguage) -> Unit,
    onIconSetChange: (ThreatIconSet) -> Unit,
    onThreatEnabledToggle: (ThreatType, Boolean) -> Unit,
    onComplete: () -> Unit,
    onLater: () -> Unit
) {
    val s = Strings.get(current)
    val other = if (current == AppLanguage.UA) AppLanguage.EN else AppLanguage.UA
    val totalSteps = 4
    var step by remember { mutableStateOf(0) }
    var tipsRevealed by remember { mutableStateOf(false) }
    BackHandler(enabled = step > 0) { step-- }
    val stepTitle = when (step) {
        0 -> Strings.get(other).languageChooseTitle
        1 -> s.wizardCareTitle
        2 -> s.wizardZonesTitle
        else -> s.onboardingFeaturesTitle
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stepTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    0 -> {
                        SetupLanguageStep(current, onChoose)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            s.onboardingIntro,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!tipsRevealed) {
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { tipsRevealed = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(s.okButton, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Spacer(Modifier.height(28.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                            OnboardingTipRow(
                                iconRes = IconCatalog.photoRes(ThreatType.SHAHED) ?: R.drawable.ic_threat_shahed,
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
                            Spacer(Modifier.height(6.dp))
                            OnboardingGradualTip(s.onboardingTipGradual)
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    1 -> {
                        WizardThreatGrid(current, iconSet, hiddenTypes, silencedTypes, onThreatEnabledToggle)
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            s.iconSetTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        IconSetSelector(
                            lang = current,
                            selected = iconSet,
                            onChange = onIconSetChange,
                            slot = 28.dp
                        )
                    }
                    2 -> SetupZoneControlsStep(s)
                    else -> SetupFeaturesStep(s)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) { Text(s.backButton) }
                } else {
                    OutlinedButton(onClick = onLater) { Text(s.languageChooseLater) }
                }
                val progressFrac = (step + 1) / totalSteps.toFloat()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to UkraineYellow,
                                    progressFrac to UkraineYellow,
                                    progressFrac to UkraineBlue,
                                    1f to UkraineBlue
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { if (step < totalSteps - 1) step++ else onComplete() },
                        enabled = step != 0 || tipsRevealed,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (step < totalSteps - 1) s.nextButton else s.wizardStartButton,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupLanguageStep(current: AppLanguage, onChoose: (AppLanguage) -> Unit) {
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
}

@Composable
private fun WizardThreatGrid(
    lang: AppLanguage,
    iconSet: ThreatIconSet,
    hiddenTypes: Set<ThreatType>,
    silencedTypes: Set<ThreatType>,
    onThreatEnabledToggle: (ThreatType, Boolean) -> Unit
) {
    val s = Strings.get(lang)
    Column {
        Text(
            s.wizardCareSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        fastAndSlowGroups(lang).forEachIndexed { i, (groupIcon, groupLabel, types) ->
            if (i > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = groupIcon),
                    contentDescription = if (groupIcon == R.drawable.ic_lightning) s.fastGroupIconDesc else s.slowGroupIconDesc,
                    tint = if (groupIcon == R.drawable.ic_turtle) TurtleGreen else Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    groupLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            // TODO: Dynamic column count based on available width (onGloballyPositioned) for
            //  tablet/foldable support. Currently hardcoded to 4 columns.
            types.toList().chunked(4).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { type ->
                        val on = type !in hiddenTypes && type !in silencedTypes
                        val info = ThreatTypeCatalog.INFO.getValue(type)
                        val label = if (lang == AppLanguage.UA) {
                            info.shortLabelUa ?: info.labelUa
                        } else {
                            info.shortLabelEn ?: info.labelEn
                        }
                        val onColor = MaterialTheme.colorScheme.onSurface
                        val offColor = MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.5.dp,
                                    color = if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pressTick()
                                .clickable { onThreatEnabledToggle(type, !on) }
                                .padding(vertical = 14.dp, horizontal = 6.dp)
                        ) {
                            ThreatIcon(
                                type = type,
                                set = iconSet,
                                size = 40.dp,
                                tint = if (on) Color.Unspecified else offColor,
                                dimmed = !on
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) onColor else offColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SetupFeaturesStep(s: Strings.StringSet) {
    val features = remember(s) {
        guideFeatures(s).filter { it.id in setOf("live", "zones", "night", "follow", "shelter") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        features.forEach { f ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureDiagram(
                    kind = f.diagram,
                    modifier = Modifier
                        .size(width = 92.dp, height = 62.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        f.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        f.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * First-run battery exemption. Appears once, right after the language picker (only when the OS
 * still throttles this app), so MainActivity's deferred system permission dialogs come last.
 */
@Composable
private fun BatteryOnboardingDialog(
    s: Strings.StringSet,
    onAllow: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        confirmButton = { TextButton(onClick = onAllow) { Text(s.batteryAllowButton) } },
        dismissButton = { TextButton(onClick = onLater) { Text(s.batteryLater) } },
        title = { Text(s.batteryTitle) },
        text = { Text(s.batteryBody) }
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
                .size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The "gradual tips" promise — visually distinct from the icon rows so it reads as a note. */
@Composable
private fun OnboardingGradualTip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Wizard page showing the zone-controls live on the map: the three hover buttons and the sliders. */
@Composable
private fun SetupZoneControlsStep(s: Strings.StringSet) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            s.wizardZonesSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        FeatureDiagram(
            kind = GuideDiagram.EDIT_ZONES,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        // The three floating buttons: red zone, yellow zone, edit gear.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WizardZoneButton(Color(0xFFD32F2F), s.zoneButtonRed)
            WizardZoneButton(Color(0xFFF9A825), s.zoneButtonYellow)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = s.editZonesLabel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            s.wizardEditZonesHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A colored circular zone button as it appears on the map, for the wizard illustration. */
@Composable
private fun WizardZoneButton(color: Color, contentDescription: String) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(2.dp, color)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 18.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    uiState: UiState,
    now: Long,
    settingsOpen: Boolean,
    mapVisible: Boolean,
    onOpenSettings: () -> Unit,
    onOpenThreatSettings: () -> Unit,
    onThreatTapped: (Threat) -> Unit,
    onThreatStripTap: (Threat) -> Unit,
    onDismissPopup: () -> Unit,
    onMapTapped: () -> Unit,
    onSlowRedChange: (Int) -> Unit,
    onSlowYellowChange: (Int) -> Unit,
    onFastRedChange: (Int) -> Unit,
    onFastYellowChange: (Int) -> Unit,
    onSlowRedArmedChange: (Boolean) -> Unit,
    onSlowYellowArmedChange: (Boolean) -> Unit,
    onFastRedArmedChange: (Boolean) -> Unit,
    onFastYellowArmedChange: (Boolean) -> Unit,
    onThreatCardSizeChange: (ThreatCardSize) -> Unit,
    onForceOfflineChange: (Boolean) -> Unit,
    onNeutralize: (String) -> Unit,
    onFlybyFinished: (String) -> Unit,
    showConnectionInfo: Boolean,
    onShowConnectionInfoChange: (Boolean) -> Unit,
    showZonesSheet: Boolean,
    onShowZonesSheetChange: (Boolean) -> Unit,
    onOpenShelters: () -> Unit,
    onOpenLogs: () -> Unit,
    onShelterModeChange: (Boolean) -> Unit,
    shelterTipStage: Int,
    onShelterTipAdvance: () -> Unit,
    settingsHintRemaining: Int = 0,
    onHeaderHeightChange: (Int) -> Unit = {}
) {
    val s = Strings.get(uiState.language)
    val context = LocalContext.current

    val lastPreciseFixMs by LocationTracker.lastPreciseFixAtMs.collectAsState()
    // The settings gear rotates gently while the "open Settings" hint is active, drawing the
    // eye to it. Infinite transition = always animating, so the value is continuously observed;
    // the rotation is only applied while the hint counter is still positive.
    val gearHintActive = settingsHintRemaining > 0
    val gearSpinTransition = rememberInfiniteTransition(label = "gearSpin")
    val gearSpin by gearSpinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearSpin"
    )
    var fitUkraineTick by remember { mutableStateOf(0) }
    var scaleMpp by remember { mutableStateOf(0.0) }
    var zoomZone by remember { mutableStateOf<ThreatZone?>(null) }
    var zoomTick by remember { mutableStateOf(0) }
    var fitZonesTick by remember { mutableStateOf(0) }
    var shelterZoomTick by remember { mutableStateOf(0) }
    var shelterSelectTick by remember { mutableStateOf(0) }
    var showNearbyShelters by remember { mutableStateOf(false) }
    var selectedShelter by remember { mutableStateOf<NearestShelter?>(null) }
    var deathActive by remember { mutableStateOf(false) }
    var replayProgress by remember { mutableStateOf<ReplayProgress?>(null) }

    // Surface shelter-mode to the ViewModel so the resolved-threat flourish/card is
    // suppressed while the shelter overlay is up.
    LaunchedEffect(showNearbyShelters) { onShelterModeChange(showNearbyShelters) }

    val fineLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LocationTracker.forceRefresh()
        }
    }

    val onToggleShelters: () -> Unit = {
        if (shelterTipStage < 6) {
            val tipText = when (shelterTipStage) {
                0, 1 -> s.shelterTapTip
                4, 5 -> s.shelterLongPressTip
                else -> null
            }
            if (tipText != null) {
                showToast(context, tipText, cardVisible = false)
            }
            onShelterTipAdvance()
        }
        val willShow = !showNearbyShelters
        showNearbyShelters = willShow
        if (!willShow) {
            selectedShelter = null
        } else {
            shelterZoomTick++
            // A fix younger than 5 minutes is fine to reuse — repeated toggling in a red
            // alert shouldn't hammer the GPS; the shelter list screen can force a fresh fix.
            val fixAgeMs = lastPreciseFixMs?.let { now - it }
            if (fixAgeMs == null || fixAgeMs >= 5 * 60_000L) {
                showToast(
                    context,
                    s.updatingPreciseGpsToast,
                    cardVisible = uiState.selectedThreat != null || selectedShelter != null || showZonesSheet
                )
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasFine) {
                    fineLocLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                LocationTracker.forceRefresh()
            }
        }
    }

    // Last strip-tapped threat id per type, so repeated taps cycle through each of that type.
    val stripCycle = remember { mutableStateMapOf<ThreatType, String>() }
    // The zones sheet edits whatever the map is currently showing.
    val editingNight = uiState.nightActive && uiState.nightUseCustomZones

    // Opening the panel also asks the map to centre + zoom to the full yellow zone.
    val openZonesPanel: () -> Unit = {
        onShowZonesSheetChange(true)
        fitZonesTick++
    }

    val openSettings: () -> Unit = {
        onShowZonesSheetChange(false)
        onOpenSettings()
    }

    val openThreatSettings: () -> Unit = {
        onShowZonesSheetChange(false)
        onOpenThreatSettings()
    }

    // Back closes the popup first, then exits — fixes "back stuck on home page".
    BackHandler(enabled = selectedShelter != null) { selectedShelter = null }
    BackHandler(enabled = uiState.selectedThreat != null) { onDismissPopup() }
    BackHandler(enabled = showZonesSheet) { onShowZonesSheetChange(false) }

    Scaffold(
        topBar = {
            val activeZone = uiState.activeZone
            val officialOnly = uiState.focusOblastAlertActive && activeZone == null
            val borderColor = when (activeZone) {
                ThreatZone.INNER -> AlertRed
                ThreatZone.OUTER -> Color(0xFFF9A825)
                null -> if (officialOnly) AlertRed else Color.Transparent
            }
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.5.dp, borderColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .onGloballyPositioned { coords ->
                        onHeaderHeightChange(coords.size.height)
                    },
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
                    val titleInteraction = remember { MutableInteractionSource() }
                    Text(
                        text = alertText,
                        modifier = Modifier
                            .pressTick()
                            .clickable(
                                interactionSource = titleInteraction,
                                indication = ripple(),
                                onClick = { fitUkraineTick++ }
                            )
                            .semantics { semanticsContentDescription = s.fitMapLabel },
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
                    forceOffline = uiState.forceOffline,
                    onForceOfflineChange = onForceOfflineChange,
                    onOpenLogs = onOpenLogs,
                    showInfo = showConnectionInfo,
                    onShowInfoChange = onShowConnectionInfoChange,
                    s = s,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = openSettings, modifier = Modifier.size(32.dp).pressTick()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_ua),
                        contentDescription = s.settingsButton,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = if (gearHintActive) gearSpin else 0f }
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
                        iconSet = uiState.iconSet,
                        onScaleChange = { scaleMpp = it },
                        onThreatTapped = {
                            showNearbyShelters = false
                            selectedShelter = null
                            onThreatTapped(it)
                        },
                        onMapTapped = {
                            selectedShelter = null
                            onMapTapped()
                        },
                        fitUkraineTick = fitUkraineTick,
                        zoomZone = zoomZone,
                        zoomTick = zoomTick,
                        fitZonesTick = fitZonesTick,
                        zonesSheetOpen = showZonesSheet,
                        revealRequest = uiState.revealRequest,
                        paused = settingsOpen,
                        mapVisible = mapVisible,
                        shelterZoomTick = shelterZoomTick,
                        shelterSelectTick = shelterSelectTick,
                        onNeutralize = onNeutralize,
                        showNearbyShelters = showNearbyShelters,
                        shelterIndex = uiState.shelterIndex,
                        selectedShelter = selectedShelter,
                        onShelterTapped = {
                            onDismissPopup()
                            selectedShelter = it
                            shelterSelectTick++
                        },
                        onExitShelterMode = {
                            showNearbyShelters = false
                            selectedShelter = null
                        },
                        onDeathActiveChange = { deathActive = it },
                        onReplayProgressChange = { replayProgress = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    uiState.flyby?.let { show ->
                        AviationFlybyOverlay(
                            show = show,
                            iconSet = uiState.iconSet,
                            onFinished = onFlybyFinished
                        )
                    }
                    if (uiState.showMapScale) {
                        ScaleIndicator(
                            metersPerPixel = scaleMpp,
                            lang = uiState.language,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                        )
                    }
                    val shelterFocus = uiState.focusLocation
                    val shelterIndex = uiState.shelterIndex
                    if (uiState.sheltersEnabled && shelterIndex != null && shelterFocus != null &&
                        shelterIndex.withinRegion(shelterFocus.lat, shelterFocus.lon)
                    ) {
                        ShelterButton(
                            alertActive = uiState.focusOblastAlertActive,
                            active = showNearbyShelters,
                            label = s.shelterButtonLabel,
                            onClick = onToggleShelters,
                            onLongClick = onOpenShelters,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                    ZoneButtons(
                        redArmed = uiState.activeSlowRedArmed || uiState.activeFastRedArmed,
                        yellowArmed = uiState.activeSlowYellowArmed || uiState.activeFastYellowArmed,
                        lang = uiState.language,
                        onZoneTap = { zone ->
                            showNearbyShelters = false
                            selectedShelter = null
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
                    val replay = replayProgress
                    if (replay != null) {
                        // A tally replay owns the whole footer while it runs — the per-group
                        // "Resolving threat X of N" replaces even the threat strip, with a
                        // slim overall-progress bar along the footer's bottom edge; the strip
                        // returns the moment the show ends.
                        val barFraction by animateFloatAsState(
                            targetValue = replay.fraction,
                            animationSpec = tween(250),
                            label = "flourishProgress"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                if (replay.groupSize <= 1) s.resolvingThreat
                                else String.format(s.resolvingThreatsFormat, replay.bulletInGroup, replay.groupSize),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF9A825),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            // Slim progress bar: faint track, amber fill easing across the
                            // bottom edge as the show advances.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFF9A825))
                                )
                            }
                        }
                    } else if (total == 0) {
                        val footerText = when {
                            deathActive -> s.neutralizingLabel
                            else -> noThreatsMessage(
                                uiState.language,
                                now / 86_400_000L,
                                uiState.calmMessagesEnabled
                            )
                        }
                        Text(
                            footerText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (deathActive) Color(0xFFF9A825) else Color(0xFF4CAF50),
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
                                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThreatType.values().forEach { type ->
                                val count = (innerCounts[type] ?: 0) + (outerCounts[type] ?: 0)
                                val visible = type !in uiState.hiddenTypes
                                val alerting = type !in uiState.silencedTypes
                                if (count > 0 && visible && alerting) {
                                    val focus = uiState.focusLocation
                                    val list = (uiState.threatsInner + uiState.threatsOuter)
                                        .filter { it.type == type }
                                        .sortedBy {
                                            if (focus != null) distanceMeters(focus.lat, focus.lon, it.lat, it.lon)
                                            else 0.0
                                        }
                                    ThreatStatusCell(
                                        type = type,
                                        count = count,
                                        enabled = true,
                                        iconSet = uiState.iconSet,
                                        onClick = {
                                            list.firstOrNull()?.let { nearest ->
                                                val current = stripCycle[type]
                                                val next = if (current == null || list.size == 1) {
                                                    nearest
                                                } else {
                                                    val idx = list.indexOfFirst { it.id == current }
                                                    if (idx in 0 until list.size - 1) list[idx + 1] else list[0]
                                                }
                                                stripCycle[type] = next.id
                                                onThreatStripTap(next)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Threat popup: the full interactive card while a threat is selected, crossfading
            // into the compact neutralized card the instant it resolves (so the popup never pops
            // out), which then fades out across the map explosion, clearing the selection.
            // The small card hugs the top-left corner and stays narrow; the large card is
            // top-centred and full-width. The measured height feeds the map so a selected or
            // struck threat is centred in the viewport left visible below the card.
            val smallCard = uiState.threatCardSize == ThreatCardSize.SMALL
            val animsOff = animationsOff()
            AnimatedContent(
                targetState = when {
                    uiState.selectedThreat != null -> 1
                    uiState.neutralizedThreat != null -> 2
                    else -> 0
                },
                                // Card appears/disappears in one frame — tap must feel instant. Selection
                // motion is the map's bullet + the card's icon pop; the slower fade below is
                // reserved for the neutralized state's death-window exit.
                transitionSpec = {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                },
                label = "threatCardSwap",
                modifier = Modifier
                    .align(if (smallCard) Alignment.TopStart else Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) { state ->
                when (state) {
                    1 -> uiState.selectedThreat?.let { threat ->
                        Column {
                            ThreatPopupCard(
                                threat = threat,
                                lang = uiState.language,
                                iconSet = uiState.iconSet,
                                proximity = uiState.selectedThreatInfo,
                                pinnedCity = if (uiState.followMe) null else uiState.pinnedCity,
                                threatLevel = uiState.threatLevel,
                                cardSize = uiState.threatCardSize,
                                alertsOff = threat.type in uiState.silencedTypes,
                                onDismiss = onDismissPopup,
                                fakeNeutralize = uiState.fakeNeutralize,
                                modifier = if (smallCard) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                ThreatCardSizeControl(
                                    current = uiState.threatCardSize,
                                    contentDescription = s.cardSizeLabel,
                                    onClick = {
                                        onThreatCardSizeChange(nextThreatCardSize(uiState.threatCardSize))
                                    },
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                    2 -> uiState.neutralizedThreat?.let { threat ->
                        val fade = remember { Animatable(1f) }
                        var neutralizing by remember { mutableStateOf(true) }
                        LaunchedEffect(Unit) {
                            delay(DEATH_EXPLOSION_START_MS)
                            neutralizing = false
                        }
                        // A short readable hold, then one smooth, clearly visible alpha ramp that
                        // runs across the whole death window — never a hard hide at impact.
                        LaunchedEffect(Unit) {
                            val holdMs = 700L
                            delay(holdMs)
                            fade.animateTo(
                                0f,
                                tween((DEATH_EXPLOSION_START_MS + DEATH_EXPLOSION_LEN_MS - holdMs).toInt())
                            )
                            onDismissPopup()
                        }
                        Box(modifier = Modifier.graphicsLayer { alpha = fade.value }) {
                            ThreatPopupCard(
                                threat = threat,
                                lang = uiState.language,
                                iconSet = uiState.iconSet,
                                proximity = null,
                                pinnedCity = null,
                                threatLevel = 0.0,
                                cardSize = uiState.threatCardSize,
                                interactive = false,
                                neutralized = true,
                                neutralizing = neutralizing,
                                onDismiss = onDismissPopup,
                                fakeNeutralize = uiState.fakeNeutralize,
                                modifier = if (smallCard) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Shelter info card: tapping a shelter marker on the map opens it here (the same
            // data as the list rows); tapping the map or the back button closes it.
            selectedShelter?.let { sh ->
                ShelterPopupCard(
                    lang = uiState.language,
                    shelter = sh,
                    withKids = uiState.sheltersWithKids,
                    onDismiss = { selectedShelter = null },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                )
            }

            // Alert-zone editor: a non-modal bottom panel over the live map so the
            // red/yellow circles update while you drag, and the map above stays pannable.
            // Every control (sliders + Fast/Slow group toggles) is visible at once.
            // Slides up/down like the connection sheet; snaps when system animations are off.
            AnimatedVisibility(
                visible = showZonesSheet,
                enter = if (animsOff) EnterTransition.None
                else slideInVertically(tween(200)) { it } + fadeIn(tween(100)),
                exit = if (animsOff) ExitTransition.None
                else slideOutVertically(tween(250)) { it } + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (editingNight) NightSectionBg else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (editingNight) NightSectionBorder else Color(0xFF3A3A3A)
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val dismissThresholdPx = with(density) { 80.dp.toPx() }
                        var dragAccum by remember { mutableFloatStateOf(0f) }
                        val closeSheet = { onShowZonesSheetChange(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressTick()
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
                            SheetDragHandle()
                        }
                        ZonesPanel(
                            slowRedKm = if (editingNight) uiState.nightSlowRedKm else uiState.slowRedKm,
                            slowYellowKm = if (editingNight) uiState.nightSlowYellowKm else uiState.slowYellowKm,
                            fastRedMin = if (editingNight) uiState.nightFastRedMin else uiState.fastRedMin,
                            fastYellowMin = if (editingNight) uiState.nightFastYellowMin else uiState.fastYellowMin,
                            slowRedArmed = if (editingNight) uiState.nightSlowRedArmed else uiState.slowRedArmed,
                            slowYellowArmed = if (editingNight) uiState.nightSlowYellowArmed else uiState.slowYellowArmed,
                            fastRedArmed = if (editingNight) uiState.nightFastRedArmed else uiState.fastRedArmed,
                            fastYellowArmed = if (editingNight) uiState.nightFastYellowArmed else uiState.fastYellowArmed,
                            lang = uiState.language,
                            nightActive = editingNight,
                            useNightZones = uiState.nightUseCustomZones,
                            nightEnabled = uiState.nightEnabled,
                            daySlowRedKm = uiState.slowRedKm,
                            daySlowYellowKm = uiState.slowYellowKm,
                            dayFastRedMin = uiState.fastRedMin,
                            dayFastYellowMin = uiState.fastYellowMin,
                            onSlowRedChange = onSlowRedChange,
                            onSlowYellowChange = onSlowYellowChange,
                            onFastRedChange = onFastRedChange,
                            onFastYellowChange = onFastYellowChange,
                            onSlowRedArmedChange = onSlowRedArmedChange,
                            onSlowYellowArmedChange = onSlowYellowArmedChange,
                            onFastRedArmedChange = onFastRedArmedChange,
                            onFastYellowArmedChange = onFastYellowArmedChange,
                            onOpenThreatSettings = openThreatSettings,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UkraineEmblem(active: Boolean, modifier: Modifier = Modifier, contentDesc: String? = null) {
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
            contentDescription = contentDesc,
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

/** "Shelter" pill: filled red while an official alert is active; when the shelter markers are
 *  shown on the map it carries a primary border/tint so the on/off state is obvious; otherwise
 *  ghost-outlined. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelterButton(
    alertActive: Boolean,
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (alertActive) AlertRed else MaterialTheme.colorScheme.surface
    val fg = when {
        alertActive -> Color.White
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = when {
        alertActive -> null
        active -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        shape = shape,
        color = bg.copy(alpha = if (isPressed) 0.8f else 1f),
        border = border,
        modifier = modifier
            .pressTick()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = fg.copy(alpha = 0.3f)),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val walkRes = remember { if (kotlin.random.Random.nextBoolean()) R.drawable.ic_walk_man else R.drawable.ic_walk_woman }
            WalkFigureIcon(resId = walkRes, height = 20.dp, tint = fg)
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = fg)
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
            val gearInteraction = remember { MutableInteractionSource() }
            val gearPressed by gearInteraction.collectIsPressedAsState()
            val gearRotation = animateFloatAsState(
                targetValue = if (gearPressed) -15f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "gearRotation"
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .semantics { semanticsContentDescription = s.editZonesLabel }
                    .pressTick()
                    .clickable(
                        interactionSource = gearInteraction,
                        indication = null,
                        onClick = onEditZones
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = gearRotation.value }
                )
            }
        }
    }
}

@Composable
private fun AllAlertsOffWarning(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier
            .pressTick()
            .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            onClick = onClick
        )
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
                // Red crossed bell floating above the pill signals this zone's alerts are off.
                AlertsOffBell(size = 16.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha = animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "zonePillBg"
    )
    val scale = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "zonePillScale"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(if (armed) zoneColor.copy(alpha = bgAlpha.value) else Color(0xFF2A2A2A))
            .border(2.dp, if (armed) zoneColor else Color(0xFF666666), CircleShape)
            .semantics { semanticsContentDescription = contentDescription }
            .pressTick()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onZoneTap(zone) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_zoom_in),
            contentDescription = null,
            tint = if (armed) Color.White else Color(0xFF777777),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ThreatStatusCell(
    type: ThreatType,
    count: Int,
    enabled: Boolean,
    iconSet: ThreatIconSet,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPressed) 0.12f else 0.06f))
            .pressTick()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThreatIcon(
            type = type,
            set = iconSet,
            size = 28.dp,
            tint = if (enabled) Color.Unspecified else Color(0xFF9E9E9E)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF9E9E9E)
        )
    }
}

/** Popup card-size stepper: SMALL → LARGE → SMALL… */
private fun nextThreatCardSize(current: ThreatCardSize): ThreatCardSize {
    val values = ThreatCardSize.values()
    return values[(current.ordinal + 1) % values.size]
}

/**
 * Full-size MiG-31K takeoff flourish: the plane crosses the whole viewport along the
 * airbase→focus bearing with a contrail, then the threat card opens ([onFinished]). Pure
 * overlay — consumes no input, moves no camera.
 */
@Composable
private fun AviationFlybyOverlay(
    show: AviationFlybyShow,
    iconSet: ThreatIconSet,
    onFinished: (String) -> Unit
) {
    val density = LocalDensity.current
    val iconSize = 160.dp
    val iconPx = with(density) { iconSize.toPx() }
    val trailPx = with(density) { 200.dp.toPx() }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(show.tick) { Animatable(0f) }
    LaunchedEffect(show.tick) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(show.durationMs.toInt(), easing = LinearEasing))
        onFinished(show.threatId)
    }
    if (canvasPx == IntSize.Zero) return
    // Map markers rotate by courseDeg minus the asset's baked-in facing; mirror that here so
    // every icon set flies nose-first along the pass.
    val rotation = show.courseDeg.toFloat() - IconCatalog.baseDeg(ThreatType.AVIATION, iconSet)
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { canvasPx = it }) {
        val (entry, exit) = AviationFlyby.endpoints(
            show.courseDeg, canvasPx.width.toFloat(), canvasPx.height.toFloat(), iconPx
        )
        val x = lerp(entry.first, exit.first, progress.value)
        val y = lerp(entry.second, exit.second, progress.value)
        val dir = AviationFlyby.direction(show.courseDeg)
        Canvas(modifier = Modifier.matchParentSize()) {
            val tailX = (x - trailPx * dir.first).toFloat()
            val tailY = (y - trailPx * dir.second).toFloat()
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.5f)),
                    start = Offset(tailX, tailY),
                    end = Offset(x, y)
                ),
                start = Offset(tailX, tailY),
                end = Offset(x, y),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset((x - iconPx / 2).roundToInt(), (y - iconPx / 2).roundToInt()) }
                .size(iconSize)
                .rotate(rotation)
        ) {
            ThreatIcon(type = ThreatType.AVIATION, set = iconSet, size = iconSize)
        }
    }
}

/** Two stacked lines (thin/thick) under the popup; tap cycles the card size. */
@Composable
private fun ThreatCardSizeControl(
    current: ThreatCardSize,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "cardSizeScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isPressed) 0.95f else 0.85f))
            .semantics { semanticsContentDescription = contentDescription }
            .pressTick()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        listOf(2.dp, 6.dp).forEachIndexed { i, thickness ->
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(thickness)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i == current.ordinal) MaterialTheme.colorScheme.primary
                        else Color(0xFF9E9E9E)
                    )
            )
            if (i < 1) Spacer(Modifier.height(4.dp))
        }
    }
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

/** Snackbar host whose snackbars can be dismissed by swiping sideways. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        key(data) {
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
                    true
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = true,
                content = { Snackbar(snackbarData = data) }
            )
        }
    }
}
