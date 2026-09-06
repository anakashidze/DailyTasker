# Build TaskNext APK on GitHub

This project is configured to build a debug APK with GitHub Actions.

## Requirements
- A GitHub account
- The contents of the `TaskNext` folder uploaded to a GitHub repository

## Repository root
The repository root must contain these items directly:

- `.github/`
- `app/`
- `build.gradle.kts`
- `gradle.properties`
- `settings.gradle.kts`
- `README.md`

Do not upload the outer ZIP or an extra parent folder around these files.

## Build
1. Open the repository on GitHub.
2. Open **Actions**.
3. Select **Build Android APK**.
4. Choose **Run workflow** -> **Run workflow**.
5. Wait for the build job to finish successfully.
6. Open that workflow run.
7. Under **Artifacts**, download **TaskNext-v1.7.0-verified-apk**.
8. Extract the downloaded artifact ZIP.
9. The APK inside is `app-debug.apk`.

The workflow uses JDK 17, Android API 35, Android Build Tools 35.0.0, Gradle 8.7, and Android Gradle Plugin 8.6.1.

## Installing on Samsung Galaxy S22
Transfer `app-debug.apk` to the S22, open it, and allow the specific source app (browser/file manager) to install unknown apps when Android asks. Then install TaskNext.

This is a debug APK intended for personal testing. It is not a Play Store release build.
