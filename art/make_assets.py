"""Turn the Blender renders into the in-app image and the README hero.

python3 art/make_assets.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ART = Path(__file__).parent
ROOT = ART.parent
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
SERIF_SMALL = SERIF

SWAMP_TOP = (20, 30, 21)
SWAMP_BOTTOM = (38, 52, 33)
BUBBLE = (244, 238, 222)
INK = (34, 38, 28)
ROBE = (139, 123, 90)


def trimmed(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGBA")
    return img.crop(img.getbbox())


def app_image() -> None:
    bust = trimmed(ART / "yoda_bust.png")
    bust.thumbnail((720, 720), Image.LANCZOS)
    out = ROOT / "app/src/main/res/drawable-nodpi/yoda.webp"
    out.parent.mkdir(parents=True, exist_ok=True)
    bust.save(out, "WEBP", quality=88, method=6)
    print(out, bust.size, out.stat().st_size // 1024, "KB")


def wrap(draw: ImageDraw.ImageDraw, text: str, font, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=font) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


def hero() -> None:
    w, h = 1280, 720
    bg = Image.new("RGB", (w, h))
    px = bg.load()
    for y in range(h):
        t = y / h
        row = tuple(round(a + (b - a) * t) for a, b in zip(SWAMP_TOP, SWAMP_BOTTOM))
        for x in range(w):
            px[x, y] = row
    glow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(glow).ellipse((760, 170, 1220, 700), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    bg.paste(Image.new("RGB", (w, h), (120, 170, 80)), mask=glow)

    yoda = trimmed(ART / "yoda_full.png")
    yoda.thumbnail((520, 660), Image.LANCZOS)
    shadow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(shadow).ellipse((800, 660, 1180, 705), fill=110)
    bg.paste((10, 15, 10), mask=shadow.filter(ImageFilter.GaussianBlur(14)))
    yx = 990 - yoda.width // 2
    bg.paste(yoda, (yx, 690 - yoda.height), yoda)

    d = ImageDraw.Draw(bg)
    # The tail ends just left of his mouth, about 21% down the figure.
    mouth = (yx + round(yoda.width * 0.36), 690 - yoda.height + round(yoda.height * 0.2))
    box = (70, 90, 740, 360)
    d.rounded_rectangle(box, radius=38, fill=BUBBLE)
    d.polygon([(box[2] - 30, 170), (box[2] - 30, 250), mouth], fill=BUBBLE)
    big = ImageFont.truetype(SERIF, 50)
    small = ImageFont.truetype(SERIF_SMALL, 26)
    y = box[1] + 44
    d.text((box[0] + 48, y), "You type:", font=small, fill=ROBE)
    y += 40
    d.text((box[0] + 48, y), "“Mor er eldre enn far.”", font=small, fill=(90, 90, 80))
    y += 64
    for line in wrap(d, "Eldre enn far er mor.", big, box[2] - box[0] - 96):
        d.text((box[0] + 48, y), line, font=big, fill=INK)
        y += 62
    title = ImageFont.truetype(SERIF, 34)
    d.text((72, 470), "Yoda Translator", font=title, fill=(214, 226, 190))
    d.text(
        (72, 518),
        "English and Norwegian, offline, on your phone.",
        font=ImageFont.truetype(SERIF, 24),
        fill=(170, 186, 150),
    )
    out = ART / "readme-hero.png"
    bg.save(out, optimize=True)
    print(out, out.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    app_image()
    hero()
