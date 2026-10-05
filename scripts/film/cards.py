"""Title and end cards for the shorts, drawn from the mod's own pixel-art banners."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageEnhance

W, H = 1080, 1920
SP = '/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad'
ART = '/home/user/bob-dev/art'
FONT = SP + '/fonts/LuckiestGuy-Regular.ttf'


def _pixel(img, scale):
    return img.resize((int(img.width * scale), int(img.height * scale)), Image.NEAREST)


def _backdrop(frame_path, darken=0.45, blur=14):
    bg = Image.open(frame_path).convert('RGB').resize((W, H))
    bg = bg.filter(ImageFilter.GaussianBlur(blur))
    return ImageEnhance.Brightness(bg).enhance(darken).convert('RGBA')


def _text(draw, y, text, size, fill=(255, 255, 255), stroke=8):
    font = ImageFont.truetype(FONT, size)
    w = draw.textlength(text, font=font)
    draw.text(((W - w) / 2, y), text, font=font, fill=fill, stroke_width=stroke, stroke_fill=(0, 0, 0))


def end_card(frame_path, out_dir, banner=None, lines=(('FREE ON CURSEFORGE', 92, (255, 225, 77)),)):
    """A one-frame shot folder: blurred backdrop, the Challenge Craft title, a banner, the call to action."""
    os.makedirs(out_dir, exist_ok=True)
    card = _backdrop(frame_path)
    title = Image.open(f'{ART}/title.png').convert('RGBA')
    t = _pixel(title, 1.85)
    card.alpha_composite(t, ((W - t.width) // 2, 560))
    y = 560 + t.height + 40
    if banner:
        b = _pixel(Image.open(f'{ART}/{banner}.png').convert('RGBA'), 1.75)
        card.alpha_composite(b, ((W - b.width) // 2, y))
        y += b.height + 50
    d = ImageDraw.Draw(card)
    for text, size, colour in lines:
        _text(d, y, text, size, colour)
        y += int(size * 1.25)
    card.convert('RGB').save(os.path.join(out_dir, 'f00000.png'))
    return out_dir


def still_card(image_path, out_dir):
    """A one-frame shot folder from any image (scaled to fit the width, letterboxed in black)."""
    os.makedirs(out_dir, exist_ok=True)
    im = Image.open(image_path).convert('RGB')
    s = W / im.width
    im = im.resize((W, int(im.height * s)), Image.LANCZOS)
    card = Image.new('RGB', (W, H), (0, 0, 0))
    card.paste(im, (0, (H - im.height) // 2))
    card.save(os.path.join(out_dir, 'f00000.png'))
    return out_dir
