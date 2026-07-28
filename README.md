# CheckIn

A personal Android attendance-discipline app. It tracks your net daily working hours through
authenticated **check-in / check-out** intervals, classifies each day against a per-day target, and
shows the record building up over time — modeled after an office fingerprint attendance system.

## What it does

- **Check in / check out** from the first tab. Every check-in *and* check-out is gated by an
  on-device face check (ML Kit, offline), with **device biometric** as a fallback after repeated
  face-detection failures. Captured frames are transient — verified, then deleted immediately.
- **Net daily time** = the sum of your completed check-in/out intervals for the day (open intervals
  are excluded). Every day counts — 7 days a week, no weekend or holiday exemption.
- **Each day is classified** against that day's target ("present mark"): `≥ target` = present,
  `≥ target/2` = half day, below that = full-day absence. Changing the target applies from that day
  forward — past days keep the classification they earned. There is no leave quota, and no screen
  shows a running deficit; the calendar and the reports are the record.
- **Sessions are immutable** — no editing, deleting, or manual entry, by design.
- **A mid-session presence check** — on by default. Partway through the day's target the app asks you
  once to verify you're still there, and by default your timer stays paused until you do. Both the
  check and its pause are switchable in Settings, and either switch reaches the session already
  running.
- **Encouragement nudges** — off by default, both the master switch and each nudge. When enabled,
  the app can nudge you to check in, bounded by a daily cap and a per-nudge cooldown (Android's own
  per-channel settings cover quiet hours). Tapping a nudge still runs the same face check.
- **Self-contained** — Room-only storage, no backend. Export your log to CSV via the share sheet.

## Tabs

| Tab | What it shows |
| --- | --- |
| **Check In** | Live timer and the check-in/out button, with today's sessions a tap away |
| **Attendance** | Monthly calendar of present / half-day / absent days, plus the month's split and averages |
| **Reports** | Daily-hours and monthly charts, the all-time split, streaks, and CSV export |
| **Settings** | Daily target, presence-check and notification preferences, and About (privacy policy, feedback, open-source licenses) |

## Requirements

- Android Studio (ships with the JetBrains JDK 21 the Gradle daemon needs)
- A device or emulator on **Android 14+** (min SDK 34; compile/target SDK 36)
- Grants for **Camera** (face verification) and **Notifications** (the live timer), both asked for at the first check-in rather than at launch

## Build & run

The Gradle wrapper is pinned to **Gradle 8.13**. Android Studio finds the required JDK automatically —
just open the project and Run. For **CLI builds**, point Gradle's toolchain detection at the JetBrains
JDK bundled with Android Studio:

```bash
export JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest -Dorg.gradle.java.installations.paths="$JBR"  # JVM unit tests
./gradlew :app:assembleDebug     -Dorg.gradle.java.installations.paths="$JBR"  # build debug APK
./gradlew :app:installDebug      -Dorg.gradle.java.installations.paths="$JBR"  # install on a device
```

Run a single test class:

```bash
./gradlew :app:testDebugUnitTest --tests "com.checkin.app.DeficitCalculatorTest"
```

## Static analysis

**ktlint** (formatting) and **detekt** (code smells) both gate CI. Style comes from `.editorconfig`;
detekt's rules are `config/detekt/detekt.yml` layered over its shipped defaults. There is no baseline
file — the tree is clean, and a new finding is meant to be fixed or suppressed at the site with a
reason.

```bash
./gradlew staticAnalysis   # what CI runs: ktlintCheck + detekt
./gradlew ktlintFormat     # auto-fix formatting
```

Run the same gate before each commit (once per clone):

```bash
git config core.hooksPath githooks
```

The hook is a no-op unless the commit stages Kotlin, and it refuses to commit a signing key or a
populated `keystore.properties`.

## Tech

Kotlin · Jetpack Compose (Material 3, a fixed indigo brand theme in light + dark, branded splash,
`WindowSizeClass`-adaptive) · Room (via KSP, reactive `Flow` queries) · a `specialUse` foreground
service for the live timer and presence reminder · CameraX + ML Kit face detection · BiometricPrompt
fallback. MVVM with a single reactive `UiState` per screen and lightweight manual DI (`AppContainer`).

See [`CLAUDE.md`](CLAUDE.md) for architecture details, conventions, and non-obvious behaviors.
