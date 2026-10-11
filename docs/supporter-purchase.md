# Supporter purchase (Google Play one-time product) – research 2026-10-11

Owner idea: a voluntary one-time "support FreePeriod" purchase on Google Play. Everything stays free; nothing
goes to the internet from our app. Not decided, research only.

## How it works
- Play Billing Library (current 9.1.0; new apps and updates need version 8+ from 2026-08-31, extension to
  2026-11-01 possible). The library talks to the Play Store app on the phone (IPC); the Play Store app does
  the network part and the payment. Our app never sees payment data.
- Flow for a non-consumable one-time product: BillingClient with `enablePendingPurchases(...)` →
  `queryProductDetailsAsync` (price comes localized from Play) → `launchBillingFlow` (Google's purchase sheet)
  → `onPurchasesUpdated` → grant only when `PURCHASED` (not `PENDING`) → `acknowledgePurchase` within 3 days,
  otherwise Google refunds automatically → `queryPurchasesAsync` on start/resume restores it (new phone too).
- Google "strongly recommends" verifying purchases on a backend. For a pure thank-you purchase that unlocks
  nothing, a client-only check is enough: a cheated "purchase" costs us nothing. No server, no INTERNET.
- Play Console: create the in-app product (e.g. 2.99 €, several price tiers possible), a payments profile
  (merchant account, bank account, tax info) is required to sell. Google keeps 15 % (first 1 M USD/year).

## What changes for the app
- Permissions: the library merges `com.android.vending.BILLING` into the manifest (install-time, no prompt
  for the user). It is a permission entry, though, not "none". INTERNET: the library is not documented to add
  it; our gate (`check.ps1 -Voll`) fails if INTERNET ever appears in the release manifest, so this is checked
  automatically before shipping.
- Two product flavors: `play` with the billing dependency and `fdroid` without (F-Droid rejects proprietary
  libraries; Play Billing is one). The supporter screen then exists only in the Play build; the F-Droid build
  could link to a donation page instead (F-Droid shows donation links from its metadata).
- Licence: the Play build would link a proprietary Google library into a GPL-3.0-only app. As the sole
  copyright holder the owner may do that; cleaner is a short linking exception for the Play Billing Library in
  the README/LICENSE notice, so outside contributors' code is covered too.
- Store/data safety: the purchase is processed by Google Play; our app transmits nothing. Per Google's Data
  safety help, payment data collected by the payment service itself does not have to be declared when the app
  never accesses it. Our app reads only "purchased yes/no" locally → "no data collected" should stay; reword
  the privacy pages ("purchases are handled by Google Play"). Re-check the form wording when it is real.
- Store text: "free, no ads, no subscription" stays true; add "optional one-time support".

## The part with real consequences: EU trader status (Digital Services Act)
Selling in-app products is a strong indicator that the developer is a "trader" under the DSA. Traders'
contact details (name, address, phone, email) are shown to EU users on the store listing. Sources disagree on
exactly which fields Play publishes for personal accounts – check the trader declaration in Play Console before
switching anything on. A business address or a PO box avoids publishing a home address.

## Open questions for the owner
- Does a published address/phone (trader status) bother you? That decides more than the code does.
- What does a supporter get? Suggestion: nothing locked; a thank-you note and maybe an extra accent colour.
- Price: one fixed amount or 2–3 tiers (e.g. 1.99 / 4.99 / 9.99 €)?

Sources: developer.android.com/google/play/billing/integrate, …/billing/release-notes,
support.google.com/googleplay/android-developer/answer/10787469 (Data safety),
…/answer/10840893 (contact information), f-droid.org/docs/Inclusion_Policy.

## Status 2026-10-11 – built for 1.1
Owner decisions: thank-you only, two products (`support_small` 5 €, `support_large` 10 €; owner 2026-10-11, prices per country come from Play), payments profile
and trader details set up (jaysay), download of Play Billing approved, INTERNET removed from the Play build.
Code: `app/src/main/java/org/freeperiod/app/support/` (interface, `NoSupporter`, product ids), `app/src/play/`
(`PlaySupporter`, manifest removing INTERNET), `app/src/fdroid/` (no purchase). UI: Settings › "Support FreePeriod"
(only when Play returns products or a purchase exists), About lists the billing library, the in-app privacy
page mentions the purchase in the Play build. Release steps and the internal-track test: `docs/release.md`.

### Licence note – added to the README (owner, 2026-10-11)
The Play build links the proprietary Google Play Billing Library into a GPL-3.0-only app. As copyright holder
the owner may distribute that; to make it explicit for everyone (also future contributors), the README's
licence section carries this GPL-3.0 section 7 additional permission:

> Additional permission under GNU GPL version 3 section 7: If you modify this program, or any covered work, by
> linking or combining it with the Google Play Billing Library (com.android.billingclient), containing parts
> covered by the terms of the Android Software Development Kit License Agreement, the licensors of this
> program grant you additional permission to convey the resulting work.
