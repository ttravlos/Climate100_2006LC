launcher-v0.4-climate100-dev.patch

Change:
- Climate button now launches only:
    com.ttravlos.lc100climate.dev
- Removes the previously guessed Climate100 package names.
- Missing-app message now says "Climate100 Dev is not installed".

Apply from the repository root:

    git apply --check launcher-v0.4-climate100-dev.patch
    git apply launcher-v0.4-climate100-dev.patch
    .\gradlew.bat :launcher:assembleDebug

This patch assumes the v0.3 Phone/Bluetooth and YouTube Music patch
has already been applied.
