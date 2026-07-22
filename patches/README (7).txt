launcher-v0.8-bluetooth-phone.patch

Built against launcher v0.7 from the exact project ZIP previously uploaded.

Changes:
- Phone now opens the TEYES Bluetooth application directly:
    package: com.syu.bt
    activity: com.syu.bt.BtAct
- Falls back to the package launch intent if the explicit activity fails.
- Removes the temporary diagnostics writer and Phone-button diagnostics action.
- Removes QUERY_ALL_PACKAGES.
- Adds package visibility entries for:
    com.syu.bt
    com.ttravlos.lc100climate.dev
- Updates launcher version:
    versionCode 8
    versionName 0.8-dev

Apply from the repository root after v0.7 is applied:

    git apply --check launcher-v0.8-bluetooth-phone.patch
    git apply launcher-v0.8-bluetooth-phone.patch
    .\gradlew.bat :launcher:assembleDebug

The patch passed git apply --check against the reconstructed v0.7 source tree.
