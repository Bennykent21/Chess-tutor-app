#!/usr/bin/env python3
"""
Reads Maia networks (lc0 weights, .pb.gz) without lc0 or protobuf, for the app's own Maia
inference (plan D26). Maia: CSSLab/maia-chess, GPL-3.0; the format is lc0's net.proto.

Only what Maia uses is read: the classical 112-plane input, the residual tower (optionally
with squeeze-excitation), the classical (fully connected) policy head and the value head.

    tools/.cache/venv/Scripts/python.exe -I tools/maia_net.py dump tools/.cache/maia/maia-1500.pb.gz
"""
import gzip
import struct
import sys

import numpy as np


# ------------------------------------------------------------------ protobuf wire format

def read_varint(buf, pos):
    result, shift = 0, 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def fields(buf):
    """Yields (field number, wire type, value) for one message; values of type 2 are bytes."""
    pos = 0
    while pos < len(buf):
        key, pos = read_varint(buf, pos)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = read_varint(buf, pos)
        elif wire == 1:
            value = buf[pos:pos + 8]
            pos += 8
        elif wire == 2:
            length, pos = read_varint(buf, pos)
            value = buf[pos:pos + length]
            pos += length
        elif wire == 5:
            value = buf[pos:pos + 4]
            pos += 4
        else:
            raise ValueError(f"wire type {wire}")
        yield number, wire, value


def message(buf):
    """Fields of one message as {number: [values]}."""
    out = {}
    for number, _, value in fields(buf):
        out.setdefault(number, []).append(value)
    return out


# ------------------------------------------------------------------ lc0 layers

def layer(buf):
    """Weights.Layer: min_val=1, max_val=2, params=3 (LINEAR16 by default), encoding=4."""
    m = message(buf)
    lo = struct.unpack("<f", m[1][0])[0] if 1 in m else 0.0
    hi = struct.unpack("<f", m[2][0])[0] if 2 in m else 0.0
    params = m.get(3, [b""])[0]
    encoding = m.get(4, [1])[0]
    if encoding not in (0, 1):
        raise ValueError(f"layer encoding {encoding} not supported")
    q = np.frombuffer(params, dtype="<u2").astype(np.float64)
    return (lo + (hi - lo) * q / 65535.0).astype(np.float32)


def conv_block(buf):
    """ConvBlock: weights=1, biases=2, bn_means=3, bn_stddivs=4, bn_gammas=5, bn_betas=6."""
    m = message(buf)
    names = {1: "weights", 2: "biases", 3: "bn_means", 4: "bn_stddivs", 5: "bn_gammas", 6: "bn_betas"}
    return {name: layer(m[num][0]) for num, name in names.items() if num in m}


def se_unit(buf):
    m = message(buf)
    names = {1: "w1", 2: "b1", 3: "w2", 4: "b2"}
    return {name: layer(m[num][0]) for num, name in names.items() if num in m}


def load(path):
    raw = gzip.open(path, "rb").read()
    net = message(raw)
    fmt = message(net[4][0]) if 4 in net else {}
    network_format = message(fmt[2][0]) if 2 in fmt else {}
    w = message(net[10][0])
    residual = []
    for block in w.get(2, []):
        r = message(block)
        residual.append({
            "conv1": conv_block(r[1][0]),
            "conv2": conv_block(r[2][0]),
            "se": se_unit(r[3][0]) if 3 in r else None,
        })
    weights = {
        "format": {k: v[0] for k, v in network_format.items() if isinstance(v[0], int)},
        "input": conv_block(w[1][0]),
        "residual": residual,
        "policy": conv_block(w[3][0]),
        "value": conv_block(w[6][0]),
        "ip1_val_w": layer(w[7][0]),
        "ip1_val_b": layer(w[8][0]),
        "ip2_val_w": layer(w[9][0]),
        "ip2_val_b": layer(w[10][0]),
    }
    if 11 in w:
        weights["policy1"] = conv_block(w[11][0])
    return weights


def dump(path):
    w = load(path)
    print("format", w["format"])
    print("input", {k: v.size for k, v in w["input"].items()})
    print("residual blocks", len(w["residual"]), "se" if w["residual"][0]["se"] else "no se")
    r0 = w["residual"][0]
    print("  conv1", {k: v.size for k, v in r0["conv1"].items()})
    if r0["se"]:
        print("  se", {k: v.size for k, v in r0["se"].items()})
    print("policy", {k: v.size for k, v in w["policy"].items()}, "policy1" in w)
    print("value", {k: v.size for k, v in w["value"].items()})
    print("ip1_val", w["ip1_val_w"].size, w["ip1_val_b"].size, "ip2_val", w["ip2_val_w"].size, w["ip2_val_b"].size)


# ------------------------------------------------------------------ reference forward pass

EPS = 1e-5


def policy_tables(folder):
    """The 1858 move names, and the conv policy map (80 x 64 entries, -1 for unused)."""
    ns = {}
    exec(open(f"{folder}/policy_index.py", encoding="utf-8").read(), ns)
    moves = ns["policy_index"]
    text = open(f"{folder}/policy_map.h", encoding="utf-8").read()
    body = text[text.index("kConvPolicyMap[] = {") + len("kConvPolicyMap[] = {"):]
    body = body[:body.index("}")]
    conv_map = [int(x) for x in body.replace("\n", " ").split(",") if x.strip()]
    return moves, conv_map


def batch_norm(x, block):
    """x: (C, 64). Conv bias, then (x - mean) / sqrt(var + eps) * gamma + beta."""
    if "biases" in block:
        x = x + block["biases"][:, None]
    if "bn_means" in block:
        x = (x - block["bn_means"][:, None]) / np.sqrt(block["bn_stddivs"][:, None] + EPS)
        if "bn_gammas" in block:
            x = x * block["bn_gammas"][:, None]
        if "bn_betas" in block:
            x = x + block["bn_betas"][:, None]
    return x


def conv(x, weights, out_ch, k):
    """x: (C_in, 8, 8) -> (out_ch, 64), 'same' padding; weights are [out][in][k][k]."""
    c_in = x.shape[0]
    w = weights.reshape(out_ch, c_in, k, k)
    pad = k // 2
    xp = np.pad(x, ((0, 0), (pad, pad), (pad, pad)))
    out = np.zeros((out_ch, 8, 8), dtype=np.float32)
    for dy in range(k):
        for dx in range(k):
            patch = xp[:, dy:dy + 8, dx:dx + 8].reshape(c_in, 64)
            out += (w[:, :, dy, dx] @ patch).reshape(out_ch, 8, 8)
    return out.reshape(out_ch, 64)


def conv_block_forward(x, block, k, relu=True):
    out_ch = block["bn_means"].size if "bn_means" in block else block["biases"].size
    y = batch_norm(conv(x.reshape(-1, 8, 8), block["weights"], out_ch, k), block)
    return np.maximum(y, 0) if relu else y


def forward(w, planes):
    """planes: (112, 64) -> (policy logits for 1858 moves, (win, draw, loss))."""
    x = conv_block_forward(planes, w["input"], 3)
    for block in w["residual"]:
        y = conv_block_forward(x, block["conv1"], 3)
        y = conv_block_forward(y, block["conv2"], 3, relu=False)
        se = block["se"]
        if se is not None:
            channels = y.shape[0]
            pooled = y.mean(axis=1)
            hidden = se["b1"].size
            h = np.maximum(se["w1"].reshape(hidden, channels) @ pooled + se["b1"], 0)
            g = se["w2"].reshape(2 * channels, hidden) @ h + se["b2"]
            gamma, beta = g[:channels], g[channels:]
            y = (1.0 / (1.0 + np.exp(-gamma)))[:, None] * y + beta[:, None]
        x = np.maximum(y + x, 0)
    # Convolutional policy head: 3x3 conv block, then a 3x3 conv to 80 planes, mapped to 1858.
    p = conv_block_forward(x, w["policy1"], 3)
    p = conv(p.reshape(-1, 8, 8), w["policy"]["weights"], 80, 3) + w["policy"]["biases"][:, None]
    return p.reshape(-1), value_head(w, x)


def value_head(w, x):
    v = conv_block_forward(x, w["value"], 1)
    hidden = w["ip1_val_b"].size
    h = np.maximum(w["ip1_val_w"].reshape(hidden, -1) @ v.reshape(-1) + w["ip1_val_b"], 0)
    out = w["ip2_val_w"].reshape(w["ip2_val_b"].size, hidden) @ h + w["ip2_val_b"]
    e = np.exp(out - out.max())
    return e / e.sum()


# ------------------------------------------------------------------ input encoding

PIECE_ORDER = "PNBRQK"


def encode(board):
    """
    board: chess.Board with its move stack. The 112 classical input planes, as lc0 builds them.
    A game that did not start from the normal starting position fills the missing history with
    its first position (lc0's default HistoryFill "fen_only"), seen alternately from each side.
    """
    import chess
    us = board.turn
    planes = np.zeros((112, 64), dtype=np.float32)
    history = board.copy()
    fill = history.root().fen() != chess.STARTING_FEN
    oldest = None
    for i in range(8):
        base = i * 13
        if oldest is None:
            position, view, repeated = history, us, history.is_repetition(2)
        else:
            # A filled slot: the first position, from the side whose turn it would be i plies ago.
            position, repeated = oldest, False
            view = us
        for sq, piece in position.piece_map().items():
            # Squares from the viewing side: mirror the ranks when it is Black.
            s = sq if view == chess.WHITE else chess.square_mirror(sq)
            idx = PIECE_ORDER.index(piece.symbol().upper())
            planes[base + idx + (0 if piece.color == view else 6), s] = 1.0
        if repeated:
            planes[base + 12, :] = 1.0
        if oldest is None and not history.move_stack:
            if not fill:
                break
            oldest, oldest_ply = history.copy(stack=False), i
            continue
        if oldest is None:
            history.pop()
    them = not us
    planes[104, :] = float(board.has_queenside_castling_rights(us))
    planes[105, :] = float(board.has_kingside_castling_rights(us))
    planes[106, :] = float(board.has_queenside_castling_rights(them))
    planes[107, :] = float(board.has_kingside_castling_rights(them))
    planes[108, :] = float(us == chess.BLACK)
    planes[109, :] = float(board.halfmove_clock)
    planes[111, :] = 1.0
    return planes


def move_name(board, move):
    """The policy name of a move, from the side to move's view; knight promotions have no suffix."""
    import chess
    frm, to = move.from_square, move.to_square
    if board.turn == chess.BLACK:
        frm, to = chess.square_mirror(frm), chess.square_mirror(to)
    name = chess.square_name(frm) + chess.square_name(to)
    if move.promotion and move.promotion != chess.KNIGHT:
        name += chess.piece_symbol(move.promotion)
    return name


def legal_policy(w, board, moves, conv_map, temperature=1.0):
    """{uci: probability} over the legal moves, softmax of the logits at [temperature]."""
    logits, wdl = forward(w, encode(board))
    by_index = {}
    for i, index in enumerate(conv_map):
        if index >= 0:
            by_index[index] = logits[i]
    lookup = {name: i for i, name in enumerate(moves)}
    values = {m.uci(): by_index[lookup[move_name(board, m)]] for m in board.legal_moves}
    top = max(values.values())
    exps = {k: np.exp((v - top) / temperature) for k, v in values.items()}
    total = sum(exps.values())
    return {k: v / total for k, v in exps.items()}, wdl


# ------------------------------------------------------------------ app asset export

def fold(block):
    """Folds a conv block's bias and batch norm into (weights, bias) per output channel."""
    w = block["weights"]
    out_ch = block["bn_means"].size if "bn_means" in block else block["biases"].size
    b = block.get("biases", np.zeros(out_ch, dtype=np.float32))
    if "bn_means" not in block:
        return w, b
    scale = block.get("bn_gammas", np.ones(out_ch, dtype=np.float32)) / np.sqrt(block["bn_stddivs"] + EPS)
    beta = block.get("bn_betas", np.zeros(out_ch, dtype=np.float32))
    w2 = (w.reshape(out_ch, -1) * scale[:, None]).reshape(-1)
    return w2.astype(np.float32), ((b - block["bn_means"]) * scale + beta).astype(np.float32)


def write_tensor(out, values):
    """count (u32), min (f32), max (f32), then each value as 16 bits between min and max."""
    values = np.asarray(values, dtype=np.float64).reshape(-1)
    lo, hi = float(values.min()), float(values.max())
    span = hi - lo if hi > lo else 1.0
    q = np.clip(np.round((values - lo) / span * 65535.0), 0, 65535).astype("<u2")
    out.write(struct.pack("<Iff", values.size, lo, hi))
    out.write(q.tobytes())


def export(path, target):
    """
    The policy part of a Maia net for the app (assets/maia/maia-XXXX.bin): batch norm folded
    into the convolutions, every tensor stored as 16-bit steps between its min and max.
    Layout: 'MAIA', u16 version 1, u16 blocks, u16 channels, u16 SE hidden size, then tensors:
    input (w, b); per block conv1 (w, b), conv2 (w, b), SE w1, b1, w2, b2; policy1 (w, b);
    policy (w, b). Convolution weights are [out][in][3][3].
    """
    w = load(path)
    channels = w["input"]["bn_means"].size
    se_hidden = w["residual"][0]["se"]["b1"].size
    with open(target, "wb") as out:
        out.write(b"MAIA")
        out.write(struct.pack("<HHHH", 1, len(w["residual"]), channels, se_hidden))
        for tensor in fold(w["input"]):
            write_tensor(out, tensor)
        for block in w["residual"]:
            for tensor in (*fold(block["conv1"]), *fold(block["conv2"])):
                write_tensor(out, tensor)
            se = block["se"]
            for name in ("w1", "b1", "w2", "b2"):
                write_tensor(out, se[name])
        for tensor in (*fold(w["policy1"]), w["policy"]["weights"], w["policy"]["biases"]):
            write_tensor(out, tensor)


def export_moves(folder, target):
    """assets/maia/maia_moves.tsv: for each policy move name, its slot in the 80 x 64 conv output."""
    moves, conv_map = policy_tables(folder)
    slots = {index: slot for slot, index in enumerate(conv_map) if index >= 0}
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        for index, name in enumerate(moves):
            out.write(f"{name}\t{slots[index]}\n")


def fixtures(folder, net, target):
    """Reference probabilities for the Kotlin tests: position id, then uci and probability."""
    import chess
    w = load(f"{folder}/{net}")
    moves, conv_map = policy_tables(folder)
    cases = [
        ("start", None, []),
        ("after_e4", None, ["e2e4"]),
        ("najdorf", None, "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6".split()),
        ("castled", None, "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 e1g1 f8c5 d2d3 e8g8 c2c3 d7d6".split()),
        ("promotion_fen", "8/P5k1/8/8/8/8/6K1/8 w - - 0 1", []),
        ("black_fen", "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3", []),
        ("fen_then_moves", "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3", ["g8f6", "d2d3"]),
    ]
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        for name, fen, played in cases:
            board = chess.Board(fen) if fen else chess.Board()
            for uci in played:
                board.push_uci(uci)
            probs, _ = legal_policy(w, board, moves, conv_map)
            start = fen or chess.STARTING_FEN
            for uci, p in sorted(probs.items(), key=lambda kv: -kv[1]):
                out.write(f"{name}\t{start}\t{' '.join(played)}\t{uci}\t{p:.6f}\n")

if __name__ == "__main__":
    command = sys.argv[1]
    if command == "dump":
        dump(sys.argv[2])
    elif command == "export":
        # export <maia folder> <assets/maia folder>: every net, the move table and test fixtures.
        import glob
        import os
        folder, target = sys.argv[2], sys.argv[3]
        os.makedirs(target, exist_ok=True)
        for net in sorted(glob.glob(f"{folder}/maia-*.pb.gz")):
            name = os.path.basename(net).replace(".pb.gz", ".bin")
            export(net, f"{target}/{name}")
            print("wrote", name, os.path.getsize(f"{target}/{name}") // 1024, "KB")
        export_moves(folder, f"{target}/maia_moves.tsv")
        if len(sys.argv) > 4:
            fixtures(folder, "maia-1500.pb.gz", sys.argv[4])
            print("wrote fixtures", sys.argv[4])
