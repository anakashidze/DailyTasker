# TaskNext 1.7.0

- Fixed the v1.6.4 Compose compile error by removing restricted `Animatable.snapTo/animateTo` calls from the pointer-event scope.
- Home left/right/up destination pages follow the finger directly during the swipe; navigation commits on release when the existing threshold rule is met.
- Pull-down keeps its fill-to-arm Add Task indicator and only opens Add Task when released after filling.
- New Task dialogs autofocus Title and show the keyboard.
- Add new group autofocuses Group name and explicitly shows the keyboard.
- Custom group icon chooser stays in two rows and adds more general-purpose icons without removing existing ones.
- Added a compact 2x1 Today home-screen widget with rounded blue-indigo gradient and tap-to-open behavior.
- Widget refreshes when Today state changes.
