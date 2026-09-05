# TaskNext v1.4.1

Build-fix release for v1.4.0.

- Fixed `HistoryScreen.kt`: moved `remember { SimpleDateFormat(...) }` out of the `LazyColumn` content builder so it runs in a composable context.
- Fixed `TaskListScreen.kt`: added the missing `androidx.compose.foundation.shape.CircleShape` import.
- No database schema or task-behavior changes from v1.4.0.
- Version bumped to 1.4.1 / versionCode 12.
