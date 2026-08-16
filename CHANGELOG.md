# Changelog

## [Unreleased]

- Map: zooming in now shows smaller towns across all regions (~300 more minor city labels, ~3–15 per oblast) in addition to the major cities, so you get a sense of distance/scale everywhere — not just around Odesa. Minor towns are map-context only: follow-me attribution and the banner city still resolve to major cities, and the pin picker is unchanged.

- Zones panel: the Fast/Slow group Map & Alerts toggles are gone from the bottom of the "Edit zones" sheet (they stay in Settings and the first-run dialog). Slider bands are now Slow red 2–20 km / yellow 21–50 km and Fast red 2–5 min / yellow 6–20 min, with fresh-install defaults at the top of each band (20/50/5/20).

- Threat popup: the "Middle" card size is gone — only Small and Large remain. The Small card's threat icon and skull now match the Large card's sizes, gained a compact "R" reliability row on top, and its distance/ETA/speed pills stay on one line.
- Photo threat icons are now the default for everyone (users who prefer the classic vectors can switch back in Settings → Additional).
- Settings → Additional: the "Threat icons" picker is now a 2x2 grid — the Classic and Photos cards each show all seven icons in a small scrollable panel, with two "coming soon" placeholder cards below. The Additional section opens expanded so the scale/icon toggles are visible, and the "Show scale" row now shows a ruler icon instead of the gear.
- System status: a new collapsible connection log shows the last 10 status changes — time, online/offline/backup, and how long each drop lasted. Drops under 30 seconds are ignored so random hiccups don't pollute the log, and the log survives app restarts. The currently-open episode shows a live running duration.
- Header: the connection pill now shows the NEPTUN emblem with a green "Online" label while the feed is healthy.
- Header: the NEPTUN emblem in the connection pill is now color-coded — green while online, red when offline, amber when on the backup source.
- Header + notifications: the misleading "Offline 0m" counter is gone — offline just says offline; the connection log gives the real duration.
- Zones panel: a gear in the top-right opens Settings directly on the Threats section.
- Language: English text is now fully offline. Place names (cities, oblasts, districts) are romanized — never "translated" — via the official Ukrainian transliteration rules (Золоте → Zolote, not "Gold"), and military vocabulary (UAV, Shahed, missile, heading toward…) is hard-coded. The live Google translate call is gone, so EN notifications post instantly with no network dependency.

- Map: when a threat is resolved or removed by the server, a playful 5-second "neutralized" animation plays at its last position — a quick ping, a 3-2-1 countdown, then a small explosion. The threat's icon stays on the map for the full animation and is removed for good only once it completes. (Long-pressing a threat marker or empty map fires it on demand — a temporary dev/testing aid.)
- Settings → top card: now the "Disclaimers" section with a full list of caveats (approximate numbers, unofficial rating, coarse location, no safety guarantee). It auto-expands on the first 3 Settings opens, then remembers the state you leave it in.
- Threat popup: the "Neutralized" fade now also triggers when the selected threat goes stale past the ghost cap, and the card carries a caption that it's just a visual flourish — the threat is no longer around for whatever reason.
- Settings → Additional: a new "Threat icons" picker switches the whole app between the classic vector icons and the new photo set (each option shows all seven icons in a scrollable panel). Photo markers on the map are rotated so each subject points along its true course; in small slots they keep their aspect ratio instead of stretching.
- Settings → Threats: Map and Alerts toggles are now fully independent — turning Map off no longer silences a type's alerts, and turning Alerts on always re-enables the type on the map (an armed alert is never hidden). The per-threat and group Alerts chips are always pressable.
- Settings → Threats: the Fast and Slow group collapsed state is now remembered across app restarts.
- Settings → Map centre: the pin-to-city list is now plain city rows (the red official-alert dots are gone), and the section/guide text no longer mentions red-marked cities.
- Threat popup: when a selected threat is resolved or disappears, a compact "Neutralized" card (type icon + name) briefly appears and fades out instead of the card vanishing instantly.
- System status: the NEPTUN logo row now also shows the site URL (neptun.in.ua), and the whole row opens the site on tap.
- Header: while the urgent (red) alert banner is up, the trident renders white instead of red-on-red so it stays visible.

- Map: city labels no longer show "(N)" threat counts next to the name — just the city name, red while its oblast is on alert.
- Notifications: official alert bodies are localized on-device — no background translation re-post.
- Threat popup: the speed pill now shows only when the speed was actually measured (server/measured fixes), not the nominal typical speed.
- Alerts/status: the backup source also steps in when the NEPTUN stream is completely silent for over a minute (any frame type), not just its alert feed.
- Map: threats that report only a heading now glide along it (dead-reckoned) even without a velocity — they actually move instead of sitting still.
- Wording: "coarse"/"rough" replaced with "approximate" throughout, and the background status notification now reads "Threat alarm is following your GPS" / pinned to a city.

- Zones: red/yellow tiers are now per-group — slow threats (drones, recon) are measured by distance (red within 60 km, yellow within 180 km by default) and fast threats (missiles, guided bombs, aviation) by time-to-arrival (red within 10 min, yellow within 30 min). Fast objects therefore alert from far outside the drawn circles, exactly when they're really imminent.
- Alerts: the "Fast objects alert sooner" setting is gone — fast threats now always tier by their own ETA, and a fast object with no usable speed never sounds.
- Zones: the zones panel is fully visible at once — Slow (km) and Fast (min) sliders plus the Fast/Slow Map & Alerts group toggles, with no sliding the panel up to reveal per-type toggles (those now live only in the first-launch dialog). The map's red/yellow circles show the slow distance zones and follow your position.
- Zones: per-type reach caps (guided bombs 70 km, FPV 40 km, recon 50 km, Shahed 1000 km, ballistic/cruise/aviation/unknown 1500 km) stop distant noise — an object that physically can't reach you never alerts.
- Zones: advisory (NEPTUN observation) threats no longer tier or sound an alert — map-only in the UI.

- Map: threats that only mark an oblast area (no real position) are no longer drawn or counted — only threats with a real point show on the map and in the threat strip.
- Alerts: when several threats enter a zone at once, each newly-entered threat now gets its own notification on successive ticks instead of only the most urgent one ever alerting.
- Status: the "Active" badge no longer appears on the backup source until it has actually delivered data at least once — a source that is red (never confirmed healthy) no longer reads as the active one.
- Status: NEPTUN connection failures (including background refresh) now surface an error, so a silent REST drop is visible in the system status instead of looking healthy.

- Alerts: the "alerts off" bell is now a red bell with a slash through it everywhere — in the threat popup, on the muted zone buttons floating over the map, and in the zone-mute controls — so a muted state can never be mistaken for alerts being on.

- Alerts: official oblast-alert notifications now show a reason line — the strongest active threat in the oblast ("Shahed heading toward Odesa", or a "Threats reported in …" fallback). If the alert fires before a threat appears, the notification updates in place with the reason as soon as one does. On all-clear, the siren notification is dismissed immediately when no zone alert is ringing.

- Threat popup: the ETA pill is now marked with a glowing blue GPS dot, making it clear at a glance that the countdown is the time until the threat reaches you.
- Threat popup: when a threat type's Alerts are switched off in Settings, its card shows a small grayed-out alarm bell next to the type name as a reminder that notifications for it are muted.
- Settings: new collapsible "Additional settings" section (bottom of the list) with a "Show scale" toggle for the map's bottom-left scale bar. The battery-exemption card moved into it.
- Map: the scale bar label is now a bold white font with no background pill — less intrusive over the map tiles.

- Settings → Threats: each threat's Map/Alerts controls are compact icon-chips (map / bell) stacked on the right of the card (no more switches or full-width row), so the list is far shorter to scroll. The threats are grouped into two tiers — Fast (lightning icon: Ballistic, Cruise, Aviation/MiG-31K, Guided bomb) and Slow (turtle icon: UAV, FPV, Recon, Unknown) — each with an icon header. A master "All" card at the top switches every type's Map/Alerts per group at once.
- Settings → Threats: the first 3 times you toggle any Map or Alerts control (per-threat or group), a short bold-highlighted toast explains how they work ("Map off hides the type and silences its alerts" / "Alerts off keeps it on the map, just dimmed").
- Settings → Threats: fixed the per-threat "Map" toggle being stuck off once turned off — it can now be switched back on (turning off a type still silences its alerts, and "Alerts" re-enables with it).
- Settings → Threats: tapping a threat's name/description now expands the card, not just the caret. The section helper text is back to the short "Threats — tap to toggle".

- Threat popup: the wordy "Distance / ETA" line and coloured speed pill are replaced with a neutral trio of pills where the number is the hero — «3 км» · «10 хв» · «180 км/год» (EN: `3 km` · `10 min` · `180 km/h`). ETA is shown in minutes only. Pills are muted, wrap-friendly and work in small/medium/large. Settings → threat card size now notes that all numbers in the app are approximate.
- Zones: red zone radius now adjustable up to 20 km (default 10 km) and yellow zone up to 50 km (default 21 km), for wider-area monitoring.
- Alerts: oblast alerts now have a real backup system. When the main NEPTUN feed is down or silent for over a minute, the app falls back to an independent official source (alerts.com.ua, the same state data other air-raid aggregators use) so air-raid notifications keep working. When an alert comes from the backup it's tagged in the notification body.
- Status: the connection pill shows one of three clear states — red "offline" with a timer when NEPTUN is down (real or simulated), amber "backup" when NEPTUN is alive but the app is on the backup source, or green "online". Tapping it opens a "System status" popup that lists NEPTUN and the backup (alerts.com.ua) as colored status dots with the source actually in effect marked "Active", and notes the backup is oblast-level only (no live map positions).
- Status: a temporary "Test: simulate NEPTUN offline" toggle inside the System status popup makes NEPTUN appear red/offline (as if the stream dropped) so the different online/offline states can be verified (resets on app restart via the same toggle).

- Alerts: when the live feed drops, a silent-but-attentive offline notification appears (after a 30s grace, or immediately when an official air-raid alert is active at drop or fires during the grace) reminding you to rely on official sirens, with a Retry action that forces an immediate reconnect. The ongoing status notification switches to "Offline for Xm" / «Офлайн Xхв» with the same Retry action, and the header connection pill shows the elapsed offline time.

- Settings → Threats: each threat type now has two separate controls — "Map" and "Alerts" — shown as bordered icon cards. Turning off "Map" hides the type from the map and automatically silences its alerts; turning off "Alerts" keeps the type on the map (dimmed) but stops its alerts. A type with either Map or Alerts off is hidden from the bottom threat strip.
- Large system font sizes (accessibility) no longer break the layout: the threat popup and top banner text wraps instead of truncating, the popup scrolls when it overflows the screen, the gauge/level bars/skulls/course arrow grow with the text, footer pills wrap, and the app caps the effective font scale at 1.5× so extreme settings can't clip.

- App icon: the launcher icon is now our Ukraine trident (blue→gold→blue gradient) instead of the drone graphic.

- First launch: tapping a language in the picker no longer auto-closes it — it just switches the language live so you can read the tips first, then confirm with the new ОК/OK button.
- First launch: the language picker now shows three short tips — tap any drone on the map to open its details card, Settings (gear) holds zones/language/feature guide, and sirens follow the phone's sound mode (override with "Sirens always sound").
- Settings → Map centre: the pin-to-city control is now a proper text-field dropdown that opens as a height-constrained scrollable list below the field (no more full-screen box). The title lives in the field label and the description below it; the red alert dots stay.
- Threat popup: the course description shows only its first sentence.
- Settings → threat card size: the note below the three sizes is replaced by a skull line explaining that the 0–10 gauge is a rough estimate (type, distance, reliability, sources) — not an official rating or a guarantee of your safety.
- Alerts: sirens now respect the phone's vibrate/silent mode by default — they ring at notification volume in sound mode and only vibrate on vibrate/silent, so the app no longer blasts over a deliberately quiet phone. A new "Sirens always sound" setting (off by default) in Settings → Alerts makes siren alerts ring even on vibrate/silent. The all-clear chime always follows the phone's mode (it's not an emergency).

- Settings: threat cards (incl. the UAV one) no longer auto-expand when Settings opens — everything starts collapsed.
- Settings: fixed the pin-to-city dropdown list appearing in a detached popup (a stray box away from the field); it now opens right below the field. The alert-red city dots stay.
- Settings: a new "Keep alerts running" card asks Android to let the app run unrestricted in the background (battery optimizations off). It only appears while the phone still pauses the app, explains that the app itself uses very little power (live alert stream + low-power location), and turns into a quiet "Unrestricted in background" status once granted.
- Settings threat card photos: the Shahed, Unknown and Ballistic reference photos are now bundled in the app (offline, instant load) instead of being served from our update server — the app no longer depends on the server `/images/` folder.
- Map: the map can't pan past Ukraine (incl. Crimea) or zoom out beyond "Ukraine fills the screen", so foreign territory like "all of Europe" is unreachable.
- Monitoring: the background service now restarts automatically after a phone reboot and after an in-app update, so alerts keep working without reopening the app.
- Alerts: when the official air-raid alert ends, a short cheerful "all clear" chime + notification plays (official alerts only — zone threats end silently). The all-clear notification names the city (e.g. "Kyiv: all clear" / «Київ: відбій тривоги»).
- Release: the APK is now signed with a dedicated release keystore (no more public debug key) and shrunk with R8, so it's smaller and can't be impersonated by anyone holding the well-known debug key.
- Settings: the new "Map center" section now shows both controls at once — a "Follow me" toggle (on by default: camera + alert zones keep following your GPS) and a dropdown that pins the camera and zones to any of 22 major cities. Picking a city switches Follow-me off automatically; cities whose oblast has an official air-raid alert are marked with a red dot in the list.
- Map: with a city pinned, the camera and the red/yellow alert zones centre on that city and a proper map pin (tip on the city) marks it. The bottom-left pill shows "Pinned: <city>" / «Прикріплено: <місто>» only while a city is pinned.
- Header: with a city pinned the title shows just the city name (e.g. "Kyiv" / «Київ»); during an official oblast alert it becomes "<city>: alert" / «<місто>: тривога» and the trident glows red for the pinned city's oblast instead of Odesa's.
- Alerts: the official alert notification follows the same focus — the banner and body use the pinned city's region (e.g. "Kyiv region" / «Київська область»); the background status notification reports that the zones are pinned to a city rather than following GPS.
- Threat popup: when a city is pinned, the distance line reads "Distance to <city>" / «Відстань до <міста>» instead of just "Distance".
- Settings: fixed the pin-to-city dropdown not opening (the anchor toggled itself twice); it's now dimmed and disabled while "Follow me" is on, since a pin only takes effect once following is off.

- Settings: "made by" credit moved to the very bottom of the screen.
- Header: the settings button in the top-right is now a gear icon (was a pulsing heart), keeping the blue-over-yellow Ukraine coloring.
- Settings: opening Settings auto-checks for updates (at most once a day, silently). The update button now lights up with a download icon and "Update available · vX" / «Доступне оновлення · vX» when a new version exists; otherwise it's a plain outlined "Update" / «Оновити» button you can tap to check manually.
- Update dialog: on app start it still pops, but only when no threat or official alert is active — during an alert it stays silent and the Settings button shows the available update instead.

- Settings: the Alerts toggles now have leading icons — a lightning bolt for "Fast objects alert sooner" and the trident for "Official alerts" — with a note explaining that a red trident in the header means the official alert is on.

- Language: English strings now use Canadian spelling — "Map centre" replaces the American "Map center" in the settings section title, its pin-to-city description and the Feature guide.- Map: the footer threat strip now shows only the types that actually have a live count — types with zero threats no longer appear greyed out; when every count is zero the strip shows the "no threats" message as before. The Feature guide's threat-strip and threat-toggles cards were updated to match.
- Language: English is now shown with the Canadian flag 🇨🇦 instead of the US one — in the Settings language switcher, the first-launch language picker and the Feature guide's language illustration.
- Removed: the "How it works" / «Як це працює» first-launch spotlight tour and its Settings replay button (which looked dead whenever an alert was ringing). The Feature guide is now the single tutorial surface.

- Guide: a brand-new "Feature guide" / «Путівник по функціях» screen (Settings → "Feature guide" / via "Learn more" / «Дізнатися більше» in the tour) explains 14 core features in short, expandable cards with small animated illustrations — map basics (live map, threat strip, connection & scale), zones & alerts, location (follow me / pin to city), threat cards (sizes, reading a card) and settings (language, threat toggles, updates). The first-launch tour tooltips are now one-line hooks with a "Learn more" link straight into the matching section instead of a wall of text.

- Map: tiles are now only downloaded (and cached) while the camera shows Ukraine plus a margin around it — panning or zooming beyond that area leaves a black base map with markers but uses no extra data and no cache.
- First launch: the language picker no longer has a "Later" button — each flag now has the language's own name under it (Українська / English); tapping outside still dismisses it.
- Map: the initial view starts at a city-level zoom instead of the whole globe, and the (approximate) GPS location permission is now requested first (before notifications) on first load, so the map can find you straight away.
- Alert zones: default red and yellow zone radii are now the maximums (5 km and 20 km) for fresh installs.

- Map: when both zone alerts are off, a small "all alerts are off" pill (two muted bells + warning icon) floats above the zone buttons; tapping it opens Edit zones. The pencil (Edit zones) button now sits level with the zoom buttons instead of floating higher.

- Settings: toggles (Follow me, fast alerts, official alerts) now respond to a tap anywhere on their row, not just on the switch itself.

- Map, settings and threat cards: the UAV (Shahed) icon is back to the cropped shahed.webp image; on the map it's scaled down to a marker-sized bitmap.

- Header: the heart (Settings) button now shows its Ukraine blue-and-yellow colors split 50/50 (blue top half, yellow bottom half — it was being tinted monochrome, and the blue used to be only the lobes) and, until you've opened Settings 10 times, the heart gently beats (scales up and down) to make it clear it's tappable.

- Threat popup: its size and detail are now selectable in Settings ("Threat card size and detail" / «Розмір і деталізація картки загрози») — Small (one glanceable line: threat icon, type and distance/ETA with the level skull beside its bar), Medium (header, distance/ETA + speed, reliability/elapsed) and Large (the full card, unchanged). Each option shows a live scaled preview; the whole tile is tappable.

- Threat icons: every threat card (Small/Medium/Large), the Settings threat rows and the footer threat strip now always use the matching vector SVG icon (no more webp photo swap). Map markers also use the vector icons. The photo now appears only as the reference image inside the expanded Settings threat card. The Small card's skull moved next to its level bar instead of replacing the threat icon.

- Settings: the Unknown threat card now ends with a playful Schrödinger one-liner («Об'єкт Шредінгера: і дрон, і ракета…» / "Schrödinger's object: both a drone and a missile…").

- Threat photos: the UAV and Unknown images now load from our own server (`/images/`) instead of being bundled in the app, and are cached on the phone in the OS cache folder (evictable, not user data) so they don't re-download each launch; until a photo is cached the app falls back to the matching icon, so it works offline too.

- Settings: "Map center" no longer offers an "Odesa (follow GPS)" entry — the city dropdown lists only the 22 cities (picking none just shows the empty control, dimmed). Pinned, the map stops following GPS entirely: the GPS dot is hidden and only the city pin is shown.

- Settings: sections now have icons — language (globe), "Map center" (map pin), "Threats" (warning) and "Alerts" (bell) — and are reordered so "Official signals come first" sits at the top, followed by Language then Map center. The heart-shaped Settings button now uses Ukraine's blue-and-yellow.
- Settings: the first threat card (UAV) is expanded by default so the expandable rows are discoverable; the expand/collapse carets are bigger for a clearer tap target.
- Settings: "Official signals come first" is expanded by default and needs two collapse taps before it stays collapsed.
- First launch: a small dismissable popup asks you to pick the app language (Ukrainian preselected); you can also skip it and choose later in Settings.

- Guide: the app now runs a short first-launch tour — a spotlight walks you through the alert zones, the "Edit zones" button, the threat strip and Settings, with Skip available at any step. It never shows during an active alert (and drops out instantly if one fires mid-tour). A "How it works" / «Як це працює» button in Settings replays it anytime.

- Threat popup: NEPTUN's bare confirmation text in the course line (e.g. «Підтверджень: 3») is dropped as redundant — the confirmation count already shows as a pill in the footer.

- Alert zones: each zone is now a single row — the bell and a proper on/off switch on the left, the radius slider in the middle, and the km value at the end. The zone-name labels are gone since the colors speak for themselves.
- Map: a zone whose alerts are muted shows a dimmed grey bell floating above its zoom button; the button itself keeps its zoom icon and still zooms to that zone on tap. The amber "All alerts are off" pill is gone — the per-zone bells carry that signal now.

- Rebranded nationwide: the app is now "Ukraine Drones" («Українські дрони») — new launcher name, in-app title, package `ua.ukrainedrones`, and update-server folder (`other_apps/ukrainedrones`).

- Map: the pinned city is now marked with a proper map pin (tip on the city) instead of a blue dot; the bottom-left pill shows only "Pinned: <city>" (and only while a city is pinned) — the "Following" pill is gone.

- Alerts: the official alert notification now carries the reason — the latest NEPTUN Telegram message mentioning Odesa (the same wording groups post), with a fallback to the highest-priority active threat over the oblast that appeared since the alarm started. In English the reason line is translated/derived and the original message is appended below. While the alert is ringing, new reason text updates the notification silently (no siren re-trigger).

- Settings: new "Map center" section — a "Follow me" toggle keeps the camera and alert zones on your GPS (the default); when it's off, a dropdown pins the camera and zones to any of 22 major cities instead.
- Map: threat markers no longer have a colored circle (zone ring) around them — just the clean type icon.
- Updates: the app now checks for a new version automatically at most once per day (silently), instead of every time the app starts or the Settings screen opens. The manual "Check for updates" button in Settings still checks on demand.
- Map footer: per-threat notification bells and OFF chips are gone — threat types are toggled only in Settings. The footer now shows just the type icon and a pulsing underline while that type is active; the count appears only when it's > 0. With everything quiet it shows "No threats — go touch grass" in green instead.
- Map: the floating red/yellow zone buttons no longer respond to long-press; tap still zooms to that zone. A new floating edit (pencil) button opens the "Edit zones" panel.
- Map: when both zone alerts are muted, a small floating "All alerts are off" pill appears next to the zone buttons — tapping it opens the panel.
- Alert zones: the radius sliders no longer show step dots (the value still snaps to whole km). The per-zone bell icons moved next to the zone label and now show an expanding ring so they clearly read as tap-to-toggle.
- Alert zones: opening the panel auto-centres and zooms the map to fit the whole yellow zone within the visible area above the panel; the top drag handle is now also tappable to close the panel.
- Settings: new "Official alerts" toggle under Alerts — controls only notifications for the official oblast air-raid signal; the Red/Yellow zone alerts are never affected. The "Official signals come first" card now has an amber warning icon.
- Settings: the Language heading names the language you'd switch to (inverted like the flags), and the language flags are now real flag emoji (🇺🇦 / 🇺🇸) instead of hand-drawn vectors.
- Threats: "Observation" (Спостереження) threats are now observations only, not take-cover signals — they never fire a zone siren or chime, are drawn dimmed on the map, and count half as much in the threat-level gauge.
- Threats: the popup now shows the specific threat name from the server (e.g. «Шахед», «Орлан-10») under the type label.
- Map: threats with a wide position uncertainty now show a soft amber ring of that size (±km) around the marker.
- Reliability: staleness, ETA and elapsed time now follow the server clock (serverTime), so a wrong device clock no longer makes threats expire early or linger.

- Map: tapping the top title banner now zooms out to show the whole of Ukraine (instead of just recentring on you).
- Threat popup: the medium card is now a fixed three rows — header (icon + type + region), a single-line pills row (ETA / distance / speed, capped font scale so it can't wrap), and a bottom row with skull + expanded level bar + reliability/age. It no longer grows as content changes.
- Threat popup: the full card's reliability is now a compact Precision-style 3-segment bar (low → high, progressive fill; High green / Average amber / Low red, gray when unknown) instead of a red text pill; the medium card keeps the text pill. The rotated course arrow was removed from the card headers.
- Threat popup: a three-line size control (thin / thick / thicker) sits under the bottom-right corner of the card — tap it to cycle Small → Medium → Full (persisted).
- Alert zones: dragging the panel up reveals a slim Fast/Slow threat-toggles panel (the same Map/Alerts controls as Settings); dragging down collapses it again or closes the panel. The first-launch "A few tips" dialog now includes the same slim threat toggles so they can be set before first use.
- Settings: the Fast and Slow threat groups start expanded; tapping a group header collapses its per-type cards (headers with their Map/Alerts master toggles stay visible).

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
