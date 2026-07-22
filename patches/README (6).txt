launcher-v0.7-file-diagnostics.patch

Built against the exact project uploaded in Climate100_2006LC.zip.

Behavior:
- Tapping Phone writes a diagnostics report to:
    Download/LC100Launcher_Diagnostics.txt
- A toast appears when writing starts.
- A second toast confirms success or reports an error.
- The file is overwritten when possible, rather than creating a new report each time.

Report contents:
- Device and Android information
- All normal launcher activities
- A filtered section matching:
    bt, blu, phone, dial, call, teyes, syu
- Every installed package visible to the launcher
- Package labels, versions, system-app status
- Launch activities
- Returned activities with exported/enabled status

Other changes:
- Adds android.permission.QUERY_ALL_PACKAGES
- Removes the temporary v0.6 popup list
- Sets launcher versionCode to 7
- Sets launcher versionName to 0.7-dev

Apply from the repository root:

    git apply --check launcher-v0.7-file-diagnostics.patch
    git apply launcher-v0.7-file-diagnostics.patch
    .\gradlew.bat :launcher:assembleDebug

After installing:
1. Tap Phone.
2. Wait for "Saved Download/LC100Launcher_Diagnostics.txt".
3. Find the file in the CC3 Download folder.
4. Copy it to USB and upload it.
