# Architecture — Ukraine Drones

Technical map of the codebase. Read this before exploring so you can jump straight to the
file(s) you need instead of re-deriving the structure. Keep it current: if you add a file or
change a documented invariant, update the relevant section.

## Quick facts

- Single-module Android app (`:app`) — a live air-threat map for Ukraine.
- Jetpack Compose (Material 3, dark-only) + OSMdroid. Kotlin 1.9.24, JDK 17, minSdk 26 /
  targetSdk 34, namespace `ua.ukrainedrones`.
- No runtime backend of ours: data comes straight from the public
  [NEPTUN](https://neptun.in.ua) API (WS stream + REST merge). No Firebase, no push.
- Update feed: static `version.json` + APK on `odesaplay.com.ua`, self-checked daily, in-app install.
- Coroutines + flows throughout; singletons expose `StateFlow`s.

## Package structure

Source files are grouped into subdirectories by subsystem (`data/ domain/ service/ ui/
widget/`) while keeping a **single flat package** `ua.ukrainedrones` — so any file can reach
any other without import ceremony. The subdirs are organizational only; don't add package
qualifiers. Migrate to a true multi-package layout only if it ever outgrows maintainability.

## System overview

The UI and the background service are **independent consumers of shared inputs** — both
re-derive state from the same singletons, never from each other.

```
                    ┌──────────────────────┐
 NEPTUN WS ────────►│                      │
 NEPTUN REST ──────►│     NeptunClient     │
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
| Official oblast alerts | `NeptunClient.oblastAlerts` | UI, `AlertService`, widget |
| Threat prediction | `Prediction.kt` | ViewModel, service |
| Zone tier math | `Zones.kt` | ViewModel, service |
| Night rule resolution | `NightMode.kt` | ViewModel, service |
| User preferences | `ZonePrefs` | all |
| UI orchestration | `MainViewModel` | Compose |
| Background monitoring | `AlertService` | notifications |
| Connection history | `ConnectionLog` | system status, Logs screen |
| Decision audit | `DebugLog` | Logs screen |
| Map rendering | `MapView.kt` | Compose |
| Threat icons | `IconCatalog.kt` | UI |

## Data flow

- **Threat ingest.** NEPTUN WS + REST merge in `NeptunClient` → `StateFlow<NeptunState>`;
  the `oblastAlerts` list is what UI/notifications read. One singleton, consumed
  independently by both paths.
- **Position prediction** (both consumers):
  `ThreatSpeedTracker.record(...)` → `estimateWithSource(id, t)` (server → measured → nominal)
  → `predictPosition(t, speed, now)` (dead-reckon **only tracks with a real velocity** —
  `bearingDeg` + `speedKmh`, matching NEPTUN's `flying`; everything else holds the raw fix —
  capped at the per-type horizon/ghost) → distance to focus → `zoneTier` (slow:
  distance-to-confirmed-fix, fast: predicted-ETA). Icon facing shares `motionHeading`, which
  prefers the server's authoritative `bearingDeg` over our measured track.
- **Update flow.** `UpdateManager.check()` → `Available` → `download()` (progress) →
  `buildInstallIntent()` → system installer. `AlertService` also checks silently every day at
  16:20 while it runs and posts one "new version available" notification per new build
  (deduped by `last_notified_update_code`); tapping it re-opens the app and pops the update dialog.

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
| `NeptunClient.kt` | `object` singleton. NEPTUN WebSocket (fast-first backoff 1–3s → capped 15s via `reconnectDelayMs`, keep-alive/watchdog) + REST merge → `StateFlow<NeptunState>`; `removedThreats` SharedFlow; `retryNow()`; hosts `OFFLINE_GRACE_MS` (5 s — sub-grace drops are ignored by the pill and the connection log; deliberate) and `USER_SHOT_GRACE_MS` (3 s). `markUserShot(id)` records map long-press fake kills in `NeptunState.userShotAt`; the snapshot handler keeps those ids alive in memory through the grace window so a quick same-id respawn redraws in place instead of re-entering as a new threat. 5s watchdog → `ConnectionLog`. Pure `reconnectDelayMs` (tested). |
| `Threat.kt` | `Threat` model + JSON parsing; `ThreatTypeCatalog` (labels, staleness, nominal speeds); `OblastAlert`/`inOblast`/`coversCity` + shared `officialAlertActiveFor` scope gate; `translateCourseAssessment` (EN course text, word-level common-word translation); `courseDeg` facing resolution (shares `motionHeading` with `predictPosition`). |

### State / orchestration

| File | Responsibility |
| --- | --- |
| `MainViewModel.kt` | `AndroidViewModel`. Combines NEPTUN + GPS + prefs + 1s clock (flourish data/policy live in `flourish/Flourish.kt` — `FlourishRecord`/`FlourishShow` and the `FlourishPolicy` gates used by `buildUiState`) → `StateFlow<UiState>`; drives the update flow (daily start check, a Settings-open check that raises `updateReminderTick` — a snackbar with a Download action — instead of a clickable toast, which Android can't make touchable, plus manual check/download/install); UI-side copy of zone/focus/alert logic (tradeoffs); `neutralizeThreat` long-press hook. The neutralizing-card swap fires only while the map screen is visible (`mapVisible`, set from the visible screen) and the shelter overlay is not up (`shelterModeActive`, set via `setShelterModeActive`) — nothing should steal focus from the shelters; it does play during an alert because the threat was already on screen. `neptunDown` ignores sub-grace drops. *Note:* the map's `focusLocation` falls back to Odesa before the first GPS fix so the first visual is complete — this is **UI-only**; `focusAttribution`/alerts still use the real fix or pin. A `flourish` request (tally-tap replay, from `AlertService`) flows to `MapView`. |
| `ConnectionLog.kt` | `object` singleton. Persisted ring buffer (last 10 episodes) fed by the watchdog; commits drops only past `OFFLINE_GRACE_MS`. `ConnStatus` = ONLINE/OFFLINE (no backup state). Pure `commitLogState` (tested); rendered in the Logs screen. |
| `DebugLog.kt` | `object` singleton. Persisted audit trail (last 500 decisions, rolling 24h window) written by `AlertService`, read by the Logs screen. Records every alert/threat decision in the active region — official on/off, zone entries, region threats — with day/night and effective sound, whether a notification was shown and why not. `DebugLog.sweep` runs once per service tick and is **read-only for the decision path**: it describes the service's own computed maps (`zoneThreats`/`alertable`/`knownZones`/`postedId`), never re-derives formulas. Pure `computeSweep`/serialize/parse (tested). *Note:* the whole feature is additive — removing it is deleting the write hooks + this object + `DebugLogScreen`. |
| `Prediction.kt` | `LatLng`, `distanceMeters`, per-type `staleAfterMs`/`isExpired`, `predictPosition` dead-reckoning (only `flying` tracks with real velocity glide), `motionHeading` (server bearing, then heading, then measured fix-track — shared by motion + facing), `ThreatSpeedTracker` (shared thread-safe singleton so every consumer measures the same speed/track). |
| `Shelters.kt` | Odesa shelter dataset: `Shelter`/`NearestShelter` (adult ~5 km/h, kid ~3 km/h walk minutes), `ShelterIndex` (JSON parse, Odesa bbox, nearest ranking). |

### Domain logic

| File | Responsibility |
| --- | --- |
| `Zones.kt` | `ThreatZone`, `ZoneParams`, `FastThreatTypes`, `zoneTier(...)` — the single source of truth for tiering — plus `etaMinutes`, `reachKm`, `BALLISTIC_SPEED_KMH`. |
| `NightMode.kt` | Shared night helpers for **both** consumers (mirror rule): `isNightActive`, `effectiveZoneParams`/`effectiveArmed`, `NightConfig`/`NightZones`/`ZoneArmed`. |
| `ThreatLevel.kt` | `ThreatLevelModel` — experimental 0–10 gauge (severity × distance × reliability × sources × count × quality × staleness × ETA). |
| `Cities.kt` | ~483 places grouped by oblast in three zoom tiers (`CityTier`: 26 MAJOR always / 14 MEDIUM from mid-zoom / rest MINOR up close; non-curated places derived from GeoNames CC BY 4.0 via `tools/gen_cities.ps1`, 2 km dedupe, same-name towns resolved by population) + `CityLabelOverlay` (colors labels red for the `redCities` set — scope-aware: whole oblast by default, city-level when the City scope is on); EN names from the app's own КМУ №55 transliteration; `focusAttribution` maps focus point → oblast stem via `cityOblast` (majors only). |
| `Transliteration.kt` | Official КМУ №55 Ukrainian→Latin romanization (the EN gate). |
| `ZonePrefs.kt` | `AppLanguage`/`ThreatCardSize`/`ThreatIconSet` + DataStore store (`zone_prefs`): all toggles/thresholds/language/follow/pin/visibility, night config, and — problematically — serialized `ConnectionLog`, offline-restore state, onboarding flags. Also `threatMapFlow`/`threatAlertFlow`; the resolved-threat tally's focus-oblast default + "All of Ukraine" opt-in (`neutralized_tally_all_ukraine`), and the daily-update notify marker (`last_notified_update_code`). *Note:* god object mixing prefs with persisted state — split candidate (tradeoffs). |
| `Strings.kt` | UA/EN `StringSet` table (never Android resource localization); `formatRelativeTime`, `formatDateTime` (app language, not device locale). |
| `WidgetSnapshot.kt` | `WidgetSnapshot` + pure `computeWidgetSnapshot(...)` — deterministic projection of threat state for the widget, from shared domain functions only (`ThreatEvaluator.evaluate`, `distanceMeters`, `focusAttribution`, `inOblast`). Counts + per-type `typeCounts` mirror the footer-strip semantics; `primaryThreat` = nearest live threat (id + position) so the widget can reveal it; `sourceOnline` is grace-filtered like the app pill. Never re-derives zone/tier logic (mirror rule). Tested by `WidgetSnapshotTest`. |
| `IconCatalog.kt` | Single source for threat icons: vector/photo/army/comic/russian sets, per-set facing (`baseDeg`), `ThreatIcon` composable; assets in `app/src/main/iconpacks/`. |
| `Toasts.kt` | Shared toast helper: one function decides placement — top (below the header banner, via `ToastHost(topInset)`) normally, bottom (above the floating zone/shelter buttons) when a card/popup is visible. Dark themed pill. Callers never hardcode gravity. |

### UI (Compose)

| File | Responsibility |
| --- | --- |
| `MainScreen.kt` | Top-level Compose UI: header, alert banner, map, threat strip, `ZonesSheet`, `UpdateDialog`, first-run wizard + battery prompt. *Note:* wizard gated on `!languageChosen` and force-dismissed during an active alert; card flip timed to `DEATH_EXPLOSION_START_MS`; popup height feeds the map as `popupCoverPx`. The wizard is now **4 pages** (language+tips → threat care → zone-controls illustration → core features) with a blue/yellow segment progress indicator; the tally-tap replay flourish closes every modal and forces the map screen. |
| `ConnectionStatus.kt` | Connection pill (online/offline) + system-status dialog: per-source dot, TEMP force-offline toggle, legend, attribution link, and a prominent "Logs" button (in the dialog header) opening the Logs screen. |
| `Haptics.kt` | Global press-haptics: `LocalHapticsEnabled` CompositionLocal (provided from the `hapticsEnabled` pref at the MainScreen root) + `Modifier.pressTick()` — a passive `awaitEachGesture` listener that vibrates on press-down without consuming the gesture. Uses the raw `Vibrator` with a short one-shot at full amplitude (`USAGE_ALARM` on API 30+ — the same always-on channel as the shoot-down flourish) because Compose's haptic API is muted when system touch feedback is off and the predefined `EFFECT_TICK` is a silent no-op on many OEMs. Applied across map controls, settings rows, and popup cards. |
| `LogsScreen.kt` | Full-screen Logs: one card list over decisions (the audit trail) and connection episodes, switched by chips (Decisions / Connections). The Decisions tab offers group-by (Timeline / Proximity = official, red zone, yellow zone, in-oblast, left / Type), a standard sort-direction icon toggle (newest/oldest) that applies within every grouping, and a "shown only" switch (only rows where a notification was actually shown); controls stay visible even when the list is empty so the shown-only switch can be flipped back. A double-arrow reveals more rows; a leading per-threat-type icon on threat rows (red trident = official on, green check = all-clear), an "ago" + absolute timestamp, day/night + effective sound, "Notification shown" or "No notification — \<reason\>", Clear button. |
| `MapView.kt` | `NeptunMapView` + `DARK_TILE_SOURCE`. Owns OSMdroid rendering: zone circles, marker overlays, course rotation, dead-reckoned positions; the shoot-down visuals/camera/haptics/replay delegate to `flourish/DeathFxController` (the death-anim collector is subscription-gated on `deathAnimationEnabled`, and a functional grey lost-dot collector runs regardless). Threat icons are a **fixed size at every zoom** (no zoom scaling); long-pressing a threat shoots it down for fun: `markUserShot` + death animation, the marker stays hidden while the animation plays and the rebuild redraws it afterwards (the object itself is never removed); the strike camera (`followStrike`) glides onto the target and — since the camera-return rework — pans back to where the user was after the explosion, with a fresh strike replacing any pending return. Long-press is disabled while an alert is active. The reveal marker is a small green **dot baked into the threat icon's top-right corner** (a single tappable marker — no separate overlay intercepting the tap) and a grey "lost" dot marks where a threat just vanished. Selecting a threat opens its popup without moving the camera. Shelter markers are hand-drawn teardrop pins (stroke-only, per-type color, white when selected) anchored tip-on-spot; tapping a shelter opens its card without moving the camera (only the shelter-list button zooms). The GPS dot is gray while "locating" (before the first fix) and blue once a fix exists. *Note:* shelter-mode zoom + deep-zoom unlock run in a dedicated `LaunchedEffect` (not the recompose-driven update block) so the fit fires reliably after the shelter markers are placed; normal zoom is capped at `NORMAL_MAX_ZOOM` (14.5, the ~5 km viewport) and only raised to `SHELTER_MAX_ZOOM` (19) while the shelter overlay is up — keeping the tile cache to what the threat map needs; zooming below `SHELTER_AUTO_EXIT_ZOOM` (13) while shelter mode is up auto-exits it. The resolved-threat death flourish (`deathFx`) is skipped while the shelter overlay is visible (`showNearbySheltersState`) and, during an alert, only when the resolution is off-screen — in-camera resolutions still play. `overlayKey` excludes raw positions so live movement updates in-place; must not perform alert decisions. The tally-tap replay flourish groups the remembered resolutions by viewport-adaptive proximity (`clusterFlourish`, threshold approx 1/3 of the current viewport width), zooms onto each group in turn, fires its bullets, then returns home; each bullet vibrates short-on-shot / longer-on-detonation, and `deathFx.active` is surfaced via `onDeathActiveChange` so the footer can swap its copy. the reveal framing always pins the revealed threat to the lower viewport so it never sits under the top popup card. |
| `SettingsScreen.kt` | Collapsible sections: language, map centre, per-type Map/Alerts toggles + icon packs, card size, alert toggles, the **Just Fun** section (last card: icon packs, calm messages, shoot-down animation + follow-the-bullet, neutralized count + all-Ukraine), night-mode card, updates, battery exemption, guide; one-time explainers. The night-mode section tints its whole collapsible card darker purple + border (`NightSectionBg`/`NightSectionBorder`), no moon icon on the enabled toggle; battery exemption lives in the Alerts section; shelter button toggle + shelter directory row live in their own dedicated **Shelter** section; sections stay user-collapsible even while searching; a Reset-tips row re-arms every first-use hint (toast counters + explainers); icon packs ship PHOTO/ARMY/COMIC/RUSSIAN only (CLASSIC removed — a stored `"CLASSIC"` pref falls back to PHOTO, classic vectors remain the internal pack-fallback); the card-size tiles preview the small card as its real compact top-left chip (~75% of tile width) and the large card full-width. |
| `ZonesSheet.kt` | "Edit zones" sheet: Slow (km)/Fast (min) sliders + per-zone bells; edits day or night values depending on the active window; night rows carry day reference ticks. |
| `ThreatPopupCard.kt` | Threat popup (small chip / large card); `AlertsOffChip` when type alerts are off; neutralized/neutralizing variant. The ETA pill's blue GPS dot mirrors the map location dot (same core + white ring, subtler radial glow). |
| `ThreatTogglePanel.kt` | Shared Fast/Slow grouping, `ToggleChip`/`IconToggle`, `SlimThreatToggles` (reused by first-run dialog + Settings). |
| `FeatureExplainer.kt` | One-time explainer popups keyed by setting id; seen state via `ZonePrefs.explainerSeen`. |
| `FeatureGuide.kt` / `FeatureDiagrams.kt` | Static feature guide + its diagram drawables. |
| `ShelterScreen.kt` | "Go to shelter" list: nearest Odesa shelters ranked by distance to the focus point, adult/kid walk times (kid row only when the "With kids" setting is on), a GPS-age header with a force precise-fix button (re-prompts location permission), transliterated names in EN; "open in maps" (`geo:` intent); the map button is a red-filled (official alert) or ghost-outlined pill in `MainScreen.kt`. |
### Flourish (isolated death + tally subsystem)

| File | Responsibility |
| --- | --- |
| `flourish/Flourish.kt` | Pure flourish core: `FlourishRecord`/`FlourishShow`/`ReplayProgress` (per-group copy + overall position for the footer bar); `FLOURISH_STAGGER_MS` + `REVEAL_MIN_SPAN_*`; `clusterFlourish`/`flourishesBoundingBox` (viewport-adaptive replay grouping); `FlourishPolicy` (the neutralized-card gate, pure + tested). |
| `flourish/DeathFxController.kt` | Map-side facade: owns `ThreatDeathOverlay` + strike camera glide/return, shot/kill haptics, a random viewport-edge take-off origin (clamped to Ukraine, so a projectile never launches from "another country") and the tally-tap replay orchestration (exposes `replayProgress: ReplayProgress?` for the footer's per-group "Resolving threat X of N" + overall progress bar; `startReplay` owns the replay job so `clear()` cancels a show mid-flight). Camera notes: `getMapCenter()` is snapshotted into a new GeoPoint everywhere (osmdroid hands back its live mutable projection point — holding it made "return home" land randomly); replay jumps per group via `zoomToBoundingBox(box, false)` + a settle beat (bullets must never fly while the camera glides). Pacing: intermediate groups spawn `quickBoom` deaths (impact + ~0.8s flash) and pan ~300ms after the last impact; only the final group plays the full 5s lifecycle. The tally-tap tick is consumed only on a real decision — transient blockers (cold start, Settings, shelters) retry; animation-off toasts + audits (`DebugLog.recordFlourish`, detail localized via the `showDetail` lambda); a live official alert does NOT block the replay (explicit user action) though a NEW alert onset mid-show still ejects it via `clear()`. `MapView` keeps only thin policy hooks and delegates every flourish mechanic here. |
| `flourish/NeutralizedTally.kt` | Service-side facade: tally count + 21-record resolution memory + the silent tally notification (tap replays the show, swipe resets), with a recent-ids ring deduping NEPTUN's re-sent removals so duplicates never double-count nor plant twin replay records. Owns `CHANNEL_NEUTRALIZED`/`NOTIF_NEUTRALIZED`/`EXTRA_FLOURISH_*`/`ACTION_NEUTRALIZED_DISMISS`. `AlertService` keeps only the enabled-pref subscription gate + focus-scope filter. |
| `flourish/ThreatDeathAnimation.kt` | `ThreatDeathOverlay`: 5s neutralized flourish (projectile enters from just off the screen edge along a random edge-clamped origin -> explosion). Per-death `durationMs`: `quickBoom` deaths (intermediate replay groups) compress the explosion to impact + ~0.8s flash; boom/fade curves and pruning derive from each death's own duration. Perf practices: explosion glow is a lazily pre-rendered per-density bitmap (no per-frame `RadialGradient` allocation), icons are cached bitmaps with fresh drawable wrappers (the per-frame alpha mutation must never touch a live marker's icon), concurrent deaths capped at `MAX_DEATHS = 14` (sized for a full replay, the old 6 silently ate bullets), and the map redraws at 30fps while active (16ms -> 33ms; invalidate redraws the whole overlay stack). `DEATH_EXPLOSION_START_MS` drives the card flip; dud on duplicate resolutions; `isActiveFor(id)` guards double-strikes; the target icon vanishes at the explosion (no fade); `active` StateFlow tells the UI when a bullet/explosion is on screen. |

### Background / alerting

| File | Responsibility |
| --- | --- |
| `AlertService.kt` | Foreground service; the shoot-down tally (count/memory/notification) is delegated to `flourish/NeutralizedTally` — the service keeps only the enabled-pref subscription gate (`flatMapLatest`) + focus-scope filter. (`specialUse` on API 34+, `dataSync` on API 29–33 — Android 15 caps `dataSync` FGS at 6h per 24h in the background, which would stop a 24/7 monitor) — the always-on monitor. Owns background monitoring and the notification lifecycle: siren/chime/all-clear (20s zone grace, coalescing), the always-visible monitor notification switching to offline wording + Retry on a drop (no separate one-shot offline alert), resolved-threat tally, per-notification vibration, `DebugLog` feed. The resolved-threat tally counts by **focus oblast** by default (GPS-follow or pinned city's oblast, matched via the shared `ThreatEvaluator.inOblast`); an "All of Ukraine" opt-in (`neutralized_tally_all_ukraine`) lifts it to any resolution country-wide. Tapping the tally notification opens `MainActivity` directly with the last **21** remembered resolutions (position + type) baked into the tap for the map's replay flourish; a red alert (official or INNER zone) erases that memory. Zone-alert dedup: ids drop from `knownZones` only when they leave the zones *and* their `userShotAt` grace (3 s) has passed, so a same-id respawn of a shot-down drone never re-alerts. *Note:* the official-alert all-clear is region-latched: it fires only while the focus is still on the region whose alert was ringing (`state.focusToken == officialRegionToken`); switching the focus away to a non-alerting region silently drops tracking (no false all-clear, no lingering siren), and returning to the still-alerting region re-announces fresh. The offline drop is persisted (`offline_pending_since`) so it re-flags after a service kill; evaluates via the shared domain functions, never local formulas. Reconnect milestone flags reset **and** the milestone notification is cancelled on the reconnect transition; official-alert notifications track their own notified-state (turning the toggle back on mid-alert re-announces); `ConnectionLog`/`DebugLog` restore is awaited before `NeptunClient.start()`. Notification taps that carry a threat use their own `PendingIntent` request code (1) so the reveal extras can't be clobbered by the plain status/tally/milestone intents (0); the ongoing status title reads "Monitoring GPS" when following and "Monitoring \<city\>" when pinned, and it switches to the offline wording with a Retry action once a drop outlasts the shared grace. A daily 16:20 coroutine checks `UpdateManager` silently and posts one silent "new version available" notification per new build (`last_notified_update_code` pref, `NOTIF_UPDATE`); its tap carries `EXTRA_SHOW_UPDATE` with its own `PendingIntent` request code (4). |
| `BootReceiver.kt` | Restarts `AlertService` on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`. |
| `NeutralizedDismissReceiver.kt` | Delete intent for the tally notification (resets the count). |
| `LocationTracker.kt` | `object` singleton. Coarse `NETWORK_PROVIDER` only (~2 min / 250 m), falls back to last known -> `StateFlow<LatLng?>`; tracks the last fix time (`lastFixAtMs`) and offers a `forceRefresh()` GPS one-shot for the shelter screen. A 15-min periodic GPS sync loop runs by default (pref `periodic_gps_enabled`, default on) so Android sees real GPS access and cell drift is corrected. || `BatteryOptimization.kt` | Battery-exemption helpers. |

### Widget

| File | Responsibility |
| --- | --- |
| `widget/ThreatWidget.kt` | Glance home-screen widget (`provideGlance` + `provideContent`, `SizeMode.Responsive`). Passive renderer of the persisted [WidgetSnapshot] - never evaluates zones/tiers itself (mirror rule). Three density buckets (compact 2x1 / standard 4x2 / detailed 4x3) picked from `LocalSize`; dark-only palette; tap opens the app. The primary threat icon (the nearest threat) is its own tap that reveals that threat on the map via the same reveal extras as a notification tap; the status badge mirrors the app-grace-filtered online/offline pill. `ThreatWidgetReceiver` is the manifest-declared `GlanceAppWidgetReceiver`. |
| `widget/WidgetUpdater.kt` | `object` singleton. Started by `AlertService` (the already-running monitor, ~zero marginal battery). Combines `NeptunClient.state` + `LocationTracker.location` + zone/follow/pin/language/type-gate prefs + a 30s clock -> `computeWidgetSnapshot` -> persists to the `widget_snapshot` DataStore and calls `updateAll()` only when a widget is actually placed. Persists `primaryThreat` (id/lat/lon/type) so the widget can reveal the nearest threat. Exposes `readSnapshot`/`readLang`/`readIconSet` 
for the widget (icon set mirrors the user's `threatIconSet` pref, so the widget uses `IconCatalog.res(type, set)` 
like the rest of the app). |

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
  **both** files or UI and notifications drift. Official-alert **scope** (oblast/city) is shared:
  both call the single `officialAlertActiveFor(...)` gate in `Threat.kt`, and the map's red city
  labels (`redCities`) follow the same gate, so a city-scoped user sees only covered cities lit. Call `zoneTier` from
  `Zones.kt` — never inline
  it. (Why/mitigation: Deliberate tradeoffs.)
- **Official alert announces once per episode, surviving service restarts.** `AlertService`
  persists the announced episode identity (focus token + NEPTUN `since` + reason threat id) to
  `ZonePrefs` (`officialAnnounced*`), loaded inside `startMonitoring()` before the first tick
  (no startup race), and reconciles it after a START_STICKY restart, so an alert already fired
  before a service kill never re-rings. The persisted identity is cleared only when the episode
  genuinely ends (all-clear / focus switch / alert gone). Re-enabling the official-alerts toggle
  does NOT re-announce a live alert that already rang (an alert that started while muted
  announces naturally). Silent reason refreshes only re-post while `NOTIF_ALERT` is still
  showing (`alertNotificationShowing()`) — a tapped/swiped alert is never resurrected as a new
  siren by a same-episode reason update. When NEPTUN omits `since`, dedup falls back to the old
  in-memory behavior (may re-ring once after a restart).
- **Night mode is shared, not mirrored.** Both sides call `isNightActive`/
  `effectiveZoneParams`/`effectiveArmed` resolved per tick (`now`); a new
  night knob needs only a pref + a `NightMode.kt` change. Vibration is fixed, not per-night.

- **Widgets read snapshots, never evaluate.** The home-screen widget is a passive renderer of
  `WidgetSnapshot`, computed solely by `WidgetUpdater` via `computeWidgetSnapshot` — which calls
  the shared domain functions (`ThreatEvaluator.evaluate`, `distanceMeters`, `focusAttribution`,
  `inOblast`). A change to zone/tier/prediction logic must **not** be reimplemented in the widget
  layer; update `computeWidgetSnapshot` instead. Counts mirror footer-strip semantics.

- **Threat type gating.** `threatMapFlow` gates map rendering, `threatAlertFlow` gates alerts —
  decoupled toggles: map-off doesn't silence alerts; alerts-on auto-enables map visibility (an
  armed alert is never hidden). A type hidden from the map is dropped from the map and the
  footer strip; a type with alerts off stays fully mapped (never dimmed — dimming is
  staleness-only) but is omitted from the footer strip, with a red crossed bell on its popup.

- **Focus point.** `followMe` → camera + zones + alerts centre on GPS, else the pinned city;
  pinning disables follow-me. Attribution via `focusAttribution` → `cityOblast` stem match,
  **major cities only** — the ~300 minors are map-context, never banner/alert. *First-launch
  visual:* before the first GPS fix the **map's** `focusLocation` falls back to Odesa (complete
  first screen) — this is UI-only; `AlertService`/`focusAttribution` never use the fallback, so
  no fake region alert is ever produced.

- **Zone tiering.** `zoneTier(t, distKm, speedKmh, ZoneParams(slowRedKm, slowYellowKm,
  fastRedMin, fastYellowMin))`. Fast types (`FastThreatTypes`) tier by ETA (≤ fastRedMin →
  INNER, ≤ fastYellowMin → OUTER); slow types by distance (≤ slowRedKm → INNER, ≤ slowYellowKm
  → OUTER). Slow distance is to the **confirmed raw fix**, never the dead-reckoned position, so
  the drawn circles and alerts always agree. Speed from `ThreatSpeedTracker` (server → measured
  → nominal); AVIATION forced to `BALLISTIC_SPEED_KMH`; a fast threat with no usable speed never
  tiers. `reachKm` caps distance (KAB 70, FPV 40, recon 50, Shahed 1000, else 1500 km). Map
  circles show the slow km thresholds only. Advisory (observation) threats never tier/sound.
  Armed bells are per group×tier (slow/fast × red/yellow), stored for day and night, resolved
  per tick by `effectiveArmed`. **Slider coupling:** the yellow threshold is relative to red —
  setters clamp yellow to `red+2 … max` (slow 1–20 km red / yellow ≤50; fast 1–5 min red /
  yellow ≤20) in `ZonePrefs`; the UI sliders derive the yellow range from the red value live.

- **Expiry / ghosts.** `staleAfterMs` per type (90s ballistic … 300s UAV). Stale threats stay
  mapped **dimmed** (alpha 0.45, tappable) but are excluded from strip, tiers, gauge, alerts.
  Removal only on server resolve / `remove` frame / `isGhost` (staleness + `STALE_GHOST_CAP_MS`
  ~30 min). When the **selected** threat disappears that way, `MainViewModel` swaps the popup
  for the neutralizing card (flips at `DEATH_EXPLOSION_START_MS`, fades across the explosion) —
  but only while the map screen is visible and the shelter overlay is down (a background screen
  never plays the flourish); it *does* play during an alert because the selected threat was
  already on screen;
  with `deathAnimationEnabled` off nothing animates. Dead-reckoning applies only to tracks with
  a real velocity (matching NEPTUN's `flying`), capped per type. ViewModel ticks 1s (`nowFlow`);
  service clears on a 20s grace.

- **Threat facing always matches its motion.** `predictPosition` (dead-reckoning) and
  `Threat.courseDeg` (icon rotation) both resolve the heading via the shared `motionHeading`
  (`Prediction.kt`): the server's authoritative velocity `bearingDeg` first, then the top-level
  `heading`, then our measured fix-track (`ThreatSpeedTracker.measuredHeading`). Dead-reckoning
  only glides tracks with a real velocity (`bearingDeg` + `speedKmh`, i.e. `Threat.flying`) —
  matching NEPTUN; everything else holds the raw fix. `courseDeg` keeps the extra
  `courseFromMessage()`/`fallbackCourse(id)` fallbacks for *stationary* "heading toward X"
  threats that don't glide. A change to heading resolution must stay in `motionHeading` so the
  marker never faces a direction it doesn't move.

- **Place names transliterate, never translate.** Any Ukrainian proper noun in the EN UI →
  `Cities.uaToEn` → `Transliteration.transliterate`; military vocabulary is hard-coded
  (`COURSE_PATTERNS`/`COURSE_GLOSSARY`/`ThreatTypeCatalog`). No network translation.

- **REST never clobbers WS.** REST merge keeps the newer record per threat id
  (`updatedAtMillis`); a REST snapshot can be CDN-stale.

- **Official alerts come from NEPTUN only** (the alerts.com.ua backup was removed — a dead
  project). `NeptunState.oblastAlerts` is NEPTUN's list, held (never cleared) while the socket
  is down: an outage must not fabricate "alert ended" / false all-clear — the truth arrives on
  reconnect. The scope is either oblast-wide (default) or city-level via the shared
  `officialAlertActiveFor(alerts, token, cityUa, scope)` gate (`OblastAlert.coversCity`), used
  by **both** `MainViewModel` and `AlertService` (mirror rule). TEMP `forceOffline` flag
  (`temp_force_offline`, restored on service start) forces the offline path for testing;
  turning it off while the socket is down kicks a real reconnect (`retryNow`).

- **Official alert is region-latched, never focus-bound for its end.** The official-alert
  all-clear (both notification and UI banner) must fire only for the region whose alert was
  ringing. Switching the focus away (pin → GPS, or to another city) to a non-alerting region
  must **not** announce an all-clear for the old region nor keep its banner — it silently drops
  the active-alert tracking, and returning to a still-alerting region re-announces fresh.
  Implemented in `AlertService` via `officialRegionToken`; the UI banner (`MainViewModel`)
  always reflects the *current* focus region only.

- **No cloud / no push.** Monitoring is a local foreground service (`specialUse` on API 34+,
  `dataSync` below; the manifest declares both types plus
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "continuous air-raid alert monitoring for user safety"`
  for Play review); alerts stop when it stops. No intermediate server buffers anything.

- **Battery-first location.** Continuous tracking is coarse `NETWORK_PROVIDER` only (~2 min /
  250 m), never fine GPS. A 15-min periodic GPS sync one-shot runs by default (`periodic_gps_enabled`,
  default on) to correct cell-tower drift and give Android real location access; `forceRefresh()` is
  the on-demand precise one-shot for calibration/shelters.

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

NEPTUN is consumed directly, parsing isolated in `Threat.kt`. Risk: upstream schema/contract changes. The REST/WS merge protects against
CDN-cached staleness.

### `ZonePrefs` is becoming a god object

It owns every preference plus serialized `ConnectionLog`, offline-restore state
and onboarding flags. Split candidate:

```
AppPrefs
├── MapPrefs / ThreatPrefs / AlertPrefs / NightPrefs / UiPrefs / SystemPrefs
```

with persisted operational state moved out:

```
ConnectionLogStore
```

The doc distinguishes **preferences** from **persisted application state**.

## Failure modes

| Failure | Behavior | Owner |
| --- | --- | --- |
| NEPTUN offline | Offline pill + monitor notification with Retry; `retryNow()`; last-known oblast alerts held; drop persisted (`offline_pending_since`) across restarts. | `NeptunClient`, `AlertService` |
| Stale threats | Dimmed on map (alpha 0.45); excluded from tiers/alerts/strip/gauge; ghosts removed after ~30 min. | `Prediction.kt` + consumers |
| Service process interrupted | Recovery depends on Android's foreground-service lifecycle; connection log + debug log restored from DataStore when restarted. | `AlertService`, DataStore |
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
- `DebugLogTest.kt` — serialize/parse round trip, ring-buffer cap, auto-clear, `computeSweep` verdicts (fired/coalesced/bell-muted, steady-state dedup, tier escalation, exits, stale/type-off region rows).
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
