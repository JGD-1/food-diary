# Food diary

A private food-tracking Android app for two people (room for more later).

- **Plan and decisions:** kept in the Claude project (`foodapp/plan.md`, `decisions.md`, `conventions.md`).
- **Install:** open the latest release on your phone (Releases, on the right of this page) and tap the `.apk` file.

## For developers

- Kotlin + Jetpack Compose, Hilt, Room. Minimum Android 10.
- `./gradlew testDebugUnitTest :core:model:test` runs the tests; `./gradlew :app:assembleRelease` builds the APK.
- Every push and pull request is built by `.github/workflows/build.yml`.

| Folder | What it holds |
|---|---|
| `app/` | Start-up, navigation, wiring |
| `core/model` | Shared data types and interfaces (plain Kotlin) |
| `core/data` | Database on the phone and repositories |
| `core/ui` | Colours, theme, shared components, screen names |
| `feature/*` | One folder per work stream |
| `build-logic/` | Shared build settings |
| `signing/` | The app's signing key (private repository only) |

NEVO food data: "Based on data from NEVO online version 2025/9.0, RIVM, Bilthoven."
