from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

W, H = 1024, 500
ICON = "app/src/main/ic_launcher-playstore.png"
OUT = "play-store-assets/feature-graphic.png"

# Brand indigo gradient endpoints
TOP = (63, 81, 181)      # #3F51B5 launcher indigo
BOT = (40, 53, 147)      # #283593 deeper indigo

def font(paths, size):
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()

BOLD = ["/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc"]
REG  = ["/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/System/Library/Fonts/Helvetica.ttc"]

img = Image.new("RGB", (W, H), TOP)
px = img.load()
# diagonal gradient
for y in range(H):
    for x in range(0, W, 1):
        t = (x / W * 0.45 + y / H * 0.55)
        r = int(TOP[0] + (BOT[0]-TOP[0]) * t)
        g = int(TOP[1] + (BOT[1]-TOP[1]) * t)
        b = int(TOP[2] + (BOT[2]-TOP[2]) * t)
        px[x, y] = (r, g, b)

draw = ImageDraw.Draw(img, "RGBA")
# faint vertical grid lines (launcher motif)
for x in range(0, W, 64):
    draw.line([(x, 0), (x, H)], fill=(255, 255, 255, 16), width=1)

# --- app icon, rounded, with soft shadow ---
icon = Image.open(ICON).convert("RGBA").resize((300, 300), Image.LANCZOS)
rad = 66
mask = Image.new("L", (300, 300), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, 299, 299], radius=rad, fill=255)
icon.putalpha(mask)

ix, iy = 90, (H - 300) // 2
# shadow
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
sd.rounded_rectangle([ix+8, iy+14, ix+300+8, iy+300+14], radius=rad, fill=(0, 0, 0, 110))
shadow = shadow.filter(ImageFilter.GaussianBlur(16))
img.paste(shadow, (0, 0), shadow)
img.paste(icon, (ix, iy), icon)

# --- text block ---
tx = 470
f_title = font(BOLD, 96)
f_sub   = font(REG, 46)
f_tag   = font(BOLD, 27)

draw.text((tx, 150), "CheckIn", font=f_title, fill=(255, 255, 255, 255))
draw.text((tx+4, 258), "Solopreneur Tracker", font=f_sub, fill=(255, 255, 255, 235))
draw.text((tx+4, 322), "Private  ·  On-device  ·  No account", font=f_tag, fill=(210, 216, 255, 255))

img.save(OUT, "PNG")
print("wrote", OUT, img.size)
