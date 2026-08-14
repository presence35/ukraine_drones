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
- The server `version.json` is generated from `app/version.properties` (versionCode/versionName) plus `app/notes_en.txt` / `app/notes_ua.txt`. FTP creds live in `app/upload.properties` (git-ignored).
- Version numbers: `versionCode` is a monotonic integer; `versionName` is human-readable. Keep both bumped together (the `bumpVersion` task does this).
