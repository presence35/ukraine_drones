package ua.okoneba.feature.alerts.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ua.okoneba.core.database.OkoNebaDatabase
import ua.okoneba.core.database.repository.RoomAuditLogRepository
import ua.okoneba.core.database.repository.RoomEpisodeLedgerRepository
import ua.okoneba.core.datastore.DeviceProtectedDataStoreRepository
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.SystemHealthState
import ua.okoneba.core.domain.model.UserLocationState
import ua.okoneba.core.domain.repository.AuditLogLevel
import ua.okoneba.core.network.feed.BackupFeedProvider
import ua.okoneba.core.network.feed.NeptunFeedProvider
import ua.okoneba.feature.alerts.location.LocationSanityChecker
import ua.okoneba.feature.alerts.notification.AlertNotificationDispatcher

/**
 * Android 16 (API 36) Foreground Service for continuous headless air-threat monitoring.
 *
 * Foreground Service Type: FOREGROUND_SERVICE_TYPE_LOCATION
 * Direct Boot: android:directBootAware="true"
 * Storage Context: Explicitly uses Device-Protected Storage (DE) for all pre-unlock operations.
 */
class AlertService : Service() {

    companion object {
        const val ACTION_START_MONITORING = "ua.okoneba.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "ua.okoneba.action.STOP_MONITORING"

        fun startMonitoringIntent(context: Context): Intent {
            return Intent(context, AlertService::class.java).apply {
                action = ACTION_START_MONITORING
            }
        }

        fun stopMonitoringIntent(context: Context): Intent {
            return Intent(context, AlertService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var notificationDispatcher: AlertNotificationDispatcher
    private lateinit var deDataStoreRepo: DeviceProtectedDataStoreRepository
    private lateinit var database: OkoNebaDatabase
    private lateinit var episodeRepo: RoomEpisodeLedgerRepository
    private lateinit var auditRepo: RoomAuditLogRepository

    private val locationSanityChecker = LocationSanityChecker()
    private val zoneEngine = ZoneEvaluationEngine()
    private val masterEvaluator = MasterThreatEvaluator()

    private val neptunFeed = NeptunFeedProvider()
    private val backupFeed = BackupFeedProvider()

    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        val deContext = if (isDeviceProtectedStorage) this else createDeviceProtectedStorageContext()

        notificationDispatcher = AlertNotificationDispatcher(this)
        deDataStoreRepo = DeviceProtectedDataStoreRepository.create(deContext)
        database = OkoNebaDatabase.createDeviceProtectedDatabase(deContext)
        episodeRepo = RoomEpisodeLedgerRepository(database.episodeLedgerDao())
        auditRepo = RoomAuditLogRepository(database.auditLogDao(), serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING

        when (action) {
            ACTION_STOP_MONITORING -> {
                handleExplicitStop()
                return START_NOT_STICKY
            }
            ACTION_START_MONITORING -> {
                startForegroundWithProperType()
                if (!isRunning) {
                    startMonitoringPipeline()
                }
                return START_STICKY
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithProperType() {
        val notification = notificationDispatcher.buildForegroundNotification(
            healthState = SystemHealthState.HEALTHY,
            activeSourceId = "Initializing",
            activeThreatCount = 0
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            AlertNotificationDispatcher.NOTIFICATION_ID_FOREGROUND,
            notification,
            foregroundType
        )
    }

    private fun startMonitoringPipeline() {
        isRunning = true

        serviceScope.launch {
            auditRepo.record("SERVICE_START", "AlertService started in foreground mode", AuditLogLevel.INFO)
            deDataStoreRepo.setMonitoringEnabled(true)
        }

        // Start network feeds
        neptunFeed.start(serviceScope)
        backupFeed.start(serviceScope)

        // Wire feed health to MasterThreatEvaluator
        serviceScope.launch {
            neptunFeed.health.collectLatest { health ->
                masterEvaluator.updateFeedHealth(health)
            }
        }
        serviceScope.launch {
            backupFeed.health.collectLatest { health ->
                masterEvaluator.updateFeedHealth(health)
            }
        }

        // Wire feed telemetry snapshots to MasterThreatEvaluator
        serviceScope.launch {
            neptunFeed.telemetrySnapshots.collectLatest { snapshot ->
                masterEvaluator.ingestFeedSnapshot(snapshot)
            }
        }
        serviceScope.launch {
            backupFeed.telemetrySnapshots.collectLatest { snapshot ->
                masterEvaluator.ingestFeedSnapshot(snapshot)
            }
        }

        // Wire Authoritative Threat State -> Zone Evaluation & Deduplication
        serviceScope.launch {
            masterEvaluator.authoritativeState.collectLatest { authState ->
                val settings = deDataStoreRepo.getSettings()
                val targets = mutableListOf<MonitoredTarget>()

                // Add follow-me target if location is available
                val lastLoc = settings.lastKnownLocation
                val followMeState = if (lastLoc != null) {
                    locationSanityChecker.processLocationUpdate(
                        latitude = lastLoc.latitude,
                        longitude = lastLoc.longitude,
                        accuracyMeters = lastLoc.accuracyMeters,
                        timestamp = lastLoc.timestamp
                    )
                } else {
                    UserLocationState.Unlocated("Awaiting coarse location fix")
                }
                targets.add(MonitoredTarget.FollowMe(locationState = followMeState))

                // Add configured pinned targets
                targets.addAll(settings.pinnedTargets)

                // Run zone evaluation against authoritative snapshot
                val evaluations = zoneEngine.evaluateSnapshot(
                    threats = authState.threats,
                    targets = targets,
                    zoneConfig = settings.zoneConfig
                )

                // Process evaluations for alert deduplication and dispatch
                val activeKeys = mutableSetOf<String>()
                val now = System.currentTimeMillis()

                for (eval in evaluations) {
                    val threat = eval.threat
                    val key = "${threat.sourceId}:${threat.threatId}:${eval.targetId}"
                    activeKeys.add(key)

                    if (eval.tier.isAlert) {
                        val previousEpisode = episodeRepo.getEpisode(threat.sourceId, threat.threatId, eval.targetId)
                        val alertEvent = zoneEngine.determineAlertEvent(eval, previousEpisode, settings.alertPolicy)

                        if (alertEvent != null) {
                            // Atomic record before dispatch to prevent duplicates on crash
                            episodeRepo.recordAlert(
                                sourceId = threat.sourceId,
                                threatId = threat.threatId,
                                targetId = eval.targetId,
                                tier = eval.tier,
                                timestamp = now
                            )

                            // Dispatch heads-up notification
                            notificationDispatcher.dispatchAlertNotification(alertEvent)
                            auditRepo.record(
                                "ALERT_DISPATCHED",
                                "Alert emitted for ${threat.sourceId}:${threat.threatId} at target ${eval.targetId} (Tier: ${eval.tier.name})",
                                AuditLogLevel.WARN
                            )
                        } else {
                            // Update last seen timestamp
                            episodeRepo.updateLastSeen(threat.sourceId, threat.threatId, now)
                        }
                    }
                }

                // Inactive episode cleanup
                episodeRepo.markEpisodesInactiveNotIn(activeKeys, now)

                // Update foreground notification status
                val updatedNotification = notificationDispatcher.buildForegroundNotification(
                    healthState = authState.systemHealth,
                    activeSourceId = authState.authoritativeSourceId,
                    activeThreatCount = authState.threats.size
                )
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(AlertNotificationDispatcher.NOTIFICATION_ID_FOREGROUND, updatedNotification)
            }
        }
    }

    private fun handleExplicitStop() {
        isRunning = false
        serviceScope.launch {
            deDataStoreRepo.setMonitoringEnabled(false)
            auditRepo.record("SERVICE_STOP", "Monitoring explicitly stopped by user", AuditLogLevel.INFO)
            neptunFeed.stop()
            backupFeed.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        neptunFeed.stop()
        backupFeed.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
