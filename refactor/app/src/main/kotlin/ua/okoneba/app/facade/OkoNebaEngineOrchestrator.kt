package ua.okoneba.app.facade

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.AlertEvent
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.OkoNebaSystemState
import ua.okoneba.core.domain.model.SystemHealthState
import ua.okoneba.core.domain.model.UserLocationState
import ua.okoneba.core.domain.model.ZoneConfiguration
import ua.okoneba.core.domain.plugin.FlourishPluginManager
import ua.okoneba.core.domain.plugin.FlourishToken
import ua.okoneba.core.domain.plugin.FlourishType
import ua.okoneba.core.domain.repository.AuditLogLevel
import ua.okoneba.core.domain.repository.AuditLogRepository
import ua.okoneba.core.domain.repository.EpisodeLedgerRepository
import ua.okoneba.core.domain.repository.MonitoringPreferencesRepository
import ua.okoneba.core.domain.repository.StoredLocation
import ua.okoneba.core.network.feed.FeedProvider
import ua.okoneba.feature.alerts.location.LocationSanityChecker

/**
 * System State Facade and Engine Orchestrator.
 * Exposes a single read-only facade `val systemState: StateFlow<OkoNebaSystemState>`
 * to future UI/presentation layers. UI never accesses internal combiners, repositories,
 * or pipelines directly.
 */
class OkoNebaEngineOrchestrator(
    private val masterEvaluator: MasterThreatEvaluator,
    private val zoneEngine: ZoneEvaluationEngine,
    private val episodeRepo: EpisodeLedgerRepository,
    private val preferencesRepo: MonitoringPreferencesRepository,
    private val auditRepo: AuditLogRepository,
    private val feeds: List<FeedProvider>,
    private val locationSanityChecker: LocationSanityChecker = LocationSanityChecker(),
    private val flourishPluginManager: FlourishPluginManager? = null,
    private val orchestratorScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _systemState = MutableStateFlow(
        OkoNebaSystemState(
            health = SystemHealthState.DEGRADED_NO_FEEDS,
            authoritativeSourceId = null,
            sourceFreshnessMs = null,
            threatSnapshot = emptyList(),
            monitoredTargets = emptyList(),
            zoneConfiguration = ZoneConfiguration.safeCreate(
                ZoneConfiguration.DEFAULT_RED_RADIUS_KM,
                ZoneConfiguration.DEFAULT_YELLOW_RADIUS_KM
            ),
            followMeLocationState = UserLocationState.Unlocated("Awaiting location initialization"),
            activeEvaluations = emptyList(),
            activeEpisodesCount = 0,
            lastEvaluationTimestamp = 0L
        )
    )
    val systemState: StateFlow<OkoNebaSystemState> = _systemState.asStateFlow()

    private val _alertEvents = MutableStateFlow<AlertEvent?>(null)
    val alertEvents: StateFlow<AlertEvent?> = _alertEvents.asStateFlow()

    init {
        startOrchestration()
    }

    private fun startOrchestration() {
        // 1. Observe and bind all feed providers
        for (feed in feeds) {
            feed.start(orchestratorScope)

            orchestratorScope.launch {
                feed.health.collectLatest { healthInfo ->
                    masterEvaluator.updateFeedHealth(healthInfo)
                }
            }

            orchestratorScope.launch {
                // Conflate to ensure latest packet wins under heavy load / reconnect bursts
                feed.telemetrySnapshots.conflate().collectLatest { snapshot ->
                    masterEvaluator.ingestFeedSnapshot(snapshot)
                }
            }
        }

        // 2. Observe active episodes count from Room database
        orchestratorScope.launch {
            episodeRepo.observeActiveEpisodesCount().collectLatest { count ->
                _systemState.value = _systemState.value.copy(activeEpisodesCount = count)
            }
        }

        // 3. Central evaluation loop triggered by changes in authoritative threat state or preferences
        orchestratorScope.launch {
            masterEvaluator.authoritativeState.conflate().collectLatest { authState ->
                evaluateCurrentState(authState.threats, authState.systemHealth, authState.authoritativeSourceId, authState.sourceFreshnessMs)
            }
        }
    }

    /**
     * Updates device location with sanity checking and trigger re-evaluation if significant.
     */
    fun onLocationUpdate(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val locState = locationSanityChecker.processLocationUpdate(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestamp = timestamp
        )

        val previousLocState = _systemState.value.followMeLocationState
        _systemState.value = _systemState.value.copy(followMeLocationState = locState)

        if (locState is UserLocationState.Valid) {
            val prevValid = (previousLocState as? UserLocationState.Valid)
            if (locationSanityChecker.isSignificantMovement(locState, prevValid)) {
                // Persist coarse location in DE preferences for Direct Boot restoration
                orchestratorScope.launch {
                    preferencesRepo.updateLastKnownLocation(
                        StoredLocation(
                            latitude = locState.latitude,
                            longitude = locState.longitude,
                            accuracyMeters = locState.accuracyMeters,
                            timestamp = locState.timestamp
                        )
                    )
                }

                // Trigger re-evaluation with updated position
                val authState = masterEvaluator.authoritativeState.value
                evaluateCurrentState(authState.threats, authState.systemHealth, authState.authoritativeSourceId, authState.sourceFreshnessMs)
            }
        } else if (locState is UserLocationState.Suspect) {
            orchestratorScope.launch {
                auditRepo.record("LOCATION_SUSPECT", locState.reason, AuditLogLevel.WARN)
            }
        }
    }

    private fun evaluateCurrentState(
        threats: List<NormalizedThreat>,
        systemHealth: SystemHealthState,
        authoritativeSourceId: String?,
        sourceFreshnessMs: Long?
    ) {
        orchestratorScope.launch {
            val settings = preferencesRepo.getSettings()
            val now = System.currentTimeMillis()

            // Build target list
            val targets = mutableListOf<MonitoredTarget>()
            targets.add(MonitoredTarget.FollowMe(locationState = _systemState.value.followMeLocationState))
            targets.addAll(settings.pinnedTargets)

            // Run zone evaluations
            val evaluations = zoneEngine.evaluateSnapshot(
                threats = threats,
                targets = targets,
                zoneConfig = settings.zoneConfig,
                evaluatedAt = now
            )

            // Process alerts and update ledger
            val activeKeys = mutableSetOf<String>()

            for (eval in evaluations) {
                val threat = eval.threat
                val key = "${threat.sourceId}:${threat.threatId}:${eval.targetId}"
                activeKeys.add(key)

                if (eval.tier.isAlert) {
                    val previousEpisode = episodeRepo.getEpisode(threat.sourceId, threat.threatId, eval.targetId)
                    val alertEvent = zoneEngine.determineAlertEvent(eval, previousEpisode, settings.alertPolicy)

                    if (alertEvent != null) {
                        // Atomic record before dispatch
                        episodeRepo.recordAlert(
                            sourceId = threat.sourceId,
                            threatId = threat.threatId,
                            targetId = eval.targetId,
                            tier = eval.tier,
                            timestamp = now
                        )

                        _alertEvents.value = alertEvent

                        // Dispatch flourish token safely without blocking
                        flourishPluginManager?.dispatchFlourishToken(
                            FlourishToken(
                                type = if (eval.tier == AlertTier.RED) FlourishType.THREAT_ENTERED_RED else FlourishType.THREAT_ENTERED_YELLOW,
                                threatId = threat.threatId,
                                targetId = eval.targetId,
                                timestamp = now
                            )
                        )
                    } else {
                        episodeRepo.updateLastSeen(threat.sourceId, threat.threatId, now)
                    }
                }
            }

            // Mark inactive episodes
            episodeRepo.markEpisodesInactiveNotIn(activeKeys, now)

            // Update read-only facade state
            _systemState.value = OkoNebaSystemState(
                health = systemHealth,
                authoritativeSourceId = authoritativeSourceId,
                sourceFreshnessMs = sourceFreshnessMs,
                threatSnapshot = threats,
                monitoredTargets = targets,
                zoneConfiguration = settings.zoneConfig,
                followMeLocationState = _systemState.value.followMeLocationState,
                activeEvaluations = evaluations,
                activeEpisodesCount = _systemState.value.activeEpisodesCount,
                lastEvaluationTimestamp = now
            )
        }
    }
}
