"""Render store artwork to store/png with headless Edge: python tools/render-store.py"""
import subprocess, os, pathlib, sys
store = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent / "store"; edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
jobs = [("feature-graphic.html", "feature-graphic", "feature-graphic.png", 1024, 500)]
names = ["today","period","diary","history","settings","setup"]
for lang, src in [("en","screenshots.html"),("de","screenshots-de.html")]:
    for i,n in enumerate(names,1): jobs.append((src, f"shot-{n}", f"{i:02d}-{n}-{lang}.png", 1080, 1920))
for src, el, out, w, h in jobs:
    html = (store/src).read_text(encoding="utf-8")
    css = f"<style>body.artwork{{padding:0!important;margin:0!important}} .screenshot-board{{display:block!important}} body > *:not(main):not(#{el}), .shot:not(#{el}){{display:none!important}}</style>"
    tmp = store/f"_render-{el}-{src}"; tmp.write_text(html.replace("</head>", css+"</head>"), encoding="utf-8")
    subprocess.run([edge, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1", f"--window-size={w},{h}",
        "--virtual-time-budget=4000", f"--screenshot={store/'png'/out}", tmp.as_uri()], capture_output=True, timeout=60)
    tmp.unlink(); print(out, (store/'png'/out).exists())
