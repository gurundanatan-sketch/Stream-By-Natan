#!/usr/bin/env python3
"""Menulis web/guest/config.js dari konfigurasi aplikasi WEB Firebase.

Dipakai di Termux sebelum `firebase deploy --only hosting`:
    python scripts/write_web_config.py stream-by-natan-29448

Kalau belum ada aplikasi WEB di proyek Firebase, skrip ini membuatnya dulu.
Hanya memakai pustaka standar Python + Firebase CLI.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "web" / "guest" / "config.js"


def run(args):
    r = subprocess.run(["firebase", *args, "--json"], capture_output=True, text=True)
    try:
        return json.loads(r.stdout)
    except json.JSONDecodeError:
        print(r.stdout, r.stderr)
        sys.exit("Perintah firebase gagal: " + " ".join(args))


def main():
    project = sys.argv[1] if len(sys.argv) > 1 else "stream-by-natan-29448"
    apps = run(["apps:list", "WEB", "--project", project]).get("result", [])
    if not apps:
        print("Membuat aplikasi WEB di Firebase…")
        created = run(["apps:create", "WEB", "Stream By Natan Web", "--project", project]).get("result", {})
        app_id = created.get("appId")
    else:
        app_id = apps[0].get("appId")
    if not app_id:
        sys.exit("Tidak menemukan appId aplikasi WEB.")
    res = run(["apps:sdkconfig", "WEB", app_id, "--project", project]).get("result", {})
    cfg = res.get("sdkConfig")
    if not cfg and res.get("fileContents"):
        txt = res["fileContents"]
        start, end = txt.find("{"), txt.rfind("}")
        cfg = json.loads(txt[start:end + 1]) if start >= 0 else None
    if not cfg:
        sys.exit("Konfigurasi web tidak terbaca.")
    OUT.write_text("window.FIREBASE_CONFIG = " + json.dumps(cfg, indent=2) + ";\n", encoding="utf-8")
    print("SELESAI: web/guest/config.js ditulis untuk proyek", project)


if __name__ == "__main__":
    main()
