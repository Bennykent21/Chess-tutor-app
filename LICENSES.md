# Bundled assets and their licences

Chess Tutor is free software under the GNU General Public License v3.0 (`LICENSE`).
This file records every third-party asset the app ships, so the licence of each one is
known before release (plan.md, section 9). Add a row whenever an asset is added.

| Asset | Where in the repo | Source | Licence |
|---|---|---|---|
| Stockfish 19 chess engine | `app/src/main/jniLibs/*/libstockfish.so` (built by CI, not committed) | Official source, tag `sf_19`, commit `edb0d9db6731067ec50ce619ff372b463bc4dd5d`, built by `tools/build_stockfish_android.sh` | GPL-3.0 |
| Stockfish neural network `nn-1a298aa575a0.nnue` | Embedded inside `libstockfish.so` | Published by the Stockfish project for Stockfish 19; downloaded and SHA-256 checked by Stockfish's own build script | Distributed as part of the GPL-3.0 Stockfish binary. Confirm the networks repository's terms before the first store release |
| Nunito (Bold, ExtraBold, Black) | `app/src/main/res/font/nunito_*.ttf` | Google Fonts release, version 3.602 | SIL Open Font License 1.1 (`licenses/OFL-1.1.txt`) |
| Inter (Regular) | `app/src/main/res/font/inter.ttf` | Inter 4.001 | SIL Open Font License 1.1 (`licenses/OFL-1.1.txt`) |
| JetBrains Mono | `app/src/main/res/font/jetbrains_mono.ttf` | JetBrains Mono 2.211 | SIL Open Font License 1.1 (`licenses/OFL-1.1.txt`) |
| Puzzle pack | `app/src/main/assets/puzzles.tsv.gz` | Lichess open puzzle database, built by `tools/build_puzzle_pack.py` | CC0 1.0 (public domain dedication) |
| Opening names and lines | `app/src/main/java/com/chesstutor/app/domain/OpeningBook.kt` | Based on `lichess-org/chess-openings` | CC0 1.0 |
| Mascots (Pip, Rookie, Sage) | Sources: `design/mascot/pip.svg`, `rook.svg`, `turtle.svg`. In the app: `app/src/main/res/drawable/mascot_pip.xml`, `mascot_rookie.xml`, `mascot_sage.xml`, generated from the SVGs by `tools/mascot_svg_to_vector.py` | Original artwork made for this project | GPL-3.0, with the app |
| Design system colours and components | `app/src/main/java/com/chesstutor/app/design/` | Original, made for this project | GPL-3.0, with the app |
| Launcher icon | `app/src/main/res/drawable/ic_launcher_*.xml`; the `mipmap-*/ic_launcher*.webp` files (Android 7 only) are rendered from those by `tools/render_launcher_icons.py` | Origin of the vector art not recorded | **To confirm** before release, or replace with original art |

Sounds are generated in code today (`ChessSoundManager`), so no sound files are bundled.
When a sound pack or piece sets are added (Phase 1), record them here first.

This is a record, not legal advice.
