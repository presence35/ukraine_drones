package ua.ukrainedrones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the monitoring service after a reboot and after an in-app update replaces the
 * package — without a BOOT_COMPLETED hook the app would sit silent until the user opens it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlertService.start(context)
        }
    }
}