# TaskNext 1.6.1

- Simplified task reminders to one **Remind me** control with the effective reminder time shown on the right. New tasks display the Settings default time automatically; tapping the time creates a task-specific override.
- Replaced the older traffic-light priority palette with modern accessible chips: Sky blue for Low, Violet for Medium, and Rose for High. Labels remain visible so priority is never communicated by color alone.
- Fixed Backlog -> Today dragging. The first backlog card now measures its real screen distance to the Today card and swaps only when the dragged card top reaches the Today card top.
- Eliminated the temporary duplicate task during Today replacement by applying the Today/Backlog membership swap in memory before Room persistence catches up.
- Persist the exact post-swap backlog order together with Today replacement, preventing stale recently-reordered positions from snapping back.
- Version: 1.6.1 (versionCode 16).
