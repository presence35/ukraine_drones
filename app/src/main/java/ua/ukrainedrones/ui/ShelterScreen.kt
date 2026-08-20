package ua.ukrainedrones

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nearest-shelter list: ranks the Odesa dataset by distance to the focus point and hands the
 *  chosen one off to the system map app. Pull down to re-fetch the list; the header shows how
 *  fresh the location fix is and forces a precise GPS one-shot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterScreen(
    lang: AppLanguage,
    focus: LatLng?,
    index: ShelterIndex?,
    withKids: Boolean,
    onWithKidsChange: (Boolean) -> Unit,
    shelterRefreshing: Boolean,
    now: Long,
    onRefresh: () -> Unit,
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
        PullToRefreshBox(
            isRefreshing = shelterRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GpsHeaderRow(
                        lastFixMs = lastFixMs,
                        lastPreciseFixMs = lastPreciseFixMs,
                        isRefreshing = gpsRefreshing || trackerRefreshing,
                        now = now,
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
}

/** Minimal pull-to-refresh: a nested-scroll connection turns a pull at the top of the child into an
 *  [onRefresh] call and shows a spinner that follows the drag. No dependency beyond foundation. */
@Composable
private fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val pullOffset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    val isRefreshingUpdated by rememberUpdatedState(isRefreshing)
    val onRefreshUpdated by rememberUpdatedState(onRefresh)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    scope.launch { pullOffset.snapTo(pullOffset.value + available.y) }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset.value >= threshold && !isRefreshingUpdated) {
                    onRefreshUpdated()
                    pullOffset.snapTo(0f)
                } else {
                    pullOffset.animateTo(0f)
                }
                return Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        content()
        val pull = pullOffset.value
        val alpha = if (isRefreshing) 1f else (pull / threshold).coerceIn(0f, 1f)
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = if (isRefreshing) 0f else pull }
                .alpha(alpha)
                .size(28.dp)
        )
    }
}

@Composable
private fun GpsHeaderRow(
    lastFixMs: Long?,
    lastPreciseFixMs: Long?,
    isRefreshing: Boolean,
    now: Long,
    s: Strings.StringSet,
    withKids: Boolean,
    onWithKidsChange: (Boolean) -> Unit,
    onForceRefresh: () -> Unit
) {
    val label = if (lastPreciseFixMs != null) {
        val age = formatAlertAge(now, lastPreciseFixMs, s)
        String.format(s.lastGpsFixFormat, if (age.isBlank()) s.gpsFixJustNow else age)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatDistance(row.distanceMeters, s),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    WalkRow(
                        icon = R.drawable.ic_adult,
                        text = String.format(s.shelterWalkMinutes, row.walkMinutesAdult),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (withKids) {
                        Spacer(Modifier.height(6.dp))
                        WalkRow(
                            icon = R.drawable.ic_adult_kid,
                            text = String.format(s.shelterWalkMinutes, row.walkMinutesKid),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            modifier = Modifier.size(20.dp)
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
