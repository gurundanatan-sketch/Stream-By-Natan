#!/usr/bin/env python3
"""
Stream By Natan - pengunduh & pembuat aset suara otomatis.

Dijalankan oleh GitHub Actions sebelum APK dibangun (bisa juga di Termux:
  python scripts/download_assets.py).

Yang dikerjakan:
1. Membuat efek suara cadangan (disintesis sendiri, selalu berhasil walau tanpa internet).
2. Mengunduh sampai 200 efek suara dari Wikimedia Commons (tanpa API key)
   memakai daftar link di scripts/sfx_sources.txt, lalu pencarian Commons sebagai tambahan.
3. Mengunduh 20 musik latar dengan suasana berbeda dari Incompetech (Kevin MacLeod, CC BY 4.0)
   memakai daftar di scripts/music_sources.txt, plus Wikimedia Commons sebagai cadangan.
4. Mengunduh paket efek suara gaya game dari Kenney (CC0).
5. Mengunduh video latar sinematik untuk template pembuka/penutup dari Pexels atau Pixabay
   (butuh secret PEXELS_API_KEY atau PIXABAY_API_KEY; kalau tidak ada, aplikasi memakai animasi buatannya sendiri).
6. Mengubah semua file ke MP3 kecil (kalau ffmpeg ada), memberi nama huruf kecil,
   lalu menaruhnya di app/src/main/res/raw.
5. Menulis katalog JSON di app/src/main/assets (dibaca aplikasi) dan file kredit.

Hanya memakai pustaka bawaan Python, jadi tidak perlu pip install.
"""
from __future__ import annotations

import json
import math
import os
import random
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import wave
import zipfile
import io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SCRIPTS = os.path.join(ROOT, "scripts")
UA = "StreamByNatan/1.0 (https://github.com/gurundanatan-sketch/Stream-By-Natan; asset build script)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

MAX_SFX = int(os.environ.get("MAX_SFX", "200"))
MAX_MUSIC = int(os.environ.get("MAX_MUSIC", "20"))
MAX_SFX_BYTES = 2_000_000
MAX_MUSIC_BYTES = 25_000_000
OFFLINE = os.environ.get("OFFLINE_ASSETS") == "1"

HAS_FFMPEG = shutil.which("ffmpeg") is not None


def log(*a):
    print("[aset]", *a, flush=True)


def safe_name(text: str, prefix: str) -> str:
    """Nama file res/raw: huruf kecil, angka, garis bawah, diawali huruf."""
    base = os.path.splitext(text)[0].lower()
    base = re.sub(r"^file:", "", base)
    base = re.sub(r"[^a-z0-9]+", "_", base).strip("_")
    base = base[:40] or "suara"
    name = f"{prefix}_{base}"
    if not name[0].isalpha():
        name = "x" + name
    return name


def http_get(url: str, timeout: int = 40, max_bytes: int | None = None) -> bytes | None:
    if OFFLINE:
        return None
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            data = r.read((max_bytes + 1) if max_bytes else -1)
            if max_bytes and len(data) > max_bytes:
                log("terlalu besar, dilewati:", url)
                return None
            return data
    except Exception as e:  # noqa: BLE001 - jaringan boleh gagal, build tetap jalan
        log("gagal unduh:", url, "->", e)
        return None


def commons_json(params: dict) -> dict:
    params = dict(params)
    params.update({"format": "json", "formatversion": "2"})
    url = COMMONS_API + "?" + urllib.parse.urlencode(params)
    data = http_get(url, timeout=30)
    if not data:
        return {}
    try:
        return json.loads(data.decode("utf-8"))
    except Exception:  # noqa: BLE001
        return {}


# ---------------------------------------------------------------- sintesis cadangan
SR = 22050


def write_wav(path: str, samples: list[float], sr: int = SR, channels: int = 1):
    with wave.open(path, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(sr)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(v * 32000))
        w.writeframes(bytes(frames))


def env(i: int, n: int, attack: float = 0.01, release: float = 0.3) -> float:
    t = i / SR
    total = n / SR
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = min(1.0, max(0.0, (total - t) / release)) if release > 0 else 1.0
    return a * r


def tone(freqs, dur, decay=4.0, wave_type="sine"):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        v = 0.0
        for f in freqs:
            ph = 2 * math.pi * f * t
            if wave_type == "square":
                v += 1.0 if math.sin(ph) >= 0 else -1.0
            elif wave_type == "saw":
                v += 2 * ((f * t) % 1.0) - 1
            else:
                v += math.sin(ph)
        v /= max(1, len(freqs))
        out.append(v * math.exp(-decay * t) * env(i, n, 0.005, 0.05) * 0.8)
    return out


def noise(dur, decay=3.0, lowpass=0.2):
    n = int(SR * dur)
    out, prev = [], 0.0
    for i in range(n):
        x = random.uniform(-1, 1)
        prev = prev + lowpass * (x - prev)
        out.append(prev * math.exp(-decay * i / SR) * env(i, n, 0.01, 0.1))
    return out


def sweep(f0, f1, dur):
    n = int(SR * dur)
    out, ph = [], 0.0
    for i in range(n):
        f = f0 + (f1 - f0) * i / n
        ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * env(i, n, 0.02, 0.1) * 0.6)
    return out


def applause(dur=3.0):
    n = int(SR * dur)
    out = [0.0] * n
    for _ in range(int(dur * 60)):
        start = random.randint(0, n - 2000)
        for j in range(1500):
            out[start + j] += random.uniform(-1, 1) * math.exp(-j / 220) * 0.35
    return [v * env(i, n, 0.3, 0.8) for i, v in enumerate(out)]


def seq(*parts):
    r = []
    for p in parts:
        r.extend(p)
    return r


def silence(d):
    return [0.0] * int(SR * d)


def synth_fallbacks() -> list[dict]:
    random.seed(7)
    items = {
        "sfx_ting": ("Ting", tone([1760, 2637], 0.9, 5)),
        "sfx_ding_dong": ("Ding dong", seq(tone([659], 0.6, 3), tone([523], 0.9, 3))),
        "sfx_chime": ("Lonceng kecil", seq(tone([1047], 0.25, 6), tone([1319], 0.25, 6), tone([1568], 0.7, 4))),
        "sfx_coin": ("Koin", seq(tone([988], 0.08, 2, "square"), tone([1319], 0.4, 6, "square"))),
        "sfx_pop": ("Pop", sweep(900, 200, 0.12)),
        "sfx_whoosh": ("Whoosh", noise(0.8, 2.5, 0.08)),
        "sfx_applause_synth": ("Tepuk tangan", applause(3.0)),
        "sfx_success": ("Berhasil", seq(tone([523], 0.12, 3), tone([659], 0.12, 3), tone([784], 0.12, 3), tone([1047], 0.6, 3))),
        "sfx_fail": ("Gagal", seq(tone([392], 0.25, 2, "saw"), tone([330], 0.25, 2, "saw"), tone([262], 0.7, 2, "saw"))),
        "sfx_level_up": ("Naik level", sweep(300, 1400, 0.5)),
        "sfx_heart": ("Hati", seq(tone([80], 0.12, 10), silence(0.08), tone([70], 0.2, 8))),
        "sfx_notif": ("Notifikasi", seq(tone([880], 0.1, 4), tone([1320], 0.35, 5))),
        "sfx_drumroll": ("Drum roll", seq(*[noise(0.05, 30, 0.5) for _ in range(30)])),
        "sfx_magic": ("Sihir", seq(*[tone([1200 + 180 * k], 0.07, 8) for k in range(10)])),
        "sfx_bell_church": ("Lonceng gereja", tone([392, 784, 1175], 2.5, 1.2)),
        "sfx_laser": ("Laser", sweep(2000, 300, 0.3)),
        "sfx_click": ("Klik", tone([3000], 0.03, 60)),
        "sfx_gong": ("Gong", tone([110, 220, 331], 3.0, 0.9)),
        "sfx_horn": ("Terompet", tone([440, 554, 659], 0.8, 1.0, "saw")),
        "sfx_bubble": ("Gelembung", seq(*[sweep(400 + 60 * k, 900 + 80 * k, 0.06) for k in range(6)])),
    }
    out = []
    for name, (label, data) in items.items():
        path = os.path.join(RAW, name + ".wav")
        write_wav(path, data)
        out.append({"id": name, "label": label, "source": "Disintesis oleh skrip (domain publik)", "license": "CC0"})

    # Musik cadangan sinematik (pad lembut) untuk template pembuka/penutup
    sr2 = 22050
    n = sr2 * 12
    pad = []
    chord = [220.0, 277.18, 329.63, 440.0]
    for i in range(n):
        t = i / sr2
        v = sum(math.sin(2 * math.pi * f * t + 0.3 * math.sin(2 * math.pi * 0.2 * t)) for f in chord) / len(chord)
        v *= min(1.0, t / 2.0) * min(1.0, (12 - t) / 2.5) * 0.5
        pad.append(v)
    write_wav(os.path.join(RAW, "bgm_cinematic_pad.wav"), pad, sr2)
    return out


# ---------------------------------------------------------------- konversi
def convert(src: str, dst_noext: str, kind: str) -> str | None:
    """Ubah ke MP3 kecil. Kalau ffmpeg tidak ada, salin file asli bila formatnya didukung Android."""
    if HAS_FFMPEG:
        dst = dst_noext + ".mp3"
        if kind == "sfx":
            args = ["-t", "10", "-ac", "1", "-ar", "22050", "-b:a", "64k"]
        else:
            args = ["-ac", "2", "-ar", "44100", "-b:a", "96k"]
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", *args, dst]
        try:
            subprocess.run(cmd, check=True, timeout=180)
            return dst
        except Exception as e:  # noqa: BLE001
            log("ffmpeg gagal:", e)
            return None
    ext = os.path.splitext(src)[1].lower()
    if ext == ".oga":
        ext = ".ogg"
    if ext in (".ogg", ".mp3", ".wav", ".flac", ".m4a"):
        dst = dst_noext + ext
        shutil.copyfile(src, dst)
        return dst
    return None


def existing_names() -> set[str]:
    return {os.path.splitext(f)[0] for f in os.listdir(RAW)}


def save_download(url: str, name: str, kind: str, max_bytes: int) -> bool:
    if name in existing_names():
        return False
    data = http_get(url, timeout=90, max_bytes=max_bytes)
    if not data or len(data) < 2000:
        return False
    ext = os.path.splitext(urllib.parse.urlparse(url).path)[1] or ".bin"
    with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp:
        tmp.write(data)
        tmp_path = tmp.name
    try:
        return convert(tmp_path, os.path.join(RAW, name), kind) is not None
    finally:
        os.unlink(tmp_path)


def read_list(path: str) -> list[str]:
    if not os.path.exists(path):
        return []
    lines = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                lines.append(line)
    return lines


def commons_file_info(titles: list[str]) -> list[dict]:
    """Ambil URL, ukuran, dan lisensi untuk daftar 'File:...' dari Commons."""
    out = []
    for i in range(0, len(titles), 40):
        chunk = titles[i:i + 40]
        js = commons_json({
            "action": "query", "titles": "|".join(chunk), "prop": "imageinfo",
            "iiprop": "url|size|mime|extmetadata",
        })
        for page in js.get("query", {}).get("pages", []):
            info = (page.get("imageinfo") or [{}])[0]
            if not info.get("url"):
                continue
            meta = info.get("extmetadata", {})
            out.append({
                "title": page.get("title", ""),
                "url": info["url"],
                "size": info.get("size", 0),
                "mime": info.get("mime", ""),
                "license": meta.get("LicenseShortName", {}).get("value", "lihat halaman Commons"),
                "artist": re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "")).strip(),
                "page": "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(page.get("title", "").replace(" ", "_")),
            })
        time.sleep(0.5)
    return out


def commons_search(query: str, limit: int = 20) -> list[str]:
    js = commons_json({
        "action": "query", "list": "search", "srnamespace": "6",
        "srsearch": f"{query} filetype:audio", "srlimit": str(limit),
    })
    return [r["title"] for r in js.get("query", {}).get("search", [])]


def collect_sfx(catalog: list[dict]):
    titles = read_list(os.path.join(SCRIPTS, "sfx_sources.txt"))
    direct = [t for t in titles if t.startswith("http")]
    file_titles = [t if t.startswith("File:") else "File:" + t for t in titles if not t.startswith("http")]
    # Tambahan dari pencarian Commons sampai mencapai target
    queries = ["applause", "bell", "chime", "ding", "whoosh", "drum roll", "laughter", "cheering crowd", "pop sound",
               "gong", "horn", "click", "notification", "coin", "magic", "fanfare", "whistle", "cymbal", "harp glissando",
               "doorbell", "clock tick", "camera shutter", "water drop", "bird song", "rooster", "cat meow", "dog bark",
               "thunder", "rain", "wind", "trumpet", "xylophone", "glockenspiel", "triangle instrument", "kazoo", "tada"]
    pool = list(dict.fromkeys(file_titles))
    for q in queries:
        if len(pool) >= MAX_SFX * 2:
            break
        pool.extend(t for t in commons_search(q, 15) if t not in pool)
    infos = commons_file_info(pool)
    count = len(catalog)
    for info in infos:
        if count >= MAX_SFX:
            break
        if not info["mime"].startswith(("audio/", "application/ogg")):
            continue
        if info["size"] and info["size"] > MAX_SFX_BYTES:
            continue
        name = safe_name(info["title"], "sfx")
        if save_download(info["url"], name, "sfx", MAX_SFX_BYTES):
            label = re.sub(r"^File:", "", os.path.splitext(info["title"])[0]).replace("_", " ")
            catalog.append({"id": name, "label": label[:60], "source": info["page"],
                            "license": info["license"], "author": info["artist"]})
            count += 1
            log(f"SFX {count}/{MAX_SFX}:", name)
    for url in direct:
        if count >= MAX_SFX:
            break
        name = safe_name(os.path.basename(urllib.parse.urlparse(url).path), "sfx")
        if save_download(url, name, "sfx", MAX_SFX_BYTES):
            catalog.append({"id": name, "label": name[4:].replace("_", " "), "source": url, "license": "lihat sumber"})
            count += 1


def collect_music(catalog: list[dict]):
    entries = read_list(os.path.join(SCRIPTS, "music_sources.txt"))
    for line in entries:
        if len(catalog) >= MAX_MUSIC:
            break
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 2:
            continue
        mood, title = parts[0], parts[1]
        url = parts[2] if len(parts) > 2 and parts[2] else \
            "https://incompetech.com/music/royalty-free/mp3-royaltyfree/" + urllib.parse.quote(title) + ".mp3"
        name = safe_name(title, "bgm")
        if save_download(url, name, "music", MAX_MUSIC_BYTES):
            catalog.append({"id": name, "label": title, "mood": mood, "source": url,
                            "license": "CC BY 4.0", "credit": f'"{title}" Kevin MacLeod (incompetech.com) - Licensed under Creative Commons: By Attribution 4.0'})
            log(f"Musik {len(catalog)}/{MAX_MUSIC}:", name)
    if len(catalog) < MAX_MUSIC:
        titles = commons_search("Kevin MacLeod music", 40) + commons_search("instrumental background music", 40)
        for info in commons_file_info(titles):
            if len(catalog) >= MAX_MUSIC:
                break
            if info["size"] and info["size"] > MAX_MUSIC_BYTES:
                continue
            name = safe_name(info["title"], "bgm")
            if save_download(info["url"], name, "music", MAX_MUSIC_BYTES):
                catalog.append({"id": name, "label": re.sub(r"^File:", "", os.path.splitext(info["title"])[0]),
                                "mood": "campuran", "source": info["page"], "license": info["license"],
                                "credit": f'{info["title"]} - {info["artist"]} ({info["license"]})'})


# ---------------------------------------------------------------- Kenney (CC0)
KENNEY_PACKS = ["interface-sounds", "impact-sounds", "digital-audio", "casino-audio", "ui-audio", "rpg-audio"]


def collect_kenney(catalog: list[dict], limit: int):
    for slug in KENNEY_PACKS:
        if len(catalog) >= limit:
            return
        page = http_get(f"https://kenney.nl/assets/{slug}", timeout=30)
        if not page:
            continue
        m = re.search(r'href="(https://kenney\.nl/media/pages/assets/[^"]+\.zip)"', page.decode("utf-8", "ignore"))
        if not m:
            continue
        data = http_get(m.group(1), timeout=120, max_bytes=60_000_000)
        if not data:
            continue
        try:
            zf = zipfile.ZipFile(io.BytesIO(data))
        except zipfile.BadZipFile:
            continue
        taken = 0
        for info in zf.infolist():
            if len(catalog) >= limit or taken >= 12:
                break
            if not info.filename.lower().endswith((".ogg", ".wav", ".mp3")):
                continue
            name = safe_name(f"kenney_{slug}_{os.path.basename(info.filename)}", "sfx")
            if name in existing_names():
                continue
            ext = os.path.splitext(info.filename)[1]
            with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp:
                tmp.write(zf.read(info))
                tmp_path = tmp.name
            try:
                if convert(tmp_path, os.path.join(RAW, name), "sfx"):
                    catalog.append({"id": name, "label": os.path.splitext(os.path.basename(info.filename))[0].replace("_", " "),
                                    "source": f"https://kenney.nl/assets/{slug}", "license": "CC0", "author": "Kenney"})
                    taken += 1
            finally:
                os.unlink(tmp_path)
        log(f"Kenney {slug}: {taken} efek suara")


# ---------------------------------------------------------------- video latar sinematik
CINE_QUERIES = {
    "ibadah": "church light rays", "santai": "pastel bokeh", "megah": "golden particles",
    "natal": "christmas lights snow", "paskah": "sunrise clouds", "malam": "night sky stars",
    "alam": "blue sky clouds sun", "modern": "abstract neon lines", "klasik": "gold elegant particles",
}


def video_url_pexels(query: str, key: str) -> str | None:
    req = urllib.request.Request(
        "https://api.pexels.com/videos/search?" + urllib.parse.urlencode({"query": query, "orientation": "portrait", "per_page": 5}),
        headers={"Authorization": key, "User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            js = json.loads(r.read().decode())
    except Exception as e:  # noqa: BLE001
        log("Pexels gagal:", e)
        return None
    for v in js.get("videos", []):
        files = sorted((f for f in v.get("video_files", []) if f.get("file_type") == "video/mp4" and (f.get("height") or 0) >= 640),
                       key=lambda f: f.get("height") or 9999)
        if files:
            return files[0]["link"]
    return None


def video_url_pixabay(query: str, key: str) -> str | None:
    data = http_get("https://pixabay.com/api/videos/?" + urllib.parse.urlencode({"key": key, "q": query, "per_page": 5}), timeout=30)
    if not data:
        return None
    for h in json.loads(data.decode()).get("hits", []):
        for size in ("small", "tiny", "medium"):
            url = h.get("videos", {}).get(size, {}).get("url")
            if url:
                return url
    return None


def collect_cinematic_videos():
    pexels = os.environ.get("PEXELS_API_KEY", "").strip()
    pixabay = os.environ.get("PIXABAY_API_KEY", "").strip()
    if not (pexels or pixabay):
        log("PEXELS_API_KEY / PIXABAY_API_KEY kosong: video latar sinematik dilewati (aplikasi memakai animasi bawaan).")
        return []
    if not HAS_FFMPEG:
        log("ffmpeg tidak ada: video latar sinematik dilewati.")
        return []
    credits = []
    for tpl, q in CINE_QUERIES.items():
        name = f"cine_{tpl}"
        if name in existing_names():
            continue
        url = (video_url_pexels(q, pexels) if pexels else None) or (video_url_pixabay(q, pixabay) if pixabay else None)
        if not url:
            continue
        data = http_get(url, timeout=180, max_bytes=80_000_000)
        if not data:
            continue
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
            tmp.write(data)
            src = tmp.name
        try:
            dst = os.path.join(RAW, name + ".mp4")
            cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-t", "8", "-an",
                   "-vf", "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,fps=24",
                   "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart", dst]
            subprocess.run(cmd, check=True, timeout=240)
            credits.append(f"Video latar '{tpl}': {'Pexels' if pexels else 'Pixabay'} ({url})")
            log("Video sinematik:", name)
        except Exception as e:  # noqa: BLE001
            log("Gagal ubah video:", e)
        finally:
            os.unlink(src)
    return credits


def main() -> int:
    os.makedirs(RAW, exist_ok=True)
    os.makedirs(ASSETS, exist_ok=True)
    log("ffmpeg tersedia:" if HAS_FFMPEG else "ffmpeg tidak ada, file asli dipakai:", HAS_FFMPEG)

    sfx_catalog = synth_fallbacks()
    music_catalog = [{"id": "bgm_cinematic_pad", "label": "Pad sinematik (bawaan)", "mood": "sinematik",
                      "source": "Disintesis oleh skrip", "license": "CC0", "credit": ""}]
    try:
        collect_kenney(sfx_catalog, min(MAX_SFX, len(sfx_catalog) + 60))  # efek gaya game dari Kenney (CC0)
    except Exception as e:  # noqa: BLE001
        log("Kenney berhenti:", e)
    try:
        collect_sfx(sfx_catalog)  # sisanya sampai 200 dari Wikimedia Commons
    except Exception as e:  # noqa: BLE001
        log("Pengumpulan SFX berhenti:", e)
    cine_credits = []
    try:
        cine_credits = collect_cinematic_videos()
    except Exception as e:  # noqa: BLE001
        log("Video sinematik berhenti:", e)
    try:
        collect_music(music_catalog)
    except Exception as e:  # noqa: BLE001
        log("Pengumpulan musik berhenti:", e)

    # Pastikan katalog hanya berisi file yang benar-benar ada
    present = existing_names()
    sfx_catalog = [s for s in sfx_catalog if s["id"] in present]
    music_catalog = [m for m in music_catalog if m["id"] in present]

    with open(os.path.join(ASSETS, "sfx_catalog.json"), "w", encoding="utf-8") as f:
        json.dump(sfx_catalog, f, ensure_ascii=False, indent=1)
    with open(os.path.join(ASSETS, "music_catalog.json"), "w", encoding="utf-8") as f:
        json.dump(music_catalog, f, ensure_ascii=False, indent=1)
    with open(os.path.join(ASSETS, "credits.txt"), "w", encoding="utf-8") as f:
        f.write("KREDIT MUSIK & EFEK SUARA - Stream By Natan\n\n")
        for m in music_catalog:
            if m.get("credit"):
                f.write(m["credit"] + "\n")
        for c in cine_credits:
            f.write(c + "\n")
        f.write("\nEfek suara:\n")
        for s in sfx_catalog:
            f.write(f'- {s["label"]}: {s.get("license", "")} {s.get("author", "")} {s.get("source", "")}\n')
    log(f"Selesai: {len(sfx_catalog)} efek suara, {len(music_catalog)} musik.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
