"""Render the README hero (docs/readme/hero.html) to docs/readme/hero.png with headless Edge: py tools/render-readme.py"""
import pathlib, subprocess
root = pathlib.Path(__file__).resolve().parent.parent
edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
src, out = root / "docs/readme/hero.html", root / "docs/readme/hero.png"
subprocess.run([edge, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
    "--window-size=1280,640", "--virtual-time-budget=4000", f"--screenshot={out}", src.as_uri()], capture_output=True, timeout=60)
print(out.name, out.stat().st_size)
