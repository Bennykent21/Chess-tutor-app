#!/usr/bin/env python3
"""
Build the opening trainer's repertoires (plan Phase 10, decisions D68 to D75) from the
Lichess game database, with every move checked by Stockfish.

Source: https://database.lichess.org/  (monthly standard rated games, .pgn.zst, CC0).
        This is not lichess-org/chess-openings (names and ECO codes, used for the names here).
Engine: official Stockfish 19 for this computer (GPL-3), used only at build time.
The game files are never bundled; only the small trees in app/src/main/assets/repertoire are.

Run from the repository root with the venv (python-chess, Python 3.14 for .zst):

    tools/.cache/venv/Scripts/python.exe -I tools/build_repertoire.py <step> ...

Steps (each one resumes where it stopped):

  fetch --month 2026-10 --gb 6 --data-dir E:/chess-data
      Downloads the first N GB of the month's file with HTTP byte ranges into the data
      folder (keep it on its own, for example an external drive). A slice ends in the
      middle of a game; the reader drops that game.

  count --input E:/chess-data/lichess_db_standard_rated_2026-10.first-6gb.pgn.zst
      Reads every rated blitz, rapid and classical game that starts like one of the
      configured openings and counts its first plies, per rating band of the player who
      makes each move. Counting is "lossy counting" (Manku and Motwani): rare tails are
      pruned so memory stays small, and no count is ever too low by more than the number of
      buckets (printed). Checkpoints to tools/.cache/repertoire/counts.json.gz.

  build italian caro-kann
      Turns the counts into each opening's tree (tools/repertoire/<id>.json), asks
      Stockfish about every position (cached in tools/.cache/repertoire/engine.jsonl, fixed
      node counts so any computer gets the same answer), checks the notes
      (tools/repertoire/<id>.notes.json), writes app/src/main/assets/repertoire/<id>.json.gz
      and a quality report (tools/.cache/repertoire/report-<id>.md).

  keys      Writes app/src/test/resources/repertoire/keys.tsv: positions and their keys, so a
            Kotlin test proves the app's Position.repetitionKey() matches fen4() here.
  sample    Builds app/src/test/resources/repertoire/sample.json.gz from made-up games
            (small thresholds, a shallow search), so the Kotlin tests have a real file.

Rating bands are cut by the Lichess rating of the player making the move (in that game's
time class), so a share means "what players at this level play here". The app speaks
Chess.com rapid ratings (D27); the Chess.com numbers below are estimates from public rating
comparisons (Lichess runs roughly 300 to 400 points higher in this range), not exact.

Frequency (D69): at a position (move orders that transpose meet at one key), games = the
counted games that reach it and go on; a move's share = its games / games. A position is
data-backed with at least --min-games games. A reply joins the tree when its share is at
least --min-share with --min-reply-games games, overall or in any band that has at least
--min-band-games games there. A band's share is left out (null) below that.

Verdicts use the app's own scale (domain/account/GameReview.kt MoveQuality, with
engine/WinProbability.kt): the winning chances lost against the best move, in points:
up to 5 sound, up to 10 inaccuracy, up to 20 mistake, more is a blunder.

Levels (D70) come from reach, the product of the opponent's shares on the way to a position
(the learner's own moves count as 1): how often the learner really meets it.
"""
import argparse
import codecs
import gzip
import json
import math
import os
import random
import re
import subprocess
import sys
import threading
import time
import urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

import chess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache", "repertoire")
CONFIGS = os.path.join(ROOT, "tools", "repertoire")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "repertoire")
TEST_RESOURCES = os.path.join(ROOT, "app", "src", "test", "resources", "repertoire")
OPENINGS_TSV = os.path.join(ROOT, "app", "src", "main", "assets", "openings.tsv.gz")
STOCKFISH = os.path.join(ROOT, "tools", ".cache", "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")
URL = "https://database.lichess.org/standard/lichess_db_standard_rated_{month}.pgn.zst"

FORMAT = 1

# By the Lichess rating of the player making the move. "chesscom" is the estimated
# Chess.com rapid range (the app's scale); the top band has no upper end.
BANDS = [
    {"id": "under-1000", "lichess": [0, 1400], "chesscom": [0, 1000]},
    {"id": "1000-1400", "lichess": [1400, 1750], "chesscom": [1000, 1400]},
    {"id": "1400-1800", "lichess": [1750, 2050], "chesscom": [1400, 1800]},
    {"id": "1800-plus", "lichess": [2050, None], "chesscom": [1800, None]},
]
EDGES = [b["lichess"][1] for b in BANDS[:-1]]
NBANDS = len(BANDS)
SPEEDS = {"blitz", "rapid", "classical"}
LEVELS = ["core", "standard", "deep"]

# Lost winning chances (points) at the top of each verdict, as in MoveQuality.of.
SOUND, INACCURACY, MISTAKE = 5.0, 10.0, 20.0


def band_of(rating):
    for i, edge in enumerate(EDGES):
        if rating < edge:
            return i
    return len(EDGES)


def fen4(board):
    """The card key: the FEN without move counters, en passant only when a capture is legal.
    Must equal the app's Position.repetitionKey() (checked by the keys fixture)."""
    return " ".join(board.fen(en_passant="legal").split()[:4])


def win(cp, mate):
    """Winning chances 0 to 100 for the side the score belongs to, as engine/WinProbability.kt."""
    if mate is not None:
        return 100.0 if mate > 0 else 0.0
    cp = max(-4000, min(4000, cp))
    return 50.0 + 50.0 * (2.0 / (1.0 + math.exp(-0.00368208 * cp)) - 1.0)


def verdict(loss):
    if loss <= SOUND:
        return "sound"
    if loss <= INACCURACY:
        return "inaccuracy"
    if loss <= MISTAKE:
        return "mistake"
    return "blunder"


# ------------------------------------------------------------------ settings

def add_thresholds(p):
    g = p.add_argument_group("thresholds (D69, D70)")
    g.add_argument("--min-games", type=int, default=1000, help="games at a position for it to be data-backed")
    g.add_argument("--min-share", type=float, default=0.03, help="share a reply needs to join the tree")
    g.add_argument("--min-reply-games", type=int, default=30, help="games a reply needs to join the tree")
    g.add_argument("--min-band-games", type=int, default=300, help="games in a band before its share counts")
    g.add_argument("--core-reach", type=float, default=0.10)
    g.add_argument("--core-ply", type=int, default=14)
    g.add_argument("--punish-reach", type=float, default=0.02, help="punish lines join Core from this reach")
    g.add_argument("--punish-ply", type=int, default=16)
    g.add_argument("--standard-reach", type=float, default=0.025)
    g.add_argument("--standard-ply", type=int, default=20)
    g.add_argument("--deep-reach", type=float, default=0.004)
    g.add_argument("--deep-ply", type=int, default=24)
    g.add_argument("--extension", type=int, default=2, help="engine-only own moves after the data ends")
    g.add_argument("--ceilings", default="25,80,200", help="own-move cards in Core, up to Standard, up to Deep")
    g.add_argument("--also-margin", type=float, default=3.0, help="points an accepted alternative may trail our move")
    g.add_argument("--pitfall-share", type=float, default=0.15)
    e = p.add_argument_group("engine")
    e.add_argument("--stockfish", default=STOCKFISH)
    e.add_argument("--nodes", type=int, default=3_000_000, help="search size per position")
    e.add_argument("--multipv", type=int, default=4)
    e.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 2))
    e.add_argument("--engine-cache", default=os.path.join(CACHE, "engine.jsonl"))


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="step", required=True)

    f = sub.add_parser("fetch", help="download the first N GB of a month")
    f.add_argument("--month", required=True, help="for example 2026-10")
    f.add_argument("--gb", type=float, default=6.0)
    f.add_argument("--data-dir", required=True)

    c = sub.add_parser("count", help="count the first plies of matching games")
    c.add_argument("--input", required=True, help=".pgn.zst (a slice is fine) or .pgn")
    c.add_argument("--openings", nargs="*", help="opening ids (default: every config)")
    c.add_argument("--plies", type=int, default=26)
    c.add_argument("--bucket", type=int, default=20_000, help="matched games per lossy-counting bucket")
    c.add_argument("--checkpoint", type=int, default=2_000_000, help="games read between checkpoints")
    c.add_argument("--counts", default=os.path.join(CACHE, "counts.json.gz"))

    b = sub.add_parser("build", help="build trees, check notes, write assets")
    b.add_argument("openings", nargs="+")
    b.add_argument("--counts", default=os.path.join(CACHE, "counts.json.gz"))
    b.add_argument("--out", default=ASSETS)
    add_thresholds(b)

    sub.add_parser("keys", help="write the shared key fixture")

    s = sub.add_parser("sample", help="write a small test asset from made-up games")
    s.add_argument("--games", type=int, default=4000)
    add_thresholds(s)
    s.set_defaults(min_games=150, min_band_games=40, min_reply_games=8, nodes=30_000, multipv=3,
                   core_reach=0.2, standard_reach=0.08, deep_reach=0.03, deep_ply=12, ceilings="8,20,40",
                   engine_cache=os.path.join(CACHE, "engine-sample.jsonl"))
    v = sub.add_parser("preview", help="write the debug-only preview openings from made-up counts")
    v.add_argument("--games", type=int, default=6000)
    add_thresholds(v)
    v.set_defaults(min_games=120, min_band_games=40, min_reply_games=12, nodes=400_000, deep_reach=0.01,
                   engine_cache=os.path.join(CACHE, "engine-preview.jsonl"))
    return p.parse_args(argv)


def load_config(opening_id):
    with open(os.path.join(CONFIGS, f"{opening_id}.json"), encoding="utf-8") as f:
        config = json.load(f)
    if config["id"] != opening_id:
        raise ValueError(f"{opening_id}.json has id {config['id']!r}")
    return config


def all_config_ids():
    return sorted(n[:-5] for n in os.listdir(CONFIGS) if n.endswith(".json") and not n.endswith(".notes.json"))


# ------------------------------------------------------------------ fetch

def fetch(args):
    url = URL.format(month=args.month)
    want = int(args.gb * 1024 ** 3)
    os.makedirs(args.data_dir, exist_ok=True)
    out = os.path.join(args.data_dir, f"lichess_db_standard_rated_{args.month}.first-{args.gb:g}gb.pgn.zst")
    have = os.path.getsize(out) if os.path.exists(out) else 0
    if have >= want:
        print(f"{out} already has {have:,} bytes")
        return out
    request = urllib.request.Request(url, headers={"Range": f"bytes={have}-{want - 1}", "User-Agent": "chess-tutor build_repertoire.py"})
    start = time.time()
    with urllib.request.urlopen(request, timeout=60) as response, open(out, "ab") as f:
        if response.status != 206:
            raise RuntimeError(f"the server did not send a byte range (HTTP {response.status}); nothing written")
        done = have
        while True:
            chunk = response.read(8 << 20)
            if not chunk:
                break
            f.write(chunk)
            done += len(chunk)
            rate = (done - have) / max(time.time() - start, 1e-9)
            print(f"  {done / 1024 ** 3:.2f} of {want / 1024 ** 3:.2f} GB, {rate / 1024 ** 2:.1f} MB/s", flush=True)
    print(f"wrote {out} ({os.path.getsize(out):,} bytes)")
    return out


# ------------------------------------------------------------------ reading games

class PgnSource:
    """Lines of a .pgn or .pgn.zst file. A .zst slice may stop mid-frame: then [truncated] is
    True at the end and the last, broken game is not yielded."""

    def __init__(self, path):
        self.path = path
        self.truncated = False

    def lines(self):
        if not self.path.endswith(".zst"):
            with open(self.path, encoding="utf-8", errors="replace") as f:
                for line in f:
                    yield line.rstrip("\n").rstrip("\r")
            return
        from compression import zstd
        decompressor = zstd.ZstdDecompressor()
        decoder = codecs.getincrementaldecoder("utf-8")("replace")
        pending = ""
        in_frame = False
        with open(self.path, "rb") as f:
            while True:
                chunk = f.read(4 << 20)
                if not chunk:
                    break
                parts = []
                data = chunk
                while data:
                    parts.append(decompressor.decompress(data))
                    in_frame = True
                    if decompressor.eof:
                        data = decompressor.unused_data
                        decompressor = zstd.ZstdDecompressor()
                        in_frame = False
                    else:
                        data = b""
                text = pending + decoder.decode(b"".join(parts))
                lines = text.split("\n")
                pending = lines.pop()
                for line in lines:
                    yield line.rstrip("\r")
        self.truncated = in_frame
        if pending and not self.truncated:
            yield pending


def games(source, skip=0):
    """(headers, movetext) for each game; only the headers counting needs are kept."""
    headers, moves, seen = {}, [], 0
    for line in source.lines():
        if line.startswith("["):
            if moves:
                seen += 1
                if seen > skip:
                    yield headers, " ".join(moves)
                headers, moves = {}, []
            if line.startswith(("[Event ", "[WhiteElo ", "[BlackElo ")):
                name, _, value = line[1:].rstrip().rstrip("]").partition(" ")
                headers[name] = value.strip().strip('"')
        elif line.strip():
            moves.append(line)
    if moves and not source.truncated:
        seen += 1
        if seen > skip:
            yield headers, " ".join(moves)


COMMENT = re.compile(r"\{[^}]*\}|\([^)]*\)")
RESULTS = {"1-0", "0-1", "1/2-1/2", "*"}


def san_moves(movetext, limit):
    """The first [limit] SAN moves of a Lichess movetext (clock and eval comments, move
    numbers, NAGs and annotation marks removed)."""
    text = movetext[:8000]
    if "{" in text or "(" in text:
        text = COMMENT.sub(" ", text)
        cut = min((i for i in (text.find("{"), text.find("(")) if i >= 0), default=-1)
        if cut >= 0:
            text = text[:cut]
    out = []
    for token in text.split():
        if token in RESULTS:
            break
        if token[0].isdigit():
            token = token[token.rfind(".") + 1:]
            if not token:
                continue
        if token[0] == "$":
            continue
        token = token.rstrip("?!")
        if token:
            out.append(token)
            if len(out) >= limit:
                break
    return out


def speed_of(event):
    parts = event.split()
    return parts[1].lower() if len(parts) >= 2 and parts[0] == "Rated" else None


# ------------------------------------------------------------------ counting

class Counter:
    """Lossy counting of SAN prefixes. counts[prefix] = [band 0..3 counts, delta]."""

    def __init__(self, filters, plies, bucket):
        self.filters = [tuple(f) for f in filters]
        self.longest = max(len(f) for f in self.filters)
        self.plies = plies
        self.bucket_size = bucket
        self.bucket = 1
        self.counts = {}
        self.games = 0
        self.rated = 0
        self.matched = 0
        self.per_filter = defaultdict(int)
        self.pruned = 0

    def compatible(self, tokens):
        return any(tuple(tokens[:len(f)]) == f[:len(tokens)] for f in self.filters)

    def add(self, headers, movetext):
        self.games += 1
        if speed_of(headers.get("Event", "")) not in SPEEDS:
            return
        try:
            bands = (band_of(int(headers["WhiteElo"])), band_of(int(headers["BlackElo"])))
        except (KeyError, ValueError):
            return
        self.rated += 1
        quick = san_moves(movetext[:400], self.longest)
        if not quick or not self.compatible(quick):
            return
        tokens = san_moves(movetext, self.plies)
        hit = next((f for f in self.filters if tuple(tokens[:len(f)]) == f), None)
        if hit is None:
            return
        self.matched += 1
        self.per_filter[" ".join(hit)] += 1
        prefix = ""
        for i, token in enumerate(tokens):
            prefix = token if i == 0 else prefix + " " + token
            entry = self.counts.get(prefix)
            if entry is None:
                entry = self.counts[prefix] = [0] * NBANDS + [self.bucket - 1]
            entry[bands[i & 1]] += 1
        if self.matched % self.bucket_size == 0:
            self.prune()

    def prune(self):
        b = self.bucket
        doomed = [p for p, e in self.counts.items() if sum(e[:NBANDS]) + e[NBANDS] <= b]
        for p in doomed:
            del self.counts[p]
        self.pruned += len(doomed)
        self.bucket += 1

    def meta(self):
        return {"games": self.games, "rated": self.rated, "matched": self.matched, "bucket": self.bucket,
                "bucketSize": self.bucket_size, "plies": self.plies, "pruned": self.pruned,
                "filters": [" ".join(f) for f in self.filters], "perFilter": dict(self.per_filter),
                "maxUndercount": self.bucket - 1}

    def save(self, path, source, done):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        tmp = path + ".tmp"
        with gzip.open(tmp, "wt", encoding="utf-8", compresslevel=1) as f:
            json.dump({"meta": self.meta() | {"input": os.path.basename(source), "done": done},
                       "counts": [[p] + e for p, e in self.counts.items()]}, f, separators=(",", ":"))
        os.replace(tmp, path)

    @classmethod
    def load(cls, path, filters, plies, bucket, source):
        counter = cls(filters, plies, bucket)
        if not os.path.exists(path):
            return counter, False
        with gzip.open(path, "rt", encoding="utf-8") as f:
            data = json.load(f)
        meta = data["meta"]
        if meta["input"] != os.path.basename(source) or meta["filters"] != [" ".join(f) for f in counter.filters] \
                or meta["plies"] != plies or meta["bucketSize"] != bucket:
            raise SystemExit(f"{path} was counted with other settings; move it away to count again")
        counter.games, counter.rated, counter.matched = meta["games"], meta["rated"], meta["matched"]
        counter.bucket, counter.pruned = meta["bucket"], meta["pruned"]
        counter.per_filter.update(meta["perFilter"])
        counter.counts = {row[0]: row[1:] for row in data["counts"]}
        return counter, meta["done"]


def count(args):
    ids = args.openings or all_config_ids()
    filters = [load_config(i)["start"].split() for i in ids]
    counter, done = Counter.load(args.counts, filters, args.plies, args.bucket, args.input)
    if done:
        print(f"{args.counts} is complete: {counter.matched:,} matching games")
        return counter
    source = PgnSource(args.input)
    start = time.time()
    skip = counter.games
    if skip:
        print(f"resuming after {skip:,} games", flush=True)
    for headers, movetext in games(source, skip=skip):
        counter.add(headers, movetext)
        if counter.games % 250_000 == 0:
            rate = (counter.games - skip) / max(time.time() - start, 1e-9)
            print(f"  {counter.games:,} games, {counter.matched:,} match, {len(counter.counts):,} prefixes, "
                  f"{rate:,.0f} games/s", flush=True)
        if counter.games % args.checkpoint == 0:
            counter.save(args.counts, args.input, done=False)
    counter.save(args.counts, args.input, done=True)
    print(f"read {counter.games:,} games ({counter.rated:,} rated blitz/rapid/classical with ratings), "
          f"{counter.matched:,} match; a count is at most {counter.bucket - 1} too low"
          + ("; the file is a slice, its last game was dropped" if source.truncated else ""))
    for name, n in sorted(counter.per_filter.items()):
        print(f"  {name}: {n:,}")
    return counter


def load_counts(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        data = json.load(f)
    return data["meta"], {row[0]: row[1:1 + NBANDS] for row in data["counts"]}


def position_edges(counts, start):
    """key -> uci -> band counts, from the SAN prefixes that agree with [start]."""
    start = tuple(start)
    edges = defaultdict(lambda: defaultdict(lambda: [0] * NBANDS))
    by_length = defaultdict(list)
    for prefix, bands in counts.items():
        tokens = prefix.split(" ")
        n = min(len(tokens), len(start))
        if tuple(tokens[:n]) == start[:n]:
            by_length[len(tokens)].append((tokens, bands))
    parents = {(): chess.Board()}
    for length in sorted(by_length):
        children = {}
        for tokens, bands in by_length[length]:
            parent = parents.get(tuple(tokens[:-1]))
            if parent is None:
                parent = replay(tokens[:-1])
                if parent is None:
                    continue
            try:
                move = parent.parse_san(tokens[-1])
            except ValueError:
                continue
            row = edges[fen4(parent)][move.uci()]
            for i in range(NBANDS):
                row[i] += bands[i]
            child = parent.copy(stack=False)
            child.push(move)
            children[tuple(tokens)] = child
        parents = children
    return edges


def replay(sans):
    board = chess.Board()
    try:
        for san in sans:
            board.push_san(san)
    except ValueError:
        return None
    return board


# ------------------------------------------------------------------ engine

class Engine:
    """One Stockfish process speaking UCI. Not thread-safe: one per worker."""

    def __init__(self, path):
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        self.proc = subprocess.Popen([os.path.abspath(path)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     text=True, bufsize=1, creationflags=flags)
        self.send("uci")
        self.wait_for("uciok")
        for option in ("Threads value 1", "Hash value 64"):
            self.send(f"setoption name {option}")
        self.multipv = None

    def send(self, line):
        self.proc.stdin.write(line + "\n")
        self.proc.stdin.flush()

    def wait_for(self, token):
        for line in self.proc.stdout:
            if line.strip() == token:
                return
        raise RuntimeError("Stockfish exited")

    def lines(self, fen, nodes, multipv):
        """[(uci, cp, mate)] best first, from the side to move."""
        if multipv != self.multipv:
            self.send(f"setoption name MultiPV value {multipv}")
            self.multipv = multipv
        self.send("ucinewgame")
        self.send("isready")
        self.wait_for("readyok")
        self.send(f"position fen {fen}")
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
            rank = int(parts[parts.index("multipv") + 1]) if "multipv" in parts else 1
            move = parts[parts.index("pv") + 1]
            latest[rank] = (move, None, value) if kind == "mate" else (move, value, None)
        return [latest[r] for r in sorted(latest)]

    def close(self):
        try:
            self.send("quit")
            self.proc.wait(timeout=5)
        except Exception:
            self.proc.kill()


class Analyzer:
    """Stockfish over many positions at once, with a JSON-lines cache keyed by position and settings."""

    def __init__(self, path, nodes, multipv, workers, cache):
        self.path, self.nodes, self.multipv, self.workers, self.cache_path = path, nodes, multipv, workers, cache
        self.cache = {}
        if os.path.exists(cache):
            with open(cache, encoding="utf-8") as f:
                for line in f:
                    entry = json.loads(line)
                    self.cache[entry["k"]] = [tuple(x) for x in entry["lines"]]
        self.engines = None
        self.lock = threading.Lock()

    def cache_key(self, board):
        return f"{fen4(board)}|{self.nodes}|{self.multipv}"

    def analyse_many(self, boards):
        """{fen4: [(uci, cp, mate)]} for each board that is not over."""
        todo, result = {}, {}
        for board in boards:
            if board.is_game_over(claim_draw=False):
                continue
            key = self.cache_key(board)
            if key in self.cache:
                result[fen4(board)] = self.cache[key]
            else:
                todo[key] = board
        if todo:
            if self.engines is None:
                self.engines = [Engine(self.path) for _ in range(self.workers)]
            free = list(self.engines)
            free_lock = threading.Lock()

            def work(item):
                key, board = item
                with free_lock:
                    engine = free.pop()
                try:
                    k = min(self.multipv, board.legal_moves.count())
                    return key, board, engine.lines(board.fen(), self.nodes, k)
                finally:
                    with free_lock:
                        free.append(engine)

            os.makedirs(os.path.dirname(self.cache_path) or ".", exist_ok=True)
            with open(self.cache_path, "a", encoding="utf-8") as out, ThreadPoolExecutor(self.workers) as pool:
                for key, board, lines in pool.map(work, todo.items()):
                    self.cache[key] = lines
                    result[fen4(board)] = lines
                    with self.lock:
                        out.write(json.dumps({"k": key, "lines": lines}) + "\n")
            print(f"  analysed {len(todo):,} positions", flush=True)
        return result

    def analyse(self, board):
        return self.analyse_many([board]).get(fen4(board))

    def close(self):
        for engine in self.engines or []:
            engine.close()


def best_win(lines):
    uci, cp, mate = lines[0]
    return win(cp, mate)


def move_wins(lines):
    return {uci: win(cp, mate) for uci, cp, mate in lines}


def white_eval(board, lines):
    """The best line's score from White's side: (cp, mate)."""
    _, cp, mate = lines[0]
    sign = 1 if board.turn == chess.WHITE else -1
    return (None if cp is None else sign * cp), (None if mate is None else sign * mate)


# ------------------------------------------------------------------ the tree

class Params:
    def __init__(self, args):
        for name in ("min_games", "min_share", "min_reply_games", "min_band_games", "core_reach", "core_ply",
                     "punish_reach", "punish_ply", "standard_reach", "standard_ply", "deep_reach", "deep_ply",
                     "extension", "also_margin", "pitfall_share"):
            setattr(self, name, getattr(args, name))
        self.ceilings = [int(x) for x in str(args.ceilings).split(",")]

    def rule_level(self, reach, ply):
        """Core, Standard or Deep (0, 1, 2) by reach and ply; punish lines are added later."""
        if reach >= self.core_reach and ply <= self.core_ply:
            return 0
        if reach >= self.standard_reach and ply <= self.standard_ply:
            return 1
        return 2


def shares(row, totals, params):
    """[pooled, band 0..3] shares, a band's left out (None) when it has too few games."""
    pooled = sum(row) / max(sum(totals), 1)
    return [pooled] + [row[i] / totals[i] if totals[i] >= params.min_band_games else None for i in range(NBANDS)]


def joins_tree(row, totals, params):
    if sum(row) >= params.min_reply_games and sum(row) / max(sum(totals), 1) >= params.min_share:
        return True
    return any(totals[i] >= params.min_band_games and row[i] >= params.min_reply_games
               and row[i] / totals[i] >= params.min_share for i in range(NBANDS))


class Item:
    """A position waiting to be expanded."""

    def __init__(self, board, path, reach, floor, extension_left=None, reply=None, parent_lines=None):
        self.board, self.path, self.reach, self.floor = board, path, reach, floor
        # None while the data lasts; then the engine-only own moves still allowed.
        self.extension_left = extension_left
        # The opponent's reply that led here (its record is completed once this position is analysed).
        self.replies = [reply] if reply is not None else []
        self.parent_lines = [parent_lines] if parent_lines is not None else []


def build_tree(config, edges, analyzer, params, names):
    """The repertoire: nodes keyed by position, and the walk's numbers for the report."""
    side = chess.WHITE if config["side"] == "white" else chess.BLACK
    start = config["start"].split()
    choices = config.get("choices", {})
    nodes = {}
    frontier = {fen4(chess.Board()): Item(chess.Board(), [], 1.0, 0)}
    ply = 0
    while frontier:
        analysis = analyzer.analyse_many([item.board for item in frontier.values()])
        extra = []
        for key, item in frontier.items():
            if item.board.turn == side and key in analysis:
                extra += own_extra_boards(item, edges.get(key, {}), analysis[key], choices, params, start)
        if extra:
            analysis |= analyzer.analyse_many(extra)
        following = {}
        for key, item in frontier.items():
            if key not in analysis:
                continue  # the game is over here
            for reply, parent_lines in zip(item.replies, item.parent_lines):
                finish_reply(reply, parent_lines, analysis[key])
            if key in nodes:
                continue
            if item.board.turn == side:
                node, children = expand_own(item, ply, edges.get(key, {}), analysis, choices, params, start)
            else:
                node, children = expand_opponent(item, ply, edges.get(key, {}), analysis[key], params, start)
            node["name"] = names.get(key)
            nodes[key] = node
            for child in children:
                ckey = fen4(child.board)
                if ckey in nodes:
                    # A transposition into a position already built: only the reply's verdict is missing.
                    lines = analyzer.analyse(child.board)
                    if lines:
                        for reply, parent_lines in zip(child.replies, child.parent_lines):
                            finish_reply(reply, parent_lines, lines)
                    continue
                if ckey in following:
                    merge(following[ckey], child)
                else:
                    following[ckey] = child
        frontier = following
        ply += 1
    return nodes


def merge(into, other):
    into.reach += other.reach
    into.floor = min(into.floor, other.floor)
    if into.extension_left is not None and other.extension_left is None:
        into.extension_left = None
    into.replies += other.replies
    into.parent_lines += other.parent_lines


def finish_reply(reply, parent_lines, lines):
    """The reply's verdict, now that the position after it is analysed."""
    opponent_after = 100.0 - best_win(lines)
    reply["loss"] = round(max(0.0, best_win(parent_lines) - opponent_after), 1)
    reply["verdict"] = verdict(reply["loss"])
    if reply["verdict"] in ("mistake", "blunder"):
        reply["trap"] = "punish"


def popular(stats, params):
    """{uci: share} of the moves played often enough here to matter (the same rule as replies)."""
    total = sum(sum(r) for r in stats.values())
    return {uci: sum(r) / total for uci, r in stats.items()
            if total and sum(r) >= params.min_reply_games and sum(r) / total >= params.min_share}


def own_move_wanted(item, choices, start):
    """The own move fixed by the opening's start or by the config, as SAN, or None."""
    if len(item.path) < len(start):
        return start[len(item.path)]
    return choices.get(" ".join(item.path))


def own_extra_boards(item, stats, lines, choices, params, start):
    """Positions after own moves we must score but the search did not list: the start or
    configured move and the moves players at this level choose often."""
    listed = {uci for uci, _, _ in lines}
    wanted = set(popular(stats, params))
    fixed = own_move_wanted(item, choices, start)
    if fixed:
        wanted.add(item.board.parse_san(fixed).uci())
    out = []
    for uci in wanted - listed:
        child = item.board.copy(stack=False)
        child.push(chess.Move.from_uci(uci))
        out.append(child)
    return out


def score_of(board, uci, lines, analysis):
    """The mover's winning chances after [uci]: from the search's own list, else from the position after it."""
    wins = move_wins(lines)
    if uci in wins:
        return wins[uci]
    child = board.copy(stack=False)
    child.push(chess.Move.from_uci(uci))
    if child.is_game_over(claim_draw=False):
        return 100.0 if child.is_checkmate() else 50.0
    after = analysis.get(fen4(child))
    if after is None:
        raise KeyError(f"{fen4(child)} was not analysed")
    return 100.0 - best_win(after)


def expand_own(item, ply, stats, analysis, choices, params, start):
    board, key = item.board, fen4(item.board)
    lines = analysis[key]
    best = best_win(lines)
    candidates = set(move_wins(lines)) | set(popular(stats, params))
    fixed = own_move_wanted(item, choices, start)
    if fixed:
        candidates.add(board.parse_san(fixed).uci())
    scores = {u: score_of(board, u, lines, analysis) for u in candidates}
    sound = {u for u, w in scores.items() if best - w <= SOUND}
    if fixed:
        move = board.parse_san(fixed).uci()
    else:
        liked = {u: s for u, s in popular(stats, params).items() if u in sound}
        move = max(liked, key=liked.get) if liked else lines[0][0]
    if move not in sound:
        raise ValueError(f"{' '.join(item.path) or 'start'}: our move {board.san(chess.Move.from_uci(move))} "
                         f"loses {best - scores[move]:.1f} points; change the config")
    level = 2 if item.extension_left is not None else params.rule_level(item.reach, ply)
    level = max(item.floor, level)
    totals = [0] * NBANDS
    for row in stats.values():
        for i in range(NBANDS):
            totals[i] += row[i]
    node = {"key": key, "ply": ply, "turn": "own", "games": totals, "data": sum(totals) >= params.min_games,
            "eval": white_eval(board, lines), "level": level, "reach": item.reach, "move": move,
            "also": sorted(u for u in sound if u != move and scores[move] - scores[u] <= params.also_margin)}
    if sum(totals) >= params.min_games:
        # How often players at this level choose the repertoire move here (shown on the reveal).
        node["moveShare"] = round(sum(stats.get(move, [0])) / sum(totals), 4)
    pitfalls = [(s, u) for u, s in popular(stats, params).items()
                if u != move and s >= params.pitfall_share and best - scores[u] > INACCURACY]
    if pitfalls:
        share, uci = max(pitfalls)
        loss = round(best - scores[uci], 1)
        node["pitfall"] = {"uci": uci, "share": round(share, 4), "games": stats[uci], "loss": loss, "verdict": verdict(loss)}
    if item.extension_left == 0 or ply + 1 > params.deep_ply:
        return node, []
    child = board.copy(stack=False)
    child.push(chess.Move.from_uci(move))
    extension = None if item.extension_left is None else item.extension_left - 1
    return node, [Item(child, item.path + [board.san(chess.Move.from_uci(move))], item.reach, level, extension)]


def expand_opponent(item, ply, stats, lines, params, start):
    board, key = item.board, fen4(item.board)
    totals = [0] * NBANDS
    for row in stats.values():
        for i in range(NBANDS):
            totals[i] += row[i]
    node = {"key": key, "ply": ply, "turn": "opponent", "games": totals, "data": sum(totals) >= params.min_games,
            "eval": white_eval(board, lines), "level": item.floor, "reach": item.reach, "replies": []}
    children = []

    def follow(uci, reach, data, extension):
        row = stats.get(uci, [0] * NBANDS)
        reply = {"uci": uci, "games": row, "share": shares(row, totals, params) if sum(totals) else None,
                 "data": data, "verdict": None, "loss": None, "trap": None}
        node["replies"].append(reply)
        child = board.copy(stack=False)
        child.push(chess.Move.from_uci(uci))
        san = board.san(chess.Move.from_uci(uci))
        children.append(Item(child, item.path + [san], reach, item.floor, extension, reply, lines))

    if len(item.path) < len(start):
        follow(board.parse_san(start[len(item.path)]).uci(), item.reach, node["data"], None)
        return node, children
    if node["data"] and item.extension_left is None:
        pooled = sum(totals)
        for uci, row in sorted(stats.items(), key=lambda kv: -sum(kv[1])):
            if not joins_tree(row, totals, params):
                continue
            reach = item.reach * sum(row) / pooled
            if reach >= params.deep_reach and ply + 1 <= params.deep_ply:
                follow(uci, reach, True, None)
    elif item.reach >= params.deep_reach and ply + 1 <= params.deep_ply:
        extension = params.extension if item.extension_left is None else item.extension_left
        if extension > 0:
            follow(lines[0][0], item.reach, False, extension)
    return node, children


def mark_punish_lines(nodes, params):
    """Punish lines join Core: after a mistake or blunder reply from a Core position (reach
    at least --punish-reach, up to --punish-ply), the next two own cards along the most
    common replies. The walk cannot know a reply was a mistake until the position after it
    is analysed, so this runs after it."""
    for node in [n for n in nodes.values() if n["turn"] == "opponent" and n["level"] == 0]:
        for reply in node["replies"]:
            if reply.get("trap") != "punish":
                continue
            key = child_key(node["key"], reply["uci"])
            for _ in range(2):
                card = nodes.get(key)
                if card is None or card["turn"] != "own" or card["ply"] > params.punish_ply \
                        or card["reach"] < params.punish_reach:
                    break
                card["level"] = 0
                after = nodes.get(child_key(key, card["move"]))
                if after is None or not after["replies"]:
                    break
                likeliest = max(after["replies"], key=lambda r: r["share"][0] if r.get("share") else 0)
                if not likeliest["data"]:
                    break  # engine-only moves stay Deep
                after["level"] = 0
                key = child_key(after["key"], likeliest["uci"])


def child_key(key, uci):
    board = chess.Board(key + " 0 1")
    board.push(chess.Move.from_uci(uci))
    return fen4(board)


def settle_levels(nodes):
    """Every reply gets the level of the card it leads to; no card is easier than the card before it."""
    for _ in range(64):
        changed = False
        for node in nodes.values():
            if node["turn"] == "own":
                nxt = nodes.get(child_key(node["key"], node["move"]))
                if nxt is not None and nxt["level"] < node["level"]:
                    nxt["level"] = node["level"]
                    changed = True
            else:
                for reply in node["replies"]:
                    card = nodes.get(child_key(node["key"], reply["uci"]))
                    if card is None:
                        continue
                    if card["level"] < node["level"]:
                        card["level"] = node["level"]
                        changed = True
                    reply["level"] = card["level"]
        if not changed:
            return


def cards_per_level(nodes):
    counts = [0, 0, 0]
    for node in nodes.values():
        if node["turn"] == "own":
            counts[node["level"]] += 1
    return counts


def build_within_ceilings(config, edges, analyzer, params, names):
    """Builds, then raises the reach cut of any level over its ceiling and builds again (the
    engine results are cached, so a rebuild is quick)."""
    def past_least(level):
        """A cut just above the least-reached card of [level] that the start line does not force."""
        reaches = [n["reach"] for n in nodes.values() if n["turn"] == "own" and n["level"] == level and n["reach"] < 1.0]
        if not reaches:
            raise RuntimeError(f"the start line alone has more {LEVELS[level]} cards than its ceiling")
        return min(reaches) * 1.0001

    for _ in range(500):
        nodes = build_tree(config, edges, analyzer, params, names)
        mark_punish_lines(nodes, params)
        settle_levels(nodes)
        core, standard, deep = cards_per_level(nodes)
        ceiling = params.ceilings
        if core > ceiling[0]:
            params.core_reach = max(params.core_reach, past_least(0))
            params.punish_reach = max(params.punish_reach, params.core_reach)
        elif core + standard > ceiling[1]:
            params.standard_reach = max(params.standard_reach, past_least(1))
        elif core + standard + deep > ceiling[2]:
            params.deep_reach = max(params.deep_reach, past_least(2))
        else:
            return nodes
    raise RuntimeError("could not fit the ceilings")


def opening_names():
    """fen4 -> database name, for positions where a named line ends."""
    names = {}
    with gzip.open(OPENINGS_TSV, "rt", encoding="utf-8") as f:
        for line in f:
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 3:
                continue
            board = chess.Board()
            try:
                for uci in cols[2].split():
                    board.push_uci(uci)
            except ValueError:
                continue
            names.setdefault(fen4(board), cols[1])
    return names


# ------------------------------------------------------------------ notes

SENTENCE = re.compile(r"(?<=[.!?])\s+")


def board_at(path):
    board = chess.Board()
    for san in path.split():
        board.push_san(san)
    return board


def play_line(board, line):
    board = board.copy(stack=False)
    for san in line:
        board.push_san(san)
    return board


def side_score(board, lines, side):
    """(cp, mate) of the best line for [side]."""
    _, cp, mate = lines[0]
    sign = 1 if board.turn == side else -1
    return (None if cp is None else sign * cp), (None if mate is None else sign * mate)


def at_least(score, threshold):
    cp, mate = score
    return mate > 0 if mate is not None else cp >= threshold


def check_claim(claim, board, analyzer):
    """(ok, why) for one claim at [board]. Kinds: best, sound, verdict, wins, threat, line, plan."""
    kind = claim["type"]
    if kind in ("best", "sound", "verdict"):
        lines = analyzer.analyse(board)
        uci = board.parse_san(claim["move"]).uci()
        loss = best_win(lines) - score_of(board, uci, lines, analyzer.analyse_many([play_line(board, [claim["move"]])]))
        if kind == "best":
            return loss <= 1.0, f"{claim['move']} loses {loss:.1f} points"
        if kind == "sound":
            return loss <= SOUND, f"{claim['move']} loses {loss:.1f} points"
        return verdict(loss) == claim["is"], f"{claim['move']} is {verdict(loss)} ({loss:.1f} points)"
    side = chess.WHITE if claim.get("side", "white") == "white" else chess.BLACK
    if kind == "wins":
        after = play_line(board, claim["line"])
        lines = analyzer.analyse(after)
        if lines is None:
            return after.is_checkmate() and after.turn != side, "the line ends the game"
        score = side_score(after, lines, side)
        return at_least(score, claim["atLeast"]), f"after the line: {score}"
    if kind == "threat":
        if board.is_check():
            return False, "a side in check cannot pass"
        passed = board.copy(stack=False)
        passed.push(chess.Move.null())
        after = play_line(passed, [claim["move"]])
        lines = analyzer.analyse(after)
        if lines is None:
            return after.is_checkmate(), "the threat ends the game"
        score = side_score(after, lines, passed.turn)
        return at_least(score, claim["atLeast"]), f"if allowed, {claim['move']} gives {score}"
    if kind == "line":
        current = board.copy(stack=False)
        for san in claim["line"]:
            if current.turn == side:
                lines = analyzer.analyse(current)
                uci = current.parse_san(san).uci()
                loss = best_win(lines) - score_of(current, uci, lines, analyzer.analyse_many([play_line(current, [san])]))
                if loss > SOUND:
                    return False, f"{san} loses {loss:.1f} points"
            current.push_san(san)
        return True, "every move is sound"
    if kind == "plan":
        after = play_line(board, claim.get("after", []))
        lines = analyzer.analyse(after)
        top = [uci for uci, _, _ in lines[:claim.get("top", 3)]]
        uci = after.parse_san(claim["move"]).uci()
        return uci in top, f"{claim['move']} {'is' if uci in top else 'is not'} in the top {len(top)}"
    raise ValueError(f"unknown claim type {kind!r}")


def check_notes(notes, nodes, analyzer):
    """Attaches each note to its node and checks its claims. Returns report rows."""
    rows = []
    for note in notes:
        board = board_at(note["path"])
        key = fen4(board)
        sentences = [s for s in SENTENCE.split(note["text"].strip()) if s]
        covered, failed = set(), []
        for claim in note.get("claims", []):
            ok, why = check_claim(claim, board, analyzer)
            if ok:
                covered.add(claim["sentence"])
            else:
                failed.append(f"{claim['type']}: {why}")
        unverified = [s for i, s in enumerate(sentences) if i not in covered]
        rows.append({"path": note["path"], "kind": note["kind"], "inTree": key in nodes, "failed": failed,
                     "unverified": unverified})
        if key in nodes:
            nodes[key]["note"] = {"kind": note["kind"], "text": note["text"]}
    return rows


# ------------------------------------------------------------------ output

def asset_of(config, nodes, meta, params, args):
    def reply_of(reply):
        out = {k: v for k, v in reply.items() if v is not None}
        if reply.get("share"):
            out["share"] = [None if s is None else round(s, 4) for s in reply["share"]]
        return out

    def clean(node):
        out = {k: v for k, v in node.items() if v is not None and k not in ("eval", "reach", "replies")}
        out["reach"] = round(node["reach"], 5)
        cp, mate = node["eval"]
        if mate is not None:
            out["mate"] = mate
        else:
            out["eval"] = cp
        if node["turn"] == "opponent":
            out["replies"] = [reply_of(r) for r in node["replies"]]
        return out

    ordered = sorted(nodes.values(), key=lambda n: (n["ply"], n["key"]))
    return {
        "format": FORMAT, "id": config["id"], "title": config["title"], "side": config["side"],
        "start": [m for m in uci_line(config["start"].split())],
        "source": {
            "data": meta.get("input"), "games": meta.get("games"), "matched": meta.get("perFilter", {}).get(config["start"]),
            "speeds": sorted(SPEEDS), "bands": BANDS, "maxUndercount": meta.get("maxUndercount"),
            "minGames": params.min_games, "minShare": params.min_share, "minReplyGames": params.min_reply_games,
            "minBandGames": params.min_band_games,
            "levels": {"core": [round(params.core_reach, 4), params.core_ply], "standard": [round(params.standard_reach, 4), params.standard_ply],
                       "deep": [round(params.deep_reach, 4), params.deep_ply]},
            "engine": f"Stockfish 19, {args.nodes} nodes, MultiPV {args.multipv}",
        },
        "nodes": [clean(n) for n in ordered],
    }


def uci_line(sans):
    board = chess.Board()
    out = []
    for san in sans:
        move = board.parse_san(san)
        out.append(move.uci())
        board.push(move)
    return out


def write_asset(asset, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = json.dumps(asset, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with open(path, "wb") as f:
        f.write(gzip.compress(raw, compresslevel=9, mtime=0))
    return len(raw), os.path.getsize(path)


def report(config, nodes, note_rows, sizes, meta):
    cards = [n for n in nodes.values() if n["turn"] == "own"]
    opp = [n for n in nodes.values() if n["turn"] == "opponent"]
    replies = [r for n in opp for r in n["replies"]]
    lines = [f"# {config['title']} ({config['side']}): quality report", ""]
    lines.append(f"Data: {meta.get('input')}, {meta.get('games', 0):,} games read, "
                 f"{meta.get('perFilter', {}).get(config['start'], 0):,} start with {config['start']}; "
                 f"counts are at most {meta.get('maxUndercount', 0)} too low.")
    lines.append("")
    lines.append("| Level | Own-move cards | Data-backed | Engine only |")
    lines.append("|---|---|---|---|")
    for i, name in enumerate(LEVELS):
        level = [c for c in cards if c["level"] == i]
        lines.append(f"| {name} | {len(level)} | {sum(1 for c in level if c['data'])} | {sum(1 for c in level if not c['data'])} |")
    lines.append("")
    lines.append(f"Opponent positions: {len(opp)}; replies in the tree: {len(replies)} "
                 f"(punish: {sum(1 for r in replies if r.get('trap') == 'punish')}; "
                 + ", ".join(f"{v}: {sum(1 for r in replies if r.get('verdict') == v)}" for v in ("sound", "inaccuracy", "mistake", "blunder"))
                 + f"). Pitfalls: {sum(1 for c in cards if 'pitfall' in c)}.")
    noted = sum(1 for n in nodes.values() if "note" in n)
    lines.append(f"Notes: {noted} of {len(nodes)} positions ({100 * noted / max(len(nodes), 1):.0f}%).")
    lines.append(f"Asset: {sizes[1]:,} bytes gzip'd ({sizes[0]:,} bytes of JSON).")
    lines.append("")
    problems = [r for r in note_rows if r["failed"] or r["unverified"] or not r["inTree"]]
    lines.append(f"## Notes needing a look ({len(problems)} of {len(note_rows)})")
    for r in problems:
        lines.append(f"- {r['path']} ({r['kind']}){'' if r['inTree'] else ': NOT IN THE TREE'}")
        for f in r["failed"]:
            lines.append(f"  - failed claim: {f}")
        for s in r["unverified"]:
            lines.append(f"  - unverified: {s}")
    return "\n".join(lines) + "\n"


def build(args):
    meta, counts = load_counts(args.counts)
    if not meta.get("done"):
        print("warning: the counts are from an unfinished count run", file=sys.stderr)
    analyzer = Analyzer(args.stockfish, args.nodes, args.multipv, args.workers, args.engine_cache)
    names = opening_names()
    try:
        for opening_id in args.openings:
            config = load_config(opening_id)
            params = Params(args)
            edges = position_edges(counts, config["start"].split())
            nodes = build_within_ceilings(config, edges, analyzer, params, names)
            notes_path = os.path.join(CONFIGS, f"{opening_id}.notes.json")
            notes = []
            if os.path.exists(notes_path):
                with open(notes_path, encoding="utf-8") as f:
                    notes = json.load(f)["notes"]
            note_rows = check_notes(notes, nodes, analyzer)
            asset = asset_of(config, nodes, meta, params, args)
            sizes = write_asset(asset, os.path.join(args.out, f"{opening_id}.json.gz"))
            text = report(config, nodes, note_rows, sizes, meta)
            os.makedirs(CACHE, exist_ok=True)
            with open(os.path.join(CACHE, f"report-{opening_id}.md"), "w", encoding="utf-8") as f:
                f.write(text)
            print(text)
    finally:
        analyzer.close()


# ------------------------------------------------------------------ fixtures

KEY_CASES = [
    # (start FEN, UCI moves, why)
    (chess.STARTING_FEN, "", "start"),
    (chess.STARTING_FEN, "e2e4", "double step, no pawn can take: no en passant square"),
    (chess.STARTING_FEN, "e2e4 d7d5 e4e5 f7f5", "en passant f6 is legal"),
    (chess.STARTING_FEN, "e2e4 d7d5 e4e5 f7f5 g1f3", "the chance to take en passant is gone"),
    (chess.STARTING_FEN, "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 e1g1", "White castled"),
    (chess.STARTING_FEN, "e2e4 e7e5 e1e2", "king moved: no White castling"),
    (chess.STARTING_FEN, "g2g3 a7a6 f1g2 a6a5 g2b7 a5a4 b7a8", "rook taken on its square: Black keeps only short castling"),
    (chess.STARTING_FEN, "h2h4 h7h5 h1h3 a7a6 h3h1", "rook went away and came back: no short castling"),
    ("8/8/8/K2p3r/8/8/4P3/7k w - - 0 1", "e2e4", "double step that no pawn can take"),
    ("4k3/2p5/8/KP5r/8/8/8/8 b - - 0 1", "c7c5", "en passant would expose the king: no square"),
    ("4k3/2p5/8/1P6/8/8/8/K7 b - - 0 1", "c7c5", "en passant legal on c6"),
    ("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "a1a8", "a rook takes a rook on its square"),
    ("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1", "b7b8n", "promotion to a knight"),
]


def keys(args=None):
    rows = []
    for fen, moves, why in KEY_CASES:
        board = chess.Board(fen)
        if not board.is_valid():
            continue
        for uci in moves.split():
            board.push_uci(uci)
        rows.append((fen, moves, fen4(board), why))
    rng = random.Random(10)
    for _ in range(40):
        board = chess.Board()
        moves = []
        for _ in range(rng.randrange(6, 40)):
            legal = list(board.legal_moves)
            if not legal:
                break
            # Pawn double steps and captures first, so en passant chances come up often.
            legal.sort(key=lambda m: (not board.is_capture(m), abs(m.from_square - m.to_square) != 16, m.uci()))
            move = legal[0] if rng.random() < 0.3 else rng.choice(legal)
            moves.append(move.uci())
            board.push(move)
        rows.append((chess.STARTING_FEN, " ".join(moves), fen4(board), "random game"))
    os.makedirs(TEST_RESOURCES, exist_ok=True)
    path = os.path.join(TEST_RESOURCES, "keys.tsv")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# start fen\tuci moves\tkey (written by tools/build_repertoire.py keys)\tcase\n")
        for row in rows:
            f.write("\t".join(row) + "\n")
    print(f"wrote {len(rows)} cases to {path}")


# Made-up games for the sample asset: a small weighted book after the Italian, then random moves.
SAMPLE_BOOK = {
    "e4 e5 Nf3 Nc6 Bc4": {"Bc5": 45, "Nf6": 35, "Be7": 8, "d6": 7, "Nd4": 5},
    "e4 e5 Nf3 Nc6 Bc4 Bc5": {"c3": 60, "O-O": 25, "d3": 15},
    "e4 e5 Nf3 Nc6 Bc4 Bc5 c3": {"Nf6": 70, "d6": 30},
    "e4 e5 Nf3 Nc6 Bc4 Nf6": {"d3": 55, "Ng5": 45},
    "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5": {"d5": 80, "Bc5": 20},
    "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5": {"Nxd5": 55, "Na5": 45},
    "e4 e5 Nf3 Nc6 Bc4 Nd4": {"Nxe5": 70, "Nxd4": 30},
    "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxe5": {"Qg5": 80, "d6": 20},
}


def sample_games(n, seed=4):
    rng = random.Random(seed)
    out = []
    for g in range(n):
        board = chess.Board()
        sans = []
        for san in "e4 e5 Nf3 Nc6 Bc4".split():
            board.push_san(san)
            sans.append(san)
        for _ in range(14):
            book = SAMPLE_BOOK.get(" ".join(sans))
            if book:
                san = rng.choices(list(book), weights=list(book.values()))[0]
            else:
                legal = sorted(board.san(m) for m in board.legal_moves)
                if not legal:
                    break
                san = legal[min(int(rng.expovariate(0.6)), len(legal) - 1)]
            board.push_san(san)
            sans.append(san)
        speed = rng.choice(["Blitz", "Rapid", "Classical", "Bullet"])
        white, black = rng.randrange(800, 2600), rng.randrange(800, 2600)
        out.append(lichess_pgn(sans, speed, white, black, g))
    return out


def lichess_pgn(sans, speed, white_elo, black_elo, number):
    """One game as the Lichess dumps write it (clock comments, some annotations)."""
    words = []
    for i, san in enumerate(sans):
        if i % 2 == 0:
            words.append(f"{i // 2 + 1}.")
        elif i % 4 == 1:
            words.append(f"{i // 2 + 1}...")
        mark = "?!" if (number + i) % 23 == 0 else ""
        words.append(f"{san}{mark} {{ [%clk 0:03:00] }}")
    words.append("1-0")
    return (f'[Event "Rated {speed} game"]\n[Site "https://lichess.org/x{number}"]\n[White "w{number}"]\n'
            f'[Black "b{number}"]\n[Result "1-0"]\n[WhiteElo "{white_elo}"]\n[BlackElo "{black_elo}"]\n\n'
            + " ".join(words) + "\n\n")


def sample(args):
    config = {"id": "sample", "title": "Sample Italian", "side": "white", "start": "e4 e5 Nf3 Nc6 Bc4", "choices": {}}
    counter = Counter([config["start"].split()], 20, 500)
    for text in sample_games(args.games):
        head, _, moves = text.partition("\n\n")
        headers = {}
        for line in head.splitlines():
            name, _, value = line[1:].rstrip("]").partition(" ")
            headers[name] = value.strip('"')
        counter.add(headers, moves.strip())
    meta = counter.meta() | {"input": "made-up games (sample)", "done": True}
    counts = {p: e[:NBANDS] for p, e in counter.counts.items()}
    analyzer = Analyzer(args.stockfish, args.nodes, args.multipv, args.workers, args.engine_cache)
    try:
        params = Params(args)
        nodes = build_within_ceilings(config, position_edges(counts, config["start"].split()), analyzer, params, opening_names())
        notes = [{"path": "e4 e5 Nf3 Nc6 Bc4 Nd4", "kind": "trap",
                  "text": "A test note with no claim. The engine lists Nxe5 among its first three moves here.",
                  "claims": [{"type": "plan", "move": "Nxe5", "top": 3, "sentence": 1}]}]
        check_notes(notes, nodes, analyzer)
        asset = asset_of(config, nodes, meta, params, args)
        sizes = write_asset(asset, os.path.join(TEST_RESOURCES, "sample.json.gz"))
    finally:
        analyzer.close()
    print(f"wrote sample.json.gz: {len(nodes)} positions, cards per level {cards_per_level(nodes)}, {sizes[1]:,} bytes")


# Made-up game counts for the debug-only preview (app/src/debug/assets): plausible weights, not data.
# Each entry: the SAN path from the start -> {next move: weight}. Games stop where the book stops.
PREVIEW_BOOKS = {
    "italian": {
        "": {"e4": 1}, "e4": {"e5": 1}, "e4 e5": {"Nf3": 1}, "e4 e5 Nf3": {"Nc6": 1}, "e4 e5 Nf3 Nc6": {"Bc4": 1},
        "e4 e5 Nf3 Nc6 Bc4": {"Bc5": 45, "Nf6": 33, "Nd4": 7, "Be7": 6, "d6": 5, "h6": 4},
        "e4 e5 Nf3 Nc6 Bc4 Bc5": {"c3": 55, "O-O": 25, "d3": 20},
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3": {"Nf6": 60, "d6": 20, "Qe7": 12, "Bb6": 8},
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6": {"d3": 60, "d4": 40},
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3": {"d6": 40, "O-O": 30, "a6": 15, "h6": 15},
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 d6": {"d4": 50, "d3": 50},
        "e4 e5 Nf3 Nc6 Bc4 Nf6": {"d3": 45, "Ng5": 40, "Nc3": 15},
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3": {"Bc5": 45, "Be7": 35, "h6": 10, "d6": 10},
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Bc5": {"c3": 60, "O-O": 40},
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Be7": {"O-O": 70, "c3": 30},
        "e4 e5 Nf3 Nc6 Bc4 Nd4": {"Nxe5": 55, "Nxd4": 30, "O-O": 15},
        "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxe5": {"Qg5": 80, "d6": 20},
        "e4 e5 Nf3 Nc6 Bc4 Be7": {"d4": 55, "d3": 45},
        "e4 e5 Nf3 Nc6 Bc4 d6": {"d4": 50, "c3": 50},
    },
    "caro-kann": {
        "": {"e4": 1}, "e4": {"c6": 1},
        "e4 c6": {"d4": 70, "Nc3": 12, "Nf3": 8, "c4": 5, "d3": 5},
        "e4 c6 d4": {"d5": 1},
        "e4 c6 d4 d5": {"e5": 35, "Nc3": 25, "exd5": 25, "Nd2": 10, "f3": 5},
        "e4 c6 d4 d5 e5": {"Bf5": 1},
        "e4 c6 d4 d5 e5 Bf5": {"Nf3": 40, "Nc3": 20, "Be2": 20, "h4": 10, "c3": 10},
        "e4 c6 d4 d5 Nc3": {"dxe4": 1}, "e4 c6 d4 d5 Nc3 dxe4": {"Nxe4": 1},
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4": {"Bf5": 1},
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5": {"Ng3": 80, "Nc5": 20},
        "e4 c6 d4 d5 Nd2": {"dxe4": 1}, "e4 c6 d4 d5 Nd2 dxe4": {"Nxe4": 1},
        "e4 c6 d4 d5 Nd2 dxe4 Nxe4": {"Bf5": 1},
        "e4 c6 d4 d5 exd5": {"cxd5": 1},
        "e4 c6 d4 d5 exd5 cxd5": {"Bd3": 40, "c4": 30, "Nf3": 30},
        "e4 c6 d4 d5 f3": {"e6": 50, "dxe4": 50},
        "e4 c6 Nc3": {"d5": 1}, "e4 c6 Nc3 d5": {"Nf3": 60, "d4": 40},
        "e4 c6 Nc3 d5 Nf3": {"Bg4": 1},
        "e4 c6 Nf3": {"d5": 1}, "e4 c6 Nf3 d5": {"e5": 50, "Nc3": 30, "exd5": 20},
        "e4 c6 c4": {"d5": 1}, "e4 c6 c4 d5": {"exd5": 70, "cxd5": 30},
        "e4 c6 d3": {"d5": 1}, "e4 c6 d3 d5": {"Nd2": 70, "e5": 30},
    },
}


def preview(args):
    """Debug-only preview openings from PREVIEW_BOOKS (made-up counts, real Stockfish), so the
    trainer screens can be tried before the Lichess data is built."""
    rng = random.Random(21)
    out_dir = os.path.join(ROOT, "app", "src", "debug", "assets", "repertoire")
    analyzer = Analyzer(args.stockfish, args.nodes, args.multipv, args.workers, args.engine_cache)
    names = opening_names()
    try:
        for opening_id, book in PREVIEW_BOOKS.items():
            config = load_config(opening_id) | {}
            config["title"] = config["title"] + " (preview)"
            counter = Counter([config["start"].split()], 24, 10_000_000)
            for g in range(args.games):
                sans = []
                while True:
                    options = book.get(" ".join(sans))
                    if not options:
                        break
                    sans.append(rng.choices(list(options), weights=list(options.values()))[0])
                speed = rng.choice(["Blitz", "Rapid"])
                headers = {"Event": f"Rated {speed} game", "WhiteElo": str(rng.randrange(900, 2300)), "BlackElo": str(rng.randrange(900, 2300))}
                counter.add(headers, " ".join(f"{i // 2 + 1}. {s}" if i % 2 == 0 else s for i, s in enumerate(sans)) + " 1-0")
            meta = counter.meta() | {"input": "made-up counts (preview, debug builds only)", "done": True}
            counts = {p: e[:NBANDS] for p, e in counter.counts.items()}
            params = Params(args)
            nodes = build_within_ceilings(config, position_edges(counts, config["start"].split()), analyzer, params, names)
            sizes = write_asset(asset_of(config, nodes, meta, params, args), os.path.join(out_dir, f"{opening_id}.json.gz"))
            print(f"{opening_id}: {len(nodes)} positions, cards per level {cards_per_level(nodes)}, {sizes[1]:,} bytes")
    finally:
        analyzer.close()


def main(argv=None):
    args = parse_args(argv)
    {"fetch": fetch, "count": count, "build": build, "keys": keys, "sample": sample, "preview": preview}[args.step](args)


if __name__ == "__main__":
    main()
