launcher-v0.2-live-actions-exact.patch

This patch was generated against the exact MainActivity.kt and
AndroidManifest.xml content you supplied.

Run from the repository root:

    git apply --check launcher-v0.2-live-actions-exact.patch
    git apply launcher-v0.2-live-actions-exact.patch

Then build:

    .\gradlew.bat :launcher:assembleDebug

Notes:
- Apps opens Android Settings temporarily.
- Climate100 package names remain assumptions and may need verification later.
- The patch was validated with git apply --check before packaging.
