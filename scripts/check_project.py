#!/usr/bin/env python3
"""Pemeriksaan cepat sebelum build: nama file res/raw valid dan katalog cocok."""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
ok = True
names = set()
for f in os.listdir(RAW):
    base = os.path.splitext(f)[0]
    if not re.fullmatch(r"[a-z][a-z0-9_]*", base):
        print("Nama tidak valid di res/raw:", f)
        ok = False
    if base in names:
        print("Nama ganda di res/raw:", base)
        ok = False
    names.add(base)
for cat in ("sfx_catalog.json", "music_catalog.json"):
    p = os.path.join(ASSETS, cat)
    if os.path.exists(p):
        for item in json.load(open(p, encoding="utf-8")):
            if item["id"] not in names:
                print(f"{cat}: {item['id']} tidak ada di res/raw")
                ok = False
print("Pemeriksaan selesai:", "OK" if ok else "ADA MASALAH")
sys.exit(0 if ok else 1)
