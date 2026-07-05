package com.checkin.app.di

import android.content.Context
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.ui.camera.SelfieStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimal manual DI: the single place that builds the repository, the side-effect seams
 * ([AttendanceSettings], [ServiceController], [CsvExporter]), and the app-wide coroutine scope.
 * ViewModels receive these via their factories, so they stay pure and unit-testable with fakes.
 */
interface AppContainer {
    val repository: CheckInRepository
    val settings: AttendanceSettings
    val serviceController: ServiceController
    val csvExporter: CsvExporter
    val timeSource: TimeSource
    val applicationScope: CoroutineScope
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(AttendancePrefs.NAME, Context.MODE_PRIVATE)

    override val timeSource: TimeSource = SystemTimeSource

    // Outlives any ViewModel/composition: used for fire-and-forget work that must not be cancelled
    // by a screen leaving composition (e.g. deleting a transient selfie after the gate is dismissed).
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Clear any selfie orphaned by process death between capture and its post-detection delete.
        applicationScope.launch(Dispatchers.IO) { SelfieStorage.sweep(appContext) }
    }

    override val settings: AttendanceSettings = SharedPrefsAttendanceSettings(prefs, timeSource)

    override val repository: CheckInRepository by lazy {
        CheckInRepository(
            AppDatabase.getDatabase(appContext).checkInSessionDao(),
            timeSource,
            targetSchedule = { settings.readSchedule() }
        )
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)
}
