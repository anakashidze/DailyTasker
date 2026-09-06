# TaskNext v1.7.4

- Dragging no longer changes Backlog/Today/Bonus membership before finger release.
- Removed mid-drag Backlog list mutations that could make a dragged task jump away from the finger.
- Backlog reorder is calculated once from the final release position.
- Bonus -> Backlog inserts at the release position instead of retaining an old orderIndex.
- Today -> Backlog is now a release-position drop and promotes the current top Backlog task to Today.
- Added a Bonus star action to the Today task when a Bonus slot is available.
- Today -> Bonus promotes the top Backlog task to Today when possible.
- Batch task updates use Room list updates to reduce transient intermediate list states.
