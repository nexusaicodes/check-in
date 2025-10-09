# Check-In Stopwatch App

A single-screen Android application with two tabs (Check-In and History) built with Kotlin, Jetpack Compose, and modern Android architecture. The app tracks timed sessions with a persistent stopwatch and displays historical data with charts.

## Features

- **Check-In Tab**:
  - Large stopwatch display (HH:MM:SS format)
  - Start/Stop button to control sessions
  - Persistent foreground service that survives app restarts
  - Ongoing notification showing elapsed time

- **History Tab**:
  - Bar chart showing daily aggregated session data
  - List of all completed sessions with date/time and duration
  - Export to CSV functionality

## Technical Stack

- **Language**: Kotlin
- **Minimum SDK**: 34 (Android 14)
- **Target SDK**: 35
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModel + LiveData
- **Database**: Room (SQLite wrapper)
- **Charts**: Vico library
- **Navigation**: Bottom Navigation Bar

## Project Structure

```
app/src/main/java/com/checkin/app/
├── data/
│   ├── local/
│   │   ├── CheckInSession.kt (Entity)
│   │   ├── CheckInSessionDao.kt
│   │   └── AppDatabase.kt
│   └── repository/
│       └── CheckInRepository.kt
├── service/
│   └── StopwatchService.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── checkin/
│   │   ├── CheckInScreen.kt
│   │   └── CheckInViewModel.kt
│   ├── history/
│   │   ├── HistoryScreen.kt
│   │   ├── HistoryViewModel.kt
│   │   └── ChartComponents.kt
│   └── navigation/
│       └── BottomNavigation.kt
└── MainActivity.kt
```

## Building the Project

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35
- Gradle 8.2

### Steps to Build

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to this project directory
4. Wait for Gradle sync to complete
5. Click "Run" or press Shift+F10

### Using Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build and run
./gradlew installDebug && adb shell am start -n com.checkin.app/.MainActivity
```

## Key Features Implementation

### Persistent Stopwatch
- The stopwatch runs as a foreground service
- Survives app process death and device restarts
- Session state stored in SharedPreferences
- Real-time updates via BroadcastReceiver

### Database Schema
- Entity: `CheckInSession`
  - `id`: Primary key (auto-generated)
  - `startTimestamp`: Session start time (milliseconds)
  - `endTimestamp`: Session end time (nullable)
  - `durationMillis`: Total duration (nullable)

### Permissions
- `FOREGROUND_SERVICE`: Required for stopwatch service
- `FOREGROUND_SERVICE_SPECIAL_USE`: Foreground service type
- `POST_NOTIFICATIONS`: For showing ongoing notification (Android 13+)
- `WRITE_EXTERNAL_STORAGE`: For CSV export (Android 10 and below)

## Material 3 Theming

The app uses Material You dynamic colors that automatically adapt to the user's device wallpaper. It also follows the system dark mode preference.

## CSV Export

The History screen includes an export feature that saves all session data to a CSV file in the Downloads folder. The CSV includes:
- Session ID
- Start timestamp
- End timestamp
- Duration in milliseconds

## Testing

### Manual Testing Checklist

1. **Stopwatch Functionality**
   - [ ] Start a session and verify timer counts up
   - [ ] Notification appears with correct time
   - [ ] Stop session and verify it appears in History
   - [ ] Force close app and verify session continues
   - [ ] Restart device and verify session persists

2. **History Screen**
   - [ ] Sessions appear in chronological order
   - [ ] Chart displays correctly
   - [ ] Export to CSV works
   - [ ] Empty state shows when no sessions exist

3. **Permissions**
   - [ ] Notification permission requested on first launch (Android 13+)
   - [ ] Storage permission requested for CSV export (Android 10-)

## Known Limitations

- Requires Android 14 (SDK 34) or higher
- CSV export location is fixed to Downloads folder
- Chart shows last 7-14 days only

## Future Enhancements

- Customizable session labels/tags
- Statistics and insights
- Data backup and restore
- Multiple concurrent sessions
- Widgets for quick check-in

## License

This project is for demonstration purposes.
