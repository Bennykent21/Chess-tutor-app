#!/usr/bin/env python3
"""
Tests for tools/build_repertoire.py, with a made-up engine so they run in seconds:

    tools/.cache/venv/Scripts/python.exe -I tools/test_build_repertoire.py
"""
import gzip
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import chess  # noqa: E402

import build_repertoire as br  # noqa: E402


class FakeAnalyzer:
    """Every move scores 0 for the side to move, except the scores given per position."""

    def __init__(self, scores=None, multipv=4):
        self.scores = scores or {}  # path (SAN from the start) -> {san: cp for the side to move}
        self.multipv = multipv
        self.asked = 0

    def _lines(self, board):
        by_key = {}
        for path, moves in self.scores.items():
            b = br.board_at(path)
            # The SAN "*" sets the score of every move not named.
            by_key[br.fen4(b)] = {(None if san == "*" else b.parse_san(san).uci()): cp for san, cp in moves.items()}
        given = dict(by_key.get(br.fen4(board), {}))
        default = given.pop(None, 0)
        rest = sorted(m.uci() for m in board.legal_moves if m.uci() not in given)
        ranked = sorted(given.items(), key=lambda kv: -kv[1]) + [(u, default) for u in rest]
        ranked.sort(key=lambda kv: -kv[1])
        return [(u, cp, None) for u, cp in ranked[:self.multipv]]

    def analyse_many(self, boards):
        out = {}
        for board in boards:
            if board.is_game_over(claim_draw=False):
                continue
            self.asked += 1
            out[br.fen4(board)] = self._lines(board)
        return out

    def analyse(self, board):
        return self.analyse_many([board]).get(br.fen4(board))

    def close(self):
        pass


def params(**overrides):
    args = br.parse_args(["build", "test"])
    for name, value in overrides.items():
        setattr(args, name, value)
    return br.Params(args)


def counts_of(lines, band=1):
    """SAN prefix counts for made-up games: {"e4 e5 ...": games} all in one band."""
    counts = {}
    for line, games in lines.items():
        tokens = line.split()
        for i in range(1, len(tokens) + 1):
            row = counts.setdefault(" ".join(tokens[:i]), [0] * br.NBANDS)
            row[band] += games
    return counts


ITALIAN = {"id": "t", "title": "Test Italian", "side": "white", "start": "e4 e5 Nf3 Nc6 Bc4", "choices": {}}
SMALL = dict(min_games=50, min_reply_games=5, min_band_games=20, deep_reach=0.01)


class Reading(unittest.TestCase):
    def test_lichess_movetext(self):
        text = "1. e4 { [%eval 0.17] [%clk 0:03:00] } 1... e5?! { [%clk 0:03:00] } 2. Nf3 $1 Nc6 3. Bc4 1-0"
        self.assertEqual(br.san_moves(text, 20), ["e4", "e5", "Nf3", "Nc6", "Bc4"])
        self.assertEqual(br.san_moves(text, 2), ["e4", "e5"])
        self.assertEqual(br.san_moves("1. e4 { [%clk 0:0", 5), ["e4"])
        self.assertEqual(br.san_moves("1. O-O-O+ 1-0", 5), ["O-O-O+"])

    def test_speed_and_bands(self):
        self.assertEqual(br.speed_of("Rated Blitz tournament https://lichess.org/tournament/x"), "blitz")
        self.assertIsNone(br.speed_of("Casual Rapid game"))
        self.assertEqual([br.band_of(r) for r in (900, 1399, 1400, 1749, 1750, 2049, 2050, 2800)], [0, 0, 1, 1, 2, 2, 3, 3])

    def test_zst_slice_drops_only_the_broken_game(self):
        from compression import zstd
        # Enough games for several zstd blocks, so a cut leaves whole games before it.
        text = "".join(br.sample_games(1500))
        with tempfile.TemporaryDirectory() as tmp:
            plain = os.path.join(tmp, "g.pgn")
            with open(plain, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            whole = os.path.join(tmp, "g.pgn.zst")
            data = zstd.compress(text.encode("utf-8"))
            with open(whole, "wb") as f:
                f.write(data)
            sliced = os.path.join(tmp, "s.pgn.zst")
            with open(sliced, "wb") as f:
                f.write(data[:len(data) * 6 // 10])
            self.assertEqual(len(list(br.games(br.PgnSource(plain)))), 1500)
            self.assertEqual(len(list(br.games(br.PgnSource(whole)))), 1500)
            source = br.PgnSource(sliced)
            got = list(br.games(source))
            self.assertTrue(source.truncated)
            self.assertTrue(0 < len(got) < 1500)
            self.assertTrue(all(movetext.endswith("1-0") for _, movetext in got))
            self.assertEqual(len(list(br.games(br.PgnSource(whole), skip=25))), 1475)


class Counting(unittest.TestCase):
    def game(self, event, white, black, moves):
        return {"Event": event, "WhiteElo": str(white), "BlackElo": str(black)}, moves

    def test_each_move_counts_in_its_players_band(self):
        counter = br.Counter([["e4", "c6"]], 6, 1000)
        counter.add(*self.game("Rated Rapid game", 1500, 2100, "1. e4 c6 2. d4 d5 1-0"))
        counter.add(*self.game("Rated Bullet game", 1500, 2100, "1. e4 c6 2. d4 d5 1-0"))
        counter.add(*self.game("Casual Rapid game", 1500, 2100, "1. e4 c6 2. d4 d5 1-0"))
        counter.add(*self.game("Rated Rapid game", 1500, 2100, "1. e4 e5 2. d4 d5 1-0"))
        self.assertEqual(counter.matched, 1)
        self.assertEqual(counter.counts["e4"][:4], [0, 1, 0, 0])
        self.assertEqual(counter.counts["e4 c6"][:4], [0, 0, 0, 1])
        self.assertEqual(counter.counts["e4 c6 d4 d5"][:4], [0, 0, 0, 1])

    def test_lossy_counting_keeps_common_prefixes_exact(self):
        counter = br.Counter([["e4"]], 4, 2)
        tails = ["a6", "h6", "a5", "h5", "Na6", "Nh6", "f6", "g6"]
        for i, tail in enumerate(tails):
            counter.add(*self.game("Rated Blitz game", 1500, 1500, f"1. e4 c6 2. d4 {tail} 1-0"))
        self.assertEqual(sum(counter.counts["e4 c6 d4"][:4]), len(tails))
        self.assertLess(len(counter.counts), 3 + len(tails))
        self.assertGreater(counter.pruned, 0)
        self.assertEqual(counter.meta()["maxUndercount"], counter.bucket - 1)

    def test_checkpoint_round_trip(self):
        counter = br.Counter([["e4"]], 4, 100)
        counter.add(*self.game("Rated Blitz game", 1500, 1500, "1. e4 c6 1-0"))
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "c.json.gz")
            counter.save(path, "x.pgn.zst", done=False)
            again, done = br.Counter.load(path, [["e4"]], 4, 100, "x.pgn.zst")
            self.assertFalse(done)
            self.assertEqual(again.counts, counter.counts)
            self.assertEqual(again.games, 1)
            with self.assertRaises(SystemExit):
                br.Counter.load(path, [["d4"]], 4, 100, "x.pgn.zst")

    def test_transpositions_meet_at_one_position(self):
        counts = counts_of({"e4 e5 Nf3 Nc6 Bc4": 30, "Nf3 Nc6 e4 e5 Bc4": 10})
        edges = br.position_edges(counts, [])
        key = br.fen4(br.board_at("e4 e5 Nf3 Nc6"))
        self.assertEqual(sum(edges[key]["f1c4"]), 40)
        italian_only = br.position_edges(counts, "e4 e5 Nf3 Nc6 Bc4".split())
        self.assertEqual(sum(italian_only[key]["f1c4"]), 30)


class Keys(unittest.TestCase):
    def test_en_passant_only_when_legal(self):
        board = br.board_at("e4 d5 e5 f5")
        self.assertTrue(br.fen4(board).endswith(" f6"))
        board = br.board_at("e4")
        self.assertTrue(br.fen4(board).endswith("KQkq -"))
        pinned = chess.Board("4k3/2p5/8/KP5r/8/8/8/8 b - - 0 1")
        pinned.push_uci("c7c5")
        self.assertTrue(br.fen4(pinned).endswith(" -"))

    def test_castling_rights_follow_rooks(self):
        board = chess.Board()
        for uci in "g2g3 a7a6 f1g2 a6a5 g2b7 a5a4 b7a8".split():
            board.push_uci(uci)
        self.assertEqual(br.fen4(board).split()[2], "KQk")


class Tree(unittest.TestCase):
    LINES = {
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4 exd4": 400,
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Be7 O-O O-O": 300,
        "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxe5 Qg5": 120,
        "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxd4 exd4": 80,
        "e4 e5 Nf3 Nc6 Bc4 h6 d4": 3,
    }
    SCORES = {
        # After 3...Nd4 White's best is to take on d4; the popular Nxe5 is a blunder.
        "e4 e5 Nf3 Nc6 Bc4 Nd4": {"Nxd4": 250, "Nxe5": -100},
    }

    def build(self, lines=None, scores=None, config=None, **over):
        config = config or ITALIAN
        edges = br.position_edges(counts_of(lines or self.LINES), config["start"].split())
        p = params(**(SMALL | over))
        nodes = br.build_within_ceilings(config, edges, FakeAnalyzer(scores or self.SCORES), p, {})
        return nodes, p

    def node(self, nodes, path):
        return nodes[br.fen4(br.board_at(path))]

    def test_start_moves_are_core_cards(self):
        nodes, _ = self.build()
        for path in ("", "e4 e5", "e4 e5 Nf3 Nc6"):
            card = self.node(nodes, path)
            self.assertEqual(card["turn"], "own")
            self.assertEqual(card["level"], 0)
        self.assertEqual(self.node(nodes, "")["move"], "e2e4")

    def test_replies_carry_share_games_and_verdict_apart(self):
        nodes, _ = self.build()
        after_bc4 = self.node(nodes, "e4 e5 Nf3 Nc6 Bc4")
        replies = {r["uci"]: r for r in after_bc4["replies"]}
        self.assertEqual(set(replies), {"f8c5", "g8f6", "c6d4"}, "h6 has too few games")
        self.assertAlmostEqual(replies["f8c5"]["share"][0], 400 / 903, places=3)
        self.assertIsNone(replies["f8c5"]["share"][1 + 0], "an empty band has no share")
        self.assertEqual(replies["f8c5"]["verdict"], "sound")
        self.assertEqual(replies["c6d4"]["verdict"], "blunder")
        self.assertEqual(replies["c6d4"]["trap"], "punish")

    def test_our_move_is_the_popular_sound_one_and_pitfalls_are_marked(self):
        nodes, _ = self.build()
        card = self.node(nodes, "e4 e5 Nf3 Nc6 Bc4 Nd4")
        self.assertEqual(card["move"], "f3d4")
        self.assertEqual(card["pitfall"]["uci"], "f3e5")
        self.assertEqual(card["pitfall"]["verdict"], "blunder")
        self.assertAlmostEqual(card["pitfall"]["share"], 0.6, places=3)
        self.assertEqual(card["level"], 0, "a punish line from a Core position joins Core")

    def test_config_choice_wins_and_must_be_sound(self):
        config = ITALIAN | {"choices": {"e4 e5 Nf3 Nc6 Bc4 Bc5": "O-O"}}
        nodes, _ = self.build(config=config)
        self.assertEqual(self.node(nodes, "e4 e5 Nf3 Nc6 Bc4 Bc5")["move"], "e1g1")
        bad = ITALIAN | {"choices": {"e4 e5 Nf3 Nc6 Bc4 Nd4": "Nxe5"}}
        with self.assertRaises(ValueError):
            self.build(config=bad)

    def test_engine_takes_over_where_the_data_ends(self):
        nodes, p = self.build(min_games=150)
        engine_only = [(n, r) for n in nodes.values() if n["turn"] == "opponent" for r in n["replies"] if not r["data"]]
        self.assertTrue(engine_only)
        for node, reply in engine_only:
            self.assertFalse(node["data"])
            self.assertEqual(len(node["replies"]), 1, "only the engine's reply")
            card = nodes[br.child_key(node["key"], reply["uci"])]
            self.assertEqual(card["level"], 2, "engine-only cards are Deep")
        # At most two engine-only own moves follow the end of the data on any line.
        for node, reply in engine_only:
            card = nodes[br.child_key(node["key"], reply["uci"])]
            depth = 0
            while card is not None and card["turn"] == "own":
                depth += 1
                after = nodes.get(br.child_key(card["key"], card["move"]))
                if after is None or not after["replies"]:
                    break
                card = nodes.get(br.child_key(after["key"], after["replies"][0]["uci"]))
            self.assertLessEqual(depth, p.extension)

    def test_levels_never_get_easier_down_a_line(self):
        nodes, _ = self.build()
        for node in nodes.values():
            if node["turn"] == "own":
                after = nodes.get(br.child_key(node["key"], node["move"]))
                if after:
                    self.assertGreaterEqual(after["level"], node["level"])

    def test_ceilings_raise_the_reach_cut(self):
        nodes, p = self.build(ceilings="5,8,12")
        core, standard, deep = br.cards_per_level(nodes)
        self.assertLessEqual(core, 5)
        self.assertLessEqual(core + standard, 8)
        self.assertLessEqual(core + standard + deep, 12)

    def test_asset_round_trips_as_json(self):
        nodes, p = self.build()
        args = br.parse_args(["build", "t"])
        asset = br.asset_of(ITALIAN, nodes, {"input": "test", "games": 903, "perFilter": {}}, p, args)
        with tempfile.TemporaryDirectory() as tmp:
            raw, packed = br.write_asset(asset, os.path.join(tmp, "t.json.gz"))
            with gzip.open(os.path.join(tmp, "t.json.gz"), "rt", encoding="utf-8") as f:
                again = json.load(f)
        self.assertEqual(again["start"], ["e2e4", "e7e5", "g1f3", "b8c6", "f1c4"])
        self.assertEqual(len(again["nodes"]), len(nodes))
        self.assertLess(packed, raw)
        own = next(n for n in again["nodes"] if n["turn"] == "own")
        self.assertIn("move", own)
        self.assertNotIn("replies", own)


class Notes(unittest.TestCase):
    def test_claims_are_checked_and_uncovered_sentences_listed(self):
        analyzer = FakeAnalyzer({"e4 e5 Nf3 Nc6 Bc4 Nd4": {"Nxd4": 250, "Nxe5": -100},
                                 "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxd4": {"*": -250}})
        notes = [{"path": "e4 e5 Nf3 Nc6 Bc4 Nd4", "kind": "trap",
                  "text": "Taking on e5 is a blunder. Take on d4 instead. Plans come later.",
                  "claims": [{"type": "verdict", "move": "Nxe5", "is": "blunder", "sentence": 0},
                             {"type": "best", "move": "Nxd4", "sentence": 1},
                             {"type": "wins", "line": ["Nxd4"], "side": "white", "atLeast": 200, "sentence": 1}]},
                 {"path": "e4 e5", "kind": "plan", "text": "Wrong on purpose.",
                  "claims": [{"type": "best", "move": "a3", "sentence": 0}]}]
        nodes = {br.fen4(br.board_at("e4 e5 Nf3 Nc6 Bc4 Nd4")): {"turn": "own"}}
        rows = br.check_notes(notes, nodes, analyzer)
        self.assertEqual(rows[0]["failed"], [])
        self.assertEqual(rows[0]["unverified"], ["Plans come later."])
        self.assertTrue(rows[0]["inTree"])
        self.assertEqual(len(rows[1]["failed"]), 0, "the fake engine scores every move alike, so a3 is 'best' here")
        self.assertFalse(rows[1]["inTree"])
        self.assertEqual(nodes[br.fen4(br.board_at("e4 e5 Nf3 Nc6 Bc4 Nd4"))]["note"]["kind"], "trap")

    def test_threat_means_the_move_wins_if_allowed(self):
        board = br.board_at("e4 e5 Nf3 Nc6 Bc4 Nf6 d3")
        ok, why = br.check_claim({"type": "threat", "move": "Ng5", "atLeast": 100}, board, FakeAnalyzer({}))
        self.assertFalse(ok, why)


if __name__ == "__main__":
    unittest.main(verbosity=1)
