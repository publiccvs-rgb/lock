"""
Builds a realistic in-game-style backdrop for the GUI preview tool out of Minecraft's own block textures
(sky gradient + tiled, biome-tinted grass/dirt ground, sun, clouds), instead of a flat placeholder.

Textures are extracted from Mojang's official client.jar at build/preview time only - nothing is committed to
the repo or redistributed; Mojang's EULA permits use for development purposes but not redistribution.

Usage:
    python3 tools/mc_background.py <path-to-client.jar> <output.png>
"""
import sys
import zipfile
import random
from io import BytesIO
from PIL import Image, ImageDraw, ImageFilter

TEX = [
    "assets/minecraft/textures/block/grass_block_top.png",
    "assets/minecraft/textures/block/dirt.png",
    "assets/minecraft/textures/colormap/grass.png",
]

def extract(jar_path):
    out = {}
    with zipfile.ZipFile(jar_path) as z:
        for t in TEX:
            out[t] = Image.open(BytesIO(z.read(t))).convert("RGBA")
    return out

def tinted(img, rgb):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return out

def build(jar_path, out_path, w=1920, h=1200, seed=5):
    tex = extract(jar_path)
    grass_top, dirt, colormap = tex[TEX[0]], tex[TEX[1]], tex[TEX[2]]
    tint = colormap.getpixel((70, 150))[:3]   # a representative plains-green sample
    grass_t = tinted(grass_top, tint)

    horizon_y = int(h * 0.40)
    canvas = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(canvas)

    sky_top, sky_horizon = (96, 142, 255), (210, 228, 255)
    for y in range(horizon_y):
        t = (y / horizon_y) ** 0.7
        draw.line([(0, y), (w, y)], fill=tuple(int(sky_top[i] + (sky_horizon[i]-sky_top[i])*t) for i in range(3)))
    for y in range(horizon_y, h):
        draw.line([(0, y), (w, y)], fill=(70, 110, 55))

    sky = canvas.crop((0, 0, w, horizon_y)).convert("RGBA")
    sun_layer = Image.new("RGBA", sky.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sun_layer)
    sx, sy, sr = int(w*0.80), int(horizon_y*0.28), 150
    for i in range(sr, 0, -3):
        sd.ellipse([sx-i, sy-i, sx+i, sy+i], fill=(255, 252, 225, int(90*(1-i/sr))))
    sd.ellipse([sx-20, sy-20, sx+20, sy+20], fill=(255, 255, 248, 255))
    random.seed(seed + 6)
    for _ in range(14):
        cx, cy = random.randint(0, w), random.randint(10, horizon_y-30)
        cw, ch = random.randint(110, 240), random.randint(20, 34)
        sd.ellipse([cx, cy, cx+cw, cy+ch], fill=(255, 255, 255, 95))
    sun_layer = sun_layer.filter(ImageFilter.GaussianBlur(3))
    canvas.paste(Image.alpha_composite(sky, sun_layer).convert("RGB"), (0, 0))

    block = 30
    cols, rows = w // block + 2, (h - horizon_y) // block + 2
    big_grass = grass_t.resize((block, block), Image.NEAREST)
    big_dirt = dirt.resize((block, block), Image.NEAREST)
    random.seed(seed)
    ground = Image.new("RGB", (cols*block, rows*block))
    for gy in range(rows):
        for gx in range(cols):
            ground.paste(big_dirt if random.random() < 0.05 else big_grass, (gx*block, gy*block))
    canvas.paste(ground, (0, horizon_y))

    haze = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(haze)
    for dy in range(-10, 10):
        hd.line([(0, horizon_y+dy), (w, horizon_y+dy)], fill=(225, 235, 255, int(70*(1-abs(dy)/10))))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), haze).convert("RGB")

    vign = Image.new("L", (w, h), 0)
    ImageDraw.Draw(vign).rectangle([0, int(h*0.72), w, h], fill=255)
    vign = vign.filter(ImageFilter.GaussianBlur(140))
    canvas = Image.composite(Image.new("RGB", (w, h), (8, 10, 8)), canvas, vign.point(lambda p: int(p*0.30)))

    canvas.save(out_path)

if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
