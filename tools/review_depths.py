"""Which search depth reviews games well enough (Phase 13B).

Downloads Lichess games by id and, for every position, asks Stockfish 19 for two lines at several
depths and at a much deeper reference, timing each search. Writes one JSON per game for
ReviewDepthTest, which grades every move at each depth with the app's own GameReviewer and counts
how often the grade differs from the reference's.

Run: tools/.cache/venv/Scripts/python.exe tools/review_depths.py ID [ID ...] [--depths 14,16,18,20] [--reference 24]
"""
import argparse
import io
import json
import os
import time
import urllib.request

import chess
import chess.engine
import chess.pgn

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STOCKFISH = os.path.join(ROOT, "tools", ".cache", "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")
OUT = os.path.join(ROOT, "app", "src", "test", "resources", "reviews", "depths")


def fetch(game_id):
    request = urllib.request.Request(f"https://lichess.org/game/export/{game_id}?clocks=false&evals=false",
                                     headers={"Accept": "application/x-chess-pgn", "User-Agent": "chess-tutor review_depths.py"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def search(engine, board, depth):
    engine.protocol.send_line("ucinewgame")
    started = time.perf_counter()
    lines = engine.analyse(board, chess.engine.Limit(depth=depth), multipv=2)
    took = time.perf_counter() - started
    first = lines[0]["score"].white()
    out = {"cp": None if first.is_mate() else first.score(), "mate": first.mate() if first.is_mate() else None,
           "best": lines[0]["pv"][0].uci(), "ms": round(took * 1000)}
    if len(lines) > 1:
        second = lines[1]["score"].white()
        out["secondCp"] = None if second.is_mate() else second.score()
        out["secondMate"] = second.mate() if second.is_mate() else None
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("ids", nargs="+")
    parser.add_argument("--depths", default="14,16,18,20")
    parser.add_argument("--reference", type=int, default=24)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    depths = [int(d) for d in args.depths.split(",")]
    os.makedirs(OUT, exist_ok=True)
    engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    engine.configure({"Threads": args.threads, "Hash": 256})
    for game_id in args.ids:
        path = os.path.join(OUT, f"{game_id}.json")
        if os.path.exists(path):
            print(f"{game_id}: already done")
            continue
        game = chess.pgn.read_game(io.StringIO(fetch(game_id)))
        board = game.board()
        moves = [m.uci() for m in game.mainline_moves()]
        plies = []
        for ply in range(len(moves) + 1):
            entry = {"ply": ply}
            if not board.is_game_over():
                for depth in depths + [args.reference]:
                    entry[str(depth)] = search(engine, board, depth)
            plies.append(entry)
            if ply < len(moves):
                board.push_uci(moves[ply])
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"id": game_id, "moves": moves, "depths": depths, "reference": args.reference, "plies": plies}, f, separators=(",", ":"))
        print(f"{game_id}: {len(moves)} moves", flush=True)
    engine.quit()


if __name__ == "__main__":
    main()
