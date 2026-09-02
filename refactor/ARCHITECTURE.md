# OkoNeba Core Engine — Architecture & Technical Reference

This document details the architecture, design decisions, and platform invariants of the **OkoNeba Core Engine**, a headless, production-ready real-time air-threat monitoring subsystem for Android 16 (API 36).

---

## 1. System Architecture & Module Boundaries

The core engine is structured into focused, modular Gradle modules to enforce separation of concerns and prevent leakage of Android-specific or presentation logic into pure business domains:

```
:core:domain
├── NormalizedThreat, ThreatType, Coordinates, SourceTrajectory
├── MonitoredTarget (FollowMe, Pinned), UserLocationState
├── ZoneConfiguration, AlertTier (OUTSIDE, YELLOW, RED), EvaluatedThreat
├── ZoneEvaluationEngine (Great-circle geodesic mathematics, tier classification)
├── MasterThreatEvaluator (Single authoritative feed coordination, failover, retention limit)
├── AlertEvent, AlertDeduplicationPolicy, SystemHealthState, OkoNebaSystemState
└── Flourish contracts & isolated exception-safe dispatcher

:core:network
├── FeedProvider, NeptunFeedProvider (Priority 0), BackupFeedProvider (Priority 10)
├── RawTelemetryPacketDto, RawThreatDto
├── TelemetryParser (Zero-crash parser, coordinate validation)
└── Conflated latest-state telemetry flows (BufferOverflow.DROP_OLDEST)

:core:database
├── Room Database (OkoNebaDatabase with DE storage builder)
├── EpisodeLedgerEntity, EpisodeLedgerDao (Atomic episode transactions, index-backed queries)
├── AuditLogEntity, AuditLogDao (Bounded rolling audit log <= 1,000 records)
└── RoomEpisodeLedgerRepository, RoomAuditLogRepository

:core:datastore
├── DeviceProtectedDataStoreRepository (DataStore on Device-Protected Storage)
└── Preferences schema for pre-unlock monitoring state

:feature:alerts
├── AlertService (Android 16 FGS with location type and directBootAware="true")
├── AlertNotificationDispatcher (Notification Channels: Critical Alerts & Status)
└── LocationSanityChecker (Coarse baseline, supersonic/impossible jump filter, GPS noise thresholding)

:app
├── OkoNebaApp (Application class with Hilt root)
├── OkoNebaEngineOrchestrator (Single read-only StateFlow<OkoNebaSystemState> facade)
├── DirectBootReceiver (LOCKED_BOOT_COMPLETED, BOOT_COMPLETED handler)
├── UserUnlockReceiver (USER_UNLOCKED handler)
└── Hilt DI Modules (CoreModule, DatabaseModule, NetworkModule, DataStoreModule)
```

---

## 2. Hard Invariants

1. **One Authoritative Source at a Time**: Feeds operate independently. Feeds are never merged or fused.
2. **Source Data Is Authoritative**: The engine does not invent local physics, flight trajectories, or threat classifications.
3. **Feed Failure Is Never "All Clear"**: `NO FEED` is represented as `DEGRADED` (while retained snapshot is within the 120s safety limit) or `DEGRADED_NO_FEEDS`. It is never represented as an empty threat list under `HEALTHY`.
4. **Source Switch Is Not a Threat Event**: Failovers switch the active snapshot without manufacturing entry, exit, or escalation events.
5. **Multi-Target Independence**: `FollowMe` and multiple `Pinned` locations are evaluated independently against the authoritative snapshot.
6. **Concentric Zone Geometry**:
   - RED: `0.0 km <= distance <= redRadius` (`2 km <= redRadius <= 20 km`)
   - YELLOW: `redRadius < distance <= yellowRadius` (`redRadius + 2 km <= yellowRadius <= 50 km`)
   - Boundaries are inclusive.
7. **Process Restart Deduplication**: The atomic Room episode ledger tracks the highest alert tier emitted per `(sourceId, threatId, targetId)`. Process death and restart cannot re-alert for existing active threats. Threats entering zones during downtime are detected upon next snapshot.
8. **Direct Boot Safety**: Pre-unlock components only access Device-Protected Storage (`Context.createDeviceProtectedStorageContext()`). Credential-Encrypted (CE) dependencies are never touched before unlock.
9. **Explicit Stop Preservation**: If the user explicitly stops monitoring, `isMonitoringEnabled = false` is committed to DE DataStore, preventing post-reboot automatic restart.

---

## 3. Android 16 (API 36) Foreground Service Strategy

### Manifest FGS Type Selection
Android 14+ through Android 16 (API 36) strictly enforces typed foreground services:
- **`dataSync` is NOT used**: Android 15+ imposes strict runtime execution time limits on `dataSync` FGS, causing `ForegroundServiceTimeoutException` if run continuously.
- **`remoteMessaging` is NOT used**: Documented specifically for SMS/MMS device-to-device continuity.
- **`location` is the selected FGS type**:
  - Declared in Manifest: `android:foregroundServiceType="location"`.
  - Required permissions: `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_LOCATION`, `android.permission.ACCESS_COARSE_LOCATION`.
  - The service actively tracks the device's coarse coordinates for the `FollowMe` monitored target.

### Runtime and Reboot Compliance
1. **Normal Launch**: Initiated when the user enables monitoring or opens the application via `ContextCompat.startForegroundService()`.
2. **Direct Boot Launch**: When device reboots, `DirectBootReceiver` receives `ACTION_LOCKED_BOOT_COMPLETED`, inspects `isMonitoringEnabled` in DE DataStore, and starts `AlertService` with ongoing `CHANNEL_MONITORING_STATUS` notification.
3. **Missing Permissions**: If location permission is revoked or disabled, `FollowMe` target transitions cleanly to `UserLocationState.Unlocated("Location permission missing")`. `Pinned` targets (e.g. Kyiv, Kharkiv, Odesa) continue uninterrupted.

---

## 4. Episode Ledger & Deduplication Lifecycle

Every alert evaluation passes through the atomic deduplication gate:

```
Authoritative Snapshot Ingestion
             ↓
ZoneEvaluationEngine.evaluateSnapshot()
             ↓
For each EvaluatedThreat:
  Check EpisodeRecord in Room Database (sourceId, threatId, targetId)
             ↓
  Is currentTier > highestAlertTier?
    ├── YES (e.g. OUTSIDE -> YELLOW or YELLOW -> RED):
    │     1. Atomic DB Transaction: Record highest tier + timestamp
    │     2. Dispatch AlertNotification
    │     3. Dispatch FlourishToken (non-blocking)
    └── NO (e.g. RED -> YELLOW or RED -> RED):
          1. Update lastSeenAt timestamp in Room
          2. Suppress notification
```

---

## 5. Flow Backpressure and Battery Protections

- **Conflation**: Telemetry packet streams and evaluation loops utilize `.conflate()` with `BufferOverflow.DROP_OLDEST` to prevent memory growth during network bursts.
- **GPS Noise Filtering**: `LocationSanityChecker` discards sub-threshold terrestrial movements (<25 meters) unless a zone boundary is approached, preventing continuous CPU re-evaluation on micro-jitter.
- **Sanity Bounds**: Terrestrial location jumps exceeding 1,200 km/h over distances > 50 km are marked `UserLocationState.Suspect` and discarded in favor of the retained last valid position.
- **Bounded Logging**: `AuditLogDao` caps log storage at 1,000 rows with asynchronous background writes, ensuring logging never blocks the threat evaluation pipeline.
