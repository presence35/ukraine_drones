package ua.ukrainedrones

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Dark-only palette — this app never switches to a light theme, regardless
// of the device's system setting.
private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    background = Color(0xFF121212),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF232323),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    error = Color(0xFFE57373)
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private companion object {
        const val REQUEST_LOCATION = 1
        const val REQUEST_NOTIFICATIONS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanLegacyOsmdroidCache()
        ConnectionLog.attach(applicationContext)
        AlertHistory.attach(applicationContext)
        AlertService.start(this)
        setContent {
            // Cap the system font scale so extreme accessibility sizes can't break the layout;
            // the popup/banner still wrap and scroll up to this ceiling.
            val baseDensity = LocalDensity.current
            val fontScale = min(baseDensity.fontScale, 1.5f)
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale)
            ) {
                MaterialTheme(colorScheme = AppDarkColors) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainScreen()
                    }
                }
            }
        }
        handleReveal(intent)
        deferPermissionRequests()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReveal(intent)
    }

    /**
     * A notification tap can carry the threat that triggered it. Select it and ask the map
     * to pan onto it so both the focus point (GPS/city) and the threat are on screen.
     */
    private fun handleReveal(intent: Intent?) {
        val id = intent?.getStringExtra(AlertService.EXTRA_REVEAL_ID) ?: return
        val lat = intent.getDoubleExtra(AlertService.EXTRA_REVEAL_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(AlertService.EXTRA_REVEAL_LON, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return
        viewModel.revealThreat(id, lat, lon)
    }

    /**
     * Tiles now live in the OS cache dir; delete the pre-migration caches (internal and
     * external app dirs) that Android counted as "user data" — once, not on every launch.
     * Tiles re-download harmlessly.
     */
    private fun cleanLegacyOsmdroidCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = ZonePrefs(applicationContext)
            if (prefs.legacyCacheCleaned().first()) return@launch
            File(filesDir, "osmdroid").takeIf { it.exists() }?.deleteRecursively()
            getExternalFilesDir(null)?.let { base ->
                File(base, "osmdroid").takeIf { it.exists() }?.deleteRecursively()
            }
            prefs.setLegacyCacheCleaned(true)
        }
    }

    override fun onStart() {
        super.onStart()
        // Mirror the website's focus handler — refresh positions and reset the stale window.
        NeptunClient.onForeground()
    }

    /**
     * Two system permission dialogs must never be up at once — Android drops a request that
     * arrives while another is showing. Ask for location first (the map depends on it), then
     * chain the notification request once that dialog is resolved.
     */
    private fun requestLocationAndNotifications() {
        val locationGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (locationGranted) {
            LocationTracker.start(this)
            requestNotificationPermission()
        } else {
            // FINE so precise one-shots (alert / future shelter handoff) are available; the
            // dialog lets the user pick "approximate" — which still grants COARSE for the map.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
        }
    }

    /**
     * Permission dialogs must never beat the first-run onboarding — ask only after the language
     * picker and (for new users) the battery prompt have resolved. Returning users who already
     * finished onboarding get the request immediately.
     */
    private fun deferPermissionRequests() {
        lifecycleScope.launch {
            val prefs = ZonePrefs(applicationContext)
            val langChosen = prefs.languageChosen().first()
            val ready = if (langChosen && prefs.batteryOnboardShown().first()) {
                true
            } else {
                combine(prefs.languageChosen(), prefs.batteryOnboardShown()) { l, b -> l && b }
                    .first { it }
            }
            if (ready) requestLocationAndNotifications()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                LocationTracker.start(this)
            }
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }
}
