# CheckIn — Google Play Store Release Checklist

An exhaustive, solo-dev playbook to take **CheckIn - Solopreneur Tracker** (published as
`com.nexusai.checkin.app`) from source to a production Google Play listing that real users can
install. Nothing here is assumed done; every box is unchecked. Work top to bottom — later
sections depend on earlier ones.

> **Status legend:** `[ ]` = not started · `[~]` = in progress · `[x]` = done
> **Tag legend:** **[BLOCKER]** submission is impossible without it · **[POLICY]** rejection/suspension risk
> · **[QUALITY]** affects approval odds, ratings, retention · **[NICE]** optional polish

**Facts pulled from this repo (verify before you rely on them):**

| Thing | Current value in repo | Target for release | Notes |
|---|---|---|---|
| `applicationId` | ✅ `com.nexusai.checkin.app` | done | Set on the `launch-hardening` branch; namespace kept `com.checkin.app`. FileProvider authority auto-tracked. |
| `versionCode` / `versionName` | `20260713` / `1.0` | `YYYYMMDD` / SemVer | Centralized in `gradle.properties`; `versionCode` = release-day date (must strictly increase every upload); `versionName` is SemVer. |
| `compileSdk` / `targetSdk` | `35` / `35` | `35` / `35` | Meets the API-35 floor. API 36 becomes mandatory **31 Aug 2026**. |
| `minSdk` | `34` (Android 14) | `34` (**kept — [D3]**) | Reaches only Android 14+; accepted trade-off for zero legacy code. |
| Release signing | ✅ wired (keystore.properties + debug fallback) | generate keystore | Gradle wiring done; **you still generate the upload keystore + enroll in Play App Signing** — see [4.1]. |
| `isMinifyEnabled` (release) | ✅ `true` (+ `shrinkResources`) | done | R8 + resource shrinking on; release bundle builds green. |
| Permissions | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `CAMERA`, `USE_BIOMETRIC` | unchanged | No `INTERNET` → strong "no data collected" story. |
| FGS type | `specialUse` (subtype `check_in`) | unchanged | **Highest-risk review item — see [Section 5].** |

---

## Locked decisions (Part 0) — decided 2026-07-05

These are settled; the rest of the doc is aligned to them.

| # | Decision | **Chosen value** | Consequences baked into this doc |
|---|---|---|---|
| **D1** | App title (≤30 chars) | **CheckIn - Solopreneur Tracker** (29) | Store title, listing text, splash/brand copy. |
| **D2** | `applicationId` (permanent) | **`com.nexusai.checkin.app`** | One-line `applicationId` change; namespace stays `com.checkin.app` ([4.1a]). |
| **D3** | Minimum SDK | **Keep `minSdk 34`** (Android 14+) | No legacy code paths; smaller audience accepted. |
| **D4** | Account type | **Organization** — legal **"AI NEXUS CONSULTING FZ-LLC"**, D-U-N-S already held | **Exempt from the 12-tester/14-day mandate** ([Section 10] is now optional). Needs org verification. |
| **D5** | Pricing / devices / regions | **Free** (IAP possible later) · **Phone + Tablet/foldable** · **All countries** | Merchant/Billing setup deferred ([12.1]); tablet 7"/10" screenshots required ([Section 8]). |
| **D6** | Public identity | Display name **"Nexus AI Technology Labs"** · contact **saksham@nexusai.world** · policy hosted on **nexusai.world** | Brand ≠ legal name is fine; legal name may still show in the developer-transparency section ([1.6]). Display name **set in Console 2026-07-13**. |

> **On D6 (brand vs. legal name):** the **verified organization name** on the account must equal
> the D-U-N-S registration (**AI NEXUS CONSULTING FZ-LLC**); the **public Developer display name**
> is a separate field set to **Nexus AI Technology Labs**. Google may still surface the verified legal name +
> address + email in the "About the developer" transparency block — expected, not a problem.

---

## Table of contents

1. [Paperwork: Google Play developer account & identity](#1-paperwork-account--identity)
2. [External dependencies you must create/host](#2-external-dependencies)
3. [Legal & policy documents](#3-legal--policy-documents)
4. [App code & build changes](#4-app-code--build-changes)
5. [Foreground service `specialUse` declaration](#5-foreground-service-specialuse-declaration)
6. [Permissions & prominent disclosure](#6-permissions--prominent-disclosure)
7. [Data safety form](#7-data-safety-form)
8. [Store listing assets](#8-store-listing-assets)
9. [Content rating & app content declarations](#9-content-rating--app-content-declarations)
10. [Testing tracks (optional for Organization accounts)](#10-testing-tracks)
11. [Pre-launch report, quality & vitals](#11-quality--vitals)
12. [Production release & rollout](#12-production-release--rollout)
13. [Post-launch & ongoing obligations](#13-post-launch--ongoing)
14. [Nice-to-haves](#14-nice-to-haves)
- [Appendix A: end-to-end critical path](#appendix-a-critical-path)
- [Appendix B: quick command reference](#appendix-b-command-reference)

---

## 1. Paperwork: account & identity

Registering as an **Organization** under **AI NEXUS CONSULTING FZ-LLC** (D-U-N-S already held).

> **✅ Status — as of 2026-07-13:** $25 registration paid; **Organization verification cleared**;
> **phone verified**; account live as **Nexus AI Technology Labs** (Organization, Account ID
> `4751942053339775952`). **App entry created 2026-07-13** — app name **CheckIn - Solopreneur
> Tracker** reserved. All of §1 is done; only the package-name claim remains (first `.aab` upload).
>
> **⚠️ Correction to the old assumption:** the **Create app** dialog reserves the **app *name***, not
> the **package name**. The current Console has no package-name field there — `com.nexusai.checkin.app`
> is claimed only when you **upload the first App Bundle to a track** (e.g. Internal testing, [Section
> 10]). So: Create app now → then build + upload the `.aab` to actually claim the package.

- [x] **[BLOCKER] Use/create the publishing Google account** tied to **saksham@nexusai.world**
      (or the account that will own the FZ-LLC's Play presence).
- [x] **[BLOCKER] Register the Play Developer account as Organization** at play.google.com/console —
      one-time **$25 fee**. **Paid.**
- [x] **[BLOCKER] Organization verification.** Provide the **legal name "AI NEXUS CONSULTING
      FZ-LLC"** exactly as registered, plus the **D-U-N-S number** (you already have it — good,
      this removes the usual multi-week D-U-N-S wait), business address, and contact details.
      **Verified 2026-07-13.**
- [x] **[BLOCKER] Verify contact email + phone** on the account. **Both verified 2026-07-13.**
- [x] **Set the public Developer display name to "Nexus AI Technology Labs"** (separate from the
      verified legal name). **Done.**
- [ ] **[1.6] Confirm the developer-transparency block** — Google may publicly show the verified
      legal name (**AI NEXUS CONSULTING FZ-LLC**), address, and **saksham@nexusai.world** in the
      "About the developer" section. Ensure that address/email is one you're comfortable showing.
- [ ] **Payments profile.** Not required to launch **Free**, but you'll need a **merchant/payments
      profile** before adding the future **in-app purchases** ([12.1]). Optionally set it up now
      while you're in the verification flow.
- [x] **Accept the Developer Distribution Agreement** and current program policies. **Done.**
- [x] **Enable 2-Step Verification** on the publishing account. **Done.**
- [~] **[BLOCKER] Reserve `com.nexusai.checkin.app`.** **Create app** now reserves the app *name*
      (**CheckIn - Solopreneur Tracker**); the **package name is claimed on first `.aab` upload to a
      track** ([Section 10]), not here. So this stays `[~]` until that first upload.

---

## 2. External dependencies

Things that live **outside** the app/repo and must exist before submission.

- [ ] **[BLOCKER] Host the privacy policy on nexusai.world.** A stable, public, non-expiring URL
      (e.g. `https://nexusai.world/checkin/privacy`). Content in [Section 3]. Since you own the
      domain, this is straightforward — just publish a static page.
- [ ] **[POLICY] Support/contact email = saksham@nexusai.world** shown on the listing.
- [ ] **[NICE] A short support/landing page** on nexusai.world (`/checkin`) — what the app does,
      privacy summary, and a support link. Good trust signal for an Organization listing.
- [ ] **[NICE] Host the demo/justification video** for the `specialUse` FGS and the camera
      prominent-disclosure review (unlisted YouTube link works). See [Section 5]/[Section 6].
- [ ] **[NICE] Testers list** — only if you choose to run a testing track ([Section 10]); as an
      Organization you are **not** required to. A small internal-testing group is still useful.
- [ ] **[NICE] Feedback channel** (email to saksham@nexusai.world, or Play tester feedback).

---

## 3. Legal & policy documents

- [ ] **[BLOCKER] Write the privacy policy** (host per [Section 2]). For CheckIn the honest,
      simple truth is a strong asset — everything is on-device. It **must** state, at minimum:
  - App name (**CheckIn - Solopreneur Tracker**), developer (**Nexus AI Technology Labs / AI NEXUS
    CONSULTING FZ-LLC**), contact (**saksham@nexusai.world**).
  - **What is collected:** attendance times and transient face frames are processed **entirely on
    the device**; face images are **deleted immediately** after detection; **no data is
    transmitted off the device** (no `INTERNET` permission, no backend).
  - **Camera use:** front camera is used **only** for on-device presence verification (ML Kit
    face detection); images are not stored, uploaded, or shared.
  - **Biometrics:** device biometric/`BiometricPrompt` is a local fallback; the app never
    receives biometric data (the OS handles it).
  - **Data retention & deletion:** attendance data lives in local storage; uninstalling / clearing
    data removes it; CSV export is the user's own copy.
  - **Backup note:** if `allowBackup` stays `true`, the local DB may be in the user's own
    Android/Google backup — mention it (see [4.7]).
  - **Children:** general/adult productivity audience, not directed at children.
  - **Changes & effective date.**
- [ ] **[POLICY] Consistency check:** every data category and permission in the policy must exactly
      match the **Data safety form** ([Section 7]) — mismatches are the #1 rejection cause.
- [ ] **[NICE] Terms of Use / EULA** — optional for a free, no-account, on-device app.

---

## 4. App code & build changes

> **✅ Status — landed on the `launch-hardening` branch (build-verified via `bundleRelease`):**
> `applicationId` → `com.nexusai.checkin.app`, release `signingConfig` wiring + `.gitignore` +
> `keystore.properties.template`, R8 `isMinifyEnabled` + `isShrinkResources`, dead OpenCSV dependency
> removed, the camera prominent-disclosure screen ([4.5]), the DB cloud-backup exclusion ([4.7]), and
> the `specialUse` justification string ([Section 5]). **Still your action:** generate the upload
> keystore + real `keystore.properties` ([4.1]), on-device runtime testing, and the Play Console
> declarations.

### 4.1 Release signing — [BLOCKER]
- [ ] **Generate an upload keystore** (keep it forever):
      ```bash
      keytool -genkeypair -v -keystore checkin-upload.jks \
        -keyalg RSA -keysize 2048 -validity 9125 \
        -alias checkin-upload
      ```
- [ ] **Store the keystore & passwords OUT of the repo.** Confirm `*.jks`/`*.keystore` and any
      `keystore.properties` are git-ignored. **Never commit them.**
- [ ] **Wire a release `signingConfig`** in `app/build.gradle.kts`, reading secrets from a local
      `keystore.properties` (or env vars for CI) — not hardcoded.
- [ ] **[BLOCKER] Enroll in Play App Signing.** You upload with the *upload key*; Google holds the
      *app signing key*.

### 4.1a Set the release `applicationId` — [BLOCKER]
- [ ] In `app/build.gradle.kts` `defaultConfig`, change **`applicationId = "com.nexusai.checkin.app"`**.
      **Leave `namespace = "com.checkin.app"`** and all Kotlin package declarations untouched —
      `applicationId` (published identity) is independent of the code namespace, so this is a
      **one-line change**, no source refactor.
- [ ] Verify the `${applicationId}.fileprovider` authority in the manifest still resolves (it's
      derived from `applicationId`, so it becomes `com.nexusai.checkin.app.fileprovider`
      automatically — just re-test CSV export/share).

### 4.2 Build an Android App Bundle — [BLOCKER]
- [~] Produce a **release `.aab`**: `./gradlew :app:bundleRelease` (with the CLI toolchain flag
      from `CLAUDE.md`). New apps must ship as `.aab`. **Builds green (2026-07-13, 55s), 22 MB —
      but the artifact is debug-signed (no keystore yet), so it validated R8 only and is NOT
      uploadable. Rebuild upload-signed once the keystore exists ([4.1]).**
- [ ] Verify the signed release build installs on a clean Android 14+ device.

### 4.3 Versioning
- [x] **Centralized in `gradle.properties`** (`VERSION_CODE` / `VERSION_NAME`), read by
      `app/build.gradle.kts` with `-P` override support. Verified end-to-end via `aapt2` badging.
- [ ] Set `VERSION_CODE` to the release-day date (**`YYYYMMDD` scheme** — currently `20260713`);
      `VERSION_NAME = "1.0"` (SemVer). Confirm at the actual first upload.
- [ ] Every subsequent upload needs a strictly higher `VERSION_CODE` (the date advances it; one
      uploadable build per day, else `+1` or a `YYYYMMDDNN` suffix).

### 4.4 Code shrinking & obfuscation — [QUALITY]
- [x] **Enable R8**: `isMinifyEnabled = true` and `isShrinkResources = true` on the release build.
- [~] **Add/verify ProGuard keep rules** for Room, ML Kit face detection, CameraX, OpenCSV, and any
      reflection-based libs. **`bundleRelease` R8 pass is clean — no missing-class warnings; only
      Room has an explicit keep, ML Kit/CameraX rely on their bundled consumer rules (sufficient at
      build time). OpenCSV already removed. Runtime path still unverified — see below.**
- [ ] Re-run the full presence-gate + check-in/out + CSV export flows on the **shrunk release** build.
      **← the real R8 proof; build-clean does not guarantee runtime-clean for the CameraX/ML Kit gate.**

### 4.5 Camera prominent-disclosure screen — [POLICY]
- [ ] Add an **in-app disclosure** immediately **before** the first camera permission request,
      stating the camera is used for on-device presence verification and images aren't stored/shared;
      require an **affirmative tap**. Exact rules in [Section 6].

### 4.6 Runtime permission handling — [QUALITY]
- [ ] **`POST_NOTIFICATIONS`** (runtime on 13+, always given `minSdk 34`): request gracefully;
      ensure check-in still functions if denied.
- [ ] **`CAMERA`**: handle grant / deny / "don't ask again"; verify the biometric fallback still
      lets the user proceed if camera is never granted.
- [ ] Confirm **no full-screen-intent** reliance (manifest declares no `USE_FULL_SCREEN_INTENT`;
      since 22 Jan 2025 it's auto-granted only to alarm/calling apps). Reminder should work as a
      normal high-priority notification.

### 4.7 Backup & data-extraction rules — [QUALITY/POLICY]
- [ ] Review `allowBackup="true"` + `@xml/backup_rules` / `@xml/data_extraction_rules`. Decide
      whether the attendance DB is backup-eligible; keep the privacy policy + Data safety form
      consistent with that choice.

### 4.8 Final manifest hygiene — [QUALITY]
- [ ] Confirm only the **five** declared permissions are needed (they are). Don't add `INTERNET`
      unless a real feature needs it (it would weaken "no data collected" and expand Data safety).
- [ ] Confirm `android:exported` correctness (MainActivity `true` w/ launcher; service & provider `false`).
- [ ] Remove debug logging / test-only code from release paths.

### 4.9 App icon & branding — [QUALITY]
- [ ] Confirm adaptive launcher icon renders correctly on Android 14+.
- [ ] Confirm the branded splash (`Theme.CheckInApp.Starting`) looks right in light/dark and, if
      you show a wordmark, reflects **"CheckIn - Solopreneur Tracker"** consistently.

---

## 5. Foreground service `specialUse` declaration — [BLOCKER/POLICY]

> **Highest-risk review item.** Google scrutinizes `specialUse` and prefers a standard FGS type
> when one fits. Prepare a solid justification.

- [ ] Manifest already declares `foregroundServiceType="specialUse"` + the
      `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property (`check_in`). Consider making the value a short
      human-readable justification/URL — reviewers read it.
- [ ] **[BLOCKER] Complete the "Foreground service permissions" declaration** on the Play Console
      **App content** page:
  - **Description:** drives the live check-in timer notification and the presence-re-verification
    reminder while the user is actively checked in.
  - **User impact / why foreground:** the user explicitly starts a check-in session; the ongoing
    timer + reminder are perceptible and user-initiated.
  - **Demo video** of the feature in use (unlisted link).
- [ ] **[POLICY] Pre-empt "why not a standard type?"** — no standard type (`dataSync`,
      `mediaPlayback`, `location`, `phoneCall`, …) matches an attendance/work-timer session. Have a
      fallback in case a reviewer pushes back.
- [ ] Verify the service starts correctly on Android 14+ with the declared type (FGS-type mismatch
      throws at runtime on 14+).

---

## 6. Permissions & prominent disclosure — [POLICY]

- [ ] **Camera prominent disclosure (in-app):** must **immediately precede** the runtime prompt, be
      **standalone** (not buried in policy/ToS), clearly name the camera use, require **affirmative
      action**, and **not** treat navigating-away or auto-dismiss as consent. No data accessed
      before consent.
- [ ] **Play Console permissions declaration:** justify `CAMERA`; optionally attach a short video of
      the in-app disclosure flow.
- [ ] **Privacy policy** covers camera + biometric (from [Section 3]) and stays consistent.
- [ ] **Biometric:** `USE_BIOMETRIC` needs no special declaration; OS-mediated.
- [ ] **Least privilege:** re-confirm no `READ_MEDIA_*`, storage, location, or contacts permissions
      are pulled in transitively (check the merged manifest of the release build).

---

## 7. Data safety form — [BLOCKER/POLICY]

> Every app must complete this. Google's definition: data is **"collected"** only if it leaves the
> device, **"shared"** if transferred to a third party.

- [ ] **Draft answer for CheckIn (verify against final behavior):**
  - **Collects or shares any user data?** → **No** — attendance times stored locally only; face
    frames processed on-device and deleted immediately; no network access.
  - **Third-party SDKs:** confirm **none** transmit data off-device. Verify you use the **bundled**
    (offline) ML Kit face-detection variant (`com.google.mlkit:face-detection` in `build.gradle.kts`
    is the bundled one) so nothing is fetched at runtime. CameraX / Room / OpenCSV / Biometric /
    Compose are all local.
  - **Deletion path:** explain local deletion (uninstall / clear data / CSV export as user copy).
- [ ] **[POLICY] Reconcile with the backup decision ([4.7])** and keep policy + form consistent.
- [ ] **Provide the nexusai.world privacy-policy URL** in the Data safety section and App content page.
- [ ] **Re-verify after every behavior change** — standing legal attestation. (Adding any network
      SDK, e.g. crash reporting, flips this answer — see [Section 14].)

---

## 8. Store listing assets — [BLOCKER for the listed items]

### Text
- [ ] **App title** → **"CheckIn - Solopreneur Tracker"** (29 chars).
- [ ] **Short description** — ≤ 80 chars (shows in search).
- [ ] **Full description** — ≤ 4000 chars: the discipline model, on-device privacy, no-account/offline
      nature. No keyword stuffing (policy).

### Graphics
- [ ] **App icon** — 512×512 PNG, 32-bit with alpha.
- [ ] **Feature graphic** — 1024×500, JPG or **24-bit PNG (no alpha)**. Required.
- [ ] **Phone screenshots** — min 2, **recommended ≥4**, max 8; PNG/JPG; 16:9 or 9:16; each side
      320–3840 px (portrait ≥ 1080×1920 recommended). First 3 carry the pitch: check-in timer,
      attendance calendar, reports/deficit.
- [ ] **[D5 — required] Tablet screenshots** — since you target **Tablet/foldable**, provide **7"
      and 10"** tablet screenshots. Showcase the **two-pane Attendance** layout (`WindowSizeClass`)
      — it's a genuine large-screen differentiator.
- [ ] **[NICE] Promo/short video** — optional YouTube link.

### Categorization & contact
- [ ] **Category** (Productivity) and tags.
- [ ] **Contact email** → **saksham@nexusai.world** (required); website → **nexusai.world**.
- [ ] **Privacy policy URL** → the nexusai.world page.

---

## 9. Content rating & app content declarations — [BLOCKER]

- [ ] **Complete the IARC content-rating questionnaire** (low rating expected — no violence/UGC/ads).
- [ ] **Target audience & content** — adult/general productivity; **not** directed at children.
- [ ] **Ads declaration** → **No ads**.
- [ ] **News / COVID / financial / government** → **No** to all.
- [ ] **Data safety** — from [Section 7].
- [ ] **Foreground service declaration** — from [Section 5].
- [ ] **Health/government** — attendance is not a regulated health category; declare accurately.
- [ ] **App access instructions — [POLICY, easy to miss for THIS app].** The whole app is gated
      behind the camera/biometric **presence check**; reviewers on a device/emulator may be unable
      to pass the face check. In **App access**, explain the gate and that the **device-biometric/PIN
      fallback** unlocks it after repeated face failures — give step-by-step reviewer instructions.
      No login credentials needed (no accounts).

---

## 10. Testing tracks — [OPTIONAL for Organization accounts]

> As an **Organization** ([D4]), you are **exempt** from the 12-tester / 14-day closed-testing
> mandate that applies to new Individual accounts. You can request production access directly.
> Testing tracks are now a **quality choice**, not a gate.

- [ ] **[Recommended] Internal testing track** (up to 100 testers, no wait) — fast device coverage
      before production. Upload the release `.aab` here first.
- [ ] **[Optional] Closed/Open testing** — only if you want a wider beta. Not required to publish.
- [ ] Use whichever track's **Pre-launch report** ([Section 11]) to catch crashes before going live.
- [ ] Bump `versionCode` on every track upload.

---

## 11. Pre-launch report, quality & vitals — [QUALITY]

- [ ] **Read the Pre-launch report** (Play auto-runs on real devices): fix crashes, ANRs, security,
      accessibility findings.
- [ ] **Android vitals targets:** crash rate **< 1.09%**, user-perceived ANR **< 0.47%**.
- [ ] **Test on Android 14 (`minSdk 34`)** across a couple of device sizes.
- [ ] **[D5] Large-screen check:** verify the width-cap (`ConstrainedContent`) and Attendance
      two-pane (`WindowSizeClass`) render on a tablet/foldable — you're listing for those devices.
- [ ] **Process-death resilience:** kill mid-check-in; confirm `ServiceReconciler` leaves no orphan
      notification / phantom pause.
- [ ] **Permission-denied paths:** camera denied, notifications denied, biometrics unavailable.
- [ ] **Empty states & edge cases:** fresh install, midnight rollover, day boundary.
- [ ] **Accessibility:** content descriptions on icon-only controls, ≥48dp targets, contrast,
      dynamic font scaling, TalkBack pass on core flows.
- [ ] **Localization sanity:** default-locale strings complete; no hardcoded user-facing strings.
- [ ] **Release-build QA (post-shrinking):** re-run every flow on the R8-minified `.aab`.

---

## 12. Production release & rollout — [BLOCKER to go live]

- [ ] **[12.1] Pricing = Free.** Confirmed. Note: a **Free** app can **never** be converted to a
      **paid (upfront-price)** app — that's the permanent bit. **In-app purchases are still allowed
      on a free app**, so your "IAP later" plan ([D5]) is preserved; you'll just need a
      **merchant/payments profile + Play Billing** when you add them (defer until then).
- [ ] **[D5] Countries/regions = All** selected.
- [ ] **[D5] Device targeting** includes phones and tablets/foldables.
- [ ] **Release notes** for v1.0.
- [ ] **Managed publishing** (optional) to control the exact go-live moment.
- [ ] **Staged rollout** — start small (10–20%), watch vitals/crashes, then ramp to 100%.
- [ ] **Submit for review** — first review can take **days to weeks**, longer for camera +
      `specialUse` surfaces. Don't commit to a hard external launch date until approved.

---

## 13. Post-launch & ongoing — [POLICY/QUALITY]

- [ ] **Monitor Android vitals & crash/ANR** weekly early on.
- [ ] **Respond to user reviews.**
- [ ] **Keep Data safety + privacy policy in sync** with any behavior change — standing obligation.
- [ ] **Target API upkeep:** plan the **API 36** bump before **31 Aug 2026** or updates get blocked.
- [ ] **Dependency & security updates** (ML Kit, CameraX, Room, etc.).
- [ ] **Back up the upload keystore** in ≥2 secure locations.
- [ ] **Watch Play policy emails** for new declarations/requirements.
- [ ] **When you add IAP:** finish the merchant/payments profile, integrate Play Billing, update the
      Data safety form (purchase data), and re-review policies.

---

## 14. Nice-to-haves

- [ ] **[NICE]** In-app "how presence verification works / your data never leaves the device"
      explainer — turns privacy into a selling point.
- [ ] **[NICE]** Reflect the "no data collected" badge in the store description.
- [ ] **[NICE]** Crash reporting **only if** you accept a network SDK — it flips the Data safety
      answer to "collects crash data" and requires policy updates. Given the offline ethos, consider
      staying network-free and relying on Android vitals.
- [ ] **[NICE]** Device-framed screenshots for a more polished listing.
- [ ] **[NICE]** `nexusai.world/checkin` landing page with policy, FAQ, support.
- [ ] **[NICE]** Deep-link / app-shortcut polish.

---

## Appendix A: critical path

Longest poles, in order — the 12-tester bottleneck is **gone** thanks to the Organization account.

1. ~~**Organization verification + phone**~~ ✅ **done 2026-07-13** — §1 complete; **Create app** is
   unlocked. Package name claims on first `.aab` upload — [Section 1].
2. **Privacy policy hosted on nexusai.world + Data safety** (must be consistent) — [Section 3]/[Section 7].
3. **`applicationId` change + release signing + `.aab`** — [4.1]/[4.1a]/[4.2].
4. **`specialUse` FGS justification + camera prominent disclosure** (rejection-prone) — [Section 5]/[Section 6].
5. **Store assets incl. phone + tablet screenshots + "App access" reviewer notes** — [Section 8]/[9].
6. **(Optional) internal testing track + Pre-launch report** — [Section 10]/[11].
7. **Submit → review → staged rollout** — [Section 12].

Steps 1–5 can run in parallel while verification completes. There is **no mandatory 14-day wait**;
production access can be requested as soon as verification + review pass.

## Appendix B: command reference

```bash
# Toolchain (per CLAUDE.md)
export JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Generate the upload keystore (keep it forever, off-repo)
keytool -genkeypair -v -keystore checkin-upload.jks \
  -keyalg RSA -keysize 2048 -validity 9125 -alias checkin-upload

# Build the release App Bundle (after setting applicationId + signingConfig)
./gradlew :app:bundleRelease -Dorg.gradle.java.installations.paths="$JBR"

# Sanity: unit tests + debug build still green
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -Dorg.gradle.java.installations.paths="$JBR"
```

---

*Target identity: **CheckIn - Solopreneur Tracker** · `com.nexusai.checkin.app` · Nexus AI Technology Labs
(AI NEXUS CONSULTING FZ-LLC) · saksham@nexusai.world. Verify every repo-derived value against the
current source before relying on it — the build config and manifest change over time.*
