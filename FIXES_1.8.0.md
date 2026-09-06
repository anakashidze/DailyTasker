# TaskNext v1.8.0

- Rebuilt Home/Tasks/History horizontal navigation as one persistent three-page surface to remove route flashes, dead mid-swipe states, and release handoff glitches.
- History back button and Android back return to Home.
- Added daily streak tracking. Only completing the actual Today task counts; one completion per local day.
- Added streak display to Home and the 2x1 Today widget.
- Added Overdue state. An unfinished prior-day Today task becomes Overdue on local-day rollover, while the queued/top backlog task becomes the new Today task.
- Added an Overdue section/tag in Tasks with edit, swipe-complete, swipe-delete, and Undo behavior.
- Database migration 5 -> 6 adds overdueSinceEpochDay without wiping existing tasks.
