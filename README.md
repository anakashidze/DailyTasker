# TaskNext

TaskNext is a focused Android task app built around one main task per day, optional bonus tasks, a draggable backlog, completion history, and reminders.

## Current version

- Version name: `1.7.0`
- Version code: `20`
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

Left/right/up navigation now follows the finger: the destination page is visible during the drag and the gesture finishes only after release. A short gesture that does not meet the navigation threshold snaps Home back into place.

Pull-down is intentionally different: a progress control fills as you pull. Release only after it is full to open Add Task; releasing early does nothing.

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

Built-in groups are Task, Home, Health and Work. Custom groups can be added and removed. Removing a custom group reassigns its tasks to the immutable Task group. The custom-group icon picker keeps all previous icons, adds car and more general-purpose choices, and displays them in two rows. Opening Add new group focuses the group-name field immediately.

## Build with GitHub Actions

Upload the project contents to the root of a GitHub repository, then use **Actions → Build Android APK**. The workflow builds and verifies the APK with `zipalign`, `aapt2`, and `apksigner` before uploading the artifact.

Artifact name: `TaskNext-v1.7.0-verified-apk`.

The included debug keystore is for consistent testing builds only; it is not a production Play Store signing key.


## Home-screen widget

TaskNext includes a compact 2x1 **Today** widget for the Android home screen. It uses a rounded blue-to-indigo gradient, shows the current Today task (or day-complete/empty state), and opens TaskNext when tapped. The widget refreshes when Today changes.
