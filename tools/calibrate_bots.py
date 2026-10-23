#!/usr/bin/env python3
"""
Measures the bots' strength (plan D26): plays the app's bot settings against each other and
against plain Maia networks (anchors with known human ratings), then fits one rating ladder.

The bots are rebuilt here exactly as BotBrain plays them out of book:
- Maia mode: lc0 gives Maia's move probabilities (go nodes 1, PolicyTemperature 1); the bot
  samples moves by probability ^ (1 / temperature); strong bots check each pick with a
  shallow Stockfish search and drop picks that lose more than the margin.
- Engine mode: Stockfish's 4 best moves at a shallow depth, picked like Skill::pick_best.
Every game starts after 4 plies sampled from Maia 1500, so games differ; each pairing plays
both colours. Results are appended to a JSON-lines file, so a stopped run can resume.

Usage (from the repository root):
    tools/.cache/venv/Scripts/python.exe -I tools/calibrate_bots.py play --games 20 --workers 4
    tools/.cache/venv/Scripts/python.exe -I tools/calibrate_bots.py fit

Anchors (assumed human ratings, Chess.com rapid scale, D27): Maia plays its favourite move
(no sampling, no search). The Maia authors and Lichess report these nets play above their
training band; Lichess blitz ratings of the Maia bots convert to roughly the numbers below.
They are estimates, so the fitted ladder is only as good as these anchors.
"""
import argparse
import json
import math
import os
import random
import re
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor

import chess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache")
MAIA = os.path.join(CACHE, "maia")
LC0 = os.path.join(MAIA, "lc0", "lc0.exe")
STOCKFISH = os.path.join(CACHE, "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")
RESULTS = os.path.join(CACHE, "calibration.jsonl")

ANCHORS = {"maia1100": 1300, "maia1500": 1400, "maia1900": 1500}
MATE = 100_000

# The app's tables (BotStrengthModel): keep in step with BotBrain.kt.
MAIA_TABLE = [
    (250, 1100, 2.2, 0.25, None, None),
    (500, 1100, 1.6, 0.10, None, None),
    (800, 1100, 1.1, 0.03, None, None),
    (1000, 1100, 0.9, 0.0, None, None),
    (1200, 1500, 0.75, 0.0, None, None),
    (1400, 1900, 0.9, 0.0, 4, 450),
    (1600, 1900, 0.8, 0.0, 5, 330),
    (1800, 1900, 0.7, 0.0, 6, 230),
    (2000, 1900, 0.6, 0.0, 7, 170),
    (2200, 1900, 0.5, 0.0, 9, 110),
    (2400, 1900, 0.42, 0.0, 11, 70),
]
ENGINE_TABLE = [(2500, 16, 13), (2800, 19, 18)]


class Uci:
    def __init__(self, args):
        self.last = ""
        self.p = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                  text=True, bufsize=1, cwd=os.path.dirname(args[0]))
        self.send("uci")
        self.wait("uciok")

    def send(self, cmd):
        if cmd.startswith("position"):
            self.last = cmd
        self.p.stdin.write(cmd + "\n")
        self.p.stdin.flush()

    def wait(self, token):
        lines = []
        while True:
            line = self.p.stdout.readline()
            if not line:
                raise RuntimeError(f"engine stopped after: {self.last}")
            lines.append(line)
            if line.startswith(token):
                return lines

    def close(self):
        try:
            self.send("quit")
        except OSError:
            pass


class Maia(Uci):
    def __init__(self, net):
        super().__init__([LC0, f"--weights={os.path.join(MAIA, f'maia-{net}.pb.gz')}", "--backend=blas", "--threads=1"])
        self.send("setoption name VerboseMoveStats value true")
        self.send("setoption name PolicyTemperature value 1.0")
        self.send("isready")
        self.wait("readyok")

    def policy(self, moves):
        self.send("position startpos" + (" moves " + " ".join(moves) if moves else ""))
        self.send("go nodes 1")
        probs = {}
        for line in self.wait("bestmove"):
            hit = re.match(r"info string (\S+)\s+\(\s*\d+\s*\).*\(P:\s*([\d.]+)%\)", line)
            if hit and hit.group(1) != "node":
                probs[hit.group(1)] = float(hit.group(2)) / 100
        return probs


class Fish(Uci):
    def __init__(self):
        super().__init__([STOCKFISH])
        self.send("setoption name Threads value 1")
        self.send("setoption name Hash value 32")
        self.send("isready")
        self.wait("readyok")

    def lines(self, moves, depth, multipv=1):
        """[(uci, score for the side to move)], best first."""
        self.send(f"setoption name MultiPV value {multipv}")
        self.send("position startpos" + (" moves " + " ".join(moves) if moves else ""))
        self.send(f"go depth {depth}")
        best = {}
        for line in self.wait("bestmove"):
            if " pv " not in line or " score " not in line:
                continue
            k = int(re.search(r"multipv (\d+)", line).group(1)) if "multipv" in line else 1
            score = re.search(r"score (cp|mate) (-?\d+)", line)
            value = int(score.group(2))
            if score.group(1) == "mate":
                value = MATE - value * 100 if value > 0 else -MATE - value * 100
            best[k] = (line.split(" pv ")[1].split()[0], value)
        return [best[k] for k in sorted(best)]


def pick_weighted(options, rng):
    total = sum(w for _, w in options)
    roll = rng.random() * total
    for value, w in options:
        roll -= w
        if roll < 0:
            return value
    return options[-1][0]


class Bot:
    def __init__(self, name, maia_net=None, temperature=1.0, random_chance=0.0, check_depth=None, margin=None,
                 skill=None, depth=None, argmax=False):
        self.name, self.maia_net, self.temperature, self.random_chance = name, maia_net, temperature, random_chance
        self.check_depth, self.margin, self.skill, self.depth, self.argmax = check_depth, margin, skill, depth, argmax

    def move(self, board, moves, engines, rng):
        legal = [m.uci() for m in board.legal_moves]
        if rng.random() < self.random_chance:
            return rng.choice(legal)
        fish = engines["fish"]
        if self.maia_net is None:
            return self.skill_pick(fish.lines(moves, self.depth, 4), rng)
        # lc0 may name castling king-takes-rook (e1h1): normalise every move to standard UCI.
        probs = {board.parse_uci(u).uci(): p for u, p in engines[f"maia{self.maia_net}"].policy(moves).items()}
        if self.argmax:
            return max(probs, key=probs.get)
        weights = {u: p ** (1.0 / self.temperature) for u, p in probs.items() if p > 0}
        order = []
        while len(order) < 4 and weights:
            u = pick_weighted(list(weights.items()), rng)
            order.append(u)
            del weights[u]
        if self.margin is None:
            return order[0]
        best_uci, best = fish.lines(moves, self.check_depth)[0]
        for u in order:
            board.push_uci(u)
            if board.is_checkmate():
                score = MATE
            elif board.is_stalemate():
                score = 0
            else:
                score = -fish.lines(moves + [u], max(self.check_depth - 1, 1))[0][1]
            board.pop()
            if best - score <= self.margin:
                return u
        return best_uci

    def skill_pick(self, lines, rng):
        """Stockfish Skill::pick_best on (uci, score) lines."""
        level = self.skill
        if level >= 20 or len(lines) == 1:
            return lines[0][0]
        top = lines[0][1]
        weakness = 120 - 2 * level
        delta = min(top - lines[-1][1], 100)
        best, best_value = lines[0][0], -10 ** 12
        for uci, score in lines:
            push = (weakness * (top - score) + delta * rng.randrange(weakness)) // 128
            if score + push >= best_value:
                best, best_value = uci, score + push
        return best


def ladder():
    bots = {}
    for rating, net, temp, rnd, depth, margin in MAIA_TABLE:
        bots[f"bot{rating}"] = Bot(f"bot{rating}", net, temp, rnd, depth, margin)
    for rating, level, depth in ENGINE_TABLE:
        bots[f"bot{rating}"] = Bot(f"bot{rating}", skill=level, depth=depth)
    for anchor in ANCHORS:
        bots[anchor] = Bot(anchor, int(anchor[4:]), argmax=True)
    return bots


def pairings(bots):
    names = [n for n in bots if n.startswith("bot")]
    pairs = []
    for i, a in enumerate(names):
        for b in names[i + 1:i + 3]:
            pairs.append((a, b))
    rating = {n: int(n[3:]) for n in names}
    for anchor, value in ANCHORS.items():
        near = sorted(names, key=lambda n: abs(rating[n] - value))[:4]
        pairs += [(anchor, n) for n in near]
    return pairs


def play_game(white, black, engines, rng, max_plies=300):
    board, moves = chess.Board(), []
    opener = engines["maia1500"]
    for _ in range(4):
        probs = {board.parse_uci(k).uci(): v for k, v in opener.policy(moves).items()}
        u = pick_weighted([(k, v) for k, v in probs.items() if v > 0], rng)
        board.push_uci(u)
        moves.append(u)
    while not board.is_game_over(claim_draw=True) and len(moves) < max_plies:
        bot = white if board.turn == chess.WHITE else black
        u = bot.move(board, moves, engines, rng)
        board.push_uci(u)
        moves.append(u)
    outcome = board.outcome(claim_draw=True)
    if outcome is None or outcome.winner is None:
        return 0.5
    return 1.0 if outcome.winner == chess.WHITE else 0.0


lock = threading.Lock()


def worker(jobs, games_done, seed):
    engines = {"fish": Fish()}
    for net in (1100, 1300, 1500, 1600, 1900):
        engines[f"maia{net}"] = Maia(net)
    bots = ladder()
    rng = random.Random(seed)
    try:
        for a, b, swap in jobs:
            white, black = (bots[b], bots[a]) if swap else (bots[a], bots[b])
            score = play_game(white, black, engines, rng)
            with lock:
                with open(RESULTS, "a", encoding="utf-8") as out:
                    out.write(json.dumps({"white": white.name, "black": black.name, "score": score}) + "\n")
                games_done[0] += 1
                print(f"{games_done[0]}: {white.name} - {black.name} {score}", flush=True)
    finally:
        for e in engines.values():
            e.close()


def play(games, workers):
    done = {}
    if os.path.exists(RESULTS):
        for line in open(RESULTS, encoding="utf-8"):
            r = json.loads(line)
            key = tuple(sorted((r["white"], r["black"])))
            done[key] = done.get(key, 0) + 1
    jobs = []
    for a, b in pairings(ladder()):
        have = done.get(tuple(sorted((a, b))), 0)
        jobs += [(a, b, i % 2 == 1) for i in range(have, games)]
    random.Random(1).shuffle(jobs)
    print(f"{len(jobs)} games to play", flush=True)
    counter = [0]
    chunks = [jobs[i::workers] for i in range(workers)]
    with ThreadPoolExecutor(workers) as pool:
        futures = [pool.submit(worker, chunk, counter, 100 + i) for i, chunk in enumerate(chunks)]
        for future in futures:
            future.result()  # re-raises a worker's error instead of hiding it


def fit():
    """Bradley-Terry ratings with the anchors fixed (Elo scale, 400 points = 10:1 odds)."""
    results = [json.loads(line) for line in open(RESULTS, encoding="utf-8")]
    names = sorted({r["white"] for r in results} | {r["black"] for r in results})
    rating = {n: ANCHORS.get(n, 1500.0) for n in names}
    for _ in range(2000):
        for n in names:
            if n in ANCHORS:
                continue
            expected, actual, games = 0.0, 0.0, 0
            for r in results:
                if n not in (r["white"], r["black"]):
                    continue
                other = r["black"] if r["white"] == n else r["white"]
                score = r["score"] if r["white"] == n else 1 - r["score"]
                expected += 1 / (1 + 10 ** ((rating[other] - rating[n]) / 400))
                actual += score
                games += 1
            if games:
                rating[n] += 8 * (actual - expected) / max(games, 1) * 4
    print(f"{'player':10s} {'label':>6s} {'measured':>9s} {'games':>6s}")
    for n in sorted(names, key=lambda x: rating[x]):
        games = sum(1 for r in results if n in (r["white"], r["black"]))
        label = ANCHORS.get(n) or int(n[3:])
        print(f"{n:10s} {label:6d} {rating[n]:9.0f} {games:6d}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["play", "fit"])
    parser.add_argument("--games", type=int, default=20)
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()
    if args.command == "play":
        play(args.games, args.workers)
    else:
        fit()
