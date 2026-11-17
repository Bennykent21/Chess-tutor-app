"""Works backwards from chess.com's own reviews to their grading (Phase 13D, D89).

Input: review fixtures (tools/review_fixture.py: every position searched deeply) and, for each
game, what chess.com's Game Review showed: each side's grade counts and accuracy. Chess.com does
not publish its formulas, so this searches for the winning-chance curve, grade thresholds and
accuracy curve that reproduce their numbers best.

Run: tools/.cache/venv/Scripts/python.exe tools/fit_chesscom.py truth.json
     truth.json: [{"fixture": "path.json", "white": {"book": 2, "best": 13, ..., "accuracy": 80.6}, "black": {...}}]
The six games behind D89 are app/src/test/resources/reviews/chesscom*.json; chess.com's numbers for
them are in ReviewFixtureTest.chessComReviews.
"""
import itertools
import json
import math
import sys

import chess

CEILING = 1000
GRADES = ["best", "excellent", "good", "inaccuracy", "mistake", "blunder"]


def win(cp, mate, k):
    """Winning chances 0..100 for White from a score, with slope k (Lichess uses 0.00368208)."""
    if mate is not None and mate != 0:
        cp = CEILING if mate > 0 else -CEILING
    cp = max(-CEILING, min(CEILING, cp or 0))
    return 50 + 50 * (2 / (1 + math.exp(-k * cp)) - 1)


def moves_of(fixture, key):
    """Per move: mover (0 White, 1 Black), White's (cp, mate) before and after, and whether it was the engine's move."""
    plies = {p["ply"]: p.get(key) for p in fixture["plies"]}
    board = chess.Board()
    out = []
    for i, uci in enumerate(fixture["moves"]):
        before = plies.get(i)
        board.push_uci(uci)
        after = plies.get(i + 1)
        if before is None:
            continue
        if after is None:
            # The game ended on the board: mate wins for the mover, anything else is level.
            score = (10_000 if i % 2 == 0 else -10_000) if board.is_checkmate() else 0
            after = {"cp": score, "mate": None}
        out.append((i % 2, (before["cp"], before["mate"]), (after["cp"], after["mate"]), before["best"] == uci))
    return out


def grade(loss, best, t, flag=True):
    """[flag]: the engine's own move is always best, as the app does; otherwise only the loss counts."""
    if (flag and best) or loss <= t[0]:
        return "best"
    for name, limit in zip(GRADES[1:], t[1:]):
        if loss <= limit:
            return name
    return "blunder"


def losses(moves, k):
    rows = []
    for side, b, a, best in moves:
        wb, wa = win(*b, k), win(*a, k)
        if side == 1:
            wb, wa = 100 - wb, 100 - wa
        rows.append((side, max(0.0, wb - wa), best, wb, wa))
    return rows


def counts(rows, side, skip, t, flag=True):
    c = dict.fromkeys(GRADES, 0)
    for r in [r for r in rows if r[0] == side][skip:]:
        c[grade(r[1], r[2], t, flag)] += 1
    return c


def their(truth):
    """Chess.com's counts folded into the six plain grades (Great and Brilliant are best moves; a miss is a mistake)."""
    return {
        "best": truth["best"] + truth.get("great", 0) + truth.get("brilliant", 0),
        "excellent": truth["excellent"],
        "good": truth["good"],
        "inaccuracy": truth["inaccuracy"],
        "mistake": truth["mistake"] + truth.get("miss", 0),
        "blunder": truth["blunder"],
    }


def accuracy(rows, side, skip, alpha, weighted):
    mine = [r for r in rows if r[0] == side][skip:]
    if not mine:
        return None
    accs = [max(0.0, min(100.0, 103.1668 * math.exp(-alpha * r[1]) - 3.1669 + 1)) if r[4] < r[3] else 100.0 for r in mine]
    if weighted == "mean":
        return sum(accs) / len(accs)
    if weighted == "lichess":
        harmonic = len(accs) / sum(1 / max(a, 1) for a in accs)
        return (sum(accs) / len(accs) + harmonic) / 2
    return sum(accs) / len(accs)


def main():
    games = json.load(open(sys.argv[1], encoding="utf-8"))
    key = sys.argv[2] if len(sys.argv) > 2 else "deep"
    loaded = []
    for g in games:
        fx = json.load(open(g["fixture"], encoding="utf-8"))
        loaded.append((moves_of(fx, key), g))

    def grade_error(k, t, flag=True, only=GRADES):
        err = 0
        for moves, g in loaded:
            rows = losses(moves, k)
            for side, name in ((0, "white"), (1, "black")):
                truth = g[name]
                mine = counts(rows, side, truth.get("book", 0), t, flag)
                want = their(truth)
                err += sum((mine[x] - want[x]) ** 2 for x in only)
        return err

    lichess_k = 0.00368208
    app = (1.0, 2.5, 5.0, 10.0, 30.0)
    errors = ["inaccuracy", "mistake", "blunder"]
    print(f"app today (Lichess curve, {app}): squared count error {grade_error(lichess_k, app)}, errors only {grade_error(lichess_k, app, only=errors)}")
    best = None
    for k in [0.0020, 0.0025, 0.0030, 0.0035, lichess_k, 0.0045, 0.0055]:
        for flag in (True, False):
            for t in itertools.product([0.5, 1.0, 1.5, 2.0], [2.0, 2.5, 3.0, 4.0, 5.0], [4.0, 5.0, 6.0, 7.5, 10.0], [8.0, 10.0, 12.5, 15.0], [15.0, 20.0, 25.0, 30.0, 35.0, 40.0]):
                if not (t[0] < t[1] < t[2] < t[3] < t[4]):
                    continue
                e = grade_error(k, t, flag)
                if best is None or e < best[0]:
                    best = (e, k, t, flag)
    print(f"best fit: slope {best[1]}, thresholds {best[2]}, engine move always best {best[3]}: squared count error {best[0]}, errors only {grade_error(best[1], best[2], best[3], errors)}")
    on_lichess = min((grade_error(lichess_k, t, f), t, f) for f in (True, False) for t in itertools.product([0.5, 1.0, 1.5, 2.0], [2.0, 2.5, 3.0, 4.0, 5.0], [4.0, 5.0, 6.0, 7.5, 10.0], [8.0, 10.0, 12.5, 15.0], [15.0, 20.0, 25.0, 30.0, 35.0, 40.0]) if t[0] < t[1] < t[2] < t[3] < t[4])
    print(f"best on the Lichess curve: thresholds {on_lichess[1]}, engine move always best {on_lichess[2]}: squared count error {on_lichess[0]}, errors only {grade_error(lichess_k, on_lichess[1], on_lichess[2], errors)}")

    def accuracy_error(k, alpha, how):
        err, n = 0.0, 0
        for moves, g in loaded:
            rows = losses(moves, k)
            for side, name in ((0, "white"), (1, "black")):
                a = accuracy(rows, side, g[name].get("book", 0), alpha, how)
                if a is not None and "accuracy" in g[name]:
                    err += (a - g[name]["accuracy"]) ** 2
                    n += 1
        return math.sqrt(err / n)

    print(f"accuracy today (Lichess move curve, plain mean): off by {accuracy_error(lichess_k, 0.04354415386753951, 'mean'):.1f} on average (root mean square)")
    fit = min(((accuracy_error(k, a, how), k, a, how) for k in [0.0025, 0.0030, 0.0035, lichess_k, 0.0045] for a in [x / 1000 for x in range(20, 121, 4)] for how in ["mean", "lichess"]))
    print(f"accuracy fit: slope {fit[1]}, alpha {fit[2]}, {fit[3]}: off by {fit[0]:.1f}")
    for moves, g in loaded:
        rows = losses(moves, best[1])
        for side, name in ((0, "white"), (1, "black")):
            skip = g[name].get("book", 0)
            print(f"  {g['fixture'].split('/')[-1].split(chr(92))[-1]:<14} {name:<5} chess.com {their(g[name])} acc {g[name].get('accuracy')}")
            print(f"  {'':<14} {'':<5} fitted    {counts(rows, side, skip, best[2], best[3])} acc {accuracy(losses(moves, fit[1]), side, skip, fit[2], fit[3]):.1f}")


if __name__ == "__main__":
    main()
