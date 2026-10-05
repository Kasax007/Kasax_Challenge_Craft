"""Compose a section banner in the style of art/*.png from glyphs cut out of the existing banners.

usage: make_banner.py TEXT OUT [cell] [space] [pink|blue|gold]
"""
import sys
from PIL import Image

ART = 'art'
PINK = {'challenges': 'CHALLENGES', 'how-it-works': 'HOW IT WORKS', 'multiplayer': 'MULTIPLAYER', 'why-play': 'WHY PLAY'}
BLUE = {'features': 'FEATURES', 'perks': 'PERKS', 'screenshots': 'SCREENSHOTS'}
CELL = 28
TOP, BOTTOM = 35, 57  # glyph rows


def clean_background():
    im = Image.open(f'{ART}/features.png').convert('RGB')
    px = im.load(); W, H = im.size
    for x in range(4, W - 4):
        for y in range(H):
            px[x, y] = px[20, y]
    return im


def glyphs(sources):
    out = {}
    for name, text in sources.items():
        im = Image.open(f'{ART}/{name}.png').convert('RGB')
        start = (im.size[0] - len(text) * CELL) // 2  # banners are centred on the cell grid
        for i, ch in enumerate(text):
            if ch != ' ' and ch not in out:
                x0 = start + i * CELL
                out[ch] = (im.crop((x0, 0, x0 + CELL, im.size[1])), im)
    return out


def gold_gradient():
    """The title's gold, top to bottom, stretched over the glyph rows; and its shadow."""
    t = Image.open(f'{ART}/title.png').convert('RGB').load()
    col = [t[80, y] for y in range(58, 84)]
    grad = {}
    for y in range(TOP, BOTTOM + 1):
        f = (y - TOP) / (BOTTOM - TOP)
        grad[y] = col[round(f * (len(col) - 1))]
    return grad, (58, 36, 16)


def recolour(c, B, P, S, G, Sg):
    """Split c into background B, body P and shadow S, then rebuild it with body G and shadow Sg."""
    u = [P[k] - B[k] for k in range(3)]
    v = [S[k] - B[k] for k in range(3)]
    d = [c[k] - B[k] for k in range(3)]
    uu = sum(a * a for a in u); vv = sum(a * a for a in v); uv = sum(a * b for a, b in zip(u, v))
    du = sum(a * b for a, b in zip(d, u)); dv = sum(a * b for a, b in zip(d, v))
    det = uu * vv - uv * uv
    wp = (du * vv - dv * uv) / det
    ws = (dv * uu - du * uv) / det
    wp = min(1, max(0, wp)); ws = min(1 - wp, max(0, ws))
    return tuple(round(B[k] + wp * (G[k] - B[k]) + ws * (Sg[k] - B[k])) for k in range(3))


def compose(text, cell=CELL, space=CELL, colour='pink', out_path=None):
    bg = clean_background(); bp = bg.load()
    g = glyphs(BLUE if colour == 'blue' else PINK)
    if colour == 'gold':
        grad, sg = gold_gradient()
        # the pink body colour per row: the brightest glyph pixel of that row
        body = {}
        for crop, _ in g.values():
            cp = crop.load()
            for y in range(TOP, BOTTOM + 1):
                for x in range(CELL):
                    c = cp[x, y]
                    if y not in body or sum(c) > sum(body[y]):
                        body[y] = c
        shadow = (36, 16, 64)
    widths = [space if ch == ' ' else cell for ch in text]
    x = (bg.size[0] - sum(widths)) // 2
    shift = (CELL - cell) // 2
    for ch, w in zip(text, widths):
        if ch != ' ':
            if ch not in g:
                raise SystemExit(f'no {colour} glyph for {ch!r}')
            src, _ = g[ch]; sp = src.load()
            for gx in range(CELL):
                for gy in range(src.size[1]):
                    c = sp[gx, gy]; b = bp[20, gy]
                    if max(abs(c[k] - b[k]) for k in range(3)) <= 3:
                        continue
                    if colour == 'gold':
                        ry = min(BOTTOM, max(TOP, gy))
                        c = recolour(c, b, body[ry], shadow, grad[ry], sg)
                    tx = x + gx - shift
                    if 4 <= tx < bg.size[0] - 4:
                        bp[tx, gy] = c
        x += w
    bg.save(out_path)
    print(out_path, 'text span', sum(widths), 'margin', (bg.size[0] - sum(widths)) // 2)


if __name__ == '__main__':
    text, out = sys.argv[1], sys.argv[2]
    cell = int(sys.argv[3]) if len(sys.argv) > 3 else CELL
    space = int(sys.argv[4]) if len(sys.argv) > 4 else cell
    colour = sys.argv[5] if len(sys.argv) > 5 else 'pink'
    compose(text, cell, space, colour, out)
