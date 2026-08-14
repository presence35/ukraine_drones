package ua.ukrainedrones

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

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

    companion object {
        const val REQUEST_NOTIFICATIONS = 1
        // Set the moment the process spawns; used to tell cold starts from warm starts.
        val PROCESS_START_MILLIS: Long = SystemClock.elapsedRealtime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tiles now live in the OS cache dir; delete the pre-migration caches (internal and
        // external app dirs) that Android counted as "user data". Tiles re-download harmlessly.
        File(filesDir, "osmdroid")?.takeIf { it.exists() }?.deleteRecursively()
        getExternalFilesDir(null)?.let { base ->
            File(base, "osmdroid").takeIf { it.exists() }?.deleteRecursively()
        }
        AlertService.start(this)
        requestNotificationPermission()
        setContent {
            MaterialTheme(colorScheme = AppDarkColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SplashGate {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Mirror the website's focus handler — refresh positions and reset the stale window.
        NeptunClient.onForeground()
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
