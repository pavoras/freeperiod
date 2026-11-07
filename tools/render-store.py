"""Render all seven store languages with headless Edge, after recording their references."""
from pathlib import Path
import re
import subprocess
import sys

LANGUAGES = ("en", "de", "pt-BR", "es", "id", "tr", "fr")
SHOTS = ("privacy", "today", "diary", "history", "modes", "ring", "switch")
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")


def jobs():
    for lang in LANGUAGES:
        suffix = "" if lang == "en" else f"-{lang}"
        yield (f"feature-graphic{suffix}.html", "feature-graphic", f"feature-graphic{suffix}.png", 1024, 500)
        for index, name in enumerate(SHOTS, 1):
            yield (f"screenshots{suffix}.html", f"shot-{name}", f"{index:02d}-{name}-{lang}.png", 1080, 1920)


def render(store):
    # Check the whole set before replacing any existing output. Never render broken phone images.
    html_files = {src: (store / src).read_text(encoding="utf-8") for src, *_ in jobs()}
    missing = sorted({str((store / ref).resolve()) for html in html_files.values()
                      for ref in re.findall(r'<img src="([^"]+)"', html) if not (store / ref).is_file()})
    if missing:
        raise SystemExit("Record these Roborazzi references first:\n" + "\n".join(missing))
    if not EDGE.is_file():
        raise SystemExit(f"Edge not found: {EDGE}")
    (store / "png").mkdir(parents=True, exist_ok=True)
    for src, element, output, width, height in jobs():
        css = (f"<style>body.artwork{{padding:0!important;margin:0!important}} "
               f".screenshot-board{{display:block!important}} body > *:not(main):not(#{element}), "
               f".shot:not(#{element}){{display:none!important}}</style>")
        tmp = store / f"_render-{element}-{src}"
        target = store / "png" / output
        try:
            tmp.write_text(html_files[src].replace("</head>", css + "</head>"), encoding="utf-8")
            target.unlink(missing_ok=True)
            subprocess.run([str(EDGE), "--headless=new", "--disable-gpu", "--hide-scrollbars",
                            "--force-device-scale-factor=1", f"--window-size={width},{height}",
                            "--virtual-time-budget=4000", f"--screenshot={target}", tmp.as_uri()],
                           capture_output=True, check=True, timeout=60)
            if not target.is_file():
                raise SystemExit(f"Edge did not write {target}")
            print(output)
        finally:
            tmp.unlink(missing_ok=True)


if __name__ == "__main__":
    render((Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "store").resolve())
