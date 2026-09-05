# TaskNext 1.2.3

## Completion invariant fix

Completed tasks now follow one rule regardless of where completion is triggered:

1. A snapshot is inserted into `completion_log` (History).
2. The live task is archived (`isArchived = true`, `slot = null`).

Those two database changes run inside one Room `@Transaction`, so they succeed or fail together.
3. Its reminder is disabled and any queued-tomorrow reference is cleared.
4. Because all active DAO queries filter `isArchived = 0`, the task immediately disappears from Home, Today, Bonus and Backlog lists.

This applies to:
- completing Today from Home;
- completing a Bonus task from Home;
- completing any task from the task-list swipe action.

An idempotency guard prevents an already archived task from being logged a second time by a stale UI callback.

## Existing-data cleanup

The Room database is now version 3. Migration `2 -> 3` archives any live task whose ID already exists in `completion_log`. This removes tasks that were completed under older versions but incorrectly returned to the active list, while keeping their History records.
