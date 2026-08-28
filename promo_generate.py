import asyncio, edge_tts, subprocess, pathlib
PROJECT_ROOT = pathlib.Path(r"H:\cruise-app")
PUBLIC = PROJECT_ROOT / "public"
PROMO_DIR = PROJECT_ROOT / "promo"
TMP_DIR = PROMO_DIR / "tmp"
OUTPUT = PUBLIC / "promo.mp4"
POSTER = PUBLIC / "promo-poster.jpg"
PROMO_DIR.mkdir(exist_ok=True)
TMP_DIR.mkdir(parents=True, exist_ok=True)
segments = [
    {"image": PUBLIC / "screenshots" / "01_welcome.png", "text": "Welcome aboard Cruise Planner — your offline-first companion for life at sea.", "caption": "Welcome aboard — offline-first"},
    {"image": PUBLIC / "screenshots" / "02_cruise_setup.png", "text": "Create your cruise. Name your ship, pick your start and end dates, and watch your itinerary come to life.", "caption": "Create your cruise — ship & dates"},
    {"image": PUBLIC / "screenshots" / "04_calendar_picker.png", "text": "Our calendar makes it easy — pick a range or tap a single date, with quick minus one, plus one adjustments.", "caption": "Calendar with range & fine-tune"},
    {"image": PUBLIC / "screenshots" / "06_dashboard.png", "text": "Your dashboard shows every sea day and port day at a glance — sea versus port, with your upcoming events.", "caption": "Dashboard — sea vs port, day by day"},
    {"image": PUBLIC / "screenshots" / "07_port_list.png", "text": "Add real port stops. Search any city and we auto-fill latitude and longitude — no API key needed.", "caption": "Real ports — search, lat/lon auto-filled"},
    {"image": PUBLIC / "screenshots" / "08_weather.png", "text": "Get live weather for any port. Seven days, current conditions, humidity and wind — cached for three hours, so it works offline on sea days.", "caption": "Live weather, no API key — cached 3h, works offline"},
    {"image": PUBLIC / "screenshots" / "09_day_detail.png", "text": "Tap any day to add dinners, excursions, and reminders. Get notified on time, even after a reboot.", "caption": "Tap any day — dinners, excursions, reminders"},
    {"image": PUBLIC / "screenshots" / "10_party.png", "text": "Stay close to your party with offline chat over Bluetooth mesh. No ship Wi-Fi needed — broadcast or private, with delivery and read receipts.", "caption": "Offline chat — Bluetooth mesh, broadcast or private"},
    {"image": PUBLIC / "screenshots" / "11_my_qr.png", "text": "Share your QR code, not your name. Private, encrypted, and instant — just scan to add a shipmate.", "caption": "Share your QR — private, encrypted"},
    {"image": PUBLIC / "screenshots" / "12_dashboard_final.png", "text": "From welcome aboard to welcome dinner — Cruise Planner. Built offline, for offline. Download today on GitHub.", "caption": "Built offline, for offline"},
]
VOICE = "en-US-AriaNeural"
RATE = "+8%"
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
    print(f"Generating {len(segments)} voiceover segments with {VOICE} rate {RATE}...")
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
            cur = end + 0.15
    print(f"SRT written to {srt_path}")
    ass_path = PROMO_DIR / "subs.ass"
    with open(ass_path, "w", encoding="utf-8") as f:
        f.write("[Script Info]\nTitle: Cruise Planner Promo\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\nPlayResX: 1920\nPlayResY: 1080\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,52,&H00FFFFFF,&H000000FF,&H80000000,&HAA0B1E3B,1,0,0,0,100,100,0,0,1,4,2,2,30,30,80,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
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
            cur = end + 0.15
    print(f"ASS written to {ass_path}")
    clip_files = []
    for i, seg in enumerate(segments):
        img = seg["image"]
        dur = seg["dur"] + 0.15
        fade_in = 0.35
        fade_out = 0.35
        fade_out_start = max(0.5, dur - fade_out)
        out_clip = TMP_DIR / f"clip{i:02d}.mp4"
        clip_files.append(out_clip)
        vf = f"scale=-1:920:flags=lanczos,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=#0B1E3B,setsar=1,fade=t=in:st=0:d={fade_in},fade=t=out:st={fade_out_start}:d={fade_out}"
        cmd = ["ffmpeg","-y","-loop","1","-i", str(img), "-t", f"{dur:.3f}", "-vf", vf, "-r","30","-c:v","libx264","-pix_fmt","yuv420p","-crf","18","-preset","medium","-an", str(out_clip)]
        print(f"Generating clip {i} {img.name} dur {dur:.2f}s ...")
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
