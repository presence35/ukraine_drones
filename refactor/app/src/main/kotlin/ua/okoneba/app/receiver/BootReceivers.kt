package ua.okoneba.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.okoneba.core.datastore.DeviceProtectedDataStoreRepository
import ua.okoneba.feature.alerts.service.AlertService

/**
 * Direct Boot aware receiver.
 * Resumes essential air-threat monitoring after device reboot before user PIN unlock.
 *
 * Requirements:
 * - android:directBootAware="true"
 * - Uses ONLY Device-Protected Storage (DE).
 * - Checks `isMonitoringEnabled` before starting AlertService.
 * - Does NOT start monitoring if user explicitly stopped it.
 */
class DirectBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val deContext = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deRepo = DeviceProtectedDataStoreRepository.create(deContext)
                val settings = deRepo.getSettings()

                if (settings.isMonitoringEnabled) {
                    val serviceIntent = AlertService.startMonitoringIntent(context)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Post-unlock receiver.
 * Receives ACTION_USER_UNLOCKED once the user enters their PIN/password.
 * Safe to access Credential-Encrypted (CE) storage and load full application state.
 */
class UserUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_UNLOCKED) return

        // Post-unlock initialization: full CE dependencies can now be initialized
    }
}
