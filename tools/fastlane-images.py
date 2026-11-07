"""Copy rendered artwork into all nine fastlane locales. Run after render-store.py."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
LOCALES = {
    "en-US": "en", "de-DE": "de", "pt-BR": "pt-BR",
    "es-419": "es", "es-ES": "es", "es-US": "es",
    "id": "id", "tr-TR": "tr", "fr-FR": "fr",
}
SHOTS = ("privacy", "today", "diary", "history", "modes", "ring", "switch")


def copy_images(root=ROOT):
    store = root / "store" / "png"
    sources = {}
    for locale, lang in LOCALES.items():
        suffix = "" if lang == "en" else f"-{lang}"
        sources[locale] = [store / "icon-512.png", store / f"feature-graphic{suffix}.png"] + [
            store / f"{index:02d}-{name}-{lang}.png" for index, name in enumerate(SHOTS, 1)]
    # A missing language must not silently reuse English or clear an existing listing.
    missing = sorted({str(path) for paths in sources.values() for path in paths if not path.is_file()})
    if missing:
        raise SystemExit("Render all store artwork first:\n" + "\n".join(missing))
    for locale, paths in sources.items():
        images = root / "fastlane" / "metadata" / "android" / locale / "images"
        shots = images / "phoneScreenshots"
        shots.mkdir(parents=True, exist_ok=True)
        shutil.copy(paths[0], images / "icon.png")
        shutil.copy(paths[1], images / "featureGraphic.png")
        for old in shots.glob("*.png"):
            old.unlink()
        for index, shot in enumerate(paths[2:], 1):
            shutil.copy(shot, shots / f"{index}.png")
        print(locale, len(paths) - 2, "screenshots")


if __name__ == "__main__":
    copy_images()
