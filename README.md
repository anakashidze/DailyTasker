# TaskNext

TaskNext is a small Android task-focus app built around one mandatory task per day,
optional bonus tasks, a rotating backlog, completion history, and reminders.

## Android compatibility

- `minSdk = 26`
- `targetSdk = 35`
- `compileSdk = 35`
- Android Gradle Plugin `8.6.1`
- Gradle `8.7` in the included GitHub Actions workflow
- JDK `17` for Android builds

The app can run on a Galaxy S22 running Android 16. It targets API 35, so Android 16
uses compatibility behavior for target-specific API 36 changes.

## Current behavior

- One mandatory task can complete the day.
- Up to two optional bonus tasks can be active at once.
- Completing a task logs it to History and moves it to the far end of the backlog,
  so the queue cycles instead of immediately suggesting the same task again.
- After the mandatory task is completed, you can explicitly choose tomorrow's task.
  The chosen task is moved to the bottom/next position of the visible backlog.
- If **Auto-pick the next backlog task for tomorrow** is enabled, the picker is skipped
  and the next backlog item is queued automatically.
- Day rollover uses the phone's local calendar date and is checked when the app opens
  or resumes.
- Reminders are scheduled only for active tasks (mandatory/bonus), not every backlog item.
- A task can use the global default reminder time or a custom time.
- Reminder notifications include a Snooze action using the configured snooze duration.
- Reminder alarms are rebuilt after reboot, app replacement, and time/time-zone changes.
- If Android does not grant exact-alarm access, TaskNext falls back to an inexact
  idle-safe alarm instead of crashing.
- The in-app Notifications switch now enables/disables scheduled reminder alarms.
- Dark theme changes immediately from Settings.

## Build with GitHub Actions

This project intentionally does not include a Gradle wrapper. The included workflow
installs the required Gradle version itself.

1. Upload the contents of this folder to the root of a GitHub repository.
2. Open **Actions**.
3. Run **Build Android APK**.
4. Open the completed run.
5. Download the **TaskNext-debug-apk** artifact.
6. Extract it and install `app-debug.apk` on the phone.

The workflow also runs on pushes to the `main` branch.

## Important testing points

Before relying on the app, test these on the actual phone:

1. Create several tasks and confirm completion rotates the backlog correctly.
2. Complete the mandatory task and choose a non-default task for tomorrow.
3. Leave the app overnight, then reopen/resume it and confirm tomorrow's task becomes active.
4. Test reminders with exact-alarm access both granted and not granted.
5. Reboot the phone and confirm an active task reminder is restored.
6. Disable Notifications in TaskNext and confirm reminders stop.
7. Test Snooze from a notification.

## Remaining limitations

- There are no automated unit/instrumentation tests yet.
- Queue reordering uses arrow buttons rather than drag-and-drop.
- The launcher icon is still a placeholder.
- The project does not include a Gradle wrapper.
- GitHub debug APK signing is not configured with a persistent private signing key. If APKs
  from different build machines use different debug keys, Android may require uninstalling
  the old build before installing the new one. Use stable release/debug signing before you
  depend on in-place upgrades without data loss.
