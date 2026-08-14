package ua.ukrainedrones

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

private val UkraineBlue = Color(0xFF005BBB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lang: AppLanguage,
    disabledTypes: Set<ThreatType>,
    fastAlertsSooner: Boolean,
    officialAlertsEnabled: Boolean,
    sirenOverride: Boolean,
    disclaimerCollapsed: Boolean,
    followMe: Boolean,
    pinnedCity: City?,
    redCities: Set<String>,
    threatCardSize: ThreatCardSize,
    versionName: String,
    isChecking: Boolean,
    latestVersion: String?,
    onBack: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThreatToggle: (ThreatType, Boolean) -> Unit,
    onFastAlertsSoonerChange: (Boolean) -> Unit,
    onOfficialAlertsChange: (Boolean) -> Unit,
    onSirenOverrideChange: (Boolean) -> Unit,
    onFollowMeChange: (Boolean) -> Unit,
    onPinnedCityChange: (City?) -> Unit,
    onDisclaimerCollapse: (Boolean) -> Unit,
    onThreatCardSizeChange: (ThreatCardSize) -> Unit,
    onExit: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGuide: () -> Unit
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
    // The "Official signals" card needs two collapse taps before it actually stays collapsed.
    var collapseAttempts by remember { mutableStateOf(0) }
    val onDisclaimerClick: () -> Unit = {
        if (disclaimerCollapsed) {
            collapseAttempts = 0
            onDisclaimerCollapse(false)
        } else {
            collapseAttempts++
            if (collapseAttempts >= 2) {
                collapseAttempts = 0
                onDisclaimerCollapse(true)
            }
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
                                imageVector = if (disclaimerCollapsed) Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(visible = !disclaimerCollapsed) {
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
                // Inverted like the flags: the header names the language you'd switch to,
                // not the one currently active.
                SectionHeader(
                    Strings.get(if (lang == AppLanguage.UA) AppLanguage.EN else AppLanguage.UA).languageLabel,
                    painterResource(id = R.drawable.ic_language)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            }

            item { SectionHeader(s.mapCenterLabel, rememberVectorPainter(Icons.Default.LocationOn)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        AlertToggleRow(
                            title = s.followMeTitle,
                            description = s.followMeDesc,
                            checked = followMe,
                            onCheckedChange = onFollowMeChange
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PinCityRow(
                            lang = lang,
                            followMe = followMe,
                            pinnedCity = pinnedCity,
                            redCities = redCities,
                            onChange = onPinnedCityChange
                        )
                    }
                }
            }

            item { SectionHeader(s.cardSizeLabel, painterResource(R.drawable.ic_card_size)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        ThreatCardSizeSelector(
                            lang = lang,
                            selected = threatCardSize,
                            onChange = onThreatCardSizeChange
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    }
                }
            }

            item { SectionHeader(s.threatsLabel, rememberVectorPainter(Icons.Default.Warning)) }
            items(ThreatType.values().toList()) { type ->
                val info = ThreatTypeCatalog.INFO.getValue(type)
                val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
                val description = if (lang == AppLanguage.UA) info.descriptionUa else info.descriptionEn
                val details = if (lang == AppLanguage.UA) info.detailsUa else info.detailsEn
                val joke = if (lang == AppLanguage.UA) info.jokeUa else info.jokeEn
                val enabled = type !in disabledTypes
                val typicalSpeed = typicalSpeedKmh(type)?.roundToInt()
                val expanded = expandedType == type
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = if (enabled) {
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThreatToggle(type, !enabled) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .alpha(if (enabled) 1f else 0.45f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = iconResFor(type)),
                                contentDescription = label,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { expandedType = if (expanded) null else type },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = s.moreInfoLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        AnimatedVisibility(visible = expanded) {
                            val context = LocalContext.current
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
                                    .alpha(if (enabled) 1f else 0.45f)
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
                                Spacer(Modifier.height(12.dp))
                                ThreatImages.drawableRes(type)?.let { resId ->
                                    Image(
                                        painter = painterResource(id = resId),
                                        contentDescription = label,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                } ?: ThreatImages.url(type)?.let { imgUrl ->
                                    AsyncImage(
                                        model = imageRequest(context, imgUrl),
                                        contentDescription = label,
                                        placeholder = painterResource(id = iconResFor(type)),
                                        error = painterResource(id = iconResFor(type)),
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                joke.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "— $it",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader(s.alertsLabel, rememberVectorPainter(Icons.Default.Notifications)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        AlertToggleRow(
                            title = s.fastAlertsSoonerTitle,
                            description = s.fastAlertsSoonerDesc,
                            checked = fastAlertsSooner,
                            onCheckedChange = onFastAlertsSoonerChange,
                            icon = painterResource(R.drawable.ic_bolt),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AlertToggleRow(
                            title = s.officialAlertsTitle,
                            description = s.officialAlertsDesc,
                            checked = officialAlertsEnabled,
                            onCheckedChange = onOfficialAlertsChange,
                            icon = painterResource(R.drawable.ic_trident),
                            note = s.officialAlertsRedTridentNote
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AlertToggleRow(
                            title = s.sirenOverrideTitle,
                            description = s.sirenOverrideDesc,
                            checked = sirenOverride,
                            onCheckedChange = onSirenOverrideChange,
                            icon = painterResource(R.drawable.ic_volume_up),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (batteryOptimized) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.batteryGranted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
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

    }

private fun imageRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .setHeader("User-Agent", "UkraineDrones (Android; https://odesaplay.com.ua)")
        .build()

@Composable
private fun AlertToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Painter? = null,
    iconTint: Color? = null,
    note: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Image(
                painter = it,
                contentDescription = null,
                colorFilter = iconTint?.let { c -> ColorFilter.tint(c) },
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinCityRow(
    lang: AppLanguage,
    followMe: Boolean,
    pinnedCity: City?,
    redCities: Set<String>,
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
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (city.nameUa in redCities) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFD32F2F))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label(city))
                        }
                    },
                    onClick = {
                        onChange(city)
                        expanded = false
                    }
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
    redKm = 3,
    yellowKm = 8,
    speedSource = SpeedSource.RECORDED,
    speedKmh = 180.0
)

/** Three selectable tiles, each a live scaled preview of that card size. */
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
            val density = LocalDensity.current
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val nominalW = with(density) { 340.dp.toPx() }
                val nominalWpx = with(density) { 340.dp.roundToPx() }
                val scale = constraints.maxWidth.toFloat() / nominalW
                val cardPlaceable = subcompose("preview-card") {
                    Box(
                        modifier = Modifier
                            .width(340.dp)
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
                            fastAlertsSooner = true,
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

@Composable
private fun SectionHeader(text: String, icon: Painter) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

private fun iconResFor(type: ThreatType): Int = when (type) {
    ThreatType.SHAHED -> R.drawable.shahed
    ThreatType.FPV_LOITERING -> R.drawable.ic_threat_fpv
    ThreatType.CRUISE_MISSILE -> R.drawable.ic_threat_cruise
    ThreatType.BALLISTIC -> R.drawable.ic_threat_ballistic
    ThreatType.KAB -> R.drawable.ic_threat_kab
    ThreatType.AVIATION -> R.drawable.ic_threat_aviation
    ThreatType.RECON -> R.drawable.ic_threat_recon
    ThreatType.UNKNOWN -> R.drawable.ic_threat_unknown
}
