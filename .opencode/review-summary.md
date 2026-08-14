# Ukraine Drones — Review Summary

## Mode: Bug Hunter (customized: precision/reliability; security de-prioritized) + light Future Maintainer
### Findings
- [Critical] AlertService.kt:168-182,227 — Stale threats are never re-evaluated: `zoneThreats` only computed inside a `distinctUntilChanged` State flow; the 60s `Tick` only ran `handleGraceTick`. A threat ages into staleness only when the stream re-emits; if the WebSocket goes quiet, the siren keeps ringing while the UI (1s `nowFlow`) already dropped it.
- [Critical] Threat.kt:52-55 — `isExpired` returns `false` for any threat with null `updatedAtMillis` (immortal threats).
- [High] MainViewModel.kt:208 / AlertService.kt:187 — GPS-follow hardcodes `"Одеськ"` for the official-alert focus regardless of actual location; banner/notification always Odesa.
- [High] NeptunClient.kt:188-206 — `scheduleReconnect` spawns raw `Thread`s; stacked failures + stop/start toggle of `manuallyStopped` can open duplicate WebSockets (no single-flight guard).
- [Medium] MainViewModel.kt:78-83 — 6 `runBlocking { prefs…first() }` on the main thread in ViewModel init.
- [Medium] MainViewModel.kt:124-173 / AlertService.kt:123-167 — index-based `combine` casting (`values[16]`) is reorder-fragile.
- [Medium] Prediction.kt:88-123 / MainViewModel.kt:257 — measured speed uses `updatedAt` time base while prediction anchors `confirmedAt`; 4-fix queue + 5s min-dt rejects fast-cadence streams (falls back to nominal).
- [Medium] MainViewModel.kt:210-230 / AlertService.kt:189-192 — raw substring oblast matching can false-positive.
- [Medium] NeptunClient.kt:114 — REST merge uses `>=`; equal timestamps let CDN-cached (older) REST coords win over the stream record.
- [Low] MainViewModel.kt:99-104 — 1s `nowFlow` keeps running while backgrounded.
- [Low] MainViewModel.kt:260 — `mapThreats` shows all-country threats whenever any oblast alert is active.
- [Low] Threat.kt:129-130 — `jokeUa/jokeEn` in a safety app (USER: IGNORED — dropped).
- [Low] UpdateManager.kt:113 — `versionNameGreater` drops non-numeric segments.

### Cross-references
- C1 + H3 fixed together (tick-driven re-evaluation + timestamp fallback).
- H1 shared helper `focusAttribution` (Cities.kt) used by MainViewModel + AlertService.
- M2 fixed by nested typed combines (MainViewModel, AlertService).
- M4 fixed by `OblastAlert.inOblast` startsWith token match.
