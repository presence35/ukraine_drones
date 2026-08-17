package ua.ukrainedrones

import android.app.Activity
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription as semanticsContentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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
    var scrollToThreatsTick by remember { mutableStateOf(0) }
    val settingsListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        settingsHintRemaining = prefs.settingsHintRemaining().first()
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
        viewModel.autoCheckForUpdates(allowPopup = false)
    }

    // The map stays composed under the Settings overlay so its camera and tiles are never
    // destroyed — returning from Settings used to reset the world into a low-zoom grid.
    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            uiState = uiState,
            settingsOpen = screen == Screen.SETTINGS,
            onOpenSettings = openSettings,
            onOpenThreatSettings = {
                openSettings()
                scrollToThreatsTick++
            },
            onThreatTapped = { viewModel.selectThreat(it) },
            onThreatStripTap = { viewModel.panToThreat(it) },
            onDismissPopup = { viewModel.selectThreat(null) },
            onMapTapped = { viewModel.selectThreat(null) },
            onSlowRedChange = { viewModel.setSlowRedKm(it) },
            onSlowYellowChange = { viewModel.setSlowYellowKm(it) },
            onFastRedChange = { viewModel.setFastRedMin(it) },
            onFastYellowChange = { viewModel.setFastYellowMin(it) },
            onSlowRedArmedChange = { viewModel.setSlowRedArmed(it) },
            onSlowYellowArmedChange = { viewModel.setSlowYellowArmed(it) },
            onFastRedArmedChange = { viewModel.setFastRedArmed(it) },
            onFastYellowArmedChange = { viewModel.setFastYellowArmed(it) },
            onThreatCardSizeChange = { viewModel.setThreatCardSize(it) },
            onForceOfflineChange = viewModel::setForceOffline,
            onTempNeutralize = { id -> viewModel.tempNeutralize(id) }
        )
        if (screen == Screen.SETTINGS) {
            // Composed after MapScreen, so its handler is checked first on Back.
            BackHandler { screen = Screen.MAP }
            SettingsScreen(
                lang = uiState.language,
                listState = settingsListState,
                onThreatsScrollHandled = { scrollToThreatsTick = 0 },
                scrollToThreatsTick = scrollToThreatsTick,
                hiddenTypes = uiState.hiddenTypes,
                silencedTypes = uiState.silencedTypes,
                officialAlertsEnabled = uiState.officialAlertsEnabled,
                sirenOverride = uiState.sirenOverride,
                fastVibrationLevel = uiState.fastVibrationLevel,
                slowVibrationLevel = uiState.slowVibrationLevel,
                nightEnabled = uiState.nightEnabled,
                nightStartMin = uiState.nightStartMin,
                nightEndMin = uiState.nightEndMin,
                nightUseCustomZones = uiState.nightUseCustomZones,
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
                deathAnimationEnabled = uiState.deathAnimationEnabled,
                fastGroupCollapsed = uiState.fastGroupCollapsed,
                slowGroupCollapsed = uiState.slowGroupCollapsed,
                versionName = BuildConfig.VERSION_NAME,
                isChecking = uiState.update is UpdateState.Checking,
                latestVersion = uiState.latestVersion,
                alertActive = uiState.alertActive,
                onBack = { screen = Screen.MAP },
                onLanguageChange = { viewModel.setLanguage(it) },
                onThreatMapToggle = { type, visible -> viewModel.setThreatMapVisible(type, visible) },
                onThreatAlertToggle = { type, enabled -> viewModel.setThreatAlertsEnabled(type, enabled) },
                onThreatMapToggleAll = { types, visible -> viewModel.setGroupThreatMapVisible(types, visible) },
                onThreatAlertToggleAll = { types, enabled -> viewModel.setGroupThreatAlertsEnabled(types, enabled) },
                onOfficialAlertsChange = { viewModel.setOfficialAlertsEnabled(it) },
                onSirenOverrideChange = { viewModel.setSirenOverride(it) },
                onFastVibrationChange = { viewModel.setFastVibrationLevel(it) },
                onSlowVibrationChange = { viewModel.setSlowVibrationLevel(it) },
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
                onDisclaimerCollapse = { viewModel.setDisclaimerCollapsed(it) },
                onDisclaimerShown = { viewModel.onDisclaimerShown() },
                onThreatCardSizeChange = { viewModel.setThreatCardSize(it) },
                onIconSetChange = { viewModel.setThreatIconSet(it) },
                onShowMapScaleChange = { viewModel.setShowMapScale(it) },
                onDeathAnimationChange = { viewModel.setDeathAnimationEnabled(it) },
                onFastGroupCollapse = { viewModel.setFastGroupCollapsed(it) },
                onSlowGroupCollapse = { viewModel.setSlowGroupCollapsed(it) },
                onExit = onExit,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onRelaunchSetup = { viewModel.relaunchSetup() },
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

    // First-install: a full-screen wizard — language (+tips), icon pack, which threats matter,
    // then a feature preview. It force-closes without saving the moment an alert goes live
    // (alerts outrank onboarding); the wizard returns once the alert clears.
    if (!uiState.languageChosen && !uiState.alertActive) {
        FirstLaunchWizard(
            current = uiState.language,
            iconSet = uiState.iconSet,
            hiddenTypes = uiState.hiddenTypes,
            silencedTypes = uiState.silencedTypes,
            onChoose = { viewModel.setLanguage(it) },
            onIconSetChange = { viewModel.setThreatIconSet(it) },
            onThreatEnabledToggle = { type, enabled -> viewModel.setThreatEnabled(type, enabled) },
            onComplete = { viewModel.skipLanguageChoose() }
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
    onComplete: () -> Unit
) {
    val s = Strings.get(current)
    var step by remember { mutableStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    val stepTitle = when (step) {
        0 -> s.languageChooseTitle
        1 -> s.iconSetTitle
        2 -> s.wizardCareTitle
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
                Text(
                    "${step + 1}/4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
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
                    1 -> SetupIconPackStep(current, iconSet, onIconSetChange)
                    2 -> WizardThreatGrid(current, iconSet, hiddenTypes, silencedTypes, onThreatEnabledToggle)
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
                    OutlinedButton(onClick = onComplete) { Text(s.languageChooseLater) }
                }
                Button(
                    onClick = { if (step < 3) step++ else onComplete() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (step < 3) s.nextButton else s.wizardStartButton, fontWeight = FontWeight.SemiBold)
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
private fun SetupIconPackStep(lang: AppLanguage, iconSet: ThreatIconSet, onIconSetChange: (ThreatIconSet) -> Unit) {
    IconSetSelector(
        lang = lang,
        selected = iconSet,
        onChange = onIconSetChange,
        slot = 28.dp
    )
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        ThreatType.values().toList().chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pair.forEach { type ->
                    val on = type !in hiddenTypes && type !in silencedTypes
                    val info = ThreatTypeCatalog.INFO.getValue(type)
                    val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
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
                            .clickable { onThreatEnabledToggle(type, !on) }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        ThreatIcon(
                            type = type,
                            set = iconSet,
                            size = 40.dp,
                            tint = if (on) onColor else offColor
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) onColor else offColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (on) s.wizardCareOn else s.wizardCareOff,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.primary else offColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SetupFeaturesStep(s: Strings.StringSet) {
    val features = remember(s) {
        guideFeatures(s).filter { it.id in setOf("live", "zones", "notif", "lang") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        f.summary,
                        style = MaterialTheme.typography.bodySmall,
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
    onTempNeutralize: (String) -> Unit
) {
    val s = Strings.get(uiState.language)
    var fitUkraineTick by remember { mutableStateOf(0) }
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

    val openThreatSettings: () -> Unit = {
        showZonesSheet = false
        onOpenThreatSettings()
    }

    // Back closes the popup first, then exits — fixes "back stuck on home page".
    BackHandler(enabled = uiState.selectedThreat != null) { onDismissPopup() }
    BackHandler(enabled = showZonesSheet) { showZonesSheet = false }

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
                        modifier = Modifier.clickable(onClick = { fitUkraineTick++ }),
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
                    forceOffline = uiState.forceOffline,
                    onForceOfflineChange = onForceOfflineChange,
                    s = s,
                    lang = uiState.language,
                    iconSet = uiState.iconSet,
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
                        iconSet = uiState.iconSet,
                        onScaleChange = { scaleMpp = it },
                        onThreatTapped = onThreatTapped,
                        onMapTapped = onMapTapped,
                        fitUkraineTick = fitUkraineTick,
                        zoomZone = zoomZone,
                        zoomTick = zoomTick,
                        fitZonesTick = fitZonesTick,
                        revealRequest = uiState.revealRequest,
                        paused = settingsOpen,
                        onTempNeutralize = onTempNeutralize,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (uiState.showMapScale) {
                        ScaleIndicator(
                            metersPerPixel = scaleMpp,
                            lang = uiState.language,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                        )
                    }
                    if (!uiState.followMe) {
                        uiState.pinnedCity?.let { city ->
                            val cityName = if (uiState.language == AppLanguage.UA) city.nameUa else city.nameEn
                            val alertsOff = !uiState.activeSlowRedArmed && !uiState.activeSlowYellowArmed &&
                                !uiState.activeFastRedArmed && !uiState.activeFastYellowArmed
                            PinnedPill(
                                text = String.format(s.mapPillPinned, cityName),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = if (alertsOff) 100.dp else 40.dp)
                            )
                        }
                    }
                    ZoneButtons(
                        redArmed = uiState.activeSlowRedArmed || uiState.activeFastRedArmed,
                        yellowArmed = uiState.activeSlowYellowArmed || uiState.activeFastYellowArmed,
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
                                    val target = (uiState.threatsInner + uiState.threatsOuter)
                                        .filter { it.type == type }
                                        .minByOrNull {
                                            if (focus != null) distanceMeters(focus.lat, focus.lon, it.lat, it.lon)
                                            else 0.0
                                        }
                                    ThreatStatusCell(
                                        type = type,
                                        count = count,
                                        enabled = true,
                                        iconSet = uiState.iconSet,
                                        onClick = { target?.let(onThreatStripTap) }
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
            Crossfade(
                targetState = when {
                    uiState.selectedThreat != null -> 1
                    uiState.neutralizedThreat != null -> 2
                    else -> 0
                },
                animationSpec = tween(300),
                label = "threatCardSwap",
                modifier = Modifier
                    .align(Alignment.TopCenter)
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
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
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
                        val cardContext = LocalContext.current
                        val vibrator = remember { cardContext.getSystemService(Vibrator::class.java) }
                        LaunchedEffect(Unit) {
                            delay(DEATH_EXPLOSION_START_MS)
                            // A short pulse as the projectile detonates on the map.
                            vibrator?.vibrate(
                                VibrationEffect.createWaveform(vibrationPattern(1), -1)
                            )
                            neutralizing = false
                            fade.animateTo(0f, tween(DEATH_EXPLOSION_LEN_MS.toInt()))
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Alert-zone editor: a non-modal bottom panel over the live map so the
            // red/yellow circles update while you drag, and the map above stays pannable.
            // Every control (sliders + Fast/Slow group toggles) is visible at once.
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
                                                ZonesPanel(
                            slowRedKm = uiState.slowRedKm,
                            slowYellowKm = uiState.slowYellowKm,
                            fastRedMin = uiState.fastRedMin,
                            fastYellowMin = uiState.fastYellowMin,
                                                        slowRedArmed = uiState.slowRedArmed,
                            slowYellowArmed = uiState.slowYellowArmed,
                            fastRedArmed = uiState.fastRedArmed,
                            fastYellowArmed = uiState.fastYellowArmed,
                            lang = uiState.language,
                            nightNote = if (uiState.nightActive) {
                                String.format(s.nightDayZonesNote, uiState.nightWindowText)
                            } else null,
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
private fun ThreatStatusCell(
    type: ThreatType,
    count: Int,
    enabled: Boolean,
    iconSet: ThreatIconSet,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        ThreatIcon(
            type = type,
            set = iconSet,
            size = 28.dp,
            tint = if (enabled) Color.Unspecified else Color(0xFF9E9E9E)
        )
        Spacer(Modifier.width(7.dp))
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

/** Two stacked lines (thin/thick) under the popup; tap cycles the card size. */
@Composable
private fun ThreatCardSizeControl(
    current: ThreatCardSize,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .semantics { semanticsContentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        listOf(2.dp, 6.dp).forEachIndexed { i, thickness ->
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(thickness)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i == current.ordinal) MaterialTheme.colorScheme.primary
                        else Color(0xFF9E9E9E)
                    )
            )
            if (i < 1) Spacer(Modifier.height(3.dp))
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
