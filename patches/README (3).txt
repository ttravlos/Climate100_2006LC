launcher-v0.3-phone-bluetooth-youtube-music.patch

Changes:
- Phone searches installed launcher apps for the exact label "Bluetooth".
- If no Bluetooth launcher app is found, Phone falls back to ACTION_DIAL.
- Music searches for the exact label "YouTube Music".
- Package fallback order is YouTube Music, Spotify, then legacy Google Music.

Apply from the repository root:

    git apply --check launcher-v0.3-phone-bluetooth-youtube-music.patch
    git apply launcher-v0.3-phone-bluetooth-youtube-music.patch
    .\gradlew.bat :launcher:assembleDebug

This patch was validated with git apply --check against the current
AppLauncher.kt produced by the v0.2 supplemental patch.
