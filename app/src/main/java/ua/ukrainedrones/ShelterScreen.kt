package ua.ukrainedrones

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Nearest-shelter list: ranks the Odesa dataset by distance to the focus point and hands the
 *  chosen one off to the system map app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterScreen(
    lang: AppLanguage,
    focus: LatLng?,
    index: ShelterIndex?,
    onBack: () -> Unit
) {
    val s = Strings.get(lang)
    val context = LocalContext.current
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
        if (near.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.shelterEmpty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(near) { row ->
                    val openInMaps: () -> Unit = {
                        val point = row.shelter
                        val uri = "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                    }
                    ShelterCard(
                        row = row,
                        s = s,
                        onOpenInMaps = openInMaps
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelterCard(
    row: NearestShelter,
    s: Strings.StringSet,
    onOpenInMaps: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                row.shelter.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatDistance(row.distanceMeters, s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val adult = String.format(s.shelterWalkMinutes, row.walkMinutesAdult)
                val kid = String.format(s.shelterWalkMinutes, row.walkMinutesKid)
                Text(
                    "$adult (${s.shelterWalkAdultLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$kid (${s.shelterWalkKidLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenInMaps)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
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

private fun formatDistance(meters: Double, s: Strings.StringSet): String =
    if (meters < 1000) String.format(s.shelterDistanceM, meters.toInt())
    else String.format(s.shelterDistanceKm, meters / 1000.0)
