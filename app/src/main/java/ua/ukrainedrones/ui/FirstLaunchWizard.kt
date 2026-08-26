package ua.ukrainedrones

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

private val UkraineBlue = Color(0xFF005BBB)
private val UkraineYellow = Color(0xFFFFD500)

@Composable
internal fun FirstLaunchWizard(
    current: AppLanguage,
    iconSet: ThreatIconSet,
    hiddenTypes: Set<ThreatType>,
    silencedTypes: Set<ThreatType>,
    followMe: Boolean,
    pinnedCity: City?,
    onChoose: (AppLanguage) -> Unit,
    onIconSetChange: (ThreatIconSet) -> Unit,
    onThreatEnabledToggle: (ThreatType, Boolean) -> Unit,
    onFollowMeChange: (Boolean) -> Unit,
    onPinnedCityChange: (City?) -> Unit,
    onComplete: () -> Unit,
    onLater: () -> Unit
) {
    val s = Strings.get(current)
    val other = if (current == AppLanguage.UA) AppLanguage.EN else AppLanguage.UA
    val totalSteps = 5
    var step by remember { mutableStateOf(0) }
    var tipsRevealed by remember { mutableStateOf(false) }
    BackHandler(enabled = step > 0) { step-- }
    val stepTitle = when (step) {
        0 -> Strings.get(other).languageChooseTitle
        1 -> s.wizardCareTitle
        2 -> s.wizardLocationTitle
        3 -> s.wizardZonesTitle
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
                    2 -> SetupLocationStep(current, followMe, pinnedCity, onFollowMeChange, onPinnedCityChange)
                    3 -> SetupZoneControlsStep(s)
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
                val nextEnabled = step != 0 || tipsRevealed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (nextEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { if (step < totalSteps - 1) step++ else onComplete() },
                        enabled = nextEnabled,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (step < totalSteps - 1) s.nextButton else s.wizardStartButton,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progressFrac)
                            .height(3.dp)
                            .background(Brush.horizontalGradient(listOf(UkraineYellow, UkraineBlue)))
                    )
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
        val gearId = "gear"
        Text(
            buildAnnotatedString {
                append(s.wizardCareSubtitle)
                append(' ')
                appendInlineContent(gearId, "[gear]")
                append(' ')
                append(s.wizardCareSubtitleGearSuffix)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            inlineContent = mapOf(
                gearId to InlineTextContent(
                    Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_ua),
                        contentDescription = s.settingsButton,
                        tint = Color.Unspecified
                    )
                }
            )
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
                        val cellInteraction = remember { MutableInteractionSource() }
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
                                .pressTick(cellInteraction)
                                .clickable(
                                    interactionSource = cellInteraction,
                                    indication = ripple(bounded = true),
                                    onClick = { onThreatEnabledToggle(type, !on) }
                                )
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

/** Wizard page: where the user's position comes from — GPS follow-me or a pinned city. */
@Composable
private fun SetupLocationStep(
    lang: AppLanguage,
    followMe: Boolean,
    pinnedCity: City?,
    onFollowMeChange: (Boolean) -> Unit,
    onPinnedCityChange: (City?) -> Unit
) {
    val s = Strings.get(lang)
    val context = LocalContext.current
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            s.wizardLocationSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WizardLocationCard(
            selected = followMe,
            onClick = {
                onFollowMeChange(true)
                if (!fineGranted) {
                    permLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = s.followMeTitle,
            desc = s.followMeDesc
        )
        WizardLocationCard(
            selected = !followMe,
            onClick = { onFollowMeChange(false) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = s.pinCityTitle,
            desc = s.pinCityDesc
        )
        AnimatedVisibility(visible = !followMe) {
            CityChipGrid(lang, selected = pinnedCity, onChange = onPinnedCityChange)
        }
    }
}

@Composable
private fun WizardLocationCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Wizard page showing the zone controls as they look in-app: a live-styled slider row plus the
 * three floating map buttons (red zone, yellow zone, edit gear) with short visible captions.
 */
@Composable
private fun SetupZoneControlsStep(s: Strings.StringSet) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            s.wizardZonesSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            SectionCaption(
                text = s.slowSectionLabel,
                leadingIcon = R.drawable.ic_turtle,
                leadingDesc = s.slowGroupIconDesc,
                leadingTint = TurtleGreen
            )
            var demoKm by remember { mutableStateOf(12f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = ZoneRedColor,
                    modifier = Modifier.size(26.dp)
                )
                Switch(
                    checked = true,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ZoneRedColor,
                        checkedTrackColor = ZoneRedColor.copy(alpha = 0.45f),
                        checkedBorderColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = demoKm,
                    onValueChange = { demoKm = it },
                    valueRange = 1f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = ZoneRedColor,
                        activeTrackColor = ZoneRedColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${demoKm.roundToInt()} ${s.kmUnit}",
                    color = ZoneRedColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WizardZoneButton(Color(0xFFD32F2F), s.zoneButtonRed)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.zoneRedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WizardZoneButton(Color(0xFFF9A825), s.zoneButtonYellow)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.zoneYellowLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Spacer(Modifier.height(4.dp))
                Text(
                    s.editZonesLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun SetupFeaturesStep(s: Strings.StringSet) {
    val features = remember(s) {
        guideFeatures(s).filter { it.id in setOf("live", "zones", "night", "shelter") }
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
internal fun BatteryOnboardingDialog(
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
