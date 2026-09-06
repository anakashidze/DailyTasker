# TaskNext 1.6.0

- Home navigation is now four-directional:
  - swipe left → History
  - swipe right → Tasks
  - swipe up → Settings
  - swipe down → Tomorrow
- History returns Home with swipe right.
- Settings returns Home with swipe down when the page is already at the top.
- Tomorrow accepts a vertical swipe to return Home.
- Added a dedicated Tomorrow screen showing the queued/suggested next task.
- History rows are tappable and show priority, group, comment, and exact completion date/time.
- History snapshots now preserve priority/group/icon/comment at completion time.
- Database migration 4 → 5 enriches existing History records from archived task data where available.
- Today's task can be long-pressed and dragged down into Backlog. The current top backlog task becomes Today automatically when one exists.
- Improved backlog-order synchronization when pool membership changes during/after a drag.
- Added the approved centered TaskNext launcher artwork as the adaptive icon source.
- Navigation transitions shortened further.
