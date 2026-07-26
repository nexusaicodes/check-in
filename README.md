# CheckIn

A personal Android attendance-discipline app. It tracks your net daily working hours through
authenticated **check-in / check-out** intervals, accumulates a rolling leave deficit against a
per-day target, and shows your compliance status — modeled after an office fingerprint attendance
system.

## What it does

- **Check in / check out** from the first tab. Every check-in *and* check-out is gated by an
  on-device face check (ML Kit, offline), with **device biometric** as a fallback after repeated
  face-detection failures. Captured frames are transient — verified, then deleted immediately.
- **Net daily time** = the sum of your completed check-in/out intervals for the day (open intervals
  are excluded). Every day counts — 7 days a week, no weekend or holiday exemption.
- **Leave** is deducted per day relative to that day's target ("present mark"): `≥ target` = present,
  `≥ target/2` = half day, below that = full-day absence. The **deficit accumulates forever** from
  your tracking start date — there is no leave quota.
- **Sessions are immutable** — no editing, deleting, or manual entry, by design.
- **Optional reminders** — off by default. When enabled in Settings, the app can nudge you to check
  in, bounded by a daily cap and a per-nudge cooldown (Android's own per-channel settings cover
  quiet hours). Tapping a nudge still runs the same face check.
- **Self-contained** — Room-only storage, no backend. Export your log to CSV via the share sheet.

## Tabs

| Tab | What it shows |
| --- | --- |
| **Check In** | Live timer and the check-in/out button, with today's sessions a tap away |
| **Attendance** | Monthly calendar of present / half-day / absent days, plus the month's split and averages |
| **Reports** | Daily-hours and monthly charts, the all-time split, streaks, and CSV export |
| **Settings** | Daily target, reminder preferences, and your tracking start date |

## Requirements

- Android Studio (ships with the JetBrains JDK 21 the Gradle daemon needs)
- A device or emulator on **Android 14+** (min SDK 34; compile/target SDK 36)
- Grants for **Camera** (face verification) and **Notifications** (the live timer) on first launch

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
