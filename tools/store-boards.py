"""Write store artwork HTML for all seven app languages from one set of texts.

  py tools/store-boards.py      -> store/screenshots[-<lang>].html, feature-graphic[-<lang>].html
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
    "pt-BR": {
        "lang": "pt-BR", "example": "Registros de exemplo", "board": "Capturas de tela do FreePeriod",
        "privacy": ("Seu ciclo fica no seu celular.", [
            ("net", "Sem permissão de internet", "O app não pode enviar nada para a internet."),
            ("user", "Sem conta nem anúncios", "Grátis, sem cadastro nem assinatura."),
            ("eye", "Sem rastreamento", "Sem análise de uso nem rastreadores de anúncios."),
            ("code", "Código aberto", "Qualquer pessoa pode conferir o código."),
        ]),
        "shots": [
            ("today", "Sua próxima menstruação à vista.", "Estimativas de calendário com base nos seus registros.", "today/regular_ptBRLight.png", "coral"),
            ("diary", "Registre o que importa para você.", "Humor, fluxo, dor, sintomas e notas – do seu jeito.", "day/filledInk_ptBRDark.png", "dark ink"),
            ("history", "Veja seus padrões.", "Gráficos de uma semana a um ano.", "history/chartsSage_ptBRLight.png", "sage"),
            ("modes", "Suave ou marcante. Claro ou escuro.", "Nove cores. Do seu jeito.", None, "duo"),
            ("ring", "Seu ciclo em um anel.", "Deslize entre o calendário e o anel.", "today/ringPlum_ptBRLight.png", "plum"),
            ("switch", "Mudando de app? Traga seus registros.", "Importe um arquivo de exportação, CSV ou backup.", "onboarding/pastPeriods_ptBRLight.png", "coral"),
        ],
        "duo": ("today/regular_ptBRLight.png", "today/regularInk_ptBRDark.png", "Claro · Coral", "Escuro · Tinta"),
        "feature": ("Seu ciclo fica no seu celular.", "Calendário menstrual e diário – grátis e de código aberto.",
                    ["Sem permissão de internet", "Sem conta", "Sem anúncios"], "today/regular_ptBRLight.png"),
    },
    "es": {
        "lang": "es", "example": "Registros de ejemplo", "board": "Capturas de FreePeriod",
        "privacy": ("Tu ciclo se queda en tu teléfono.", [
            ("net", "Sin permiso de internet", "La app no puede enviar nada a internet."),
            ("user", "Sin cuenta ni anuncios", "Gratis, sin registro ni suscripción."),
            ("eye", "Sin seguimiento", "Sin análisis de uso ni rastreadores publicitarios."),
            ("code", "Código abierto", "Cualquiera puede revisar el código."),
        ]),
        "shots": [
            ("today", "Tu próximo periodo de un vistazo.", "Estimaciones del calendario basadas en tus registros.", "today/regular_esLight.png", "coral"),
            ("diary", "Registra lo que te importa.", "Ánimo, sangrado, dolor, síntomas y notas – a tu manera.", "day/filledInk_esDark.png", "dark ink"),
            ("history", "Observa tus patrones.", "Gráficos de una semana a un año.", "history/chartsSage_esLight.png", "sage"),
            ("modes", "Suave o intenso. Claro u oscuro.", "Nueve colores. A tu manera.", None, "duo"),
            ("ring", "Tu ciclo en un anillo.", "Desliza entre el calendario y el anillo.", "today/ringPlum_esLight.png", "plum"),
            ("switch", "¿Cambias de app? Trae tus registros.", "Importa un archivo de exportación, un CSV o una copia de seguridad.", "onboarding/pastPeriods_esLight.png", "coral"),
        ],
        "duo": ("today/regular_esLight.png", "today/regularInk_esDark.png", "Claro · Coral", "Oscuro · Tinta"),
        "feature": ("Tu ciclo se queda en tu teléfono.", "Calendario del periodo y diario – gratis y de código abierto.",
                    ["Sin permiso de internet", "Sin cuenta", "Sin anuncios"], "today/regular_esLight.png"),
    },
    "id": {
        "lang": "id", "example": "Contoh catatan", "board": "Screenshot FreePeriod",
        "privacy": ("Siklus kamu tetap di ponsel kamu.", [
            ("net", "Tanpa izin internet", "Aplikasi tidak bisa mengirim apa pun ke internet."),
            ("user", "Tanpa akun atau iklan", "Gratis, tanpa pendaftaran atau langganan."),
            ("eye", "Tanpa pelacakan", "Tanpa analitik atau pelacak iklan."),
            ("code", "Sumber terbuka", "Siapa pun bisa memeriksa kodenya."),
        ]),
        "shots": [
            ("today", "Perkiraan haid berikutnya.", "Perkiraan kalender dari catatan kamu sendiri.", "today/regular_idLight.png", "coral"),
            ("diary", "Catat yang penting buat kamu.", "Suasana hati, darah haid, nyeri, gejala, catatan – sesuaikan sendiri.", "day/filledInk_idDark.png", "dark ink"),
            ("history", "Lihat pola kamu.", "Grafik dari seminggu hingga setahun.", "history/chartsSage_idLight.png", "sage"),
            ("modes", "Lembut atau tegas. Terang atau gelap.", "Sembilan warna. Sesuai selera kamu.", None, "duo"),
            ("ring", "Siklus kamu dalam lingkaran.", "Geser antara kalender dan lingkaran.", "today/ringPlum_idLight.png", "plum"),
            ("switch", "Pindah aplikasi? Bawa catatan kamu.", "Impor file ekspor, CSV atau cadangan.", "onboarding/pastPeriods_idLight.png", "coral"),
        ],
        "duo": ("today/regular_idLight.png", "today/regularInk_idDark.png", "Terang · Koral", "Gelap · Tinta"),
        "feature": ("Siklus kamu tetap di ponsel kamu.", "Kalender haid dan catatan harian – gratis, dengan kode terbuka.",
                    ["Tanpa izin internet", "Tanpa akun", "Tanpa iklan"], "today/regular_idLight.png"),
    },
    "tr": {
        "lang": "tr", "example": "Örnek kayıtlar", "board": "FreePeriod ekran görüntüleri",
        "privacy": ("Döngün telefonunda kalır.", [
            ("net", "İnternet izni yok", "Uygulama internete hiçbir şey gönderemez."),
            ("user", "Hesap yok, reklam yok", "Ücretsiz, kayıt veya abonelik yok."),
            ("eye", "İzleme yok", "Kullanım analizi veya reklam takibi yok."),
            ("code", "Açık kaynak", "Kodu herkes inceleyebilir."),
        ]),
        "shots": [
            ("today", "Sonraki reglin bir bakışta.", "Kendi kayıtlarına dayanan takvim tahminleri.", "today/regular_trLight.png", "coral"),
            ("diary", "Senin için önemli olanı kaydet.", "Ruh hâli, kanama, ağrı, belirtiler, notlar – istediğin gibi.", "day/filledInk_trDark.png", "dark ink"),
            ("history", "Zaman içindeki değişimleri gör.", "Bir haftadan bir yıla kadar grafikler.", "history/chartsSage_trLight.png", "sage"),
            ("modes", "Yumuşak veya canlı. Açık veya koyu.", "Dokuz renk. Seçim senin.", None, "duo"),
            ("ring", "Döngün bir halka içinde.", "Takvim ve halka arasında kaydır.", "today/ringPlum_trLight.png", "plum"),
            ("switch", "Uygulama mı değiştiriyorsun?", "Regl kayıtlarını dışa aktarım, CSV veya yedek dosyasından getir.", "onboarding/pastPeriods_trLight.png", "coral"),
        ],
        "duo": ("today/regular_trLight.png", "today/regularInk_trDark.png", "Açık · Mercan", "Koyu · Mürekkep"),
        "feature": ("Döngün telefonunda kalır.", "Regl takvimi ve günlük – ücretsiz ve açık kaynaklı.",
                    ["İnternet izni yok", "Hesap yok", "Reklam yok"], "today/regular_trLight.png"),
    },
    "fr": {
        "lang": "fr", "example": "Exemples d’entrées", "board": "Captures de FreePeriod",
        "privacy": ("Ton cycle reste sur ton téléphone.", [
            ("net", "Sans autorisation Internet", "L’appli ne peut rien envoyer en ligne."),
            ("user", "Sans compte ni pub", "Gratuite, sans inscription ni abonnement."),
            ("eye", "Sans pistage", "Ni analyse d’utilisation ni traceurs publicitaires."),
            ("code", "Code source ouvert", "Tout le monde peut vérifier le code."),
        ]),
        "shots": [
            ("today", "Tes prochaines règles en un coup d’œil.", "Des estimations du calendrier à partir de tes entrées.", "today/regular_frLight.png", "coral"),
            ("diary", "Note ce qui compte pour toi.", "Humeur, flux, douleur, symptômes, notes – à ta façon.", "day/filledInk_frDark.png", "dark ink"),
            ("history", "Observe tes cycles.", "Des graphiques d’une semaine à un an.", "history/chartsSage_frLight.png", "sage"),
            ("modes", "Doux ou vif. Clair ou sombre.", "Neuf couleurs. À ta façon.", None, "duo"),
            ("ring", "Ton cycle en anneau.", "Balaye entre le calendrier et l’anneau.", "today/ringPlum_frLight.png", "plum"),
            ("switch", "Tu changes d’appli ? Garde tes entrées.", "Importe un fichier d’export, un CSV ou une sauvegarde.", "onboarding/pastPeriods_frLight.png", "coral"),
        ],
        "duo": ("today/regular_frLight.png", "today/regularInk_frDark.png", "Clair · Corail", "Sombre · Encre"),
        "feature": ("Ton cycle reste sur ton téléphone.", "Calendrier des règles et journal – gratuits et open source.",
                    ["Sans autorisation Internet", "Sans compte", "Sans pub"], "today/regular_frLight.png"),
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
    for lang, t in T.items():
        suffix = "" if lang == "en" else f"-{lang}"
        (store / f"screenshots{suffix}.html").write_text(screenshots(t), encoding="utf-8")
        (store / f"feature-graphic{suffix}.html").write_text(feature(t), encoding="utf-8")
    print(f"written: screenshots and feature graphic for {', '.join(T)}")
