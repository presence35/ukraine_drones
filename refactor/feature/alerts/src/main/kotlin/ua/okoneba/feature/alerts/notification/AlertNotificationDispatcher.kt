package ua.okoneba.feature.alerts.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ua.okoneba.core.domain.model.AlertEvent
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.SystemHealthState

class AlertNotificationDispatcher(
    private val context: Context
) {
    companion object {
        const val CHANNEL_CRITICAL_ALERTS = "okoneba_critical_alerts"
        const val CHANNEL_MONITORING_STATUS = "okoneba_monitoring_status"

        const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val BASE_ALERT_NOTIFICATION_ID = 2000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel 1: Critical Alerts (High priority, sound, vibration, DND bypass request)
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALERTS,
                "Critical Air Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when air threats enter configured danger zones"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setBypassDnd(true)
                }
            }

            // Channel 2: Monitoring Service Status (Low priority background status)
            val statusChannel = NotificationChannel(
                CHANNEL_MONITORING_STATUS,
                "Air Threat Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing status of the background air-threat monitoring engine"
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    fun buildForegroundNotification(
        healthState: SystemHealthState,
        activeSourceId: String?,
        activeThreatCount: Int
    ): Notification {
        val title = "OkoNeba Threat Monitor Active"
        val content = when (healthState) {
            SystemHealthState.HEALTHY -> "Feed: ${activeSourceId ?: "Connected"} | Active threats: $activeThreatCount"
            SystemHealthState.DEGRADED -> "Telemetry Degraded (${activeSourceId ?: "Unknown"}) | Active threats: $activeThreatCount"
            SystemHealthState.DEGRADED_NO_FEEDS -> "No feeds connected. Waiting for telemetry..."
        }

        return NotificationCompat.Builder(context, CHANNEL_MONITORING_STATUS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun dispatchAlertNotification(event: AlertEvent) {
        val tier = event.tier
        val threat = event.threat
        val distanceKm = String.format("%.1f", event.distanceKm)

        val title = when (tier) {
            AlertTier.RED -> "RED ALERT: Threat in Immediate Vicinity!"
            AlertTier.YELLOW -> "YELLOW ALERT: Threat Approaching"
            AlertTier.OUTSIDE -> return
        }

        val text = "Type: ${threat.type.name} | Distance: $distanceKm km | Target: ${event.targetId}"

        val notification = NotificationCompat.Builder(context, CHANNEL_CRITICAL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationId = BASE_ALERT_NOTIFICATION_ID + (event.threatId.hashCode() and 0x7FFF)
        notificationManager.notify(notificationId, notification)
    }

    fun cancelAllAlerts() {
        notificationManager.cancelAll()
    }
}
