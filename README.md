# Chess Tutor

**A chess teacher on your phone.** Play bots that feel like people, learn from lessons that let you move the pieces, solve thousands of puzzles, and get every game reviewed by Stockfish 19, all on your phone, offline, with no account and no ads.

[![Android CI](https://github.com/Bennykent21/Chess-tutor-app/actions/workflows/android.yml/badge.svg)](https://github.com/Bennykent21/Chess-tutor-app/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/Bennykent21/Chess-tutor-app?label=download)](https://github.com/Bennykent21/Chess-tutor-app/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

## What it does

### Play

- **12 bots with their own style and voice**, from Pebble (250), who is still learning how the pieces move, to Titan, a dragon rated 2500. Between 1100 and 1900 the bots move like people of that rating, using the Maia networks, so their mistakes look human.
- **Build your own bot**: pick its strength, style and favourite openings.
- **Quick match** finds a fair opponent; you can also play on from any position, drill an opening against a bot (Opening Battle), explore a position with the engine, or watch two bots play each other.
- Clocks, takebacks, hints and an evaluation bar, each one your choice.

### Learn

- **38 courses and 73 lessons** across the basics, openings, tactics, the middlegame, endgames and the blunders everyone makes. You learn by moving the pieces on the board, with a coach beside it.
- An **opening trainer** that drills the lines you want to play, from a database of about 3,900 named openings.

### Puzzles

- **13,485 puzzles** from the Lichess puzzle database, with a puzzle rating that follows how you solve.
- The daily puzzle, a level path, Rush (3 minutes, 5 minutes or three lives), puzzles by theme, and Custom run, where you choose the rating, the pattern, how many and a timer.
- **Your own mistakes come back as puzzles**: positions from your games where you went wrong return on a spaced schedule until you get them right.

### Your games and progress

- **Link Chess.com or Lichess** (just your username, no password) to bring in your games, rapid, blitz and bullet.
- **Stockfish 19 reviews each game on the phone**: accuracy, each move's grade, the key moments, and the better move each time.
- Stats for each time control: a rating graph, records, results, how your games end and how your openings score. A Training page shows your puzzle rating, streaks and the themes to work on. Badges and levels mark the way.

### Make it yours

- Four colour themes (Mono, Parchment, Baize, Slate), each in light and dark.
- Board colours and four piece sets, sounds, vibration, Reduce motion, and a choice of coach.

## Install

1. On your Android phone, open the [latest release](https://github.com/Bennykent21/Chess-tutor-app/releases/latest) and download the `.apk` file.
2. Open it. Android asks once to allow installs from your browser or file manager; allow it, then tap **Install**.
3. To update, install the newer `.apk` from the same page over the old one. Your progress stays.

Needs Android 7.0 or newer on a 64-bit ARM phone (almost every phone made since 2017). The app is large because Stockfish 19 and its neural network are inside it, so it can run with no connection.

Each release lists the SHA-256 checksum of its APK, so you can check the file you downloaded.

## How to use it

- **New to chess?** On first start, choose "I'm new to chess" and Learn opens at the basics.
- **Already play?** Choose "I play online", then link Chess.com or Lichess on Account. Your games arrive, your rating sets the bots and puzzles to your level, and each game can be reviewed.
- **Every day:** solve the daily puzzle on Home, play a quick match, and clear the mistakes waiting in Puzzles.
- **After a game:** tap **Review this game** on the result. The review shows where the game turned and what to play instead; tap a key moment to see it on the board.

## Privacy

Everything you do stays on your phone: no account, no ads, no analytics and no server of our own. The app goes online only:

- to read your public games and ratings from **Chess.com** or **Lichess**, when you link an account; and
- if the on-device engine ever fails to start, to send the **position being analysed** (and nothing else) to a public online Stockfish service (chess-api.com or stockfish.online), so analysis keeps working.

## Build from source

You need JDK 17 or 21 and the Android SDK (compile SDK 36). Gradle downloads the rest.

```bash
./gradlew assembleDebug                  # build the debug APK (gradlew.bat on Windows)
./gradlew testDebugUnitTest lintDebug    # run the tests and lint
```

Stockfish is not in the repository (about 95 MB per CPU type). A build without it runs on the app's simple built-in engine and says so in Settings > Engine. To include it, either:

- download the `stockfish-android` artifact from any [CI run](https://github.com/Bennykent21/Chess-tutor-app/actions) and copy `arm64-v8a/libstockfish.so` and `x86_64/libstockfish.so` into `app/src/main/jniLibs/`; or
- build it yourself on Linux or WSL with the Android NDK r27c: `bash tools/build_stockfish_android.sh app/src/main/jniLibs`.

Every push builds test APKs in GitHub Actions. A tag such as `v1.0.0` builds the signed APK and publishes it as a release; see [RELEASING.md](RELEASING.md).

### How it is built

- **Kotlin and Jetpack Compose**, Material 3, Navigation Compose; Room and DataStore for what is saved.
- **Stockfish 19** runs as a separate process and talks UCI. If it cannot start, an online engine answers, then a small built-in engine, and the app says which one is working.
- **Maia** networks run on the phone for the human-like bots.
- The code is grouped by feature (`feature/home`, `feature/learn`, `feature/puzzles`, `feature/account`) on top of the board (`board/`), the rules of chess (`core/`), the design system (`design/`) and the data layer (`data/`). More than 700 unit tests cover the rules, the engine's use, reviews, imports and every screen.

## Credits

Chess Tutor stands on open work: [Stockfish](https://stockfishchess.org) (GPL-3.0), the [Lichess](https://lichess.org) puzzle database and [opening names](https://github.com/lichess-org/chess-openings) (CC0), [Maia Chess](https://maiachess.com) (GPL-3.0), piece sets by Colin M.L. Burnett, Armando Hernandez Marroquin, Alexis Luengas and Maurizio Monge, sounds by [Kenney](https://kenney.nl) (CC0), the Inter, Schibsted Grotesk and JetBrains Mono fonts (OFL), and a chess set model and lighting from [Poly Haven](https://polyhaven.com) (CC0). [LICENSES.md](LICENSES.md) lists every bundled asset and its licence; the app shows the same list under Settings > About.

## Licence

Chess Tutor is free software under the [GNU General Public License v3.0](LICENSE). You may use, study, share and change it; if you share a changed version, share its source under the same licence.
