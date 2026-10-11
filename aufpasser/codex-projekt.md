# Codex in FreePeriod. – project addendum to skill `codex-delegation`

Same conventions as JaySay (`Diktier App/aufpasser/codex-projekt.md`) unless stated here.
Fixed rules for Codex live in `AGENTS.md` (created in M0; do not repeat them in prompts).

## Roles
- Claude = supervisor ("Aufpasser"), owner = Marvin. Short answers to Marvin.
- Since round 24 Claude is the main developer (owner prefers raising the Claude plan over running
  out of Codex). Codex only for big rounds it does better; Claude plans, reviews, verifies, commits.
- Project state for a new session: `docs/handover.md`.

## Models
- Coding: `gpt-6.1-sol`, effort `high`; `xhigh` only after asking Marvin.
- Design/judgement/review/images: `gpt-6-astra`.
- Never: `gpt-6-luna`, `gpt-6-sol` (6.0), `ultra`, effort below `high`.

## Paths and commands
- Repo: `C:\Users\kempe\Desktop\Projekte\Period Tracker`. Rounds: `aufpasser/runden/prompt-N.txt`,
  `runde-N.md|.jsonl|.log` (jsonl/log git-ignored).
- Start (fresh, detached), from repo root:
  `Start-Process powershell -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-Command',"Set-Location '<repo>'; Get-Content -Raw -Encoding utf8 aufpasser\runden\prompt-N.txt | codex exec -m <model> -c model_reasoning_effort=high --json -C '<repo>' -s workspace-write -c approval_policy=never -o aufpasser\runden\runde-N.md - > aufpasser\runden\runde-N.jsonl 2> aufpasser\runden\runde-N.log" -WindowStyle Hidden`
- Review-only rounds: `-s read-only`.
- Resume: `--add-dir` is NOT inherited. Pass the Gradle cache explicitly:
  `codex exec resume <thread> -m <model> -c model_reasoning_effort=high -c sandbox_mode=workspace-write -c approval_policy=never -c 'sandbox_workspace_write.writable_roots=["C:\\Users\\kempe\\dev-tools\\gradle-home"]' --json -o … -`

## Round log
| Round | Model/effort | Thread | Result |
|---|---|---|---|
| 1 | gpt-6-astra/high, read-only | 01a11748-43fb-7d42-91e4-7c5864544b74 | Critical review of brief → spec v1 (fertility opt-in v1.1, explicit periods, scope split, one-time supporter) |
| 2 | gpt-6-astra/high, read-only | 01a1175b-18da-7b31-ab82-83a714771403 | Plan review: 10 must-fix (CycleUse, addPeriod, reminder keyed by period, restore atomic incl. domain settings in Room, crypto bounds, coverage, rounding, DB FKs/mutex, backup rules, offline readiness) → plan amended |
| 3 | gpt-6.1-sol/high | 01a1176e-8211-79a2-b7f9-496cc769cad6 | Engine Tasks 1–3: 69 tests green, +1041 lines; reviewed Prediction + BackupCodec OK; commit 552d287 |
| 4 | gpt-6.1-sol/high | 01a11785-e4e0-7950-bcac-280ebc04dac8 | Core Tasks 4–6: 103 tests, 12 Today screenshots OK; full gate green; commit 8e58f58 |
| 5 | resume of 4 | 01a11785-… | History + Day entry (Tasks 7–8): 136 tests, screenshots OK; resume lost gradle-home write access (Codex used local cache); commit 95d3c17 |
| 6 | gpt-6.1-sol/high fresh | 01a117c2-4b22-7b11-85e1-a26f717328bf | Recovery Task 9: 162 tests; MoodIconTest removed (Robolectric capture timeout); commit cef96fa |
| 7 | gpt-6-astra/high read-only | – | 3 design directions (Full Stop / Dayline / Soft Forms) |
| 8 | resume of 6 | 01a117c2-… | Device Tasks 10–12: 198 tests; commit dcd96dd |
| 9 | gpt-6-astra/high | 01a11813-0a4c-7020-8b81-854675f0a080 | 3 design drafts as HTML boards (draft 1 = owner notes: soft, grounded sans, timeline/ruler, calm, subtle) |

| 10–11 | astra resume of 9 | 01a11813-… | Combined draft 4 → refined draft 5 (final design reference) |
| 12 | gpt-6-astra/high read-only | 01a1189b-8ee2-7d40-916d-6938f4702311 | Review spec rev 2 + plan part 2 → 14 binding decisions |
| 13 | gpt-6.1-sol/high | 01a118a0-a60d-7e90-83bb-a61633d096de | Round A engine v2 (parallel to 14) |
| 14 | gpt-6-astra/high | 01a118a0-e435-70d1-bd90-18c211b2f96e | Round B1 design system (all screens except Today) |
| 15 | resume of 14 (astra) | 01a118a0-e435-… | Today v2 design (7.7 M input) |
| 16 | resume of 13 (sol) | 01a118a0-a60d-… | Database v2, MIGRATION_1_2, backup schema 2 (8.9 M) |
| 17 | resume of 13 (sol) | 01a118a0-a60d-… | My situation, reminder engine, day-entry customization (13.3 M) |
| 18 | resume of 14 (astra) | 01a118a0-e435-… | Today polish (12.9 M) |
| 19 | resume of 13 (sol) | 01a118a0-a60d-… | Onboarding v2 + history v2 (18.4 M); commit ef8af3c |
| 20 | resume of 14 (astra) | 01a118a0-e435-… | Settings polish + launcher icon (19.6 M); commit ef8af3c |
| 21 | resume of 13 (sol) | 01a118a0-a60d-… | Fixes, import in onboarding + CSV preview, release signing (24.5 M). Claude fixed 6 red tests (drag bug, Robolectric dialog text fields, Room executors); commit 5523371 |
| 22 | resume of 14 (astra) | 01a118a0-e435-… | Website, privacy policies, store listing + artwork (22.1 M); commit 9ff1e9d |

| 23 | gpt-6.1-sol/high fresh | (see runde-23.jsonl) | Higher-chance-of-pregnancy days (default on, spec §12) + medical disclaimer, +303 lines. Claude: fixed situation-switch persistence bug, poisoned Gradle build cache (caching off), readability pass, period block, feedback mail, chip grid |
| 24 | – cancelled | – | Design polish done by Claude instead (Codex quota 39 % on day 2; owner: Codex only for what it does better) |
| 25 | gpt-6.1-sol/high fresh | (see runde-25.jsonl) | 1.1 "Customize everything" Stage A+B: own items as tags in `builtin:<field>` marker categories, `EntrySelection`/`EntryAppearance`/`EntryCsv`, Room 3, backup schema 3, CSV diary import (+1,457 lines; 7.3 M input, quota 88 → 90 %). Claude fixed 5 tests the sandbox couldn't run (Robolectric NATIVE graphics), icons only where meaningful, symptom order, one-row item editor; commit c1baa6a |

| 26 | gpt-6.1-sol/high fresh | (see runde-26.jsonl) | Sample backup for owner testing: `engine/src/test/.../SampleBackupTest.kt` writes `engine/build/sample/freeperiod-sample.fpbackup` (password `freeperiod`, 14 months relative to today; +265 lines; Codex weekly quota 13 % after reset) |

Automatic backup (local folder, Keystore-sealed password, WorkManager) was built by Claude in parallel on
branch `auto-backup` and merged after round 25.

**Claude-only work since round 24 (no Codex):** 6–8 day higher-chance window around the middle of the
prediction (Wilcox 1995/2000, Bull 2019); Today calendar scrolls freely, snaps to week rows; Today fits one
screen (`OneScreenColumn`: calendar takes the rest, legend always visible; large fonts keep 3 weeks and scroll);
pregnancy-chance hint moved from the card into the legend; icon set 24 → 102 picker icons in 10 groups
(`FpIcons.groups`, `ic_item_*`, spoken names `icon_*`), icon choice also for categories, "sad" is now a face.
Icon source paths: Claude's generator script (scratchpad) – the XML drawables are the source of truth.
Owner decision 2026-10-09: calendar marks period days by chance (normal start spread × period length):
≥ 60 % "likely", ≥ 30 % "possible"; the period after next with spread × √2. CSV export is one file (one row
per day, `period_day`), re-importable. Settings: measured cycle length, archived categories restorable,
lock intro + 10/15 min.
Owner feedback 2026-10-09 (later): the period after next counts as "possible" from 20 % (short periods with
spread × √2 never reach 30 %); always exactly two periods are predicted. Today uses the same accent as every
screen (light fills too pale on the Today surfaces get a thin border edge instead of a darker shade). Ink is the
dark, non-pastel alternative (light #2F3440 with light text, dark graphite #9A9EA6); onAccent follows fill
luminance. Appearance preview: more space between sample days and below the badge.
Release 2026-10-09: plural forms EN/DE, 19 unused strings removed; v1.0.0 versionCode 2 (commit 83fa0dc)
submitted to Play production review. GitHub account renamed to `pavoras` (site pavoras.github.io/freeperiod).

**Decision 2026-10-08:** both threads are too long (resumes cost 2–4 % weekly quota each). Next rounds start
fresh with a short handover. Codex quota must last until the reset (Wed 14.10. 10:15); Claude takes small
and medium work, Codex only big rounds. `~/.codex/config.toml`: `model_auto_compact_token_limit = 150000`.

Device: Marvin's S22 Ultra via Wi-Fi debugging (`adb connect 192.168.178.21:<port>`; port changes, pairing already done for this laptop).

Start rounds with `aufpasser\runden\start.ps1 -N <n> [-Model m] [-Resume id] [-ReadOnly]`.
HTML mockups → PNG: headless Edge (`msedge --headless=new --screenshot=… --window-size=…`) from a temp dir without spaces.

## Toolchain notes
- Gradle 8.11.1 at `C:\Users\kempe\dev-tools\gradle-8.11.1` (copied from JaySay), JDK 17, SDK 36, shared
  `dev-tools\gradle-home` cache (filled online by Claude via `tools\check.ps1 -Voll -Online`).
- WorkManager's ACCESS_NETWORK_STATE/FOREGROUND_SERVICE + SystemForegroundService removed in manifest.
