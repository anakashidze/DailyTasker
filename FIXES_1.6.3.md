# TaskNext 1.6.3

- Home left/right/up swipes now behave like an interactive pager: the destination screen is visible and follows the finger instead of navigating immediately when the threshold is crossed.
- Releasing a qualifying left/right/up swipe completes the transition; otherwise Home animates back to its original position.
- Pull-down no longer behaves like a page swipe. A circular progress control fills while pulling; only a fully armed pull opens Add Task on release. Releasing early does nothing.
- Pull-to-add gives haptic feedback when the release threshold is reached.
- Add new group now automatically focuses the Group name text field and opens the keyboard.
- The custom group icon picker keeps every existing icon, adds car plus more general-purpose icons, and lays the choices out in two horizontally scrollable rows.
- Includes the updated launcher artwork with extra adaptive-icon safe-zone padding so Samsung launcher masks do not crop the symbol.
- Version bumped to 1.6.3 / versionCode 18.

No Room schema migration is required for this release.
