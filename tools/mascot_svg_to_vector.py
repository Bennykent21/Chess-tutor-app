"""Converts the coach SVGs in design/mascot/ (simple shapes only) to Android VectorDrawables.

Usage: python3 tools/mascot_svg_to_vector.py design/mascot app/src/main/res/drawable

Each coach (Pip, Rookie, Sage) has one SVG per mood (ready, thinking, kind, proud), named
<coach>_<mood>.svg, and becomes mascot_<coach>_<mood>.xml.

Handles: path, circle, ellipse, rect (rx, rotate transform), line, objectBoundingBox
linear gradients (converted exactly to user space), a userSpaceOnUse checker <pattern> on a
rect or a path (expanded into explicit squares, clipped to a path's outline), opacity,
fill-opacity, stroke-linecap, stroke-linejoin and <g clip-path>.
"""
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"


def num(v):
    f = float(v)
    return f"{f:g}"


def color(v):
    v = v.strip()
    if len(v) == 4:  # #abc
        v = "#" + "".join(c * 2 for c in v[1:])
    return "#FF" + v[1:].upper()


def ellipse_path(cx, cy, rx, ry):
    return (f"M{num(cx - rx)},{num(cy)}"
            f"a{num(rx)},{num(ry)} 0 1,0 {num(2 * rx)},0"
            f"a{num(rx)},{num(ry)} 0 1,0 {num(-2 * rx)},0Z")


def rect_path(x, y, w, h, rx):
    rx = min(rx, w / 2, h / 2)
    if rx <= 0:
        return f"M{num(x)},{num(y)}h{num(w)}v{num(h)}h{num(-w)}Z"
    return (f"M{num(x + rx)},{num(y)}H{num(x + w - rx)}"
            f"A{num(rx)},{num(rx)} 0 0,1 {num(x + w)},{num(y + rx)}V{num(y + h - rx)}"
            f"A{num(rx)},{num(rx)} 0 0,1 {num(x + w - rx)},{num(y + h)}H{num(x + rx)}"
            f"A{num(rx)},{num(rx)} 0 0,1 {num(x)},{num(y + h - rx)}V{num(y + rx)}"
            f"A{num(rx)},{num(rx)} 0 0,1 {num(x + rx)},{num(y)}Z")


def shape_path(el):
    tag = el.tag.replace(NS, "")
    a = {k: float(v) for k, v in el.attrib.items()
         if k in ("cx", "cy", "r", "rx", "ry", "x", "y", "width", "height", "x1", "y1", "x2", "y2")}
    if tag == "path":
        return " ".join(el.get("d").split()), None
    if tag == "circle":
        return ellipse_path(a["cx"], a["cy"], a["r"], a["r"]), None
    if tag == "ellipse":
        return ellipse_path(a["cx"], a["cy"], a["rx"], a["ry"]), None
    if tag == "rect":
        x, y, w, h = a.get("x", 0), a.get("y", 0), a["width"], a["height"]
        return rect_path(x, y, w, h, a.get("rx", 0)), (x, y, w, h)
    if tag == "line":
        return f"M{num(a['x1'])},{num(a['y1'])}L{num(a['x2'])},{num(a['y2'])}", None
    raise ValueError(f"unsupported element {tag}")


def gradient_xml(grad, bbox, ind):
    """objectBoundingBox (0,0)->(1,1) mapped exactly into user space for this bbox."""
    x, y, w, h = bbox
    gx1, gy1 = float(grad.get("x1", 0)), float(grad.get("y1", 0))
    gx2, gy2 = float(grad.get("x2", 1)), float(grad.get("y2", 0))
    # Value along the bbox-space vector, expressed in user space: v(p) = g . p + c.
    dx, dy = (gx2 - gx1), (gy2 - gy1)
    L2 = dx * dx + dy * dy
    gux, guy = dx / L2 / w, dy / L2 / h          # gradient of v in user space
    sx, sy = x + gx1 * w, y + gy1 * h            # v = 0 here
    t = 1.0 / (gux * gux + guy * guy)            # step along grad to reach v = 1
    ex, ey = sx + gux * t, sy + guy * t
    stops = grad.findall(NS + "stop")
    out = [f'{ind}<aapt:attr name="android:fillColor">',
           f'{ind}  <gradient android:type="linear"',
           f'{ind}    android:startX="{num(round(sx, 3))}" android:startY="{num(round(sy, 3))}"',
           f'{ind}    android:endX="{num(round(ex, 3))}" android:endY="{num(round(ey, 3))}">']
    for s in stops:
        out.append(f'{ind}    <item android:offset="{num(s.get("offset"))}" android:color="{color(s.get("stop-color"))}"/>')
    out += [f"{ind}  </gradient>", f"{ind}</aapt:attr>"]
    return out


def path_xml(el, defs, ind, path_override=None):
    d, bbox = shape_path(el)
    if path_override:
        d = path_override
    # A line has no area, so SVG's default black fill would never show.
    fill = el.get("fill", "none" if el.tag == NS + "line" else "#000")
    stroke = el.get("stroke")
    opacity = el.get("opacity")
    fill_alpha = float(opacity or 1) * float(el.get("fill-opacity", 1))
    attrs = [f'android:pathData="{d}"']
    grad = None
    if fill.startswith("url("):
        grad = defs[fill[5:-1]]
    elif fill != "none":
        attrs.append(f'android:fillColor="{color(fill)}"')
        if fill_alpha != 1:
            attrs.append(f'android:fillAlpha="{num(fill_alpha)}"')
    if stroke:
        attrs.append(f'android:strokeColor="{color(stroke)}"')
        attrs.append(f'android:strokeWidth="{num(el.get("stroke-width", 1))}"')
        if el.get("stroke-linecap"):
            attrs.append(f'android:strokeLineCap="{el.get("stroke-linecap")}"')
        if el.get("stroke-linejoin"):
            attrs.append(f'android:strokeLineJoin="{el.get("stroke-linejoin")}"')
        if opacity:
            attrs.append(f'android:strokeAlpha="{num(opacity)}"')
    head = f"{ind}<path\n" + "\n".join(f"{ind}    {a}" for a in attrs)
    if grad is None:
        return [head + " />"]
    return [head + ">"] + gradient_xml(grad, bbox, ind + "  ") + [f"{ind}</path>"]


def pattern_squares(pat, area):
    """Expands a 2-colour checker pattern tile over a rectangle into explicit squares."""
    px, py = float(pat.get("x", 0)), float(pat.get("y", 0))
    pw = float(pat.get("width"))
    rects = pat.findall(NS + "rect")
    base = rects[0].get("fill")
    cell = float(rects[1].get("width"))
    fg = rects[1].get("fill")
    ax, ay, aw, ah = area
    squares = []
    import math
    a0 = math.floor((ax - px) / cell)
    a1 = math.ceil((ax + aw - px) / cell)
    b0 = math.floor((ay - py) / cell)
    b1 = math.ceil((ay + ah - py) / cell)
    assert pw == 2 * cell
    for b in range(b0, b1):
        for a in range(a0, a1):
            if (a + b) % 2 == 0:
                sx, sy = px + a * cell, py + b * cell
                # Clip each square to the filled rectangle.
                x0, y0 = max(sx, ax), max(sy, ay)
                x1, y1 = min(sx + cell, ax + aw), min(sy + cell, ay + ah)
                if x1 > x0 and y1 > y0:
                    squares.append(f"M{num(x0)},{num(y0)}H{num(x1)}V{num(y1)}H{num(x0)}Z")
    return base, fg, squares


def convert(src, out, name, source_dir="design/mascot"):
    root = ET.parse(src).getroot()
    vb = [float(v) for v in root.get("viewBox").split()]
    vw, vh = vb[2], vb[3]
    defs = {}
    clips = {}
    for d in root.iter(NS + "defs"):
        for child in d:
            tag = child.tag.replace(NS, "")
            if tag == "clipPath":
                clips[child.get("id")] = shape_path(list(child)[0])[0]
            else:
                defs[child.get("id")] = child
    uses_gradient = any(t.replace(NS, "") == "linearGradient" for t in [c.tag for c in defs.values()])
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             f"<!-- {name}: generated from {source_dir}/{src.split('/')[-1]} (the source of truth).",
             "     Original art, GPL-3.0 with the app. See LICENSES.md. -->",
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"']
    if uses_gradient:
        lines.append('    xmlns:aapt="http://schemas.android.com/aapt"')
    lines += [f'    android:width="{num(vw / 2)}dp"',
              f'    android:height="{num(vh / 2)}dp"',
              f'    android:viewportWidth="{num(vw)}"',
              f'    android:viewportHeight="{num(vh)}">']

    def emit(el, ind):
        tag = el.tag.replace(NS, "")
        if tag == "defs":
            return
        if tag == "g":
            lines.append(f"{ind}<group>")
            cp = el.get("clip-path")
            if cp:
                lines.append(f'{ind}  <clip-path android:pathData="{clips[cp[5:-1]]}" />')
            for c in el:
                emit(c, ind + "  ")
            lines.append(f"{ind}</group>")
            return
        fill = el.get("fill", "")
        if fill.startswith("url(") and defs[fill[5:-1]].tag == NS + "pattern":
            outline, area = shape_path(el)
            # A path has no box of its own: cover the box around its points and clip to it.
            clipped = area is None
            if clipped:
                nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", outline)]
                xs, ys = nums[0::2], nums[1::2]
                area = (min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys))
                lines.append(f"{ind}<group>")
                lines.append(f'{ind}  <clip-path android:pathData="{outline}" />')
                ind += "  "
            base, fg, squares = pattern_squares(defs[fill[5:-1]], area)
            lines.append(f'{ind}<path android:pathData="{outline}" android:fillColor="{color(base)}" />')
            # One path per row of squares keeps each path short (lint's VectorPath check).
            rows = {}
            for sq in squares:
                rows.setdefault(sq.split("H")[0].split(",")[1], []).append(sq)
            for row in rows.values():
                lines.append(f'{ind}<path android:fillColor="{color(fg)}"')
                lines.append(f'{ind}    android:pathData="{"".join(row)}" />')
            if clipped:
                lines.append(f"{ind[:-2]}</group>")
            return
        tr = el.get("transform")
        if tr:
            m = re.fullmatch(r"rotate\(([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\)", tr.strip())
            if not m:
                raise ValueError(f"unsupported transform {tr}")
            lines.append(f'{ind}<group android:rotation="{m[1]}" android:pivotX="{m[2]}" android:pivotY="{m[3]}">')
            lines.extend(path_xml(el, defs, ind + "  "))
            lines.append(f"{ind}</group>")
            return
        lines.extend(path_xml(el, defs, ind))

    for el in root:
        emit(el, "    ")
    lines.append("</vector>")
    with open(out, "w") as f:
        f.write("\n".join(lines) + "\n")


COACHES = {"pip": "Pip", "rookie": "Rookie", "sage": "Sage"}
MOODS = ["ready", "thinking", "kind", "proud"]

if __name__ == "__main__":
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    for coach, name in COACHES.items():
        for mood in MOODS:
            out = f"mascot_{coach}_{mood}.xml"
            convert(f"{src_dir}/{coach}_{mood}.svg", f"{out_dir}/{out}", f"{name}, {mood}")
            print("wrote", out)
