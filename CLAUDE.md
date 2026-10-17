# CLAUDE.md

Personal offline-first Android workout logger. Claude writes training programs into Airtable; the app logs workouts and syncs.

- **Source of truth for requirements & vocabulary:** `docs/FOUNDATION.md`. Use its entity names exactly (Routine, Exercise, Session, Set).
- **Self-contained repo:** all project notes, decisions, and memory live in this repo (this file or `docs/`), never in Claude's external memory directory. Record durable decisions here or in `docs/`.
- **Public repo** (GitHub user `greg7gkb`): never commit secrets. The Airtable token lives in `local.properties` (gitignored).
- **Keep it small:** single `app` module, minimal dependencies, no DI framework.
- **Lint before committing:** `./gradlew :app:lintDebug :app:lintRelease` must pass. Lint is strict (all checks, warnings are errors) and catches API-level problems such as calls above minSdk 29.

## Testing on an emulator

- Claude uses its **own** headless emulator: AVD `Pixel_7_API_35_-_Google_APIs_No_Google_Play_` on port 5560 (serial `emulator-5560`). Start it with `emulator -avd <AVD> -port 5560 -no-window -no-audio -no-boot-anim -no-snapshot-save`.
- **Leave it running** between tasks; don't kill it after each test.
- **Never touch other emulators/devices** except as below. Always set `ANDROID_SERIAL=emulator-5560` for `adb` and `./gradlew installDebug` while testing.
- **Keep the user's emulator live:** `emulator-5554` is the user's (used from Android Studio). After finishing and verifying an app change, install and relaunch it there so the user always sees the latest code:
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug && adb -s emulator-5554 shell am start -n io.github.greg7gkb.iworkout/.ui.MainActivity`.
  **Only** install and launch. No taps, no network or setting changes, and no test data on that device.
