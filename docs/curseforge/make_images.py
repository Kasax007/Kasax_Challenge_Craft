"""Images of the CurseForge pages of Challenge Craft: the mod (description-mod.html) and the modpack
(description-modpack.html). Writes docs/curseforge/img/*.

  python3 docs/curseforge/make_images.py FOOTAGE_DIR FONT_DIR [what ...]

FOOTAGE_DIR: the footage folder of the video archive (shot groups chal/, casino/, ui/, long/: one portrait MP4 per shot,
1080x1920, filmed with the film harness in src/film). FONT_DIR: the archive's fonts folder (Press Start 2P, Inter Display,
Lilita One). The vanilla textures come from the Minecraft client jar ($MC_JAR, default: the Loom cache); the logo, the
menu art and the pack logo of the modpack are in docs/curseforge/src (copied from the modpack's FancyMenu assets).
Frames are cut with ffmpeg into $CC_FRAMES (default: a temporary folder).
what: image names without extension (header glance ...), or nothing for everything. Every number on the images is
counted from this repository (see counts()); the modpack numbers come from $CC_PACK (the modpack zip) when it is set,
otherwise from the list in PACK_MODS below (the manifest of Challenge Craft 26.3 v5)."""
import io
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import zipfile

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HERE, 'img')
SRC = os.path.join(HERE, 'src')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'kasax', 'challengecraft')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'challengecraft')
FOOT = sys.argv[1] if len(sys.argv) > 1 else ''
FONTS = sys.argv[2] if len(sys.argv) > 2 else ''
CACHE = os.environ.get('CC_FRAMES') or os.path.join(tempfile.gettempdir(), 'cc_frames')
MC_JAR = os.environ.get('MC_JAR') or os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')
W = 1200
MAX_BYTES = 950 * 1024

# The look: the night purple of the pack menu, the magenta of the logo, cyan and gold like an arcade cabinet,
# light bulbs around the panels like a game-show stage.
NIGHT, DEEP, PLUM, VIOLET = (14, 8, 32), (24, 12, 52), (44, 20, 88), (96, 44, 178)
MAGENTA, PINK, ROSE = (226, 64, 214), (255, 178, 246), (255, 92, 168)
CYAN, ICE, GOLD, AMBER = (60, 220, 255), (190, 246, 255), (255, 200, 64), (255, 150, 30)
LIME, RED, WHITE, INK = (130, 240, 90), (255, 72, 96), (255, 255, 255), (20, 8, 40)
MUTED = (196, 182, 232)


# ----------------------------------------------------------------------------------------------- fonts
def pixel(size):
    return ImageFont.truetype(os.path.join(FONTS, 'PressStart2P-Regular.ttf'), size)


def bold(size):
    return ImageFont.truetype(os.path.join(FONTS, 'InterDisplay-Bold.otf'), size)


def black(size):
    return ImageFont.truetype(os.path.join(FONTS, 'InterDisplay-Black.otf'), size)


def semi(size):
    return ImageFont.truetype(os.path.join(FONTS, 'InterDisplay-SemiBold.otf'), size)


# ----------------------------------------------------------------------------------------------- the numbers
def read(*p):
    return open(os.path.join(*p), encoding='utf-8').read()


def counts():
    """Every number on the images, counted from the code and data of this repository."""
    lang = json.loads(read(ASSETS, 'lang', 'en_us.json'))
    names = {int(m.group(1)): v for k, v in lang.items() for m in [re.fullmatch(r'challengecraft\.worldcreate\.challenge(\d+)', k)] if m}
    classes = sorted(int(m.group(1)) for f in os.listdir(os.path.join(JAVA, 'challenges'))
                     for m in [re.match(r'Chal_(\d+)_\w+\.java$', f)] if m)
    assert sorted(names) == classes, (sorted(names), classes)
    lm = read(JAVA, 'LevelManager.java')
    max_level = int(re.search(r'MAX_LEVEL = (\d+);', lm).group(1))
    req = {}
    body = re.search(r'int getRequiredLevel\(int id\) \{(.*?)\n    \}', lm, re.S).group(1)
    for ids, lvl in re.findall(r'case ([\d, ]+) -> (\d+);', body):
        for i in ids.split(','):
            req[int(i)] = int(lvl)
    perk_ids = dict(re.findall(r'PERK_(\w+) = (\d+);', lm))
    perk_level = {}
    for name, lvl in re.findall(r'case PERK_(\w+) -> (\d+);', body):
        if int(lvl) <= max_level:
            perk_level[int(perk_ids[name])] = int(lvl)
    perks = [(perk_level[p], lang['challengecraft.perk.%d' % p], p) for p in sorted(perk_level, key=lambda p: perk_level[p])]
    stars = re.findall(r'case (\d+) -> "(\w+)";', re.search(r'getStarReward\(int stars\) \{(.*?)\}', lm, re.S).group(1))
    star_xp = int(re.search(r'if \(level >= MAX_LEVEL\) return (\d+)L;', lm).group(1))
    cm = read(JAVA, 'ChallengeManager.java')
    diff = {}
    dbody = re.search(r'return switch \(id\) \{(.*?)default', cm, re.S).group(1)
    for i, expr in re.findall(r'case (\d+) -> ([^;]+);', dbody):
        diff[int(i)] = expr.strip()
    conflicts = len(re.findall(r'new int\[\]\{', re.search(r'CONFLICTS = List\.of\((.*?)\n    \);', cm, re.S).group(1)))
    icons = dict((int(i), it) for i, it in re.findall(r'ICONS\.put\((\d+), ([^;]+)\);', read(JAVA, 'client', 'screen', 'ChallengeIconProvider.java')))
    cat_src = read(JAVA, 'client', 'screen', 'ChallengeBrowser.java')
    cats = {}
    for ids, c in re.findall(r'case ([\d, ]+) -> Category\.(\w+);', cat_src):
        for i in ids.split(','):
            cats[int(i)] = c
    # Lockout Bingo: goals that can be drawn on a normal board (implemented and not switched off)
    pool = read(JAVA, 'challenges', 'lockout', 'LockoutBingoGoalPool.java')
    start = pool.index('GOALS = List.of(') + len('GOALS = List.of(')
    depth, entries, cur, i = 1, [], '', start
    while depth > 0:
        ch = pool[i]
        i += 1
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if depth == 1 and ch == ',':
            entries.append(cur)
            cur = ''
            continue
        if depth > 0:
            cur += ch
    entries.append(cur)
    entries = [re.sub(r'//[^\n]*', '', e).strip() for e in entries]
    entries = [e for e in entries if e]
    goals = sum(1 for e in entries if not re.search(r',\s*false\s*\)$', e))
    teams = len(re.findall(r'^\s+[A-Z]+\("\w+", 0x', read(JAVA, 'challenges', 'lockout', 'LockoutBingoTeam.java'), re.M))
    trivia = len(re.findall(r'^\s+add\("', read(JAVA, 'challenges', 'TriviaQuestions.java'), re.M))
    daily = len(re.findall(r'DailyEntry\.of\(', read(JAVA, 'daily', 'DailyChallenges.java')))
    dt = read(JAVA, 'casino', 'DeviceType.java')
    games = len(re.findall(r'^\s+[A-Z]+\("\w+", true,', dt, re.M))
    rtp = int(re.search(r'PAYOUT_PERCENT = (\d+);', read(JAVA, 'casino', 'SlotMath.java')).group(1))
    fee_min = int(re.search(r'FEE_PERIOD_TICKS = 20 \* 60 \* (\d+);', read(JAVA, 'casino', 'CasinoEconomy.java')).group(1))
    slot_max = int(re.search(r'MAX_WIN_UNITS = (\d+)L \* LINES', read(JAVA, 'casino', 'SlotMath.java')).group(1))
    death_tax = int(re.search(r'keeps (\d+) % of the chips on every death', read(JAVA, 'casino', 'BlackjackRevival.java')).group(1))
    bob = [lang['challengecraft.lockout.bot.difficulty.' + d] for d in ('easy', 'normal', 'hard')]
    keys = {k.split('.')[-1]: v for k, v in lang.items() if k.startswith('key.challengecraft.')}
    return dict(n=len(names), names=names, max_level=max_level, req=req, perks=perks, stars=stars, star_xp=star_xp,
                diff=diff, conflicts=conflicts, icons=icons, cats=cats, goals=goals, teams=teams, trivia=trivia,
                daily=daily, games=games, rtp=rtp, fee_min=fee_min, slot_max=slot_max, death_tax=death_tax, bob=bob,
                keys=keys, lang=lang)


# The modpack (manifest of "Challenge Craft 26.3 v5", CurseForge project 1653698). Groups for the pictures.
PACK_MODS = {
    'challenge': ['Challenge Craft'],
    'looks': ['Iris Shaders', 'Complementary Reimagined', 'BSL Shaders', 'Bliss Shaders', 'Entity Model Features',
              'Entity Texture Features', '3D Skin Layers', 'Not Enough Animations'],
    'sound': ['Simple Voice Chat', 'AmbientSounds', 'Sound Physics Remastered'],
    'comfort': ['AppleSkin', 'Mouse Tweaks', 'Just Zoom', 'Upgraded Iron Chests', 'Storage Drawers'],
    'speed': ['Sodium', 'Lithium', 'FerriteCore', 'Entity Culling', 'Packet Fixer', 'spark'],
    'menu': ['FancyMenu', 'Drippy Loading Screen', 'Melody', 'BisectHosting Server Menu'],
    'library': ['Fabric API', 'CreativeCore', 'Konkrete', 'Prickle', 'AttributeFix'],
}
SHADERS = ['Complementary Reimagined', 'BSL Shaders', 'Bliss Shaders']


def pack_counts():
    entries = [m for g in PACK_MODS.values() for m in g]
    zpath = os.environ.get('CC_PACK')
    if zpath:
        z = zipfile.ZipFile(zpath)
        files = json.loads(z.read('manifest.json'))['files']
        assert len(files) == len(entries), (len(files), len(entries))
    return dict(entries=len(entries), shaders=len(SHADERS), mods=len(entries) - len(SHADERS))


# ----------------------------------------------------------------------------------------------- sources
def frame(spec):
    """'group/shot@t': the picture at t seconds of FOOTAGE_DIR/group/shot.mp4 (1080x1920)."""
    os.makedirs(CACHE, exist_ok=True)
    clip, t = spec.split('@')
    out = os.path.join(CACHE, clip.replace('/', '_') + '_' + t + '.png')
    if not os.path.exists(out):
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-threads', '1', '-ss', t, '-i', os.path.join(FOOT, clip + '.mp4'),
                        '-frames:v', '1', out], check=True)
    return Image.open(out).convert('RGB')


def cover(im, w, h, fx=0.5, fy=0.5, zoom=1.0):
    """Crops im to the ratio w:h around the focus (fx, fy) and scales it to w x h."""
    iw, ih = im.size
    s = max(w / iw, h / ih) * zoom
    cw, ch = min(iw, w / s), min(ih, h / s)
    x0 = max(0, min(fx * iw - cw / 2, iw - cw))
    y0 = max(0, min(fy * ih - ch / 2, ih - ch))
    return im.resize((w, h), Image.LANCZOS, box=(x0, y0, x0 + cw, y0 + ch))


_JAR = None


def tex(path):
    """A texture of the Minecraft client jar ('item/apple') or of this mod ('cc:item/dice'), first animation frame."""
    global _JAR
    if path.startswith('cc:'):
        im = Image.open(os.path.join(ASSETS, 'textures', path[3:] + '.png'))
    else:
        if _JAR is None:
            _JAR = zipfile.ZipFile(MC_JAR)
        im = Image.open(io.BytesIO(_JAR.read('assets/minecraft/textures/' + path + '.png')))
    im = im.convert('RGBA')
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    return im


def cube(top, side, size, front=None):
    """An isometric block from its textures (like an inventory icon): top lit, left side mid, right side dark."""
    s = 64
    top, side = top.resize((s, s), Image.NEAREST), side.resize((s, s), Image.NEAREST)
    front = front.resize((s, s), Image.NEAREST) if front else side
    h = s / 2
    out = Image.new('RGBA', (2 * s, 2 * s), (0, 0, 0, 0))

    def face(img, coeffs, shade):
        img = ImageEnhance.Brightness(img).enhance(shade)
        return img.transform((2 * s, 2 * s), Image.AFFINE, coeffs, Image.NEAREST)

    # inverse affine maps: output (x, y) -> texture (u, v)
    # top: (0,h) <- (0,0), (s,0) <- (s,0)... a rhombus with corners (s,0) (2s,h) (s,2h) (0,h)
    top_f = face(top, _inv(((0, 0), (s, 0), (0, s)), ((s, 0), (2 * s, h), (0, h))), 1.0)
    left_f = face(front, _inv(((0, 0), (s, 0), (0, s)), ((0, h), (s, 2 * h), (0, h + s))), 0.8)
    right_f = face(side, _inv(((0, 0), (s, 0), (0, s)), ((s, 2 * h), (2 * s, h), (s, 2 * h + s))), 0.62)
    for f in (left_f, right_f, top_f):
        out.alpha_composite(f)
    out = out.crop((0, 0, 2 * s, 2 * h + s))
    return fit_icon(out, size)


def _inv(src, dst):
    """Affine coefficients (for Image.transform) that map dst triangle points back onto src points."""
    (x0, y0), (x1, y1), (x2, y2) = dst
    (u0, v0), (u1, v1), (u2, v2) = src
    det = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    a = ((u1 - u0) * (y2 - y0) - (u2 - u0) * (y1 - y0)) / det
    b = ((u2 - u0) * (x1 - x0) - (u1 - u0) * (x2 - x0)) / det
    d = ((v1 - v0) * (y2 - y0) - (v2 - v0) * (y1 - y0)) / det
    e = ((v2 - v0) * (x1 - x0) - (v1 - v0) * (x2 - x0)) / det
    return (a, b, u0 - a * x0 - b * y0, d, e, v0 - d * x0 - e * y0)


def fit_icon(im, size):
    w, h = im.size
    s = size / max(w, h)
    return im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.NEAREST if s >= 1 else Image.LANCZOS)


def chest_icon(size):
    t = tex('entity/chest/normal')
    k = t.width // 64
    top = t.crop((14 * k, 0, 28 * k, 14 * k))
    front = Image.new('RGBA', (14 * k, 14 * k))
    front.paste(t.crop((42 * k, 14 * k, 56 * k, 19 * k)), (0, 0))
    front.paste(t.crop((42 * k, 33 * k, 56 * k, 43 * k)), (0, 5 * k))
    front.paste(t.crop((1 * k, 0, 3 * k, 4 * k)).resize((2 * k, 4 * k)), (6 * k, 3 * k))
    side = Image.new('RGBA', (14 * k, 14 * k))
    side.paste(t.crop((28 * k, 14 * k, 42 * k, 19 * k)), (0, 0))
    side.paste(t.crop((28 * k, 33 * k, 42 * k, 43 * k)), (0, 5 * k))
    return cube(top, side, size, front=front)


ITEM_TEX = {'CLOCK': 'item/clock_00', 'COMPASS': 'item/compass_16', 'FILLED_MAP': 'item/map', 'COBWEB': 'block/cobweb',
            'LADDER': 'block/ladder', 'GOLDEN_SWORD': 'item/golden_sword'}
BLOCKS = {'GRASS_BLOCK': ('block/grass_block_top', 'block/grass_block_side'), 'BEDROCK': ('block/bedrock',) * 2,
          'COBBLESTONE': ('block/cobblestone',) * 2, 'CRAFTING_TABLE': ('block/crafting_table_top', 'block/crafting_table_side', 'block/crafting_table_front'),
          'SPAWNER': ('block/spawner',) * 2, 'HONEY_BLOCK': ('block/honey_block_top', 'block/honey_block_side'),
          'STONE': ('block/stone',) * 2, 'ANVIL': None, 'RED_CONCRETE': ('block/red_concrete',) * 2}


def item_icon(expr, size):
    """The icon of a challenge or perk as ChallengeIconProvider names it (Items.X, ModItems.Y, cushion, concrete)."""
    if 'ModItems.DICE' in expr:
        return fit_icon(tex('cc:item/dice'), size)
    if 'CUSHION' in expr:
        return fit_icon(tex('item/white_cushion'), size)
    if 'CONCRETE' in expr:
        name = 'RED_CONCRETE'
    else:
        name = re.search(r'Items\.(\w+)', expr).group(1)
    if name == 'CHEST':
        return chest_icon(size)
    if name == 'ANVIL':
        a = tex('block/anvil')
        return cube(tex('block/anvil_top'), a, size)
    if name == 'SHIELD':
        return fit_icon(shield_icon(), size)
    if name in BLOCKS:
        t = BLOCKS[name]
        if name == 'GRASS_BLOCK':
            top = tint(tex('block/grass_block_top'), (124, 189, 107))
            side = tex('block/grass_block_side')
            over = tint(tex('block/grass_block_side_overlay'), (124, 189, 107))
            side.alpha_composite(over)
            return cube(top, side, size)
        return cube(tex(t[0]), tex(t[1]), size, front=tex(t[2]) if len(t) > 2 else None)
    return fit_icon(tex(ITEM_TEX.get(name, 'item/' + name.lower())), size)


def shield_icon():
    t = tex('entity/shield/shield_base_nopattern') if _has('entity/shield/shield_base_nopattern') else tex('entity/shield_base_nopattern')
    k = t.width // 64
    return t.crop((1 * k, 1 * k, 13 * k, 23 * k))


def _has(path):
    global _JAR
    if _JAR is None:
        _JAR = zipfile.ZipFile(MC_JAR)
    return ('assets/minecraft/textures/' + path + '.png') in _JAR.namelist()


def tint(im, rgb):
    r, g, b, a = im.split()
    grey = Image.merge('RGB', (r, g, b)).convert('L')
    out = Image.merge('RGB', [grey.point(lambda v, c=c: v * c // 255) for c in rgb])
    out.putalpha(a)
    return out


# ----------------------------------------------------------------------------------------------- drawing
def rounded(size, r):
    m = Image.new('L', size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def paste_round(base, im, xy, r, border=None, bw=4, shadow=True):
    w, h = im.size
    if shadow:
        sh = Image.new('RGBA', (w + 40, h + 40), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle((20, 26, 20 + w, 26 + h), r, fill=(0, 0, 0, 150))
        base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(9)), (xy[0] - 20, xy[1] - 20))
    m = rounded((w, h), r)
    base.paste(im.convert('RGB'), xy, m)
    if border:
        ImageDraw.Draw(base).rounded_rectangle((xy[0], xy[1], xy[0] + w - 1, xy[1] + h - 1), r, outline=border, width=bw)


def vgrad(size, top, bot):
    w, h = size
    g = Image.new('RGB', (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        g.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return g.resize((w, h))


def stage(size, top=DEEP, bot=NIGHT, rays=True, seed=3):
    """The background of every panel: night purple, a soft spotlight and faint diagonal stripes."""
    w, h = size
    im = vgrad(size, top, bot).convert('RGBA')
    over = Image.new('RGBA', size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for x in range(-h, w, 34):
        d.line([(x, h), (x + h, 0)], fill=(255, 255, 255, 9), width=12)
    if rays:
        glow = Image.new('RGBA', size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((w * 0.15, -h * 0.6, w * 0.85, h * 0.55), fill=VIOLET + (90,))
        im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(min(w, h) // 5 + 1)))
    # little pixel stars like on the menu
    import random
    rnd = random.Random(seed)
    for _ in range(w * h // 9000):
        x, y = rnd.randrange(w), rnd.randrange(h)
        s = rnd.choice((2, 2, 3, 4))
        d.rectangle((x, y, x + s, y + s), fill=(255, 255, 255, rnd.randrange(40, 130)))
    im.alpha_composite(over)
    return im


def bulbs(im, box, step=26, r=5, color=GOLD, phase=0, sides=True):
    """Marquee light bulbs along the edge of box (x0, y0, x1, y1), every other one a bit dimmer."""
    x0, y0, x1, y1 = box
    glow = Image.new('RGBA', im.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    pts = []
    edges = (((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0)))
    if not sides:
        edges = (((x0, y0), (x1, y0)), ((x0, y1), (x1, y1)))
    for edge in edges:
        (ax, ay), (bx, by) = edge
        n = max(1, round(math.hypot(bx - ax, by - ay) / step))
        for i in range(n + (0 if sides else 1)):
            pts.append((ax + (bx - ax) * i / n, ay + (by - ay) * i / n))
    for i, (x, y) in enumerate(pts):
        gd.ellipse((x - r * 2.6, y - r * 2.6, x + r * 2.6, y + r * 2.6), fill=color + (70 if (i + phase) % 2 else 30,))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(r * 1.4)))
    d = ImageDraw.Draw(im)
    for i, (x, y) in enumerate(pts):
        on = (i + phase) % 2
        c = color if on else tuple(int(v * 0.7) for v in color)
        d.ellipse((x - r, y - r, x + r, y + r), fill=c, outline=(90, 50, 10), width=1)
        if on:
            d.ellipse((x - r * 0.45, y - r * 0.6, x, y - r * 0.1), fill=(255, 250, 220))


def blocky(text, size, top=PINK, bot=MAGENTA, side=(70, 22, 120), depth=None, outline=INK, ow=None, tracking=0):
    """Pixel letters with a 3D side like the Challenge Craft logo: a gradient face, an extruded side, a dark outline."""
    f = pixel(size)
    depth = depth if depth is not None else max(3, size // 6)
    ow = ow if ow is not None else max(2, size // 9)
    if tracking:
        widths = [f.getlength(c) + tracking for c in text]
        tw = int(sum(widths) - tracking)
    else:
        tw = int(f.getlength(text))
    th = size
    pad = ow + 2
    W_, H_ = tw + 2 * pad + depth, th + 2 * pad + depth + 4
    mask = Image.new('L', (W_, H_), 0)
    md = ImageDraw.Draw(mask)
    if tracking:
        x = pad
        for c, cw in zip(text, widths):
            md.text((x, pad), c, font=f, fill=255)
            x += cw
    else:
        md.text((pad, pad), text, font=f, fill=255)
    out = Image.new('RGBA', (W_, H_), (0, 0, 0, 0))
    # outline of the whole extruded shape
    ext = Image.new('L', (W_, H_), 0)
    for dd in range(depth + 1):
        ext = ImageChops.lighter(ext, ImageChops.offset(mask, dd // 3, dd))
    ring = ext.filter(ImageFilter.MaxFilter(2 * ow + 1))
    out.paste(outline + (255,), (0, 0), ring)
    out.paste(side + (255,), (0, 0), ext)
    face = vgrad((W_, H_), top, bot).convert('RGBA')
    # the gradient runs over the letter height only
    face = vgrad((W_, th), top, bot).resize((W_, th))
    full = Image.new('RGB', (W_, H_), bot)
    full.paste(vgrad((W_, pad), top, top), (0, 0))
    full.paste(face, (0, pad))
    out.paste(full, (0, 0), mask)
    # a light line on top of each letter like the logo's bevel
    hi = ImageChops.subtract(mask, ImageChops.offset(mask, 0, max(2, size // 12)))
    out.paste((255, 240, 255), (0, 0), hi.point(lambda v: v * 0.55))
    return out


def text_w(d, s, f):
    return d.textlength(s, font=f)


def wrap(d, s, f, width):
    words, lines, cur = s.split(), [], ''
    for w_ in words:
        t = (cur + ' ' + w_).strip()
        if d.textlength(t, font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w_
    if cur:
        lines.append(cur)
    return lines


def draw_lines(d, xy, lines, f, fill, lh, anchor='la'):
    x, y = xy
    for ln in lines:
        d.text((x, y), ln, font=f, fill=fill, anchor=anchor)
        y += lh
    return y


def pill(d, box, fill, text=None, f=None, tfill=WHITE, outline=None, ow=3):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, (y1 - y0) // 2, fill=fill, outline=outline, width=ow)
    if text:
        d.text(((x0 + x1) / 2, (y0 + y1) / 2 + 1), text, font=f, fill=tfill, anchor='mm')


def save(im, name, quality=90):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    if name.endswith('.png'):
        im.save(path, optimize=True)
        if os.path.getsize(path) > MAX_BYTES:
            im.convert('RGB').quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).save(path, optimize=True)
    else:
        q = quality
        while True:
            im.convert('RGB').save(path, quality=q, optimize=True, progressive=True, subsampling=0 if q >= 90 else 2)
            if os.path.getsize(path) <= MAX_BYTES or q <= 60:
                break
            q -= 4
    assert os.path.getsize(path) <= MAX_BYTES, (name, os.path.getsize(path))
    print('%-24s %4dx%-4d %4d KB' % (name, im.width, im.height, os.path.getsize(path) // 1024))


# ----------------------------------------------------------------------------------------------- pieces
def logo(width):
    lg = Image.open(os.path.join(SRC, 'wordmark.png')).convert('RGBA')
    return lg.resize((width, round(lg.height * width / lg.width)), Image.LANCZOS)


def glow_behind(im, layer, xy, color, radius=18, alpha=150):
    a = layer.split()[3].point(lambda v: v * alpha // 255)
    g = Image.new('RGBA', layer.size, color + (0,))
    g.putalpha(a)
    pad = radius * 3
    big = Image.new('RGBA', (layer.width + 2 * pad, layer.height + 2 * pad), (0, 0, 0, 0))
    big.alpha_composite(g, (pad, pad))
    im.alpha_composite(big.filter(ImageFilter.GaussianBlur(radius)), (xy[0] - pad, xy[1] - pad))


def game_card(shot, w, h, fx=0.5, fy=0.5, zoom=1.0, border=GOLD, r=18, bw=5):
    """A footage frame as a rounded card with a bright rim."""
    im = cover(frame(shot), w, h, fx, fy, zoom).convert('RGBA')
    card = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    card.paste(im, (0, 0), rounded((w, h), r))
    ImageDraw.Draw(card).rounded_rectangle((0, 0, w - 1, h - 1), r, outline=border, width=bw)
    return card


def drop_shadow(base, im, xy, off=(0, 10), blur=12, alpha=170):
    sh = Image.new('RGBA', (im.width + 60, im.height + 60), (0, 0, 0, 0))
    a = im.split()[3].point(lambda v: v * alpha // 255)
    blk = Image.new('RGBA', im.size, (0, 0, 0, 255))
    blk.putalpha(a)
    sh.alpha_composite(blk, (30, 30))
    base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)), (xy[0] - 30 + off[0], xy[1] - 30 + off[1]))
    base.alpha_composite(im, xy)


def tilt(im, angle):
    return im.rotate(angle, Image.BICUBIC, expand=True)


def icon_tile(icon, size, fill=PLUM, rim=VIOLET):
    t = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(t)
    d.rounded_rectangle((0, 0, size - 1, size - 1), size // 5, fill=fill, outline=rim, width=3)
    d.rounded_rectangle((4, 4, size - 5, size // 2), size // 6, fill=(255, 255, 255, 18))
    ic = icon if icon.width <= size * 0.72 else fit_icon(icon, int(size * 0.72))
    t.alpha_composite(ic, ((size - ic.width) // 2, (size - ic.height) // 2))
    return t


def heading(im, text, y, size=34, top=PINK, bot=MAGENTA, side=(70, 22, 120)):
    t = blocky(text, size, top, bot, side)
    x = (im.width - t.width) // 2
    glow_behind(im, t, (x, y), MAGENTA, 16, 90)
    im.alpha_composite(t, (x, y))
    return y + t.height


# ----------------------------------------------------------------------------------------------- images
STRIPS = {  # name: (title, icon, accent)
    'how': ('HOW IT WORKS', 'item/experience_bottle', CYAN),
    'challenges': ('THE CHALLENGES', 'item/filled_map', MAGENTA),
    'runs': ('DAILY, CODES AND MORE', 'item/clock_00', GOLD),
    'casino': ('THE HOUSE ALWAYS WINS', 'cc:item/chip_wallet', RED),
    'together': ('PLAY TOGETHER', 'item/cake', LIME),
    'bob': ('LOCKOUT VS BOB', 'item/iron_pickaxe', CYAN),
    'levels': ('LEVELS AND PERKS', 'item/nether_star', GOLD),
    'menu': ('A MENU OF ITS OWN', 'item/painting', MAGENTA),
    'friends': ('START A RUN WITH FRIENDS', 'item/goat_horn', LIME),
}


def strip(key):
    title, icon, accent = STRIPS[key]
    w, h = W, 104
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    bg = stage((w - 8, h - 12), PLUM, DEEP, rays=False, seed=len(title))
    paste_round(im, bg, (4, 4), 22, border=None, shadow=False)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((4, 4, w - 5, h - 9), 22, outline=accent, width=4)
    bulbs(im, (124, 13, w - 186, h - 18), step=28, r=3, color=GOLD, sides=False)
    if icon.startswith('cc:'):
        ic = tex(icon)
    elif icon == 'item/cake':
        ic = cube(tex('block/cake_top'), tex('block/cake_side'), 48)
    else:
        ic = tex(icon)
    tile = icon_tile(fit_icon(ic, 46), 66, fill=DEEP, rim=accent)
    im.alpha_composite(tile, (40, (h - 4 - 66) // 2 - 2))
    t = blocky(title, 30, top=WHITE, bot=PINK if accent != GOLD else (255, 230, 150), side=(70, 22, 120))
    glow_behind(im, t, (128, (h - 4 - t.height) // 2), accent, 12, 110)
    im.alpha_composite(t, (128, (h - 4 - t.height) // 2))
    # three arcade buttons on the right
    for i, c in enumerate((RED, GOLD, CYAN)):
        x = w - 150 + i * 36
        y = (h - 4) // 2 - 2
        d.ellipse((x - 13, y - 11, x + 13, y + 15), fill=tuple(v // 3 for v in c))
        d.ellipse((x - 13, y - 14, x + 13, y + 12), fill=c, outline=INK, width=2)
        d.ellipse((x - 7, y - 10, x, y - 4), fill=(255, 255, 255, 170))
    save(im, 'strip_%s.png' % key)


def night_bg(size, src='menu_night.jpg', fy=0.6, dark=0.75):
    bg = cover(Image.open(os.path.join(SRC, src)).convert('RGB'), size[0], size[1], 0.5, fy)
    bg = ImageEnhance.Brightness(bg).enhance(dark).convert('RGBA')
    st = stage(size, DEEP, NIGHT, rays=True)
    st.putalpha(110)
    bg.alpha_composite(st)
    return bg


def fan(im, shots, x0, y0, cw, ch, angles, step, rims):
    for i, (shot, ang, rim) in enumerate(zip(shots, angles, rims)):
        spec, fx, fy, zoom = shot
        card = tilt(game_card(spec, cw, ch, fx, fy, zoom, border=rim, r=20, bw=6), ang)
        x = x0 + i * step - (card.width - cw) // 2
        y = y0 + (abs(i - (len(shots) - 1) / 2) * 16) - (card.height - ch) // 2
        drop_shadow(im, card, (int(x), int(y)), off=(6, 14), blur=14)


def header(c, pack=False):
    w, h = 1600, 500
    im = night_bg((w, h), 'menu_dusk.jpg' if pack else 'menu_night.jpg')
    d = ImageDraw.Draw(im)
    # spotlight cones behind the cards
    cone = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cone)
    for cx, col in ((1060, MAGENTA), (1300, CYAN), (1520, GOLD)):
        cd.polygon([(cx - 30, -10), (cx + 30, -10), (cx + 190, h), (cx - 190, h)], fill=col + (38,))
    im.alpha_composite(cone.filter(ImageFilter.GaussianBlur(30)))
    if pack:
        shots = [('long/chunk_walk_v2@4.4', 0.5, 0.55, 1.0), ('casino/casino_slot_legendary@9.6', 0.5, 0.45, 1.05),
                 ('long/nametags_close@3.4', 0.55, 0.5, 1.25), ('long/dragon@4.4', 0.45, 0.6, 1.0)]
    else:
        shots = [('chal/void_trail_walk@5.1', 0.55, 0.42, 1.45), ('casino/casino_slot_legendary@9.6', 0.5, 0.45, 1.05),
                 ('long/chunk_walk_v2@4.4', 0.5, 0.55, 1.0), ('long/red_light_v2@8.2', 0.62, 0.5, 1.35)]
    fan(im, shots, 892, 66, 196, 348, (9, 3, -3, -9), 150, (MAGENTA, GOLD, CYAN, LIME))
    lg = logo(820)
    glow_behind(im, lg, (52, 52), MAGENTA, 24, 120)
    im.alpha_composite(lg, (52, 52))
    y = 52 + lg.height + 22
    sub = 'THE MODPACK: EVERYTHING SET UP' if pack else 'TURN EVERY WORLD INTO A CHALLENGE RUN'
    t = blocky(sub, 22, top=WHITE, bot=ICE, side=(20, 60, 110), depth=3, ow=2)
    glow_behind(im, t, (62, y), CYAN, 10, 120)
    im.alpha_composite(t, (62, y))
    y += t.height + 26
    f = pixel(17)
    x = 66
    pills = [('MINECRAFT 26.3', CYAN), ('FABRIC', CYAN)]
    pills += [('%d MODS + %d SHADERS' % (pack_counts()['mods'], pack_counts()['shaders']), GOLD)] if pack else [('%d CHALLENGES' % c['n'], GOLD)]
    for text, col in pills:
        tw = d.textlength(text, font=f)
        pill(d, (x, y, x + tw + 40, y + 44), DEEP, text, f, tfill=col, outline=col, ow=3)
        x += tw + 56
    bulbs(im, (16, 16, w - 17, h - 17), step=32, r=4, color=GOLD)
    save(im, 'header_pack.png' if pack else 'header.png')


def panel_bg(w, h, seed=1):
    """A whole image: transparent corners, night stage, magenta rim and marquee bulbs."""
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    paste_round(im, stage((w - 8, h - 8), DEEP, NIGHT, seed=seed), (4, 4), 30, shadow=False)
    ImageDraw.Draw(im).rounded_rectangle((4, 4, w - 5, h - 5), 30, outline=MAGENTA, width=4)
    return im


def stat_tile(im, box, icon, number, label, line, accent):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(im)
    tile = Image.new('RGBA', (x1 - x0, y1 - y0), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    td.rounded_rectangle((0, 0, x1 - x0 - 1, y1 - y0 - 1), 22, fill=PLUM + (235,), outline=accent, width=4)
    im.alpha_composite(tile, (x0, y0))
    it = icon_tile(fit_icon(icon, 64), 92, fill=DEEP, rim=accent)
    im.alpha_composite(it, (x0 + 22, y0 + 26))
    n = blocky(str(number), 46, top=WHITE, bot=accent, side=tuple(v // 3 for v in accent), depth=6)
    glow_behind(im, n, (x0 + 134, y0 + 18), accent, 12, 110)
    im.alpha_composite(n, (x0 + 134, y0 + 18))
    ls = 16
    while d.textlength(label, font=pixel(ls)) > x1 - x0 - 152:
        ls -= 1
    d.text((x0 + 138, y0 + 18 + n.height + 6), label, font=pixel(ls), fill=accent)
    lines = wrap(d, line, semi(19), x1 - x0 - 44)
    draw_lines(d, (x0 + 24, y0 + 136), lines, semi(19), MUTED, 24)


def infinite_chest(size):
    b = 'cc:block/infinite_chest'
    return cube(tex(b + '_top'), tex(b + '_side'), size, front=tex(b + '_front'))


def glance(c):
    w, h = W, 780
    im = panel_bg(w, h, seed=7)
    y = heading(im, 'CHALLENGE CRAFT AT A GLANCE', 34, 30)
    tiles = [
        (tex('cc:item/challenge_stick'), c['n'], 'CHALLENGES', 'Play one, or stack as many as you dare.', MAGENTA),
        (tex('item/experience_bottle'), c['max_level'], 'LEVELS', 'Then Infinity Stars: a new one every %s XP.' % format(c['star_xp'], ','), CYAN),
        (infinite_chest(64), len(c['perks']), 'PERKS', 'From %s to the %s.' % (c['perks'][0][1], c['perks'][-1][1]), GOLD),
        (tex('cc:item/lockout_bingo_map'), c['goals'], 'BINGO GOALS', 'Up to %d teams race on a 5 x 5 Lockout board.' % c['teams'], LIME),
        (tex('item/written_book'), c['trivia'], 'TRIVIA QUESTIONS', 'Answer right every 2 minutes, or die.', ROSE),
        (tex('item/clock_00'), c['daily'], 'DAILY RUNS', 'One ruleset and one seed a day, the same for everyone.', AMBER),
    ]
    tw, th, gap = 360, 196, 24
    x0 = (w - 3 * tw - 2 * gap) // 2
    for i, (ic, num, lab, line, col) in enumerate(tiles):
        x = x0 + (i % 3) * (tw + gap)
        yy = y + 30 + (i // 3) * (th + gap)
        stat_tile(im, (x, yy, x + tw, yy + th), ic, num, lab, line, col)
    # the new ones
    by = y + 30 + 2 * (th + gap) + 18
    bh = h - by - 34
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((x0, by, w - x0, by + bh), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    lab = blocky('NEW', 20, top=WHITE, bot=(255, 230, 150), side=(110, 70, 10), depth=3, ow=2)
    lx = (w - lab.width - 40) // 2
    d.rounded_rectangle((lx, by - 22, lx + lab.width + 40, by + 22), 22, fill=GOLD, outline=INK, width=3)
    im.alpha_composite(lab, (lx + 20, by - lab.height // 2))
    news = [(51, 'Void Trail'), (50, 'The House\nAlways Wins'), (48, 'Red Light,\nGreen Light'), (47, 'Cushion Only'), (49, 'All Biomes')]
    cw = (w - 2 * x0) // len(news)
    for i, (cid, name) in enumerate(news):
        assert c['names'][cid].replace(',', '').replace(' ', '') == name.replace('\n', '').replace(',', '').replace(' ', ''), (c['names'][cid], name)
        cx = x0 + cw * i + cw // 2
        ic = item_icon(c['icons'][cid] if cid != 50 else 'ModItems.CHIP', 54) if cid != 50 else fit_icon(tex('cc:item/chip_wallet'), 54)
        im.alpha_composite(ic, (cx - ic.width // 2, by + 34))
        lines = name.split('\n')
        draw_lines(d, (cx, by + 100 + (12 if len(lines) == 1 else 0)), lines, bold(21), WHITE, 25, anchor='ma')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'glance.png')


def rating(c, cid, **kw):
    """The difficulty rating of a challenge as ChallengeManager computes it (sliders given as keywords)."""
    expr = c['diff'][cid]
    env = dict(ticks=kw.get('ticks', 20), slots=kw.get('slots', 36), mobHealthMult=kw.get('mob', 1),
               doubleTroubleMult=kw.get('dt', 2), gameSpeedMult=kw.get('speed', 1), playerCount=kw.get('players', 1))
    expr = re.sub(r'\(playerCount <= 1\) \? 0\.0 : \(1\.0 \+ playerCount\)', '(0.0 if playerCount <= 1 else 1.0 + playerCount)', expr)
    return eval(expr, {}, env)


def xp_for(diff):
    src = read(JAVA, 'mixin', 'PlayerAdvancementTrackerMixin.java')
    k = float(re.search(r'Math\.round\(([\d.]+) \* difficulty\)', src).group(1))
    return round(k * diff)


def step_badge(n, col):
    s = 54
    b = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(b)
    d.ellipse((0, 4, s - 1, s - 1), fill=tuple(v // 3 for v in col))
    d.ellipse((0, 0, s - 1, s - 5), fill=col, outline=INK, width=3)
    d.text((s // 2 + 1, s // 2 - 1), str(n), font=pixel(22), fill=INK, anchor='mm')
    return b


def arrow(im, x, y, col=GOLD):
    d = ImageDraw.Draw(im)
    for i, a in enumerate((90, 160, 255)):
        dx = x + i * 9
        d.polygon([(dx, y - 12), (dx + 12, y), (dx, y + 12), (dx + 4, y)], fill=col[:3] + (a,))


def scoreboard(c, w, h):
    """Step 2 of 'how it works': a stack of challenges adds up to a rating, the rating pays XP."""
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    paste_round(im, stage((w, h), PLUM, DEEP, rays=False, seed=11), (0, 0), 18, shadow=False)
    d = ImageDraw.Draw(im)
    picks = [(21, {}), (29, {}), (35, {'dt': 2})]
    y = 22
    total = 0.0
    for cid, kw in picks:
        r = rating(c, cid, **kw)
        total += r
        ic = item_icon(c['icons'][cid], 30)
        d.rounded_rectangle((12, y, w - 12, y + 44), 10, fill=DEEP, outline=VIOLET, width=2)
        im.alpha_composite(ic, (22, y + 7))
        name = c['names'][cid].replace(' Challenge', '')
        d.text((58, y + 22), name, font=semi(16), fill=WHITE, anchor='lm')
        d.text((w - 22, y + 22), '+%.1f' % r, font=pixel(11), fill=LIME, anchor='rm')
        y += 54
    d.line((20, y + 2, w - 20, y + 2), fill=GOLD, width=3)
    y += 16
    d.text((22, y + 10), 'RATING', font=pixel(13), fill=MUTED, anchor='lm')
    d.text((w - 24, y + 10), '%.1f' % total, font=pixel(20), fill=WHITE, anchor='rm')
    y += 34
    xp = blocky('%d XP' % xp_for(total), 30, top=WHITE, bot=GOLD, side=(110, 70, 10), depth=5)
    glow_behind(im, xp, ((w - xp.width) // 2, y + 6), GOLD, 12, 140)
    im.alpha_composite(xp, ((w - xp.width) // 2, y + 6))
    ImageDraw.Draw(im).rounded_rectangle((0, 0, w - 1, h - 1), 18, outline=GOLD, width=5)
    return im


def flow_how(c):
    w, h = W, 600
    im = panel_bg(w, h, seed=5)
    cols = [
        ('PICK', 'Choose challenges when you create a world: one, or a whole stack.', CYAN,
         ('long/ui_stack@6.8', 0.27, 0.6, 2.0)),
        ('GET RATED', 'Every combination gets a difficulty rating. The rating sets your XP.', GOLD, None),
        ('BEAT IT', 'Defeat the Ender Dragon, or win the game mode you picked.', MAGENTA,
         ('long/dragon@4.4', 0.5, 0.67, 1.0)),
        ('LEVEL UP', 'Lifetime XP stays forever and unlocks new challenges and perks.', LIME,
         ('ui/ui_journey@2.8', 0.5, 0.33, 1.9)),
    ]
    cw, ch, gap = 252, 300, 40
    x0 = (w - 4 * cw - 3 * gap) // 2
    d = ImageDraw.Draw(im)
    for i, (title, text, col, shot) in enumerate(cols):
        x = x0 + i * (cw + gap)
        y = 44
        card = scoreboard(c, cw, ch) if shot is None else game_card(shot[0], cw, ch, shot[1], shot[2], shot[3], border=col, r=18, bw=5)
        drop_shadow(im, card, (x, y + 22))
        im.alpha_composite(step_badge(i + 1, col), (x - 14, y))
        t = blocky(title, 22, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
        im.alpha_composite(t, (x + (cw - t.width) // 2, y + ch + 44))
        lines = wrap(d, text, semi(19), cw)
        draw_lines(d, (x + cw // 2, y + ch + 92), lines, semi(19), WHITE, 25, anchor='ma')
        if i < 3:
            arrow(im, x + cw + 8, y + 22 + ch // 2)
    note = 'Fair play: changing the challenges in the middle of a run sets its XP to 0.'
    d.text((w // 2, h - 46), note, font=semi(18), fill=MUTED, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'flow_how.png')


STAR_COLORS = {'green': (85, 255, 85), 'blue': (85, 85, 255), 'red': (255, 85, 85), 'purple': (255, 85, 255),
               'gold': (255, 170, 0), 'rainbow': None}


def star_icon(size, color):
    s = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    cx = cy = size / 2
    pts = []
    for i in range(10):
        r = size * (0.48 if i % 2 == 0 else 0.21)
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    if color is None:   # rainbow
        g = Image.new('RGBA', (size, size))
        cols = [(255, 85, 85), (255, 170, 0), (255, 255, 85), (85, 255, 85), (85, 255, 255), (85, 85, 255), (255, 85, 255)]
        gd = ImageDraw.Draw(g)
        for x in range(size):
            t = x / size * (len(cols) - 1)
            a, b = cols[int(t)], cols[min(len(cols) - 1, int(t) + 1)]
            f = t - int(t)
            gd.line([(x, 0), (x, size)], fill=tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)))
        m = Image.new('L', (size, size), 0)
        ImageDraw.Draw(m).polygon(pts, fill=255)
        s.paste(g, (0, 0), m)
        d.polygon(pts, outline=INK, width=max(2, size // 16))
    else:
        d.polygon(pts, fill=color, outline=INK, width=max(2, size // 16))
    return s


def levels(c):
    w, h = W, 690
    im = panel_bg(w, h, seed=9)
    y = heading(im, 'THE ROAD TO LEVEL %d' % c['max_level'], 34, 30)
    d = ImageDraw.Draw(im)
    unlocks = {}
    for cid in c['names']:
        unlocks[c['req'][cid]] = unlocks.get(c['req'][cid], 0) + 1
    perk_at = {lvl: (name, pid) for lvl, name, pid in c['perks']}
    lm = read(JAVA, 'LevelManager.java')
    a, b = re.search(r'return (\d+)L \* level \* \(level - (\d+)\);', lm).groups()
    xp_at = lambda L: int(a) * L * (L - int(b)) if L > 1 else 0
    n = c['max_level']
    per_row = n // 2
    x0, x1 = 90, w - 90
    step = (x1 - x0) / (per_row - 1)
    rows = [y + 110, y + 300]
    # the road: a snake through both rows
    pts = [(x0 + i * step, rows[0]) for i in range(per_row)] + [(x1 + 40, rows[0]), (x1 + 40, rows[1])] + \
          [(x1 - i * step, rows[1]) for i in range(per_row)]
    d.line(pts, fill=(70, 30, 120), width=22, joint='curve')
    d.line(pts, fill=VIOLET, width=8, joint='curve')
    for L in range(1, n + 1):
        r = 0 if L <= per_row else 1
        i = (L - 1) % per_row
        x = x0 + i * step if r == 0 else x1 - i * step
        yy = rows[r]
        perk = perk_at.get(L)
        col = GOLD if perk else (CYAN if L != n else MAGENTA)
        rad = 30
        d.ellipse((x - rad, yy - rad + 5, x + rad, yy + rad + 5), fill=tuple(v // 3 for v in col))
        d.ellipse((x - rad, yy - rad, x + rad, yy + rad), fill=DEEP, outline=col, width=5)
        d.text((x, yy + 1), str(L), font=pixel(18 if L < 10 else 16), fill=WHITE, anchor='mm')
        k = unlocks.get(L, 0)
        if k:
            pill(d, (x - 34, yy + 40, x + 34, yy + 66), PLUM, '+%d' % k, pixel(12), tfill=LIME, outline=LIME, ow=2)
        if perk:
            ic = infinite_chest(40) if perk[1] == 109 else item_icon(c['icons'][perk[1]], 40)
            t = icon_tile(ic, 56, fill=(70, 46, 10), rim=GOLD)
            im.alpha_composite(t, (int(x - 28), int(yy - 100)))
    # legend
    ly = rows[1] + 92
    pill(d, (90, ly, 158, ly + 26), PLUM, '+3', pixel(12), tfill=LIME, outline=LIME, ow=2)
    d.text((170, ly + 13), 'new challenges at that level', font=semi(18), fill=MUTED, anchor='lm')
    t = icon_tile(item_icon(c['icons'][101], 22), 30, fill=(70, 46, 10), rim=GOLD)
    im.alpha_composite(t, (520, ly - 2))
    d.text((560, ly + 13), 'a permanent perk', font=semi(18), fill=MUTED, anchor='lm')
    d.text((w - 90, ly + 13), 'Level %d = %s Lifetime XP' % (n, format(xp_at(n), ',')), font=semi(18), fill=MUTED, anchor='rm')
    # infinity stars
    by = ly + 52
    d.rounded_rectangle((60, by, w - 60, h - 34), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    d.text((86, by + 34), 'INFINITY STARS', font=pixel(16), fill=GOLD, anchor='lm')
    d.text((86, by + 66), 'After level %d, every %s XP' % (n, format(c['star_xp'], ',')), font=semi(17), fill=MUTED, anchor='lm')
    d.text((86, by + 90), 'earns a star. Stars color your name.', font=semi(17), fill=MUTED, anchor='lm')
    sx = 470
    for k, (cnt, reward) in enumerate(c['stars']):
        x = sx + k * 98
        if reward.startswith('perk_'):
            st = star_icon(44, WHITE)
            lab = 'SECRET'
        else:
            st = star_icon(44, STAR_COLORS[reward])
            lab = reward.upper()
        im.alpha_composite(st, (x - 22, by + 16))
        d.text((x, by + 76), cnt, font=pixel(13), fill=WHITE, anchor='mm')
        d.text((x, by + 100), lab, font=pixel(9), fill=MUTED, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'levels.png')


CAT_COLORS = {'DROPS': GOLD, 'WORLD': CYAN, 'COMBAT': RED, 'CHAOS': MAGENTA}
CAT_NAMES = {'DROPS': 'Drops & Loot', 'WORLD': 'World & Progression', 'COMBAT': 'Combat & Survival', 'CHAOS': 'Chaos & Randomizers'}

HIGHLIGHTS = [
    [(40, 'long/bob_board@3.5', 0.5, 0.49, 1.9, 'Teams race for one 5 x 5 board. Every goal can be claimed only once.'),
     (45, 'chal/force_item@5.6', 0.5, 0.42, 1.05, 'Everyone hunts their own random items. Most items when time runs out wins.'),
     (49, 'long/all_biomes@0.4', 0.5, 0.0, 1.0, 'Stand in every biome of all three dimensions, caves included.'),
     (36, 'long/trivia@3.4', 0.5, 0.5, 1.12, 'A Minecraft question every 2 minutes. Answer wrong and you die.'),
     (11, 'chal/skyblock@4.5', 0.45, 0.6, 1.2, 'The whole world is one tiny island in the void.'),
     (38, 'long/chunk_hunt@7.2', 0.5, 0.5, 1.3, 'Each new chunk locks you in and sends a glowing mob to beat.'),
     (51, 'chal/void_trail_walk@5.1', 0.55, 0.42, 1.3, 'Every block you leave falls away, all the way down to the void.'),
     (48, 'long/red_light_v2@8.2', 0.6, 0.48, 1.25, 'Move on red and you die. Not even the camera may turn.')],
    [(46, 'chal/dice_throw@0.9', 0.6, 0.45, 1.15, 'Throw the die: the pips are how many blocks you may walk.'),
     (47, 'chal/cushion@1.9', 0.42, 0.5, 1.6, 'No walking, no jumping. Place a cushion and sit on it to move.'),
     (29, 'chal/floor_lava@3.1', 0.5, 0.45, 1.3, 'Stand still for 3 seconds, or on natural blocks, and you burn.'),
     (33, 'chal/size_matters@2.2', 0.4, 0.5, 1.0, 'Mobs spawn tiny and fast, or huge with long reach.'),
     (35, 'chal/double_trouble@5.5', 0.5, 0.5, 1.4, 'Two to ten times the mobs. Set the slider, if you dare.'),
     (34, 'chal/upside_down@2.2', 0.5, 0.4, 1.0, 'Drops float up into the sky. Catch them fast.'),
     (16, 'long/chunk_walk_v2@4.4', 0.45, 0.55, 1.2, 'Every chunk is built from one random block.'),
     (44, 'long/progressive_drops@4.5', 0.6, 0.45, 1.2, 'Blocks drop nothing until you unlock them, one by one.')],
]


def challenge_card(c, cid, shot, fx, fy, zoom, text, cw, ih):
    col = CAT_COLORS[c['cats'].get(cid, 'WORLD')]
    th = 112
    card = Image.new('RGBA', (cw, ih + th), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle((0, 0, cw - 1, ih + th - 1), 20, fill=PLUM, outline=col, width=5)
    pic = cover(frame(shot), cw - 10, ih - 5, fx, fy, zoom).convert('RGBA')
    m = Image.new('L', pic.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, pic.width - 1, pic.height + 30), 16, fill=255)
    card.paste(pic, (5, 5), m)
    cd.line((5, ih, cw - 6, ih), fill=col, width=4)
    lvl = 'LV %d' % c['req'][cid]
    pill(cd, (14, 14, 14 + 18 + cd.textlength(lvl, font=pixel(12)), 42), INK, lvl, pixel(12), tfill=GOLD, outline=GOLD, ow=2)
    it = icon_tile(item_icon(c['icons'][cid], 34) if cid != 50 else fit_icon(tex('cc:item/chip_wallet'), 34), 50, fill=DEEP, rim=col)
    card.alpha_composite(it, (cw - 64, 12))
    name = c['names'][cid]
    f = bold(22)
    while cd.textlength(name, font=f) > cw - 24:
        f = bold(f.size - 1)
    cd.text((cw // 2, ih + 16), name, font=f, fill=WHITE, anchor='ma')
    lines = wrap(cd, text, semi(16), cw - 26)
    draw_lines(cd, (cw // 2, ih + 48), lines, semi(16), MUTED, 20, anchor='ma')
    return card


def highlights(c, k):
    w = W
    cw, ih, gap = 270, 330, 22
    rows = 2
    top = 34
    h = top + rows * (ih + 112) + (rows - 1) * gap + (70 if k == 1 else 34)
    im = panel_bg(w, h, seed=13 + k)
    x0 = (w - 4 * cw - 3 * gap) // 2
    for i, (cid, shot, fx, fy, zoom, text) in enumerate(HIGHLIGHTS[k]):
        card = challenge_card(c, cid, shot, fx, fy, zoom, text, cw, ih)
        drop_shadow(im, card, (x0 + (i % 4) * (cw + gap), top + (i // 4) * (ih + 112 + gap)), off=(0, 8), blur=10)
    if k == 1:
        shown = {e[0] for g in HIGHLIGHTS for e in g}
        rest = c['n'] - len(shown)
        ImageDraw.Draw(im).text((w // 2, h - 46), '+ %d more challenges, all listed below' % rest, font=pixel(15), fill=GOLD, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'highlights_%d.jpg' % (k + 1), quality=88)


NEW = (47, 48, 49, 50, 51)


def chip_icon(c, cid, size):
    if cid == 50:
        return fit_icon(tex('cc:item/chip_wallet'), size)
    return item_icon(c['icons'][cid], size)


def all51(c):
    w = W
    f = semi(16)
    probe = ImageDraw.Draw(Image.new('RGB', (1, 1)))
    colw, ch, gapx, gapy = 548, 36, 8, 8

    def lay(L):
        ids = sorted(i for i in c['names'] if c['req'][i] == L)
        x, rows, items = 0, 1, []
        for cid in ids:
            cw = 44 + probe.textlength(c['names'][cid], font=f) + 14 + (44 if cid in NEW else 0)
            if x + cw > colw - 64:
                x, rows = 0, rows + 1
            items.append((cid, x, (rows - 1) * (ch + gapy), cw))
            x += cw + gapx
        return items, rows

    blocks = [(L,) + lay(L) for L in range(1, c['max_level'] + 1)]
    heights = [r * (ch + gapy) + 8 for _, _, r in blocks]
    split = min(range(1, len(blocks)), key=lambda k: max(sum(heights[:k]), sum(heights[k:])))
    top = 150
    h = top + max(sum(heights[:split]), sum(heights[split:])) + 40
    im = panel_bg(w, h, seed=17)
    heading(im, 'ALL %d CHALLENGES' % c['n'], 34, 30)
    d = ImageDraw.Draw(im)
    leg = [(CAT_COLORS[k], CAT_NAMES[k]) for k in ('DROPS', 'WORLD', 'COMBAT', 'CHAOS')]
    total = sum(26 + d.textlength(n, font=semi(17)) + 30 for _, n in leg)
    lx = (w - total) / 2
    for col, name in leg:
        d.rounded_rectangle((lx, 92, lx + 18, 110), 5, fill=col)
        d.text((lx + 26, 101), name, font=semi(17), fill=MUTED, anchor='lm')
        lx += 26 + d.textlength(name, font=semi(17)) + 30
    d.text((w // 2, 126), 'Sorted by the level that unlocks them', font=semi(16), fill=MUTED, anchor='mm')
    for k, (L, items, rows) in enumerate(blocks):
        col0 = 40 if k < split else 40 + colw + 32
        by = top + sum(heights[:k] if k < split else heights[split:k])
        bh = rows * (ch + gapy) - gapy
        d.rounded_rectangle((col0, by, col0 + 52, by + bh), 12, fill=PLUM, outline=GOLD, width=3)
        if rows > 1:
            d.text((col0 + 26, by + bh / 2 - 9), str(L), font=pixel(16 if L < 10 else 14), fill=WHITE, anchor='mm')
            d.text((col0 + 26, by + bh / 2 + 12), 'LV', font=pixel(8), fill=GOLD, anchor='mm')
        else:
            d.text((col0 + 26, by + bh / 2 + 1), str(L), font=pixel(15 if L < 10 else 13), fill=WHITE, anchor='mm')
        for cid, x, dy, cw in items:
            col = CAT_COLORS[c['cats'].get(cid, 'WORLD')]
            x += col0 + 64
            y0 = by + dy
            d.rounded_rectangle((x, y0, x + cw, y0 + ch), 10, fill=(38, 18, 74), outline=col, width=2)
            ic = chip_icon(c, cid, 24)
            im.alpha_composite(ic, (int(x + 10 + (24 - ic.width) / 2), int(y0 + (ch - ic.height) / 2)))
            d.text((x + 44, y0 + ch / 2), c['names'][cid], font=f, fill=WHITE, anchor='lm')
            if cid in NEW:
                nx = x + cw - 48
                d.rounded_rectangle((nx, y0 + 8, nx + 40, y0 + ch - 8), 6, fill=GOLD)
                d.text((nx + 20, y0 + ch / 2 + 1), 'NEW', font=pixel(9), fill=INK, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'all_challenges.png')


def feature_card(shot, fx, fy, zoom, title, text, col, cw, ih, icon):
    th = 118
    card = Image.new('RGBA', (cw, ih + th), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle((0, 0, cw - 1, ih + th - 1), 20, fill=PLUM, outline=col, width=5)
    pic = cover(frame(shot), cw - 10, ih - 5, fx, fy, zoom).convert('RGBA')
    m = Image.new('L', pic.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, pic.width - 1, pic.height + 30), 16, fill=255)
    card.paste(pic, (5, 5), m)
    cd.line((5, ih, cw - 6, ih), fill=col, width=4)
    it = icon_tile(fit_icon(icon, 40), 58, fill=DEEP, rim=col)
    card.alpha_composite(it, (18, ih + 18))
    t = blocky(title, 18, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
    card.alpha_composite(t, (92, ih + 14))
    lines = wrap(cd, text, semi(18), cw - 112)
    draw_lines(cd, (94, ih + 50), lines, semi(18), MUTED, 23)
    return card


def features(c):
    w = W
    cw, ih, gap = 556, 330, 24
    h = 34 + 2 * (ih + 118) + gap + 34
    im = panel_bg(w, h, seed=19)
    cards = [
        ('long/ui_hub@1.5', 0.5, 0.5, 1.0, 'HUB AND DAILY',
         'Your level, recent runs and today\'s daily: one ruleset and seed for everyone.', AMBER, tex('item/clock_00')),
        ('long/ui_code@3.4', 0.5, 0.5, 1.1, 'CHALLENGE CODES',
         'Share any setup as a short code, seed included. Your friends paste it and play the same world.', CYAN, tex('item/name_tag')),
        ('ui/ui_summary@2.0', 0.5, 0.47, 1.0, 'RUN SUMMARY CARD',
         'Time, difficulty and XP on one card. Save it, or paste it straight into a post.', MAGENTA, tex('item/paper')),
        ('long/ui_presets@5.6', 0.5, 0.5, 1.05, 'PRESETS',
         'Save your favourite setups and load them again with one click.', LIME, tex('item/writable_book')),
    ]
    x0 = (w - 2 * cw - gap) // 2
    for i, (shot, fx, fy, zoom, title, text, col, icon) in enumerate(cards):
        card = feature_card(shot, fx, fy, zoom, title, text, col, cw, ih, icon)
        drop_shadow(im, card, (x0 + (i % 2) * (cw + gap), 34 + (i // 2) * (ih + 118 + gap)), off=(0, 8), blur=10)
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'features.jpg', quality=88)


def casino(c):
    w = W
    plinko_max = max(int(v) for v in re.search(r'MULT100 = \{([^}]*)\}', read(JAVA, 'casino', 'PlinkoMath.java')).group(1).split(',')) // 100
    tiles = [
        ('casino/casino_counter@6.2', 0.5, 0.38, 1.0, 'THE CROUPIER', 'Every item is worth chips. Cash them in at his booth.', GOLD),
        ('casino/casino_slot_legendary@9.6', 0.5, 0.6, 1.0, 'MINE FEVER', 'Five reels, free spins and up to %sx your bet.' % format(c['slot_max'], ','), MAGENTA),
        ('casino/casino_plinko@1.5', 0.5, 0.56, 1.0, 'PLINKO', 'Drop the ball and hope for the %dx edges.' % plinko_max, CYAN),
        ('casino/casino_crash_launch@2.8', 0.5, 0.47, 1.0, 'CRASH', 'The rocket climbs. Cash out before it blows up.', AMBER),
        ('casino/casino_roulette_spin@6.6', 0.5, 0.56, 1.15, 'ROULETTE', 'A full single-zero table. Bet right on the felt.', LIME),
        ('casino/casino_blackjack@5.0', 0.5, 0.45, 1.15, 'BLACKJACK', 'Die, and one hand against the House decides if you come back.', RED),
    ]
    tw, ih, th, gap = 364, 286, 104, 22
    top = 34
    h = top + 2 * (ih + th) + gap + 214
    im = panel_bg(w, h, seed=23)
    x0 = (w - 3 * tw - 2 * gap) // 2
    for i, (shot, fx, fy, zoom, title, text, col) in enumerate(tiles):
        card = Image.new('RGBA', (tw, ih + th), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        cd.rounded_rectangle((0, 0, tw - 1, ih + th - 1), 20, fill=PLUM, outline=col, width=5)
        pic = cover(frame(shot), tw - 10, ih - 5, fx, fy, zoom).convert('RGBA')
        m = Image.new('L', pic.size, 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, pic.width - 1, pic.height + 30), 16, fill=255)
        card.paste(pic, (5, 5), m)
        cd.line((5, ih, tw - 6, ih), fill=col, width=4)
        t = blocky(title, 18, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
        card.alpha_composite(t, ((tw - t.width) // 2, ih + 12))
        lines = wrap(cd, text, semi(17), tw - 30)
        draw_lines(cd, (tw // 2, ih + 46), lines, semi(17), MUTED, 22, anchor='ma')
        drop_shadow(im, card, (x0 + (i % 3) * (tw + gap), top + (i // 3) * (ih + th + gap)), off=(0, 8), blur=10)
    # the rules of the House
    d = ImageDraw.Draw(im)
    by = top + 2 * (ih + th) + gap + 28
    d.rounded_rectangle((x0, by, w - x0, by + 116), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    lab = 'THE RULES OF THE HOUSE'
    lw = d.textlength(lab, font=pixel(14)) + 40
    d.rounded_rectangle(((w - lw) / 2, by - 18, (w + lw) / 2, by + 18), 18, fill=GOLD, outline=INK, width=3)
    d.text((w / 2, by + 1), lab, font=pixel(14), fill=INK, anchor='mm')
    rules = [(tex('item/clock_00'), 'A fee every %d min' % c['fee_min'], 'from the whole team'),
             (tex('item/gold_ingot'), 'Every game pays', 'back %d %% on average' % c['rtp']),
             (tex('item/zombie_spawn_egg'), 'Lost bets send', 'monsters after you'),
             (tex('item/totem_of_undying'), 'A death costs', '%d %% of your chips' % c['death_tax'])]
    cw = (w - 2 * x0) // 4
    for i, (ic, a, b) in enumerate(rules):
        cx = x0 + i * cw + 28
        im.alpha_composite(fit_icon(ic, 40), (cx, by + 38))
        d.text((cx + 54, by + 44), a, font=bold(18), fill=WHITE)
        d.text((cx + 54, by + 68), b, font=semi(17), fill=MUTED)
    d.text((w // 2, h - 40), 'Chips are play money that only exist in your world. No real money, nothing to buy.',
           font=semi(17), fill=MUTED, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'casino.jpg', quality=88)


def photo_card(shot, fx, fy, zoom, title, text, col, cw, ih, th=108, tsize=18, fsize=17):
    card = Image.new('RGBA', (cw, ih + th), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle((0, 0, cw - 1, ih + th - 1), 20, fill=PLUM, outline=col, width=5)
    pic = cover(frame(shot), cw - 10, ih - 5, fx, fy, zoom).convert('RGBA')
    m = Image.new('L', pic.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, pic.width - 1, pic.height + 30), 16, fill=255)
    card.paste(pic, (5, 5), m)
    cd.line((5, ih, cw - 6, ih), fill=col, width=4)
    if title:
        t = blocky(title, tsize, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
        card.alpha_composite(t, ((cw - t.width) // 2, ih + 12))
    lines = wrap(cd, text, semi(fsize), cw - 30)
    draw_lines(cd, (cw // 2, ih + (46 if title else 16)), lines, semi(fsize), MUTED, fsize + 5, anchor='ma')
    return card


def together(c):
    w = W
    cw, ih, gap = 364, 400, 22
    top = 34
    h = top + ih + 108 + 150
    im = panel_bg(w, h, seed=29)
    cards = [
        ('long/nametags_close@3.4', 0.5, 0.42, 1.0, 'NAME TAGS', 'Every player\'s level and stars float above their head.', GOLD),
        ('long/bob_board@0.5', 0.5, 0.5, 1.75, 'TEAM LOCKOUT', 'Up to %d teams race on one Lockout Bingo board.' % c['teams'], LIME),
        ('long/all_biomes@3.4', 0.5, 0.42, 1.35, 'SHARED GOALS', 'All Biomes: one checklist for the whole server.', CYAN),
    ]
    x0 = (w - 3 * cw - 2 * gap) // 2
    for i, (shot, fx, fy, zoom, title, text, col) in enumerate(cards):
        drop_shadow(im, photo_card(shot, fx, fy, zoom, title, text, col, cw, ih), (x0 + i * (cw + gap), top), off=(0, 8), blur=10)
    d = ImageDraw.Draw(im)
    by = top + ih + 108 + 30
    d.rounded_rectangle((x0, by, w - x0, h - 34), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    items = [(tex('item/experience_bottle'), 'Lifetime XP and levels', 'sync to every player'),
             (tex('item/end_crystal'), 'Symbiotic Bond: one', 'health pool for everyone'),
             (tex('item/name_tag'), 'Challenge codes: same', 'setup, same seed')]
    cw3 = (w - 2 * x0) // 3
    for i, (ic, a, b) in enumerate(items):
        cx = x0 + i * cw3 + 30
        im.alpha_composite(fit_icon(ic, 40), (cx, by + 26))
        d.text((cx + 54, by + 26), a, font=bold(18), fill=WHITE)
        d.text((cx + 54, by + 50), b, font=semi(17), fill=MUTED)
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'together.jpg', quality=88)


def hazard_tape(w, h, text, size=20):
    t = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(t)
    d.rectangle((0, 0, w, h), fill=GOLD)
    for x in range(-h, w + h, 40):
        d.polygon([(x, 0), (x + 20, 0), (x + 20 - h, h), (x - h, h)], fill=INK)
    f = pixel(size)
    tw = d.textlength(text, font=f)
    d.rectangle(((w - tw) / 2 - 22, 4, (w + tw) / 2 + 22, h - 5), fill=GOLD)
    d.text((w / 2, h / 2 + 1), text, font=f, fill=INK, anchor='mm')
    return t


def bob(c):
    w = W
    cw, ih, gap = 270, 360, 22
    top = 112
    h = top + ih + 92 + 190
    im = panel_bg(w, h, seed=31)
    tape = hazard_tape(w - 80, 52, 'EARLY DEVELOPMENT STAGE', 20)
    drop_shadow(im, tape, (40, 36), off=(0, 6), blur=8)
    cards = [
        ('long/bob_1@1.5', 0.4, 0.45, 1.25, 'He gathers wood and food.', CYAN),
        ('long/bob_2@4.5', 0.6, 0.45, 1.25, 'He mines, crafts and smelts.', CYAN),
        ('long/bob_3@1.5', 0.35, 0.5, 1.15, 'He fights back.', CYAN),
        ('long/bob_board@3.5', 0.0, 0.47, 1.6, 'And claims tiles for his team.', MAGENTA),
    ]
    x0 = (w - 4 * cw - 3 * gap) // 2
    for i, (shot, fx, fy, zoom, text, col) in enumerate(cards):
        card = photo_card(shot, fx, fy, zoom, None, text, col, cw, ih, th=64, fsize=17)
        drop_shadow(im, card, (x0 + i * (cw + gap), top), off=(0, 8), blur=10)
    d = ImageDraw.Draw(im)
    by = top + ih + 64 + 30
    d.rounded_rectangle((x0, by, w - x0, h - 34), 22, fill=(60, 18, 96), outline=CYAN, width=4)
    btn = 'PLAY AGAINST BOB'
    f = pixel(15)
    bw = d.textlength(btn, font=f) + 44
    d.rounded_rectangle((x0 + 28, by + 30, x0 + 28 + bw, by + 74), 10, fill=(40, 120, 70), outline=LIME, width=3)
    d.text((x0 + 28 + bw / 2, by + 53), btn, font=f, fill=WHITE, anchor='mm')
    d.text((x0 + 30, by + 104), 'in the Lockout Bingo lobby', font=semi(17), fill=MUTED, anchor='lm')
    x = x0 + 28 + bw + 40
    for i, (lvl, col) in enumerate(zip(c['bob'], (LIME, GOLD, RED))):
        tw = d.textlength(lvl.upper(), font=pixel(14)) + 32
        pill(d, (x, by + 32, x + tw, by + 72), DEEP, lvl.upper(), pixel(14), tfill=col, outline=col, ow=3)
        x += tw + 14
    lines = wrap(d, 'An AI rival for Lockout Bingo. He reads the board and plans a route, but he still makes mistakes and learns with every update.', semi(17), w - x0 - (x0 + 28 + bw + 40) - 20)
    draw_lines(d, (x0 + 28 + bw + 40, by + 88), lines, semi(17), MUTED, 22)
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'bob.jpg', quality=88)


def bisect():
    """The BisectHosting partner banner in the look of this page (logo cut from the official partner banner)."""
    w, h = 1600, 323
    im = night_bg((w, h), 'menu_night.jpg', fy=0.5, dark=0.8)
    d = ImageDraw.Draw(im)
    # white logo plate on the left, like the official banner
    plate = Image.new('RGBA', (560, h - 60), (0, 0, 0, 0))
    ImageDraw.Draw(plate).rounded_rectangle((0, 0, plate.width - 1, plate.height - 1), 26, fill=WHITE, outline=CYAN, width=5)
    lg = Image.open(os.path.join(SRC, 'bisect_logo.png')).convert('RGBA')
    lg = lg.resize((480, round(lg.height * 480 / lg.width)), Image.LANCZOS)
    plate.alpha_composite(lg, ((plate.width - lg.width) // 2, (plate.height - lg.height) // 2))
    drop_shadow(im, plate, (40, 30), off=(0, 8), blur=12)
    x = 650
    t = blocky('PLAY WITH YOUR FRIENDS', 30, top=WHITE, bot=PINK)
    glow_behind(im, t, (x, 40), MAGENTA, 14, 110)
    im.alpha_composite(t, (x, 40))
    d.text((x + 4, 104), 'Rent a server at BisectHosting', font=bold(30), fill=WHITE)
    # the ticket
    ty = 158
    d.rounded_rectangle((x, ty, w - 60, ty + 104), 20, fill=GOLD, outline=INK, width=4)
    d.rounded_rectangle((x + 14, ty + 14, x + 330, ty + 90), 14, fill=(255, 236, 170), outline=(150, 100, 20), width=3)
    d.text((x + 172, ty + 36), 'CODE', font=pixel(16), fill=(150, 100, 20), anchor='mm')
    d.text((x + 172, ty + 66), 'KASAX007', font=pixel(24), fill=INK, anchor='mm')
    for yy in range(ty + 12, ty + 96, 14):
        d.line((x + 350, yy, x + 350, yy + 7), fill=(150, 100, 20), width=3)
    off = blocky('25% OFF', 34, top=WHITE, bot=(255, 120, 200), side=(90, 20, 70), depth=5)
    im.alpha_composite(off, (x + 376, ty + 10))
    d.text((x + 380 + off.width + 20, ty + 52), 'your first month', font=bold(24), fill=INK, anchor='lm')
    d.text((w - 64, h - 26), 'bisecthosting.com/kasax007', font=bold(22), fill=ICE, anchor='rm')
    bulbs(im, (14, 14, w - 15, h - 15), step=32, r=4, color=GOLD)
    save(im, 'bisect.png')


def pack_cta(c):
    w, h = W, 300
    im = night_bg((w, h), 'menu_dusk.jpg', fy=0.55, dark=0.85)
    lg = Image.open(os.path.join(SRC, 'pack_logo.jpg')).convert('RGB').resize((236, 236), Image.LANCZOS)
    card = Image.new('RGBA', (236, 236))
    card.paste(lg, (0, 0), rounded((236, 236), 24))
    ImageDraw.Draw(card).rounded_rectangle((0, 0, 235, 235), 24, outline=MAGENTA, width=5)
    drop_shadow(im, card, (40, 32))
    x = 310
    t = blocky('OR TAKE THE WHOLE PACK', 28, top=WHITE, bot=PINK)
    glow_behind(im, t, (x, 40), MAGENTA, 14, 110)
    im.alpha_composite(t, (x, 40))
    d = ImageDraw.Draw(im)
    lines = wrap(d, 'The Challenge Craft modpack: this mod plus shaders, proximity voice chat, a full soundscape, '
                    'big storage and a menu made for it. All set up, one click.', semi(22), w - x - 60)
    draw_lines(d, (x + 2, 100), lines, semi(22), WHITE, 29)
    pk = pack_counts()
    chips = ['%d MODS' % pk['mods'], '%d SHADER PACKS' % pk['shaders'], 'SERVER PACK']
    cx = x
    for ch_ in chips:
        tw = d.textlength(ch_, font=pixel(14)) + 32
        pill(d, (cx, h - 76, cx + tw, h - 38), DEEP, ch_, pixel(14), tfill=GOLD, outline=GOLD, ow=3)
        cx += tw + 14
    bulbs(im, (14, 14, w - 15, h - 15), step=30, r=3, color=GOLD)
    save(im, 'pack_cta.jpg', quality=90)


# the server pack of v5 (Serverpack Challenge Craft 26.3-v5.zip): the gameplay mods, plus configs and server.properties
SERVER_MODS = ['Challenge Craft', 'Fabric API', 'Simple Voice Chat', 'Upgraded Iron Chests', 'Storage Drawers', 'Lithium',
               'FerriteCore', 'Sodium', 'Packet Fixer', 'spark', 'Mouse Tweaks', 'CreativeCore', 'AttributeFix', 'Prickle',
               'Konkrete', 'Melody', 'BisectHosting Server Menu']
VOICE_PORT = 24454


def info_tile(im, box, icon, title, text, col, names=None):
    x0, y0, x1, y1 = box
    tile = Image.new('RGBA', (x1 - x0, y1 - y0), (0, 0, 0, 0))
    ImageDraw.Draw(tile).rounded_rectangle((0, 0, x1 - x0 - 1, y1 - y0 - 1), 22, fill=PLUM + (235,), outline=col, width=4)
    im.alpha_composite(tile, (x0, y0))
    d = ImageDraw.Draw(im)
    it = icon_tile(fit_icon(icon, 60), 84, fill=DEEP, rim=col)
    im.alpha_composite(it, (x0 + 20, y0 + 20))
    t = blocky(title, 18, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
    im.alpha_composite(t, (x0 + 120, y0 + 24))
    lines = wrap(d, text, semi(18), x1 - x0 - 140)
    yy = draw_lines(d, (x0 + 122, y0 + 62), lines, semi(18), WHITE, 23)
    if names:
        lines = wrap(d, ' · '.join(names), semi(15), x1 - x0 - 40)
        draw_lines(d, (x0 + 22, max(yy + 10, y0 + 118)), lines, semi(15), MUTED, 19)


def pack_glance(c):
    w = W
    tw, th, gap = 552, 176, 24
    h = 34 + 50 + 30 + 3 * (th + gap) + 14 + 104 + 34
    im = panel_bg(w, h, seed=37)
    y = heading(im, 'WHAT THE PACK ADDS', 34, 30)
    pk = pack_counts()
    tiles = [
        (cube(tex('block/glowstone'), tex('block/glowstone'), 60), '%d SHADER PACKS' % pk['shaders'],
         'Complementary Reimagined is on from the start. BSL and Bliss are one click away.', CYAN,
         ['Iris'] + SHADERS),
        (tex('item/goat_horn'), 'PROXIMITY VOICE', 'Hear your friends when they are near, and their screams from the next cave.', LIME,
         ['Simple Voice Chat']),
        (tex('item/music_disc_cat'), 'SOUNDSCAPE', 'Caves, forests, water and weather get their own sound, and it echoes like it should.', MAGENTA,
         ['AmbientSounds', 'Sound Physics Remastered']),
        (chest_icon(60), 'BIG STORAGE', 'Iron to netherite chests, safe chests with a passcode, and drawers for huge stacks.', GOLD,
         ['Upgraded Iron Chests', 'Storage Drawers']),
        (tex('item/golden_apple'), 'COMFORT', 'Food values, mouse sorting, a zoom key, and livelier players and mobs.', AMBER,
         ['AppleSkin', 'Mouse Tweaks', 'Just Zoom', 'Not Enough Animations', '3D Skin Layers', 'Entity Model Features', 'Entity Texture Features']),
        (tex('item/painting'), 'CUSTOM MENU', 'A pixel-art title screen and loading screen made for this pack.', ROSE,
         ['FancyMenu', 'Drippy Loading Screen', 'Melody']),
    ]
    x0 = (w - 2 * tw - gap) // 2
    for i, (ic, title, text, col, names) in enumerate(tiles):
        x = x0 + (i % 2) * (tw + gap)
        yy = y + 30 + (i // 2) * (th + gap)
        info_tile(im, (x, yy, x + tw, yy + th), ic, title, text, col, names)
    d = ImageDraw.Draw(im)
    by = y + 30 + 3 * (th + gap) + 14
    d.rounded_rectangle((x0, by, w - x0, h - 34), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    im.alpha_composite(fit_icon(tex('item/redstone'), 44), (x0 + 24, by + 22))
    d.text((x0 + 84, by + 30), 'PERFORMANCE', font=pixel(16), fill=GOLD)
    fast = [m_ for m_ in PACK_MODS['speed'] if m_ != 'spark']
    d.text((x0 + 84, by + 56), ' · '.join(fast) + ' keep it smooth, even with 10x the mobs.', font=semi(18), fill=WHITE)
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'pack_glance.png')


def menu_art(c):
    w = W
    pw, ph = 564, 318
    h = 34 + ph + 70 + 34
    im = panel_bg(w, h, seed=41)
    x0 = (w - 2 * pw - 24) // 2
    for i, (src, label) in enumerate((('menu_night.jpg', 'MAIN MENU ART'), ('menu_dusk.jpg', 'LOADING SCREEN ART'))):
        pic = cover(Image.open(os.path.join(SRC, src)).convert('RGB'), pw - 10, ph - 10).convert('RGBA')
        if i == 0:
            lg = logo(290)
            pic.alpha_composite(lg, (34, 40))
        card = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
        ImageDraw.Draw(card).rounded_rectangle((0, 0, pw - 1, ph - 1), 20, fill=PLUM, outline=MAGENTA if i == 0 else AMBER, width=5)
        card.paste(pic, (5, 5), rounded(pic.size, 16))
        drop_shadow(im, card, (x0 + i * (pw + 24), 34))
        t = blocky(label, 16, top=WHITE, bot=MAGENTA if i == 0 else AMBER, depth=3, ow=2)
        im.alpha_composite(t, (x0 + i * (pw + 24) + (pw - t.width) // 2, 34 + ph + 22))
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'menu.jpg', quality=90)


def flow_friends(c):
    w, h = W, 520
    im = panel_bg(w, h, seed=43)
    pk_logo = Image.open(os.path.join(SRC, 'pack_logo.jpg')).convert('RGBA')
    steps = [
        (pk_logo, 'INSTALL', 'Get the modpack in the CurseForge app. Everyone plays the same pack.', MAGENTA),
        (cube(tex('block/command_block_front'), tex('block/command_block_side'), 60), 'SERVER',
         'Put the server pack on a Fabric 26.3 server: %d mods and the configs.' % len(SERVER_MODS), CYAN),
        (tex('item/goat_horn'), 'VOICE', 'Open UDP port %d for proximity voice chat.' % VOICE_PORT, LIME),
        (tex('cc:item/challenge_stick'), 'PICK', 'An operator presses %s, picks the challenges and clicks Save + Restart.' % 'J', GOLD),
        (tex('item/nether_star'), 'PLAY', 'A fresh world for everyone, the old one archived. XP syncs to every player.', ROSE),
    ]
    cw, gap = 200, 24
    x0 = (w - 5 * cw - 4 * gap) // 2
    d = ImageDraw.Draw(im)
    for i, (ic, title, text, col) in enumerate(steps):
        x = x0 + i * (cw + gap)
        tile = icon_tile(fit_icon(ic, 92) if ic is not pk_logo else rounded_img(ic, 104), 136, fill=DEEP, rim=col)
        drop_shadow(im, tile, (x + (cw - 136) // 2, 60))
        im.alpha_composite(step_badge(i + 1, col), (x + (cw - 136) // 2 - 22, 40))
        t = blocky(title, 20, top=WHITE, bot=col, side=tuple(v // 3 for v in col), depth=3, ow=2)
        im.alpha_composite(t, (x + (cw - t.width) // 2, 216))
        lines = wrap(d, text, semi(18), cw + 4)
        draw_lines(d, (x + cw // 2, 260), lines, semi(18), WHITE, 23, anchor='ma')
        if i < 4:
            arrow(im, x + cw + 2, 128)
    by = h - 128
    d.rounded_rectangle((x0, by, w - x0, h - 34), 22, fill=(60, 18, 96), outline=GOLD, width=4)
    d.text((w // 2, by + 30), 'No server? Open a singleplayer world to LAN, or rent one at BisectHosting with code KASAX007.',
           font=semi(18), fill=WHITE, anchor='mm')
    d.text((w // 2, by + 60), 'Shaders, menu and sound mods stay on the clients. The server pack only has what the server needs.',
           font=semi(17), fill=MUTED, anchor='mm')
    bulbs(im, (18, 18, w - 19, h - 19), step=30, r=3, color=GOLD)
    save(im, 'flow_friends.png')


def rounded_img(im, size):
    im = im.resize((size, size), Image.LANCZOS)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(im, (0, 0), rounded((size, size), 14))
    return out


PAGE_STRIPS = list(STRIPS)


def main():
    what = sys.argv[3:]
    c = counts()
    jobs = {
        'header': lambda: header(c), 'header_pack': lambda: header(c, pack=True), 'bisect': bisect,
        'glance': lambda: glance(c), 'flow_how': lambda: flow_how(c), 'levels': lambda: levels(c),
        'highlights_1': lambda: highlights(c, 0), 'highlights_2': lambda: highlights(c, 1),
        'all_challenges': lambda: all51(c), 'features': lambda: features(c), 'casino': lambda: casino(c),
        'together': lambda: together(c), 'bob': lambda: bob(c), 'pack_cta': lambda: pack_cta(c),
        'pack_glance': lambda: pack_glance(c), 'menu': lambda: menu_art(c), 'flow_friends': lambda: flow_friends(c),
    }
    for k in PAGE_STRIPS:
        jobs['strip_' + k] = (lambda k=k: strip(k))
    for name in what or list(jobs):
        jobs[name]()


if __name__ == '__main__':
    main()
