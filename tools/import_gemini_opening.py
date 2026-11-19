#!/usr/bin/env python3
"""
Import an opening drafted by Gemini (docs/gemini/2_opening_courses.txt) into the app's files,
after checking that every move in it is legal (plan Phase 15).

One Gemini answer holds four parts. They go to:

  config      tools/repertoire/<id>.json            (build_repertoire.py builds the tree)
  notes       tools/repertoire/<id>.notes.json      (build_repertoire.py checks each claim)
  ideas       app/src/main/assets/lessons/<course id>.json
  variations  app/src/main/assets/lessons/<course id>.json, one file each

Nothing is written for a file with errors. After importing, check the chess with Stockfish:

    tools/.cache/venv/Scripts/python.exe -I tools/check_lessons.py app/src/main/assets/lessons/openings-<id>*.json
    tools/.cache/venv/Scripts/python.exe -I tools/build_repertoire.py build <id> --source explorer

Run from the repository root:

    tools/.cache/venv/Scripts/python.exe -I tools/import_gemini_opening.py docs/gemini/openings/*.json [--dry-run]
"""
import argparse
import glob
import json
import os
import sys

import chess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIGS = os.path.join(ROOT, "tools", "repertoire")
LESSONS = os.path.join(ROOT, "app", "src", "main", "assets", "lessons")
STEP_TYPES = {"explain", "play", "quiz"}
LEVELS = {"beginner", "intermediate", "advanced"}


def board_after(path_san):
    """The board after a SAN path from the start ("e4 c6 d4"); raises on an illegal move."""
    board = chess.Board()
    for san in path_san.split():
        board.push_san(san)
    return board


def check_config(config, errors):
    if not config.get("start"):
        errors.append("config: no start line")
        return
    try:
        board_after(config["start"])
    except ValueError as e:
        errors.append(f"config start {config['start']!r}: {e}")
    for path, move in config.get("choices", {}).items():
        try:
            board = board_after(path)
            if not path.startswith(config["start"]):
                errors.append(f"config choice {path!r}: does not start with {config['start']!r}")
            if board.turn != (chess.WHITE if config["side"] == "white" else chess.BLACK):
                errors.append(f"config choice {path!r}: it is not {config['side']}'s move there")
            board.push_san(move)
        except ValueError as e:
            errors.append(f"config choice {path!r} -> {move!r}: {e}")


def check_notes(notes, errors):
    for note in notes:
        where = f"note {note.get('path')!r}"
        try:
            board = board_after(note["path"])
        except ValueError as e:
            errors.append(f"{where}: {e}")
            continue
        sentences = [s for s in note["text"].replace("...", "…").split(". ") if s]
        for claim in note.get("claims", []):
            if claim.get("sentence", 0) >= len(sentences):
                errors.append(f"{where}: claim points at sentence {claim.get('sentence')} of {len(sentences)}")
            try:
                b = board.copy()
                if claim["type"] == "plan":
                    for san in claim.get("after", []):
                        b.push_san(san)
                    b.push_san(claim["move"])
                elif claim["type"] == "line":
                    for san in claim["line"]:
                        b.push_san(san)
                elif claim["type"] == "threat":
                    # A threat is what the other side would do if this side passed.
                    b.push(chess.Move.null())
                    b.push_san(claim["move"])
                elif claim["type"] in ("verdict", "best", "sound"):
                    b.push_san(claim["move"])
                elif claim["type"] == "wins":
                    for san in claim["line"]:
                        b.push_san(san)
                else:
                    errors.append(f"{where}: unknown claim type {claim['type']!r}")
            except ValueError as e:
                errors.append(f"{where}: claim {claim}: {e}")


def check_course(course, errors):
    cid = course.get("id", "?")
    for key in ("id", "category", "title", "intro", "level", "order", "side", "opening", "battleLine", "lessons"):
        if key not in course:
            errors.append(f"{cid}: no {key!r}")
    if course.get("level") not in LEVELS:
        errors.append(f"{cid}: level {course.get('level')!r}")
    for line in (course.get("variation") or "").split(","):
        if not line.strip():
            continue
        board = chess.Board()
        try:
            for uci in line.split():
                move = chess.Move.from_uci(uci)
                if move not in board.legal_moves:
                    raise ValueError(f"illegal {uci}")
                board.push(move)
        except ValueError as e:
            errors.append(f"{cid} variation {line.strip()!r}: {e}")
    for lesson in course.get("lessons", []):
        check_lesson(cid, lesson, errors)


def impossible_pieces(board):
    """What a position early in a game cannot have: a second queen, a third rook, knight or bishop, two bishops on one colour."""
    if board.fullmove_number > 25:
        return None
    for color in (chess.WHITE, chess.BLACK):
        name = "white" if color == chess.WHITE else "black"
        for piece, most in ((chess.QUEEN, 1), (chess.ROOK, 2), (chess.KNIGHT, 2), (chess.BISHOP, 2)):
            if len(board.pieces(piece, color)) > most:
                return f"{len(board.pieces(piece, color))} {name} {chess.piece_name(piece)}s"
        bishops = board.pieces(chess.BISHOP, color)
        if len(bishops) == 2 and len({(chess.square_file(s) + chess.square_rank(s)) % 2 for s in bishops}) == 1:
            return f"two {name} bishops on one colour"
    return None


def push(board, uci, where, errors):
    move = chess.Move.from_uci(uci)
    if move not in board.legal_moves:
        errors.append(f"{where}: {uci} is not legal in {board.fen()}")
        return False
    board.push(move)
    return True


def check_lesson(cid, lesson, errors):
    """Walks the lesson the way the app and check_lessons.py do: a step's fen resets the board."""
    lid = lesson.get("id", "?")
    if not lid.startswith(cid):
        errors.append(f"{lid}: lesson id does not start with the course id {cid}")
    if lesson.get("orientation") not in ("white", "black"):
        errors.append(f"{lid}: orientation {lesson.get('orientation')!r}")
    board = chess.Board()
    for i, step in enumerate(lesson.get("steps", [])):
        where = f"{lid} step {i + 1}"
        kind = step.get("type")
        if kind not in STEP_TYPES:
            errors.append(f"{where}: type {kind!r}")
            return
        if step.get("fen"):
            try:
                board = chess.Board(step["fen"])
            except ValueError as e:
                errors.append(f"{where}: fen: {e}")
                return
            if not board.is_valid():
                errors.append(f"{where}: fen is not a valid position ({board.status()!r})")
                return
            extra = impossible_pieces(board)
            if extra:
                errors.append(f"{where}: fen has {extra}, which no opening reaches")
        for arrow in step.get("arrows", []):
            squares = arrow.split(":")[0]
            if len(squares) != 4 or squares[:2] not in chess.SQUARE_NAMES or squares[2:] not in chess.SQUARE_NAMES:
                errors.append(f"{where}: arrow {arrow!r}")
        if kind == "explain":
            if step.get("move") and not push(board, step["move"], where, errors):
                return
            continue
        for key in ("text", "hint", "ifWrong", "ifRight"):
            if not step.get(key):
                errors.append(f"{where}: no {key!r}")
        for uci in step.get("feedback", {}):
            if chess.Move.from_uci(uci) not in board.legal_moves:
                errors.append(f"{where}: feedback for {uci}, which is not legal here")
        if step.get("expect"):
            for uci in step["expect"]:
                if chess.Move.from_uci(uci) not in board.legal_moves:
                    errors.append(f"{where}: expected {uci} is not legal in {board.fen()}")
                    return
            board.push_uci(step["expect"][0])
        elif step.get("line"):
            for uci in step["line"]:
                if not push(board, uci.split("|")[0].strip(), where, errors):
                    return
        else:
            errors.append(f"{where}: neither 'expect' nor 'line'")


def courses_of(data):
    return [data["ideas"]] + list(data.get("variations", []))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("files", nargs="+")
    parser.add_argument("--dry-run", action="store_true", help="check only, write nothing")
    args = parser.parse_args()
    files = [f for pattern in args.files for f in (glob.glob(pattern) or [pattern])]
    seen_courses, seen_lessons = {}, {}
    failed = 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        errors = []
        config = data["config"]
        check_config(config, errors)
        check_notes(data.get("notes", []), errors)
        for course in courses_of(data):
            check_course(course, errors)
            if course["opening"] != config["id"]:
                errors.append(f"{course['id']}: opening {course['opening']!r} is not {config['id']!r}")
            if course["side"] != config["side"]:
                errors.append(f"{course['id']}: side {course['side']!r} is not {config['side']!r}")
            if course["id"] in seen_courses:
                errors.append(f"course id {course['id']} also in {seen_courses[course['id']]}")
            seen_courses[course["id"]] = path
            for lesson in course["lessons"]:
                if lesson["id"] in seen_lessons:
                    errors.append(f"lesson id {lesson['id']} also in {seen_lessons[lesson['id']]}")
                seen_lessons[lesson["id"]] = path
        print(f"{os.path.basename(path)}: {config['id']}, {len(data.get('notes', []))} notes, "
              f"{len(courses_of(data))} courses, {sum(len(c['lessons']) for c in courses_of(data))} lessons, "
              f"{len(errors)} problem(s)")
        for e in errors:
            print("  " + e)
        if errors:
            failed += 1
            continue
        if args.dry_run:
            continue
        with open(os.path.join(CONFIGS, f"{config['id']}.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(config, f, ensure_ascii=False, indent=2)
            f.write("\n")
        with open(os.path.join(CONFIGS, f"{config['id']}.notes.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"notes": data.get("notes", [])}, f, ensure_ascii=False, indent=2)
            f.write("\n")
        for course in courses_of(data):
            with open(os.path.join(LESSONS, f"{course['id']}.json"), "w", encoding="utf-8", newline="\n") as f:
                json.dump(course, f, ensure_ascii=False, indent=2)
                f.write("\n")
    for lesson_id, path in seen_lessons.items():
        if lesson_id in seen_courses:
            print(f"lesson id {lesson_id} ({os.path.basename(path)}) is also a course id; videos and links would mix them up")
            failed += 1
    # Ids already in the app that the new files do not replace.
    for existing in glob.glob(os.path.join(LESSONS, "*.json")):
        name = os.path.basename(existing)[:-5]
        if name in seen_courses:
            continue
        with open(existing, encoding="utf-8") as f:
            other = json.load(f)
        for lesson in other.get("lessons", []):
            if lesson["id"] in seen_lessons:
                print(f"lesson id {lesson['id']} is already used by {name}")
                failed += 1
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
