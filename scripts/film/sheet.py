"""sheet.py SHOT [step] [out]: a contact sheet of a filmed shot, every step-th frame, with frame numbers."""
import os, sys
from PIL import Image, ImageDraw
SP = '/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad'
shot = sys.argv[1]
step = int(sys.argv[2]) if len(sys.argv) > 2 else 15
out = sys.argv[3] if len(sys.argv) > 3 else f'{SP}/film/sheet_{os.path.basename(shot)}.png'
d = shot if os.path.isabs(shot) else f'{SP}/film/{shot}'
files = sorted(f for f in os.listdir(d) if f.startswith('f'))
pick = files[::step]
cols = 8; tw = 150; th = 267
rows = (len(pick) + cols - 1) // cols
sheet = Image.new('RGB', (cols * tw, rows * (th + 16)), (0, 0, 0))
dr = ImageDraw.Draw(sheet)
for i, f in enumerate(pick):
    im = Image.open(os.path.join(d, f)).convert('RGB').resize((tw, th))
    x = (i % cols) * tw; y = (i // cols) * (th + 16)
    sheet.paste(im, (x, y + 16)); dr.text((x + 3, y + 2), f.split('.')[0], fill=(255, 255, 0))
sheet.save(out)
print(out, len(files), 'frames')
