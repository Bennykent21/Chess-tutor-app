"""Renders the legacy (API 24-25) launcher icons (mipmap-*/ic_launcher*.webp) from the adaptive icon's VectorDrawables.

Supports what ic_launcher_background.xml / ic_launcher_foreground.xml use: groups with
translate/scale, fills (solid or linear gradient), strokes, and path commands
M L H V C A Z in absolute and relative forms. Draws at 4x and downsamples.

Usage: python3 tools/render_launcher_icons.py app/src/main/res   (needs Pillow)
"""
import math
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

A = "{http://schemas.android.com/apk/res/android}"
SS = 4  # supersampling


def argb(v):
    v = v.lstrip("#")
    if len(v) == 6:
        v = "FF" + v
    a, r, g, b = (int(v[i:i + 2], 16) for i in (0, 2, 4, 6))
    return (r, g, b, a)


def tokens(d):
    return re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?", d)


def arc_points(x1, y1, rx, ry, phi, fa, fs, x2, y2, n=48):
    """SVG endpoint arc to points (spec F.6.5)."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    phi = math.radians(phi)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    rx, ry = abs(rx), abs(ry)
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    coef = math.sqrt(max(0, num / den)) * (-1 if fa == fs else 1)
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    pts = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        pts.append((cx + rx * math.cos(t) * c - ry * math.sin(t) * s,
                    cy + rx * math.cos(t) * s + ry * math.sin(t) * c))
    return pts


def flatten(d):
    """Returns a list of subpaths, each (points, closed)."""
    tk = tokens(d)
    i = 0
    subs, cur = [], []
    x = y = sx = sy = 0.0
    cmd = None
    closed = False

    def nums(k):
        nonlocal i
        out = [float(t) for t in tk[i:i + k]]
        i += k
        return out

    while i < len(tk):
        if re.fullmatch(r"[A-Za-z]", tk[i]):
            cmd = tk[i]
            i += 1
            if cmd in "Zz":
                if cur:
                    subs.append((cur, True))
                cur = []
                x, y = sx, sy
                continue
        rel = cmd.islower()
        C = cmd.upper()
        if C == "M":
            px, py = nums(2)
            if rel:
                px, py = x + px, y + py
            if cur:
                subs.append((cur, False))
            x, y = sx, sy = px, py
            cur = [(x, y)]
            cmd = "l" if rel else "L"
        elif C == "L":
            px, py = nums(2)
            x, y = (x + px, y + py) if rel else (px, py)
            cur.append((x, y))
        elif C == "H":
            (px,) = nums(1)
            x = x + px if rel else px
            cur.append((x, y))
        elif C == "V":
            (py,) = nums(1)
            y = y + py if rel else py
            cur.append((x, y))
        elif C == "C":
            a = nums(6)
            if rel:
                a = [a[0] + x, a[1] + y, a[2] + x, a[3] + y, a[4] + x, a[5] + y]
            for k in range(1, 25):
                t = k / 24
                mt = 1 - t
                cur.append((mt ** 3 * x + 3 * mt * mt * t * a[0] + 3 * mt * t * t * a[2] + t ** 3 * a[4],
                            mt ** 3 * y + 3 * mt * mt * t * a[1] + 3 * mt * t * t * a[3] + t ** 3 * a[5]))
            x, y = a[4], a[5]
        elif C == "A":
            rx, ry, phi, fa, fs, px, py = nums(7)
            if rel:
                px, py = x + px, y + py
            cur.extend(arc_points(x, y, rx, ry, phi, int(fa), int(fs), px, py))
            x, y = px, py
        else:
            raise ValueError(f"unsupported command {cmd}")
    if cur:
        subs.append((cur, closed))
    return subs


def render_vector(path, size):
    root = ET.parse(path).getroot()
    vw = float(root.get(A + "viewportWidth"))
    px = size * SS
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    k = px / vw

    def walk(el, tx, ty, s):
        for ch in el:
            tag = ch.tag
            if tag == "group":
                walk(ch, tx + float(ch.get(A + "translateX", 0)) * s, ty + float(ch.get(A + "translateY", 0)) * s,
                     s * float(ch.get(A + "scaleX", 1)))
            elif tag == "path":
                draw_path(ch, tx, ty, s)

    def draw_path(el, tx, ty, s):
        subs = flatten(el.get(A + "pathData"))
        tp = [([((tx + xx * s) * k, (ty + yy * s) * k) for xx, yy in pts], closed) for pts, closed in subs]
        fill = el.get(A + "fillColor")
        grad = None
        for attr in el:
            if attr.tag == "{http://schemas.android.com/aapt}attr":
                grad = attr.find("gradient")
        if grad is not None:
            mask = Image.new("L", img.size, 0)
            md = ImageDraw.Draw(mask)
            for pts, _ in tp:
                if len(pts) > 2:
                    md.polygon(pts, fill=255)
            g = linear_gradient(grad, tx, ty, s, k, img.size)
            img.paste(Image.composite(g, img, mask), (0, 0))
        elif fill and argb(fill)[3] > 0:
            layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ld = ImageDraw.Draw(layer)
            for pts, _ in tp:
                if len(pts) > 2:
                    ld.polygon(pts, fill=argb(fill))
            img.alpha_composite(layer)
        stroke = el.get(A + "strokeColor")
        if stroke:
            w = float(el.get(A + "strokeWidth", 1)) * s * k
            layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ld = ImageDraw.Draw(layer)
            col = argb(stroke)
            for pts, closed in tp:
                seq = pts + [pts[0]] if closed else pts
                ld.line(seq, fill=col, width=max(1, round(w)), joint="curve")
                r = w / 2  # round caps and joins
                for qx, qy in (seq[0], seq[-1]):
                    ld.ellipse([qx - r, qy - r, qx + r, qy + r], fill=col)
            img.alpha_composite(layer)

    walk(root, 0.0, 0.0, 1.0)
    return img


def linear_gradient(grad, tx, ty, s, k, size):
    x0 = (tx + float(grad.get(A + "startX")) * s) * k
    y0 = (ty + float(grad.get(A + "startY")) * s) * k
    x1 = (tx + float(grad.get(A + "endX")) * s) * k
    y1 = (ty + float(grad.get(A + "endY")) * s) * k
    stops = [(float(it.get(A + "offset")), argb(it.get(A + "color"))) for it in grad.findall("item")]
    w, h = size
    out = Image.new("RGBA", size)
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    px = out.load()
    for yy in range(h):
        for xx in range(w):
            t = max(0.0, min(1.0, ((xx - x0) * dx + (yy - y0) * dy) / L2))
            for j in range(len(stops) - 1):
                if t <= stops[j + 1][0]:
                    a, ca = stops[j]
                    b, cb = stops[j + 1]
                    u = 0 if b == a else (t - a) / (b - a)
                    px[xx, yy] = tuple(round(ca[c] + (cb[c] - ca[c]) * u) for c in range(4))
                    break
    return out


def legacy_icon(bg, fg, dp, density, round_shape):
    """Adaptive layers (108dp) cropped to the visible 72dp, then masked as a legacy icon."""
    full = 108 * density / 160
    canvas = render_vector(bg, round(full))
    canvas.alpha_composite(render_vector(fg, round(full)))
    big = canvas.size[0]
    crop = round(big * 18 / 108)
    canvas = canvas.crop((crop, crop, big - crop, big - crop))
    out_px = round(dp * density / 160) * SS
    margin = round(out_px * 2 / 48)  # 2dp margin, like Asset Studio's legacy icons
    inner = out_px - 2 * margin
    art = canvas.resize((inner, inner), Image.LANCZOS)
    mask = Image.new("L", (inner, inner), 0)
    md = ImageDraw.Draw(mask)
    if round_shape:
        md.ellipse([0, 0, inner - 1, inner - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, inner - 1, inner - 1], radius=round(inner * 0.14), fill=255)
    art.putalpha(Image.composite(art.getchannel("A"), Image.new("L", art.size, 0), mask))
    out = Image.new("RGBA", (out_px, out_px), (0, 0, 0, 0))
    out.alpha_composite(art, (margin, margin))
    return out.resize((out_px // SS, out_px // SS), Image.LANCZOS)


if __name__ == "__main__":
    res = sys.argv[1]
    bg = f"{res}/drawable/ic_launcher_background.xml"
    fg = f"{res}/drawable/ic_launcher_foreground.xml"
    for name, density in [("mdpi", 160), ("hdpi", 240), ("xhdpi", 320), ("xxhdpi", 480), ("xxxhdpi", 640)]:
        for icon, rnd in [("ic_launcher", False), ("ic_launcher_round", True)]:
            im = legacy_icon(bg, fg, 48, density, rnd)
            dest = f"{res}/mipmap-{name}/{icon}.webp"
            im.save(dest, "WEBP", lossless=True)
            print(dest, im.size)
