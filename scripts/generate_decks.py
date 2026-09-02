#!/usr/bin/env python3
"""
Generate placeholder deck maps (CC0 original vectors) for MVP 20 ships.
Each deck is a 1200x1800 PNG (will be converted to webp/q82) with:
 - ship hull outline
 - deck number banner
 - venue blocks & cabin rows (schematic, not copied art)
 - license footer CC0 attribution
Safe to commit to MIT repo.
"""
import os, json, textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_DECKS = ROOT / "public" / "decks"
ASSETS_DECKS = ROOT / "app" / "src" / "main" / "assets" / "decks"

# 20 popular ships (covers ~70% sailings) — deck counts approximate real ships but schematic only
SHIPS = [
    {"id": "royal-caribbean__symphony-of-the-seas", "displayName": "Symphony of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["symphony ots","symphony","symphony of the seas","symphony of seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "royal-caribbean__wonder-of-the-seas", "displayName": "Wonder of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["wonder ots","wonder of the seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "royal-caribbean__icon-of-the-seas", "displayName": "Icon of the Seas", "line": "Royal Caribbean", "cls": "Icon", "aliases": ["icon ots","icon of the seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "royal-caribbean__oasis-of-the-seas", "displayName": "Oasis of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["oasis ots","oasis of the seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "royal-caribbean__utopia-of-the-seas", "displayName": "Utopia of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["utopia ots","utopia of the seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "carnival__mardi-gras", "displayName": "Mardi Gras", "line": "Carnival", "cls": "Excel", "aliases": ["mardi gras","carnival mardi gras"], "deckCount": 10, "decks": [5,6,7,8,9,10,11,12,15,16]},
    {"id": "carnival__celebration", "displayName": "Carnival Celebration", "line": "Carnival", "cls": "Excel", "aliases": ["celebration","carnival celebration"], "deckCount": 10, "decks": [5,6,7,8,9,10,11,12,15,16]},
    {"id": "carnival__vista", "displayName": "Carnival Vista", "line": "Carnival", "cls": "Vista", "aliases": ["vista","carnival vista"], "deckCount": 10, "decks": [3,4,5,6,7,8,9,10,11,14]},
    {"id": "msc__world-europa", "displayName": "MSC World Europa", "line": "MSC", "cls": "World", "aliases": ["world europa","msc world europa"], "deckCount": 11, "decks": [5,6,7,8,9,14,15,16,18,19,20]},
    {"id": "msc__seashore", "displayName": "MSC Seashore", "line": "MSC", "cls": "Seaside", "aliases": ["seashore","msc seashore"], "deckCount": 10, "decks": [5,6,7,8,9,10,15,16,18,19]},
    {"id": "ncl__prima", "displayName": "Norwegian Prima", "line": "Norwegian", "cls": "Prima", "aliases": ["prima","norwegian prima","ncl prima"], "deckCount": 10, "decks": [5,6,7,8,9,11,12,15,16,17]},
    {"id": "ncl__encore", "displayName": "Norwegian Encore", "line": "Norwegian", "cls": "Breakaway Plus", "aliases": ["encore","norwegian encore","ncl encore"], "deckCount": 11, "decks": [5,6,7,8,9,11,12,14,15,16,17]},
    {"id": "princess__discovery-princess", "displayName": "Discovery Princess", "line": "Princess", "cls": "Royal", "aliases": ["discovery princess","princess discovery"], "deckCount": 10, "decks": [5,6,7,8,9,10,14,15,16,17]},
    {"id": "princess__enchanted-princess", "displayName": "Enchanted Princess", "line": "Princess", "cls": "Royal", "aliases": ["enchanted princess"], "deckCount": 10, "decks": [5,6,7,8,9,10,14,15,16,17]},
    {"id": "celebrity__edge", "displayName": "Celebrity Edge", "line": "Celebrity", "cls": "Edge", "aliases": ["edge","celebrity edge"], "deckCount": 10, "decks": [5,6,7,8,9,10,12,14,15,16]},
    {"id": "celebrity__apex", "displayName": "Celebrity Apex", "line": "Celebrity", "cls": "Edge", "aliases": ["apex","celebrity apex"], "deckCount": 10, "decks": [5,6,7,8,9,10,12,14,15,16]},
    {"id": "royal-caribbean__harmony-of-the-seas", "displayName": "Harmony of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["harmony ots","harmony of the seas"], "deckCount": 12, "decks": list(range(3,15))},
    {"id": "carnival__jubilee", "displayName": "Carnival Jubilee", "line": "Carnival", "cls": "Excel", "aliases": ["jubilee","carnival jubilee"], "deckCount": 10, "decks": [5,6,7,8,9,10,11,12,15,16]},
    {"id": "msc__seascape", "displayName": "MSC Seascape", "line": "MSC", "cls": "Seaside EVO", "aliases": ["seascape","msc seascape"], "deckCount": 10, "decks": [5,6,7,8,9,10,15,16,18,19]},
    {"id": "ncl__joy", "displayName": "Norwegian Joy", "line": "Norwegian", "cls": "Breakaway Plus", "aliases": ["joy","norwegian joy","ncl joy"], "deckCount": 11, "decks": [5,6,7,8,9,11,12,14,15,16,17]},
]

# Venue templates per deck number (schematic labels)
VENUES = {
    3: ["Medical Center","Crew Quarters","Gangway","Luggage"],
    4: ["Crew","Laundry","Gangway"],
    5: ["Promenade","Shops","Cafe","Dining Room","Atrium"],
    6: ["Cabins 6200-6400","Cabins 6100-6300","Laundromat","Cabins"],
    7: ["Cabins 7200-7400","Balcony Cabins","Cabins"],
    8: ["Cabins 8200-8500","Balcony","Cabins"],
    9: ["Lido Pool","Buffet","Grill","Pool Bar"],
    10: ["Spa","Fitness","Jogging Track","Sun Deck"],
    11: ["Sports Court","Mini Golf","Waterslides","Kids Club"],
    12: ["Observation Lounge","Library","Card Room","Chapel"],
    14: ["Theater","Comedy Club","Nightclub","Casino"],
    15: ["Pool Deck","Stage","Outdoor Movies","Cabana"],
    16: ["Sun Deck","Serenity","Hot Tubs","Suites"],
    17: ["Bridge","Suites","Helipad View","Sky Lounge"],
    18: ["Aqua Park","Slides","Rope Course"],
    19: ["Suite Deck","Yacht Club","Private Pool"],
    20: ["Sky Deck","Observation","Helipad"],
}

# Colors - teal/navy theme matching app
BG = (250, 253, 252)
HULL_FILL = (237, 244, 243)
HULL_STROKE = (0, 106, 96)
HEADER_BG = (0, 106, 96)
HEADER_FG = (255,255,255)
BLOCK_FILL = (116, 248, 231)
BLOCK_STROKE = (74, 99, 95)
CABIN_FILL = (210, 235, 233)
TEXT_DARK = (11, 30, 59)
MUTED = (90, 120, 115)

W, H = 1200, 1800

def get_font(size, bold=False):
    # Try to find a reasonable font, fallback to default
    try:
        # DejaVu is available on many systems; fallback to default
        if bold:
            return ImageFont.truetype("DejaVuSans-Bold.ttf", size)
        return ImageFont.truetype("DejaVuSans.ttf", size)
    except:
        try:
            if bold:
                return ImageFont.truetype("arialbd.ttf", size)
            return ImageFont.truetype("arial.ttf", size)
        except:
            return ImageFont.load_default()

def draw_deck(base_dir: Path, ship: dict, deck_num: int):
    ship_dir = base_dir / "ships" / ship["id"]
    ship_dir.mkdir(parents=True, exist_ok=True)
    fname = f"deck-{deck_num:02d}.webp"
    out = ship_dir / fname

    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    font_title = get_font(54, bold=True)
    font_sub = get_font(28)
    font_block = get_font(22, bold=True)
    font_small = get_font(18)
    font_footer = get_font(16)

    # Header banner
    d.rectangle([0,0,W,130], fill=HEADER_BG)
    d.text((32, 22), ship["displayName"], fill=HEADER_FG, font=font_title)
    d.text((32, 78), f"Deck {deck_num:02d}  •  {VENUES.get(deck_num, ['Cabins & Venues'])[0]}  •  {ship['line']} {ship['cls']}", fill=(200,255,245), font=font_sub)
    # hull outline schematic
    hull_margin = 40
    hull_top = 160
    hull_bottom = H - 110
    # Hull shape: rounded bow top, flat stern bottom-ish but stylized
    # Simple: rectangle with bow curve at top
    hull_w = W - hull_margin*2
    hull_h = hull_bottom - hull_top
    # outer hull
    # bow ellipse at top
    bow_h = 90
    # hull rect
    d.rectangle([hull_margin, hull_top+bow_h, hull_margin+hull_w, hull_bottom], fill=HULL_FILL, outline=HULL_STROKE, width=4)
    # bow half ellipse
    d.ellipse([hull_margin, hull_top, hull_margin+hull_w, hull_top+bow_h*2], fill=HULL_FILL, outline=HULL_STROKE, width=4)
    # cover seam line where rect meets ellipse (remove line)
    d.rectangle([hull_margin+2, hull_top+bow_h, hull_margin+hull_w-2, hull_top+bow_h+6], fill=HULL_FILL)
    # bow tip marker
    d.text((W//2-18, hull_top+22), "▲ BOW", fill=HULL_STROKE, font=get_font(16, bold=True))
    d.text((W//2-40, hull_bottom+8), "▼ STERN", fill=MUTED, font=get_font(16, bold=True))

    # Venue blocks inside hull
    inner_l = hull_margin + 22
    inner_r = hull_margin + hull_w - 22
    inner_t = hull_top + bow_h + 30
    inner_b = hull_bottom - 24
    inner_w = inner_r - inner_l
    inner_h = inner_b - inner_t

    venues = VENUES.get(deck_num, ["Cabins","Cabins","Lounge"])
    # Layout: if cabin-heavy deck, draw cabin rows; else venue blocks
    is_cabin = "Cabin" in venues[0] or deck_num in [6,7,8]

    if is_cabin:
        # cabin deck: central corridor + cabin rows port/starboard
        corridor_w = 80
        corridor_x = inner_l + (inner_w - corridor_w)//2
        # corridor
        d.rectangle([corridor_x, inner_t, corridor_x+corridor_w, inner_b], fill=(255,255,255), outline=(180,210,205), width=2)
        d.text((corridor_x+8, inner_t+8), "Corridor", fill=MUTED, font=get_font(14))
        # port cabins
        cabin_h = 36
        rows = max(1, (inner_h - 40)//(cabin_h+6))
        port_x = inner_l + 8
        star_x = corridor_x + corridor_w + 8
        cabin_w_port = corridor_x - port_x - 8
        cabin_w_star = inner_r - star_x - 8
        for i in range(rows):
            y = inner_t + 34 + i*(cabin_h+6)
            if y + cabin_h > inner_b - 4: break
            # port
            d.rectangle([port_x, y, port_x+cabin_w_port, y+cabin_h], fill=CABIN_FILL, outline=BLOCK_STROKE, width=1)
            # starboard
            d.rectangle([star_x, y, star_x+cabin_w_star, y+cabin_h], fill=CABIN_FILL, outline=BLOCK_STROKE, width=1)
            if i % 4 == 0:
                d.text((port_x+6, y+10), f"{deck_num}{100+i*2:03d}", fill=TEXT_DARK, font=get_font(13))
                d.text((star_x+6, y+10), f"{deck_num}{101+i*2:03d}", fill=TEXT_DARK, font=get_font(13))
        # venue labels at ends
        d.rectangle([inner_l+8, inner_t+2, inner_r-8, inner_t+28], fill=BLOCK_FILL, outline=BLOCK_STROKE, width=1)
        d.text((inner_l+16, inner_t+8), venues[0][:44], fill=TEXT_DARK, font=font_small)
        if len(venues)>1:
            d.rectangle([inner_l+8, inner_b-28, inner_r-8, inner_b-4], fill=BLOCK_FILL, outline=BLOCK_STROKE, width=1)
            d.text((inner_l+16, inner_b-22), venues[-1][:44], fill=TEXT_DARK, font=font_small)
    else:
        # venue deck: grid of blocks + pool shape + text
        # split into 2 columns for most decks, or full width for Lido
        if deck_num in [9,15]:
            # Lido: large pool center
            d.ellipse([inner_l+inner_w*0.22, inner_t+20, inner_l+inner_w*0.78, inner_t+260], fill=(180,220,255), outline=BLOCK_STROKE, width=2)
            d.text((inner_l+inner_w//2-44, inner_t+118), "POOL", fill=TEXT_DARK, font=get_font(26, bold=True))
            # buffet blocks top
            d.rectangle([inner_l+8, inner_t+290, inner_r-8, inner_t+360], fill=BLOCK_FILL, outline=BLOCK_STROKE, width=2)
            d.text((inner_l+20, inner_t+312), venues[1] if len(venues)>1 else "Buffet", fill=TEXT_DARK, font=font_block)
            # bar / grill side blocks
            cols = 2
            block_w = (inner_w - 24)//2
            for ci, name in enumerate(venues[2:4]):
                x = inner_l + 8 + ci*(block_w+8)
                d.rectangle([x, inner_t+376, x+block_w, inner_t+450], fill=(255,255,255), outline=BLOCK_STROKE, width=2)
                d.text((x+12, inner_t+398), name[:18], fill=TEXT_DARK, font=font_block)
            # sun loungers rows
            for row in range(5):
                y = inner_t + 480 + row*58
                if y > inner_b - 60: break
                for col in range(6):
                    lx = inner_l + 20 + col*( (inner_w-40)//6 + 4)
                    d.rectangle([lx, y, lx+ (inner_w-60)//6, y+32], fill=CABIN_FILL, outline=(180,210,205), width=1)
            d.text((inner_l+12, inner_b-36), "Sun Loungers • Bar • Stage", fill=MUTED, font=font_small)
        else:
            # generic venue deck: 2x3 grid
            gap = 12
            cols = 2
            rows = 3
            bw = (inner_w - gap*(cols+1))//cols
            bh = (inner_h - gap*(rows+1) - 40)//rows
            idx = 0
            for r in range(rows):
                for c in range(cols):
                    if idx >= len(venues): break
                    x = inner_l + gap + c*(bw+gap)
                    y = inner_t + gap + r*(bh+gap)
                    fill = BLOCK_FILL if r==0 else (255,255,255)
                    d.rectangle([x,y,x+bw,y+bh], fill=fill, outline=BLOCK_STROKE, width=2)
                    # center text
                    label = venues[idx]
                    # wrap
                    # simple truncate
                    d.text((x+14, y+ bh//2 - 14), label[:22], fill=TEXT_DARK, font=font_block)
                    d.text((x+14, y+ bh//2 + 10), f"Deck {deck_num:02d}", fill=MUTED, font=get_font(15))
                    idx+=1
            # filler cabins if venues short
            if idx < cols*rows:
                x = inner_l + gap + (idx%cols)*(bw+gap)
                y = inner_t + gap + (idx//cols)*(bh+gap)
                d.rectangle([x,y,x+bw,y+bh], fill=CABIN_FILL, outline=(180,210,205), width=1)
                d.text((x+14,y+14), "Cabins / Open", fill=MUTED, font=font_small)

    # footer
    d.rectangle([0, H-58, W, H], fill=(11,30,59))
    d.text((20, H-38), "CC0-1.0  •  Original schematic, not affiliated with cruise line  •  Cruise Planner • Offline", fill=(160,180,190), font=font_footer)
    # scale marker
    d.text((W-160, H-38), f"Deck {deck_num:02d}", fill=(255,255,255), font=get_font(18, bold=True))

    # Save as webp q82 + png fallback? Save webp primary
    try:
        img.save(out, "WEBP", quality=82, method=4)
    except Exception as e:
        # fallback png if webp not supported
        out_png = out.with_suffix(".png")
        img.save(out_png, "PNG")
        print(f"webp fail {e}, saved png {out_png}")
        return out_png
    return out

def make_thumb(base_dir: Path, ship: dict):
    # simple thumb: header + hull silhouette
    ship_dir = base_dir / "ships" / ship["id"]
    out = ship_dir / "thumb.webp"
    if out.exists(): return out
    img = Image.new("RGB", (400, 260), HEADER_BG)
    d = ImageDraw.Draw(img)
    font_b = get_font(20, bold=True)
    font_s = get_font(14)
    d.text((14,14), ship["displayName"][:28], fill=(255,255,255), font=font_b)
    d.text((14,40), f"{ship['line']} • {len(ship['decks'])} decks", fill=(200,255,245), font=font_s)
    # mini hull
    d.ellipse([20,70,380,120], fill=HULL_FILL, outline=(255,255,255), width=2)
    d.rectangle([20,95,380,220], fill=HULL_FILL, outline=(255,255,255), width=2)
    for i in range(3):
        y = 130 + i*30
        d.rectangle([36, y, 364, y+16], fill=BLOCK_FILL, outline=BLOCK_STROKE, width=1)
    d.text((14,236), "CC0 deck plan", fill=(160,180,190), font=get_font(11))
    try:
        img.save(out, "WEBP", quality=80)
    except:
        out = out.with_suffix(".png")
        img.save(out, "PNG")
    return out

def main():
    for base in [PUBLIC_DECKS, ASSETS_DECKS]:
        base.mkdir(parents=True, exist_ok=True)
    manifest = {"version": 1, "updatedAt": "2026-08-28T00:00:00Z", "ships": []}
    total_bytes = 0
    for ship in SHIPS:
        decks_info = []
        for deck_num in ship["decks"]:
            out = draw_deck(PUBLIC_DECKS, ship, deck_num)
            # also copy to assets decks for bundled fallback? we will copy same image to assets
            # ensure asset copy exists (as webp)
            asset_path = ASSETS_DECKS / "ships" / ship["id"] / out.name
            asset_path.parent.mkdir(parents=True, exist_ok=True)
            # copy bytes
            try:
                import shutil; shutil.copy2(out, asset_path)
            except: pass
            sz = out.stat().st_size if out.exists() else 0
            total_bytes += sz
            # single source of truth base url is Firebase Hosting /decks/
            decks_info.append({
                "number": deck_num,
                "name": f"Deck {deck_num:02d} - {VENUES.get(deck_num, ['Deck'])[0]}",
                "file": out.name,
                "width": W,
                "height": H,
                "bytes": sz,
                "license": "CC0-1.0"
            })
            print(f"Generated {ship['id']} deck {deck_num} -> {out.name} {sz>>10}KB")
        thumb = make_thumb(PUBLIC_DECKS, ship)
        # copy thumb to assets too
        try:
            import shutil; shutil.copy2(thumb, ASSETS_DECKS / "ships" / ship["id"] / thumb.name)
        except: pass
        entry = {
            "id": ship["id"],
            "displayName": ship["displayName"],
            "line": ship["line"],
            "class": ship["cls"],
            "aliases": ship["aliases"],
            "deckCount": len(ship["decks"]),
            "imageBase": f"https://cruise-app-2026.web.app/decks/ships/{ship['id']}",
            "thumb": thumb.name,
            "decks": decks_info,
            "attribution": "Original CC0 schematic by Cruise Planner — not affiliated with cruise line. Drawn from public facts, own expression.",
            "externalUrl": None
        }
        manifest["ships"].append(entry)
    # also add a few link-only entries for coverage without images (to reach 'every ship' perception)
    link_only = [
        {"id": "royal-caribbean__allure-of-the-seas", "displayName": "Allure of the Seas", "line": "Royal Caribbean", "cls": "Oasis", "aliases": ["allure ots"], "externalUrl": "https://www.royalcaribbean.com/cruise-ships/allure-of-the-seas", "decks": []},
        {"id": "carnival__horizon", "displayName": "Carnival Horizon", "line": "Carnival", "cls": "Vista", "aliases": ["horizon"], "externalUrl": "https://www.carnival.com/cruise-ships/carnival-horizon", "decks": []},
        {"id": "ncl__breakaway", "displayName": "Norwegian Breakaway", "line": "Norwegian", "cls": "Breakaway", "aliases": ["breakaway"], "externalUrl": "https://www.ncl.com/cruise-ships/norwegian-breakaway", "decks": []},
        {"id": "msc__euribia", "displayName": "MSC Euribia", "line": "MSC", "cls": "Meraviglia Plus", "aliases": ["euribia"], "externalUrl": "https://www.msccruises.com/ships/msc-euribia", "decks": []},
        {"id": "princess__sky-princess", "displayName": "Sky Princess", "line": "Princess", "cls": "Royal", "aliases": ["sky princess"], "externalUrl": "https://www.princess.com/ships-and-experience/ships/sky-princess/", "decks": []},
    ]
    for lo in link_only:
        manifest["ships"].append({
            "id": lo["id"], "displayName": lo["displayName"], "line": lo["line"], "class": lo["cls"],
            "aliases": lo["aliases"], "deckCount": 0, "imageBase": f"https://cruise-app-2026.web.app/decks/ships/{lo['id']}",
            "thumb": None, "decks": [], "attribution": "Link to official cruise line deck plan — requires internet.", "externalUrl": lo["externalUrl"]
        })
    # sort ships by line + name
    manifest["ships"].sort(key=lambda s: (s["line"], s["displayName"]))
    out_json = PUBLIC_DECKS / "manifest.json"
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    # also copy to assets
    with open(ASSETS_DECKS / "manifest.json", "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    # also copy to root decks/ for raw github fallback if requested
    root_decks = ROOT / "decks"
    try:
        import shutil
        if root_decks.exists(): shutil.rmtree(root_decks)
        shutil.copytree(PUBLIC_DECKS, root_decks)
    except Exception as e:
        print(f"root copy warn: {e}")
    print(f"\nDone: {len(manifest['ships'])} ships, {total_bytes>>20} MB total, manifest -> {out_json}")
    # write stats
    with open(PUBLIC_DECKS / "README.md","w",encoding="utf-8") as f:
        f.write(f"# Deck Maps Catalog\n\nCC0-1.0 original schematics — not affiliated with any cruise line.\nGenerated 2026-08-28. Version {manifest['version']}. Ships: {len(manifest['ships'])}.\nTotal deck images: {sum(len(s['decks']) for s in manifest['ships'])}.\n\nHosted via Firebase Hosting `https://cruise-app-2026.web.app/decks/` and raw GitHub fallback.\n\n## License\nEach `webp` is CC0-1.0. Manifest is CC0. No cruise line art is copied.\n")

if __name__ == "__main__":
    main()
