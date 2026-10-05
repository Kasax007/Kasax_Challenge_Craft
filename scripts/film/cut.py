"""A small editor for the shorts: clips from the filmed frame folders, cut hard, with punch-in
zooms, shakes and flashes, captions drawn on the frames (TikTok style: big, white, black outline,
key words in colour, popping in), and a sound-effect track. Frames go straight into ffmpeg."""
import functools, os, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 1920, 30
SP = '/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad'
FONTS = SP + '/fonts'
CAPTION_FONT = FONTS + '/LuckiestGuy-Regular.ttf'
TITLE_FONT = FONTS + '/LuckiestGuy-Regular.ttf'
YELLOW = (255, 225, 77)
GOLD = (255, 196, 46)
RED = (255, 72, 72)
GREEN = (98, 235, 104)
WHITE = (255, 255, 255)


class Clip:
    """{@code seconds} of a filmed shot from {@code start}, played at {@code speed}.
    zoom: (from, to) scale over the clip; focus: the point (0..1, 0..1) the zoom closes in on."""

    def __init__(self, shot, start, length, speed=1.0, zoom=(1.0, 1.0), focus=(0.5, 0.5), shake=0.0,
                 flash=False, hold_last=0.0, reverse=False, label=None):
        self.shot = shot
        self.start = start
        self.length = length
        self.speed = speed
        self.zoom = zoom
        self.focus = focus
        self.shake = shake
        self.flash = flash
        self.hold_last = hold_last
        self.reverse = reverse
        self.label = label or shot

    @property
    def frames(self):
        return int(round(self.length * FPS))

    def source_index(self, k):
        """Frame of the shot shown at output frame k of this clip."""
        played = min(k, int(round((self.length - self.hold_last) * FPS)) - 1)
        played = max(0, played)
        if self.reverse:
            played = int(round((self.length - self.hold_last) * FPS)) - 1 - played
        return int(round(self.start * FPS + played * self.speed))


@functools.lru_cache(maxsize=64)
def _frame(path):
    im = Image.open(path).convert('RGB')
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return im


def shot_frame(shot_dir, i):
    files = _files(shot_dir)
    i = max(0, min(len(files) - 1, i))
    return _frame(os.path.join(shot_dir, files[i]))


@functools.lru_cache(maxsize=None)
def _files(shot_dir):
    return sorted(f for f in os.listdir(shot_dir) if f.endswith(('.png', '.jpg')) and f.startswith('f'))


def _zoomed(im, z, focus, dx=0, dy=0):
    if z <= 1.0001 and dx == 0 and dy == 0:
        return im
    cw, ch = W / z, H / z
    cx = focus[0] * W + (W / 2 - focus[0] * W) * (1 - 1 / z) * 0  # keep the focus point where it is
    # The crop that keeps the focus point fixed on screen while scaling round it.
    left = focus[0] * W - focus[0] * cw + dx
    top = focus[1] * H - focus[1] * ch + dy
    left = max(0, min(W - cw, left))
    top = max(0, min(H - ch, top))
    return im.resize((W, H), Image.BICUBIC, box=(left, top, left + cw, top + ch))


# ------------------------------------------------------------------ captions

class Caption:
    """Text on screen from t0 to t1 (seconds of the final cut). Words wrapped in *stars* are
    coloured (accent). style: 'cap' (the running caption), 'hook' (big title), 'tag' (small label)."""

    def __init__(self, t0, t1, text, style='cap', y=None, accent=YELLOW, size=None):
        self.t0, self.t1, self.text, self.style = t0, t1, text, style
        self.y = y
        self.accent = accent
        self.size = size


def _font(path, size):
    return ImageFont.truetype(path, size)


@functools.lru_cache(maxsize=256)
def _caption_image(text, style, accent, size):
    """The caption rendered once, on a transparent canvas: (image, height)."""
    if style == 'hook':
        size = size or 112
        stroke = 10
    elif style == 'tag':
        size = size or 64
        stroke = 7
    else:
        size = size or 92
        stroke = 9
    font = _font(CAPTION_FONT, size)
    words = []
    inside = False  # an accent may span several words: *GAME OVER*, and end before punctuation: *GAMBLE*?
    for raw in text.split(' '):
        hot = inside or '*' in raw
        if raw.count('*') % 2:
            inside = not inside
        words.append((raw.replace('*', ''), hot))
    # Wrap to the safe width.
    max_w = W - 140
    lines, line = [], []
    d0 = ImageDraw.Draw(Image.new('RGBA', (10, 10)))

    def width(ws):
        return d0.textlength(' '.join(w for w, _ in ws), font=font)

    for w in words:
        if line and width(line + [w]) > max_w:
            lines.append(line)
            line = [w]
        else:
            line.append(w)
    if line:
        lines.append(line)
    asc, desc = font.getmetrics()
    lh = int((asc + desc) * 1.02)
    img = Image.new('RGBA', (W, lh * len(lines) + 2 * stroke + 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    y = stroke + 6
    space = d.textlength(' ', font=font)
    for ln in lines:
        x = (W - width(ln)) / 2
        for w, hot in ln:
            fill = accent if hot else WHITE
            d.text((x, y), w, font=font, fill=fill, stroke_width=stroke, stroke_fill=(0, 0, 0))
            x += d.textlength(w, font=font) + space
        y += lh
    # A soft shadow under the outline.
    shadow = img.split()[3].filter(ImageFilter.GaussianBlur(8)).point(lambda a: int(a * 0.55))
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.paste((0, 0, 0, 255), (0, 6), shadow)
    out.alpha_composite(img)
    return out


def _draw_captions(frame, t, captions):
    for c in captions:
        if not (c.t0 <= t < c.t1):
            continue
        img = _caption_image(c.text, c.style, c.accent, c.size)
        age = t - c.t0
        # Pop in: 0.55 -> 1.08 -> 1.0 over the first 0.16 s.
        if age < 0.08:
            s = 0.55 + (1.08 - 0.55) * age / 0.08
        elif age < 0.16:
            s = 1.08 - 0.08 * (age - 0.08) / 0.08
        else:
            s = 1.0
        if s != 1.0:
            img = img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.BICUBIC)
        if c.y is not None:
            cy = c.y
        else:
            cy = {'hook': 0.2, 'tag': 0.12}.get(c.style, 0.64) * H
        x = (W - img.width) // 2
        y = int(cy - img.height / 2)
        frame.alpha_composite(img, (x, y))


# ------------------------------------------------------------------ rendering

def render(clips, captions, out_path, audio=None, overlay=None, crf=18):
    """Renders the cut. overlay(frame_rgba, t) may draw extra things (flash frames, logos)."""
    total = sum(c.frames for c in clips)
    cmd = ['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-']
    if audio:
        cmd += ['-i', audio, '-c:a', 'aac', '-b:a', '192k', '-shortest']
    cmd += ['-c:v', 'libx264', '-preset', 'medium', '-crf', str(crf), '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out_path]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = 0
    for clip in clips:
        shot_dir = os.path.join(SP, 'film', clip.shot) if not os.path.isabs(clip.shot) else clip.shot
        rng = np.random.default_rng(n)
        for k in range(clip.frames):
            t = n / FPS
            f = k / max(1, clip.frames - 1)
            z = clip.zoom[0] + (clip.zoom[1] - clip.zoom[0]) * (f * f * (3 - 2 * f))
            dx = dy = 0
            if clip.shake:
                decay = max(0.0, 1 - k / (FPS * 0.5))
                dx = rng.normal() * clip.shake * decay * 20
                dy = rng.normal() * clip.shake * decay * 20
                z = max(z, 1.0 + clip.shake * 0.06)
            im = _zoomed(shot_frame(shot_dir, clip.source_index(k)), z, clip.focus, dx, dy).convert('RGBA')
            if clip.flash and k < 4:
                white = Image.new('RGBA', (W, H), (255, 255, 255, int(255 * (1 - k / 4) * 0.85)))
                im.alpha_composite(white)
            if overlay:
                overlay(im, t)
            _draw_captions(im, t, captions)
            proc.stdin.write(im.convert('RGB').tobytes())
            n += 1
    proc.stdin.close()
    proc.wait()
    return total / FPS


def contact_sheet(video, out_png, every=1.0, cols=6, thumb=180):
    """Frames of a rendered cut side by side, with their times, for reviewing it."""
    import math, tempfile
    tmp = tempfile.mkdtemp()
    subprocess.run(['ffmpeg', '-v', 'error', '-i', video, '-vf', f'fps=1/{every},scale={thumb}:-1', f'{tmp}/t%04d.png'], check=True)
    files = sorted(os.listdir(tmp))
    rows = math.ceil(len(files) / cols)
    th = int(thumb * H / W)
    sheet = Image.new('RGB', (cols * thumb, rows * (th + 22)), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    for i, f in enumerate(files):
        im = Image.open(os.path.join(tmp, f))
        x, y = (i % cols) * thumb, (i // cols) * (th + 22)
        sheet.paste(im, (x, y + 22))
        d.text((x + 4, y + 4), f'{i * every:.1f}s', fill=(255, 255, 0))
    sheet.save(out_png)
