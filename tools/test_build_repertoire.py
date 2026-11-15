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

    def test_levels_never_get_easier_than_the_easiest_way_in(self):
        nodes, _ = self.build()
        parents = {}
        for node in nodes.values():
            moves = [node["move"]] if node["turn"] == "own" else [r["uci"] for r in node["replies"]]
            for uci in moves:
                parents.setdefault(br.child_key(node["key"], uci), []).append(node["level"])
        for key, levels in parents.items():
            if key in nodes:
                self.assertGreaterEqual(nodes[key]["level"], min(levels))

    def test_a_rare_transposition_does_not_push_a_main_line_down(self):
        def own(path, move, level):
            return {"key": br.fen4(br.board_at(path)), "turn": "own", "move": br.board_at(path).parse_san(move).uci(), "level": level}

        def opponent(path, replies, level):
            board = br.board_at(path)
            return {"key": br.fen4(board), "turn": "opponent", "level": level,
                    "replies": [{"uci": board.parse_san(r).uci(), "level": level} for r in replies]}

        # 3.Nc3 dxe4 4.Nxe4 is the main line; 3.Nd2 dxe4 4.Nxe4 reaches the same card from a rare line.
        main = opponent("e4 c6 d4 d5 Nc3 dxe4", ["Nxe4"], 0)
        rare = opponent("e4 c6 d4 d5 Nd2 dxe4", ["Nxe4"], 2)
        card = own("e4 c6 d4 d5 Nc3 dxe4 Nxe4", "Bf5", 0)
        self.assertNotEqual(main["key"], rare["key"])
        self.assertEqual(card["key"], br.child_key(rare["key"], rare["replies"][0]["uci"]), "the two lines meet")
        nodes = {n["key"]: n for n in (main, rare, card)}
        br.settle_levels(nodes)
        self.assertEqual(0, card["level"])
        self.assertEqual(0, main["replies"][0]["level"])
        # A card with only a deep way in still follows it down.
        lone = {k: dict(v) for k, v in nodes.items() if k != main["key"]}
        lone[card["key"]]["level"] = 0
        br.settle_levels(lone)
        self.assertEqual(2, lone[card["key"]]["level"])

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


class FakeDatabase:
    """Plays the explorer: answers fetcher calls from made-up SAN-prefix counts, all in one band."""

    def __init__(self, lines, band=1, castle_as_rook=True):
        self.edges = br.position_edges(counts_of(lines, band=band), [])
        self.castle_as_rook = castle_as_rook
        self.calls = []

    def __call__(self, fen, ratings, speeds, since, until, moves):
        self.calls.append((fen, tuple(ratings), tuple(speeds), since, until, moves))
        board = chess.Board(fen)
        group = br.EXPLORER_BANDS.index(list(ratings))
        out = []
        for uci, row in self.edges.get(br.fen4(board), {}).items():
            if row[group]:
                move = chess.Move.from_uci(uci)
                if self.castle_as_rook and board.is_castling(move):
                    # Lichess writes castling as the king taking its own rook.
                    rook = chess.square(7 if move.to_square > move.from_square else 0, chess.square_rank(move.from_square))
                    uci = chess.Move(move.from_square, rook).uci()
                out.append({"uci": uci, "san": "?", "white": row[group], "draws": 0, "black": 0})
        return {"white": 0, "draws": 0, "black": 0, "moves": out}


class ExplorerSource(unittest.TestCase):
    def explorer(self, db, tmp, **kw):
        return br.Explorer(db, os.path.join(tmp, "explorer.jsonl"), **kw)

    def test_bands_cover_every_bucket_once_in_order(self):
        flat = [r for group in br.EXPLORER_BANDS for r in group]
        self.assertEqual(flat, br.EXPLORER_BUCKETS)
        self.assertEqual(len(br.EXPLORER_BANDS), br.NBANDS)

    def test_games_land_in_the_mapped_band(self):
        db = FakeDatabase({"e4 e5 Nf3": 7}, band=2)
        with tempfile.TemporaryDirectory() as tmp:
            rows = self.explorer(db, tmp).position(br.fen4(chess.Board()))
        self.assertEqual(rows, {"e2e4": [0, 0, 7, 0]})
        self.assertEqual([c[1] for c in db.calls], [tuple(g) for g in br.EXPLORER_BANDS])

    def test_cache_makes_a_second_run_ask_nothing_and_a_stopped_run_resume(self):
        db = FakeDatabase({"e4 e5": 5})
        key = br.fen4(chess.Board())
        with tempfile.TemporaryDirectory() as tmp:
            first = self.explorer(db, tmp)
            first.position(key)
            self.assertEqual(first.asked, 4)
            # A run cut off mid-line leaves a broken last line; it is ignored.
            with open(os.path.join(tmp, "explorer.jsonl"), "a", encoding="utf-8") as f:
                f.write('{"k": "broken')
            again = self.explorer(FakeDatabase({}), tmp)
            self.assertEqual(again.position(key), {"e2e4": [0, 5, 0, 0]})
            self.assertEqual(again.asked, 0)
            # Other settings are other cache entries.
            other = self.explorer(db, tmp, since="2026-01")
            other.position(key)
            self.assertEqual(other.asked, 4)

    def test_transpositions_share_one_cache_entry(self):
        db = FakeDatabase({"e4 e5 Nf3 Nc6": 5, "Nf3 Nc6 e4 e5": 5})
        with tempfile.TemporaryDirectory() as tmp:
            ex = self.explorer(db, tmp)
            ex.position(br.fen4(br.board_at("e4 e5 Nf3 Nc6")))
            ex.position(br.fen4(br.board_at("Nf3 Nc6 e4 e5")))
            self.assertEqual(ex.asked, 4)
            self.assertEqual(ex.hits, 4)

    def test_castling_is_normalised(self):
        board = chess.Board("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        answer = {"moves": [{"uci": "e1h1", "white": 3, "draws": 1, "black": 1}, {"uci": "e1a1", "white": 2, "draws": 0, "black": 0},
                            {"uci": "a1a8", "white": 1, "draws": 0, "black": 0}, {"uci": "e1e5", "white": 9, "draws": 0, "black": 0},
                            {"uci": "h1h2", "white": 0, "draws": 0, "black": 0}]}
        self.assertEqual(br.normalise_moves(board, answer), [["e1g1", 5], ["e1c1", 2], ["a1a8", 1]])

    def test_fake_database_castles_as_the_real_one_and_it_is_normalised(self):
        db = FakeDatabase({"e4 e5 Nf3 Nc6 Bc4 Nf6 O-O": 9})
        key = br.fen4(br.board_at("e4 e5 Nf3 Nc6 Bc4 Nf6"))
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(self.explorer(db, tmp).position(key), {"e1g1": [0, 9, 0, 0]})
        raw = db(chess.Board(key + " 0 1").fen(), [1400, 1600], (), None, None, 5)
        self.assertEqual(raw["moves"][0]["uci"], "e1h1")

    def test_request_url_and_pacing(self):
        sleeps, seen = [], []

        class Reply:
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def read(self):
                return b'{"moves": []}'

        def opener(request, timeout):
            seen.append(request)
            return Reply()

        client = br.ExplorerClient("SECRET", pause=0.5, sleep=sleeps.append, opener=opener)
        client(chess.STARTING_FEN, [1400, 1600], ("blitz", "rapid"), "2025-11", None, 40)
        request = seen[0]
        self.assertEqual(request.get_header("Authorization"), "Bearer SECRET")
        self.assertIn("ratings=1400%2C1600", request.full_url)
        self.assertIn("since=2025-11", request.full_url)
        self.assertNotIn("until", request.full_url)
        self.assertIn("topGames=0", request.full_url)
        self.assertEqual(sleeps, [0.5])

    def test_429_waits_a_minute_and_network_errors_back_off(self):
        import io
        import urllib.error
        sleeps = []
        outcomes = [urllib.error.HTTPError("u", 429, "slow", {}, io.BytesIO(b"")),
                    urllib.error.URLError("flaky"), TimeoutError("slow line"), "ok"]

        class Reply:
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def read(self):
                return b'{"moves": [], "white": 1}'

        def opener(request, timeout):
            outcome = outcomes.pop(0)
            if isinstance(outcome, Exception):
                raise outcome
            return Reply()

        client = br.ExplorerClient("t", pause=0.1, sleep=sleeps.append, opener=opener)
        self.assertEqual(client(chess.STARTING_FEN, [1400], ("blitz",), None, None, 5)["white"], 1)
        self.assertEqual(sleeps, [60, 2, 4, 0.1])

    def test_refused_token_and_giving_up_are_clear(self):
        import io
        import urllib.error

        def refuse(request, timeout):
            raise urllib.error.HTTPError("u", 401, "no", {}, io.BytesIO(b""))

        client = br.ExplorerClient("t", sleep=lambda s: None, opener=refuse)
        with self.assertRaises(SystemExit) as caught:
            client(chess.STARTING_FEN, [1400], ("blitz",), None, None, 5)
        self.assertIn("lichess.org/account/oauth/token/create", str(caught.exception))

        def down(request, timeout):
            raise urllib.error.URLError("down")

        client = br.ExplorerClient("t", sleep=lambda s: None, opener=down, retries=2)
        with self.assertRaises(RuntimeError):
            client(chess.STARTING_FEN, [1400], ("blitz",), None, None, 5)

    def test_missing_token_tells_how_to_make_one(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "token.txt")
            with self.assertRaises(SystemExit) as caught:
                br.load_token({}, path)
            message = str(caught.exception)
            self.assertIn("https://lichess.org/account/oauth/token/create", message)
            self.assertIn("no scopes", message)
            self.assertIn("tools/.cache/lichess-token.txt", message)
            with open(path, "w", encoding="utf-8") as f:
                f.write("  fromfile\n")
            self.assertEqual(br.load_token({}, path), "fromfile")
            self.assertEqual(br.load_token({"LICHESS_TOKEN": "fromenv"}, path), "fromenv")

    def test_start_line_keeps_only_the_start_move(self):
        db = FakeDatabase({"e4 e5": 5, "e4 c5": 50})
        with tempfile.TemporaryDirectory() as tmp:
            edges = br.ExplorerEdges(self.explorer(db, tmp), ["e4", "e5"])
            self.assertEqual(list(edges.get(br.fen4(chess.Board()))), ["e2e4"])
            self.assertEqual(edges.start_games(), 5)
            after_e4 = br.fen4(br.board_at("e4"))
            self.assertEqual(list(edges.get(after_e4)), ["e7e5"], "c5 games are not on the start line")

    def test_build_from_the_explorer_matches_build_from_counts(self):
        lines, scores = Tree.LINES, Tree.SCORES
        edges = br.position_edges(counts_of(lines), ITALIAN["start"].split())
        from_counts = br.build_within_ceilings(ITALIAN, edges, FakeAnalyzer(scores), params(**SMALL), {})
        with tempfile.TemporaryDirectory() as tmp:
            ex = self.explorer(FakeDatabase(lines), tmp)
            lazy = br.ExplorerEdges(ex, ITALIAN["start"].split())
            p2 = params(**SMALL)
            from_explorer = br.build_within_ceilings(ITALIAN, lazy, FakeAnalyzer(scores), p2, {})
            self.assertEqual(from_explorer, from_counts)
            self.assertGreater(ex.asked, 0)
            # Only positions the tree visited were asked about, four requests each.
            self.assertLessEqual(ex.asked, 4 * (len(from_explorer) + 12))
            args = br.parse_args(["build", "t", "--source", "explorer"])
            asset = br.asset_of(ITALIAN, from_explorer, br.explorer_meta(args, lazy), p2, args)
            self.assertEqual(asset["source"]["games"], 903)
            self.assertIn("AVERAGE rating", asset["source"]["data"])
            self.assertEqual(asset["source"]["bands"][0]["explorerBuckets"], [400, 1000, 1200])
            self.assertIn("average rating", asset["source"]["ratingBasis"])
            # A rebuild asks nothing.
            again = self.explorer(FakeDatabase({}), tmp)
            br.build_within_ceilings(ITALIAN, br.ExplorerEdges(again, ITALIAN["start"].split()), FakeAnalyzer(scores), params(**SMALL), {})
            self.assertEqual(again.asked, 0)


if __name__ == "__main__":
    unittest.main(verbosity=1)
