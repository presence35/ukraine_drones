# Architecture — Ukraine Drones

Technical map of the codebase. Read this before exploring so you can jump straight to the
file(s) you need instead of re-deriving the structure. Keep it current: if you add a file or
change a documented invariant, update the relevant section.

## Quick facts

- Single-module Android app (`:app`) — a live air-threat map for Ukraine.
- Jetpack Compose (Material 3, dark-only) + OSMdroid. Kotlin 1.9.24, JDK 17, minSdk 26 /
  targetSdk 34, namespace `ua.ukrainedrones`.
- No runtime backend of ours: data comes straight from the public
  [NEPTUN](https://neptun.in.ua) API (WS stream + REST merge) with a keyless alerts.com.ua
  backup. No Firebase, no push.
- Update feed: static `version.json` + APK on `odesaplay.com.ua`, self-checked daily, in-app install.
- Coroutines + flows throughout; singletons expose `StateFlow`s.

## Package structure

All production code lives in one flat package — `app/src/main/java/ua/ukrainedrones/`. This is
**deliberate for now**; files are grouped conceptually here, not physically. Migrate by
subsystem when it outgrows maintainability: `data/ domain/ state/ ui/ service/ system/`.

## System overview

The UI and the background service are **independent consumers of shared inputs** — both
re-derive state from the same singletons, never from each other.

```
                    ┌──────────────────────┐
 NEPTUN WS ────────►│                      │
 NEPTUN REST ──────►│     NeptunClient     │
 Alerts.com.ua ────►│  (+ AlertsUaClient)  │
                    └──────────┬───────────┘
                               │ StateFlow<NeptunState>
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
          MainViewModel                AlertService
                 │                           │
                 ▼                           ▼
            Compose UI                 Notifications
```

```
ZonePrefs ────────┬──► MainViewModel        Shared domain logic (call, don't duplicate):
                  └──► AlertService         Zones.kt (zoneTier) / Prediction.kt (predictPosition)
LocationTracker ──┬──► MainViewModel        NightMode.kt / Cities.kt (focusAttribution)
                  └──► AlertService
```

## Core ownership

| Concern | Source of truth | Consumers |
| --- | --- | --- |
| NEPTUN connection | `NeptunClient` | UI, `AlertService` |
| Backup oblast alerts | `AlertsUaClient` | `NeptunClient` state |
| Threat prediction | `Prediction.kt` | ViewModel, service |
| Zone tier math | `Zones.kt` | ViewModel, service |
| Night rule resolution | `NightMode.kt` | ViewModel, service |
| User preferences | `ZonePrefs` | all |
| UI orchestration | `MainViewModel` | Compose |
| Background monitoring | `AlertService` | notifications |
| Connection history | `ConnectionLog` | system status |
| Alert history | `AlertHistory` | system status |
| Map rendering | `MapView.kt` | Compose |
| Threat icons | `IconCatalog.kt` | UI |

## Data flow

- **Threat ingest.** NEPTUN WS + REST merge in `NeptunClient` → `StateFlow<NeptunState>`;
  the alerts.com.ua backup merges into `oblastAlerts` only when NEPTUN is down/silent
  (`backupActive`). One singleton, consumed independently by both paths.
- **Position prediction** (both consumers):
  `ThreatSpeedTracker.record(...)` → `estimateWithSource(id, t)` (server → measured → nominal)
  → `predictPosition(t, speed, now)` (dead-reckon active tracks with a real heading, capped at
  the per-type horizon/ghost) → distance to focus → `zoneTier` (slow: distance-to-confirmed-fix,
  fast: predicted-ETA).
- **Update flow.** `UpdateManager.check()` → `Available` → `download()` (progress) →
  `buildInstallIntent()` → system installer.

## Module map

Grouped by subsystem. Every file with its responsibility; a terse *Note:* flags implementation
detail that matters when editing that file.

### App entry / theme

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Single activity; dark theme; starts `AlertService`; legacy osmdroid cache cleanup. *Note:* location→notification permissions defer until first-run onboarding resolves; "Later" sets `permission_prompt_deferred` (re-armed each cold start). |

### Data ingress (NEPTUN)

| File | Responsibility |
| --- | --- |
| `NeptunClient.kt` | `object` singleton. NEPTUN WebSocket (fast-first backoff 1–3s → capped 15s via `reconnectDelayMs`, keep-alive/watchdog) + REST merge → `StateFlow<NeptunState>`; `removedThreats` SharedFlow; `retryNow()`; hosts `OFFLINE_GRACE_MS` (0 ms — every drop is logged/alerted instantly, deliberate) and `USER_SHOT_GRACE_MS` (3 s). `markUserShot(id)` records map long-press fake kills in `NeptunState.userShotAt`; the snapshot handler keeps those ids alive in memory through the grace window so a quick same-id respawn redraws in place instead of re-entering as a new threat. 5s watchdog → `ConnectionLog`. Pure `reconnectDelayMs` (tested). |
| `AlertsUaClient.kt` | `object` singleton. Backup oblast alerts: polls alerts.com.ua ~20s → `StateFlow<AlertsUaState>`; merged into `NeptunState.oblastAlerts` only when NEPTUN down/silent (`backupActive`) **and** the backup itself is up (`backupUp`) — a stale backup payload is never served as live; health → system-status popup. |
| `Threat.kt` | `Threat` model + JSON parsing; `ThreatTypeCatalog` (labels, staleness, nominal speeds); `OblastAlert`/`mergeAlerts`; `translateCourseAssessment` (EN course text); `courseDeg` heading resolution. |

### State / orchestration

| File | Responsibility |
| --- | --- |
| `MainViewModel.kt` | `AndroidViewModel`. Combines NEPTUN + GPS + prefs + 1s clock → `StateFlow<UiState>`; drives the update flow (daily start check, a Settings-open check that raises `updateReminderTick` — a snackbar with a Download action — instead of a clickable toast, which Android can't make touchable, plus manual check/download/install); UI-side copy of zone/focus/alert logic (tradeoffs); `neutralizeThreat` long-press hook. |
| `ConnectionLog.kt` | `object` singleton. Persisted ring buffer (last 10 episodes) fed by the watchdog; commits drops only past `OFFLINE_GRACE_MS`. Pure `commitLogState` (tested); rendered in system-status popup. |
| `AlertHistory.kt` | `object` singleton. Persisted ring buffer (last 20 alerts), written by `AlertService`, read by system-status popup; auto-cleans >6h; entries still open when the service dies are stamped ended on restore (`markInterruptedOpenEntries`); pure serialize/parse (tested). |
| `Prediction.kt` | `LatLng`, `distanceMeters`, per-type `staleAfterMs`/`isExpired`, `predictPosition` dead-reckoning, `ThreatSpeedTracker` (shared thread-safe singleton so every consumer measures the same speed). |
| `Shelters.kt` | Odesa shelter dataset: `Shelter`/`NearestShelter` (adult ~5 km/h, kid ~3 km/h walk minutes), `ShelterIndex` (JSON parse, Odesa bbox, nearest ranking). |

### Domain logic

| File | Responsibility |
| --- | --- |
| `Zones.kt` | `ThreatZone`, `ZoneParams`, `FastThreatTypes`, `zoneTier(...)` — the single source of truth for tiering — plus `etaMinutes`, `reachKm`, `BALLISTIC_SPEED_KMH`. |
| `NightMode.kt` | Shared night helpers for **both** consumers (mirror rule): `isNightActive`, `effectiveZoneParams`/`effectiveArmed`/`effectiveVibration`, `NightConfig`/`NightZones`/`ZoneArmed`/`NightVibration`. |
| `ThreatLevel.kt` | `ThreatLevelModel` — experimental 0–10 gauge (severity × distance × reliability × sources × count × quality × staleness × ETA). |
| `Cities.kt` | ~350 curated cities (by oblast) + `CityLabelOverlay`; EN names from the app's own КМУ №55 transliteration; `focusAttribution` maps focus point → oblast stem via `cityOblast` (majors only). |
| `Transliteration.kt` | Official КМУ №55 Ukrainian→Latin romanization (the EN gate). |
| `ZonePrefs.kt` | `AppLanguage`/`ThreatCardSize`/`ThreatIconSet` + DataStore store (`zone_prefs`): all toggles/thresholds/language/follow/pin/visibility, vibration + night config, and — problematically — serialized `ConnectionLog`/`AlertHistory`, offline-restore state, onboarding flags. Also `threatMapFlow`/`threatAlertFlow`. *Note:* god object mixing prefs with persisted state — split candidate (tradeoffs). |
| `Strings.kt` | UA/EN `StringSet` table (never Android resource localization); `formatRelativeTime`, `formatDateTime` (app language, not device locale). |
| `IconCatalog.kt` | Single source for threat icons: vector/photo/army/comic sets, per-set facing (`baseDeg`), `ThreatIcon` composable; assets in `app/src/main/iconpacks/`. |

### UI (Compose)

| File | Responsibility |
| --- | --- |
| `MainScreen.kt` | Top-level Compose UI: header, alert banner, map, threat strip, `ZonesSheet`, `UpdateDialog`, first-run wizard + battery prompt. *Note:* wizard gated on `!languageChosen` and force-dismissed during an active alert; card flip timed to `DEATH_EXPLOSION_START_MS`; popup height feeds the map as `popupCoverPx`. |
| `ConnectionStatus.kt` | Connection pill (online/backup/offline) + system-status dialog: per-source dots, TEMP force-offline toggle, legend, collapsible `AlertHistory` + `ConnectionLog`, attribution link. |
| `MapView.kt` | `NeptunMapView` + `DARK_TILE_SOURCE`. Owns OSMdroid rendering: zone circles, marker overlays, course rotation, dead-reckoned positions, visual death animations. Threat icons scale with the map zoom (`zoomIconScale`, keep-pace exponential: 1.0×@z10 → 2.0×@z12 → capped 3.0×@~z13.2; applied to live markers from the zoom listener and at rebuild); long-pressing a threat shoots it down for fun: `markUserShot` + death animation, the marker stays hidden while the animation plays and the rebuild redraws it afterwards (the object itself is never removed); the strike camera (`followStrike`) glides onto the target and nothing else — no pan to the launching city, no return after the explosion (off with `follow_bullet` disabled). Selecting a threat opens its popup without moving the camera. *Note:* `overlayKey` excludes raw positions so live movement updates in-place; must not perform alert decisions. |
| `SettingsScreen.kt` | Collapsible sections: language, map centre, per-type Map/Alerts toggles + icon packs, card size, alert toggles (tally + death-animation), vibration sliders, night-mode card, TTA lines, updates, battery exemption, guide; one-time explainers. The night-mode card sits inside the Alerts section in its own subtle indigo-tinted box (background + border, moon icon on its toggle); the card-size tiles preview the small card as its real compact top-left chip (~75% of tile width) and the large card full-width. |
| `ZonesSheet.kt` | "Edit zones" sheet: Slow (km)/Fast (min) sliders + per-zone bells; edits day or night values depending on the active window; night rows carry day reference ticks. |
| `ThreatPopupCard.kt` | Threat popup (small chip / large card); `AlertsOffChip` when type alerts are off; neutralized/neutralizing variant. |
| `ThreatTogglePanel.kt` | Shared Fast/Slow grouping, `ToggleChip`/`IconToggle`, `SlimThreatToggles` (reused by first-run dialog + Settings). |
| `FeatureExplainer.kt` | One-time explainer popups keyed by setting id; seen state via `ZonePrefs.explainerSeen`. |
| `FeatureGuide.kt` / `FeatureDiagrams.kt` | Static feature guide + its diagram drawables. |
| `ShelterScreen.kt` | "Go to shelter" list: nearest Odesa shelters ranked by distance to the focus point, adult/kid walk times (kid row only when the "With kids" setting is on), pull-to-refresh data re-fetch, a GPS-age header with a force precise-fix button (re-prompts location permission), transliterated names in EN; "open in maps" (`geo:` intent); the map button is a red-filled (official alert) or ghost-outlined pill in `MainScreen.kt`. |
| `ThreatDeathAnimation.kt` | `ThreatDeathOverlay`: 5s neutralized flourish (projectile enters from just off the screen edge along the line from the nearest major city → explosion); `DEATH_EXPLOSION_START_MS` drives the card flip; dud on duplicate resolutions; `isActiveFor(id)` guards double-strikes. |

### Background / alerting

| File | Responsibility |
| --- | --- |
| `AlertService.kt` | Foreground `dataSync` service — the always-on monitor. Owns background monitoring and the notification lifecycle: siren/chime/all-clear (60s grace, coalescing), offline notifications + Retry, resolved-threat tally, per-notification vibration, `AlertHistory` feed. Zone-alert dedup: ids drop from `knownZones` only when they leave the zones *and* their `userShotAt` grace (3 s) has passed, so a same-id respawn of a shot-down drone never re-alerts. *Note:* the offline drop is persisted (`offline_pending_since`) so it re-flags after a service kill; evaluates via the shared domain functions, never local formulas. Reconnect milestone flags reset **and** the milestone notification is cancelled on the reconnect transition; official-alert notifications track their own notified-state (turning the toggle back on mid-alert re-announces); `ConnectionLog`/`AlertHistory` restore is awaited before `NeptunClient.start()`. |
| `BootReceiver.kt` | Restarts `AlertService` on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`. |
| `NeutralizedDismissReceiver.kt` | Delete intent for the tally notification (resets the count). |
| `LocationTracker.kt` | `object` singleton. Coarse `NETWORK_PROVIDER` only (~2 min / 250 m), falls back to last known → `StateFlow<LatLng?>`; tracks the last fix time (`lastFixAtMs`) and offers a `forceRefresh()` GPS one-shot for the shelter screen. |
| `BatteryOptimization.kt` | Battery-exemption helpers. |

### Updates / misc

| File | Responsibility |
| --- | --- |
| `UpdateManager.kt` | `UPDATE_BASE_URL`, `check()`/`download()`/`buildInstallIntent()` (FileProvider); `fetchSheltersJson()` pulls the daily shelter-list copy. |
| `UkraineTileProvider.kt` | Tile provider that refuses to download/cache tiles outside Ukraine (+margin). |

### Build / release

| File | Responsibility |
| --- | --- |
| `app/build.gradle.kts` | Android config + custom tasks: `bumpVersion`, `release`, `uploadRelease`. |
| `app/version.properties` | `versionCode`/`versionName` — source of truth for the build + `version.json`. |
| `server/version.json` | Committed example of the generated update feed. |

## Key invariants

Treat these as a contract. If you change one, update **every** place that relies on it.

- **Two independent alert paths.** `MainViewModel` (UI) and `AlertService` (notifications)
  each reimplement zone tiering, focus attribution, prediction. A change to `zoneTier`,
  `ZoneParams`, `focusAttribution`, `staleAfterMs`, or `predictPosition` must be mirrored in
  **both** files or UI and notifications drift. Call `zoneTier` from `Zones.kt` — never inline
  it. (Why/mitigation: Deliberate tradeoffs.)
- **Night mode is shared, not mirrored.** Both sides call `isNightActive`/
  `effectiveZoneParams`/`effectiveArmed`/`effectiveVibration` resolved per tick (`now`); a new
  night knob needs only a pref + a `NightMode.kt` change.

- **Threat type gating.** `threatMapFlow` gates map rendering, `threatAlertFlow` gates alerts —
  decoupled toggles: map-off doesn't silence alerts; alerts-on auto-enables map visibility (an
  armed alert is never hidden). A type hidden from the map is dropped from the map and the
  footer strip; a type with alerts off stays fully mapped (never dimmed — dimming is
  staleness-only) but is omitted from the footer strip, with a red crossed bell on its popup.

- **Focus point.** `followMe` → camera + zones + alerts centre on GPS, else the pinned city;
  pinning disables follow-me. Attribution via `focusAttribution` → `cityOblast` stem match,
  **major cities only** — the ~300 minors are map-context, never banner/alert.

- **Zone tiering.** `zoneTier(t, distKm, speedKmh, ZoneParams(slowRedKm, slowYellowKm,
  fastRedMin, fastYellowMin))`. Fast types (`FastThreatTypes`) tier by ETA (≤ fastRedMin →
  INNER, ≤ fastYellowMin → OUTER); slow types by distance (≤ slowRedKm → INNER, ≤ slowYellowKm
  → OUTER). Slow distance is to the **confirmed raw fix**, never the dead-reckoned position, so
  the drawn circles and alerts always agree. Speed from `ThreatSpeedTracker` (server → measured
  → nominal); AVIATION forced to `BALLISTIC_SPEED_KMH`; a fast threat with no usable speed never
  tiers. `reachKm` caps distance (KAB 70, FPV 40, recon 50, Shahed 1000, else 1500 km). Map
  circles show the slow km thresholds only. Advisory (observation) threats never tier/sound.
  Armed bells are per group×tier (slow/fast × red/yellow), stored for day and night, resolved
  per tick by `effectiveArmed`.

- **Expiry / ghosts.** `staleAfterMs` per type (90s ballistic … 300s UAV). Stale threats stay
  mapped **dimmed** (alpha 0.25, tappable) but are excluded from strip, tiers, gauge, alerts.
  Removal only on server resolve / `remove` frame / `isGhost` (staleness + `STALE_GHOST_CAP_MS`
  ~30 min). When the **selected** threat disappears that way, `MainViewModel` swaps the popup
  for the neutralizing card (flips at `DEATH_EXPLOSION_START_MS`, fades across the explosion);
  with `deathAnimationEnabled` off nothing animates. Dead-reckoning applies to active threats
  with a real heading, capped per type. ViewModel ticks 1s (`nowFlow`); service clears on a 60s
  grace.

- **Place names transliterate, never translate.** Any Ukrainian proper noun in the EN UI →
  `Cities.uaToEn` → `Transliteration.transliterate`; military vocabulary is hard-coded
  (`COURSE_PATTERNS`/`COURSE_GLOSSARY`/`ThreatTypeCatalog`). No network translation.

- **REST never clobbers WS.** REST merge keeps the newer record per threat id
  (`updatedAtMillis`); a REST snapshot can be CDN-stale.

- **Backup never overrides a healthy NEPTUN.** The backup merges only when NEPTUN is
  disconnected or the stream is silent >60s (`backupActive`, driven by `lastFrameAt`).
  The backup only counts while it is actually up (`backupUp`): if its own polls fail, its last
  payload is stale and is never served — while NEPTUN **and** the backup are both down,
  `NeptunState.oblastAlerts` returns NEPTUN's **last-known** list (held, never cleared: an
  outage must not fabricate "alert ended" / false all-clear — the truth arrives on reconnect),
  and `alertSourceFor` never reports `BACKUP`/`BOTH` for a dead backup (held alerts are never
  source-tagged as live).
  `AlertSource` tags only the notification body. TEMP `forceOffline` flag
  (`temp_force_offline`, restored on service start) forces the offline/backup path for testing;
  turning it off while the socket is down kicks a real reconnect (`retryNow`).

- **No cloud / no push.** Monitoring is a local foreground `dataSync` service; alerts stop when
  it stops. No intermediate server buffers anything.

- **Battery-first location.** Coarse `NETWORK_PROVIDER` only (~2 min / 250 m), never fine GPS.

- **Siren channels.** Notification stream by default; alarm stream (DND-piercing) only with
  `sirenOverride`. All-clear never overrides.

## Ownership boundaries

### NeptunClient

Owns:
- network connection
- frame parsing
- REST/WS merge

Must not:
- decide alert tiers
- access UI state
- post notifications

### MainViewModel

Owns:
- UI state derivation
- selected threat
- UI flows

Must not:
- own the WebSocket lifecycle
- directly perform map rendering
- duplicate zone math

### AlertService

Owns:
- background monitoring
- notification lifecycle

Must not:
- depend on Compose
- use UI-only selected state
- implement new zone formulas locally

### MapView

Owns:
- rendering
- map interaction
- visual animation

Must not:
- decide whether a threat should alert
- persist application state

## Deliberate tradeoffs / risks

### Mirrored UI and alert evaluation

`MainViewModel` and `AlertService` independently evaluate threats.

- **Why:** the UI needs continuously refreshed state; background monitoring must continue
  independently of the UI.
- **Risk:** the logic can drift.
- **Mitigation:** shared pure functions own all decision logic — `zoneTier`,
  `predictPosition`, `focusAttribution`, staleness rules, night-mode resolution. Both consumers
  independently orchestrate evaluation, but must call shared domain functions rather than
  duplicate decision formulas; any change needs tests covering both paths (contract in Key
  invariants). `ThreatSpeedTracker` is one shared thread-safe singleton, so the UI and the
  service also measure the **same** speed history — they can't disagree near a zone boundary.
- **Future:** a shared `ThreatEvaluator` returning evaluated threats instead of two giant
  consumers reconstructing the same logic.

### Flat package

All code in one flat package — deliberate for now (Package structure). Cost grows with file
count; migrate by subsystem when it outgrows maintainability.

### Foreground service instead of backend/push

No intermediate server buffers anything, so nothing is missed server-side — but the app must be
kept alive (hence the battery-exemption flow), and alerts stop when monitoring stops.

### Direct third-party API dependency

NEPTUN and alerts.com.ua are consumed directly, parsing isolated in `Threat.kt` /
`AlertsUaClient.kt`. Risk: upstream schema/contract changes. The REST/WS merge protects against
CDN-cached staleness.

### `ZonePrefs` is becoming a god object

It owns every preference plus serialized `ConnectionLog`/`AlertHistory`, offline-restore state
and onboarding flags. Split candidate:

```
AppPrefs
├── MapPrefs / ThreatPrefs / AlertPrefs / NightPrefs / UiPrefs / SystemPrefs
```

with persisted operational state moved out:

```
ConnectionLogStore / AlertHistoryStore
```

The doc distinguishes **preferences** from **persisted application state**.

## Failure modes

| Failure | Behavior | Owner |
| --- | --- | --- |
| NEPTUN offline | Offline pill + notifications; `retryNow()`; backup alerts take over; drop persisted (`offline_pending_since`) across restarts. | `NeptunClient`, `AlertService` |
| Backup offline | `backupUp` false in system-status; no oblast alerts while NEPTUN is also down. | `AlertsUaClient` |
| Stale threats | Dimmed on map; excluded from tiers/alerts/strip/gauge; ghosts removed after ~30 min. | `Prediction.kt` + consumers |
| Service process interrupted | Recovery depends on Android's foreground-service lifecycle; connection + alert history restored from DataStore when restarted. | `AlertService`, DataStore |
| Reboot | `BootReceiver` restarts the service on `BOOT_COMPLETED`. | `BootReceiver` |
| Package replaced | `BootReceiver` restarts the service on `MY_PACKAGE_REPLACED`. | `BootReceiver` |
| Location unavailable | Falls back to last known / pinned city; `focusAttribution` uses the pin. | `LocationTracker`, `Cities` |

## Testing

JUnit unit tests in `app/src/test/java/ua/ukrainedrones/`. Invariant → test: the mirror paths
(`zoneTier`, `predictPosition`, staleness) are pinned by `ZonesTest` / `PredictionTest`, which
both consumers rely on.

- `PredictionTest.kt` — `predictPosition`, `distanceMeters`, staleness, speed tracking.
- `CitiesTest.kt` — city-list integrity; majors-only `nearestCity`/`focusAttribution`.
- `ThreatTest.kt` — JSON parsing, type mapping, course translation.
- `TransliterationTest.kt` — КМУ №55 romanization, no semantic translation, digraph rules.
- `ThreatLevelTest.kt` — threat-level scoring.
- `ZonesTest.kt` — slow km / fast min tiering, `etaMinutes`, `reachKm`, AVIATION override, null-speed fast.
- `UpdateManagerTest.kt` — `versionNameGreater`.
- `NeptunClientTest.kt` — reconnect backoff.
- `NightModeTest.kt` — night-window resolution + effective params/armed.
- `ConnectionLogTest.kt` — episode-commit rules (grace window, blips, recovery, ring-buffer cap).
- `AlertHistoryTest.kt` — serialize/parse round trip, malformed-line skipping, ring-buffer cap.
- `AlertsUaTest.kt` — backup activation (disconnected/silent/force-offline), merge dedupe, offline elapsed, **stale-backup gating** (backup down + NEPTUN down → no oblast alerts; dead backup never merges while NEPTUN is merely silent).
- `VibrationTest.kt` — `vibrationPattern` levels.
- `StringsFormatTest.kt` — `formatDateTime` per-language correctness.
- `TestThreats.kt` — shared `threat(...)` builder helper.

Run: `.\gradlew.bat :app:testDebugUnitTest`

## Build & release

- `.\gradlew.bat :app:assembleDebug` — debug APK (no secrets needed).
- `.\gradlew.bat :app:release` — bumps version, builds release APK, uploads APK + generated
  `version.json` over FTP. Requires git-ignored `app/keystore.properties` (signing) and
  `app/upload.properties` (FTP creds). Release notes from `notes_en.txt` / `notes_ua.txt`.
- Full release workflow is documented in `AGENTS.md` ("release it").