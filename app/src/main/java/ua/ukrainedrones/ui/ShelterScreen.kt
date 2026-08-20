package ua.ukrainedrones

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** Nearest-shelter list: ranks the Odesa dataset by distance to the focus point and hands the
 *  chosen one off to the system map app. The header shows how fresh the location fix is and
 *  forces a precise GPS one-shot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterScreen(
    lang: AppLanguage,
    focus: LatLng?,
    index: ShelterIndex?,
    withKids: Boolean,
    onWithKidsChange: (Boolean) -> Unit,
    now: Long,
    onBack: () -> Unit
) {
    val s = Strings.get(lang)
    val context = LocalContext.current
    val lastFixMs by LocationTracker.lastFixAtMs.collectAsState()
    val lastPreciseFixMs by LocationTracker.lastPreciseFixAtMs.collectAsState()
    val trackerRefreshing by LocationTracker.isRefreshing.collectAsState()
    var gpsRefreshing by remember { mutableStateOf(false) }

    // Reset GPS spinner when a new fix arrives or tracker finishes
    LaunchedEffect(lastFixMs, trackerRefreshing) {
        if (!trackerRefreshing) {
            gpsRefreshing = false
        }
    }

    // Auto-reset GPS spinner after 10s timeout if no fix received
    LaunchedEffect(gpsRefreshing) {
        if (gpsRefreshing) {
            delay(10_000)
            gpsRefreshing = false
        }
    }

    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            gpsRefreshing = true
            LocationTracker.forceRefresh { gpsRefreshing = false }
        }
    }
    val forceGps: () -> Unit = {
        if (fineGranted) {
            gpsRefreshing = true
            LocationTracker.forceRefresh { gpsRefreshing = false }
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val near = if (focus != null && index != null) index.nearest(focus.lat, focus.lon, 30) else emptyList()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.shelterScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backButton)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                GpsHeaderRow(
                    lastFixMs = lastFixMs,
                    lastPreciseFixMs = lastPreciseFixMs,
                    isRefreshing = gpsRefreshing || trackerRefreshing,
                    now = now,
                    lang = lang,
                    s = s,
                    withKids = withKids,
                    onWithKidsChange = onWithKidsChange,
                    onForceRefresh = forceGps
                )
            }
            if (near.isEmpty()) {
                item {
                    Text(
                        s.shelterEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    )
                }
            } else {
                items(near) { row ->
                    val point = row.shelter
                    val uri = "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}"
                    ShelterCard(
                        lang = lang,
                        row = row,
                        s = s,
                        withKids = withKids,
                        onOpenInMaps = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsHeaderRow(
    lastFixMs: Long?,
    lastPreciseFixMs: Long?,
    isRefreshing: Boolean,
    now: Long,
    lang: AppLanguage,
    s: Strings.StringSet,
    withKids: Boolean,
    onWithKidsChange: (Boolean) -> Unit,
    onForceRefresh: () -> Unit
) {
    val label = if (lastPreciseFixMs != null) {
        val ageMin = ((now - lastPreciseFixMs) / 60_000).coerceAtLeast(0)
        if (ageMin <= 0) s.gpsFixFresh else preciseGpsAgePhrase(ageMin, lang)
    } else if (lastFixMs != null) {
        s.networkLocationOnly
    } else {
        s.shelterGpsUnknown
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = if (lastPreciseFixMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isRefreshing) {
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
                    IconButton(onClick = onForceRefresh, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = s.calibrateGpsNow,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_adult_kid),
                    contentDescription = null,
                    tint = if (withKids) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        s.shelterWithKidsTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        s.shelterWithKidsDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = withKids,
                    onCheckedChange = onWithKidsChange
                )
            }
        }
    }
}

@Composable
private fun ShelterCard(
    lang: AppLanguage,
    row: NearestShelter,
    s: Strings.StringSet,
    withKids: Boolean,
    onOpenInMaps: () -> Unit
) {
    val name = if (lang == AppLanguage.EN) shelterNameEn(row.shelter.name) else row.shelter.name
    val type = row.shelter.type
    val typeColor = when (type) {
        ShelterType.MOBILE -> androidx.compose.ui.graphics.Color(0xFFFFA000)
        ShelterType.BUNKER -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        ShelterType.BASIC -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    }
    val typeLabel = when (type) {
        ShelterType.MOBILE -> s.shelterTypeMobile
        ShelterType.BUNKER -> s.shelterTypeBunker
        ShelterType.BASIC -> s.shelterTypeBasic
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(typeColor)
                    )
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }
                Text(
                    formatDistance(row.distanceMeters, s),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WalkRow(
                        icon = R.drawable.ic_adult,
                        text = String.format(s.shelterWalkMinutes, row.walkMinutesAdult),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (withKids) {
                        WalkRow(
                            icon = R.drawable.ic_adult_kid,
                            text = String.format(s.shelterWalkMinutes, row.walkMinutesKid),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenInMaps)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.shelterOpenInMaps,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkRow(icon: Int, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDistance(meters: Double, s: Strings.StringSet): String =
    if (meters < 1000) String.format(s.shelterDistanceM, meters.toInt())
    else String.format(s.shelterDistanceKm, meters / 1000.0)
