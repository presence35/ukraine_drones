# Architecture — Ukraine Drones

Technical map of the codebase. Read this before exploring so you can jump straight to the
file(s) you need instead of re-deriving the structure. Keep it current: if you add a file or
change a documented invariant, update the relevant section.

## Overview

A single-module Android app (`:app`) — a live air-threat map for Ukraine.

- **UI**: Jetpack Compose (Material 3, dark-only theme) + OSMdroid for the map.
- **Language**: Kotlin 1.9.24, JDK 17, minSdk 26 / targetSdk 34, namespace `ua.ukrainedrones`.
- **Runtime backend**: none of ours. Threat + oblast-alert data come straight from the public
  [NEPTUN](https://neptun.in.ua) API (WebSocket stream + REST merge). No Firebase, no push.
- **Update feed**: a static `version.json` + APK hosted on `odesaplay.com.ua`; the app
  self-checks daily and installs in-app.
- **Concurrency**: coroutines + flows throughout; singletons expose `StateFlow`s.

All production code lives in `app/src/main/java/ua/ukrainedrones/` (flat package, no
subpackages). Unit tests live in `app/src/test/java/ua/ukrainedrones/`.

## Module map

Grouped by subsystem. Every file is listed with its one-line responsibility.

### App entry / theme

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Single activity; dark Material theme; starts `AlertService`; chained location→notification permission requests; one-time legacy osmdroid cache cleanup. |

### Data ingress (NEPTUN)

| File | Responsibility |
| --- | --- |
| `NeptunClient.kt` | `object` singleton. Owns the NEPTUN WebSocket (`wss://neptun.in.ua/api/v1/stream`) with backoff reconnect + keep-alive/watchdog tasks, plus REST merge (`/api/v1/threats`). Exposes `StateFlow<NeptunState>` (threats map, oblastAlerts, connected, `offlineSince`/`offlineElapsedSec`). Handles `snapshot`/`upsert`/`remove`/`alerts`/`heartbeat` frames. `retryNow()` forces an immediate reconnect (used by the offline-notification Retry action). |
| `AlertsUaClient.kt` | `object` singleton. Independent oblast-alert backup source: polls the keyless public `https://alerts.com.ua/api/states` every ~20s and exposes `StateFlow<AlertsUaState>` (alerts + `lastOkAt`/`lastError` health). Merged into `NeptunState.oblastAlerts` only when NEPTUN is down or its alert feed is silent (`backupActive`); its own health (`backupUp`/`backupOfflineElapsedSec`) feeds the system-status popup. |
| `Threat.kt` | `Threat` data model + JSON parsing, `ThreatType`/`ThreatTypeCatalog` (labels/descriptions UA+EN, staleness, nominal speeds), `OblastAlert`, `Reliability`, `AlertSource`, `mergeAlerts`, and `translateCourseAssessment` (best-effort EN translation of NEPTUN's Ukrainian course text). |

### State / orchestration

| File | Responsibility |
| --- | --- |
| `MainViewModel.kt` | `AndroidViewModel`. Combines NEPTUN state + GPS + prefs + a 1s clock into `StateFlow<UiState>` via `buildUiState`. Surfaces `offlineElapsedSec` for the UI. Also drives the update flow. This is the UI-side copy of the zone/focus/alert logic (see invariants). |
| `Prediction.kt` | `LatLng`, `distanceMeters`, per-type staleness (`staleAfterMs`/`isExpired`), `predictPosition` dead-reckoning, and `ThreatSpeedTracker` (speed from server → measured fixes → nominal). |

### Domain logic

| File | Responsibility |
| --- | --- |
| `Zones.kt` | `ThreatZone` (INNER/OUTER), `RadialZones`, `radialZone` (distance→tier), `effectiveZone` + `FAST_THREAT_TYPES` (fast objects claim INNER at any zone entry when "alert sooner"). |
| `ThreatLevel.kt` | `ThreatLevelModel` — experimental 0–10 threat gauge for the popup (severity × distance × reliability × sources × count × quality × staleness × ETA). |
| `Cities.kt` | Curated city list + `CityLabelOverlay` (draws city names, red when oblast on alert, threat counts). `focusAttribution` maps the focus point (pinned city, else nearest city to GPS) to an oblast stem via `cityOblast`. |
| `ZonePrefs.kt` | `AppLanguage`, `ThreatCardSize`, and the DataStore-backed preference store (`zone_prefs`). All toggles/radii/language/follow/pin/threat map-visibility + alert-enable live here. Also `threatMapFlow` and `threatAlertFlow`. |
| `Strings.kt` | `Strings` → `StringSet` — the UA/EN string table (the app never relies on Android resource localization). |
| `ThreatImages.kt` | Reference photos for the expanded threat card: bundled webp for some types, Wikimedia Commons hotlinks for the rest. |

### UI (Compose)

| File | Responsibility |
| --- | --- |
| `MainScreen.kt` | Top-level Compose UI: header (trident glow, title, connection pill, gear), alert banner, `MapScreen`, threat strip, `ZonesSheet`, `UpdateDialog`, first-run `LanguageChooseDialog`. |
| `MapView.kt` | `NeptunMapView` + `DARK_TILE_SOURCE` (CartoDB dark-nolabels). OSMdroid rendering: zone circles, type-icon markers, course rotation, dead-reckoned positions, GPS dot, city pin, scale bar, Ukraine view limits. |
| `SettingsScreen.kt` | Language, map centre (pin city / follow me), per-type toggles + reference photos, threat card size, zone radii, alert toggles, updates, battery exemption, feature guide. |
| `ZonesSheet.kt` | "Edit zones" bottom sheet with live red/yellow radius sliders. |
| `ThreatPopupCard.kt` | Threat detail popup in three sizes: type, region, threat-level gauge, a neutral distance/ETA/speed pill trio, precision, reliability, wave size, time since seen. |
| `FeatureGuide.kt` | Static in-app feature guide. |
| `FeatureDiagrams.kt` | Diagram drawables used by the feature guide. |

### Background / alerting

| File | Responsibility |
| --- | --- |
| `AlertService.kt` | Foreground `dataSync` service — the always-on monitor. Owns `NeptunClient.start()`, consumes `NeptunClient.state` + `LocationTracker.location` + prefs, and posts siren/chime/all-clear notifications with a 60s grace window and event coalescing. Also posts silent offline notifications (30s grace, immediate when an official alert is active) with a Retry action → `NeptunClient.retryNow()`. **Independent reimplementation of the zone/focus logic.** |
| `BootReceiver.kt` | Restarts `AlertService` after reboot (`BOOT_COMPLETED`) and in-app update (`MY_PACKAGE_REPLACED`). |
| `LocationTracker.kt` | `object` singleton. Battery-cheap coarse location: `NETWORK_PROVIDER` only, ~2 min / 250 m, falls back to last known. Exposes `StateFlow<LatLng?>`. |
| `BatteryOptimization.kt` | Helpers for the "keep monitoring alive" battery-exemption flow. |

### Updates / misc

| File | Responsibility |
| --- | --- |
| `UpdateManager.kt` | `UPDATE_BASE_URL` constant, `check()` (version.json → `Available`/`UpToDate`/`Failed`), `download()` (streams APK, validates), `buildInstallIntent()` (FileProvider). |
| `UkraineTileProvider.kt` | OSMdroid tile provider that refuses to download/cache tiles outside Ukraine (+margin) — saves data/battery. |
| `Translate.kt` | `Translator` — unofficial Google translate endpoint (`client=gtx`) for NEPTUN course text; Ukrainian is the fallback. |

### Build / release

| File | Responsibility |
| --- | --- |
| `app/build.gradle.kts` | Android config + custom tasks: `bumpVersion` (versionCode + patch auto-bump), `release` (bump → assemble → upload in a fresh run), `uploadRelease` (build APK, generate `version.json` from `version.properties` + `notes_en.txt`/`notes_ua.txt`, FTP-upload both via `curl`). |
| `app/version.properties` | `versionCode` / `versionName` — source of truth for the build and `version.json`. |
| `server/version.json` | Committed example of the generated update feed. |

## Data flow

```
NEPTUN WS  ─┐
            ├─► NeptunClient ──► StateFlow<NeptunState>   (threats Map<id,Threat>, oblastAlerts = NEPTUN ∪ backup, connected)
NEPTUN REST ┘        ▲
ALERTS.COM.UA REST ──┘   (AlertsUaClient: backup oblast alerts, merged only when NEPTUN down/silent)
                      │ (same singleton consumed by both, independently)
        ┌────────────┴───────────────┐
        ▼                            ▼
MainViewModel                  AlertService (foreground)
  combine:                      combine:
    NeptunClient.state             NeptunClient.state
    radii (ZonePrefs)              LocationTracker.location
    LocationTracker.location       radii (ZonePrefs)
    selectedThreat                  prefs toggles/language/pin
    nowFlow (1s clock)             nowFlow (60s grace)
        │                            │
  buildUiState                    handleState → notifications
  ──► StateFlow<UiState>          (siren/chime/all-clear)
        │
        ▼
  MainScreen/MapView/etc.
```

Position prediction per threat (both ViewModel and AlertService):

```
ThreatSpeedTracker.record(...)          # keep ≤4 recent fixes per id
   → estimateWithSource(id, t)          # server speedKmh → measured fixes → nominal
   → predictPosition(t, speed, now)     # advance along courseDeg within per-type horizon/ghost cap
   → distance to focus → radialZone → effectiveZone (fast-object override)
```

Update flow: `UpdateManager.check()` → `UpdateState.Available` → `download()` (progress) →
`buildInstallIntent()` → system installer.

## Key invariants

Treat these as a contract. If you change one, update **every** place that relies on it.

- **Two independent alert paths.** `MainViewModel` (UI state) and `AlertService`
  (notifications) each reimplement zone tiering, focus attribution, and prediction. A change
  to `effectiveZone`, `FAST_THREAT_TYPES`, `focusAttribution`, `RadialZones`, `staleAfterMs`,
  or `predictPosition` must be mirrored in **both** files or the UI and notifications drift.

- **Threat type gating.** `ZonePrefs.threatMapFlow` gates which types render on the map
  (`MainViewModel`); `threatAlertFlow` gates which types fire alerts (`AlertService`). Turning a
  type's map visibility off also turns its alerts off (coupling); turning alerts off keeps it on
  the map but dimmed. A type hidden from the map or with alerts off is omitted from the footer
  threat strip.

- **Focus point.** `followMe` → camera + zones + alerts centre on GPS; otherwise on the pinned
  city (`ZonePrefs.pinnedCity`). Pinning auto-disables follow-me. Oblast attribution goes
  through `focusAttribution` → `Cities.cityOblast` stem match (e.g. `"Харківськ"` hits
  `"Харківська область"`).

- **Zone tiering.** `RadialZones`: INNER = distance ≤ red (1–20 km), OUTER = ≤ yellow (21–50 km),
  else outside both. With `fastAlertsSooner` on, fast types
  (`FAST_THREAT_TYPES`: ballistic, cruise, KAB, aviation) claim INNER at any zone entry.

- **Expiry / ghosts.** `staleAfterMs` is per-type (90s ballistic … 300s UAV). `isExpired` drops
  stale threats. Dead-reckoning caps at a per-type horizon and max-ghost distance. The ViewModel
  refreshes every 1s via `nowFlow`; `AlertService` uses a 60s grace window before clearing.

- **REST never clobbers WS.** REST merge keeps the newer record per threat id
  (`updatedAtMillis` compare); a REST snapshot is CDN-cached and can be older than the stream.

- **Backup never overrides a healthy NEPTUN.** The alerts.com.ua backup (`AlertsUaClient`)
  feeds `NeptunState.oblastAlerts` only when NEPTUN is disconnected or its own alert feed has
  been silent for >60s (`backupActive`). Both `MainViewModel` and `AlertService` read the same
  union (`oblastAlerts`), so the backup needs no changes to the mirrored zone/focus logic. The
  `AlertSource` tag (NEPTUN / BACKUP / BOTH) only labels the notification body. Backup health
  (`backupUp`/`backupOfflineElapsedSec`) is surfaced read-only in the system-status popup.

- **No cloud / no push.** Monitoring is a local foreground `dataSync` service. Alerts stop when
  it stops ("Stop Monitoring & Exit"). There is no intermediate server to buffer anything.

- **Battery-first location.** `LocationTracker` uses coarse `NETWORK_PROVIDER` only
  (~2 min / 250 m) — never fine GPS. The alert zones are km-scale, so this is deliberate.

- **Siren channels.** Notification stream by default (respects ringer/vibrate). Only with
  `sirenOverride` do sirens use the alarm stream (sound even on vibrate/silent). All-clear
  never overrides.

## Testing

JUnit unit tests in `app/src/test/java/ua/ukrainedrones/`:

- `PredictionTest.kt` — `predictPosition`, `distanceMeters`, staleness, speed tracking.
- `ThreatTest.kt` — JSON parsing, type mapping, course translation.
- `ThreatLevelTest.kt` — threat-level scoring.
- `ZonesTest.kt` — `radialZone` / `effectiveZone`.
- `UpdateManagerTest.kt` — `versionNameGreater`.
- `TestThreats.kt` — shared `threat(...)` builder helper.

Run: `.\gradlew.bat :app:testDebugUnitTest`

## Build & release

- `.\gradlew.bat :app:assembleDebug` — debug APK (no secrets needed).
- `.\gradlew.bat :app:release` — bumps version, builds release APK, uploads APK + generated
  `version.json` over FTP. Requires git-ignored `app/keystore.properties` (signing) and
  `app/upload.properties` (FTP creds). Release notes come from `notes_en.txt` / `notes_ua.txt`.
- Full release workflow is documented in `AGENTS.md` ("release it").
