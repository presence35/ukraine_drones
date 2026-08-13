# Changelog

## [Unreleased]

- Updates: the app now checks for a new version automatically at most once per day (silently), instead of every time the app starts or the Settings screen opens. The manual "Check for updates" button in Settings still checks on demand.
- Map footer: per-threat notification bells and OFF chips are gone — threat types are toggled only in Settings. The footer now shows just the type icon and a pulsing underline while that type is active; the count appears only when it's > 0. With everything quiet it shows "No threats — go touch grass" in green instead.
- Map: the floating red/yellow zone buttons no longer respond to long-press; tap still zooms to that zone. A new floating edit (pencil) button opens the "Edit zones" panel.
- Map: when both zone alerts are muted, a small floating "All alerts are off" pill appears next to the zone buttons — tapping it opens the panel.
- Alert zones: the radius sliders no longer show step dots (the value still snaps to whole km). The per-zone bell icons moved next to the zone label and now show an expanding ring so they clearly read as tap-to-toggle.
- Alert zones: opening the panel auto-centres and zooms the map to fit the whole yellow zone within the visible area above the panel; the top drag handle is now also tappable to close the panel.
- Settings: new "Official alerts" toggle under Alerts — controls only notifications for the official oblast air-raid signal; the Red/Yellow zone alerts are never affected. The "Official signals come first" card now has an amber warning icon.
- Settings: the Language heading names the language you'd switch to (inverted like the flags), and the USA flag was redrawn as a proper 13-stripe flag with a real 50-star field.

## [0.3.9] — 2026-08-13

- Map: city labels now turn red while an official air-raid alert is active for that city's oblast (from the live alerts stream), and show an active-threat count in brackets next to the name, e.g. "Kharkiv (2)". No new API calls — reuses the existing alerts + threat stream.
- Settings: the speed pill moved out of the threat card row into the expanded "more info" section (below the description, above the photo).
- Connection pill moved into the header next to Settings; its info dialog now explains the status with literal green/red dots (green — data updating live, red — connection lost) instead of text.
- Map footer: the per-type status row (icons, alert bells, counts, OFF chips) now shows always, even when no threats are active, so the armed/muted bells are visible at a glance.

## [0.3.8] — 2026-08-13

- Settings: threat-type cards now expand in place ("more info" chevron) with a full paragraph of background on each type plus a Wikimedia photo — no more separate dialog. Images send a proper app User-Agent and fall back to the type icon on failure, so they actually load.
- Settings: threat-type photos refreshed — cleaner catalogue shots (Shahed mockup, Lancet close-up, MiG-31K carrying Kinzhal at the 2018 parade, Orlan-10 at Army-2022, etc.).
- Map footer: the inner/outer count columns are replaced by a single compact row — per-type icon, alert bell (filled while armed, grey when muted), live count, a pulsing underline while that type is active, and an OFF chip for types disabled in Settings. A compact edit (pencil) icon opens "Edit zones".

## [0.3.7] — 2026-08-13

- Threat popup: the "i"/more-info slide-down is gone (it expanded the card to full height with blank space). That info — type description, Wikimedia photo, speed pill — now lives behind a per-threat-type "i" button in Settings.
- Threat popup: the speed pill is back in the summary row (beside Distance/ETA); confirmations return as their own colored pill next to reliability, correctly pluralized ("1 source" / "3 sources" / «1 джерело» / «3 джерела» / «5 джерел»).

## [0.3.6] — 2026-08-13

- Threat popup: the group-size row now reads "Wave size" / «Хвиля» instead of "Group" — it's the reported size of a raid group (one track for the whole wave), not a count of drones currently in the zone.
- Location is now approximate (coarse) only — no precise GPS, no blue accuracy circle, and a single battery-cheap network fix (~2 min / 250 m) replaces the old dual GPS stack. App-info now shows "Approximate location" and background battery use drops sharply.
- Map: alert-zone circles are outlines only — the red/yellow fill colors are gone so the map underneath stays clean.
- Threat popup: the 0–10 level number is gone; a hazard skull above the bar now shows the level (fills/tints with severity).
- Threat popup: the "In red/yellow zone" pill is removed — the card's outer border itself is colored by the zone you're in.
- Threat popup: new "i" button at the top-right slides down more info for that threat — type description, a photo hotlinked from Wikimedia Commons, and the speed pill (moved out of the summary row).
- Connection pill: tapping it now opens a proper dialog — green/red status dot in the title, the explanation broken onto its own lines, and the NEPTUN link moved here from Settings.
- Alert zones: the floating master bell is gone; each zone (Red/Yellow) has its own bell icon right in its slider row in "Edit zones" — filled/colored while armed, outlined gray when muted.
- Alert zones: the "Radius of the critical/warning zone …" hint pills under the sliders are removed; the sliders show only the km value.
- Alert zones: the two floating zone circles now show a magnifier icon (tap = zoom to that zone) instead of "+".
- Map: Settings now opens as an overlay over the still-alive map — returning no longer resets the view into a low-zoom whole-world tile grid.
- Map: the floating red/yellow zone buttons sit in a horizontal row, each with a zoom-in icon (tap = zoom to that zone, long-press = Settings), plus an alarm bell that arms/silences both zone alerts at once.
- "Edit zones" panel: radius sliders update the live circles while dragging; the ✕ button is gone and the top handle dismisses the panel on a swipe down; the hint under each slider is now a pill; the per-zone alert toggles moved here from Settings.
- Settings: the separate Red/Yellow zone alert toggles were removed — arming now lives on the map (bell in the floating controls, per-zone toggles in "Edit zones").

## [0.3.5] — 2026-08-13

- Yellow zone radius now goes up to 20 km — for slow threats (Shaheds ~180 km/h) that's ~6–7 minutes of extra lead time instead of ~3.
- New "Fast objects alert sooner" setting (on by default): ballistic, cruise missile, guided bomb and MiG-31K fire the urgent siren as soon as they cross any zone boundary, since their 20-km and 1-km ETA differ by only seconds. Inbound fast objects also present as red everywhere (red banner, red count column, red marker ring, red popup tier).
- Settings: separate Red zone / Yellow zone alert toggles so you can silence either tier independently.
- Map: the +/– zoom buttons are gone (pinch to zoom). Two always-visible zone circles now sit in their place — tap one to zoom to that zone plus 5% margin, long-press to jump to Settings.
- Threat popup: the 0–10 gauge no longer shows "/10" (a full bar reads as 10) and moved to the right edge for balance. The ✕ close button is gone — tap anywhere on the map to dismiss.
- Threat popup: the fix-quality meter now reads "Precision" (UA «Точність») instead of "Uncertainty" — the ±km caption and meter polarity are unchanged.
- Settings: the Threats section header now reads "Threats — tap to toggle" / «Загрози — натисни, щоб увімкнути» to hint that threat cards are toggleable.
- Map: the app now opens zoomed to fit the whole yellow zone around your location instead of a tight city view — you see the full danger radius at a glance.

## [0.3.4] — 2026-08-12

- Header: the Ukraine trident moved to the left of the app logo (top-left of the map screen).
- Map: the blue GPS dot is now centered exactly on your location fix (it was anchored slightly high, making the zone epicentre look off).
- Settings: the check-for-updates control is now a prominent full-width "Update?" button instead of a tiny refresh icon.
- Unknown threat types no longer get a fake typical speed: no speed pill in the popup, no ~km/h pill in Settings, and they don't dead-reckon (markers sit at the last fix).
- Typical speeds rounded to clean numbers (ballistic 3300, cruise 850, KAB/aviation 900, shahed 180, FPV 120, recon 80 km/h); popup distances and ±uncertainty now round to whole km.
- Settings "Exit" renamed to "Stop Monitoring & Exit" / «Зупинити моніторинг і вийти».
- Map footer: "Edit zones" moved to the left side to balance the connection pill on the right.
- The "no threats — go touch grass" line is now green.
- The "Edit zones" panel is no longer modal: you can pan/zoom the map behind it while adjusting the sliders (close via the ✕, swipe handle, or Back).

## [0.3.3] — 2026-08-12

- Threat popup: removed the collapsible details panel — everything is visible at once. Added a vertical 0–10 threat-level gauge on the left (experimental: type, distance, reliability, source count, raid size, position quality and staleness combined into one number).
- Threat popup: the summary now shows a real speed pill (measured or nominal km/h) instead of the "(typical speed)" text; the redundant "Confirmed/Approx" and "Confirmations: N" lines are gone — source count now lives in the reliability pill ("Reliability: High · 3 sources").
- Alert-zone sliders moved out of Settings into an "Edit zones" bottom sheet on the map, so the red/yellow circles update live while you drag. The note under the sliders is now color pills (red/yellow) instead of plain text.
- Settings: each threat-type card now shows its typical-speed pill (~km/h), like the old Info tab.
- Disclaimer now notes that positions are approximate even when marked confirmed, and that the threat level is a rough estimate, not an official rating.

## [0.3.2] — 2026-08-12

- Full redesign: alert zones now follow your GPS position as two concentric circles instead of drawn polygons. Red zone radius 1–5 km (urgent, siren), yellow zone 6–10 km (warning, chime); both adjustable with sliders in the new Settings screen.
- New Settings screen (gear icon): bigger language flags, zone-radius sliders, per-threat-type cards with toggled borders (tap to enable/disable), a hideable "official signals come first" disclaimer, and the Exit button moved here.
- Threat popup redesigned: compact header with a collapsible details panel.
- Official alert indicator is now a Ukraine trident (wave-gradient) that glows red while an oblast alert is active.
- GPS tracking made battery-minimal: ~2-minute updates at 250 m of movement (network provider first), shared between UI and the background service.

## [0.3.1] — 2026-08-12

- Legend "How to read the threat card" section is now visual, mirroring the popup: colored position-quality dots (green Confirmed, amber Approx, gray Uncertain), distance dots with zone tints, recorded/typical speed pills, and a sample uncertainty bar.
- The GPS-precision warning for "Approx" now lives in full in the legend; the small tappable "Approx" tag was removed from the threat popup.

## [0.3.0] — 2026-08-12

- Reworked the three-zone model into two editable tiers: Centre (red, urgent siren) and Region (amber, two-tone warning chime). The built-in Centre/Region boundaries are now just the defaults — drawing a square over either zone replaces it.
- OUTER (Region) alerts ring a distinct two-tone chime; INNER (Centre) and the official oblast alert keep the air-raid siren.
- Your previously drawn custom zone is kept as your Region (outer) zone.

- Scale bar moved onto the map (bottom-left, Google-Maps style): thin alternating black/white bar with a white outline, label above it.

- Header slimmed down: connection status is a tappable green/red dot (tap for details), alert text shortened to "alert"/«тривога», and the OFFICIAL pill only shows when a zone alert is also active.
- Info tab now shows a typical-speed pill (~km/h) for each threat type.
- When redrawing the custom zone, the previous zone outline is hidden so only the new draft square shows.
- Draw-tool banner slimmed down (one-line hint, cleaner Cancel/Done layout); Done no longer jumps the map when redrawing, Cancel clears the draft.
- GPS location marker is now a classic blue glowing dot instead of the default icon.

## [0.2.9] — 2026-08-11

- Map UX redrawn: square-only custom zone (drag the edge to move, the corner to resize), free pan/pinch-zoom while drawing, GPS blue dot, and an on-map scale bar.
- Threat popup improved: user-distance colouring (red <5 km, amber <15 km, green), zone distances tinted per zone, position-quality colours, confirmations merged into details, tighter layout.
- English UI now translates NEPTUN's Ukrainian locality/course text automatically (with raw fallback).
- Info tab shows the current map zone and a "how to read the threat card" legend.

## [0.2.8] — 2026-08-11

- Custom alert zone you draw yourself on the map (circle or square) — redraw or remove anytime; alerts now fire for Centre, Region and your custom zone as color-coded banners + texts, alongside the official oblast alert.
- Segmented Centre / Region / Custom switch on the bottom bar; map shows zone outlines (red/yellow/blue) and threat markers are ringed in their zone's color.
- Per-zone threat count panel under the bar; threat popup shows distance/proximity in km.
- Tapping an alert notification opens the app on the existing screen instead of stacking a second copy.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.7] — 2026-08-07

- Threats expire after a per-type staleness window (ballistic 1.5m, missile/KAB 3m, aviation 4m, UAV/recon 5m) — no more long-lived ghost markers; dead-reckoning horizons match.
- Threat popup shows a live m:ss count since the object was last seen.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.6] — 2026-08-07

- Threat course is now a reliable heading (velocity bearing while live → heading → A(id)); flying flag added (velocity + confirmed fix + active).
- Prediction only dead-reckons while flying, anchored on `confirmedAtMillis`; otherwise markers keep the raw fix — no phantom drift.
- Map markers are placed at their predicted position on build; rotation uses the new course.
- REST merges by newer `updatedAtMillis`; foreground refresh only when the WS has been quiet >5s.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.5] — 2026-08-07

- Info tab pops the update dialog (new version + release notes) as soon as an update is available, on auto-check and manual check alike.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.4] — 2026-08-07

- Info tab shows the new version inline as a bigger, tappable "v#"; tapping it downloads and installs the update right away.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.3] — 2026-08-07

- Better alerts, better caching, less battery use, colored drones.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.

## [0.2.2] — 2026-08-07

- Better alerts, better caching, less battery use, colored drones.
- Info tab shows the current version with a manual check button.
- Info tab auto-checks on open; "new version: vX.Y.Z" shown (Ukraine-colored) without popping the update dialog; startup is a silent check.
- Version bumped automatically via `bumpVersion`; release APK + `version.json` uploaded via `uploadRelease`.
