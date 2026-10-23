#!/usr/bin/env python3
"""
Build the app's opening database from lichess-org/chess-openings (CC0).

Source: https://github.com/lichess-org/chess-openings  (a.tsv to e.tsv: eco, name, pgn)
Needs python-chess (GPL-3), used only at build time. On this PC it lives in a private
virtual environment so it never touches the system Python:

    C:\\Users\\HP\\AppData\\Local\\Python\\pythoncore-3.14-64\\python.exe -m venv tools/.cache/venv
    tools/.cache/venv/Scripts/python.exe -m pip install chess==1.11.2

Usage (from the repository root):
    tools/.cache/venv/Scripts/python.exe -I tools/build_openings.py tools/.cache/openings ^
        -o app/src/main/assets/openings.tsv.gz

Output: gzip'd TSV, one named line per row, in the source order (ECO, then name):
    eco <TAB> name <TAB> uci

`uci` is the move sequence from the starting position, space-separated. The app replays
each line with its own move generator when it loads the file, so positions (and
transpositions between lines) are worked out on the device and the file stays small.
"""
import argparse
import csv
import gzip
import io
import os
import sys

import chess
import chess.pgn


def read_rows(folder):
    for letter in "abcde":
        path = os.path.join(folder, f"{letter}.tsv")
        with open(path, encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f, delimiter="\t"):
                yield row["eco"].strip(), row["name"].strip(), row["pgn"].strip()


def pgn_to_uci(pgn):
    game = chess.pgn.read_game(io.StringIO(pgn))
    if game is None or game.errors:
        raise ValueError(f"unreadable PGN: {pgn}")
    board = game.board()
    moves = []
    for move in game.mainline_moves():
        if move not in board.legal_moves:
            raise ValueError(f"illegal move {move} in: {pgn}")
        moves.append(move.uci())
        board.push(move)
    if not moves:
        raise ValueError(f"empty line: {pgn}")
    return moves


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("source", help="folder holding a.tsv to e.tsv")
    parser.add_argument("-o", "--output", required=True)
    args = parser.parse_args()

    rows, seen = [], set()
    for eco, name, pgn in read_rows(args.source):
        if "\t" in name:
            raise ValueError(f"tab in name: {name!r}")
        uci = " ".join(pgn_to_uci(pgn))
        if uci in seen:
            print(f"duplicate line skipped: {eco} {name}", file=sys.stderr)
            continue
        seen.add(uci)
        rows.append(f"{eco}\t{name}\t{uci}")

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    # mtime=0 keeps the file byte-identical between runs on the same input.
    with open(args.output, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as gz:
        gz.write(("\n".join(rows) + "\n").encode("utf-8"))

    plies = [len(r.split("\t")[2].split()) for r in rows]
    print(f"{len(rows)} lines, longest {max(plies)} plies, "
          f"{os.path.getsize(args.output) / 1024:.0f} KB -> {args.output}")


if __name__ == "__main__":
    main()
