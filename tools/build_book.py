"""Builds the opening book for game reviews (Phase 13B): app/src/main/assets/book.bin.

A move counts as book when its position was reached in at least --min-games master games (the
Lichess masters explorer), up to --max-ply plies. The book holds one 64-bit fingerprint per position
(FNV-1a of the app's repetition key: board, side, castling, and an en passant square only when a
capture there is legal), sorted, big-endian, so the app can binary-search it.

Crawls breadth-first from the start, one request at a time (the token and the polite client from
build_repertoire.py), caching every answer in tools/.cache/book/masters.jsonl so a stopped run
resumes and a rebuild asks nothing.

Run: tools/.cache/venv/Scripts/python.exe tools/build_book.py [--min-games 50] [--max-ply 30]
"""
import argparse
import json
import os
import struct
import sys
import urllib.parse
from collections import deque

import chess

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_repertoire import ExplorerClient, load_token  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache", "book", "masters.jsonl")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "book.bin")
MASTERS_URL = "https://explorer.lichess.ovh/masters"


def key_of(board):
    """The app's Position.repetitionKey(): the first four FEN fields, en passant only when legal."""
    return " ".join(board.fen(en_passant="legal").split(" ")[:4])


def fingerprint(key):
    """FNV-1a, 64-bit, over the key's UTF-8 bytes; the app computes the same (domain/opening/OpeningBook.kt)."""
    h = 0xcbf29ce484222325
    for b in key.encode("utf-8"):
        h ^= b
        h = (h * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF
    return h


class Masters(ExplorerClient):
    """The masters database instead of the Lichess one: same politeness, a different address."""

    def url(self, fen, ratings, speeds, since, until, moves):
        return MASTERS_URL + "?" + urllib.parse.urlencode({"fen": fen, "moves": moves, "topGames": 0})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--min-games", type=int, default=50)
    parser.add_argument("--max-ply", type=int, default=30)
    parser.add_argument("--pause", type=float, default=0.3)
    args = parser.parse_args()

    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    cache = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            for line in f:
                entry = json.loads(line)
                cache[entry["k"]] = entry["moves"]
    client = Masters(load_token(), pause=args.pause)
    asked = 0

    book = set()
    seen = set()
    queue = deque([(chess.Board(), 0)])
    with open(CACHE, "a", encoding="utf-8", newline="\n") as log:
        while queue:
            board, ply = queue.popleft()
            key = key_of(board)
            if key in seen:
                continue
            seen.add(key)
            book.add(fingerprint(key))
            if ply >= args.max_ply:
                continue
            moves = cache.get(key)
            if moves is None:
                answer = client(board.fen(), [], [], None, None, 30)
                moves = [[m["uci"], int(m.get("white", 0)) + int(m.get("draws", 0)) + int(m.get("black", 0))] for m in answer.get("moves", [])]
                cache[key] = moves
                log.write(json.dumps({"k": key, "moves": moves}) + "\n")
                log.flush()
                asked += 1
                if asked % 200 == 0:
                    print(f"  {asked:,} asked, {len(book):,} book positions, {len(queue):,} waiting", flush=True)
            for uci, games in moves:
                if games < args.min_games:
                    continue
                try:
                    move = board.parse_uci(uci)
                except ValueError:
                    continue
                # The explorer writes castling as the king taking its rook (e1h1).
                child = board.copy(stack=False)
                child.push(move)
                queue.append((child, ply + 1))

    fingerprints = sorted(book)
    with open(OUT, "wb") as f:
        for h in fingerprints:
            f.write(struct.pack(">Q", h))
    print(f"{len(fingerprints):,} book positions ({asked:,} new requests) written to {OUT}")


if __name__ == "__main__":
    main()
