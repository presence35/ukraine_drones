# Roadmap

Planned-but-not-started features, tracked so a future session can pick them up. Nothing here is
committed to a release.

## Multi-city official-alert monitoring (TL;DR)

Replace the single-focus alert model with a tappable watch set: a "Monitor cities" card grid in
Settings (26 MAJOR cities, tap on/off, pin auto-included). Each watched city is
checked per tick with the existing shared gates (`officialAlertActiveFor`/`coversCity`) — official
alerts only; zone tiering stays tied to GPS. Siren + city-named notification per new
region onset, all-clear chime per region end (remaining cities rewrite the notification). UI banner
gains a display-only "also in alert" line; widget's official-alert status covers focus ∪ watch set.
Core work: per-region episode latching in `AlertService` (replaces the single
`officialAnnounced*`/`officialRegionToken` state) driven by a pure, tested policy
(`domain/OfficialWatch.kt`); mirror-side computation in `MainViewModel`. Full plan with file list
was worked out 2026-08-26 (see session notes / this entry).

## Map engine: migrate raster/osmdroid → vector/MapLibre (TL;DR)

CARTO's free basemap key unblocks the current raster tiles today, but their PNG tiles are on a
recorded retirement path, and the industry direction is vector-only. Target stack:

- **Renderer**: MapLibre Native for Android. Wrapper decision deferred between
  `org.maplibre.compose:maplibre-compose` (official Compose Multiplatform wrapper, idiomatic,
  pre-1.0 API churn) vs raw SDK in `AndroidView` (stable API, more boilerplate).
- **Tiles/styles**: OpenFreeMap public instance — free, no key, unlimited views; Dark style at
  `https://tiles.openfreemap.org/styles/dark` (caveat: Dark/Fiord are unmaintained upstream
  forks; Liberty/Bright/Positron get the upkeep — may need runtime layer tweaks).
- **Sizing**: 38 osmdroid touchpoints across 10 files. Full rewrites: `MapView.kt`
  (~1200 lines), `UkraineTileProvider.kt` (obsolete — Ukraine-only tile blocking becomes hard
  camera bounds). Rework: `flourish/` death animations + `Cities.kt` label overlay draw
  directly onto osmdroid canvases. Decouple: `GeoPoint` leaks into `domain/` (`Prediction`,
  `ThreatEvaluator`, `Cities`) — replace with a local lat/lon value class.
- **Gotchas**: osmdroid `TileWriter` cache disappears (MapLibre manages its own); zone circles,
  threat markers and shelter pins become GeoJSON layers or annotation plugins; attribution
  string changes to OpenMapTiles/OSM/OpenFreeMap.

Worked out 2026-08-26. Ship behind nothing — it replaces the map wholesale; do it as its own
focused task with device testing of every visual surface.
