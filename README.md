# i-workout

A small, offline-first Android app for logging strength workouts. Routines are written by Claude into Airtable; the app pulls them, lets you log Sessions in the gym with no signal, and syncs the logs back.

- Requirements, vocabulary, and decisions: [docs/FOUNDATION.md](docs/FOUNDATION.md)
- Airtable tables and field IDs: [docs/airtable-schema.md](docs/airtable-schema.md)
- How data moves on and off the phone (diagram): [docs/data-flow.md](docs/data-flow.md)

## Setup

1. Open the project in Android Studio (or use `./gradlew` with JDK 17+).
2. Create an Airtable personal access token at <https://airtable.com/create/tokens>:
   - scopes: `data.records:read`, `data.records:write`
   - access: the **i-workout** base only
3. Add it to `local.properties` (gitignored; never commit it):
   ```
   airtable.token=pat...
   ```
4. `./gradlew installDebug` with your phone connected.

**Release build** (R8-shrunk, about 3 MB instead of about 34 MB, signed with the debug key for sideloading):
`./gradlew assembleRelease`, then install `app/build/outputs/apk/release/app-release.apk`.

## Tests

`./gradlew :app:testDebugUnitTest`: about 22 JVM tests in about 5 seconds, no emulator needed. They cover:
- the Airtable data contract (Sets JSON and Summary)
- the pre-fill rules
- Room queries and sync bookkeeping (under Robolectric)
- database upgrades from every past schema version
- push and pull against a fake Airtable (MockWebServer)

## Stack

Kotlin · Jetpack Compose · Room · WorkManager · Retrofit + kotlinx.serialization. Single `app` module, no DI framework.
