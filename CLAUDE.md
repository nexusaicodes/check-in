# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Attendance Discipline System — a native Android punch-in/punch-out tracker that enforces daily work discipline. Tracks net daily hours via auth-gated punch intervals, calculates a rolling leave deficit, and presents compliance status. Modeled after an office fingerprint attendance system.

**Attendance rules:**
- Net time = sum of all completed punch-in/out intervals per day (open breaks between punches are excluded)
- Every day counts (7 days/week — no weekends/holidays exemption)
- Leave deduction is relative to the day's **target** (the "present mark"): `≥ target` = present (0.0), `≥ target/2` = half-day (0.5), `< target/2` = full-day leave (1.0)
- Deficit accumulates forever from the tracking start date — there is no leave quota, the deficit only grows
- **Sessions are immutable** — no edit/delete/manual entry by design
- Every punch-in AND punch-out is gated by a presence check: an on-device ML Kit face detection (offline), with **device biometric** as a fallback after repeated face failures
- Self-contained: Room-only persistence, no backend; CSV export via share sheet

## Build Commands

The Gradle wrapper is checked in and pinned to **Gradle 8.13** (the project's Kotlin 1.9.20 + AGP 8.13.2 are incompatible with Gradle 9.x, which removed `HasConvention`).

`gradle/gradle-daemon-jvm.properties` requires a **JetBrains vendor JDK 21** for the daemon — the one bundled with Android Studio (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`). Android Studio finds it automatically. For **CLI builds**, point Gradle's toolchain detection at it:

```bash
export JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest -Dorg.gradle.java.installations.paths="$JBR"  # JVM unit tests
./gradlew :app:assembleDebug     -Dorg.gradle.java.installations.paths="$JBR"  # Build debug APK
./gradlew :app:installDebug      -Dorg.gradle.java.installations.paths="$JBR"  # Install on a device
```

Run a single test class: `./gradlew :app:testDebugUnitTest --tests "com.checkin.app.DeficitCalculatorTest"`.

Toolchain: **JDK 17** source/target compatibility. Android **SDK 35** (`compile`/`target`), **min SDK 34** (Android 14). Unit tests live in `app/src/test/java/com/checkin/app/` and are pure JVM (no Robolectric).

## Architecture

**MVVM with Jetpack Compose + Room + Foreground Service + CameraX + ML Kit + BiometricPrompt.** No DI framework — each `AndroidViewModel` builds its own `CheckInRepository` from the `AppDatabase` singleton, injecting a `TimeSource` and a target-schedule supplier.

```
UI (3-Tab Compose Screens) → ViewModel (StateFlow) → Repository → Room DAO → SQLite
                                    ↕
                           StopwatchService (foreground service; timer + presence-check reminder)
                                    ↕
                           Presence gate: CameraX + ML Kit face detection → BiometricPrompt fallback
```

- **Pure/testable core** (no Android deps, unit-tested):
  - `data/local/AttendanceRules` — single source of truth for status classification and leave fractions
  - `data/local/TargetSchedule` — the immutable per-day target log (see gotchas)
  - `data/DeficitCalculator`, `data/AttendanceStats` — deficit and streak/summary math
  - `data/TimeSource` (`SystemTimeSource`) — injectable clock so date logic is deterministic in tests
  - `service/ReminderScheduler` — reminder timing; `ui/camera/AuthGate` — biometric-fallback decisions
  - `util/TimeFormat` — the one place durations/clock times are formatted
- **Room** — single entity `CheckInSession` (table `sessions`): `id`, `started_at`, `stopped_at` (null while active), `duration` (null until punch-out), `date_key` (`yyyy-MM-dd`, the punch-in day). The `punch_in_selfie`/`punch_out_selfie` columns are **vestigial** (always `""`) — selfies are transient now (see gotchas); the columns will be dropped during the deferred data-model work. DB name `"_app"`, version 2, `fallbackToDestructiveMigration()`.
- **StopwatchService** — `specialUse` foreground service driving the ongoing timer notification and the presence-check reminder. Persists timer + reminder state in `stopwatch_prefs` to survive process death (`START_STICKY`).
- **Presence gate** — `SelfieCaptureScreen` captures a front-camera frame; `FaceDetectionHelper` (ML Kit) must find a face. The image is **deleted as soon as the outcome is known** (detection + deletion run on a detached IO coroutine so a mid-capture dismiss can't strand the file); the camera bind is guarded so a dismiss before the provider resolves still releases it. `AuthGate.BIOMETRIC_FALLBACK_AFTER` (3) consecutive failures — of any kind, no-face **or** capture error — offer a device-unlock (`BiometricPrompt`) fallback. `MainActivity` is a `FragmentActivity` (required by `BiometricPrompt`). At the reminder-triggered root gate the gate and the nav host render **mutually exclusively** (gate XOR host), but `rememberNavController()` is **hoisted above the switch** so re-auth preserves the active tab and back stack; a `BackHandler` dismisses the gate, and the triggering intent extra is consumed once so recreation doesn't replay it.

## Key Source Paths

- `app/src/main/java/com/checkin/app/`
  - `data/` — `TimeSource`, `AttendancePrefs`, `AttendanceStats`, `DeficitCalculator`
  - `data/local/` — `CheckInSession`, `CheckInSessionDao`, `AppDatabase`, `DailySummary`/`DailyAggregate`/`AttendanceStatus`, `AttendanceRules`, `TargetSchedule`
  - `data/repository/` — `CheckInRepository` (punch-in/out, per-day-target summaries, deficit)
  - `service/` — `StopwatchService`, `ReminderScheduler`, `PresenceCheckSignal`
  - `ui/punch/`, `ui/attendance/`, `ui/reports/`, `ui/camera/` (+ `AuthGate`), `ui/navigation/`, `ui/theme/` (`statusColor` in `Color.kt`)
  - `util/TimeFormat`, `MainActivity`

## Gotchas & Non-Obvious Behaviors

- **The daily target is per-day and immutable.** Changing the target slider records `(today, hours)` in the `TargetSchedule` log (`attendance_prefs` key `target_schedule`); each day is classified against the target in effect **on that date**, so past days keep their original classification. Thresholds derive from that target (present `= target`, half `= target/2`). The log is seeded at the first punch-in. For installs that predate the log (they have `tracking_start_date` + the legacy scalar `daily_target_hours` but no `target_schedule`), `AttendancePrefs.readSchedule` synthesizes a single entry `(tracking_start, daily_target_hours)` so the old target isn't silently lost to the default — do all schedule reads through that helper.
- **Selfies are transient.** The captured JPEG is deleted immediately after face detection resolves (success or failure) — nothing accumulates in `filesDir/selfies/`, and no image is ever displayed.
- **Two SharedPreferences namespaces.** `attendance_prefs` (settings + `target_schedule` + `tracking_start_date`, keys **and readers** — `readSchedule`/`readTrackingStart` — centralized in `data/AttendancePrefs`, used by all three ViewModels and `StopwatchService`); `stopwatch_prefs` (live timer + reminder state in `StopwatchService`).
- **Tracking starts at the first authenticated punch-in**, not first launch — `PunchViewModel` seeds `tracking_start_date` there. There is no UI to change it (by design).
- **Deficit and stats exclude today** — computed from the tracking start up to yesterday. Today's in-progress day never counts. The Attendance month-summary tiles and the calendar cell for today follow the same rule: today is shown unclassified (its "today" marker only), not counted as leave until it becomes a completed past day.
- **Only completed sessions aggregate** — every summary/total query filters `stopped_at IS NOT NULL`; an active punch contributes nothing until punch-out.
- **Forgot-to-punch-out reminder.** While punched in, once elapsed passes 50% of the day's present mark, `StopwatchService` fires a high-priority reminder at a random point in the `[50%, 100%]` window (`ReminderScheduler`). Tapping it opens a re-auth gate at the UI root (`PresenceCheckSignal` → `MainActivity`); on success the service re-arms the next check (`ACTION_REARM_REMINDER`). No auto punch-out — the session stays open (immutable). **Known limitation:** the fire time is polled inside the service's 1-second `delay` loop, which has no wakelock/AlarmManager backing, so it can slip while the device is in Doze (screen off, CPU asleep) and fire late when the device next wakes. This is a deliberate trade-off to avoid the exact-alarm permission; moving to `AlarmManager.setExactAndAllowWhileIdle` (with `USE_EXACT_ALARM`) would make it Doze-proof.
- **Two independent 1-second tickers** remain by design: `StopwatchService` updates its notification; `PunchViewModel` drives the on-screen stopwatch. They serve different lifecycles; there is no cross-process broadcast between them.
- **Midnight attribution:** a session belongs wholly to its punch-in `date_key`, even if it ends the next day (immutable single row). Asserted in `CheckInRepositoryTest`.
- **CSV export** writes to `cacheDir/exports/` and shares via `FileProvider`; it fills gap days as `FULL_DAY_LEAVE`.

## Conventions

- Kotlin official code style; Composables PascalCase; private backing StateFlows prefixed `_`.
- All DAO/repository DB operations are `suspend` (or return `Flow`); Room via **KSP**.
- Status colors come from `ui/theme/statusColor(...)` (light/dark tuned) — never hardcode them in screens.
- Duration/clock formatting goes through `util/TimeFormat` — do not re-implement it.
- Material 3 with dynamic colors and system dark mode.
