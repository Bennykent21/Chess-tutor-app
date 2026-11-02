#!/usr/bin/env python3
"""
Draws the bot roster's faces (plan 6.1): twelve original animal characters in the coaches'
style (flat muted colours, one shaded side, small ink eyes with a glint, brass accents), each
with a hint of its playing style.

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

INK = "#1F2430"
IVORY = "#F4EEE2"
WHITE = "#FFFFFF"
BRASS = "#C99A4E"
BRASS_SHADE = "#B5873E"


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


def eye(cx, cy, s=1.0, look=(0, 0)):
    """A small ink eye with a glint, like the coaches'."""
    dx, dy = look
    return [
        circle(cx + dx, cy + dy, 7.5 * s, INK),
        circle(cx + dx + 2.4 * s, cy + dy - 2.6 * s, 2.6 * s, WHITE),
    ]


def wink(cx, cy):
    return [stroke(f"M{cx - 9} {cy + 2} Q{cx} {cy - 7} {cx + 9} {cy + 2}", INK, 4.4)]


def blush(*points):
    return [circle(x, y, 9, "#E59C7E", opacity=".32") for x, y in points]


def smile(cx, cy, w=11, d=7, color=INK):
    return stroke(f"M{cx - w} {cy} Q{cx} {cy + d} {cx + w} {cy}", color, 4.4)


def shine(cx, cy, rx=11, ry=5.5):
    """The soft highlight at the top left of every coach."""
    return el("ellipse", cx=cx, cy=cy, rx=rx, ry=ry, fill=WHITE, opacity=".26", transform=f"rotate(-28 {cx} {cy})")


def crescent(cx, cy, rx, ry, color, inner=0.74):
    """The shaded right side of a round shape, as on Sage's head."""
    return path(f"M{cx} {cy - ry} A{rx} {ry} 0 0 1 {cx} {cy + ry} A{rx * inner:g} {ry} 0 0 0 {cx} {cy - ry} Z", color)


def shoulders(color, dark, top=152):
    return [
        path(f"M28 200 C32 {top + 18} 60 {top} 100 {top} C140 {top} 168 {top + 18} 172 200 Z", color),
        path(f"M112 {top + 1} C142 {top + 3} 168 {top + 20} 172 200 L136 200 C134 {top + 26} 126 {top + 10} 112 {top + 1} Z", dark),
    ]


def scarf(color, dark, y=148):
    return [
        rect(58, y, 84, 16, 8, color),
        path(f"M116 {y + 10} L132 {y + 42} L118 {y + 47} L106 {y + 14} Z", color),
        path(f"M124 {y + 10} L132 {y + 42} L125 {y + 44} L114 {y + 12} Z", dark),
        path(f"M112 {y} L134 {y} C139 {y} 142 {y + 4} 142 {y + 8} C142 {y + 12} 139 {y + 16} 134 {y + 16} L112 {y + 16} Z", dark),
    ]


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
    body, dark = "#CDBD97", "#B8A882"
    return [
        circle(128, 118, 48, "#B8734A"),
        crescent(128, 118, 48, 48, "#A5643E"),
        path("M128 118 m-6 0 a6 6 0 1 1 12 0 a12 12 0 1 1 -24 0 a18 18 0 1 1 36 0 a24 24 0 1 1 -48 0",
             stroke=BRASS, stroke_width=5.5, stroke_linecap="round"),
        path("M22 200 C26 178 50 166 82 166 L178 170 C190 176 190 200 190 200 Z", body),
        path("M118 168 L178 170 C190 176 190 200 190 200 L140 200 C138 186 130 174 118 168 Z", dark),
        stroke("M66 92 L56 54", body, 10), stroke("M88 92 L98 54", body, 10),
        ellipse(76, 124, 38, 42, body),
        crescent(76, 124, 38, 42, dark),
        shine(60, 100),
        circle(55, 48, 11, body), circle(99, 48, 11, body),
        *eye(56, 48, 0.72), *eye(100, 48, 0.72),
        smile(76, 134, 10, 6),
        *blush((54, 128), (98, 128)),
    ]


def wayne():  # bear cub, casual
    fur, dark, inner = "#A9794F", "#94663F", "#D8B48A"
    return [
        *shoulders(dark, "#825836"),
        circle(56, 60, 20, fur), circle(56, 60, 10, inner),
        circle(144, 60, 20, dark), circle(144, 60, 10, "#C4A077"),
        circle(100, 104, 54, fur),
        crescent(100, 104, 54, 54, dark),
        shine(76, 70),
        ellipse(100, 124, 26, 20, "#EAD6B6"),
        ellipse(100, 114, 9, 6, INK),
        stroke("M100 119 L100 126 M91 128 Q100 136 109 128", INK, 4),
        *eye(80, 96, 0.92), *eye(120, 96, 0.92),
        *blush((66, 118), (134, 118)),
        *scarf(BRASS, BRASS_SHADE),
    ]


def juno():  # fox, trickster with a mask
    fur, dark, cream, tip = "#C8763F", "#B0652F", IVORY, "#3A2C24"
    ear, ear_tip, ear_in = "M50 84 L56 30 L92 62 Z", "M54 50 L56 30 L70 42 Z", "M60 74 L62 46 L82 64 Z"
    return [
        *shoulders(fur, dark),
        path("M76 200 C80 176 90 164 100 164 C110 164 120 176 124 200 Z", cream),
        path(ear, fur), path(mirror(ear), dark),
        path(ear_tip, tip), path(mirror(ear_tip), tip),
        path(ear_in, "#E7BE9A"), path(mirror(ear_in), "#D6AA85"),
        path("M44 96 C44 64 70 52 100 52 C130 52 156 64 156 96 C156 124 132 150 100 156 C68 150 44 124 44 96 Z", fur),
        path("M100 52 C130 52 156 64 156 96 C156 124 132 150 100 156 C124 132 134 84 100 52 Z", dark),
        path("M48 104 C60 118 80 124 100 150 C120 124 140 118 152 104 C150 132 128 154 100 158 C72 154 50 132 48 104 Z", cream),
        shine(72, 68),
        path("M56 84 C70 74 90 78 100 86 C110 78 130 74 144 84 C146 98 132 106 118 104 C110 102 104 98 100 96 "
             "C96 98 90 102 82 104 C68 106 54 98 56 84 Z", "#4F4358"),
        circle(82, 91, 7.5, IVORY), circle(118, 91, 7.5, IVORY),
        circle(83, 92, 5, INK), circle(119, 92, 5, INK),
        circle(84.6, 90.4, 1.8, WHITE), circle(120.6, 90.4, 1.8, WHITE),
        ellipse(100, 128, 7, 5, INK),
        smile(100, 136, 8, 5),
        *blush((66, 120), (134, 120)),
    ]


def bramble():  # hedgehog, defender curled in its spines
    return [
        spikes(100, 106, 74, 54, 150, 390, 9, "#6A523F"),
        spikes(100, 106, 62, 48, 165, 375, 8, "#7D634C"),
        *shoulders("#6A523F", "#5C4635"),
        ellipse(100, 112, 46, 44, "#E8D3AE"),
        crescent(100, 112, 46, 44, "#D9C198"),
        circle(64, 78, 10, "#D9C198"), circle(136, 78, 10, "#CBB288"),
        shine(82, 86, 10, 5),
        ellipse(100, 128, 16, 12, "#F4E8D2"),
        circle(100, 122, 6, INK),
        smile(100, 136, 8, 5),
        *eye(84, 104, 0.82), *eye(116, 104, 0.82),
        *blush((72, 124), (128, 124)),
    ]


def ziggy():  # parrot, attacker
    body, dark = "#4F9388", "#437F75"
    beak = "M86 112 C86 102 114 102 114 112 C114 126 106 138 100 142 C98 132 90 124 86 112 Z"
    return [
        path("M86 62 C72 46 66 34 68 24 C80 34 88 46 94 58 Z", "#4D6E9A"),
        path("M104 58 C104 36 116 24 128 22 C122 36 116 50 112 62 Z", "#D9A441"),
        path("M92 58 C84 34 90 20 98 14 C100 30 104 44 104 58 Z", "#C96A52"),
        *shoulders(dark, "#396E66"),
        ellipse(100, 106, 52, 54, body),
        crescent(100, 106, 52, 54, dark),
        shine(74, 72),
        ellipse(80, 96, 16, 17, IVORY), ellipse(120, 96, 16, 17, IVORY),
        *eye(81, 97, 0.82), *eye(121, 97, 0.82),
        path(beak, BRASS),
        path("M100 104 C106 104 114 106 114 112 C114 126 106 138 100 142 Z", BRASS_SHADE),
        *blush((64, 122), (136, 122)),
    ]


def marlo():  # raccoon, gambit lover
    grey, dark, mask, light = "#8E939C", "#7B808A", "#2F333B", "#E9E4DA"
    ear, ear_in = "M50 84 C44 60 48 44 56 38 C70 44 82 54 88 64 Z", "M58 74 C56 60 58 52 62 48 C70 54 76 60 80 66 Z"
    return [
        *shoulders(dark, "#6C717B"),
        path(ear, grey), path(mirror(ear), dark),
        path(ear_in, mask), path(mirror(ear_in), mask),
        ellipse(100, 106, 56, 50, grey),
        crescent(100, 106, 56, 50, dark),
        path("M48 108 C60 92 84 92 100 104 C116 92 140 92 152 108 C146 132 124 150 100 152 C76 150 54 132 48 108 Z", light),
        path("M94 60 L106 60 L104 94 L96 94 Z", mask),
        path("M50 96 C62 82 86 84 100 98 C114 84 138 82 150 96 C150 110 134 116 120 112 C110 110 104 104 100 102 "
             "C96 104 90 110 80 112 C66 116 50 110 50 96 Z", mask),
        shine(72, 72),
        circle(80, 100, 7.5, IVORY), circle(120, 100, 7.5, IVORY),
        circle(81, 101, 5, INK), circle(121, 101, 5, INK),
        circle(82.6, 99.4, 1.8, WHITE), circle(122.6, 99.4, 1.8, WHITE),
        ellipse(100, 126, 8, 6, INK),
        smile(100, 134, 10, 6),
        *blush((66, 128), (134, 128)),
    ]


def tessa():  # otter, endgame grinder with glasses
    fur, dark, face, whisker = "#8A5F45", "#774F38", "#E0C8A6", "#F4E8D2"
    return [
        *shoulders(dark, "#67432F"),
        circle(52, 84, 11, fur), circle(148, 84, 11, dark),
        ellipse(100, 106, 54, 50, fur),
        crescent(100, 106, 54, 50, dark),
        shine(74, 74),
        ellipse(100, 124, 40, 30, face),
        stroke("M62 124 L40 118", whisker, 2.6), stroke("M62 131 L40 133", whisker, 2.6),
        stroke("M138 124 L160 118", whisker, 2.6), stroke("M138 131 L160 133", whisker, 2.6),
        path("M92 112 C92 106 108 106 108 112 C108 118 102 122 100 122 C98 122 92 118 92 112 Z", INK),
        stroke("M100 122 L100 127 M91 129 Q95.5 135 100 129 Q104.5 135 109 129", INK, 3.6),
        circle(80, 96, 17, IVORY, opacity=".82"), circle(120, 96, 17, IVORY, opacity=".82"),
        *eye(80, 97, 0.8), *eye(120, 97, 0.8),
        circle(80, 96, 17, "none", stroke=BRASS, stroke_width=3.6),
        circle(120, 96, 17, "none", stroke=BRASS, stroke_width=3.6),
        stroke("M97 94 Q100 91 103 94", BRASS, 3.6),
    ]


def kofi():  # lion, attacker
    mane, mane_dark = "#A9602C", "#955226"
    scallops = [circle(round(100 + 64 * math.cos(math.radians(a)), 1), round(104 + 64 * math.sin(math.radians(a)), 1), 18,
                       mane_dark if -80 < ((a + 180) % 360) - 180 < 80 else mane)
                for a in range(0, 360, 30)]
    return [
        *scallops,
        circle(100, 104, 66, mane),
        crescent(100, 104, 66, 66, mane_dark, inner=0.8),
        *shoulders("#D29A4B", "#BD863C"),
        circle(62, 66, 14, "#DDAA5E"), circle(62, 66, 7, "#BD863C"),
        circle(138, 66, 14, "#CC9A50"), circle(138, 66, 7, "#AD7A35"),
        circle(100, 108, 50, "#DDAA5E"),
        crescent(100, 108, 50, 50, "#CC9A50"),
        shine(78, 78),
        ellipse(88, 128, 14, 11, "#F2E0BA"), ellipse(112, 128, 14, 11, "#F2E0BA"),
        path("M90 116 C94 113 106 113 110 116 L100 128 Z", "#4A2A18"),
        stroke("M100 128 L100 134 M90 138 Q100 144 110 138", INK, 3.6),
        stroke("M70 84 L90 90", "#6F3F1C", 4.6), stroke("M130 84 L110 90", "#6F3F1C", 4.6),
        *eye(82, 102, 0.86), *eye(118, 102, 0.86),
        *blush((66, 122), (134, 122)),
    ]


def ingrid():  # penguin, defender with a scarf
    feathers, rim = "#2E333B", "#4A525E"
    return [
        *shoulders(rim, "#3D4450"),
        path("M66 200 C70 176 84 164 100 164 C116 164 130 176 134 200 Z", IVORY),
        ellipse(100, 100, 56, 58, rim),
        ellipse(100, 101, 53, 55, feathers),
        crescent(100, 101, 53, 55, "#252A31"),
        ellipse(82, 108, 26, 30, IVORY), ellipse(118, 108, 26, 30, IVORY),
        shine(74, 62),
        *eye(84, 102, 0.86), *eye(116, 102, 0.86),
        path("M88 116 C92 112 108 112 112 116 L100 132 Z", BRASS),
        path("M100 113 C104 113 108 114 112 116 L100 132 Z", BRASS_SHADE),
        *blush((68, 124), (132, 124)),
        *scarf("#5B8A86", "#4E7773", y=150),
    ]


def nova():  # cat, trickster (winks)
    fur, dark, whisker = "#736E8E", "#64607C", "#EDE8EC"
    ear, ear_in = "M50 92 L52 36 L94 62 Z", "M60 80 L62 50 L84 64 Z"
    return [
        *shoulders(dark, "#57536C"),
        path(ear, fur), path(mirror(ear), dark),
        path(ear_in, "#BC95A4"), path(mirror(ear_in), "#AB8494"),
        ellipse(100, 106, 56, 50, fur),
        crescent(100, 106, 56, 50, dark),
        shine(74, 76),
        star(100, 72, 11, BRASS),
        stroke("M64 118 L40 112", whisker, 2.6), stroke("M64 125 L40 127", whisker, 2.6),
        stroke("M136 118 L160 112", whisker, 2.6), stroke("M136 125 L160 127", whisker, 2.6),
        circle(82, 100, 9.5, IVORY), circle(83, 101, 6, INK), circle(85, 99, 2.1, WHITE),
        stroke("M109 102 Q118 93 127 102", IVORY, 4.6),
        path("M94 115 L106 115 L100 122 Z", "#E0A7B4"),
        stroke("M100 122 Q95 130 88 126 M100 122 Q105 130 112 126", IVORY, 3.6),
        *blush((68, 118), (132, 118)),
    ]


def titan():  # dragon, attacker
    scale, dark = "#3F7059", "#355F4C"
    horn = "M64 66 C56 44 60 26 72 14 C72 34 78 48 86 58 Z"
    return [
        path(horn, "#D2C4A2"), path(mirror(horn), "#BFB18F"),
        path("M90 56 L100 38 L110 56 Z", BRASS),
        *shoulders(dark, "#2C5040"),
        path("M84 200 L90 176 L110 176 L116 200 Z", "#86A88F"),
        ellipse(100, 104, 54, 50, scale),
        crescent(100, 104, 54, 50, dark),
        shine(74, 74),
        ellipse(100, 128, 32, 22, "#86A88F"),
        ellipse(90, 124, 4, 3, INK), ellipse(110, 124, 4, 3, INK),
        stroke("M80 136 Q100 148 120 136", INK, 4.2),
        path("M88 139 L92 147 L96 140 Z", IVORY),
        stroke("M64 82 L90 88", "#20382C", 5), stroke("M136 82 L110 88", "#20382C", 5),
        circle(80, 100, 9, IVORY), circle(120, 100, 9, IVORY),
        circle(81, 101, 5.6, INK), circle(121, 101, 5.6, INK),
        circle(83, 99, 2, WHITE), circle(123, 99, 2, WHITE),
    ]


def bolt():  # robot, full strength
    steel, deep, glow = "#B7BEC9", "#9AA2AF", "#E2BE82"
    return [
        stroke("M100 46 L100 24", deep, 5),
        circle(100, 18, 13, BRASS, opacity=".28"), circle(100, 18, 8, BRASS),
        *shoulders(deep, "#868E9C"),
        rect(82, 168, 36, 22, 6, "#2A2F37"), circle(100, 179, 5, BRASS),
        circle(40, 104, 10, deep), circle(160, 104, 10, "#868E9C"),
        rect(44, 46, 112, 112, 26, steel),
        path("M122 46 L130 46 C144 46 156 58 156 72 L156 132 C156 146 144 158 130 158 L122 158 C136 130 136 74 122 46 Z", deep),
        shine(66, 58),
        rect(58, 64, 84, 72, 16, "#2A2F37"),
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
