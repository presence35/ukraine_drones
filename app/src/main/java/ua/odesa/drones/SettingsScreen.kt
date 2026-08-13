package ua.odesa.drones

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

private val UkraineBlue = Color(0xFF005BBB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lang: AppLanguage,
    disabledTypes: Set<ThreatType>,
    fastAlertsSooner: Boolean,
    officialAlertsEnabled: Boolean,
    disclaimerCollapsed: Boolean,
    versionName: String,
    isChecking: Boolean,
    onBack: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThreatToggle: (ThreatType, Boolean) -> Unit,
    onFastAlertsSoonerChange: (Boolean) -> Unit,
    onOfficialAlertsChange: (Boolean) -> Unit,
    onDisclaimerCollapse: (Boolean) -> Unit,
    onExit: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val s = Strings.get(lang)
    var expandedType by remember { mutableStateOf<ThreatType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s.backButton)
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
                // Inverted like the flags: the header names the language you'd switch to,
                // not the one currently active.
                SectionHeader(
                    Strings.get(if (lang == AppLanguage.UA) AppLanguage.EN else AppLanguage.UA).languageLabel
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LanguageFlag(
                        resId = R.drawable.ic_flag_ua,
                        active = lang == AppLanguage.UA,
                        onClick = { onLanguageChange(AppLanguage.UA) },
                        modifier = Modifier.weight(1f)
                    )
                    LanguageFlag(
                        resId = R.drawable.ic_flag_usa,
                        active = lang == AppLanguage.EN,
                        onClick = { onLanguageChange(AppLanguage.EN) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader(s.threatsLabel) }
            items(ThreatType.values().toList()) { type ->
                val info = ThreatTypeCatalog.INFO.getValue(type)
                val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
                val description = if (lang == AppLanguage.UA) info.descriptionUa else info.descriptionEn
                val details = if (lang == AppLanguage.UA) info.detailsUa else info.detailsEn
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
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = s.moreInfoLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
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
                                ThreatImages.url(type)?.let { imgUrl ->
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
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    s.wikiSourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(s.alertsLabel) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        AlertToggleRow(
                            title = s.fastAlertsSoonerTitle,
                            description = s.fastAlertsSoonerDesc,
                            checked = fastAlertsSooner,
                            onCheckedChange = onFastAlertsSoonerChange
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AlertToggleRow(
                            title = s.officialAlertsTitle,
                            description = s.officialAlertsDesc,
                            checked = officialAlertsEnabled,
                            onCheckedChange = onOfficialAlertsChange
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDisclaimerCollapse(!disclaimerCollapsed) }
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${s.madeBy} · v$versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
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
                    } else {
                        Button(
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
            }
        }
    }

    }

private fun imageRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .setHeader("User-Agent", "OdesaDrones (Android; https://odesaplay.com.ua)")
        .build()

@Composable
private fun AlertToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
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
private fun LanguageFlag(resId: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                // Inverted: the flag of the language you'd switch to is the colored one.
                alpha = if (active) 0.3f else 1f,
                modifier = Modifier
                    .height(36.dp)
                    .fillMaxWidth(0.5f)
            )
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
