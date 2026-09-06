# TaskNext

TaskNext is a focused Android task app built around one main task per day, optional bonus tasks, a draggable backlog, completion history, and reminders.

## Current version

- Version name: `1.6.1`
- Version code: `15`
- minSdk: `26`
- targetSdk / compileSdk: `35`
- Android Gradle Plugin: `8.6.1`
- GitHub Actions Gradle: `8.7`
- JDK: `17`

## Home gestures

- Swipe left → History
- Swipe right → Task List
- Swipe up → Settings
- Pull down → Add Task

Return gestures:
- History: swipe right → Home
- Task List: swipe left → Home (on unconsumed/empty list space)
- Settings: swipe down at the top of the page → Home

## Task flow

- One mandatory Today task.
- Up to two Bonus tasks.
- Long-press backlog tasks to drag/reorder them.
- Drag the first backlog task upward to replace Today.
- Drag Today downward into Backlog; the top backlog task becomes Today.
- Drag Bonus downward to return it to Backlog.
- Swipe right on a task to complete it, with a 15-second Undo.
- Swipe left to delete it, with a 15-second Undo.
- Completed tasks disappear from active lists and remain in History.

## History

Tap a History row to see:
- priority
- group
- comment
- exact completion date/time

History supports deleting individual records or clearing all records.

## Groups

Built-in groups are Task, Home, Health and Work. Custom groups can be added and removed. Removing a custom group reassigns its tasks to the immutable Task group.

## Build with GitHub Actions

Upload the project contents to the root of a GitHub repository, then use **Actions → Build Android APK**. The workflow builds and verifies the APK with `zipalign`, `aapt2`, and `apksigner` before uploading the artifact.

Artifact name: `TaskNext-v1.6.2-verified-apk`.

The included debug keystore is for consistent testing builds only; it is not a production Play Store signing key.
