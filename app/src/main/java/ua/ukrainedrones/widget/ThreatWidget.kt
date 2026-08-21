package ua.ukrainedrones

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home-screen widget for the air-threat state. Passive: it only renders the snapshot persisted
 * by [WidgetUpdater] and never derives zones/tiers itself (mirror rule — see ARCHITECTURE.md).
 * One responsive widget; density (compact / standard / detailed) is picked from the on-screen
 * size. Tapping anywhere opens the map.
 */
class ThreatWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT, STANDARD, DETAILED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, lang) = withContext(Dispatchers.IO) {
            WidgetUpdater.readSnapshot(context) to WidgetUpdater.readLang(context)
        }
        provideContent {
            WidgetContent(snapshot = snapshot, lang = lang)
        }
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetSnapshot, lang: AppLanguage) {
        val strings = Strings.get(lang)
        val size = LocalSize.current
        val detailed = size.height >= DETAILED.height - 20.dp
        val standard = size.height >= STANDARD.height - 30.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BG)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                HeaderRow(snapshot, strings, standard)
                if (standard) ZoneLine(snapshot, strings)
                if (detailed) StatusLine(snapshot, strings)
                UpdatedLine(snapshot, strings, detailed)
            }
        }
    }

    @Composable
    private fun HeaderRow(snapshot: WidgetSnapshot, strings: Strings.StringSet, standard: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.widget.threatsLabel,
                style = TextStyle(
                    color = ColorProvider(TEXT),
                    fontSize = if (standard) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            ZoneDot(snapshot.activeZone, snapshot.officialAlert)
        }
    }

    @Composable
    private fun ZoneDot(zone: ThreatZone?, officialAlert: Boolean) {
        val color = when {
            officialAlert || zone == ThreatZone.INNER -> RED
            zone == ThreatZone.OUTER -> YELLOW
            else -> MUTED
        }
        Box(
            modifier = GlanceModifier.size(14.dp).background(color)
        ) {}
    }

    @Composable
    private fun ZoneLine(snapshot: WidgetSnapshot, strings: Strings.StringSet) {
        val zoneLabel = when (snapshot.activeZone) {
            ThreatZone.INNER -> strings.redZoneLabel
            ThreatZone.OUTER -> strings.yellowZoneLabel
            null -> strings.widget.noThreats
        }
        val text = if (snapshot.threatCount > 0) {
            snapshot.nearestKm?.let { "$zoneLabel · ~${it.toInt()} km" } ?: zoneLabel
        } else zoneLabel
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(MUTED), fontSize = 13.sp),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }

    @Composable
    private fun StatusLine(snapshot: WidgetSnapshot, strings: Strings.StringSet) {
        val source = when {
            snapshot.officialAlert -> strings.officialAlertBanner
            snapshot.sourceBackup -> strings.status.connBackup
            snapshot.sourceOnline -> strings.status.connOnline
            else -> strings.status.connOffline
        }
        val count = "${snapshot.threatCount} ${strings.widget.threatsLabel.lowercase()}"
        Text(
            text = "$count · $source",
            style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }

    @Composable
    private fun UpdatedLine(snapshot: WidgetSnapshot, strings: Strings.StringSet, detailed: Boolean) {
        if (snapshot.updatedAtMs <= 0L) return
        Text(
            text = String.format(strings.widget.updatedFormat, formatWidgetTime(snapshot.updatedAtMs)),
            style = TextStyle(
                color = ColorProvider(MUTED),
                fontSize = if (detailed) 12.sp else 11.sp
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }

    companion object {
        val COMPACT = DpSize(110.dp, 55.dp)
        val STANDARD = DpSize(250.dp, 110.dp)
        val DETAILED = DpSize(250.dp, 180.dp)

        private val BG = Color(0xFF121212)
        private val TEXT = Color(0xFFE6E6E6)
        private val MUTED = Color(0xFF9E9E9E)
        private val RED = Color(0xFFE53935)
        private val YELLOW = Color(0xFFFDD835)
    }
}

private fun formatWidgetTime(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}

/** The manifest-declared receiver for [ThreatWidget]. */
class ThreatWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThreatWidget()
}
