# Proposed Google Play declarations for FreePeriod. 1.0.0

Package: `org.freeperiod.app`  
Developer organisation: `JaySay (Marvin Kemper)`  
Contact: `support@jaysay.it`  
Audience: ages 13 and up  
Prepared: 2026-10-08

This is a submission worksheet, not a claim of Google approval. It uses the supplied release facts and the repository's Android backup rules. Current Play Console wording and policy guidance have not been checked online in this offline round. The account owner should match these proposals to the current form and the final release artifact.

## Data safety: proposed answers

| Topic | Proposed answer | Reasoning / qualification |
|---|---|---|
| Does the app collect user data? | No | Entries, settings, estimates and reminder schedules are processed on the device. No developer server, account, network permission, analytics SDK or crash-reporting SDK. |
| Does the app share user data? | No | No automatic transfer to the developer, advertising partners or analytics providers. User-directed file operations are addressed separately below. |
| Data types collected/shared | None | The app **handles** sensitive health and sex-life entries locally; this is not a claim that no sensitive information exists. Under the proposed interpretation, local-only processing is not off-device collection. |
| Encryption in transit | Not applicable | No app-to-developer network transport exists. Password-protected backup files concern file encryption, not transport encryption. Do not invent a TLS claim to fill a conditional form field. |
| Deletion | In-app deletion is available | Settings → Delete all data removes local app data; uninstalling removes private app data from that phone. Exported files and other-device copies require separate deletion. |
| Account creation | No | No registration, sign-in or developer-held account. Account-deletion questions should be answered as not applicable where the form permits. |
| External account-deletion page | Not applicable to accounts | The public privacy page explains local deletion. There is no server-side account deletion endpoint to provide. |
| Independent security review / certification | No claim | No such assessment or certification has been supplied. Open source and encrypted backup support are not independent security certification. |

### File picker and Android transfers

The user explicitly chooses to export/import CSV or create/open an encrypted backup through Android's system file picker. CSV is readable text; encrypted backups require a user-chosen password. The developer does not receive these files.

A cloud-backed document provider can upload or download a file through its own app. The lack of FreePeriod.'s INTERNET permission does not prevent that other app from using the internet. The proposed “no collected/shared data” answers depend on the current Play treatment of explicit user-initiated file transfers. Confirm that treatment against the actual picker flow and current guidance; do not assume every external transfer is exempt simply because the app lacks INTERNET permission.

Android backup rules exclude automatic cloud backup. Supported Android versions permit system-managed device-to-device transfer. Review the final manifest and backup rules without interpreting `allowBackup=true` alone as permission for cloud backup. No file upload service or cloud backup is part of the app. From 1.1 the user can turn on an optional automatic backup: on a daily or weekly schedule the app writes an encrypted backup file (user-chosen password, sealed with an Android Keystore key on the device) into a folder the user picked in the system folder picker, and keeps the newest five. If that folder belongs to a cloud app, that app uploads the file, exactly as with a manually saved backup; FreePeriod. still has no network access and the developer receives nothing.

### Google Play diagnostics, support and the website

Google Play may supply its own crash/ANR reports or statistics to the developer. FreePeriod. embeds no analytics or crash SDK. Platform diagnostics are disclosed in the privacy policies and must not be hidden behind an absolute “the developer never sees any data” claim. Verify the current Console treatment of Google-managed platform diagnostics; update the proposed answers if any diagnostic transmission is performed by the app or an included SDK.

Support messages deliberately emailed by users and technical requests to the GitHub Pages website are separate from in-app diary processing. The policies cover them separately. There is no in-app support form or automatic diary attachment in these release facts. Reassess the answers if that changes.

## Health apps declaration

- Declare the menstrual/period tracking feature in the current applicable category. The app records periods, symptoms, mood, pain and related diary entries, with calendar estimates and optional reminders.
- Do not select “no health features” merely because all processing is local.
- Optional life-phase, method, pill-pack and ovulation-test **logging** provides no contraception guidance, medication dosing advice or treatment recommendations. The app shows days with a higher chance of pregnancy as a calendar estimate (on by default, can be switched off, hidden for hormonal methods, pregnancy and menopause), always next to the "not a medical device" disclaimer (owner decision 2026-10-08, spec §12).
- Do not market fertility prediction, pregnancy detection, diagnostic accuracy, disease management or contraceptive efficacy.
- Product position: not a medical device. No CE marking, FDA clearance or other certification is claimed.
- Use this wording consistently: “FreePeriod. records what you enter and shows calendar estimates. It does not diagnose conditions or recommend treatment.” Add that it is not for contraception and that estimates can differ from actual cycles.
- Supply the public privacy URL, organisation identity and contact information. Any form questions about medical-device approval must follow this non-medical product position and the actual release functionality, not a guessed registration number.

## Content rating questionnaire hints

Complete the live IARC questionnaire from the actual content and functionality. These are factual prompts, not a preselected age rating:

- Health and reproductive information is present. Optional fields include sexual activity, discharge and ovulation-test results. Describe these honestly wherever the questionnaire asks about sexual/reproductive content or references.
- Text is neutral and informational. There is no app-supplied pornographic imagery, sexual entertainment, violence, gambling or substance promotion in the stated feature set.
- Notes and custom labels are private user-authored content, not a public feed or messaging service. Answer distinctions between private entries and public user-generated content according to the live question wording.
- No ads, subscriptions, in-app purchases, public chat or social sharing feed are part of v1.0. User-directed file export is still possible.
- Do not promise an IARC/PEGI/USK rating before the questionnaire returns it. Store content rating and intended audience are separate declarations.

## Target audience: 13+

Select the available age bands from 13 upward that match the intended release audience, not any under-13 band. Keep the website, privacy policies and listing consistent with ages 13 and up. Do not describe the app as designed for young children or make an unsupported Families-program claim.

A 13+ audience selection is not a blanket statement about minors' consent under the GDPR: applicable ages and rules can differ by country. The app has no account or developer-side health diary collection. Revisit the privacy and age analysis before introducing cloud accounts, remote health processing or other changes involving minors' data.

## Publication handoff

1. Replace `JaySay (Marvin Kemper)`, `support@jaysay.it` and `pavoras`; confirm the organisation's legal identity/contact details and the website/email arrangements described in the policies.
2. Host `site/` with a public, accessible policy at both language URLs. Proposed GitHub Pages URLs: `https://pavoras.github.io/freeperiod/privacy/` and `https://pavoras.github.io/freeperiod/privacy/de/`.
3. Compare these answers with the final manifest, dependencies, file-picker flows, backup rules and actual Google Play diagnostic setup. Final review of legal wording and Console declarations belongs to the publishing organisation.
4. Submit the current questionnaires and keep the answers consistent with each shipped update. No declaration has been submitted by this round.

## 1.1 additions (2026-10-11)

- **Supporter purchase (Play build only):** two optional one-time products (`support_small` 4.99 €,
  `support_large` 9.99 €) that unlock nothing. Google Play handles purchase and payment; the app uses the Google
  Play Billing Library, which talks to the Play Store app on the phone over IPC. The app reads only whether a
  purchase exists, on the phone, and sends nothing anywhere. Proposed Data safety answer: unchanged, "no data
  collected or shared" – per Google's help, payment data collected by the payment service itself need not be
  declared when the app never accesses it. Re-check the live form wording for "purchase history".
- **No INTERNET:** Play Billing 9.1.0 brings Google's telemetry transport (`com.google.android.datatransport`),
  which requests `android.permission.INTERNET`. The Play build removes that permission in its manifest
  (`app/src/play/AndroidManifest.xml`, `tools:node="remove"`); the gate fails if INTERNET ever reaches a
  release manifest. The telemetry component stays in the APK but cannot reach the network.
- **Content rating:** answer "yes" to in-app/digital purchases.
- **F-Droid build:** no billing library, no purchase, no Google components.
- **Automatic backup:** already described above (encrypted file into a user-chosen folder; no network access).
