#!/usr/bin/env python3
"""
Build the app's puzzle pack (v2) from the Lichess puzzle database, and find every move
that also wins (plan section 6.3.5) with Stockfish, so a correct alternative is never
marked wrong.

Source: https://database.lichess.org/#puzzles  (lichess_db_puzzle.csv.zst, CC0)
Engine: official Stockfish 19 for your computer (GPL-3), used only at build time.

Usage (Windows, from the repository root):
    python tools/build_puzzle_pack.py tools/.cache/lichess/lichess_db_puzzle.csv.zst ^
        --stockfish tools/.cache/stockfish/stockfish/stockfish-windows-x86-64-universal.exe ^
        -o app/src/main/assets/puzzles.tsv.gz

    Reads .csv.zst directly (Python 3.14+), .csv, or - for stdin. Without --stockfish it
    only selects puzzles (no alternatives). Engine results are cached in
    tools/.cache/alternatives.jsonl, so an interrupted run resumes where it stopped.

Input columns (Lichess):
    PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags

Output: gzip'd TSV, one puzzle per line, sorted by rating then id:
    id <TAB> fen <TAB> moves <TAB> rating <TAB> primaryTheme <TAB> themes <TAB> popularity <TAB> alternatives

`alternatives` lists, for each move the solver must find (moves[1], moves[3], ...), the
other moves that also win, comma-separated, with steps separated by "|". It is empty when
no step has an alternative. Example for a 2-step puzzle with one alternative at step 1:
"d1h5|" .

IMPORTANT (Lichess convention): `fen` is the position BEFORE the opponent's move.
`moves` is a space-separated UCI list; the FIRST move is the opponent's move that the
app plays automatically, then the player has to find moves[1], moves[3], ... while the
opponent replies with moves[2], moves[4], ...

Which alternatives count (all scores from the solver's side, at the same search):
- The listed move is always right and is not stored.
- Mate puzzles: when the listed move mates in N, another move counts only if it also
  mates in N or fewer (a slower mate fails "find the mate in N").
- Otherwise a move counts if it scores within --tolerance of the listed move, or if both
  are clearly winning (at least --winning) and it trails by no more than --wide-tolerance.
- A move never counts if it throws away an advantage the listed move keeps (the listed
  move scores at least --keep and the other move does not).
- The search must confirm the listed move first: unless the listed move scores at least
  --keep (or, in "save the game" puzzles tagged equality, at least --hold), the engine has
  not seen the idea and the step accepts nothing except a mate it actually found. Every
  alternative must itself score at least that floor.
"""
import argparse
import csv
import gzip
import io
import json
import os
import subprocess
import sys
import threading
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed

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
MATE_SCORE = 100_000


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("input", help=".csv.zst, .csv, or - for stdin")
    p.add_argument("-o", "--output", default="puzzles.tsv.gz")
    p.add_argument("--per-cell", type=int, default=45,
                   help="max puzzles per (primary theme, rating bucket) cell")
    p.add_argument("--min-popularity", type=int, default=90)
    p.add_argument("--min-plays", type=int, default=300)
    p.add_argument("--max-deviation", type=int, default=90)
    p.add_argument("--max-moves", type=int, default=9, help="max UCI moves in the line (keeps puzzles short)")
    p.add_argument("--stockfish", help="path to a Stockfish binary; omit to skip alternatives")
    p.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 2),
                   help="Stockfish processes in parallel (one thread each)")
    p.add_argument("--nodes", type=int, default=300_000, help="search size per step (same result on any computer)")
    p.add_argument("--multipv", type=int, default=4)
    p.add_argument("--tolerance", type=int, default=60, help="centipawns an alternative may trail the listed move")
    p.add_argument("--winning", type=int, default=500, help="centipawns that count as clearly winning")
    p.add_argument("--wide-tolerance", type=int, default=250,
                   help="how far a clearly winning alternative may trail a clearly winning listed move")
    p.add_argument("--keep", type=int, default=150,
                   help="an advantage this large must not be thrown away by an alternative")
    p.add_argument("--hold", type=int, default=-100,
                   help="the floor for equality (save the game) puzzles: a holding move")
    p.add_argument("--recheck-alternatives", action="store_true",
                   help="re-analyse only puzzles whose cached result has alternatives (after a rule change)")
    p.add_argument("--cache", default=os.path.join("tools", ".cache", "alternatives.jsonl"))
    return p.parse_args()


# ------------------------------------------------------------------ input

def open_input(path):
    if path == "-":
        return sys.stdin
    if path.endswith(".zst"):
        from compression import zstd  # Python 3.14+
        return io.TextIOWrapper(zstd.open(path, "rb"), encoding="utf-8", newline="")
    return open(path, newline="", encoding="utf-8")


def select(args):
    reader = csv.DictReader(open_input(args.input))
    cells = defaultdict(list)  # (theme, bucket) -> [(popularity, plays, row)]
    seen = kept = 0
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
        bucket = cells[(primary, (rating // BUCKET) * BUCKET)]
        bucket.append((pop, plays, row))
        kept += 1
        # Trim as we go so memory stays small even on the 5M-row file.
        if len(bucket) > args.per_cell * 4:
            bucket.sort(key=lambda x: (-x[0], -x[1]))
            del bucket[args.per_cell:]
        if seen % 500_000 == 0:
            print(f"  read {seen:,} rows", flush=True)

    rows = []
    for (theme, _), bucket in cells.items():
        bucket.sort(key=lambda x: (-x[0], -x[1]))
        for pop, _, row in bucket[:args.per_cell]:
            rows.append({
                "id": row["PuzzleId"], "fen": row["FEN"], "moves": row["Moves"].split(),
                "rating": int(row["Rating"]), "theme": theme, "themes": row["Themes"], "pop": pop,
            })
    rows.sort(key=lambda r: (r["rating"], r["id"]))
    print(f"read {seen:,} rows, {kept:,} passed filters, selected {len(rows):,} puzzles", flush=True)
    return rows


# ------------------------------------------------------------------ engine

class Engine:
    """One Stockfish process speaking UCI. Not thread-safe: one per worker."""

    def __init__(self, path, multipv):
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        # Windows will not start a relative path written with forward slashes.
        self.proc = subprocess.Popen([os.path.abspath(path)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     text=True, bufsize=1, creationflags=flags)
        self.send("uci")
        self.wait_for("uciok")
        for option in ("Threads value 1", "Hash value 64", f"MultiPV value {multipv}"):
            self.send(f"setoption name {option}")
        self.send("isready")
        self.wait_for("readyok")

    def send(self, line):
        self.proc.stdin.write(line + "\n")
        self.proc.stdin.flush()

    def wait_for(self, token):
        for line in self.proc.stdout:
            if line.strip() == token:
                return
        raise RuntimeError("Stockfish exited")

    def new_game(self):
        self.send("ucinewgame")
        self.send("isready")
        self.wait_for("readyok")

    def lines(self, fen, moves, nodes):
        """{first move: score} for each MultiPV line, scores from the side to move."""
        self.send(f"position fen {fen} moves {' '.join(moves)}" if moves else f"position fen {fen}")
        self.send(f"go nodes {nodes}")
        latest = {}
        for raw in self.proc.stdout:
            parts = raw.split()
            if not parts:
                continue
            if parts[0] == "bestmove":
                break
            if parts[0] != "info" or "pv" not in parts or "score" not in parts:
                continue
            if "lowerbound" in parts or "upperbound" in parts:
                continue
            i = parts.index("score")
            kind, value = parts[i + 1], int(parts[i + 2])
            score = (MATE_SCORE - abs(value)) * (1 if value > 0 else -1) if kind == "mate" else value
            rank = int(parts[parts.index("multipv") + 1]) if "multipv" in parts else 1
            latest[rank] = (parts[parts.index("pv") + 1], score, value if kind == "mate" else None)
        return {move: (score, mate) for move, score, mate in latest.values()}

    def close(self):
        try:
            self.send("quit")
            self.proc.wait(timeout=5)
        except Exception:
            self.proc.kill()


def accepted_alternatives(listed, lines, args, defensive=False):
    """The other moves at one step that also win, by the rules in the module docstring."""
    if listed in lines:
        ref_score, ref_mate = lines[listed]
    else:
        # The listed move fell out of the engine's top lines: compare with its best line.
        ref_score, ref_mate = max(lines.values(), key=lambda v: v[0])
    floor = args.hold if defensive else args.keep
    confirmed = (ref_mate is not None and ref_mate > 0) or ref_score >= floor
    alts = []
    for move, (score, mate) in lines.items():
        if move == listed:
            continue
        if ref_mate is not None and ref_mate > 0:
            if mate is not None and 0 < mate <= ref_mate:
                alts.append(move)
            continue
        if mate is not None and mate > 0:
            alts.append(move)  # a mate the engine found is proof the move wins
            continue
        if not confirmed or score < floor:
            continue  # the search did not confirm the idea, or this move is not good enough
        if ref_score >= args.keep and score < args.keep:
            continue
        both_winning = ref_score >= args.winning and score >= args.winning and score >= ref_score - args.wide_tolerance
        if score >= ref_score - args.tolerance or both_winning:
            alts.append(move)
    return sorted(alts)


def analyse_puzzle(engine, puzzle, args):
    engine.new_game()
    steps = []
    moves = puzzle["moves"]
    for i in range(1, len(moves), 2):
        lines = engine.lines(puzzle["fen"], moves[:i], args.nodes)
        defensive = "equality" in puzzle["themes"].split()
        steps.append(accepted_alternatives(moves[i], lines, args, defensive) if lines else [])
    return "|".join(",".join(step) for step in steps) if any(steps) else ""


def find_alternatives(rows, args):
    cache = {}
    if os.path.exists(args.cache):
        with open(args.cache, encoding="utf-8") as f:
            for line in f:
                entry = json.loads(line)
                cache[entry["id"]] = entry["alts"]
    if args.recheck_alternatives:
        todo = [r for r in rows if cache.get(r["id"])]
    else:
        todo = [r for r in rows if r["id"] not in cache]
    print(f"alternatives: {len(rows) - len(todo):,} cached, {len(todo):,} to analyse "
          f"with {args.workers} Stockfish processes", flush=True)

    engines = [Engine(args.stockfish, args.multipv) for _ in range(args.workers)]
    free = list(engines)
    free_lock = threading.Lock()
    write_lock = threading.Lock()
    done = 0
    start = time.time()

    def work(puzzle):
        with free_lock:
            engine = free.pop()
        try:
            return puzzle["id"], analyse_puzzle(engine, puzzle, args)
        finally:
            with free_lock:
                free.append(engine)

    os.makedirs(os.path.dirname(args.cache) or ".", exist_ok=True)
    with open(args.cache, "a", encoding="utf-8") as out, ThreadPoolExecutor(args.workers) as pool:
        for future in as_completed([pool.submit(work, p) for p in todo]):
            pid, alts = future.result()
            cache[pid] = alts
            with write_lock:
                out.write(json.dumps({"id": pid, "alts": alts}) + "\n")
                out.flush()
            done += 1
            if done % 250 == 0 or done == len(todo):
                rate = done / max(time.time() - start, 1e-9)
                left = (len(todo) - done) / rate if rate else 0
                print(f"  {done:,}/{len(todo):,} puzzles, about {left / 60:.0f} min left", flush=True)
    for engine in engines:
        engine.close()
    return cache


# ------------------------------------------------------------------ output

def main():
    args = parse_args()
    rows = select(args)
    alternatives = find_alternatives(rows, args) if args.stockfish else {}

    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write("\t".join([
                r["id"], r["fen"], " ".join(r["moves"]), str(r["rating"]), r["theme"], r["themes"],
                str(r["pop"]), alternatives.get(r["id"], ""),
            ]) + "\n")

    per_theme = defaultdict(int)
    for r in rows:
        per_theme[r["theme"]] += 1
    with_alts = sum(1 for r in rows if alternatives.get(r["id"]))
    print(f"wrote {len(rows):,} puzzles ({with_alts:,} with accepted alternatives) -> {args.output}"
          f" ({os.path.getsize(args.output) / 1024:.0f} KB)")
    for theme in CORE_THEMES:
        if per_theme[theme]:
            print(f"  {theme:<18}{per_theme[theme]:>6}")


if __name__ == "__main__":
    main()
