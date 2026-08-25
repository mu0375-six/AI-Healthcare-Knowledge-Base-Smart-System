"""Draw a high-contrast metformin carton for the OCR demo."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

out = Path("frontend/public/demo/metformin-label.jpg")
out.parent.mkdir(parents=True, exist_ok=True)

img = Image.new("RGB", (900, 560), "#f4efe4")
d = ImageDraw.Draw(img)
d.rectangle((18, 18, 881, 541), outline="#17342b", width=4)
d.rectangle((18, 18, 881, 110), fill="#2c5648")

try:
    title = ImageFont.truetype("msyhbd.ttc", 42)
    body = ImageFont.truetype("msyh.ttc", 28)
    small = ImageFont.truetype("msyh.ttc", 20)
    en = ImageFont.truetype("arial.ttf", 26)
except OSError:
    title = body = small = en = ImageFont.load_default()

d.text((40, 42), "盐酸二甲双胍片", font=title, fill="#f4efe4")
d.text((40, 140), "通用名称：盐酸二甲双胍片", font=body, fill="#161d1a")
d.text((40, 190), "Metformin Hydrochloride Tablets", font=en, fill="#3b4540")
d.text((40, 250), "规格：0.5 g  ×  20 片", font=body, fill="#161d1a")
d.text((40, 300), "适应症：2 型糖尿病", font=body, fill="#161d1a")
d.text((40, 350), "用法：口服   请遵医嘱", font=body, fill="#161d1a")
d.text((40, 410), "注意：肝肾功能不全者慎用", font=body, fill="#9b3f24")
d.text((40, 470), "OTC 标识仅供演示，不能替代说明书", font=small, fill="#6d7671")
img.save(out, quality=92)
print(out.resolve(), out.stat().st_size)
