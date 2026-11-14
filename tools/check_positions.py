"""Checks app/src/main/assets/positions/positions.json with Stockfish (Phase 12, step 3).

A position is a FEN, or {"fen", "goal", "aim"} to override its set.
Every position must be legal, and its goal must hold with best play from the side to move:
  win      a win (mate, or at least +3.00), and it is an endgame
  hold     a draw (within 1.00 of level), and at least one move loses (so there is something to find)
  convert  a clear but not finished edge, +1.40 to +4.00, so there is something to convert

Run: tools/.cache/venv/Scripts/python.exe tools/check_positions.py
"""
import argparse
import json
import os
import sys

import chess
import chess.engine

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STOCKFISH = os.path.join(ROOT, "tools", ".cache", "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "positions", "positions.json")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stockfish", default=STOCKFISH)
    parser.add_argument("--time", type=float, default=1.5)
    args = parser.parse_args()
    with open(ASSET, encoding="utf-8") as f:
        sets = json.load(f)["sets"]
    engine = chess.engine.SimpleEngine.popen_uci(args.stockfish)
    engine.configure({"Threads": 4, "Hash": 256})
    failures = 0
    for s in sets:
        for i, entry in enumerate(s["positions"], 1):
            fen = entry if isinstance(entry, str) else entry["fen"]
            goal = s["goal"] if isinstance(entry, str) else entry.get("goal", s["goal"])
            name = f"{s['id']}-{i}"
            board = chess.Board(fen)
            if not board.is_valid():
                print(f"FAIL {name}: not a legal position ({board.status()})")
                failures += 1
                continue
            lines = engine.analyse(board, chess.engine.Limit(time=args.time), multipv=board.legal_moves.count())
            scores = [line["score"].pov(board.turn) for line in lines]
            best = scores[0]
            cp = best.score(mate_score=100000)
            if goal == "win":
                ok = best.is_mate() and best.mate() > 0 or cp >= 300
            elif goal == "hold":
                ok = not best.is_mate() and abs(cp) <= 100 and any(x.score(mate_score=100000) <= -300 for x in scores)
            elif goal == "convert":
                ok = not best.is_mate() and 140 <= cp <= 400
            else:
                ok = False
            print(f"{'ok  ' if ok else 'FAIL'} {name:22} {goal:8} best {board.san(lines[0]['pv'][0]):6} {best}")
            failures += 0 if ok else 1
    engine.quit()
    print(f"{failures} failed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
