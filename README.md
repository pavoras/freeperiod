# FreePeriod.

A free, private period tracker for Android. No account. No ads. No subscription. No internet
permission – your data stays on your phone.

- Period calendar with estimates for the next two periods, based on your own entries
- Daily diary: mood, flow, pain, symptoms, discharge, tags, notes and your own categories
- Cycle history, reminders, optional app lock, encrypted backups and CSV export/import
- English and German, light and dark, six accent colours

FreePeriod. is not a medical device and not a method of contraception.

Website: https://pavoras.github.io/freeperiod/ · Privacy: https://pavoras.github.io/freeperiod/privacy/

## Development

- Project state and handover: `docs/handover.md`
- Spec: `docs/superpowers/specs/2026-10-07-freeperiod-design.md` (+ rev 2 `2026-10-08-freeperiod-v1-scope-2.md`)
- Plan: `docs/superpowers/plans/2026-10-07-freeperiod-v1.md`
- Build/check: `powershell -File tools\check.ps1 -Schnell` (or `-Voll`)
- Store artwork: `python tools/render-store.py` (renders `store/*.html` to `store/png/`)

Licence: GPL-3.0-only (see `LICENSE`).
