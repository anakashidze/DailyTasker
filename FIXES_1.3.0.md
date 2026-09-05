# TaskNext 1.3.0

Interaction-focused update for the task list.

- Swipe right completes immediately; swipe left deletes immediately.
- Removed completion/delete confirmation dialogs from task-list swipe actions.
- Added a centered bottom Undo snackbar for 15 seconds (`Task completed` / `Task deleted`).
- Undo completion restores the exact task and removes the latest completion-history snapshot created by that action.
- Undo delete restores the original task id, state, queue position and tomorrow reservation when applicable.
- Removed the visible drag-handle icon.
- Long-press anywhere on a draggable task card to pick it up; haptic feedback confirms the grab.
- The dragged card follows the finger 1:1 vertically.
- Reordering waits until the card has travelled approximately one full measured row plus list spacing before swapping.
- The dragged item stays above surrounding items while non-dragged rows animate into their new positions.
- Increased reorder animation duration to 300 ms for clearer movement.
- Increased Low / Medium / High priority-chip contrast.
