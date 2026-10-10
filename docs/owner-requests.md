# Owner requests (Marvin) – running list

Every idea or request Marvin writes goes here right away, before working on it, so nothing gets lost
while something else is in progress. Status: `[ ]` open · `[~]` in progress · `[x]` done (with commit).
Newest round on top. Decisions that change the product also go into the spec/handover.

## Round 2026-10-10 (late, after testing dev.51) – done

- [x] Ring view: buttons back below the ring, stacked, as in the first dark version (owner liked that).
- [x] Day entry: the arrows change the day, but the new day slides in from the bottom → slide in from the
      right (next day) or left (previous day), matching the arrows.
- [x] Day entry order: sex drive right below Sex; appetite below Symptoms; "Effect on your day" (Alltag)
      below Symptoms; Tags below the note. New default order; turning appetite / sex drive / the pain diary
      on puts them there, also after a custom order (owner's phone: switch appetite and sex drive off and on once).
- [x] Day entry: appetite and sex drive sections can't be collapsed again → collapsible like the others.
- [x] History: a category hidden in Customize day entry also hides its chart.
- [x] Charts customizable: per category in Customize a "chart" choice next to One / Several / Count; when on,
      History draws a chart for it. Scale = the category's items, highest on top, lowest at the bottom; existing
      orders adjusted to match. Example: add "Very strong" to Pain, move it to the very top → the chart gets a
      new row above "Severe".
      Owner decided: a switch per category (built-ins too), for pick-one (levels) and count (number per day);
      pick-several gets no chart. Day-entry chips stay lowest first; only the Customize list shows highest on top.
- [x] No screenshot re-recording (and no APK) until the owner finds no more bugs in this test round.
- [x] Running text: write "FreePeriod" without the dot (it breaks the reading flow); the dot stays only in the
      wordmark/logo.
- [ ] Hold the APK until the owner has finished the current test (owner, 2026-10-10 late). Test done; owner: implement
      ALL requests of this list first, then one final APK for testing.
- [x] Version number only in "About the app", not at the bottom of Settings.
- [x] Ring day numbers 1/8/15/22/29 (every 7th day) are not self-explanatory → removed (tried: no numbers looks bad).
- [x] Ring numbers instead: every 5th calendar date (5, 10, 15, 20, 25, 30), numbered by the month's date, so
      tapping the dot next to "15" opens the 15th.
- [x] Ring centre: sentence under the number a little higher; check left/right balance visually (the "22" looked cramped).

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
