#!/usr/bin/env python3
"""
Check the lesson files in app/src/main/assets/lessons with Stockfish, at authoring time.

The app's own parser (LessonParser, run by the unit test LessonAssetsTest) already makes sure
every position and move is legal. This script answers the chess questions a parser cannot:

  * Is every accepted move in a "play" or "quiz" step actually good? (flagged when it is
    clearly worse than the engine's best move)
  * Are there other moves just as good that the step does not accept? A lesson should never
    tell a player that an equally good move is wrong; add them to "expect" (or a "|" choice
    in a "line"), or give them "feedback" that explains why the lesson wants the other move.
  * Is the opponent's reply in a "line" a reasonable move?

Steps marked "rule": true (a particular move to practise a rule or a book line) are only
checked for blunders, since the engine's favourite move is not the point there.

Needs python-chess and Stockfish, both in tools/.cache (see tools/build_openings.py):

    tools/.cache/venv/Scripts/python.exe -I tools/check_lessons.py [file.json ...]

Set CHECK_DEPTH (default 18) to search less deeply; 14 checks all files in a few minutes.

With no arguments it checks every file. It prints one block per step that needs a look.
"""
import argparse
import glob
import json
import os
import sys

import chess
import chess.engine

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LESSONS = os.path.join(ROOT, "app", "src", "main", "assets", "lessons")
STOCKFISH = os.path.join(ROOT, "tools", ".cache", "stockfish", "stockfish", "stockfish-windows-x86-64-universal.exe")

DEPTH = int(os.environ.get("CHECK_DEPTH", "18"))
MULTIPV = 10
# An accepted move this much worse than the best (centipawns, mover's view) is flagged.
WORSE_CP = 80
# An unaccepted move within this margin of the best accepted move is listed as an alternative.
ALTERNATIVE_CP = 25
# In a rules step ("rule": true), an accepted move is flagged only when it throws away this much
# (centipawns) of an advantage capped at RULE_WINNING_CP: a blunder, not merely a slower win.
RULE_BLUNDER_CP = 150
RULE_WINNING_CP = 300
# Replies in a "line" this much worse than the opponent's best are flagged.
REPLY_CP = 150


def score_of(info, board):
    return info["score"].pov(board.turn)


def as_cp(score):
    return score.score(mate_score=100000)


def label(score):
    if score.is_mate():
        return f"mate {score.mate():+d}"
    return f"{score.score() / 100:+.2f}"


def analyse(engine, board):
    infos = engine.analyse(board, chess.engine.Limit(depth=DEPTH), multipv=min(MULTIPV, board.legal_moves.count()))
    return [(info["pv"][0], score_of(info, board)) for info in infos if info.get("pv")]


def score_move(engine, board, move):
    """The score of [move] from the mover's point of view."""
    mover = board.turn
    board.push(move)
    info = engine.analyse(board, chess.engine.Limit(depth=DEPTH - 1))
    board.pop()
    return info["score"].pov(mover)


def turns_of(step):
    if step.get("expect"):
        return [(step["expect"], None)]
    line = step.get("line") or []
    out = []
    for i in range(0, len(line), 2):
        out.append(([m.strip() for m in line[i].split("|") if m.strip()], line[i + 1] if i + 1 < len(line) else None))
    return out


def check_turn(engine, board, accepted, explained, where, notes, rule=False):
    ranked = analyse(engine, board)
    if not ranked:
        return
    best_move, best = ranked[0]
    scores = {m.uci(): s for m, s in ranked}
    for uci in accepted:
        if uci not in scores:
            scores[uci] = score_move(engine, board, chess.Move.from_uci(uci))
    best_accepted = max((scores[u] for u in accepted), key=as_cp)
    for uci in accepted:
        if rule:
            # A rules move only has to avoid a real blunder; it may be far from the best move
            # when the side is winning anyway.
            bad = as_cp(scores[uci]) < min(as_cp(best), RULE_WINNING_CP) - RULE_BLUNDER_CP
        else:
            bad = as_cp(best) - as_cp(scores[uci]) > WORSE_CP
        if bad:
            notes.append(f"{where}: accepted {board.san(chess.Move.from_uci(uci))} ({label(scores[uci])}) is worse than "
                         f"{board.san(best_move)} ({label(best)})")
    if rule:
        return
    for move, score in ranked:
        if move.uci() in accepted or move.uci() in explained:
            continue
        if best_accepted.is_mate() and best_accepted.mate() > 0:
            same = score.is_mate() and 0 < score.mate() <= best_accepted.mate()
        else:
            same = as_cp(best_accepted) - as_cp(score) <= ALTERNATIVE_CP
        if same:
            notes.append(f"{where}: {board.san(move)} ({move.uci()}, {label(score)}) is as good as the accepted move "
                         f"({label(best_accepted)}); accept it or explain it in feedback")


def check_reply(engine, board, reply, where, notes):
    ranked = analyse(engine, board)
    if not ranked:
        return
    best_move, best = ranked[0]
    scores = {m.uci(): s for m, s in ranked}
    if reply not in scores:
        scores[reply] = score_move(engine, board, chess.Move.from_uci(reply))
    gap = as_cp(best) - as_cp(scores[reply])
    if gap > REPLY_CP:
        notes.append(f"{where}: reply {board.san(chess.Move.from_uci(reply))} ({label(scores[reply])}) is a big mistake; "
                     f"best is {board.san(best_move)} ({label(best)}). Fine if the lesson shows a mistake on purpose.")


def is_mate_after(board, move):
    board.push(move)
    mate = board.is_checkmate()
    board.pop()
    return mate


def check_file(engine, path):
    with open(path, encoding="utf-8") as f:
        course = json.load(f)
    notes = []
    for lesson in course["lessons"]:
        carried = None
        for i, step in enumerate(lesson["steps"]):
            where = f"{os.path.basename(path)} {lesson['id']} step {i + 1}"
            board = chess.Board(step["fen"]) if step.get("fen") else (carried.copy() if carried else chess.Board())
            kind = step["type"]
            if not board.is_valid():
                notes.append(f"{where}: position is not valid ({board.status()!r}); the app will refuse this file")
                break
            try:
                if kind == "explain" and step.get("move"):
                    board.push_uci(step["move"])
                elif kind in ("play", "quiz") and step.get("goal") == "mate":
                    if not any(board.gives_check(m) and is_mate_after(board, m) for m in board.legal_moves):
                        notes.append(f"{where}: no move mates here")
                elif kind in ("play", "quiz") and not step.get("goal"):
                    # A rules exercise ("move the king to d1", "play the book move") is only
                    # checked for blunders, not compared with the engine's best move.
                    for accepted, reply in turns_of(step):
                        for uci in accepted:
                            if chess.Move.from_uci(uci) not in board.legal_moves:
                                raise ValueError(f"illegal move {uci}")
                        check_turn(engine, board, accepted, step.get("feedback", {}), where, notes, rule=bool(step.get("rule")))
                        board.push_uci(accepted[0])
                        if reply:
                            check_reply(engine, board, reply, where, notes)
                            board.push_uci(reply)
            except ValueError as e:
                notes.append(f"{where}: {e}; the app will refuse this file")
                break
            if kind != "watch" or step.get("fen"):
                carried = board
    return notes


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("files", nargs="*", help="lesson files (default: all)")
    args = parser.parse_args()
    files = args.files or sorted(glob.glob(os.path.join(LESSONS, "*.json")))
    engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    engine.configure({"Threads": 4, "Hash": 256})
    total = 0
    try:
        for path in files:
            print(f"checking {os.path.basename(path)}", file=sys.stderr, flush=True)
            notes = check_file(engine, path)
            total += len(notes)
            for note in notes:
                print(note, flush=True)
    finally:
        engine.quit()
    print(f"{len(files)} files checked, {total} notes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
