# Chess Tutor (Android)

An offline-first, verified tactical chess tutor and sparring application for Android built with Jetpack Compose, Room, and a local UCI chess engine with win-probability move classification, ECO opening practice, and Chess.com / Lichess game review.

---

## 1. Setup & Build Instructions

### Prerequisites
- **JDK**: JDK 17 or JDK 21
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`
- **NDK (for building Stockfish from source)**: Android NDK r26d+ (`COMP=ndk` with embedded NNUE networks)

### Build & Test Commands
```bash
# Run the JVM unit and verification test suite
gradle :app:testDebugUnitTest

# Assemble the debug APK
gradle :app:assembleDebug
```

---

## 2. Product Structure & Navigation Contract

The app is organized around four tabs connected by typed requests (`TrainRequest` and `PlayRequest`):

1. **Learn**: Curriculum library covering Openings, Tactics, Middlegame, Endgame, and Blunder Patterns. Progress is driven strictly by solved attempts in Train. Every lesson links into Train via `TrainRequest.Lesson(topicId)`.
2. **Train**: Interactive session player with a 4-step hint ladder, `PuzzlePhase` state machine (`SOLVING`, `CORRECT`, `WRONG`, `REVEALED`), auto-orientation to side to move, and spaced-repetition mistake drills.
3. **Play**: Free play and opening practice against calibrated bots (White, Black, or Random colour) with live ECO opening recognition, "Learn the line" and "Start from opening" modes, verified coaching banners, takeback, and resign.
4. **Review**: Public, read-only account connection for **Chess.com** and **Lichess** (games import, W/D/L, Score %, Wilson confidence-adjusted per-opening performance, ranked insights with deep links into Learn/Train/Play, move-by-move game review, and spaced-repetition mistake queue).

---

## 3. Native Engine & Licensing

- **Native Packaging**: `jniLibs/arm64-v8a/libstockfish.so` and `jniLibs/x86_64/libstockfish.so` are extracted to `context.applicationInfo.nativeLibraryDir` (`useLegacyPackaging = true`, `android:extractNativeLibs="true"`).
- **Process Execution**: `StockfishProcessEngineClient` spawns the binary over standard UCI pipes (`uci` → `uciok`, `isready` → `readyok`, `UCI_ShowWDL`) and automatically falls back to the local Kotlin engine (`LocalFallbackEngineClient`) when running on desktop JVM tests or if the native binary cannot execute.
- **License**: Distributed under the **GNU General Public License v3.0 (GPL-3.0)**. See `LICENSE` and `NOTICE`.
