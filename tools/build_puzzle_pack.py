#!/usr/bin/env python3
"""
Build a compact, curated puzzle pack for the app from the Lichess puzzle database.

Source: https://database.lichess.org/#puzzles  (lichess_db_puzzle.csv.zst, CC0)

Usage:
    zstd -dc lichess_db_puzzle.csv.zst | python3 build_puzzle_pack.py - -o puzzles.tsv.gz
    python3 build_puzzle_pack.py lichess_db_puzzle.csv -o puzzles.tsv.gz --per-cell 6

Input columns (Lichess):
    PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags

Output: gzip'd TSV, one puzzle per line, sorted by rating then id:
    id <TAB> fen <TAB> moves <TAB> rating <TAB> primaryTheme <TAB> themes <TAB> popularity

IMPORTANT (Lichess convention): `fen` is the position BEFORE the opponent's move.
`moves` is a space-separated UCI list; the FIRST move is the opponent's move that the
app must play automatically, then the player has to find moves[1], moves[3], ... while
the opponent replies with moves[2], moves[4], ... The pack keeps that raw format.
"""
import argparse
import csv
import gzip
import sys
from collections import defaultdict

# Ordered by teaching priority: the first one that matches becomes the puzzle's
# "primary theme", so every puzzle is stored once and cells stay balanced.
CORE_THEMES = [
    "mateIn1", "mateIn2", "mateIn3",
    "backRankMate", "smotheredMate", "anastasiaMate", "arabianMate", "hookMate",
    "fork", "pin", "skewer", "discoveredAttack", "doubleCheck",
    "hangingPiece", "trappedPiece", "capturingDefender",
    "deflection", "attraction", "clearance", "interference", "xRayAttack",
    "intermezzo", "quietMove", "zugzwang", "sacrifice", "exposedKing",
    "defensiveMove", "advancedPawn", "promotion",
    "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame",
]

MIN_RATING, MAX_RATING, BUCKET = 400, 2600, 200


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("input", help="CSV path, or - for stdin")
    p.add_argument("-o", "--output", default="puzzles.tsv.gz")
    p.add_argument("--per-cell", type=int, default=6,
                   help="max puzzles per (primary theme, rating bucket) cell")
    p.add_argument("--min-popularity", type=int, default=90)
    p.add_argument("--min-plays", type=int, default=500)
    p.add_argument("--max-deviation", type=int, default=90)
    p.add_argument("--max-moves", type=int, default=9, help="max UCI moves in the line (keeps puzzles short)")
    return p.parse_args()


def main():
    args = parse_args()
    src = sys.stdin if args.input == "-" else open(args.input, newline="", encoding="utf-8")
    reader = csv.DictReader(src)

    cells = defaultdict(list)  # (theme, bucket) -> [(popularity, plays, row)]
    seen = kept_candidates = 0
    for row in reader:
        seen += 1
        try:
            rating = int(row["Rating"])
            dev = int(row["RatingDeviation"])
            pop = int(row["Popularity"])
            plays = int(row["NbPlays"])
        except (KeyError, ValueError):
            continue
        if not (MIN_RATING <= rating < MAX_RATING):
            continue
        if pop < args.min_popularity or plays < args.min_plays or dev > args.max_deviation:
            continue
        moves = row["Moves"].split()
        if len(moves) < 2 or len(moves) > args.max_moves:
            continue
        themes = row["Themes"].split()
        primary = next((t for t in CORE_THEMES if t in themes), None)
        if primary is None:
            continue
        key = (primary, (rating // BUCKET) * BUCKET)
        bucket = cells[key]
        bucket.append((pop, plays, row))
        kept_candidates += 1
        # Trim as we go so memory stays small even on the 4M-row file.
        if len(bucket) > args.per_cell * 4:
            bucket.sort(key=lambda x: (-x[0], -x[1]))
            del bucket[args.per_cell:]

    out_rows = []
    for (theme, _), bucket in cells.items():
        bucket.sort(key=lambda x: (-x[0], -x[1]))
        for pop, _, row in bucket[:args.per_cell]:
            out_rows.append((
                int(row["Rating"]), row["PuzzleId"], row["FEN"], row["Moves"],
                theme, row["Themes"], pop,
            ))
    out_rows.sort(key=lambda r: (r[0], r[1]))

    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n") as f:
        for rating, pid, fen, moves, theme, themes, pop in out_rows:
            f.write("\t".join([pid, fen, moves, str(rating), theme, themes, str(pop)]) + "\n")

    per_theme = defaultdict(int)
    for r in out_rows:
        per_theme[r[4]] += 1
    print(f"read {seen:,} rows, {kept_candidates:,} passed filters, wrote {len(out_rows):,} puzzles -> {args.output}")
    for theme in CORE_THEMES:
        if per_theme[theme]:
            print(f"  {theme:<18}{per_theme[theme]:>6}")


if __name__ == "__main__":
    main()
