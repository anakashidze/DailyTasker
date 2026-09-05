# TaskNext 1.1.1 build fixes

Fixes based on GitHub Actions Kotlin compiler output:

- Removed ambiguous `AnimatedVisibility` calls inside the nested Column/Box receiver hierarchy in `HomeScreen.kt`.
- Added `@OptIn(ExperimentalMaterial3Api::class)` to `HistoryScreen`, `SettingsScreen`, and `TaskListScreen` for Material 3 `TopAppBar`.
- Bumped version to 1.1.1 / versionCode 3.

Re-run the GitHub Actions workflow **Build Android APK** after uploading this version.
