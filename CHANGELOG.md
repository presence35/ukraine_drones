# Changelog

## [Unreleased]

- First-run wizard: the three tips on the language step now use bigger icons and larger text, and the threat cards on the second step use compact one-line labels (Cruise / Guided / Recon — «Крилата» / «КАБ» / «Розвідка») so all cards are the same height.
- Settings → Alerts: the per-zone vibration strength sliders (Fast/Slow) and the separate night-vibration sliders are gone — zone alerts vibrate at a fixed strong level, official alerts stay urgent.
- Small threat card: redesigned — only the time-to-arrival and distance pills remain (speed pill dropped), and the "R" (reliability) and threat-level (skull) indicators are now horizontal bars under the pills.
- Alerts-off bells: the crossed grey "off" bell on the threat popup now sits on a subtle grey chip so it's visible on the dark card instead of vanishing into it.
- Zones panel + Settings: the Fast/Slow group headers now use real vector icons — a green turtle for slow threats and a lightning bolt for fast — instead of emoji (which also fixed the turtle rendering below the text baseline).

- Map: dragging the slow-zone slider while the zones panel is open now re-fits and recentres the map so the new yellow circle fills the visible map section above the panel.
- Map: the bottom-left "Pinned: <city>" pill is gone — the header already names the pinned city, so the pill was redundant.
- Notifications: an official-alert notification body now always states an official alert is on (e.g. "Official alert in Odesa region" / «Офіційна тривога в Одеській області»), appending the specific reason when there is one, instead of a bare region name or a standalone reason text.

- Fix: opening a threat card while the small card size is selected and the map is pinned to a city no longer crashes (the distance tooltip passed a string to an integer format slot).

- Debug log: a new full-screen audit trail (opened from the System-status popup) logs every alert and threat decision in your region — official alerts on/off, threats entering a red/yellow zone and threats in the region that stayed quiet — with day/night, the effective sound setting, the vibration level that would have been used, whether a notification actually fired, and why not when it didn't (bell muted, already notified, another alert posted first, type alerts off, observation threat, stale, outside alert zones, notifications off). Every row carries an "ago" timestamp plus the absolute time, and the log is a rolling 24-hour window (last 500 events, older rows drop off). Rows are color-coded cards with a leading icon (red trident = official alert on, green check = all-clear, red/amber warning = entered red/yellow zone, blue pin = "Threat in \<region\>", gray close = left) and "Threat in region" now names the actual oblast/locality.

- Map: shelter markers are now bigger hand-drawn teardrop pins (thicker stroke, larger tap area) and tapping a shelter no longer pans/zooms the camera onto it — the map stays put, only its card opens.
- Shelters: the first-taps tip toast now reads "Tap a shelter to see its info" / «Торкнись укриття, щоб побачити інформацію» instead of the long-press hint.
- Settings: sections stay user-collapsible even while a search is active (they used to auto-expand every match); searching "english"/"українська"/"language" now finds the language row.
- Settings: the search box now matches curated keywords, not raw screen text — "threat"/"drone"/"missile"/"recon" (and every threat type's name in UA and EN) hit the Threats card without "night vibration…" wording falsely matching "threat".
- Settings: the header search field is now a slim filled pill — no more big outline box swallowing the header — with a little bottom padding so the disclaimer card isn't flush against it; the related/did-you-mean chips now slide in under the header as you type.
- Settings: search suggests related options as tappable chips ("You might also look for…" / «Можливо, ви також шукаєте») — e.g. "тихо"/"quiet" → Sirens always sound / Vibration / Night mode; "dark"/"темний" → Night mode — plus "Did you mean" chips for near-miss typos ("сирина" → сирена) when nothing else matches.
- Settings: the search box shrinks into a search button in the header while you scroll the list, so it's always one tap away; tapping it brings the full box back and focuses it.
- Settings → Night mode: the whole section card (header included) is tinted the darker purple instead of just an inner panel.
- Settings → Alerts: the battery-exemption block moved here from System with much shorter wording ("Battery exemption" / «Фонова робота», "Allow" / «Дозволити»).
- Icon packs: the "Classic" pack was removed — only Photo, Army, Comic and Russian remain; anyone who had Classic selected falls back to Photo automatically.
- Settings → System: "Reset all tips" is now a button (was a row) that re-arms every first-use hint (toggle toasts + the one-time explainers).
- Settings → Location: periodic GPS sync is now a sub-setting of "Follow me" — it only appears while following, so the cell-tower-vs-GPS choice shows exactly when GPS is in use.
- Settings → Location: the "Calibrate GPS" button now shows a "finding you" toast while it acquires a precise fix.
- Notifications: tapping an alert notification now reliably reveals the threat — the reveal-carrying intent uses its own PendingIntent request code so the plain status/tally/milestone intents can't wipe its extras (which left the app stuck on whatever screen you were on).
- Notifications: the ongoing status notification now reads "Monitoring GPS" / «Моніторинг GPS» when following, or "Monitoring Odesa" / «Моніторинг Одеса» (pinned city) when pinned — was a long sentence or the bare city name.

- Notifications: the resolved-threats tally now counts only threats resolved in your oblast (or the pinned city's oblast) by default — the old per-threat reach radius is gone; a new "All of Ukraine" sub-setting in Settings → Alerts counts resolved threats anywhere in the country.
- Settings: the shelter button toggle and the shelter directory moved out of the Alerts section into their own dedicated "Shelter" section card.

- Shelter walk-time icons are now man/woman silhouettes (adult randomly a man or a woman; a child appears beside them when "with kids" is on), sized at their true aspect ratio with the child standing on the same line as the adult. The "With kids" toggle slot is a fixed compact width; the list rows and map popup fit their figures so the "Open in maps" button never wraps.
- Shelter GPS header wording updated: "GPS fix is…" → "Precise GPS: …" (EN) / "Точний GPS: …" (UA).
- Map shelters: markers have a much larger tap target; shelter mode no longer auto-hides when a new threat appears (it stays until you toggle the Shelter button, tap a threat, or tap empty map); pressing the Shelter button zooms to fit the full nearby-shelter range with a buffer; opening the shelter list from Settings and pressing back returns to Settings (from the map it still returns to the map).
- Updates: the "Update v… is available" snackbar no longer shows a doubled "vv", and it can now be swiped away.
