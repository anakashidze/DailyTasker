# TaskNext 1.2.0

Usability revision for Samsung Galaxy S22 / Android 16.

## Home
- Uses dark status-bar/navigation-bar icons in light theme and light icons in dark theme.
- Changed `also today` to `Also today`.

## Task list
- Removed the large up/down reorder buttons.
- Added a compact drag handle.
- Backlog is shown with the next task at the top.
- Drag backlog tasks up/down to reorder.
- Drag the first backlog task upward to replace the current Today task.
- Drag a Bonus task downward to return it to Backlog without deleting it.
- Swipe left -> asks for permanent deletion.
- Swipe right -> asks to mark completed. Backlog tasks can be completed directly without first becoming Today.
- TODAY/BONUS/NEXT/TOMORROW are fixed single-line pills placed immediately left of Edit.
- Priority is displayed as a pastel badge: Low blue, Medium yellow, High red.

## Add/Edit task
- Named preset colors replace visible hex codes.
- Added Custom color with a user-defined name and RGB sliders.
- Added first-letter/sentence capitalization hints for the Android keyboard.
- Replaced numeric reminder hour/minute fields with a 24-hour time picker.

## Settings
- Replaced editable default hour/minute fields with a time picker.
- Snooze input can be cleared and retyped normally.

## Data
- Database schema version 2 adds `colorName` with a migration from v1; existing tasks are preserved.
