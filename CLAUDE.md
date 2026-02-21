# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Attendance Discipline System — a native Android punch-in/punch-out tracker that enforces daily work discipline. Tracks net daily hours via selfie-verified punch intervals, calculates leave deficits, and presents compliance status. Modeled after an office fingerprint attendance system.

**Rules:**
- Net time: sum of all punch-in/out intervals per day (breaks excluded)
- 2-hour daily target (configurable), every day (7 days/week)
- Leave deduction: <1hr = full day leave, 1-2hr = half day, ≥2hr = present
- Rolling deficit accumulation (no fixed leave quota — deficit only grows)
- Selfie with ML Kit face detection required on every punch-in AND punch-out
- Self-contained (Room only, no backend), with CSV export

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Build and install on connected device
./gradlew test                   # Run unit tests
./gradlew app:testDebugUnitTest  # Run unit tests (explicit variant)
```

Requires JDK 17 and Android SDK 35. Min SDK is 34 (Android 14).

## Architecture

**MVVM with Jetpack Compose + Room + Foreground Service + CameraX + ML Kit**

```
UI (3-Tab Compose Screens) → ViewModel (StateFlow) → Repository → Room DAO → SQLite
                                    ↕
                           StopwatchService (foreground service, SharedPreferences for state)
                                    ↕
                           CameraX + ML Kit Face Detection (selfie gate)
```

- **ViewModels** extend `AndroidViewModel`, use `StateFlow` for reactive state, coroutines via `viewModelScope`
- **Room** has a single entity `CheckInSession` with columns: `id`, `started_at`, `stopped_at`, `duration`, `date_key`, `punch_in_selfie`, `punch_out_selfie`
- **StopwatchService** is a foreground service that broadcasts elapsed time and persists state in SharedPreferences
- **Selfie Gate**: Every punch-in/out requires a front-camera selfie verified by ML Kit face detection (bundled, fully offline)

## Key Source Paths

- `app/src/main/java/com/checkin/app/` — all application code
  - `data/local/` — Room entity (`CheckInSession`), DAO, database singleton, `DailySummary` data classes
  - `data/repository/` — `CheckInRepository` (punch-in/out, deficit calculation, daily summaries)
  - `service/` — `StopwatchService` (foreground service)
  - `ui/punch/` — Punch screen (primary UX) and `PunchViewModel`
  - `ui/attendance/` — Attendance calendar screen, `AttendanceViewModel`, and components (CalendarGrid, MonthSummaryCard)
  - `ui/reports/` — Reports screen (stats, CSV export, settings) and `ReportsViewModel`
  - `ui/camera/` — `SelfieCaptureScreen` (CameraX) and `FaceDetectionHelper` (ML Kit)
  - `ui/navigation/` — 3-tab bottom navigation (Punch, Attendance, Reports)
  - `ui/theme/` — Material 3 theme with Roboto typography and dynamic colors

## Conventions

- Kotlin official code style (`kotlin.code.style=official`)
- Composables use PascalCase; private backing StateFlows prefixed with `_`
- All database operations are `suspend` functions
- Material 3 with dynamic colors (Material You) and system dark mode
- Room annotation processing uses KSP (not kapt)
- Destructive migration enabled (`fallbackToDestructiveMigration`)
- Selfie images stored in `context.filesDir/selfies/`
- Attendance preferences stored in SharedPreferences `"attendance_prefs"`
