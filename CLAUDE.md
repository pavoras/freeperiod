# FreePeriod. – notes for Claude

Start here: `docs/handover.md` (current state, release status, commands, open tasks).

- Rules for code and texts: `AGENTS.md` (they apply to Claude too, except that Claude commits,
  pushes and records screenshots). Codex rounds: `aufpasser/codex-projekt.md` + `codex-delegation` skill.
- Before committing app code: `tools\check.ps1 -Voll` green; look at every re-recorded screenshot.
- Long runs in the background with a watcher; report progress, never let a run end unnoticed.
- Never read or print anything under `%USERPROFILE%\dev-tools\keys`; pass only paths.
- Keep `docs/handover.md` current when the state changes (release, decisions, open tasks).
- Answer the owner (Marvin) short; German or English as he writes.
- Every idea/request Marvin writes goes into `docs/owner-requests.md` at once (also mid-task); work from that list.
