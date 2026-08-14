# Ukraine Drones

A live air-threat map for Odesa that connects straight to the
[NEPTUN](https://neptun.in.ua) public API and rings alerts when threats come close.

**No account, no server, no API key.** The app talks directly to NEPTUN's public
WebSocket, tracks your (approximate) location on-device, and fires siren/chime
notifications from its own local background service.

## At a glance

- Live threat stream from `wss://neptun.in.ua/api/v1/stream` — drones, missiles, bombs,
  and more, over an OpenStreetMap base (no Google account or key needed).
- **Two alert rings around your location**: a Red zone (urgent, full siren) and a Yellow
  zone (warning, two-tone chime). Radius is adjustable per zone.
- **Official oblast alert** shown on its own — the trident-glow indicator turns red while
  a government air-raid signal is active; controlled independently in Settings.
- Threats render full-strength when inside a zone and dimmed outside. Tap any threat for a
  detail card — type, region, level, speed, precision, reliability, last seen.
- UA/EN language switcher in the header; city labels turn red when their oblast is on alert.
- Dark-only theme, battery-cheap location, self-updates with in-app install.

## Alerts model

There are three independent alert sources, each with its own toggle:

1. **Red zone** — inner ring (default 1–5 km). Entering it triggers the urgent air-raid siren.
2. **Yellow zone** — outer ring (default 6–20 km). Entering it triggers a two-tone warning chime.
3. **Official oblast alert** — follows the government signal on its own; never mixed with the
   zone alerts.

Zone rings follow your last location fix; radii are dragged in the **Edit zones** panel
(sliders update the circles live). Each zone has its own bell toggle, plus a master bell in
the floating map controls. When both zone bells are muted, a small "All alerts are off" pill
appears.

The **"Fast objects alert sooner"** setting (on by default) fires the siren the moment a
ballistic/cruise missile, guided bomb, or MiG-31K crosses any zone edge — for those types
the travel-time difference between the rings is only seconds. Inbound fast objects also
present as red everywhere (banner, ring, marker).

Alerting keeps working while you're in another app — a foreground dataSync service monitors
in the background. The **Stop Monitoring & Exit** button in Settings ends it. Position is
deliberately **coarse-only** (one network fix, ~250 m), so the rings stay honest and the
battery stays alive; you'll see a disclaimer that positions are approximate.

## Screens & controls

**Map** — pinch to zoom, tap a zone floating button to zoom to that circle. Threats show a
type icon, live count per type, and a pulsing underline while a type is active; a footer
always shows the armed/muted state of every toggled type.

**Threat popup** — tap a marker. Shows type + icon, region + locality, a vertical 0–10
threat-level gauge (experimental blend of distance, speed, reliability, source count, raid
size, position quality and staleness), speed, distance/ETA, precision, reliability with
source count ("High · 3 sources"), wave size, and time since last seen. Tap the map to
dismiss.

**Edit zones** — the pencil button on the map opens the bottom sheet with Red/Yellow radius
sliders and per-zone bells. Non-modal: you can pan/zoom the map behind it.

**Settings** — gear icon. Language flags, per-threat-type cards (tap to enable/disable,
expand for background on the type plus a photo), official-alert toggle, "Fast objects alert
sooner", update check, and Stop Monitoring & Exit.

## What it deliberately does NOT do

- **No cloud anywhere** — no server of ours, no Firebase, no accounts, no billing. If the
  NEPTUN stream is down, the app keeps retrying silently; there's no intermediate service
  to buffer anything.
- **No precise GPS** — coarse location only, to save battery and respect your privacy
  (Settings shows "Approximate location").
- **No push infrastructure** — alerts are generated locally by the app's own foreground
  service, so they stop the moment you exit. This is a conscious zero-backend tradeoff.
- **Not an official alert system** — NEPTUN is an aggregator. Always defer to official
  sirens and government channels for actual safety decisions.

## Architecture

Built with Jetpack Compose + OSMdroid, Kotlin, coroutines + DataStore. Key files under
`app/src/main/java/ua/ukrainedrones/`:

- `NeptunClient.kt` — WebSocket client with auto-reconnect (backoff) and REST merge when the
  stream goes quiet.
- `MainViewModel.kt` — combines the threat stream, alerts and location into UI state; tracks
  the selected threat.
- `MapView.kt` — OSMdroid rendering: zone circles, type-icon markers, dimming, tap-to-select,
  scale bar.
- `MainScreen.kt` — top-level Compose UI: header (trident, flags, connection dot), alert
  banner, map, footer, navigation.
- `SettingsScreen.kt` — per-type toggles, alert settings, update check.
- `ZonesSheet.kt` — "Edit zones" bottom sheet with live radius sliders.
- `AlertService.kt` — foreground dataSync service that rings siren/chime for zone crossings.
- `LocationTracker.kt` — battery-cheap coarse GPS (network-first, ~2 min / 250 m).
- `Prediction.kt` — dead-reckoning: markers drift only while flying, using a real course.
- `Threat.kt` / `ThreatTypeCatalog` — data model, type catalog (labels/descriptions in UA+EN,
  icons), staleness windows per type.
- `ThreatLevel.kt` — the experimental 0–10 threat-level estimator.
- `ThreatImages.kt` — Wikimedia photos for the type info, with app User-Agent + icon fallback.
- `Zones.kt` / `ZoneConfig` — zone definitions and point-in-polygon helpers.
- `Cities.kt` / `Translate.kt` — city alert coloring and NEPTUN locality translation.
- `UpdateManager.kt` — daily silent version check + manual check, in-app install.
- `Strings.kt` / `ZonePrefs.kt` — UA/EN string table and DataStore-backed prefs.

## Attribution

Per NEPTUN's API terms, the app links back to [neptun.in.ua](https://neptun.in.ua/). NEPTUN
is an aggregator, not an official alert system — always defer to official siren/alert
sources for actual safety decisions.