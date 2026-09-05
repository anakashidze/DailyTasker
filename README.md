# TaskNext

TaskNext is a focused Android task app built around one mandatory task per day,
optional bonus tasks, a rotating backlog, completion history, and reminders.

## Android compatibility

- `minSdk = 26`
- `targetSdk = 35`
- `compileSdk = 35`
- Android Gradle Plugin `8.6.1`
- Gradle `8.7` in the included GitHub Actions workflow
- JDK `17` for Android builds

The app is intended to run on a Galaxy S22 running Android 16. It targets API 35,
so Android 16 uses compatibility behavior for target-specific API 36 changes.

## Task flow

- One mandatory **Today** task can complete the day.
- Up to two optional **Bonus** tasks can be active at once.
- The backlog is shown in queue order with the next task at the top.
- Drag backlog tasks up/down by the drag handle to reorder them.
- Drag the first backlog task upward again to replace the current Today task.
- Drag a Bonus task downward to return it to Backlog without deleting it.
- Swipe a task left to request deletion confirmation.
- Swipe a task right to request completion confirmation. A backlog task can be completed
  directly without first becoming Today.
- Completing a task logs it to History and moves it to the far end of the backlog.
- A task can be explicitly chosen for tomorrow. If no explicit choice exists, the top
  backlog task is the next suggestion.

## Editing and reminders

- Preset colors use human-readable names.
- Custom colors can be named and selected with RGB sliders.
- Priority chips use pastel colors: Low blue, Medium yellow, High red.
- Title/comment fields request sentence capitalization from the Android keyboard.
- Default and custom reminder times use a time picker rather than editable hour/minute fields.
- Reminder notifications support Snooze.
- Reminder alarms are rebuilt after reboot, app replacement, and time/time-zone changes.
- If Android does not grant exact-alarm access, TaskNext falls back to an inexact idle-safe alarm.

## Build with GitHub Actions

1. Upload the contents of this folder to the root of a GitHub repository.
2. Open **Actions**.
3. Run **Build Android APK**.
4. Open the completed run.
5. Download the **TaskNext-debug-apk** artifact.
6. Extract it and install `app-debug.apk` on the phone.

The workflow also runs on pushes to the `main` branch.

## Debug signing

Version 1.2.0 includes a stable testing-only debug keystore in the project so future GitHub
builds are signed consistently and can update one another in place. It is not a production
Play Store signing key.

If the APK currently installed on the phone came from an earlier GitHub runner, it may have
been signed by a different temporary debug key. In that case Android will require uninstalling
the older build once before installing v1.2.0. From v1.2.0 onward, keep the included
`app/debug.keystore` unchanged so subsequent testing builds can update normally.

## Important testing points

1. Confirm the status-bar icons are dark and visible in light theme.
2. Drag backlog items up/down and confirm their order persists.
3. Drag the first backlog item upward and confirm it replaces Today while the old Today task
   returns to the backlog.
4. Drag a Bonus task down and confirm it returns to Backlog without being deleted.
5. Swipe left and cancel/confirm deletion.
6. Swipe right and complete a backlog task directly.
7. Create/edit named and custom colors.
8. Test Low/Medium/High priority styling.
9. Test default and task-specific reminder time pickers.
10. Reboot the phone and confirm an active reminder is restored.

## Remaining limitations

- There are no automated unit/instrumentation tests yet.
- Drag reordering is intentionally handle-based and stepwise rather than a free-floating list animation.
- The launcher icon is still a placeholder.
- The project does not include a Gradle wrapper; GitHub Actions installs Gradle 8.7 explicitly.
