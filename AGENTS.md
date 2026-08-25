# Ukraine Drones — Release Workflow

## Trigger phrase: "release it"

When the user says **"release it"**, perform a full release:

1. Read the `## [Unreleased]` entries in `CHANGELOG.md`.
2. Infer the new version from `app/version.properties`.
3. Write EN + UA release notes to `app/notes_en.txt` / `app/notes_ua.txt` (UTF-8).
4. Show the notes + inferred version and wait for confirmation ("go"). Upload only after confirmation.
5. Run the single command (no args — the version auto-bumps its patch, e.g. 0.3.8 → 0.3.9; use `-PnewVersion=<ver>` only for an explicit override):
   - `.\gradlew.bat :app:release`
6. Verify the live result at `https://odesaplay.com.ua/other_apps/ukrainedrones/version.json` (version + both translations).
7. Move the released entries under a new `## [<ver>]` heading in `CHANGELOG.md` and clear `## [Unreleased]`.

## While working

- Append user-visible changes to `CHANGELOG.md` under `## [Unreleased]` as you go, so any session can release them.
- Changelog entries are short one-liners: `- Area: change / Область: зміна` (EN sentence, then UA after ` / `). No multi-paragraph essays.
- The server `version.json` is generated from `app/version.properties` (versionCode/versionName) plus `app/notes_en.txt` / `app/notes_ua.txt`. FTP creds live in `app/upload.properties` (git-ignored).
- Version numbers: `versionCode` is a monotonic integer; `versionName` is human-readable. Keep both bumped together (the `bumpVersion` task does this).

## Development conventions

Read `ARCHITECTURE.md` before exploring the codebase — it has the full module map (every
source file with its one-line responsibility), the data-flow pipeline, and the key invariants.
Use it to jump straight to the right file instead of re-reading the codebase.

### Coding conventions

- Minimal patches; don't rewrite whole files for small changes.
- Don't add comments unless asked.
- Dark-only theme; never add a light theme.
- UA/EN text goes through `Strings` (`Strings.get(lang).StringSet`), not Android resource
  localization.
- User settings/prefs go through `ZonePrefs` (DataStore-backed); don't add a second prefs store.
- **Mirror rule**: `MainViewModel` (UI) and `AlertService` (notifications) each reimplement the
  zone/focus/alert logic. Any change to `zoneTier`, `ZoneParams`, `reachKm`,
  `focusAttribution`, `staleAfterMs`, or `predictPosition` must be applied in
  **both** files — see `ARCHITECTURE.md#key-invariants`.

### Always build/verify before finishing

After a meaningful code change, verify before declaring the task done:

- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:testDebugUnitTest` — when touching domain logic
  (`Prediction`/`Zones`/`ThreatLevel`/`Threat`/`UpdateManager`).

Fix any failures before finishing.

### Never paste full logs or data blobs

- Summarize build output, errors, and stack traces rather than dumping them into the chat.
  Point to `file:line` or the saved tool-output file instead of reproducing it inline.
- Don't paste large API/JSON payloads or long log dumps into responses.

### Keep ARCHITECTURE.md current

When you add a source file or change a documented invariant, update the module map /
key-invariants section of `ARCHITECTURE.md` in the same change, so the docs never rot.
