"""Generates every texture of "The House Always Wins" (challenge 50).

  python3 make_textures.py KENNEY_BOARDGAME_PACK_DIR OUTPUT_ASSETS_DIR

Block and item textures are hand-placed 16x16 pixel art in Minecraft's own style (light from the
top left, 1-px highlights and dark outlines, a little dithered noise so flat colours do not look
plastic). The croupier is a 64x64 skin in the player layout, base layer only. The GUI textures
(roulette wheel, felt, cards, chips) are higher resolution because the screens show them large;
cards and chips come from Kenney's Boardgame Pack (CC0), the wheel is drawn supersampled.
Deterministic: the same inputs always give the same pixels.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont, ImageFilter

KENNEY, OUT = sys.argv[1], sys.argv[2]
BLOCK = os.path.join(OUT, "textures/block/casino")
ENTITY = os.path.join(OUT, "textures/entity")
ITEM = os.path.join(OUT, "textures/item")
GUI = os.path.join(OUT, "textures/gui/casino")
for d in (BLOCK, ENTITY, ITEM, GUI):
    os.makedirs(d, exist_ok=True)

rnd = random.Random(50)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def noise(img, amount=0.06, seed=0):
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            f = 1.0 + (r.random() - 0.5) * 2 * amount
            px[x, y] = shade(c, f)
    return img


def new(w=16, h=16, c=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), c)


def save(img, *path):
    p = os.path.join(*path)
    img.save(p)
    return p


# ---- palettes --------------------------------------------------------------------------------
RED = [hexc("5a0d14"), hexc("7c1520"), hexc("9c1f2a"), hexc("bf3038"), hexc("e0626a")]
GOLD = [hexc("6e4a12"), hexc("a4761f"), hexc("d4a238"), hexc("f0cd6a"), hexc("fff0b5")]
DARK = [hexc("0d0d12"), hexc("17171e"), hexc("22222b"), hexc("2f2f3a"), hexc("454555")]
CHROME = [hexc("4e5663"), hexc("7a8494"), hexc("a8b3c2"), hexc("d4dde8"), hexc("f4f8fc")]
WOOD = [hexc("2c130a"), hexc("45200f"), hexc("5f2e17"), hexc("7c4022"), hexc("9c5630")]
FELT = [hexc("073b20"), hexc("0b5230"), hexc("116a3f"), hexc("17804c"), hexc("22985c")]
STEEL = [hexc("2e343c"), hexc("444c57"), hexc("5d6875"), hexc("7a8795"), hexc("9fadbb")]
HAZ_Y, HAZ_K = hexc("e8b21f"), hexc("1d1a16")


def vertical(pal, h=16, w=16, top_light=True):
    """A lacquered surface: lighter at the top, darker at the bottom, soft noise."""
    img = new(w, h)
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        idx = 3.2 - 2.4 * t if top_light else 2.0
        lo = int(math.floor(idx))
        c = mix(pal[lo], pal[min(4, lo + 1)], idx - lo)
        for x in range(w):
            px[x, y] = c
    return noise(img, 0.035, seed=h * 7 + w)


def brushed(pal, w=16, h=16, seed=1):
    r = random.Random(seed)
    img = new(w, h)
    px = img.load()
    for y in range(h):
        row = r.random()
        for x in range(w):
            v = 2.0 + 0.9 * math.sin((x * 0.9 + y * 0.35) + row * 6) * 0.5 + (r.random() - 0.5) * 0.5
            v = max(0, min(3.99, v))
            lo = int(v)
            px[x, y] = mix(pal[lo], pal[lo + 1], v - lo)
    for x in range(w):
        px[x, 0] = pal[4]
        px[x, h - 1] = pal[0]
    return img


def bevel(img, light, dark):
    px = img.load()
    w, h = img.size
    for x in range(w):
        px[x, 0] = light
        px[x, h - 1] = dark
    for y in range(h):
        px[0, y] = light
        px[w - 1, y] = dark
    return img


# ---- slot machine ----------------------------------------------------------------------------

def slot_body():
    img = vertical(RED)
    px = img.load()
    # Gold pinstripe near the edges and a glossy highlight streak.
    for y in range(16):
        px[1, y] = GOLD[2]
        px[14, y] = GOLD[1]
    for y in range(2, 14):
        px[4, y] = shade(px[4, y], 1.18)
        px[5, y] = shade(px[5, y], 1.1)
    for x in range(16):
        px[x, 0] = RED[4]
        px[x, 15] = RED[0]
    return img


def slot_gold():
    img = brushed(GOLD, seed=3)
    px = img.load()
    for x in range(0, 16, 4):
        px[x, 8] = GOLD[4]
    return img


def slot_dark():
    img = new(16, 16, DARK[1])
    noise(img, 0.08, seed=5)
    px = img.load()
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px[x, y] = CHROME[3]
        px[x + 1 if x < 8 else x - 1, y] = CHROME[1]
    return img


def slot_panel():
    """Lower front: an inset gold frame, a coin slot and a chip emblem."""
    img = vertical(RED)
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 14, 14], outline=GOLD[2])
    d.rectangle([2, 2, 13, 13], outline=GOLD[0])
    # Coin slot.
    d.rectangle([6, 3, 9, 5], fill=DARK[0], outline=CHROME[2])
    d.point((6, 3), fill=CHROME[4])
    # Chip emblem.
    cx, cy = 7.5, 10
    for y in range(7, 14):
        for x in range(4, 12):
            r = math.hypot(x - cx, y - cy)
            if r < 3.2:
                seg = int((math.atan2(y - cy, x - cx) + math.pi) / (math.pi / 4)) % 2
                c = GOLD[3] if r > 2.2 and seg == 0 else GOLD[1] if r > 2.2 else RED[1]
                img.putpixel((x, y), c)
    img.putpixel((7, 10), GOLD[4])
    return img


def slot_marquee():
    """The lit sign above the reels (32x32 sprite; the model shows the 32x14 band y=9..22):
    a bulb border around three red sevens on a deep red glow."""
    img = new(32, 32, DARK[0])
    px = img.load()
    y0, y1 = 9, 22
    for y in range(32):
        for x in range(32):
            t = abs(y - 15.5) / 7
            px[x, y] = mix(hexc("4a0c16"), DARK[0], min(1, t))
    for x in range(32):
        px[x, y0] = GOLD[3]
        px[x, y1] = GOLD[1]
    for y in range(y0, y1 + 1):
        px[0, y] = GOLD[3]
        px[31, y] = GOLD[1]
    for i in range(2, 31, 3):
        on = (i // 3) % 2 == 0
        px[i, y0 + 1] = hexc("fff6c8") if on else hexc("f0a830")
        px[i, y1 - 1] = hexc("f0a830") if on else hexc("fff6c8")
    seven = ["######", "######", "....##", "...##.", "...##.", "..##..", "..##..", ".##...", ".##..."]
    for n in range(3):
        ox, oy = 4 + n * 9, y0 + 2
        for yy, row in enumerate(seven):
            for xx, ch in enumerate(row):
                if ch == "#":
                    px[ox + xx, oy + yy] = RED[4] if yy < 2 else RED[3] if yy < 5 else RED[2]
        for yy, row in enumerate(seven):
            for xx, ch in enumerate(row):
                if ch != "#":
                    continue
                for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    x2, y2 = ox + xx + dx, oy + yy + dy
                    inside = 0 <= yy + dy < len(seven) and 0 <= xx + dx < 6 and seven[yy + dy][xx + dx] == "#"
                    if not inside and 1 <= x2 < 31 and y0 + 1 < y2 < y1 - 1:
                        px[x2, y2] = GOLD[3]
        px[ox, oy] = hexc("ffd8d8")
    return img


def slot_top():
    img = brushed(GOLD, seed=9)
    px = img.load()
    for i in range(2, 15, 3):
        for (x, y) in [(i, 3), (i, 12)]:
            px[x, y] = hexc("fff6c8")
            px[x, y + 1] = hexc("c07818")
    return img


def slot_knob():
    img = new(16, 16, RED[2])
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 5, y - 5)
            px[x, y] = mix(RED[4], RED[0], min(1, r / 14))
    px[4, 4] = hexc("ffffff")
    px[5, 4] = hexc("ffd0d0")
    return img


def slot_chrome():
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = (x % 8) / 7
            v = 0.5 + 0.5 * math.cos(t * math.pi * 2)
            px[x, y] = mix(CHROME[1], CHROME[4], v)
    return noise(img, 0.03, seed=11)


# ---- roulette table --------------------------------------------------------------------------

def wood(seed=1, pal=WOOD):
    r = random.Random(seed)
    img = new(16, 16)
    px = img.load()
    phase = [r.random() * 6 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            g = math.sin(y * 1.9 + phase[x // 4] + math.sin(x * 0.7) * 0.8)
            v = 2.0 + 0.9 * g + (r.random() - 0.5) * 0.4
            v = max(0, min(3.99, v))
            lo = int(v)
            px[x, y] = mix(pal[lo], pal[lo + 1], v - lo)
    return img


def roulette_top():
    """Felt corners, a mahogany rim and the dark bowl the wheel spins in (32x32)."""
    img = new(32, 32)
    px = img.load()
    r = random.Random(21)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 13.8:
                t = d / 13.8
                px[x, y] = mix(hexc("1a0d08"), hexc("3a1c0f"), t)
            elif d < 15.4:
                v = 2.5 + math.sin(math.atan2(y - 15.5, x - 15.5) * 9) * 0.8
                lo = int(max(0, min(3, v)))
                px[x, y] = mix(WOOD[lo], WOOD[lo + 1], v - lo)
            elif d < 15.9:
                px[x, y] = GOLD[3]
            else:
                f = FELT[2] if r.random() > 0.3 else FELT[1]
                px[x, y] = f
    # Gold print in the corners.
    for (cx, cy) in [(2, 2), (29, 2), (2, 29), (29, 29)]:
        px[cx, cy] = GOLD[3]
    return img


def roulette_side():
    img = wood(seed=4)
    px = img.load()
    for x in range(16):
        px[x, 1] = GOLD[3]
        px[x, 2] = GOLD[1]
    return img


def brass():
    return brushed([hexc("5c3d10"), hexc("8e6420"), hexc("c09234"), hexc("e3bd5e"), hexc("fbe7a4")], seed=17)


def felt_block():
    img = new(16, 16)
    r = random.Random(33)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = FELT[2] if r.random() > 0.35 else FELT[1] if r.random() > 0.2 else FELT[3]
    return img


# ---- crash pad ---------------------------------------------------------------------------------

def crash_top():
    """Steel deck with a hazard-striped border and a scorched launch ring (32x32)."""
    img = new(32, 32)
    px = img.load()
    r = random.Random(41)
    for y in range(32):
        for x in range(32):
            border = x < 3 or y < 3 or x > 28 or y > 28
            if border:
                px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K
            else:
                v = 2 + (r.random() - 0.5) * 0.6
                px[x, y] = mix(STEEL[int(v)], STEEL[int(v) + 1], v - int(v))
                if (x - 3) % 7 == 0 or (y - 3) % 7 == 0:
                    px[x, y] = STEEL[1]
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if 6.5 < d < 8:
                px[x, y] = hexc("ff8a2a") if int(math.atan2(y - 15.5, x - 15.5) * 4) % 2 == 0 else hexc("ffcf5a")
            elif d <= 6.5:
                t = d / 6.5
                px[x, y] = mix(hexc("0c0c0e"), hexc("3a2a20"), t)
    return img


def crash_side():
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K
    for x in range(16):
        px[x, 0] = STEEL[4]
        px[x, 15] = STEEL[0]
    return img


def steel():
    img = new(16, 16)
    r = random.Random(44)
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 2 + (r.random() - 0.5) * 0.7 - y * 0.04
            px[x, y] = mix(STEEL[int(v)], STEEL[int(v) + 1], v - int(v))
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[x, y] = STEEL[4]
        px[x + 1, y + 1] = STEEL[0]
    return img


def crash_box():
    img = steel()
    d = ImageDraw.Draw(img)
    # Gauge.
    d.ellipse([3, 3, 12, 12], fill=hexc("e8e8e0"), outline=STEEL[0])
    d.line([7.5, 7.5, 11, 5], fill=RED[3])
    d.point((7, 7), fill=DARK[0])
    return img


def big_button():
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 6, y - 6)
            px[x, y] = mix(hexc("ff5a4a"), hexc("7a0c0c"), min(1, r / 13))
    px[5, 5] = hexc("ffffff")
    return img


# ---- cashier counter ---------------------------------------------------------------------------

def counter_front():
    img = wood(seed=61)
    d = ImageDraw.Draw(img)
    d.rectangle([1, 2, 14, 14], outline=WOOD[0])
    d.rectangle([2, 3, 13, 13], outline=WOOD[4])
    # A gold plaque with a chip emblem in the middle.
    d.rectangle([4, 6, 11, 10], fill=GOLD[2], outline=GOLD[0])
    d.point((5, 7), fill=GOLD[4])
    for (x, y) in [(7, 8), (8, 8)]:
        d.point((x, y), fill=RED[2])
    d.point((6, 8), fill=GOLD[0])
    d.point((9, 8), fill=GOLD[0])
    return img


def marble():
    img = new(16, 16)
    r = random.Random(71)
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 0.5 + 0.5 * math.sin(x * 0.6 + y * 0.9 + math.sin(y * 0.8) * 2)
            base = mix(hexc("e9e4dc"), hexc("c9c2b6"), v * 0.6 + r.random() * 0.15)
            px[x, y] = base
    for i in range(10):
        px[(i * 3 + 2) % 16, (i * 5) % 16] = hexc("a89f93")
    return img


def counter_side():
    img = wood(seed=67)
    d = ImageDraw.Draw(img)
    d.rectangle([1, 2, 14, 14], outline=WOOD[0])
    return img


# ---- item ---------------------------------------------------------------------------------------

def wallet():
    """A velvet pouch with gold chips spilling out of the top."""
    img = new(16, 16)
    px = img.load()
    shape = [
        "................",
        "......gGGg......",
        "....gGGGGGGg....",
        "...rrRRRRRRrr...",
        "....rRRRRRRr....",
        ".....yYYYYy.....",
        "....rrRRRRrr....",
        "...rRRRRRRRRr...",
        "..rRRRRRRRRRRr..",
        "..rRRRRRRRRRRr..",
        "..rRRRRRRRRRRr..",
        "..rRRRRRRRRRRr..",
        "...rRRRRRRRRr...",
        "....rrRRRRrr....",
        "......rrrr......",
        "................",
    ]
    cols = {"g": GOLD[1], "G": GOLD[3], "r": RED[0], "R": RED[2], "y": GOLD[0], "Y": GOLD[3]}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in cols:
                px[x, y] = cols[ch]
    # Light on the upper left of the pouch, a gold coin emblem.
    for (x, y) in [(4, 8), (4, 9), (5, 7), (5, 8)]:
        px[x, y] = RED[4]
    for (x, y) in [(7, 9), (8, 9), (7, 10), (8, 10)]:
        px[x, y] = GOLD[3]
    px[7, 9] = GOLD[4]
    px[7, 1] = GOLD[4]
    return img


# ---- croupier skin ------------------------------------------------------------------------------

def croupier():
    img = new(64, 64)
    d = ImageDraw.Draw(img)
    px = img.load()
    skin, skin_d, skin_l = hexc("e2b48f"), hexc("c28f6b"), hexc("f0c8a4")
    hair, hair_l = hexc("17110e"), hexc("33261e")
    black, black_l, black_d = hexc("141419"), hexc("2a2a33"), hexc("0a0a0d")
    white, white_d = hexc("f2f0ea"), hexc("c9c5bb")
    vest, vest_l = hexc("8e1b24"), hexc("b73038")

    def rect(x0, y0, w, h, c):
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=c)

    # Head: top, bottom, right, front, left, back.
    rect(8, 0, 8, 8, hair)
    rect(16, 0, 8, 8, skin_d)
    for (fx, name) in [(0, "right"), (8, "front"), (16, "left"), (24, "back")]:
        rect(fx, 8, 8, 8, skin)
        rect(fx, 8, 8, 2, hair)
        rect(fx, 10, 8, 1, hair if name == "back" else skin)
        if name == "back":
            rect(fx, 8, 8, 5, hair)
        if name in ("right", "left"):
            rect(fx + (0 if name == "left" else 6), 10, 2, 3, hair)  # sideburns
            px[fx + (5 if name == "right" else 2), 12] = skin_d  # ear shadow
    # Face.
    for x in range(8, 16):
        px[x, 9] = hair_l if x % 2 else hair
    px[9, 11], px[10, 11] = hexc("2b1a12"), hexc("2b1a12")  # eyebrows
    px[13, 11], px[14, 11] = hexc("2b1a12"), hexc("2b1a12")
    px[9, 12], px[10, 12] = white, hexc("2d5a8c")  # eyes
    px[13, 12], px[14, 12] = hexc("2d5a8c"), white
    px[11, 13], px[12, 13] = skin_d, skin_d  # nose shadow
    for x in range(9, 15):
        px[x, 14] = hair  # thin croupier moustache
    px[9, 14], px[14, 14] = hair_l, hair_l
    px[11, 15], px[12, 15] = skin_d, skin_d
    px[8, 10] = skin_l

    # Body: shirt, red vest, black tuxedo.
    rect(20, 16, 8, 4, black)  # top (shoulders)
    rect(28, 16, 8, 4, black_d)
    rect(16, 20, 4, 12, black)  # right side
    rect(28, 20, 4, 12, black)  # left side
    rect(32, 20, 8, 12, black)  # back
    for y in range(22, 32):
        px[35, y] = black_l
        px[36, y] = black_l
    # Front.
    rect(20, 20, 8, 12, black)
    rect(22, 20, 4, 12, vest)
    rect(23, 20, 2, 10, white)  # shirt placket
    px[23, 23], px[23, 26] = white_d, white_d  # studs
    px[24, 23], px[24, 26] = hexc("d4a238"), hexc("d4a238")
    for y in range(20, 30):
        px[21, y] = black_l  # lapels
        px[26, y] = black_l
    for x in range(22, 26):
        px[x, 30] = vest_l
        px[x, 31] = black_d
    px[22, 21] = vest_l

    # Arms: black sleeves, white cuffs, hands.
    for (ax, ay) in [(40, 16), (32, 48)]:
        rect(ax + 4, ay, 4, 4, black)  # top
        rect(ax + 8, ay, 4, 4, skin)  # bottom = hand
        for side in range(4):
            sx = ax + side * 4
            rect(sx, ay + 4, 4, 12, black)
            rect(sx, ay + 13, 4, 1, white)
            rect(sx, ay + 14, 4, 2, skin)
            px[sx, ay + 5] = black_l
    # Legs: trousers with satin stripe, polished shoes.
    for (lx, ly) in [(0, 16), (16, 48)]:
        rect(lx + 4, ly, 4, 4, black)
        rect(lx + 8, ly, 4, 4, black_d)
        for side in range(4):
            sx = lx + side * 4
            rect(sx, ly + 4, 4, 12, black)
            rect(sx, ly + 14, 4, 2, black_d)
            px[sx + 1, ly + 14] = hexc("5a5a66")  # shoe shine
            if side in (0, 2):
                for y in range(ly + 4, ly + 13):
                    px[sx + (3 if side == 0 else 0), y] = black_l

    # Top hat crown (texOffs 32,0; 7x6x7) and brim (texOffs 0,32; 11x1x11).
    rect(39, 0, 7, 7, black_l)  # top
    rect(46, 0, 7, 7, black_d)
    for i, sx in enumerate([32, 39, 46, 53]):
        rect(sx, 7, 7, 6, black)
        rect(sx, 11, 7, 1, vest)  # red hat band
        px[sx + 1, 8] = black_l
    rect(11, 32, 11, 11, black)
    rect(22, 32, 11, 11, black_d)
    rect(0, 43, 44, 1, black_l)
    # Bow tie (texOffs 44,32; 4x1.6x1) and coat tail (texOffs 0,44; 8x5x1).
    rect(44, 32, 10, 4, hexc("b01e28"))
    px[46, 33] = hexc("ff5a60")
    rect(0, 44, 18, 6, black)
    for x in range(1, 9):
        px[x, 49] = black_l
    return img


# ---- GUI -----------------------------------------------------------------------------------

def felt_tile():
    img = new(64, 64)
    r = random.Random(81)
    px = img.load()
    for y in range(64):
        for x in range(64):
            v = 0.55 + (r.random() - 0.5) * 0.18
            px[x, y] = mix(hexc("0a4a2a"), hexc("147a45"), v)
    img = img.filter(ImageFilter.SMOOTH)
    return img


def cards_atlas():
    ranks = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"]
    suits = ["Spades", "Hearts", "Diamonds", "Clubs"]
    atlas = new(14 * 70, 4 * 95)
    for s, suit in enumerate(suits):
        for i, rank in enumerate(ranks):
            card = Image.open(os.path.join(KENNEY, "PNG/Cards", f"card{suit}{rank}.png")).convert("RGBA")
            atlas.alpha_composite(card.resize((70, 95), Image.LANCZOS), (i * 70, s * 95))
    back = Image.open(os.path.join(KENNEY, "PNG/Cards", "cardBack_red2.png")).convert("RGBA")
    atlas.alpha_composite(back.resize((70, 95), Image.LANCZOS), (13 * 70, 0))
    return atlas


def hue_shift(img, degrees):
    import colorsys
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s < 0.12:
                continue
            h = (h + degrees / 360.0) % 1.0
            r2, g2, b2 = colorsys.hls_to_rgb(h, l, s)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return out


def chips_atlas():
    names = [("chipWhite", 0), ("chipRedWhite", 0), ("chipGreenWhite", 0), ("chipBlackWhite", 0),
             ("chipBlueWhite", 60), ("chipRedWhite", 45), ("chipRedWhite", 20), ("chipWhiteBlue", 0)]
    atlas = new(256, 32)
    for i, (name, shift) in enumerate(names):
        chip = Image.open(os.path.join(KENNEY, "PNG/Chips", name + "_border.png")).convert("RGBA")
        if shift:
            chip = hue_shift(chip, shift)
        atlas.alpha_composite(chip.resize((32, 32), Image.LANCZOS), (i * 32, 0))
    return atlas


def roulette_wheel():
    """A European wheel, supersampled 4x and scaled down to 256x256."""
    S = 1024
    img = new(S, S)
    d = ImageDraw.Draw(img)
    c = S / 2
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 33)
    order = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14,
             31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
    red = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
    # Outer wood rim.
    d.ellipse([0, 0, S - 1, S - 1], fill=hexc("3b1a0c"))
    d.ellipse([18, 18, S - 19, S - 19], fill=hexc("6a3418"))
    d.ellipse([34, 34, S - 35, S - 35], fill=hexc("d4a238"))
    d.ellipse([42, 42, S - 43, S - 43], fill=hexc("2a120a"))
    n = 37
    R_out, R_num, R_in = 470, 408, 345
    for i, num in enumerate(order):
        a0 = math.radians(i * 360 / n - 90 - 180 / n)
        a1 = math.radians((i + 1) * 360 / n - 90 - 180 / n)
        col = hexc("1c8c3e") if num == 0 else hexc("b3141c") if num in red else hexc("111114")
        pts = [(c + R_in * math.cos(a0), c + R_in * math.sin(a0)), (c + R_out * math.cos(a0), c + R_out * math.sin(a0)),
               (c + R_out * math.cos(a1), c + R_out * math.sin(a1)), (c + R_in * math.cos(a1), c + R_in * math.sin(a1))]
        d.polygon(pts, fill=col)
        # Brass fret.
        d.line([pts[0], pts[1]], fill=hexc("e3bd5e"), width=5)
        # Number, rotated to read from the rim.
        txt = str(num)
        tw, th = d.textbbox((0, 0), txt, font=font)[2:]
        tile = new(tw + 8, th + 8)
        ImageDraw.Draw(tile).text((4, 0), txt, font=font, fill=(255, 255, 255, 255))
        mid = (a0 + a1) / 2
        tile = tile.rotate(-math.degrees(mid) - 90, resample=Image.BICUBIC, expand=True)
        px_, py_ = c + R_num * math.cos(mid), c + R_num * math.sin(mid)
        img.alpha_composite(tile, (int(px_ - tile.width / 2), int(py_ - tile.height / 2)))
    # Pocket ring below the numbers.
    for i, num in enumerate(order):
        a0 = math.radians(i * 360 / n - 90 - 180 / n)
        a1 = math.radians((i + 1) * 360 / n - 90 - 180 / n)
        col = hexc("15692f") if num == 0 else hexc("8a0f16") if num in red else hexc("0b0b0d")
        pts = [(c + 285 * math.cos(a0), c + 285 * math.sin(a0)), (c + R_in * math.cos(a0), c + R_in * math.sin(a0)),
               (c + R_in * math.cos(a1), c + R_in * math.sin(a1)), (c + 285 * math.cos(a1), c + 285 * math.sin(a1))]
        d.polygon(pts, fill=col)
        d.line([pts[0], pts[1]], fill=hexc("c9a24a"), width=7)
    d.ellipse([c - R_in, c - R_in, c + R_in, c + R_in], outline=hexc("e3bd5e"), width=6)
    # Wooden cone with a turned finish.
    for r in range(285, 90, -1):
        t = (285 - r) / 195
        col = mix(hexc("5a2a12"), hexc("a8622f"), 0.5 + 0.5 * math.sin(r * 0.12)) if r > 120 else hexc("7a4020")
        col = mix(col, hexc("2a1208"), 0.25 * (1 - t))
        d.ellipse([c - r, c - r, c + r, c + r], fill=col)
    # Four brass spokes and the turret.
    for k in range(4):
        a = math.radians(k * 90 + 45)
        d.line([c, c, c + 240 * math.cos(a), c + 240 * math.sin(a)], fill=hexc("e3bd5e"), width=22)
        d.ellipse([c + 240 * math.cos(a) - 20, c + 240 * math.sin(a) - 20, c + 240 * math.cos(a) + 20,
                   c + 240 * math.sin(a) + 20], fill=hexc("f0cd6a"))
    d.ellipse([c - 70, c - 70, c + 70, c + 70], fill=hexc("d4a238"))
    d.ellipse([c - 44, c - 44, c + 44, c + 44], fill=hexc("f0cd6a"))
    d.ellipse([c - 18, c - 18, c + 18, c + 18], fill=hexc("fff0b5"))
    return img.resize((256, 256), Image.LANCZOS)


def main():
    save(slot_body(), BLOCK, "slot_body.png")
    save(slot_gold(), BLOCK, "slot_gold.png")
    save(slot_dark(), BLOCK, "slot_dark.png")
    save(slot_panel(), BLOCK, "slot_panel.png")
    save(slot_marquee(), BLOCK, "slot_marquee.png")
    save(slot_top(), BLOCK, "slot_top.png")
    save(slot_knob(), BLOCK, "slot_knob.png")
    save(slot_chrome(), BLOCK, "slot_chrome.png")
    save(roulette_top(), BLOCK, "roulette_top.png")
    save(roulette_side(), BLOCK, "roulette_side.png")
    save(wood(seed=7), BLOCK, "mahogany.png")
    save(brass(), BLOCK, "brass.png")
    save(felt_block(), BLOCK, "felt.png")
    save(crash_top(), BLOCK, "crash_top.png")
    save(crash_side(), BLOCK, "crash_side.png")
    save(steel(), BLOCK, "steel.png")
    save(crash_box(), BLOCK, "crash_box.png")
    save(big_button(), BLOCK, "big_button.png")
    save(counter_front(), BLOCK, "counter_front.png")
    save(counter_side(), BLOCK, "counter_side.png")
    save(marble(), BLOCK, "marble.png")
    save(wallet(), ITEM, "chip_wallet.png")
    save(croupier(), ENTITY, "croupier.png")
    save(felt_tile(), GUI, "felt.png")
    save(cards_atlas(), GUI, "cards.png")
    save(chips_atlas(), GUI, "chips.png")
    save(roulette_wheel(), GUI, "roulette_wheel.png")
    print("textures written")


main()
