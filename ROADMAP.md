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
