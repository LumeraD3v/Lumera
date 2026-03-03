# Lumera - Development Notes

## Database Migrations
- **NEVER use `fallbackToDestructiveMigration()` on upgrades** — users lose all their data (addons, profiles, hub rows, watch progress)
- When changing the database schema, ALWAYS write a proper `Migration` object
- Current database version: 28 (in `LumeraDatabase.kt`)
- Migrations are registered in `DatabaseModule.kt` via `.addMigrations()`
- Steps for schema changes:
  1. Bump `version` in `LumeraDatabase.kt`
  2. Write a `Migration(oldVersion, newVersion)` with the SQL in `DatabaseModule.kt`
  3. Add it to `.addMigrations(...)` in the database builder

## Build & Release
- Release APK is built via Android Studio: Build > Generate Signed Bundle / APK
- Signing keystore is stored outside the project (never commit it)
- Local `.aar` files live in `playbackcore/libs/` but are referenced from `app/build.gradle.kts` (not from `playbackcore/build.gradle.kts`) to avoid AAR-in-AAR build errors
- ABI filters: only `arm64-v8a` and `armeabi-v7a` (no x86/x86_64)
- GitHub Releases: tag format is `vX.Y.Z-beta`, APK name `Lumera-vX.Y.Z-beta.apk`

## Auto-Update System
- `AppUpdateManager` checks GitHub Releases API (`LumeraD3v/Lumera`)
- Compares release `tag_name` (stripped of `v` prefix) against `BuildConfig.VERSION_NAME`
- Release `body` is shown as changelog in the update dialog
- Release assets must include a `.apk` file
- Update popup is gated on: splash finished + not dismissed + popup preference enabled

## Git / GitHub
- Do NOT include co-author lines in commits
- GitHub repo: `LumeraD3v/Lumera`
- Main branch: `main`

## Project Structure
- `app/` — main application module (Kotlin, Jetpack Compose for TV)
- `playbackcore/` — Media3/ExoPlayer wrapper library module
- Architecture: MVVM with Hilt DI
- Database: Room (encrypted preferences via SharedPreferences)
