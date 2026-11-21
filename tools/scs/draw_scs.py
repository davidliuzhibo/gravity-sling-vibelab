"""Draw the South China Sea islands and reefs (东沙、西沙、中沙、南沙群岛) onto GravitySling's Earth texture.

uv run --with pillow --with numpy python -I draw_scs.py <osm.json> <earth.jpg in> <earth.jpg out> <preview dir>

Positions and shapes from OpenStreetMap (natural=coastline / natural=reef / place=islet, 2026-11-21). The texture is
2048 x 1024 equirectangular (1 px = 0.176 deg, about 19.5 km): every island is smaller than a pixel, so islands are
drawn as small sand-coloured dots at their true positions (a globe's usual symbol, about 1.6 px across) and the reefs
and atolls as their true outlines in shallow-water turquoise, never smaller than about one pixel. Coastal islands of
other mainlands inside the search boxes (Sabah, Sarawak) are left out.
"""
import json, math, sys
import numpy as np
from PIL import Image, ImageDraw

osm, src, dst, prev = sys.argv[1:5]
W, H = 2048, 1024
SS = 16                                   # supersampling per texture pixel
LON0, LON1, LAT0, LAT1 = 108.0, 120.0, 2.0, 23.0   # the work window (deg)

def px(lon, lat):                         # texture pixel (float) of a point
    return (lon + 180.0) / 360.0 * W, (90.0 - lat) / 180.0 * H

x0, y0 = px(LON0, LAT1); x1, y1 = px(LON1, LAT0)
X0, Y0, X1, Y1 = math.floor(x0), math.floor(y0), math.ceil(x1), math.ceil(y1)
cw, ch = (X1 - X0) * SS, (Y1 - Y0) * SS

def sub(lon, lat):                        # supersampled canvas coordinate
    x, y = px(lon, lat)
    return (x - X0) * SS, (y - Y0) * SS

def other_coast(lon, lat):
    """Islands of the Sabah and Sarawak coasts that the search boxes also caught."""
    return (lon > 115.8 and lat < 7.6) or (lon > 115.3 and lat < 6.7) or (lon > 113.2 and lat < 4.6)

reef = Image.new("L", (cw, ch), 0); dr = ImageDraw.Draw(reef)
land = Image.new("L", (cw, ch), 0); dl = ImageDraw.Draw(land)
rimg = Image.new("L", (cw, ch), 0); drim = ImageDraw.Draw(rimg)
DOT = 1.2 * SS                            # island dot radius: 1.2 px (about 2.4 px across)
RIM = 0.95 * SS                           # a ring of shallow-water blue round each island (as coral islands look)
REEF_MIN = 0.50 * SS                      # a reef is never smaller than about 1 px across
seen = []                                 # island dots already drawn (one dot per island, however many OSM pieces)
islands, reefs, skipped = [], [], 0

def ring_of(geom):
    return [(p["lon"], p["lat"]) for p in geom]

d = json.load(open(osm))
for e in d["elements"]:
    t = e.get("tags", {})
    kind = "reef" if t.get("natural") == "reef" else ("land" if t.get("natural") == "coastline" or t.get("place") in ("island", "islet") else None)
    if kind is None:
        continue
    rings = []
    if e["type"] == "way":
        rings.append(ring_of(e.get("geometry", [])))
    elif e["type"] == "relation":
        for m in e.get("members", []):
            if m.get("role") == "outer" and m.get("geometry"):
                rings.append(ring_of(m["geometry"]))
    elif e["type"] == "node":
        rings.append([(e["lon"], e["lat"])])
    for r in rings:
        if not r:
            continue
        clon = sum(p[0] for p in r) / len(r); clat = sum(p[1] for p in r) / len(r)
        if other_coast(clon, clat):
            skipped += 1
            continue
        pts = [sub(lon, lat) for lon, lat in r]
        cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
        if kind == "reef":
            reefs.append((clon, clat, t.get("name:zh") or t.get("name") or ""))
            if len(pts) >= 3:
                dr.polygon(pts, fill=255)
            dr.ellipse([cx - REEF_MIN, cy - REEF_MIN, cx + REEF_MIN, cy + REEF_MIN], fill=255)
        else:
            islands.append((clon, clat, t.get("name:zh") or t.get("name") or ""))
            if len(pts) >= 3:
                dl.polygon(pts, fill=255)
            # One dot per island: OSM often has an island as several pieces plus a name point within a kilometre or two.
            if any(abs(cx - sx) < 0.25 * SS and abs(cy - sy) < 0.25 * SS for sx, sy in seen):
                continue
            seen.append((cx, cy))
            dl.ellipse([cx - DOT, cy - DOT, cx + DOT, cy + DOT], fill=255)
            drim.ellipse([cx - DOT - RIM, cy - DOT - RIM, cx + DOT + RIM, cy + DOT + RIM], fill=255)

# Down to texture pixels: the share of each pixel covered.
def cover(img):
    a = np.asarray(img, dtype=np.float32) / 255.0
    return a.reshape(Y1 - Y0, SS, X1 - X0, SS).mean(axis=(1, 3))

rc, lc, mc = cover(reef), cover(land), cover(rimg)
base = Image.open(src).convert("RGB")
arr = np.asarray(base, dtype=np.float32).copy()
win = arr[Y0:Y1, X0:X1]
REEF_RGB = np.array([62, 138, 168], np.float32)     # shallow water over coral (lighter than the deep sea)
SAND_RGB = np.array([110, 150, 66], np.float32)     # land: the green of Hainan and the Philippines nearby
ra = np.clip(rc * 1.2, 0, 0.55)[..., None]           # reefs: soft, translucent
la = np.clip(lc * 1.6, 0, 1.0)[..., None]
RIM_RGB = np.array([96, 196, 214], np.float32)      # shallow water round the island: stands out on the deep sea
ma = np.clip(mc * 1.3, 0, 0.85)[..., None]
win = win * (1 - ra) + REEF_RGB * ra
win = win * (1 - ma) + RIM_RGB * ma
win = win * (1 - la) + SAND_RGB * la
arr[Y0:Y1, X0:X1] = win
out = Image.fromarray(np.clip(arr + 0.5, 0, 255).astype(np.uint8))
out.save(dst, quality=95, subsampling=0, optimize=True)

# Previews: the window, 6x, before and after side by side.
def crop6(im):
    return im.crop((X0 - 20, Y0, X1 + 20, Y1)).resize(((X1 - X0 + 40) * 6, (Y1 - Y0) * 6), Image.NEAREST)
a, b = crop6(base), crop6(out)
pv = Image.new("RGB", (a.width * 2 + 12, a.height), (255, 255, 255)); pv.paste(a, (0, 0)); pv.paste(b, (a.width + 12, 0))
pv.save(f"{prev}/scs_before_after.png")
crop6(out).save(f"{prev}/scs_after.png")
out.crop((X0 - 60, Y0 - 30, X1 + 60, Y1 + 20)).resize(((X1 - X0 + 120) * 3, (Y1 - Y0 + 50) * 3), Image.BILINEAR).save(f"{prev}/scs_view3x.png")
print("island dots", len(seen))
print(f"window px x {X0}..{X1} y {Y0}..{Y1}; islands {len(islands)}, reefs {len(reefs)}, skipped (other coasts) {skipped}")
print("named islands:", sorted({n for _, _, n in islands if n})[:80])
