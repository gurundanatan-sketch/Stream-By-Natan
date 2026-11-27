#!/usr/bin/env python3
"""
Membuat animasi Lottie (JSON) untuk efek cahaya & partikel layar pembuka/penutup sinematik.
Hasil: app/src/main/assets/lottie/*.json  (dibaca pustaka Lottie di aplikasi).
Hanya pustaka bawaan Python.
"""
import json
import math
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "lottie")
W, H, FR = 720, 1280, 30
EASE_I = {"x": [0.42], "y": [1]}
EASE_O = {"x": [0.58], "y": [0]}


def static(v):
    return {"a": 0, "k": v}


def anim(keys):
    """keys: list of (frame, value). Nilai berupa list."""
    k = []
    for i, (t, v) in enumerate(keys):
        item = {"t": t, "s": v if isinstance(v, list) else [v]}
        if i < len(keys) - 1:
            item["i"] = {"x": [0.42] * len(item["s"]), "y": [1] * len(item["s"])}
            item["o"] = {"x": [0.58] * len(item["s"]), "y": [0] * len(item["s"])}
        k.append(item)
    return {"a": 1, "k": k}


def tr():
    return {"ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]), "r": static(0), "o": static(100)}


def ellipse_layer(ind, size, color, pos, opacity, scale=None, op=90, blur_layers=True):
    shapes = [{"ty": "gr", "nm": "g", "it": [
        {"ty": "el", "p": static([0, 0]), "s": static([size, size]), "d": 1},
        {"ty": "fl", "c": static(color), "o": static(100), "r": 1},
        tr(),
    ]}]
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": f"e{ind}", "sr": 1, "ao": 0, "ip": 0, "op": op, "st": 0, "bm": 0,
        "ks": {"o": opacity, "r": static(0), "p": pos, "a": static([0, 0, 0]), "s": scale or static([100, 100, 100])},
        "shapes": shapes,
    }


def rect_layer(ind, w, h, color, pos, rot, opacity, op=90):
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": f"r{ind}", "sr": 1, "ao": 0, "ip": 0, "op": op, "st": 0, "bm": 0,
        "ks": {"o": opacity, "r": rot, "p": pos, "a": static([0, 0, 0]), "s": static([100, 100, 100])},
        "shapes": [{"ty": "gr", "nm": "g", "it": [
            {"ty": "rc", "p": static([0, 0]), "s": static([w, h]), "r": static(w / 2), "d": 1},
            {"ty": "fl", "c": static(color), "o": static(100), "r": 1},
            tr(),
        ]}],
    }


def comp(name, layers, op=90):
    return {"v": "5.7.4", "fr": FR, "ip": 0, "op": op, "w": W, "h": H, "nm": name, "ddd": 0, "assets": [], "layers": layers}


WHITE = [1, 1, 1, 1]
GOLD = [1, 0.84, 0.45, 1]
CYAN = [0.13, 0.9, 1, 1]


def glow_pulse():
    layers = []
    for i, (size, col) in enumerate([(900, GOLD), (600, WHITE), (300, WHITE)]):
        layers.append(ellipse_layer(i + 1, size, col, static([W / 2, H * 0.4, 0]),
                                    anim([(0, 4), (45, 22 - i * 4), (90, 4)]),
                                    anim([(0, [90, 90, 100]), (45, [110, 110, 100]), (90, [90, 90, 100])])))
    return comp("glow_pulse", layers)


def light_rays():
    layers = []
    for i in range(12):
        ang = i * 30
        layers.append(rect_layer(i + 1, 60, 1600, GOLD, static([W / 2, H * 0.3, 0]),
                                 anim([(0, ang), (180, ang + 40)]), anim([(0, 6), (90, 14), (180, 6)]), op=180))
    return comp("light_rays", layers, 180)


def sparkles():
    random.seed(11)
    layers = []
    for i in range(36):
        x = random.uniform(0, W)
        start = random.randint(0, 60)
        size = random.uniform(6, 18)
        layers.append(ellipse_layer(i + 1, size, random.choice([WHITE, GOLD, CYAN]),
                                    anim([(start, [x, H + 20, 0]), (start + 120, [x + random.uniform(-60, 60), -20, 0])]),
                                    anim([(start, 0), (start + 20, 90), (start + 100, 90), (start + 120, 0)]), op=180))
    return comp("sparkles", layers, 180)


def light_sweep():
    return comp("light_sweep", [rect_layer(1, 260, 2600, WHITE, anim([(0, [-400, H / 2, 0]), (75, [W + 400, H / 2, 0])]),
                                           static(20), anim([(0, 0), (20, 22), (55, 22), (75, 0)]), op=90)])


def rings():
    layers = []
    for i in range(4):
        st = i * 20
        layers.append(ellipse_layer(i + 1, 200, CYAN, static([W / 2, H / 2, 0]),
                                    anim([(st, 30), (st + 80, 0)]),
                                    anim([(st, [20, 20, 100]), (st + 80, [420, 420, 100])]), op=160))
    return comp("rings", layers, 160)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in [("glow_pulse", glow_pulse), ("light_rays", light_rays), ("sparkles", sparkles),
                     ("light_sweep", light_sweep), ("rings", rings)]:
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(fn(), f, separators=(",", ":"))
        print("Lottie:", name)


if __name__ == "__main__":
    main()
