launcher-v0.6-app-diagnostics.patch

Temporary diagnostic behavior:
- Tapping Phone no longer tries to launch the phone app.
- It opens a scrollable dialog listing every launchable app visible to the launcher.
- Each entry shows:
    visible app label
    package name
    activity class name

Use:
1. Apply and build.
2. Install/update the launcher APK on the CC3.
3. Tap Phone.
4. Find the entry labeled Bluetooth.
5. Photograph or transcribe that entry, especially package and activity.

Apply from the repository root:

    git apply --check launcher-v0.6-app-diagnostics.patch
    git apply launcher-v0.6-app-diagnostics.patch
    .\gradlew.bat :launcher:assembleDebug

This patch assumes v0.2 through v0.5 have already been applied.
