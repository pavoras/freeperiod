# Owner requests (Marvin) – running list

Every idea or request Marvin writes goes here right away, before working on it, so nothing gets lost
while something else is in progress. Status: `[ ]` open · `[~]` in progress · `[x]` done (with commit).
Newest round on top. Decisions that change the product also go into the spec/handover.

## Round 2026-10-10 (evening, after the 3 ring drafts) – done in d56f525 

- [x] Home: cycle ring, direction A ("Perlenkette"), as a second view next to the calendar.
  - [x] In the ring view the card with cycle day + mini timeline goes away (redundant).
  - [x] Generous empty space ("Mut zur Lücke").
  - [x] Ring view: Period started / Log today buttons at the top (like the calendar view), stacked
        (full-width primary, Log today below) – owner prefers stacked over side by side.
  - [x] Ring view spacing: buttons too close to the top edge, everything cramped – lay it out with proper
        spacing (8 dp grid, buttons aligned with the card buttons of the calendar view, ring with margins).
  - [x] Switching calendar ↔ ring by swiping, simple and reliable (plus tappable dots; last view is remembered).
- [x] Day entry: appetite and sex drive (libido) trackable, can be turned on (off by default).
- [x] History: everything that can be a chart is a chart; selectable ranges (week, month, 3 months, year).
- [x] Bleeding (flow) shown as a chart too.
- [x] Empty day entry outside a period: bleeding preselected as "None" (display only, nothing saved).
- [x] Link to the day-entry settings from inside the day entry ("Kategorien anpassen" with gear, top right).
- [x] Setup: ask what to track and show the day-entry settings (new step "Was möchtest du erfassen?").
- [x] Tap on the big cycle-day number on Today, or on the "Heute" tab, brings the calendar back to today.
- [x] General rule: keep this list of all ideas and requests (this file).

## Still open from earlier

- [ ] Owner checks the PDF summary on a device (Robolectric can't render PdfDocument).
- [ ] Release prep 1.1: store texts (medication log, pain diary, PDF, charts, ring), privacy pages,
      Play health declaration check (`docs/release.md`).
- [ ] Widget (planned for 1.2).
