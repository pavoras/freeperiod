"""Write the store artwork HTML (screenshots and feature graphic, EN + DE) from one set of texts.

  py tools/store-boards.py      -> store/screenshots.html, screenshots-de.html, feature-graphic.html, feature-graphic-de.html
  py tools/render-store.py      -> store/png/ (then py tools/fastlane-images.py)
Screens come from the Roborazzi references in app/src/test/screenshots/.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REF = "../app/src/test/screenshots/"
SWATCHES = ["#f3b6a4", "#d6b6d6", "#b8ccac", "#accadb", "#e3c17f", "#2f3440", "#2f6b4f", "#3456a6", "#b24a22"]

ICONS = {  # simple 24px line icons
    "net": '<path d="M2 8.8a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19.5" r="1"/><path d="M3 3l18 18"/>',
    "user": '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/><path d="M3 3l18 18"/>',
    "eye": '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/><path d="M3 3l18 18"/>',
    "code": '<path d="M8 6l-6 6 6 6M16 6l6 6-6 6M14 4l-4 16"/>',
}

T = {
    "en": {
        "lang": "en", "example": "Example entries", "board": "FreePeriod screenshots",
        "privacy": ("Your cycle stays on your phone.", [
            ("net", "No internet permission", "The app can't send anything online."),
            ("user", "No account, no ads", "Free, without sign-up or subscription."),
            ("eye", "No tracking", "No analytics, no ad trackers."),
            ("code", "Open source", "Anyone can check the code."),
        ]),
        "shots": [
            ("today", "Your next period at a glance.", "Calendar estimates from your own entries.", "today/regular_enLight.png", "coral"),
            ("diary", "Track only what matters to you.", "Mood, flow, pain, symptoms, notes – all yours to shape.", "day/filledInk_enDark.png", "dark ink"),
            ("history", "See your patterns.", "Charts from a week to a year.", "history/chartsSage_enLight.png", "sage"),
            ("modes", "Soft or bold. Light or dark.", "Nine colours. Make it yours.", None, "duo"),
            ("ring", "Your cycle as a ring.", "Swipe between calendar and ring.", "today/ringPlum_enLight.png", "plum"),
            ("switch", "Switching apps? Bring your periods.", "Import an export file, a CSV or a backup.", "onboarding/pastPeriods_enLight.png", "coral"),
        ],
        "duo": ("today/regular_enLight.png", "today/regularInk_enDark.png", "Light · Coral", "Dark · Ink"),
        "feature": ("Your cycle stays on your phone.", "A period calendar and diary – free and open source.",
                    ["No internet permission", "No account", "No ads"], "today/regular_enLight.png"),
    },
    "de": {
        "lang": "de", "example": "Beispieldaten", "board": "FreePeriod-Screenshots",
        "privacy": ("Dein Zyklus bleibt auf deinem Handy.", [
            ("net", "Keine Internet-Berechtigung", "Die App kann nichts ins Internet senden."),
            ("user", "Kein Konto, keine Werbung", "Kostenlos, ohne Anmeldung oder Abo."),
            ("eye", "Kein Tracking", "Keine Analyse, keine Werbe-Tracker."),
            ("code", "Offener Quellcode", "Jede und jeder kann ihn prüfen."),
        ]),
        "shots": [
            ("today", "Deine nächste Periode auf einen Blick.", "Schätzungen aus deinen eigenen Einträgen.", "today/regular_deLight.png", "coral"),
            ("diary", "Erfasse nur, was dir wichtig ist.", "Stimmung, Blutung, Schmerzen, Notizen – ganz nach dir.", "day/filledInk_deDark.png", "dark ink"),
            ("history", "Erkenne deine Muster.", "Graphen von einer Woche bis zu einem Jahr.", "history/chartsSage_deLight.png", "sage"),
            ("modes", "Sanft oder kräftig. Hell oder dunkel.", "Neun Farben. Ganz nach dir.", None, "duo"),
            ("ring", "Dein Zyklus als Kreis.", "Zwischen Kalender und Kreis wischen.", "today/ringPlum_deLight.png", "plum"),
            ("switch", "App-Wechsel? Nimm deine Perioden mit.", "Export-Datei, CSV oder Backup importieren.", "onboarding/pastPeriods_deLight.png", "coral"),
        ],
        "duo": ("today/regular_deLight.png", "today/regularInk_deDark.png", "Hell · Koralle", "Dunkel · Tinte"),
        "feature": ("Dein Zyklus bleibt auf deinem Handy.", "Periodenkalender und Tagebuch – kostenlos und quelloffen.",
                    ["Keine Internet-Berechtigung", "Kein Konto", "Keine Werbung"], "today/regular_deLight.png"),
    },
}


def head(title, lang):
    return (f'<!doctype html>\n<html lang="{lang}">\n<head>\n  <meta charset="utf-8">\n'
            f'  <meta name="viewport" content="width=device-width, initial-scale=1">\n  <title>{title}</title>\n'
            '  <link rel="stylesheet" href="../site/styles.css">\n  <link rel="stylesheet" href="artwork.css">\n</head>\n')


def icon(name):
    return (f'<svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="currentColor" stroke-width="2" '
            f'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{ICONS[name]}</svg>')


def screenshots(t):
    out = [head(f"FreePeriod | {t['board']}", t["lang"]), '<body class="artwork">\n',
           f'  <main class="screenshot-board" aria-label="{t["board"]}">\n']
    title, rows = t["privacy"]
    items = "".join(f'<li><span class="ico">{icon(i)}</span><span><b>{b}</b><small>{s}</small></span></li>'
                    for i, b, s in rows)
    out.append(f'    <article id="shot-privacy" class="shot privacy"><div class="wordmark">FreePeriod<span class="stop">.</span></div>'
               f'<h2>{title}</h2><ul class="checks">{items}</ul></article>\n')
    for key, h, cap, img, look in t["shots"]:
        text = f'<header><div class="wordmark">FreePeriod<span class="stop">.</span></div><h2>{h}</h2><p class="caption">{cap}</p></header>'
        if look == "duo":
            light, dark, ll, dl = t["duo"]
            sw = "".join(f'<li style="background:{c}"></li>' for c in SWATCHES)
            body = (f'<ul class="swatches">{sw}</ul>'
                    f'<div class="phone light"><img src="{REF}{light}" alt="{ll}"></div>'
                    f'<div class="phone dark"><img src="{REF}{dark}" alt="{dl}"></div>'
                    f'<p class="mode light">{ll}</p><p class="mode dark">{dl}</p>')
        else:
            body = f'<div class="phone"><img src="{REF}{img}" alt="{h}"></div>'
        out.append(f'    <article id="shot-{key}" class="shot {look}">{text}{body}'
                   f'<footer>{t["example"]}</footer></article>\n')
    out.append("  </main>\n</body>\n</html>\n")
    return "".join(out)


def feature(t):
    h, sub, chips, img = t["feature"]
    chip = "".join(f'<li{" class=key" if i == 0 else ""}>{c}</li>' for i, c in enumerate(chips))
    return (head("FreePeriod | Feature graphic, 1024 × 500", t["lang"]) + '<body class="artwork">\n'
            f'  <main id="feature-graphic" class="feature"><div class="feature-copy">'
            f'<div class="wordmark">FreePeriod<span class="stop">.</span></div><h1>{h}</h1><p>{sub}</p>'
            f'<ul class="chips">{chip}</ul></div>'
            f'<div class="feature-phone"><img src="{REF}{img}" alt=""></div></main>\n</body>\n</html>\n')


if __name__ == "__main__":
    store = ROOT / "store"
    for lang, suffix in (("en", ""), ("de", "-de")):
        (store / f"screenshots{suffix}.html").write_text(screenshots(T[lang]), encoding="utf-8")
        (store / f"feature-graphic{suffix}.html").write_text(feature(T[lang]), encoding="utf-8")
    print("written: screenshots(.html|-de.html), feature-graphic(.html|-de.html)")
