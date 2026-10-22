"""Render the README images (docs/readme/*.html) to PNG at 2x with headless Edge: py tools/render-readme.py"""
import pathlib, subprocess
root = pathlib.Path(__file__).resolve().parent.parent
edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
for name, size in [("hero", "1280,640"), ("architecture", "1280,630")]:
    src, out = root / f"docs/readme/{name}.html", root / f"docs/readme/{name}.png"
    subprocess.run([edge, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
        f"--window-size={size}", "--virtual-time-budget=4000", f"--screenshot={out}", src.as_uri()], capture_output=True, timeout=60)
    print(out.name, out.stat().st_size)
