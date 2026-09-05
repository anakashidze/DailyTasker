# TaskNext v1.5.0

## Changes

- Custom groups can now be removed from the Group dropdown. Built-in Task/Home/Health/Work groups are immutable.
- Removing a custom group automatically reassigns every task using it to the built-in **Task** group.
- The Undo snackbar uses a dedicated row layout so the message stays on the left and **UNDO** stays pinned to the right.
- Backlog dragging now reorders an in-memory list immediately and persists the final order when the drag ends, eliminating the brief old-order snap-back caused by asynchronous Room updates.
- Drag swaps are re-armed after each successful boundary crossing; no offset compensation occurs when there is no neighboring task to swap with.
- Non-dragged rows animate into place faster (170 ms).
- Home navigation swipes are more sensitive (42 dp threshold).
- Swipe left on empty/unconsumed Task List space returns Home; swipe right on Settings returns Home. Child task swipe gestures retain priority.
- Navigation transitions are shortened to 60–80 ms.
- `Add new group…` is now `Add new group`.

No Room schema migration is required for this release.
