"""Copies the rendered store artwork (store/png) into the fastlane metadata F-Droid reads.

Run after `python tools/render-store.py`: python tools/fastlane-images.py
"""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store" / "png"
for locale, lang in (("en-US", "en"), ("de-DE", "de")):
    images = ROOT / "fastlane" / "metadata" / "android" / locale / "images"
    shots = images / "phoneScreenshots"
    shots.mkdir(parents=True, exist_ok=True)
    shutil.copy(STORE / "icon-512.png", images / "icon.png")
    shutil.copy(STORE / "feature-graphic.png", images / "featureGraphic.png")
    for old in shots.glob("*.png"):
        old.unlink()
    for index, shot in enumerate(sorted(STORE.glob(f"[0-9][0-9]-*-{lang}.png")), 1):
        shutil.copy(shot, shots / f"{index}.png")
    print(locale, index, "screenshots")
