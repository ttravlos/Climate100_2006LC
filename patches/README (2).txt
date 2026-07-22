This supplemental patch adds the missing AppLauncher.kt file.

Run from the repository root:

    git apply --check launcher-v0.2-AppLauncher-missing-file.patch
    git apply launcher-v0.2-AppLauncher-missing-file.patch
    .\gradlew.bat :launcher:assembleDebug

Cause: the previous patch generator used git diff without staging the new file,
so Git omitted AppLauncher.kt even though MainActivity.kt referenced it.
