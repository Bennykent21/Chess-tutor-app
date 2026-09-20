# Chess Tutor (Android)

An interactive, tactical chess tutor and sparring engine for Android powered by Jetpack Compose, Room, and a bundled official Stockfish 19 UCI engine with CCRL-calibrated ELO tuning and online rating integration.

---

## 1. Setup & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug / Meerkat (or newer)
- **JDK**: JDK 17 or JDK 21 (Gradle toolchain compatible)
- **Android SDK**:
  - `compileSdk = 36`
  - `targetSdk = 36`
  - `minSdk = 24` (Android 7.0 Nougat or higher)
- **NDK (for Stockfish cross-compilation)**:
  - Pinned NDK version: **r26d** (or 25.2.9519653+)
  - Toolchain: Clang with Android API 26 target headers (`aarch64-linux-android26-clang++`, `x86_64-linux-android26-clang++`)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/Bennykent21/Chess-tutor.git
cd chess-tutor

# Run unit and local verification tests
./gradlew test --no-configuration-cache

# Build the debug APK
./gradlew assembleDebug --no-configuration-cache

# Install on an active device or emulator via adb
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Android Native Packaging Configuration
In `app/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```
And in `AndroidManifest.xml`:
```xml
<application
    android:extractNativeLibs="true"
    ... >
```
*Note*: `useLegacyPackaging = true` and `android:extractNativeLibs="true"` ensure that native binaries in `jniLibs` are extracted directly to the app's private sandbox folder (`context.applicationInfo.nativeLibraryDir`) where the OS permits standard ELF execution.

---

## 2. Architecture Overview

The codebase is organized in clean architectural layers:

```
app/src/main/java/com/chesstutor/app/
├── domain/                  # Pure Kotlin chess rules, movegen & tactical algorithms
│   ├── ChessPosition.kt     # FEN parsing, board state, move legality, SAN generator
│   ├── TacticalAnalysis.kt  # Static Exchange Evaluation (SEE), hanging pieces, fork detection
│   └── ReviewScheduler.kt   # Spaced repetition scheduler (SuperMemo SM-2 interval algorithm)
├── engine/                  # UCI protocol & engine process management
│   ├── EngineClient.kt      # EngineClient interface & AnalysisRequest/Result contracts
│   ├── StockfishProcessEngineClient.kt # Real subprocess management, stdin/stdout UCI pipes, setElo
│   ├── LocalFallbackEngineClient.kt    # In-memory heuristic evaluator (safety fallback)
│   ├── UciProtocol.kt       # Pure parser for UCI info/bestmove lines
│   └── BlunderClassifier.kt # Centipawn drops & mate-horizon blunder taxonomy
├── data/                    # Local storage and network lookup
│   ├── model/               # Domain models (LinkedChessProfile, RatingPlatform, etc.)
│   ├── local/               # Room Database, DAOs, and entities (ReviewItem, LinkedProfile)
│   ├── network/             # RatingApiClient (Moshi + OkHttp for Chess.com & Lichess REST APIs)
│   └── repository/          # ReviewRepository and RatingRepository implementations
├── viewmodel/               # Presentation state management
│   ├── AppViewModel.kt      # MVVM orchestrator for Coach, Arena, Reviews, and Ratings
│   └── AppUiState.kt        # Immutable UI state (live FEN, annotations, profile, bot tuning)
└── ui/                      # Jetpack Compose UI (Material Design 3)
    ├── navigation/          # AppNavHost, bottom navigation bar
    ├── components/          # ChessBoard, EvaluationBar, AcademyCard
    ├── coach/               # CoachScreen with interactive blunder feedback and hint reveals
    ├── arena/               # ArenaScreen, RatingLinkCard, difficulty presets, live play
    └── review/              # ReviewScreen for spaced repetition mistyped positions
```

---

## 3. How to Run Tests

### Running JVM Tests (Desktop / CI)
The primary unit and integration test suite runs on the desktop JVM using Gradle:
```bash
./gradlew test --no-configuration-cache
```
Or to run only the primary specification verification test:
```bash
./gradlew test --no-configuration-cache --tests com.chesstutor.app.ChessTutorSpecVerificationTest
```

### Test Target Distinction: JVM vs. Real Android Device

| Test Suite / Target | Environment | What is Tested | What is NOT Tested |
|---|---|---|---|
| `ChessTutorSpecVerificationTest` | **Desktop JVM** (`x86_64`) | • Pure Kotlin chess logic (SEE, forks, move legality)<br>• UCI line parsing & blunder classification<br>• Rating API DTO serialization & ViewModel state flows<br>• Real Stockfish process execution using the desktop **x86_64** ELF binary | • Does **not** verify Android SELinux security context<br>• Does **not** execute the Android `arm64-v8a` binary<br>• Does **not** test `nativeLibraryDir` permission flags on real hardware |
| **On-Device Diagnostic Execution** | **Android Device / Emulator** (`arm64-v8a` or Android emulator) | • Verifies APK extraction of `libstockfish.so` into `nativeLibraryDir`<br>• Tests `ProcessBuilder` execution under Android app sandbox permissions<br>• Tests real phone thermal/battery performance during UCI search | • Requires physical device or Android emulator with adb or emulator streaming |

---

## 4. Engine Bundling & Native Binaries

### Native Binaries in `assets/stockfish`
Real Stockfish binaries are bundled inside:
- `app/src/main/assets/stockfish/arm64-v8a/libstockfish.so` (Target: Android 64-bit ARM devices)
- `app/src/main/assets/stockfish/x86_64/libstockfish.so` (Target: Android x86_64 emulators)

`StockfishBinaryProvider` copies the architecture-matched binary from the APK assets into the app's private files directory and the process client launches that copied executable. The app therefore does not depend on `nativeLibraryDir` for Stockfish execution.

### Why Named `.so`?
Android's package manager extracts files from the APK into `nativeLibraryDir` if they adhere to the `lib<name>.so` naming convention. `AppContainer.kt` inspects `context.applicationInfo.nativeLibraryDir + "/libstockfish.so"`, ensures execute permissions, and launches Stockfish via `ProcessBuilder`. If the process fails or the architecture is unsupported, the system gracefully falls back to `LocalFallbackEngineClient`.

### GPL-3.0 Compliance & Licensing Notice
Stockfish is distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
- **Process Boundary**: The application communicates with Stockfish strictly via standard input/output pipes using the open UCI (Universal Chess Interface) protocol as an independent subprocess.
- **Distribution Notice**: Any public release or APK distribution containing the bundled Stockfish executable **should**:
  1. Include the full GNU GPL-3.0 license text and copyright notices in the application's Open Source Licenses section.
  2. Provide access to the exact source code corresponding to the bundled Stockfish build, available at: [https://github.com/official-stockfish/Stockfish](https://github.com/official-stockfish/Stockfish).

---

## 5. Features & Current State

### Implemented & Verified
- [x] **Full Chess Domain Engine**: FEN parser, pseudo-legal and legal move generator, SAN notation generator, castling, en passant, promotion, and check/checkmate detection.
- [x] **Tactical Analysis (SEE & Forks)**: Static Exchange Evaluation (SEE) to detect hanging pieces and geometric double-attacks/forks.
- [x] **Centipawn & Mate Blunder Taxonomy**: move-quality classification at the application analysis boundary.
- [x] **Subprocess Stockfish Engine**: Real UCI process runner with automatic recovery, fallback safety, and CCRL-calibrated `setElo()`.
- [x] **Online Rating Linking**:
  - Read-only public lookups for **Chess.com** (`GET api.chess.com/pub/player/{username}/stats`) and **Lichess** (`GET lichess.org/api/user/{username}`).
  - User selection of preferred time-control format (**Rapid**, **Blitz**, or **Bullet**).
  - Room database persistence for offline caching with on-demand refresh.
  - Automatic `setElo()` feeding to tune Stockfish against the user's approximate rating.
  - Strict UI copy compliance: *"Tuned to approximate your [Platform] [Format] rating"*.
  - Preset bot tiers fallback (Beginner, Casual, Intermediate, Advanced) for unlinked users.
- [x] **Spaced Repetition Review System**: staged 1/3/7/14/30-day review scheduling with Room persistence for blunder drills.

### Roadmap / Intentionally Out of Scope for Now
- **User Accounts & Cloud Sync**: Firebase/Supabase synchronization across multiple devices.
- **PGN Game Import**: Parsing entire PGN game records for batch post-mortem reviews.
- **General Strategic Explanations**: Deep natural-language positional explanations beyond tactical motifs.
- **Visual & UI Polish**: Drag-and-drop board gestures, piece animation curves, tablet two-pane layout, light theme tuning.
