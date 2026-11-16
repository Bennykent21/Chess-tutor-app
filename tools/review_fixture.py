"""Makes a review test fixture from a real game (Phase 13B).

Runs Stockfish 19 over every position of a PGN the way the app's GameAnalyzer does (depth 14, two
lines) and, for comparison, much deeper. Writes JSON the unit tests read with GameReviewer, so a
real game the developer found wrongly reviewed becomes a test.

Run: tools/.cache/venv/Scripts/python.exe tools/review_fixture.py game.pgn out.json [--deep 22]
"""
import argparse
import io
import json
import os

import chess
import chess.engine
import chess.pgn

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STOCKFISH = os.path.join(ROOT, "tools", ".cache", "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")


def evals(engine, board, depth):
    """The app's PlyEval for one position, White's side: score, best move, line, second line."""
    if board.is_game_over():
        return None
    lines = engine.analyse(board, chess.engine.Limit(depth=depth), multipv=2)
    first = lines[0]
    score = first["score"].white()
    out = {
        "cp": None if score.is_mate() else score.score(),
        "mate": score.mate() if score.is_mate() else None,
        "best": first["pv"][0].uci(),
        "line": [m.uci() for m in first["pv"][:8]],
        "depth": first.get("depth"),
    }
    if len(lines) > 1:
        second = lines[1]["score"].white()
        out["secondCp"] = None if second.is_mate() else second.score()
        out["secondMate"] = second.mate() if second.is_mate() else None
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pgn")
    parser.add_argument("out")
    parser.add_argument("--depth", type=int, default=14)
    parser.add_argument("--deep", type=int, default=22)
    parser.add_argument("--stockfish", default=STOCKFISH)
    args = parser.parse_args()
    with open(args.pgn, encoding="utf-8") as f:
        game = chess.pgn.read_game(io.StringIO(f.read()))
    engine = chess.engine.SimpleEngine.popen_uci(args.stockfish)
    # One thread and a fresh hash per position, so the fixture is the same on every run.
    engine.configure({"Threads": 1, "Hash": 64})
    board = game.board()
    moves = [m.uci() for m in game.mainline_moves()]
    plies = []
    for ply in range(len(moves) + 1):
        engine.protocol.send_line("ucinewgame")
        app = evals(engine, board, args.depth)
        deep = evals(engine, board, args.deep)
        plies.append({"ply": ply, "fen": board.fen(), "app": app, "deep": deep})
        if ply < len(moves):
            board.push_uci(moves[ply])
    engine.quit()
    headers = dict(game.headers)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"headers": headers, "moves": moves, "plies": plies}, f, indent=1)
    print(f"{len(plies)} positions written to {args.out}")


if __name__ == "__main__":
    main()
