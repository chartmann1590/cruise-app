import asyncio, edge_tts, subprocess, pathlib, os
PROJECT_ROOT = pathlib.Path(r"H:\cruise-app")
PUBLIC = PROJECT_ROOT / "public"
PROMO_DIR = PROJECT_ROOT / "promo"
TMP_DIR = PROMO_DIR / "tmp"
OUTPUT = PUBLIC / "promo.mp4"
POSTER = PUBLIC / "promo-poster.jpg"
STORE_SHOTS = PROJECT_ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"
PROMO_DIR.mkdir(exist_ok=True)
TMP_DIR.mkdir(parents=True, exist_ok=True)

def make_ending_card():
    """Generate 1920x1080 ending card with website + GitHub. Uses PIL, no network."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        print("PIL not available — skipping ending card gen (will use existing if present)")
        return TMP_DIR / "ending_card.png"
    W, H = 1920, 1080
    BG = "#0B1E3B"; TEAL = "#0EA5A3"; WHITE = "#FFFFFF"; MUTED = "#94A3B8"; MUTED_LIGHT = "#CBD5E1"
    def load_font(sz, bold=False):
        cands = ["C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
                 "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
                 "C:/Windows/Fonts/arial.ttf"]
        for p in cands:
            if os.path.exists(p):
                try: return ImageFont.truetype(p, sz)
                except: pass
        return ImageFont.load_default()
    out = TMP_DIR / "ending_card.png"
    img = Image.new('RGB', (W,H), BG)
    draw = ImageDraw.Draw(img, "RGBA")
    for r, a in [(650,18),(500,14),(350,10)]:
        cx, cy = W//2, H//2-60
        draw.ellipse([cx-r,cy-r,cx+r,cy+r], fill=(14,165,163,a))
    icon_path = PUBLIC / "cruiseloom-icon.png"
    icon_size = 168
    try:
        icon = Image.open(icon_path).convert("RGBA").resize((icon_size,icon_size), Image.LANCZOS)
        ix, iy = (W-icon_size)//2, 138
        shadow = Image.new("RGBA",(icon_size+20,icon_size+20),(0,0,0,0))
        ImageDraw.Draw(shadow).ellipse([0,0,icon_size+20,icon_size+20], fill=(0,0,0,40))
        img.paste(shadow,(ix-10,iy-6),shadow)
        img.paste(icon,(ix,iy),icon)
        icon_bottom = iy+icon_size
    except Exception as e:
        print(f"ending card icon failed: {e}")
        icon_bottom = 270
    f_title = load_font(92, True); f_tag = load_font(36, False); f_lbl = load_font(22, True)
    f_url = load_font(34, True); f_small = load_font(24, False); f_tiny = load_font(20, False)
    title_y = icon_bottom + 28
    draw.text((W//2,title_y),"CruiseLoom",font=f_title,fill=WHITE,anchor="mm")
    tag_y = title_y+62
    draw.text((W//2,tag_y),"Your cruise, perfectly planned  \u2022  Works offline",font=f_tag,fill=MUTED_LIGHT,anchor="mm")
    div_y = tag_y+48; div_w=520
    draw.line([(W//2-div_w//2,div_y),(W//2+div_w//2,div_y)],fill=(255,255,255,30),width=1)
    draw.ellipse([W//2-4,div_y-4,W//2+4,div_y+4],fill=TEAL)
    cards_y = div_y+44; card_h=84; gap=24; card_w=520; radius=18
    c1x = W//2 - card_w - gap//2; c2x = W//2 + gap//2
    c1=[c1x,cards_y,c1x+card_w,cards_y+card_h]; c2=[c2x,cards_y,c2x+card_w,cards_y+card_h]
    for c, accent in [(c1, TEAL),(c2, "#0B1E3B")]:
        draw.rounded_rectangle(c,radius=radius,fill=(255,255,255,255),outline=(14,165,163,40),width=1)
        draw.rounded_rectangle([c[0],c[1],c[0]+7,c[3]],radius=4,fill=accent)
    draw.text((c1[0]+28,cards_y+18),"WEBSITE",font=f_lbl,fill="#64748B",anchor="lm")
    draw.text((c2[0]+28,cards_y+18),"GITHUB",font=f_lbl,fill="#64748B",anchor="lm")
    draw.text((c1[0]+28,cards_y+52),"cruise-app-2026.web.app",font=f_url,fill="#0B1E3B",anchor="lm")
    draw.text((c2[0]+28,cards_y+52),"github.com/chartmann1590/cruise-app",font=f_url,fill="#0B1E3B",anchor="lm")
    try:
        f_arr=load_font(28,False)
        draw.text((c1[2]-32,cards_y+card_h//2),"\u2197",font=f_arr,fill="#94A3B8",anchor="mm")
        draw.text((c2[2]-32,cards_y+card_h//2),"\u2197",font=f_arr,fill="#94A3B8",anchor="mm")
    except: pass
    draw.text((W//2,cards_y+card_h+38),"Download for Android \u2022 Android 8+ \u2022 Free \u2022 No account required",font=f_small,fill=MUTED,anchor="mm")
    draw.text((W//2,H-56),"\u00a9 2026 CruiseLoom \u2022 cruise-app-2026.web.app/privacy",font=f_tiny,fill="#475569",anchor="mm")
    draw.line([(0,H-3),(W,H-3)],fill=TEAL,width=3)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out,"PNG")
    print(f"Ending card generated: {out} {out.stat().st_size} bytes")
    return out

segments = [
    {"image": STORE_SHOTS / "01_01_welcome.png", "text": "Welcome aboard CruiseLoom, your offline-first companion for a smoother vacation at sea.", "caption": "Welcome aboard CruiseLoom"},
    {"image": STORE_SHOTS / "02_02_cruise_setup.png", "text": "Create your cruise, name your ship, and choose your sail dates in seconds.", "caption": "Set your ship and sail dates"},
    {"image": STORE_SHOTS / "03_06_dashboard.png", "text": "See every sea day, port day, and upcoming plan together on one calm dashboard.", "caption": "Your whole voyage at a glance"},
    {"image": STORE_SHOTS / "04_07_port_list.png", "text": "Add real port stops and keep arrival details organized without juggling notes.", "caption": "Organize every port stop"},
    {"image": STORE_SHOTS / "05_08_weather.png", "text": "Check live port weather, then keep recent forecasts cached for spotty connections at sea.", "caption": "Port weather, cached for the voyage"},
    {"image": STORE_SHOTS / "06_09_day_detail.png", "text": "Plan dinners, excursions, and reminders day by day, so the moments you care about stay on schedule.", "caption": "Dinners, excursions, and reminders"},
    {"image": STORE_SHOTS / "07_10_party.png", "text": "Connect your travel party nearby using Bluetooth and Wi-Fi Direct, even without ship internet.", "caption": "Nearby party chat without ship internet"},
    {"image": STORE_SHOTS / "08_11_my_qr.png", "text": "Share a local QR code to add a shipmate quickly.", "caption": "Share a QR code to add shipmates"},
]
VOICE = "en-US-AriaNeural"
RATE = "+8%"
ENDING_TEXT = "Find CruiseLoom at cruise-app-2026 dot web dot app, and on GitHub at github dot com slash chartmann1590 slash cruise-app."
ENDING_CAPTION = "cruise-app-2026.web.app  \u2022  github.com/chartmann1590/cruise-app"
ENDING_DUR = 5.0
async def gen_audio(text, out_path):
    communicate = edge_tts.Communicate(text, voice=VOICE, rate=RATE)
    await communicate.save(str(out_path))
def probedur(mp3):
    result = subprocess.run(["ffprobe","-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1", str(mp3)], capture_output=True, text=True)
    try:
        return float(result.stdout.strip())
    except:
        print(f"ffprobe failed for {mp3}: {result.stdout} {result.stderr}")
        return 3.0
async def main():
    ending_card = make_ending_card()
    print(f"Generating {len(segments)} voiceover segments + ending card with {VOICE} rate {RATE}...")
    audio_files = []
    for i, seg in enumerate(segments):
        out = TMP_DIR / f"seg{i:02d}.mp3"
        if out.exists():
            out.unlink()
        print(f"[{i+1}/{len(segments)}] {seg['caption'][:40]}...")
        await gen_audio(seg["text"], out)
        await asyncio.sleep(0.3)
        dur = probedur(out)
        seg["audio"] = out
        seg["dur"] = dur
        print(f"  -> {dur:.2f}s")
        audio_files.append(out)
    print(f"[{len(segments)+1}/{len(segments)+1}] {ENDING_CAPTION[:40]} (ending card)...")
    ending_mp3 = TMP_DIR / f"seg{len(segments):02d}_ending.mp3"
    if ending_mp3.exists():
        ending_mp3.unlink()
    await gen_audio(ENDING_TEXT, ending_mp3)
    await asyncio.sleep(0.3)
    ending_dur_raw = probedur(ending_mp3)
    ending_dur = max(ENDING_DUR, ending_dur_raw)
    print(f"  -> ending voice {ending_dur_raw:.2f}s, hold {ending_dur:.2f}s")
    audio_files.append(ending_mp3)
    if ending_dur > ending_dur_raw + 0.05:
        silence_mp3 = TMP_DIR / "ending_silence.mp3"
        pad = ending_dur - ending_dur_raw
        cmd = ["ffmpeg","-y","-f","lavfi","-i","anullsrc=r=24000:cl=mono","-t",f"{pad:.3f}","-c:a","libmp3lame","-q:a","2", str(silence_mp3)]
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        audio_files.append(silence_mp3)
        ending_audio_dur = ending_dur
    else:
        ending_audio_dur = ending_dur_raw
    list_path = TMP_DIR / "audio_concat.txt"
    with open(list_path, "w", encoding="utf-8") as f:
        for p in audio_files:
            f.write(f"file '{p.as_posix()}'\n")
    voiceover = PROMO_DIR / "voiceover.mp3"
    cmd = ["ffmpeg","-y","-f","concat","-safe","0","-i", str(list_path), "-c:a","libmp3lame","-q:a","2", str(voiceover)]
    print("Concatenating audio...")
    subprocess.run(cmd, check=True)
    total_dur = probedur(voiceover)
    print(f"Total voiceover duration: {total_dur:.2f}s")
    srt_path = PROMO_DIR / "subs.srt"
    with open(srt_path, "w", encoding="utf-8") as f:
        cur = 0.0
        for idx, seg in enumerate(segments):
            start = cur
            end = cur + seg["dur"]
            def fmt(t):
                h = int(t//3600)
                m = int((t%3600)//60)
                s = int(t%60)
                ms = int((t - int(t))*1000)
                return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"
            f.write(f"{idx+1}\n")
            f.write(f"{fmt(start)} --> {fmt(end)}\n")
            f.write(f"{seg['caption']}\n\n")
            cur = end
        def fmt2(t):
            h = int(t//3600); m = int((t%3600)//60); s = int(t%60); ms = int((t - int(t))*1000)
            return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"
        f.write(f"{len(segments)+1}\n")
        f.write(f"{fmt2(cur)} --> {fmt2(cur+ending_audio_dur)}\n")
        f.write(f"{ENDING_CAPTION}\n\n")
    print(f"SRT written to {srt_path}")
    ass_path = PROMO_DIR / "subs.ass"
    with open(ass_path, "w", encoding="utf-8") as f:
        f.write("[Script Info]\nTitle: CruiseLoom Promo\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\nPlayResX: 1920\nPlayResY: 1080\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,52,&H00FFFFFF,&H000000FF,&H80000000,&HAA0B1E3B,1,0,0,0,100,100,0,0,1,4,2,2,30,30,80,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        cur = 0.0
        for seg in segments:
            start = cur
            end = cur + seg["dur"]
            def ass_fmt(t):
                h = int(t//3600)
                m = int((t%3600)//60)
                s = int(t%60)
                cs = int((t - int(t))*100)
                return f"{h}:{m:02d}:{s:02d}.{cs:02d}"
            cap = seg["caption"].replace(",", r"\,")
            f.write(f"Dialogue: 0,{ass_fmt(start)},{ass_fmt(end)},Default,,0,0,0,,{cap}\n")
            cur = end
        def ass_fmt2(t):
            h = int(t//3600); m = int((t%3600)//60); s = int(t%60); cs = int((t - int(t))*100)
            return f"{h}:{m:02d}:{s:02d}.{cs:02d}"
        ending_cap_esc = ENDING_CAPTION.replace(",", r"\,")
        f.write(f"Dialogue: 0,{ass_fmt2(cur)},{ass_fmt2(cur+ending_audio_dur)},Default,,0,0,0,,{ending_cap_esc}\n")
    print(f"ASS written to {ass_path}")
    clip_files = []
    for i, seg in enumerate(segments):
        img = seg["image"]
        dur = seg["dur"]
        fade_in = 0.35
        fade_out = 0.35
        fade_out_start = max(0.5, dur - fade_out)
        out_clip = TMP_DIR / f"clip{i:02d}.mp4"
        clip_files.append(out_clip)
        vf = f"scale=-1:920:flags=lanczos,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=#0B1E3B,setsar=1,fade=t=in:st=0:d={fade_in},fade=t=out:st={fade_out_start}:d={fade_out}"
        cmd = ["ffmpeg","-y","-loop","1","-i", str(img), "-t", f"{dur:.3f}", "-vf", vf, "-r","30","-c:v","libx264","-pix_fmt","yuv420p","-crf","18","-preset","medium","-an", str(out_clip)]
        print(f"Generating clip {i} {img.name} dur {dur:.2f}s ...")
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"Generating ending card clip dur {ending_audio_dur:.2f}s ...")
    ending_clip = TMP_DIR / f"clip{len(segments):02d}_ending.mp4"
    fade_in_e = 0.4; fade_out_e = 0.6; fade_out_start_e = max(0.8, ending_audio_dur - fade_out_e)
    vf_end = f"scale=1920:1080:flags=lanczos,setsar=1,fade=t=in:st=0:d={fade_in_e},fade=t=out:st={fade_out_start_e}:d={fade_out_e}"
    cmd = ["ffmpeg","-y","-loop","1","-i", str(ending_card), "-t", f"{ending_audio_dur:.3f}", "-vf", vf_end, "-r","30","-c:v","libx264","-pix_fmt","yuv420p","-crf","18","-preset","medium","-an", str(ending_clip)]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    clip_files.append(ending_clip)
    concat_list = TMP_DIR / "video_concat.txt"
    with open(concat_list, "w", encoding="utf-8") as f:
        for p in clip_files:
            f.write(f"file '{p.as_posix()}'\n")
    temp_video = PROMO_DIR / "temp_video.mp4"
    cmd = ["ffmpeg","-y","-f","concat","-safe","0","-i", str(concat_list), "-c","copy", str(temp_video)]
    print("Concatenating video clips...")
    subprocess.run(cmd, check=True)
    print("Muxing final promo with voiceover and captions...")
    rel_ass = ass_path.relative_to(PROJECT_ROOT).as_posix()
    cmd = ["ffmpeg","-y","-i", str(temp_video), "-i", str(voiceover), "-filter_complex", f"[0:v]ass={rel_ass}[v]", "-map","[v]","-map","1:a","-c:v","libx264","-pix_fmt","yuv420p","-crf","20","-preset","medium","-c:a","aac","-b:a","192k","-shortest","-movflags","+faststart", str(OUTPUT)]
    subprocess.run(cmd, check=True, cwd=str(PROJECT_ROOT))
    print(f"Final promo written to {OUTPUT} size {OUTPUT.stat().st_size} bytes")
    cmd = ["ffmpeg","-y","-i", str(OUTPUT), "-ss","0","-vframes","1","-q:v","2", str(POSTER)]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"Poster at {POSTER}")
    print("Done")
if __name__ == "__main__":
    asyncio.run(main())
