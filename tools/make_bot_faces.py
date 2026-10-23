#!/usr/bin/env python3
"""
Draws the bot roster's faces (plan 6.1): twelve original animal characters in the mascot
style (flat shapes, big eyes with a glint, blush), each with a hint of its playing style.

Writes the SVG sources to design/bots/ (the source of truth, editable by hand afterwards)
and converts them to VectorDrawables with tools/mascot_svg_to_vector.py.

Usage (from the repository root):
    python tools/make_bot_faces.py design/bots app/src/main/res/drawable

Every face is drawn on a 200 x 200 canvas with a transparent background; the app puts it
on a coloured disc. The head sits around (100, 104) and the shoulders fill the bottom.
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mascot_svg_to_vector import convert  # noqa: E402

DARK = "#121838"
WHITE = "#FFFFFF"


def el(tag, **attrs):
    parts = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in attrs.items())
    return f"<{tag} {parts}/>"


def circle(cx, cy, r, fill, **kw):
    return el("circle", cx=cx, cy=cy, r=r, fill=fill, **kw)


def ellipse(cx, cy, rx, ry, fill, **kw):
    return el("ellipse", cx=cx, cy=cy, rx=rx, ry=ry, fill=fill, **kw)


def path(d, fill="none", **kw):
    return el("path", d=d, fill=fill, **kw)


def stroke(d, color, width=4):
    return path(d, stroke=color, stroke_width=width, stroke_linecap="round")


def rect(x, y, w, h, rx, fill):
    return el("rect", x=x, y=y, width=w, height=h, rx=rx, fill=fill)


def mirror(d):
    """Mirrors an absolute path (M, L, C, Q, Z only) across x = 100."""
    out, tokens = [], re.findall(r"[A-Za-z]|-?\d+(?:\.\d+)?", d)
    axis = 0
    for t in tokens:
        if t.isalpha():
            out.append(t)
            axis = 0
            continue
        v = float(t)
        out.append(f"{200 - v:g}" if axis % 2 == 0 else f"{v:g}")
        axis += 1
    return " ".join(out)


def eye(cx, cy, s=1.0, look=(2, 3)):
    dx, dy = look
    return [
        ellipse(cx, cy, 10 * s, 12 * s, WHITE),
        circle(cx + dx * s, cy + dy * s, 6 * s, DARK),
        circle(cx + (dx + 2) * s, cy + (dy - 3) * s, 2 * s, WHITE),
    ]


def wink(cx, cy):
    return [stroke(f"M{cx - 9} {cy + 2} Q{cx} {cy - 7} {cx + 9} {cy + 2}", DARK, 4)]


def blush(*points):
    return [ellipse(x, y, 8, 5, "#FF6F6F", opacity=".55") for x, y in points]


def smile(cx, cy, w=12, d=7):
    return stroke(f"M{cx - w} {cy} Q{cx} {cy + d} {cx + w} {cy}", DARK, 4)


def shine(cx, cy, rx=13, ry=7):
    return ellipse(cx, cy, rx, ry, WHITE, opacity=".3")


def shoulders(color, top=152):
    return path(f"M28 200 C32 {top + 18} 60 {top} 100 {top} C140 {top} 168 {top + 18} 172 200 Z", color)


def star(cx, cy, r, fill):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append(f"{cx + rr * math.cos(a):.1f} {cy + rr * math.sin(a):.1f}")
    return path("M" + " L".join(pts) + " Z", fill)


def spikes(cx, cy, outer, inner, start, end, count, fill):
    pts = []
    for i in range(count * 2 + 1):
        a = math.radians(start + (end - start) * i / (count * 2))
        r = outer if i % 2 else inner
        pts.append(f"{cx + r * math.cos(a):.1f} {cy + r * math.sin(a):.1f}")
    return path("M" + " L".join(pts) + " Z", fill)


def pebble():  # snail, beginner
    body = "#8EDFC0"
    return [
        circle(130, 116, 46, "#F2A65A"),
        path("M130 116 m-6 0 a6 6 0 1 1 12 0 a12 12 0 1 1 -24 0 a18 18 0 1 1 36 0 a24 24 0 1 1 -48 0",
             stroke="#C46F2E", stroke_width=6, stroke_linecap="round"),
        path("M22 200 C26 178 50 166 82 166 L178 170 C190 176 190 200 190 200 Z", body),
        stroke("M68 90 L58 50", body, 10), stroke("M88 90 L98 50", body, 10),
        ellipse(78, 122, 40, 44, body),
        shine(64, 100, 10, 6),
        *eye(58, 46, 0.85), *eye(98, 46, 0.85),
        smile(78, 134, 10, 6),
        *blush((56, 128), (100, 128)),
    ]


def wayne():  # bear cub, casual
    fur, inner = "#B9784A", "#E8B48A"
    return [
        shoulders("#A0653D"),
        rect(56, 148, 88, 18, 9, "#FF6F6F"),
        path("M118 158 L134 190 L120 195 L108 162 Z", "#FF6F6F"),
        circle(56, 60, 20, fur), circle(56, 60, 10, inner),
        circle(144, 60, 20, fur), circle(144, 60, 10, inner),
        circle(100, 104, 54, fur),
        shine(80, 68),
        ellipse(100, 124, 26, 20, "#F1CFA6"),
        ellipse(100, 114, 9, 6, DARK),
        stroke("M100 119 L100 127 M90 129 Q100 137 110 129", DARK, 4),
        *eye(82, 94, 0.9), *eye(118, 94, 0.9),
        *blush((68, 116), (132, 116)),
    ]


def juno():  # fox, trickster with a mask
    fur, cream, tip = "#FF8A3D", "#FFF4E6", "#3B2416"
    ear, ear_tip, ear_in = "M50 84 L56 30 L92 62 Z", "M54 50 L56 30 L70 42 Z", "M60 74 L62 46 L82 64 Z"
    return [
        shoulders(fur),
        path("M76 200 C80 176 90 164 100 164 C110 164 120 176 124 200 Z", cream),
        path(ear, fur), path(mirror(ear), fur),
        path(ear_tip, tip), path(mirror(ear_tip), tip),
        path(ear_in, "#FFD6B8"), path(mirror(ear_in), "#FFD6B8"),
        path("M44 96 C44 64 70 52 100 52 C130 52 156 64 156 96 C156 124 132 150 100 156 C68 150 44 124 44 96 Z", fur),
        path("M48 104 C60 118 80 124 100 150 C120 124 140 118 152 104 C150 132 128 154 100 158 C72 154 50 132 48 104 Z", cream),
        shine(78, 66, 12, 6),
        path("M56 84 C70 74 90 78 100 86 C110 78 130 74 144 84 C146 98 132 106 118 104 C110 102 104 98 100 96 "
             "C96 98 90 102 82 104 C68 106 54 98 56 84 Z", "#6A4BE0"),
        *eye(82, 90, 0.85), *eye(118, 90, 0.85),
        ellipse(100, 128, 7, 5, DARK),
        smile(100, 136, 8, 5),
        *blush((66, 118), (134, 118)),
    ]


def bramble():  # hedgehog, defender with a shield
    return [
        spikes(100, 106, 72, 52, 150, 390, 9, "#7A5236"),
        shoulders("#7A5236"),
        ellipse(100, 112, 46, 44, "#F3D2A2"),
        circle(64, 76, 10, "#E3B47E"), circle(136, 76, 10, "#E3B47E"),
        shine(84, 84, 10, 5),
        ellipse(100, 128, 16, 12, "#FBE3C0"),
        circle(100, 122, 6, DARK),
        smile(100, 136, 8, 5),
        *eye(84, 104, 0.8), *eye(116, 104, 0.8),
        *blush((72, 124), (128, 124)),
        path("M32 150 L60 142 L88 150 L86 172 C84 186 72 194 60 198 C48 194 36 186 34 172 Z", "#4C8DFF"),
        stroke("M46 164 L60 174 L74 164", WHITE, 5),
    ]


def ziggy():  # parrot, attacker
    return [
        path("M86 62 C72 46 66 34 68 24 C80 34 88 46 94 58 Z", "#4C8DFF"),
        path("M104 58 C104 36 116 24 128 22 C122 36 116 50 112 62 Z", "#FFC53D"),
        path("M92 58 C84 34 90 20 98 14 C100 30 104 44 104 58 Z", "#FF6F6F"),
        shoulders("#2BB673"),
        path("M40 200 C44 178 56 168 70 166 C66 180 64 190 64 200 Z", "#1E8E5A"),
        path("M160 200 C156 178 144 168 130 166 C134 180 136 190 136 200 Z", "#1E8E5A"),
        ellipse(100, 106, 52, 54, "#3DDC84"),
        shine(80, 70, 12, 6),
        ellipse(80, 96, 17, 18, WHITE), ellipse(120, 96, 17, 18, WHITE),
        *eye(80, 96, 0.9), *eye(120, 96, 0.9),
        path("M86 112 C86 102 114 102 114 112 C114 126 106 138 100 142 C98 132 90 124 86 112 Z", "#FFC53D"),
        path("M90 124 C94 132 98 136 100 142 C104 136 108 130 110 124 C104 128 96 128 90 124 Z", "#F29A1F"),
        *blush((64, 120), (136, 120)),
    ]


def marlo():  # raccoon, gambit lover: offers you a pawn coin
    grey, dark, light = "#9AA3B5", "#3B4255", "#E6E9F0"
    ear, ear_in = "M50 84 C44 60 48 44 56 38 C70 44 82 54 88 64 Z", "M58 74 C56 60 58 52 62 48 C70 54 76 60 80 66 Z"
    coin = [
        circle(134, 168, 20, "#FFC53D"),
        circle(134, 168, 15, "none", stroke="#F29A1F", stroke_width=3),
        circle(134, 160, 5, "#B87512"),
        path("M127 178 C127 170 130 166 134 166 C138 166 141 170 141 178 Z", "#B87512"),
    ]
    return [
        shoulders("#8A93A6"),
        path(ear, grey), path(mirror(ear), grey),
        path(ear_in, dark), path(mirror(ear_in), dark),
        ellipse(100, 106, 56, 50, grey),
        path("M48 108 C60 92 84 92 100 104 C116 92 140 92 152 108 C146 132 124 150 100 152 C76 150 54 132 48 108 Z", light),
        path("M94 60 L106 60 L104 94 L96 94 Z", dark),
        path("M50 96 C62 82 86 84 100 98 C114 84 138 82 150 96 C150 110 134 116 120 112 C110 110 104 104 100 102 "
             "C96 104 90 110 80 112 C66 116 50 110 50 96 Z", dark),
        shine(76, 70, 10, 5),
        *eye(80, 100, 0.85), *eye(120, 100, 0.85),
        ellipse(100, 126, 8, 6, DARK),
        smile(100, 134, 10, 6),
        *blush((64, 126), (136, 126)),
        *coin,
    ]


def tessa():  # otter, endgame grinder with glasses
    fur, face, whisker = "#8C5A3C", "#E2BE98", "#F4E2CC"
    return [
        shoulders("#7A4A2E"),
        circle(52, 82, 11, fur), circle(148, 82, 11, fur),
        ellipse(100, 106, 54, 50, fur),
        shine(80, 72, 12, 6),
        ellipse(100, 124, 40, 30, face),
        stroke("M62 124 L38 118", whisker, 3), stroke("M62 131 L38 133", whisker, 3),
        stroke("M138 124 L162 118", whisker, 3), stroke("M138 131 L162 133", whisker, 3),
        path("M92 112 C92 106 108 106 108 112 C108 118 102 122 100 122 C98 122 92 118 92 112 Z", DARK),
        stroke("M100 122 L100 128 M90 130 Q95 136 100 130 Q105 136 110 130", DARK, 3.5),
        *eye(80, 96, 0.8), *eye(120, 96, 0.8),
        circle(80, 96, 16, "none", stroke="#FFC53D", stroke_width=4),
        circle(120, 96, 16, "none", stroke="#FFC53D", stroke_width=4),
        stroke("M96 96 L104 96", "#FFC53D", 4),
        *blush((62, 114), (138, 114)),
    ]


def kofi():  # lion, attacker
    mane = "#E07A1F"
    scallops = [circle(round(100 + 64 * math.cos(math.radians(a)), 1), round(104 + 64 * math.sin(math.radians(a)), 1), 18, mane)
                for a in range(0, 360, 30)]
    return [
        *scallops,
        circle(100, 104, 66, mane),
        shoulders("#FFB43D"),
        circle(62, 66, 14, "#FFC53D"), circle(62, 66, 7, "#F29A1F"),
        circle(138, 66, 14, "#FFC53D"), circle(138, 66, 7, "#F29A1F"),
        circle(100, 108, 50, "#FFC53D"),
        shine(82, 76, 12, 6),
        ellipse(88, 128, 14, 11, "#FFE6A8"), ellipse(112, 128, 14, 11, "#FFE6A8"),
        path("M90 116 C94 113 106 113 110 116 L100 128 Z", "#5A2A10"),
        stroke("M100 128 L100 134 M90 138 Q100 144 110 138", DARK, 3.5),
        stroke("M70 84 L90 89", "#8A4A12", 4.5), stroke("M130 84 L110 89", "#8A4A12", 4.5),
        *eye(82, 101, 0.85), *eye(118, 101, 0.85),
        *blush((66, 122), (134, 122)),
    ]


def ingrid():  # penguin, defender with a scarf
    # Near-black with a lighter rim, so the head stands out on the dark disc.
    feathers, rim = "#0D111E", "#56669E"
    return [
        shoulders(feathers),
        path("M66 200 C70 176 84 164 100 164 C116 164 130 176 134 200 Z", WHITE),
        ellipse(100, 100, 57, 59, rim),
        ellipse(100, 100, 54, 56, feathers),
        ellipse(82, 108, 26, 30, WHITE), ellipse(118, 108, 26, 30, WHITE),
        shine(82, 62, 12, 6),
        *eye(84, 102, 0.85), *eye(116, 102, 0.85),
        path("M88 116 C92 112 108 112 112 116 L100 132 Z", "#FFA23D"),
        *blush((70, 124), (130, 124)),
        rect(54, 150, 92, 16, 8, "#2EE6D6"),
        path("M64 158 L52 190 L66 194 L76 162 Z", "#2EE6D6"),
    ]


def nova():  # cat, trickster (winks)
    fur, whisker = "#B9A6FF", "#F4F0FF"
    ear, ear_in = "M50 92 L52 36 L94 62 Z", "M60 80 L62 50 L84 64 Z"
    return [
        shoulders("#8F7BE8"),
        path(ear, fur), path(mirror(ear), fur),
        path(ear_in, "#FF9EC4"), path(mirror(ear_in), "#FF9EC4"),
        ellipse(100, 106, 56, 50, fur),
        shine(78, 74, 12, 6),
        star(100, 72, 11, "#FFC53D"),
        stroke("M64 118 L38 112", whisker, 3), stroke("M64 125 L38 127", whisker, 3),
        stroke("M136 118 L162 112", whisker, 3), stroke("M136 125 L162 127", whisker, 3),
        *eye(82, 100, 0.9), *wink(118, 100),
        path("M94 115 L106 115 L100 122 Z", "#FF7FAF"),
        stroke("M100 122 Q95 130 88 126 M100 122 Q105 130 112 126", DARK, 3.5),
        *blush((68, 116), (132, 116)),
    ]


def titan():  # dragon, attacker
    horn = "M64 66 C56 44 60 26 72 14 C72 34 78 48 86 58 Z"
    return [
        path(horn, "#F5E6C8"), path(mirror(horn), "#F5E6C8"),
        path("M90 56 L100 38 L110 56 Z", "#1E8E5A"),
        shoulders("#1E8E5A"),
        path("M84 200 L90 176 L110 176 L116 200 Z", "#7BE0AE"),
        ellipse(100, 104, 54, 50, "#2BB673"),
        shine(78, 72, 12, 6),
        ellipse(100, 128, 32, 22, "#5FD39A"),
        ellipse(90, 124, 4, 3, DARK), ellipse(110, 124, 4, 3, DARK),
        stroke("M80 136 Q100 148 120 136", DARK, 4),
        path("M88 139 L92 147 L96 140 Z", WHITE),
        stroke("M64 80 L90 86", "#145E3C", 5), stroke("M136 80 L110 86", "#145E3C", 5),
        *eye(80, 98, 0.9), *eye(120, 98, 0.9),
    ]


def bolt():  # robot, full strength
    steel, deep, glow = "#C9D3E8", "#8792AE", "#2EE6D6"
    return [
        stroke("M100 46 L100 24", deep, 5),
        circle(100, 18, 13, glow, opacity=".3"), circle(100, 18, 8, glow),
        shoulders(deep),
        rect(82, 168, 36, 22, 6, DARK), circle(100, 179, 5, "#A8E63A"),
        circle(40, 104, 10, deep), circle(160, 104, 10, deep),
        rect(44, 46, 112, 112, 26, steel),
        ellipse(66, 56, 12, 5, WHITE, opacity=".4"),
        rect(58, 64, 84, 72, 16, DARK),
        rect(72, 82, 16, 22, 6, glow), rect(112, 82, 16, 22, 6, glow),
        stroke("M80 114 Q100 126 120 114", glow, 5),
    ]


BOTS = [
    ("pebble", "Pebble", pebble), ("wayne", "Wayne", wayne), ("juno", "Juno", juno),
    ("bramble", "Bramble", bramble), ("ziggy", "Ziggy", ziggy), ("marlo", "Marlo", marlo),
    ("tessa", "Tessa", tessa), ("kofi", "Kofi", kofi), ("ingrid", "Ingrid", ingrid),
    ("nova", "Nova", nova), ("titan", "Titan", titan), ("bolt", "Bolt", bolt),
]


def main():
    svg_dir, drawable_dir = sys.argv[1], sys.argv[2]
    os.makedirs(svg_dir, exist_ok=True)
    for bot_id, name, draw in BOTS:
        body = "\n  ".join(draw())
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" '
               f'aria-label="{name}, a chess bot">\n  {body}\n</svg>\n')
        src = os.path.join(svg_dir, f"{bot_id}.svg").replace("\\", "/")
        with open(src, "w", encoding="utf-8") as f:
            f.write(svg)
        convert(src, os.path.join(drawable_dir, f"bot_{bot_id}.xml"), name, source_dir="design/bots")
        print("wrote", src, "and", f"bot_{bot_id}.xml")


if __name__ == "__main__":
    main()
