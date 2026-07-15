# Play Console — "Set up your app" answers (copy-paste)

Field-by-field answers for the **App content / Set up your app** declarations for
**CheckIn - Solopreneur Tracker** (`com.nexusai.checkin.app`). Every answer reflects the app's
actual behavior: 100% on-device, no `INTERNET` permission, no accounts, no analytics, no ads, no
third-party data transmission. Keep this consistent with the privacy policy at
`https://nexusai.world/checkin/privacy` and the Data safety form — mismatches are the #1 rejection
cause.

---

## 1. Set privacy policy

- **Privacy policy URL:**
  ```
  https://nexusai.world/checkin/privacy
  ```

---

## 2. Sign in details / App access

The app has **no accounts and no login credentials**, but the *entire* app is gated behind an
on-device presence check (face detection, with a device-unlock fallback). A reviewer on a device or
emulator can be blocked at that gate, so choose **"All or some functionality is restricted"** and
give the instructions below (this is the safe choice — it stops a reviewer getting stuck).

- **Access type:** All or some functionality is restricted
- **Instruction name:** `Presence check (face / device-unlock gate)`
- **Username / Password:** leave blank — *no account or login is required*
- **Any other instructions:**
  ```
  CheckIn has no user accounts and needs no username or password. The whole app is gated behind an
  on-device presence check at launch and at every check-in / check-out.

  To get past the gate:
  1. At launch a full-screen "presence verification" camera screen appears. Pointing the front
     camera at any face passes it. Detection runs entirely on-device (ML Kit); the frame is deleted
     immediately and never stored or uploaded.
  2. On an emulator or if the face check cannot be passed, the app automatically offers a
     device-unlock fallback (biometric or screen-lock PIN / pattern / password) after 3 consecutive
     failed face attempts. Trigger 3 failed captures and authenticate with the device's screen lock
     to proceed.
  3. IMPORTANT for reviewers: the test device/emulator must have a screen lock (PIN/pattern/biometric)
     enrolled, otherwise the fallback cannot be used.
  4. Once past the gate, every feature (check-in/out timer, attendance calendar, reports, CSV export)
     is fully accessible with no further credentials.
  ```

---

## 3. Ads

- **Does your app contain ads?** → **No**
- (No ad SDKs, no `INTERNET` permission — nothing can serve ads.)

---

## 4. Content rating (IARC questionnaire)

- **Email for the rating certificate:** `saksham@nexusai.world`
- **Category:** **Utility, Productivity, Communication, or Other**
- **Answer every content question "No"**, specifically:
  - Violence, blood/gore, scary content → No
  - Sexual content / nudity → No
  - Profanity / crude humor → No
  - Controlled substances (drugs / alcohol / tobacco) → No
  - Gambling (simulated or real-money) → No
  - Users can interact / communicate / exchange content → No
  - User-generated content shared with others → No
  - Shares the user's current physical location → No
  - Digital purchases / in-app purchases → No
- **Expected result:** lowest rating (Everyone / PEGI 3 / etc.).

---

## 5. Target audience and content

- **Target age groups:** select **18 and over only** — do **not** tick any under-18 group (keeps the
  app out of the Families / children's-policy scope).
- **Is your app designed for / directed at children?** → **No**
- **Could your app unintentionally appeal to children?** → **No** (it is a work-hours productivity
  tool for adults).
- **Store presence / ads to children:** not targeted at children.

---

## 6. Data safety

Google's definitions: data is **"collected"** only if it **leaves the device**, and **"shared"**
only if transferred to a third party. CheckIn transmits nothing (no `INTERNET` permission), processes
face frames on-device and deletes them immediately, and stores attendance locally. So:

- **Does your app collect or share any of the required user data types?** → **No**
  - Rationale (keep for reviewer questions): attendance times live in a local Room/SQLite DB; the
    front-camera frame is processed on-device by ML Kit and deleted the moment detection resolves;
    the biometric fallback is OS-mediated and the app never receives biometric data. Local storage
    and on-device, ephemeral processing are **not** "collection" under Google's definition, and there
    is no network access to transmit anything.
- Because the answer is **No**, the data-type matrix is skipped.
- **Independent security review / validation against a global standard?** → **No** (optional; not
  applicable to a solo on-device app).
- **Account-deletion URL:** N/A — the app has no accounts. Data is removed by uninstalling or
  clearing app data (state this if a free-text field appears).
- **Privacy policy URL (Data safety section):** `https://nexusai.world/checkin/privacy`
- **Resulting summary shown on the listing:** *"No data collected · No data shared."*

---

## 7. Government apps

- **Is your app a government app?** → **No** (private productivity app; not developed by or for a
  government entity).

---

## 8. Financial features

- **Does your app provide financial features?** → **No** (no banking, lending, investments, crypto,
  payments, or money transfer; the app is free with no in-app purchases yet).

---

## 9. Health

- **Does your app have health features?** → **No** (work-attendance / time tracking is not a health,
  medical, or fitness feature; the app does not use Health Connect or handle any health data).

---

## 10. Store listing (Grow → Store presence → Main store listing)

Locale is **en-GB** — copy is written in British English.

- **App name (30 max):** `CheckIn - Solopreneur Tracker` (29)
- **Short description (80 max):**
  ```
  Private check-in & attendance tracker. On-device, no account, no internet.
  ```
- **Full description (4000 max):**
  ```
  CheckIn is a private, on-device attendance and presence tracker for solopreneurs, freelancers, and remote workers who want real, office-style work discipline — without a boss, a login, or a server.

  Start a session with a single check-in and CheckIn keeps a live timer running in a notification until you check out. Every check-in and check-out is confirmed by an on-device presence check, so your recorded hours reflect time you were genuinely present.

  HOW IT WORKS
  • Check in to start a work session; check out to end it. Your net time is the sum of your completed intervals for the day.
  • Each check-in and check-out is verified by an on-device face check (offline). If it cannot be completed, the app falls back to your device biometric or screen lock.
  • While you're checked in, a periodic presence reminder asks you to re-verify. Time between the reminder and your response is paused, so idle time doesn't inflate your hours.
  • Set a daily target. Each day is graded against the target in effect that day: meet it and you're present; fall short and a half-day or full-day leave is recorded.
  • A rolling leave deficit accumulates from your start date, giving an honest running picture of your discipline over time.

  PRIVATE BY DESIGN
  • 100% on-device. There is no account and no server.
  • The app ships without the internet permission, so nothing about you can be uploaded, tracked, or sold.
  • The camera is used only for the presence check. Each frame is analysed on your device and deleted immediately — no image is ever stored, shown, or shared.
  • No ads. No analytics. No third-party data collection.
  • Your attendance history stays in a local database on your phone. Export it to CSV whenever you like — that copy is yours.

  FEATURES
  • One-tap check-in / check-out with a live session timer
  • On-device face presence verification with a biometric / screen-lock fallback
  • Automatic, pause-aware net-hours calculation
  • Per-day targets with present / half-day / full-day classification
  • Rolling leave-deficit tracking
  • Attendance calendar and monthly summaries
  • Reports with totals and averages
  • CSV export via the system share sheet
  • Modern interface with light and dark themes; optimised for phones, tablets, and foldables

  WHO IT'S FOR
  CheckIn suits solopreneurs, freelancers, consultants, students, and anyone working without external oversight who wants to build and measure a consistent daily work habit. It treats every day the same — the discipline is yours to keep.

  CheckIn is a self-discipline tool, not a substitute for any employer or legal time-keeping system.
  ```

### Graphics
- **App icon (512×512 PNG):** upload `app/src/main/ic_launcher-playstore.png` — 512×512, 402 KB, meets spec. ✅ ready.
- **Feature graphic (1024×500 PNG):** upload `play-store-assets/feature-graphic.png` — brand-indigo, real app icon + wordmark. ✅ generated (regenerate via `play-store-assets/generate_feature_graphic.py`).
- **Phone screenshots (2–8, ≥1080px on a side):** capture the check-in timer, attendance calendar, and reports/deficit tabs from the running app. ⏳ TODO — needs the app running.
- **7″ + 10″ tablet screenshots (required — the app lists for tablets):** capture the two-pane Attendance layout on a tablet emulator. ⏳ TODO.
- **Video:** optional; leave blank (add the demo/justification YouTube link later — it doubles for the §5 FGS review).

### Categorisation & contact (same listing area)
- **Category:** Productivity
- **Contact email:** `saksham@nexusai.world`  ·  **Website:** `https://nexusai.world`
- **Privacy policy:** `https://nexusai.world/checkin/privacy`

---

## 11. Foreground service permissions — `specialUse` declaration (App content page)

> The highest-risk review item. Google scrutinises `specialUse` and prefers a standard FGS type
> when one fits. The manifest declares `android:foregroundServiceType="specialUse"` with
> `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = *"Runs a user-initiated attendance check-in session: an
> ongoing timer notification plus a periodic on-device presence re-verification reminder, active only
> while the user is checked in."* (already human-readable — reviewers read it).

**FGS type used:** Special use (`specialUse`).

**A. What the app uses the foreground service for (description):**
```
CheckIn runs a foreground service only while the user is in an active, user-initiated attendance
check-in session. The user taps "Check In", which starts the session and the service. While it runs
the service:
 • shows an ongoing notification with a live session timer (elapsed working time), updated every
   second, plus a "Check Out" action; and
 • fires one high-priority "presence re-verification" reminder partway through the session, asking
   the user to re-confirm they are present so idle/away time is not counted toward their hours.
The service stops the moment the user checks out. It performs no networking (the app has no INTERNET
permission) and transfers no data off the device.
```

**B. Why a foreground service is required (why it can't be backgrounded or deferred):**
```
The session is explicitly started by the user and is inherently ongoing and time-sensitive:
 • The elapsed-time timer must accrue continuously and stay visible to the user for the whole
   session, which can last hours. A background service would be killed and the timer would stop or
   drift; WorkManager/JobScheduler target deferrable, batched work and cannot maintain a live
   per-second timer or guarantee timely execution.
 • The presence re-verification reminder must reach the user promptly during the session. Deferring
   it would let unattended time be recorded as present, defeating the app's core purpose.
The persistent notification keeps the running session continuously perceivable, matching the
foreground-service contract.
```

**C. Why no standard FGS type applies (pre-empt "why not a standard type?"):**
```
No standard foreground-service type describes a self-tracked work-attendance timer:
 • Not dataSync / mediaPlayback / mediaProjection — no data transfer, media, or screen capture.
 • Not location — no location access or permission.
 • Not camera / microphone — the camera is used only momentarily for the on-device presence check,
   never by the ongoing service; the service uses neither camera nor microphone.
 • Not phoneCall / connectedDevice / health / remoteMessaging / shortService / systemExempted —
   none match a user-initiated timekeeping session.
The functionality is a user-initiated, user-visible attendance timer with no fitting standard
category — exactly the intended scope of specialUse.
```

**D. How a reviewer can see the FGS in use (instructions):**
```
1. Launch the app and pass the presence gate (see the App access instructions — face check, or the
   device-unlock fallback after 3 failed attempts).
2. On the Check-In tab, tap "Check In" and pass the presence check. A persistent notification with a
   running timer and a "Check Out" action appears — that is the foreground service.
3. Later in the session a presence re-verification reminder notification appears; tapping it
   re-verifies presence. Tapping "Check Out" and passing the presence check ends the session and
   stops the service.
```

**E. Demo video: intentionally skipped.** The video field is not a hard submission blocker — the
written description (A) + reviewer instructions (D) + the human-readable manifest subtype are enough
to submit. Decision (2026-07-15): submit without a video and only record one **if a reviewer asks**.
(If needed later: a 20–30s unlisted YouTube clip of check-in → timer notification → check-out; ads
off, not age-restricted.)

---

## Still separate from this section (do elsewhere)

- **Advertising ID** — if a separate "Advertising ID" declaration appears, answer **No, the app does
  not use an advertising ID** (no ads SDK, no `INTERNET`).
