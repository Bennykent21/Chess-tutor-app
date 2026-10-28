# Chess Tutor (Android)

An offline-first, verified tactical chess tutor and sparring application for Android built with Jetpack Compose, Room, and a local UCI chess engine with win-probability move classification, ECO opening practice, and Chess.com / Lichess game review.

---

## 1. Setup & Build Instructions

### Prerequisites
- **JDK**: JDK 17 or JDK 21
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`
- **NDK (only to build Stockfish yourself)**: Android NDK r27c or newer, on Linux or WSL. CI does this for you; see section 3.

### Build & Test Commands
```bash
# Run the JVM unit and verification test suite (macOS / Linux)
./gradlew :app:testDebugUnitTest

# Run the JVM unit and verification test suite (Windows)
gradlew.bat :app:testDebugUnitTest

# Assemble the debug APK
./gradlew :app:assembleDebug

# Assemble the release APK
./gradlew :app:assembleRelease
```

### Installing test builds from GitHub Actions

Every push builds two APKs for 64-bit ARM phones (arm64-v8a, every modern phone), in the Actions run's *Artifacts* section:

- `chess-tutor-debug-apk`: installs as **Chess Tutor Debug** (`io.github.bennykent21.chesstutor.debug`).
- `chess-tutor-release-apk`: the minified (R8) build, installs as **Chess Tutor**. It is signed with the test key below, so it is for testing only. **Never upload it to Google Play.**

The two are separate apps with separate data, so both can be installed at once. The app's version name (`0.1.<run number>`) tells you which Actions run you are on.

**One-time setup, so updates install over the previous build.** Android only updates an app when the new APK has the same signature, so CI needs a fixed test key, stored as a repository secret. On Windows (PowerShell), using Android Studio's own debug key (create it by building any debug app once in Android Studio if it does not exist):

```powershell
# Optional check: the file should exist and list "androiddebugkey"
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android

# Copy the key, base64-encoded, to the clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore")) | Set-Clipboard
```

Then on GitHub: repository **Settings > Secrets and variables > Actions > New repository secret**, name `CI_DEBUG_KEYSTORE_B64`, paste, save. If the secret is missing, CI still builds but warns, and its APKs will not install over earlier builds.

---

## 2. Product Structure & Navigation

The app is being rebuilt in phases (see `plan.md`). The shell has four tabs, built with Navigation Compose, each with its own back stack:

1. **Home**: the play hub. Quick match works now; custom bots, Opening Battle and the daily challenge arrive in Phase 3.
2. **Learn**: six category buttons with progress. Until the new lesson pages (Phase 4) they open the classic lesson list on that category.
3. **Puzzles**: the puzzle trainer and mistakes review. The level path and modes arrive in Phase 2.
4. **Account**: profile, games and review, settings, engine health, the design-system gallery and a list of every classic screen.

**Classic screens:** the pre-revamp screens (lesson and drill player, lesson list, Play, Review, puzzle trainer) open full screen through `LegacyHost`, with a back button, and keep their old behaviour until each is replaced.

**Design system v2** lives in `app/src/main/java/com/chesstutor/app/design/`: colours (`Palette`, contrast-tested), type (Schibsted Grotesk headings, Inter body), shapes, motion that respects "Reduce motion", and the core components. Settings are stored with DataStore (`data/settings/`).

---

## 3. Native Engine & Licensing

- **Native packaging**: `libstockfish.so` is extracted to `context.applicationInfo.nativeLibraryDir` (`useLegacyPackaging = true`) and run as a child process.
- **Process client**: `StockfishProcessEngineClient` talks UCI over pipes. Positions are checked first (`EngineFenGate`), because Stockfish 19 exits on any position it rejects. If Stockfish fails, the request is answered by the online engine, then the built-in Lite engine; after two failures in a session it stops retrying and the app shows a "Lite engine" badge. Bot strength is applied per request, so analysis always runs at full strength.
- **Engine**: Stockfish 19 with its neural network embedded, built from the pinned official source by `tools/build_stockfish_android.sh` (Android 7+ / API 24, 16 KB page aligned, checked before install). CI builds it once and caches it; the binaries are too large for git and are not committed.
- **Local builds**: download the `stockfish-android` artifact from any successful Actions run and copy `arm64-v8a/libstockfish.so` and `x86_64/libstockfish.so` into `app/src/main/jniLibs/`. Without them the app still runs, on its Lite engine, and Account > Engine health says so.
- **Health check**: Account > Engine health (or Settings > Engine check) reports which engine answered, the speed Stockfish itself measured, and whether its network loaded. Nothing in that report is estimated.
- **Third-party assets**: see `LICENSES.md`.
- **License**: Distributed under the **GNU General Public License v3.0 (GPL-3.0)**. See `LICENSE` and `NOTICE`.
