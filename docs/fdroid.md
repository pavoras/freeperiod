# FreePeriod on F-Droid – preparation

Status 2026-10-11: repo prepared, submission open (owner decision: prepare soon).

## Done in the repo
- `fastlane/metadata/android/{en-US,de-DE}/`: title, short and full description (1.1 feature set, "FreePeriod"
  without the dot in running text), changelogs `2.txt` (1.0.0) and `3.txt` (1.1.0), icon, feature graphic and
  the six store screenshots. The screenshots are copies of `store/png/`: after `python tools/render-store.py`
  run `python tools/fastlane-images.py` so both stay the same.
- Gradle wrapper (`gradlew`, `gradle/wrapper/`, Gradle 8.11.1 over https). Our own gate keeps using the local
  Gradle; F-Droid's build server reads the wrapper version.
- `dependenciesInfo` off: no Google-signed dependency blob in APK/AAB (F-Droid's scanner rejects it).
- The `fdroid` flavor's dependencies are all FOSS (AndroidX, Kotlin, kotlinx, Room, WorkManager, Biometric); no
  Play Services, no Firebase, no network. Unsigned release build works without the signing properties (F-Droid signs itself).

## Still to do
1. Tag releases: `v1.0.0` on `83fa0dc` (after Google's approval, see handover) and `v1.1.0` on the 1.1 release
   commit. F-Droid builds tags (`UpdateCheckMode: Tags`).
2. Submit (owner, needs a GitLab account): either a merge request to
   https://gitlab.com/fdroid/fdroiddata with the file below as `metadata/org.freeperiod.app.yml`, or a
   "request for packaging" issue at https://gitlab.com/fdroid/rfp. The MR route is faster; Claude can prepare
   it and walk through `fdroid build` / `fdroid lint` if the owner wants to run fdroidserver locally.
3. Signing: F-Droid signs with its own key, so Play and F-Droid installs can't update each other (same
   package name, different signature; switching needs backup → uninstall → install → restore). Optional later:
   reproducible builds so F-Droid ships our signature.
4. Flavors (since 1.1): `play` contains the supporter purchase (Play Billing, proprietary), `fdroid` has only
   free dependencies and no purchase. F-Droid builds `gradle: [fdroid]`; the gate builds `assembleFdroidRelease`
   and checks its manifest. The scanner should find no Google libraries in that variant.

## Draft metadata (`metadata/org.freeperiod.app.yml` in fdroiddata)
```yaml
Categories:
  - Sports & Health
License: GPL-3.0-only
AuthorName: Marvin
AuthorEmail: support@jaysay.it
WebSite: https://pavoras.github.io/freeperiod/
SourceCode: https://github.com/pavoras/freeperiod
IssueTracker: https://github.com/pavoras/freeperiod/issues

AutoName: FreePeriod.

RepoType: git
Repo: https://github.com/pavoras/freeperiod.git

Builds:
  - versionName: 1.1.0
    versionCode: 3
    commit: v1.1.0
    subdir: app
    gradle:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 3
```
Check the category list and the author name with the owner before submitting (F-Droid renamed categories
in 2024; use whatever `fdroid lint` accepts).
