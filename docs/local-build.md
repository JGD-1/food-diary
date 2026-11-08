# Building on Guido's PC (since 8 Nov 2026)

GitHub (JGD-1) is suspended, so the code, the checks and the releases live on Guido's Windows PC.

## Where things are
- Code: `C:\Users\guido\food-diary` (a git repo with no remote). Keep it in a path **without spaces**, because KSP/Room fails with "Processor arguments not in the format \S+=\S+".
- Releases: `C:\Users\guido\food-diary-releases` (`food-diary-0.1.N.apk`, `latest.apk`, `version.json`, `build-number.txt`).
- Tools: Git, Temurin JDK 17, Android SDK in `%LOCALAPPDATA%\Android\Sdk` (platform 35, build-tools 35.0.0, platform-tools).

## Everyday commands (PowerShell, in the code folder)
- Tests + APK: `powershell -ExecutionPolicy Bypass -File tools\local\build.ps1` (on `main` it also releases the next version).
- Home update server: `powershell -ExecutionPolicy Bypass -File tools\local\serve.ps1` (port 8787, home network only).
- Refresh the Open Food Facts list: `powershell -ExecutionPolicy Bypass -File tools\local\off-snapshot.ps1`, then commit the asset on a branch.

## Restoring from a backup bundle
The history is shallow (it starts in early October). After `git init` + `git bundle unbundle <file>` (or a clone), write
`e1397ca6fd508fcf39265c0338128f3e1e2d7bdf` into `.git/shallow`; without it clone/fsck report a missing parent.

## Decisions (also for foodapp/decisions.md)
- 2026-11-08 Built locally on Guido's PC (GitHub suspended): tools/local/build.ps1 replaces build.yml (tests, Room schema commit, APK; on main the next version from ~/food-diary-releases/build-number.txt, starting at 0.1.300), off-snapshot.ps1 replaces off-snapshot.yml; camera-device-tests not replaced. #79 merged locally. (lead)
- 2026-11-08 Round 13 updater, local hosting: serve.ps1 shares version.json + APKs read-only on the home network, port 8787; the app checks once a day and on "Zoek naar update", address in Profile > Updates (default 192.168.178.67); socket client limited to home-network addresses instead of a network security config; APK verified (size, SHA-256, package, versionCode) before install. (lead, update)
- 2026-11-08 Round 12 G1 "Opscheppen voor iedereen" rebuilt locally (#80 was lost with GitHub): NEW_BATCH route arg dishUp=true, feature/recipes only. (recipes)
