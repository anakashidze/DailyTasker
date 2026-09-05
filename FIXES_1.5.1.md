# TaskNext 1.5.1

- GitHub Actions: upgrade `actions/upload-artifact` from v4 to v6 so the action runs natively on Node.js 24.
- Launcher icon: replace the busier foreground with a centered, text-free Task check that flows into a Next arrow, on a blue-to-indigo gradient background.
- Drag reorder: preserve the in-memory order after a drag until Room confirms the same persisted id order. Starting another long-press no longer reloads a stale previous order and cannot move tasks before the finger moves.
- Version: 1.5.1 (versionCode 14).
