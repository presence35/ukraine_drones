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
| `MainActivity.kt` | Single activity; dark Material theme; starts `AlertService`; one-time legacy osmdroid cache cleanup. Chained location→notification permission requests are deferred until the first-run onboarding resolves (language + battery prompt, tracked via `language_chosen` + `battery_onboard_shown`), so system dialogs never beat the onboarding dialogs. |

### Data ingress (NEPTUN)

| File | Responsibility |
| --- | --- |
| `NeptunClient.kt` | `object` singleton. Owns the NEPTUN WebSocket (`wss://neptun.in.ua/api/v1/stream`) with fast-first-attempt backoff reconnect (1-3s on the first retry, exponential capped at 15s) + keep-alive/watchdog tasks, plus REST merge (`/api/v1/threats`). Exposes `StateFlow<NeptunState>` (threats map, oblastAlerts, connected, `offlineSince`/`offlineElapsedSec`). Handles `snapshot`/`upsert`/`remove`/`alerts`/`heartbeat` frames. Emits `removedThreats: SharedFlow<ThreatRemoved>` (resolved/remove frames — drives the map death animation). `retryNow()` forces an immediate reconnect (used by the offline-notification Retry action). Hosts the shared `OFFLINE_GRACE_MS` (30s) and a 5s watchdog feeding `ConnectionLog`. `reconnectDelayMs` is pure (unit-tested). |
| `AlertsUaClient.kt` | `object` singleton. Independent oblast-alert backup source: polls the keyless public `https://alerts.com.ua/api/states` every ~20s and exposes `StateFlow<AlertsUaState>` (alerts + `lastOkAt`/`lastError` health). Merged into `NeptunState.oblastAlerts` only when NEPTUN is down or its alert feed is silent (`backupActive`); its own health (`backupUp`/`backupOfflineElapsedSec`) feeds the system-status popup. |
| `Threat.kt` | `Threat` data model + JSON parsing, `ThreatType`/`ThreatTypeCatalog` (labels/descriptions UA+EN, staleness, nominal speeds), `OblastAlert`, `Reliability`, `AlertSource`, `mergeAlerts`, and `translateCourseAssessment` (EN rendering of NEPTUN's course text: hard-coded sentence templates + glossary, place names transliterated). |

### State / orchestration

| File | Responsibility |
| --- | --- |
| `MainViewModel.kt` | `AndroidViewModel`. Combines NEPTUN state + GPS + prefs + a 1s clock into `StateFlow<UiState>` via `buildUiState`. Also drives the update flow. This is the UI-side copy of the zone/focus/alert logic (see invariants). A TEMP `tempNeutralize` hook treats a long-pressed threat id as neutralized so its card self-destructs like a real resolution. |
| `ConnectionLog.kt` | `object` singleton. Persisted ring buffer of the last 10 connection statuses (ONLINE/OFFLINE/BACKUP with episode durations). Fed by `NeptunClient`'s 5s watchdog via `observe(...)`; only commits a drop once it outlasts the shared `OFFLINE_GRACE_MS` (random hiccups are ignored); the in-progress episode is exposed live (`currentEpisode`). Rendered in the System-status popup. The episode-commit decision is the pure `commitLogState` (unit-tested); `attach()` restores DataStore asynchronously off the main thread. |
| `AlertHistory.kt` | `object` singleton. Persisted ring buffer of the last 20 fired alerts (zone sirens + official alerts) with start/end times, tier, threat type, locality and distance. Written by `AlertService` at siren/chime posts (opens keyed by threat id / "official", closes when the alert ends or the grace window clears); read by the System-status popup. Serialization is the pure `serializeAlertHistory` / `parseAlertHistory` (unit-tested). |
| `Prediction.kt` | `LatLng`, `distanceMeters`, per-type staleness (`staleAfterMs`/`isExpired`), `predictPosition` dead-reckoning, and `ThreatSpeedTracker` (speed from server → measured fixes → nominal). |

### Domain logic

| File | Responsibility |
| --- | --- |
| `Zones.kt` | `ThreatZone` (INNER/OUTER), `ZoneParams` (slow km / fast min thresholds), `FastThreatTypes` (the single source for the fast group), `zoneTier(t, distKm, speedKmh, params)` — the single source of truth for zone tiering, plus `etaMinutes`, `reachKm` (per-type max cover) and `BALLISTIC_SPEED_KMH` (AVIATION override). |
| `NightMode.kt` | Night-mode shared helpers consumed by **both** `MainViewModel` and `AlertService` (mirror rule): `NightConfig` (window), `NightZones` (night thresholds + the four armed bells), `ZoneArmed` (slow/fast × red/yellow), `isNightActive` (overnight-aware), `effectiveZoneParams`/`effectiveArmed` (night values only while the window is active). |
| `ThreatLevel.kt` | `ThreatLevelModel` — experimental 0–10 threat gauge for the popup (severity × distance × reliability × sources × count × quality × staleness × ETA). |
| `Cities.kt` | Curated city list (grouped by oblast region, ~350 places) + `CityLabelOverlay` (draws city names, red when oblast on alert, threat counts). EN names derive from the app's own КМУ №55 transliteration. `focusAttribution` maps the focus point (pinned city, else nearest **major** city to GPS) to an oblast stem via `cityOblast`; minor cities are map-context only (zoom ≥ 10) and never drive attribution/banner. |
| `Transliteration.kt` | `Transliteration` — official КМУ №55 Ukrainian→Latin romanization. Place names are transliterated, never semantically translated (the EN gate). |
| `ZonePrefs.kt` | `AppLanguage`, `ThreatCardSize`, `ThreatIconSet`, and the DataStore-backed preference store (`zone_prefs`). All toggles/km+min zone thresholds/language/follow/pin/threat map-visibility + alert-enable + map-scale + icon-set live here, plus the serialized `ConnectionLog` entries, in-progress episode, offline-restore state (`offline_pending_since`), the serialized `AlertHistory`, the one-shot battery-onboarding flag (`battery_onboard_shown`) and the Fast/Slow vibration strengths (`fast_vibration_level` / `slow_vibration_level`, 0–4). Also `threatMapFlow` and `threatAlertFlow`. Night mode keeps its own copies of the zone thresholds/armed bells/always-sound overrides plus the window (`night*`) here, so the day config is never clobbered. |
| `Strings.kt` | `Strings` → `StringSet` — the UA/EN string table (the app never relies on Android resource localization). Also hosts `formatRelativeTime` and the site-wide absolute-datetime formatter `formatDateTime(lang, millis)` (UA `dd.MM, HH:mm`, EN `MMM d, HH:mm` — follows the app language, not the device locale). |
| `ThreatImages.kt` | Reference photos for the expanded threat card: bundled webp for some types, Wikimedia Commons hotlinks for the rest. |
| `IconCatalog.kt` | Single source of truth for threat icon drawables: `classicRes` (vector set), `res(type, set)` (vector, photo, army, or comic set — photos live in `iconpacks/photo/drawable-nodpi/threat_photo_*.png`, army in `iconpacks/army/drawable-nodpi/threat_army_*.png`, comic in `iconpacks/comic/drawable-nodpi/threat_comic_*.png`), per-set baked-in facing `photoBaseDeg`/`armyBaseDeg`/`comicBaseDeg` (also via `baseDeg(type, set)` for map rotation), and the `ThreatIcon` composable that letterboxes raster sets in square slots. Replaces the old per-file `iconFor`/`iconResFor`/`threatIconRes` mappings. Icon-pack assets live under `app/src/main/iconpacks/` (classic + photo + army + comic) and are merged into the res namespace via `res.srcDirs` in `build.gradle.kts`. |

### UI (Compose)

| File | Responsibility |
| --- | --- |
| `MainScreen.kt` | Top-level Compose UI: header (trident glow, title — tap to fit the whole country, gear), alert banner, `MapScreen`, threat strip, `ZonesSheet` (fully-visible zones panel), `UpdateDialog`, first-run `LanguageChooseDialog` (with slim threat toggles) and the one-shot first-run battery prompt (`BatteryOnboardingDialog`, skipped automatically when the OS already exempts the app). |
| `ConnectionStatus.kt` | Connection pill (red "offline" / amber "backup" / green "online" — the online state shows the NEPTUN emblem and a green label) + the "System status" dialog: per-source dot rows (NEPTUN + backup alerts.com.ua), backup scope note, TEMP force-offline toggle, legend, attribution link, a collapsible connection log (`ConnectionLog`, last 10 statuses with durations) and a collapsible alert-history log (`AlertHistory`, last 20 fired alerts with icon/tier/datetime/location/distance/duration). Timestamps render via `formatDateTime` per the selected language. |
| `MapView.kt` | `NeptunMapView` + `DARK_TILE_SOURCE` (CartoDB dark-nolabels). OSMdroid rendering: slow-distance zone circles, type-icon markers, course rotation, dead-reckoned positions, GPS dot, city pin, scale bar, Ukraine view limits; `fitUkraineTick` zooms to the whole country. `overlayKey` covers only threat identity/status/staleness + static config (never raw lat/lon/course), so marker positions, rotation and staleness dimming update in-place via the 1s loop instead of full `overlays.clear()` rebuilds; a rebuild is deferred while a death animation is active. A TEMP map long-press fires the death animation (and self-destructs the selected threat's card) on demand. |
| `SettingsScreen.kt` | Language, map centre (pin city / follow me), Fast/Slow-grouped per-type Map/Alerts icon-chips + a per-group master row (collapsible per group, expanded by default), reference photos, threat card size, alert toggles, Fast/Slow vibration-strength sliders (0–4, in the Alerts card), updates, battery exemption, feature guide. The top "Disclaimers" card auto-expands on the first 3 Settings opens (`disclaimer_read_count`) then remembers the user's collapse state. |
| `ZonesSheet.kt` | "Edit zones" bottom sheet over the live map: Slow (km) and Fast (min) red/yellow sliders with per-zone alert bells and section captions — everything visible at once. A gear in the top-right opens Settings scrolled to the Threats section. |
| `ThreatPopupCard.kt` | Threat detail popup in two sizes (small / large): type, region, threat-level gauge, a neutral distance/ETA/speed pill trio, precision, reliability (3-segment bar in full / "R" (UA "Д") bar with the skull bar on the small card's bottom row), wave size, time since seen. The `neutralized`/`neutralizing` compact variant announces a resolved threat ("Neutralized" / "Neutralizing enemy…"). |
| `ThreatTogglePanel.kt` | Shared Fast/Slow grouping (`fastAndSlowGroups`), the Map/Alerts `ToggleChip` + `IconToggle`, and `SlimThreatToggles` — the per-type compact panel reused by the first-run dialog and Settings. |
| `FeatureGuide.kt` | Static in-app feature guide. |
| `FeatureDiagrams.kt` | Diagram drawables used by the feature guide. |
| `ThreatDeathAnimation.kt` | `ThreatDeathOverlay` — an osmdroid overlay drawing a 5s "neutralized" flourish at a threat's last position when NEPTUN resolves/removes it: a soft ping marking the target, then a small projectile flies in — from your GPS position (or pinned city) when it's on screen, else from just outside the screen edge — and explodes on impact. Explosion start is exposed as `DEATH_EXPLOSION_START_MS` so `MainScreen`'s neutralizing→neutralized card flip matches it. The threat's own marker icon keeps rendering in the overlay for the full 5s and is hidden forever the moment the animation completes. The map's projection anchors it (tracks pan/zoom); a per-frame invalidate ticker in `MapView` animates it. If the target threat is off-screen, `MapView` pans it into view once at spawn (a one-time nudge that never fights the user's pan). A TEMP map long-press (on a marker or empty ground) fires it on demand for testing. |

### Background / alerting

| File | Responsibility |
| --- | --- |
| `AlertService.kt` | Foreground `dataSync` service — the always-on monitor. Owns `NeptunClient.start()`, consumes `NeptunClient.state` + `LocationTracker.location` + prefs, and posts siren/chime/all-clear notifications with a 60s grace window and event coalescing. Official-alert bodies carry a threat reason (best `ThreatLevelModel`-scored threat in the focus oblast, else a region fallback) and are silently re-posted on the same id as reasons arrive; all-clear cancels the siren immediately when no zone alert is active. Also posts silent offline notifications (30s grace via the shared `NeptunClient.OFFLINE_GRACE_MS`, immediate when an official alert is active) with a Retry action → `NeptunClient.retryNow()`, and calls `ConnectionLog.attach()` + `AlertHistory.attach()` on start. The offline state is persisted (`offline_pending_since`) so a drop that spans a service kill is re-flagged after restart. Alert notifications carry a per-notification vibration pattern from the Fast/Slow strengths (`fast_vibration_level` / `slow_vibration_level`, threaded through `MonitorEvent.State` alongside the focus location) via the pure `vibrationPattern(level)`; `AlertHistory` is fed at each siren/chime post and close. **Independent reimplementation of the zone/focus logic.** |
| `BootReceiver.kt` | Restarts `AlertService` after reboot (`BOOT_COMPLETED`) and in-app update (`MY_PACKAGE_REPLACED`). |
| `LocationTracker.kt` | `object` singleton. Battery-cheap coarse location: `NETWORK_PROVIDER` only, ~2 min / 250 m, falls back to last known. Exposes `StateFlow<LatLng?>`. |
| `BatteryOptimization.kt` | Helpers for the "keep monitoring alive" battery-exemption flow. |

### Updates / misc

| File | Responsibility |
| --- | --- |
| `UpdateManager.kt` | `UPDATE_BASE_URL` constant, `check()` (version.json → `Available`/`UpToDate`/`Failed`), `download()` (streams APK, validates), `buildInstallIntent()` (FileProvider). |
| `UkraineTileProvider.kt` | OSMdroid tile provider that refuses to download/cache tiles outside Ukraine (+margin) — saves data/battery. |

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
    zone params (ZonePrefs)        LocationTracker.location
    LocationTracker.location       zone params (ZonePrefs)
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
   → estimateWithSource(id, t)          # server speedKmh → measured fixes → nominal (m/s)
   → predictPosition(t, speed, now)     # dead-reckon any ACTIVE track with a real heading (velocity
                                        #   bearingDeg, else top-level heading) along its course within
                                        #   per-type horizon/ghost cap; anchor = confirmedAtMillis
   → distance to focus → zoneTier (per-group: slow distance / fast ETA, shared in Zones.kt)
```

Update flow: `UpdateManager.check()` → `UpdateState.Available` → `download()` (progress) →
`buildInstallIntent()` → system installer.

## Key invariants

Treat these as a contract. If you change one, update **every** place that relies on it.

- **Two independent alert paths.** `MainViewModel` (UI state) and `AlertService`
  (notifications) each reimplement zone tiering, focus attribution, and prediction. A change
  to `zoneTier`, `ZoneParams`, `focusAttribution`, `staleAfterMs`,
  or `predictPosition` must be mirrored in **both** files or the UI and notifications drift.
  `Zones.kt` is the single source of truth for tier math — call `zoneTier`, never inline it.
  Night mode is shared, not mirrored: both sides call `isNightActive`/`effectiveZoneParams`/
  `effectiveArmed` from `NightMode.kt` and resolve the effective params/armed/overrides per
  tick (`now`), so a new night knob needs only a pref + a `NightMode.kt` change.

- **Threat type gating.** `ZonePrefs.threatMapFlow` gates which types render on the map
  (`MainViewModel`); `threatAlertFlow` gates which types fire alerts (`AlertService`). The two
  toggles are decoupled: turning a type's map visibility off no longer silences its alerts, and
  turning a type's alerts on auto-enables its map visibility (an armed alert is never hidden).
  Turning alerts off keeps the type on the map but dimmed. A type hidden from the map is
  omitted from the footer threat strip. When a type's alerts are off, its detail popup
  (`ThreatPopupCard`) shows a red crossed bell next to the type name (presentational only — no
  effect on the mirrored zone/alert logic). The same red crossed bell (`AlertsOffBell` in
  `ThreatPopupCard.kt`, drawable `ic_notifications_off`) marks muted zone bells in
  `ZonesSheet` and floats above the disarmed zone pill on the map (`MainScreen.ZoneButton`).

- **Focus point.** `followMe` → camera + zones + alerts centre on GPS; otherwise on the pinned
  city (`ZonePrefs.pinnedCity`). Pinning auto-disables follow-me. Oblast attribution goes
  through `focusAttribution` → `Cities.cityOblast` stem match (e.g. `"Харківськ"` hits
  `"Харківська область"`). Attribution resolves to **major** cities only (`Cities.nearestCity`
  skips minors) — the ~300 minor city labels are map-context and never change the banner or
  alert region.

- **Zone tiering.** Per-group model: `zoneTier(t, distKm, speedKmh, ZoneParams(slowRedKm, slowYellowKm, fastRedMin, fastYellowMin))`.
  Fast threats (`FastThreatTypes`: ballistic, cruise, aviation, KAB) tier by ETA — ETA ≤
  fastRedMin → INNER (urgent siren), ≤ fastYellowMin → OUTER (warning chime), beyond → outside
  both. Slow threats (everything else, incl. UNKNOWN) tier by plain distance — distKm ≤
  slowRedKm → INNER, ≤ slowYellowKm → OUTER. Speed comes from `ThreatSpeedTracker`
  (server → measured → nominal, m/s × 3.6); AVIATION is forced to `BALLISTIC_SPEED_KMH`
  (a MiG-31K Kinzhal is country-wide) and a fast threat with no usable speed never tiers.
  Per-type `reachKm` caps distance (KAB 70, FPV 40, recon 50, Shahed 1000, else 1500 km) —
  beyond it no alert for either group. The map's red/yellow circles show the **slow** km
  thresholds (there is no time-reference circle), so fast objects legitimately alert from
  outside the drawn circle. Advisory (NEPTUN observation) threats never tier/sound —
  map-only in the UI.
  Armed bells are **per group×tier**, not per color: slow red / slow yellow / fast red /
  fast yellow are four independent toggles (in `ZonesSheet` and in the night custom zones),
  each stored for day and night (`ZonePrefs` keys), resolved per tick by `effectiveArmed`
  into a `ZoneArmed`. `AlertService.alertTier` applies the bell for the threat's group
  (`FastThreatTypes`), so muting slow yellow no longer silences fast yellow.

- **Expiry / ghosts.** `staleAfterMs` is per-type (90s ballistic … 300s UAV). `isExpired` marks
  a threat stale; stale/expired threats stay on the map **dimmed** (marker alpha 0.25, still
  tappable; popup shows "Last seen m:ss ago" / «Востаннє m:ss тому») but are excluded from the
  threat strip, zone tiers, gauge and alerts in both `MainViewModel` and `AlertService`. A
  threat is removed entirely only when the server resolves it (or a `remove` frame arrives),
  or once `isGhost` — the staleness window plus `STALE_GHOST_CAP_MS` (~30 min) — passes. When
  the **selected** threat is gone this way (removed from the map, server-marked `resolved` /
  area-only, or a ghost), `MainViewModel` sets `UiState.neutralizedThreat` and nulls
  `selectedThreat`, so the UI swaps the popup for a compact "neutralizing" card (icon + type +
  a caption) that reads "Neutralizing enemy…" while the strike plays, flips to "Neutralized"
  at the explosion (after `DEATH_EXPLOSION_START_MS`) and fades out across it. Stale-but-trackable
  threats never trigger it.
  Dead-reckoning applies to any active threat with a real heading (velocity `bearingDeg`,
  else top-level `heading`) and caps at a per-type horizon and max-ghost distance. The ViewModel
  refreshes every 1s via `nowFlow`; `AlertService` uses a 60s grace window before clearing.

- **Place names transliterate, never translate.** Any Ukrainian proper noun shown in the EN
  UI (city, oblast, district — from NEPTUN's structured fields or a course-sentence capture)
  goes through `Cities.uaToEn` first, then `Transliteration.transliterate` — it is romanized,
  never passed to a live translator. A semantic "translation" (Золоте → "Gold") is a wrong
  result in a safety app. There is deliberately **no network translation** left in the app;
  military vocabulary (UAV, Shahed, missile, bomb, heading toward…) is hard-coded in
  `COURSE_PATTERNS` / `COURSE_GLOSSARY` / `ThreatTypeCatalog`.

- **REST never clobbers WS.** REST merge keeps the newer record per threat id
  (`updatedAtMillis` compare); a REST snapshot is CDN-cached and can be older than the stream.

- **Backup never overrides a healthy NEPTUN.** The alerts.com.ua backup (`AlertsUaClient`)
  feeds `NeptunState.oblastAlerts` only when NEPTUN is disconnected or the stream has been
  completely silent for >60s (`backupActive` — driven by `lastFrameAt`, the timestamp of the
  last frame of any type, not just the alert feed). Both `MainViewModel` and `AlertService` read the same
  union (`oblastAlerts`), so the backup needs no changes to the mirrored zone/focus logic. The
  `AlertSource` tag (NEPTUN / BACKUP / BOTH) only labels the notification body. Backup health
  (`backupUp`/`backupOfflineElapsedSec`) is surfaced read-only in the system-status popup. A
  TEMP `NeptunState.forceOffline` flag (persisted as `temp_force_offline`, set via the
  system-status popup toggle and restored on service start) sets `neptunDown` (`!connected ||
  forceOffline`) so the offline display and backup path can be tested.

- **No cloud / no push.** Monitoring is a local foreground `dataSync` service. Alerts stop when
  it stops ("Stop Monitoring & Exit"). There is no intermediate server to buffer anything.

- **Battery-first location.** `LocationTracker` uses coarse `NETWORK_PROVIDER` only
  (~2 min / 250 m) — never fine GPS. The alert tiers are minute-scale, so this is deliberate.

- **Siren channels.** Notification stream by default (respects ringer/vibrate). Only with
  `sirenOverride` do sirens use the alarm stream (sound even on vibrate/silent). All-clear
  never overrides.

## Testing

JUnit unit tests in `app/src/test/java/ua/ukrainedrones/`:

- `PredictionTest.kt` — `predictPosition`, `distanceMeters`, staleness, speed tracking.
- `CitiesTest.kt` — city-list integrity (unique names, full `cityOblast` coverage, derived EN
  names, count sanity) and majors-only `nearestCity`/`focusAttribution`.
- `ThreatTest.kt` — JSON parsing, type mapping, course translation.
- `ThreatLevelTest.kt` — threat-level scoring.
- `ZonesTest.kt` — slow km tiering, fast min tiering, `etaMinutes`, `reachKm`, AVIATION override, null-speed fast.
- `UpdateManagerTest.kt` — `versionNameGreater`.
- `NeptunClientTest.kt` — reconnect backoff (fast first attempt, exponential cap).
- `ConnectionLogTest.kt` — episode-commit rules (grace window, blips, recovery rows, ring-buffer cap).
- `AlertHistoryTest.kt` — serialize/parse round trip, malformed-line skipping, ring-buffer cap.
- `VibrationTest.kt` — `vibrationPattern` levels (off, distinct patterns, default fallback).
- `StringsFormatTest.kt` — `formatDateTime` per-language wall-clock correctness.
- `TestThreats.kt` — shared `threat(...)` builder helper.

Run: `.\gradlew.bat :app:testDebugUnitTest`

## Build & release

- `.\gradlew.bat :app:assembleDebug` — debug APK (no secrets needed).
- `.\gradlew.bat :app:release` — bumps version, builds release APK, uploads APK + generated
  `version.json` over FTP. Requires git-ignored `app/keystore.properties` (signing) and
  `app/upload.properties` (FTP creds). Release notes come from `notes_en.txt` / `notes_ua.txt`.
- Full release workflow is documented in `AGENTS.md` ("release it").
