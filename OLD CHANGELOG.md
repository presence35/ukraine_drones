# Changelog

## [Unreleased]

- Threat popup (EN UI): common words in a course assessment are now translated, not transliterated — "БпЛА над морем" reads "UAV over the sea" instead of "UAV over morem", "курсом на Чорне море" → "heading toward Black Sea". The dictionary covers sea/coast, border, airspace, settlement and attack vocabulary (with the usual case forms); place names are still transliterated, never translated.

- Map: threat icons now keep pace with the map as you pinch-zoom in — flat up to zoom 10, 2× at zoom 12, capped at 3× around zoom 13.2 and flat beyond — so a threat visibly grows while you zoom closer instead of shrinking against the map.
- Map: shooting a drone down with a long-press no longer "respawns" it as a new alert — the object stays in memory, the death animation plays, and the drone stays hidden until the next redraw brings it back in place. A same-id respawn within 3 seconds of the shot is treated as the same drone (no new siren/notification); after that, a fresh appearance is a new threat again.
- Map: a drone the stream briefly drops (snapshot glitch) right after you shot it is kept alive in memory for those 3 seconds, so it comes back in place instead of being removed and re-added.

- Connectivity: fixed a reconnect deadlock where the client could permanently stop reconnecting until the phone was rebooted — a manual retry (Retry button, foreground, "test" toggle off) during an in-flight connection attempt left the reconnect guard stuck, silently swallowing every later attempt.
- Alerts: an official alert can no longer show a false "alert ended" — while NEPTUN and the backup are both unreachable, the last-known official-alert state is held instead of cleared (no fake all-clear chime, no banner flicker); the all-clear fires only when a live source actually reports it. A stale backup snapshot is never served as live, and held alerts are never source-tagged.
- Connectivity: turning the TEMP "test" (force-offline) toggle off while the socket is genuinely down now kicks a real reconnect attempt instead of leaving the app offline.
- Connectivity: reconnect attempts now really back off — the first retry after a drop is near-immediate (~1–3s), then exponential up to a 15s cap (the fixed 5-second retry is gone); the 10-minute offline message no longer claims a fixed 5s cadence.
- Notifications: the offline milestone notifications (3/6/10/20 min) reset correctly on reconnect — a second outage re-notifies each milestone instead of staying silent — and any lingering milestone notification is dismissed the moment the connection returns.
- Notifications: turning official-alert notifications back on while an alert is still live now announces it (previously the "already notified" flag swallowed it silently).
- Notifications: the offline milestone messages are now fully translated in Ukrainian (they were English-only).
- Notifications: the alert notification-channel names and descriptions now follow the app language (previously only the status channel was localized).

- Shelters: a new "Go to shelter" (До укриття) button appears on the map when your location is within Odesa shelter coverage — solid red while an official alert is active, ghost-outlined otherwise — and opens a screen ranking the nearest shelters by distance. Each row shows the distance, adult and kid walking times, and opens the shelter in your maps app. Pull down on the list to re-fetch the shelter data; the header shows how fresh your GPS fix is and can force a precise location refresh; shelter names are transliterated for the English UI. The shelter list is bundled with the app and refreshes daily from the update server.
- Settings → Shelter: a new section groups the "Go to shelter" button toggle with a new "With kids" toggle — kids walk slower, so walk times are calculated a bit longer (shown on the list with an adult-and-child icon).

- Alert history: when an alert has ended, its row now shows "ended 1-59 min ago" (завершено … тому) right after the fired-ago readout, so a settled alert is distinguishable from a still-ringing one. Entries that were still open when the app/process restarted are now stamped ended at load instead of looking active forever.
- Alert history: an official oblast alert is now always logged when it starts, even when a zone alert posts on the same tick — so the official row (and its "ended" time) always appears in the log.
- Notifications: tapping an alert notification while a city is pinned is hardened — invalid coordinates from a stale/corrupted notification are rejected before reaching the map, and the reveal camera-framing can no longer crash the app if the map isn't laid out yet or the framing box is out of range (it falls back to a plain centre-on-threat pan).
- Settings → Card size: the small-card preview now shows the compact top-left chip (narrower, hugging the corner) just like it looks on the map, instead of a full-width card.
- Settings → Alerts: the Night mode section is boxed in a subtle indigo-tinted panel with a border and the moon icon on its toggle, so the long section stands apart from the rest of the card.

- Settings: every time it opens, the app checks for a new version — when one is available a snackbar with a "Download" action appears (an already-known version just re-reminds without another network hit); tapping it opens the download dialog.
- Threat popup: the indicator next to the threat title when that type's alerts are off is now a crossed bell with a small "off" / "вимк" label, so it can't be mistaken for an active alert.
- Threat popup: on the small card the metrics now start under the title (not under the icon), with the reliability and level gauges aligned beneath their "R" and skull headers; on the large card the reliability row is vertically centred against the "x sources" pill; the card-size toggle sits in the bottom-left corner on both sizes.

- System status popup / connection pill: the NEPTUN logo is now the project's own emblem (vector drawable), replacing the old raster copies — the connection pill tints it green/red/amber per state instead of swapping separate PNGs.
- Alert history: entries now auto-clear after 6 hours (was 3 days) and each row shows how long ago it fired — "1-59 sec" / "1-59min" / "1-6hrs" — grouped into second/minute/hour buckets; the connection log below keeps absolute timestamps.
- Alert history: an official alert now shows the monitored city (GPS or pinned) even when the alert itself carries no target.
- Map: death-strike projectiles now come from the nearest major city to the target (falling back to your GPS position or pinned city) instead of from your GPS fix — the bullet enters from just off the screen edge on the city's side, and the camera never scrolls to the city. With "Follow the bullet" on, the camera glides onto the strike; with it off, the camera stays put — and it never returns to your position afterwards either.
- Map: threat icons grow as you pinch-zoom in — flat up to zoom 10, scaling to 2.0× at zoom 16 and clamped there — instead of growing only while a threat's card is open; opening a card no longer moves the camera at all.
- System status popup: the "x" next to the NEPTUN URL is gone — the popup dismisses by swipe-down or tap-outside, exactly like the alert-zone panel.
- Connection: the 30-second "connection wobble" grace is gone — a dropped connection is reported instantly, so every loss writes a connection-log entry the moment it's detected.
- Map: long-press neutralization is now a permanent feature (was the temporary debug trigger); comments and naming updated accordingly.
- Only one popup/modal can be open at a time on the map — opening the zones sheet, the system-status popup, a feature explainer, the update dialog, or a threat popup closes any of the others first.

- Threat popup: the neutralizing card now shows a visible fade — after the projectile strikes, the card holds, then dissolves over the rest of the explosion instead of vanishing instantly — and the flourish text now reads "Neutralizing threat…" / "Threat neutralized" ("no longer tracked by the network") in both languages.
- Threat popup: the small (non-selected) card is now a compact top-left chip — max 300dp wide, title/icon row with the "R"/skull gauge up top, one-line pills, and the distance/speed/metrics stacked below it — so the map stays visible beside the card instead of a full-width banner.
- Threat popup: a selected threat's card is now capped to the same narrow width, so the map stays visible beside the card instead of a full-width banner.
- Map: the camera no longer re-centres a selected or struck threat below the popup card — tapping a threat just opens its card in place, and a death strike glides the camera onto it only when "Follow the bullet" is on.
- Settings: Night mode moved into the Alerts section (it follows the same "when it's night" rule as the other night settings); the one-time explainer popups now snap the Settings scroller to the explained row and flash it blue.
- Notifications: when "break through do not disturb" is on, override alerts also launch the app full-screen on top of DND (new `USE_FULL_SCREEN_INTENT` permission) — a missed siren can no longer hide behind the lock screen.

- First-run wizard: rebuilt into three steps — language (title shown in the language you'd switch to, with an intro note) + the three tips (larger text, the drone tip now uses the photo) + the threat-icon pack picker at the bottom; then the "what matters to you" threat grid split into fast/slow groups; then the feature preview (live map, zones, notifications, night mode, follow me) and Start.
- "Later" on the first-run wizard now exits all setup chrome: it also skips the battery prompt and defers the location/notification permission dialogs to the next launch (they re-ask each cold start until granted).
- First-run wizard: the flash on startup is gone — the wizard only appears once the live feed has settled (or after a short offline grace), so it can never mount-then-vanish when your area is already on alert.

- Fixed a force-close when tapping a threat while a city is pinned (or while turning "follow me" off with the threat card open) — the distance pill passed a string where the format expected a number.
- Slow (UAV/Shahed) alerts now fire on the threat's confirmed fix only — a drone gliding ahead of its fix can no longer trigger a chime while it's still far outside your zone; the drawn circles and the alerts now always agree.
- Death-strike animation: nothing draws at the target before the projectile arrives — the old "ping" ring and the brief lock-on marker are gone, so nothing at the target can look like an explosion early.
- Map: "Time-to-arrival lines" now default to ON (Settings → Additional can still turn them off) — fast-threat course lines are visible from a fresh install.
- Map: guided-bomb, aviation and recon marker icons now point along the course the app reports (photo/army/comic packs) — long-pressing a marker showed them slightly off; verified against each pack's artwork.
- First-run wizard: more breathing room above the threat-icon picker, the intro note sits under the language flags, the icon previews tint to show which pack you're picking, the page-2 "Alert/Off" labels are gone (the border shows the state) with a clearer subtitle, and page 3 uses larger text.
- "Follow the bullet": the camera glides onto the strike and stays there — it no longer returns to where you were after the explosion, and the pan no longer misfires before the map is laid out.

- Settings → Threats: the expanded "Unknown" card's large preview now shows a Schrödinger's-cat image (the small row icon, map markers and alert history keep the question-mark icon).

- Map: the death-strike animation now vibrates the phone once, right at the explosion, with an alarm-class haptic pulse so it's felt even when the system "touch feedback" vibration is off — and the projectile no longer has a glowing halo around it.
- Notifications: the "Resolved threats" tally now shows only the running count plus the last threat type — the extra body line is gone.
- Settings → Threats: the Fast and Slow group sections are separated by a divider, and each expanded type card shows the selected icon pack large and full-width.
- Night mode: removed the duplicate moon icon on the mode toggle, reworded its description, and aligned the "Sound at night" caption with the toggles beneath it.

- First-run wizard / alert gating: "an alert is live" now means one relevant to *you* — a threat in your red/yellow zones or an official alert on your oblast/pinned city — instead of any alert anywhere in the country. The wizard now loads even while other regions are on alert, and still force-closes (without saving) when your own area is hit.
- System status popup: the NEPTUN logo and an underlined `neptun.in.ua` link now sit in the popup header next to the status title (were at the bottom).
- System status popup → Alerts: a "Clear" button wipes the alert history. Rows no longer show the ring duration (that was how long the siren rang, not arrival time) — only the distance. The tier dot is gone; the alert title itself is colored red/yellow/blue by tier. Alerts group by how long ago they fired (seconds/minutes/hours) with a divider between groups, and locality names transliterate to English in EN mode.

- Settings → Additional: new "Follow the bullet" sub-setting under "Resolved-threat animation" (shown only while it's on) — the camera glides onto the strike while the animation plays; turn it off to keep the camera still.
- Settings: your place is saved — every section keeps its collapsed/expanded state and the scroll position across opening/closing Settings and app restarts (the zone-panel gear still jumps straight to the relevant section).
- Settings → Threats: the " — tap to toggle" hint is gone from the title, the Map/Alerts toggles are compact icon buttons on one line, the rows are tighter, and each expanded type card now shows a large preview of its icon from the selected icon pack.
- Threat strip: tapping a footer threat type now cycles through each threat of that type (nearest first) instead of always landing on the nearest.
- A notification tap while Settings is open now closes Settings and reveals the threat on the map.
- Threat icons now face the place named in the course message ("…курсом на Київ" aims the icon at Kyiv) when the stream reports no velocity bearing or heading.

- Monitor notification is now title-only: "Alert monitoring your approx location" (or the pinned city name when a city is pinned) — the "following your GPS" subtitle is gone.
- Alert sounds are now compressed OGG files (~70 KB total, down from ~430 KB) — smaller APK, same sound.
- Settings → Threats: reference photos (bundled and hotlinked) are gone from the expanded threat cards — icons only.

- System status popup is now a bottom sheet with a scrollable body — the long alert history and connection log no longer clip off-screen.
- Settings: "Replay first launch" and "Check for updates" are no longer disabled during an active alarm — they stay available regardless of alert state. Replay opens the first-run wizard even mid-alert; the wizard still force-closes without saving if an alert goes live while it's open (and returns once the alert clears).

- Zone bells: the muted "alerts off" indicator is a plain grey bell again (no more red X) on the map pill, in the zones panel and on threat cards.
- Night mode / Zones: removed the "attacks are most common at night" notes from the night-mode description, the muted-zones warning and the feature guide — night mode is about your own schedule, not a frequency claim.

- Notifications: new "Resolved threats" tally — a silent, dismissible notification counting threats resolved near you (within each type's reach of your GPS/pin — an FPV gone 300 km away doesn't count, a Shahed does), appending as each one drops and stopping when you swipe it away. Tap it to open the map. The copy never claims interception ("resolved"/«Завершено», not "shot down"/"neutralized" — we can't know whether it hit its target). Toggle it off in Settings → Alerts.
- Map: optional "Time-to-arrival lines" (Settings → Additional, off by default) — for fast threats a red line along the course shows where the object will be at the red time threshold and a yellow one at the yellow threshold.
- Zones panel: the title now matches what it edits — "🌙 Night zones 🌙" during the night window, "Day zones" when night zones are configured but it's daytime, otherwise "Alert zones" (was always "Alert zones").
- Night mode: new "Vibration at night" toggle (off by default) with its own Fast/Slow strength sliders, used instead of the day strengths while the night window is active.
- Settings: while editing the night's custom zone sliders, each slider shows a ghost tick plus a bracketed "day N" reference so you can align the night thresholds with the day ones.

- Zones panel: the settings gear now opens Settings scrolled to the Night mode section when the night window is active, otherwise to the Threats section as before.

- Zones panel: it now edits whatever the map is currently showing — during the night window it tunes the night zones/bells (moon-flanked "Night zones" title, subtle indigo panel border, moon in the header), otherwise the day ones ("Day zones"); sliders and bells always move the rings you see.
- Zones panel: the Slow and Fast groups each sit in a subtle rounded border box; the turtle/lightning icons are vertically centered and the lightning now renders as a real emoji everywhere (no more half-bolt text glyph).
- Settings: every section (Language, Map centre, Card size, Threats, Night mode, Alerts) is now collapsible from its header.
- First-run setup is now a full-screen 4-step wizard: language + tips → icon pack → "What matters to you?" threat grid (tap a type to enable/disable its map markers and alerts together, all on by default) → a preview of the core features with a single Start button. A live alert force-closes the wizard without saving anything — it returns once the alert clears.
- Settings: "Repeat first setup" moved next to the feature guide button and reworded to «Повторити початкове налаштування» / "Replay first launch"; both it and "Check for updates" are disabled while an alert is active.
- Settings: advanced toggles (threat types, official alerts, siren override, follow me, card size, night mode) now show a one-time explainer — a visual example plus a real-life scenario — the first time they're flipped ("Got it" dismisses it forever).

- Threat strip: tapping a footer threat type pans the camera onto the nearest threat of that type (no popup), and the cells are bigger — a larger icon with the live count to its right (the pulsing underline bar is gone).
- Map: fixed a bug where a threat could glide forward along its course while its marker icon faced a wrong or pseudo-random direction (marker movement and icon facing now resolve the course identically).
- First-run setup is now a guided 4-step walkthrough: language → icon pack → tips + Fast/Slow alert groups (no more per-type toggles) → a quick tour of the core features. Settings → Additional has a "Repeat first setup" button to walk through it again (nothing else is reset).
- First-run icon-pack step + Settings → Additional: the icon picker is now four stacked full-width rows (Classic, Photos, Army, Comic), each fitting all seven icons side by side — no titles, no scrolling.
- Settings → Alerts: the vibration strength sliders now give a live preview — a short pulse at the chosen strength while you drag.
- Threat card: when a selected threat resolves, the popup now crossfades into the compact "Resolving…/Resolved" card instead of vanishing instantly, and the card fades out across the explosion.
- Death animation: the projectile flight is now 1.5s (impact comes earlier), and the bullet is painted in Ukraine colors (gold head, blue chevron).
- Death animation: real NEPTUN resolutions no longer leave the live marker on the map during the flight — the overlay renders its own copy of the icon, so the icon can't flip or change direction mid-flight.
- Death animation: threats that resolve while the map isn't visible (Settings open or the app backgrounded) no longer fire stale or "bullet to nowhere" animations on return.
- Settings → Additional: new "Resolved-threat animation" toggle — turn off the projectile/explosion flourish entirely (the resolved card no longer flips to "Resolved" and stays put until you close it). When on, the map vibrates briefly the moment the projectile detonates. If the server re-sends a resolution for an already-destroyed threat, the follow-up projectile just flies off-screen instead of exploding where the threat used to be.
- Death animation: the projectile now flies as the new bullet sprite; Settings → Additional's "Resolved-threat animation" row uses an explosion icon (was a skull).
- Settings: the night-zone sliders are grouped in subtle bordered boxes (Slow and Fast), matching the map's zones panel.
- Zone bells: the muted "alerts off" crossed-bell icon now renders reliably on the map pills and in the zones panel (it was invisible before).

- First run: the onboarding sequence is now ordered — language picker, then the "keep alerts running" battery prompt, and only after both the system location/notification dialogs appear (permission dialogs no longer jump the queue). Existing users get the battery prompt once too (skipped automatically when already exempt).
- Settings → Alerts: new "Vibration" section — independent Fast (missiles) and Slow (drones) strength sliders (Off/Soft/Medium/Strong/Urgent). Android has no per-notification vibration amplitude, so strength is expressed as a pulse pattern; official alerts without a known reason threat always ring with the strongest pattern.
- System status: new "Alerts" log below the connection log — the last 20 fired alerts (red/yellow zone sirens + official alerts), each row with the threat icon, tier dot, type, datetime, location, distance from your GPS/pin and how long it rang. Survives restarts.
- Timestamps: absolute times (connection log, alert history) now follow the app language instead of the device locale (UA "17.08, 14:30", EN "Aug 17, 14:30").
- Planned (not started): home-screen widget / lock-screen glance — see ROADMAP.md.

- Settings → Additional: the "Comic" icon set is here — a new drawn-style set of all seven threat icons, selectable in the "Threat icons" grid (the last "coming soon" placeholder is now gone, all four sets are real). Comic markers rotate along their course like the photo and army sets.
- Settings → Additional: the "Army" icon set is here — a new drawn-style set of all seven threat icons, selectable in the "Threat icons" grid (the Comic placeholder remains). Army markers rotate along their course like the photo set.

- Zones panel: slow/fast alerts are now independent — each of the four (slow red, slow yellow, fast red, fast yellow) has its own bell in the "Edit zones" sheet, so you can, e.g., mute slow yellow while keeping fast yellow. The night's custom zones get the same four independent bells.
- Zones panel: the Slow/Fast section headers now carry their icons (turtle ⚡-free = slow, lightning = fast).
- Settings: opens to the last scroll position instead of always jumping — only the "Edit zones" gear lands on the Threats section (the header gear no longer inherits that jump).
- Night mode: now ON by default and reframed as the critical window — night is when attacks are most likely, so alerts stay on. Muting a night bell shows a warning plus a pointer to "Stop Monitoring & Exit" for total silence (so you don't forget to re-enable tomorrow).
- Feature guide: a new "Night mode" card.

- Header: the NEPTUN emblem is red whenever NEPTUN is actually down; it stays amber only while the feed is alive but on the backup source.
- System status: the connection log timestamps now include the date ("MM/dd hh:mm:ss").
- Notifications: the offline notification's "Retry" action reliably restarts the connection (fixed a race where a superseded socket could swallow the manual retry, and the retry now uses a foreground-service PendingIntent so it survives service restarts on newer Android).
- Settings: the "Additional settings" section no longer shows a duplicate group header (the collapsible card keeps its own title + arrow). The icon-set tiles show just the icons (bigger), and the Army/Comic placeholders now show a gently bobbing rocket with a "Coming soon" badge instead of plain text.

- Stability: the map no longer rebuilds itself on every threat position update — marker positions, course rotation and staleness dimming now update in place, so during an active alert the map stays smooth (fixes a force-close during the death animation and a significant battery drain).
- Notifications: an official alert whose reason text arrives after the initial trigger now updates the same notification silently instead of re-ringing the siren.
- Notifications: a connection drop that starts while the app's background service is killed (and restarted by the system) is now surfaced after the restart instead of being lost.
- Connectivity: the first reconnect attempt after a drop is now near-immediate (~1-3s) instead of up to 30s, so a flapping feed recovers fast.
- Banner: with no GPS fix and no pinned city, the banner no longer claims the location is Odesa — it shows "Unknown location" instead.

- Photo threat icons: the cruise-missile photo now faces right (was top-right).

- Settings → new "Night mode" section: a separate scenario for the night. Set on/off hours (overnight windows supported), decide whether zone sirens and official alerts still ring on vibrate/silent ("always sound") during the night, and optionally give the night its own zone thresholds + armed bells. While the window is active, the map circles and zone tiers switch to the effective (night) values; the "Edit zones" sheet keeps editing the day ones and notes the night window. Alerts follow the same effective logic in the background service.

- Settings → Additional: the "Unrestricted in background" status now explains itself — once the app runs unrestricted, the card shows the same context as the request (Android won't pause it, so alerts keep ringing on very little power) instead of a bare status line.

- Notifications: an official alert no longer re-sounds every few minutes when its reason text is the same threat — the reason body updates only when a genuinely different threat becomes the reason (NEPTUN refreshes confirmation counts on the same object, which used to retrigger the siren).
- Notifications: the official-alert reason line no longer carries a dangling confirmation count (e.g. "…: 6") in the body — count-only text is stripped.

- Notifications: tapping a threat alert opens the app on that threat — the map pans so your GPS/pinned city sits near the top and the threat near the bottom (zoom matches how far away it is), and a green ring highlights the new threat for a few seconds.

- Map: zooming in now shows smaller towns across all regions (~300 more minor city labels, ~3–15 per oblast) in addition to the major cities, so you get a sense of distance/scale everywhere — not just around Odesa. Minor towns are map-context only: follow-me attribution and the banner city still resolve to major cities, and the pin picker is unchanged.

- Zones panel: the Fast/Slow group Map & Alerts toggles are gone from the bottom of the "Edit zones" sheet (they stay in Settings and the first-run dialog). Slider bands are now Slow red 2–20 km / yellow 21–50 km and Fast red 2–5 min / yellow 6–20 min, with fresh-install defaults at the top of each band (20/50/5/20).

- Threat popup: the "Middle" card size is gone — only Small and Large remain. Both cards share a larger threat icon (40dp) and a stable header (no jumping when you switch), and the Small card is a tidy 3 rows — icon + title, the distance/ETA/speed pills, then the "R" (UA "Д") reliability bar with the skull bar.
- Threats: the aviation threat type is now just "MiG-31K" (UA "МіГ-31К") — the redundant "Aviation" prefix is gone.
- Photo threat icons are now the default for everyone (users who prefer the classic vectors can switch back in Settings → Additional).
- Settings → Additional: the "Threat icons" picker is a 2x2 grid — the Classic and Photos cards each show all seven icons 25% bigger in a row that scrolls horizontally within the card, with two bordered placeholder cards below (Army, Comic). All four cards have borders. The Additional section opens expanded so the scale/icon toggles are visible, and the "Show scale" row now shows a ruler icon instead of the gear.
- System status: a new collapsible connection log shows the last 10 status changes — time, online/offline/backup, and how long each drop lasted. Drops under 30 seconds are ignored so random hiccups don't pollute the log, and the log survives app restarts. The currently-open episode shows a live running duration.
- Header: the connection pill now shows the NEPTUN emblem with a green "Online" label while the feed is healthy.
- Header: the NEPTUN emblem in the connection pill is now color-coded — green while online, red when offline, amber when on the backup source; the Offline label is red too, and the emblem is larger.
- Header + notifications: the misleading "Offline 0m" counter is gone — offline just says offline; the connection log gives the real duration.
- Zones panel: a gear in the top-right opens Settings directly on the Threats section.
- Language: English text is now fully offline. Place names (cities, oblasts, districts) are romanized — never "translated" — via the official Ukrainian transliteration rules (Золоте → Zolote, not "Gold"), and military vocabulary (UAV, Shahed, missile, heading toward…) is hard-coded. The live Google translate call is gone, so EN notifications post instantly with no network dependency.

- Map: when a threat is resolved or removed by the server, a playful 5-second "neutralized" animation plays at its last position — a soft ping marks the target, then a small projectile flies in from your GPS position (or pinned city, or just outside the screen edge when it's off-screen) and explodes on impact. If the target threat is off-screen, the map glides it back into view so the strike is always seen. The threat's icon stays on the map for the full animation and is removed for good only once it completes. While the strike plays, the popup card reads "Neutralizing enemy…"; the moment the explosion starts it flips to "Neutralized" and fades away. (Long-pressing a threat marker or empty map fires it on demand, including the card self-destruct — a temporary dev/testing aid.)
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
