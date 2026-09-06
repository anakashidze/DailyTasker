# TaskNext v1.7.3

- Removed Home swipe-to-Settings; Settings remains available from the Settings button.
- Forward swipes now settle from the finger-release position and keep the outgoing Home page off-screen until Navigation replaces it, eliminating the one-frame flash/restart.
- History and Task List back swipes now move the entire page (including top bar) with the finger and settle from the release position.
- Committed back swipes keep the outgoing page off-screen until popBackStack disposes it, preventing the previous-page flash.
- Task List page-back gestures are blocked when the touch starts inside any task card. Task-card left/right gestures remain exclusively Complete/Delete.
- Page-back remains available from Backlog/section titles, gaps between tasks, outer padding, and empty space.
- Re-applied the exact user-uploaded launcher artwork as a true PNG Android resource, without reconstructing the logo in vectors.
