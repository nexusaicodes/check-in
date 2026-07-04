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
- **Self-contained** — Room-only storage, no backend. Export your log to CSV via the share sheet.

## Tabs

| Tab | What it shows |
| --- | --- |
| **Check In** | Live timer, the check-in/out button, today's running status and deficit |
| **Attendance** | Monthly calendar of present / half-day / absent days |
| **Reports** | Overall stats, streaks, cumulative deficit, and CSV export |

## Requirements

- Android Studio (ships with the JetBrains JDK 21 the Gradle daemon needs)
- A device or emulator on **Android 14+** (min SDK 34; compile/target SDK 35)
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

## Tech

Kotlin · Jetpack Compose (Material 3, a fixed indigo brand theme in light + dark, branded splash,
`WindowSizeClass`-adaptive) · Room (via KSP, reactive `Flow` queries) · a `specialUse` foreground
service for the live timer and presence reminder · CameraX + ML Kit face detection · BiometricPrompt
fallback. MVVM with a single reactive `UiState` per screen and lightweight manual DI (`AppContainer`).

See [`CLAUDE.md`](CLAUDE.md) for architecture details, conventions, and non-obvious behaviors.
