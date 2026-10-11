# FreePeriod. – release checklist v1.0.0

Status 2026-10-09: owner steps done, internal test positive, production release with versionCode 2
(versionCode 1 was used by the internal test upload).

Owner = Marvin (Play Console, keys, e-mail). Claude prepares everything else.

## Owner (one-time)
- [x] Support/privacy e-mail (dedicated Gmail) → replace `CONTACT_EMAIL` in `site/` and `store/`.
- [x] Developer name as shown on Play → replace `DEVELOPER_NAME`.
- [x] GitHub repo (public at launch, GPL-3.0) → replace `OWNER` in links; enable GitHub Pages
      via Settings → Pages → Source "GitHub Actions" (workflow `pages.yml` publishes `/site`) → privacy URL `https://pavoras.github.io/freeperiod/privacy/`.
- [x] Upload key: run `powershell -File tools\make-upload-key.ps1`, fill the two passwords in
      `%USERPROFILE%\dev-tools\keys\freeperiod-signing.properties`. Back the key up privately.
- [x] Play Console: create app "FreePeriod." (default language EN-US, add DE), free, category
      Health & Fitness. Enrol in Play App Signing (default).

## Claude (after owner steps)
- [x] Build: `gradle -Pfreeperiod.signing=<properties> :app:bundleRelease` → AAB; verify merged
      manifest (no INTERNET), size, versionCode 2 / versionName 1.0.0.
- [x] Store listing EN/DE from `store/listing-*.md`; screenshots + feature graphic from `store/`.
- [x] App content: privacy policy URL; Data safety per `store/data-safety.md`; Health apps
      declaration (menstrual cycle tracking); content rating questionnaire; target audience 13+;
      ads: none; news app: no; government app: no.
- [x] Internal testing track → install on owner's phone via Play → smoke test (onboarding, log,
      backup/restore, reminder, lock).
- [x] Testers (owner) – organisation account, no 12-tester/14-day rule.
- [ ] Production 100 % (first release, no existing users); after approval: Play badge + link on the site
      (`PLAY_STORE_URL`), tag `v1.0.0`. Repo is already public.
- Every new upload needs a higher versionCode.

## Not in v1.0 (by decision)
Fertile window (regulatory, spec rev 2 §11) · Clue/Flo import (waiting for example files) ·
widget/tile/PDF/Health Connect/auto backup (v1.1) · supporter purchase (later).

## v1.1 – release checklist (prepared 2026-10-11)

Two builds since 1.1: `play` (Google Play, with the optional supporter purchase) and `fdroid` (F-Droid, free
dependencies only, no purchase). Same package `org.freeperiod.app`, versionCode 3, versionName 1.1.0.

### Owner – Play Console, before the upload
- [x] Payments profile and EU trader details (done for the jaysay account).
- [ ] Monetize → Products → One-time products: create **`support_small`** (5 €) and **`support_large`**
      (10 €; the app consumes purchases so people can support again), names e.g. "Support FreePeriod" / "FreePeriod unterstützen", then activate.
      The ids must match exactly (`supportProductIds` in `app/src/main/java/org/freeperiod/app/support/`).
- [ ] License testing: add your Google account as a license tester (Settings → License testing) so test
      purchases cost nothing.

### Claude – build and texts
- [x] versionCode 3, versionName 1.1.0.
- [x] Store texts EN/DE `store/listing-*.md` (title without the dot – owner confirms), release notes 1.1.
- [x] Store screenshots re-rendered (ring and charts instead of "period" and the old history shot) and copied
      to `fastlane/` for F-Droid.
- [x] Privacy pages EN/DE prepared as `docs/release-1.1/site-privacy.patch` (automatic backup, supporter
      purchase, "FreePeriod" without the dot). **Apply on release day only**: `git apply docs/release-1.1/site-privacy.patch`,
      set "Effective"/"Gültig ab" to the release date, push (the site deploys on push).
- [x] Data safety notes in `store/data-safety.md` (1.1 section).
- [x] Signed AAB (built 2026-10-11 from 14b0190 + signing fix, jarsigner verified, no INTERNET, BILLING present): `:app:bundlePlayRelease` with `-Pfreeperiod.signing=<properties path>` →
      `app/build/outputs/bundle/playRelease/app-play-release.aab`; check versionCode 3 and that the merged
      manifest has no INTERNET (the gate checks both release manifests).

### Together – internal test track (purchase without INTERNET)
- [ ] Owner uploads the AAB to **Internal testing**, installs from Play on the phone.
- [ ] Settings shows "Support FreePeriod" with both prices → buy 5 € with the license tester → "Thank you!"
      appears; reopen the app → still "Thank you!" (restored from Play); Play Console → Order management →
      refund → after a while the thank-you is gone.
- [ ] No crash in the app or in Play Console → Android vitals after the purchase (the telemetry component runs
      without network permission).
- [ ] Smoke test of the rest: onboarding, log, ring, charts, backup/restore, reminder, lock.
- If the purchase fails or crashes: remove the purchase from 1.1 (flavor stays, product list empty) and ship.

### Play Console – app content for 1.1
- [ ] Data safety: stays "no data collected/shared" (purchase handled by Google Play, the app only reads
      "purchased yes/no" on the phone). Re-read the form's wording for in-app purchases when filling it in.
- [ ] Content rating questionnaire: answer "yes" to digital purchases / in-app purchases.
- [ ] Health apps declaration: medication logging is a diary entry ("log what you take"); tick it if the form
      lists medication tracking.
- [ ] Store listing: texts from `store/listing-*.md`, screenshots `store/png/`.
- [ ] Production rollout after the internal test; then tag `v1.1.0`, apply the privacy patch, F-Droid (below).

### F-Droid (see `docs/fdroid.md`)
- [ ] Tag `v1.1.0` (and `v1.0.0` on `83fa0dc` once Google approved 1.0).
- [ ] Owner: GitLab account → merge request to fdroiddata with the draft YAML (Claude prepares it).
