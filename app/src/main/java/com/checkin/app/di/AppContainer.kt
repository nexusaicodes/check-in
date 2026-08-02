package com.checkin.app.di

import android.content.Context
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.TrackingPrefs
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.AndroidNotifier
import com.checkin.app.notify.AndroidStringResolver
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.engagement.DefaultEngagementReporter
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.notify.engagement.EngagementSettings
import com.checkin.app.notify.engagement.NudgeDispatcher
import com.checkin.app.notify.engagement.SharedPrefsEngagementSettings
import com.checkin.app.notify.log.EngagementDatabase
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.RoomEngagementLog
import com.checkin.app.service.AndroidSessionAlarms
import com.checkin.app.service.SessionReminderRunner
import com.checkin.app.service.SessionWatchdog
import com.checkin.app.ui.camera.SelfieStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimal manual DI: the single place that builds the repository, the side-effect seams
 * ([TrackingSettings], [ServiceController], [CsvExporter]), and the app-wide coroutine scope.
 * ViewModels receive these via their factories, so they stay pure and unit-testable with fakes.
 */
interface AppContainer {
    val repository: CheckInRepository
    val settings: TrackingSettings
    val serviceController: ServiceController
    val csvExporter: CsvExporter
    val timeSource: TimeSource
    val applicationScope: CoroutineScope

    // Notification plumbing, shared by the foreground service and the engagement layer so that all
    // three notifications are described and built one way.
    val notifier: Notifier
    val notificationFactory: NotificationFactory

    // Engagement layer. Isolated from everything above: its own prefs namespace, its own database,
    // and no writes to the sessions table.
    val engagementSettings: EngagementSettings
    val engagementLog: EngagementLog
    val nudgeDispatcher: NudgeDispatcher
    val engagementReporter: EngagementReporter

    // Session mechanics that deliberately do not live inside CheckInService, because both have to
    // work in a process where no service is running: an alarm can be delivered into a process the
    // broadcast just created, and the watchdog exists precisely for when the service is gone.
    val sessionReminderRunner: SessionReminderRunner
    val sessionWatchdog: SessionWatchdog
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    init {
        // Before anything reads prefs: an existing install's tracking start still lives in the
        // namespace this replaced, and every date window the app computes is anchored on it.
        TrackingPrefs.migrateFromLegacy(appContext)
    }

    private val prefs = appContext.getSharedPreferences(TrackingPrefs.NAME, Context.MODE_PRIVATE)

    override val timeSource: TimeSource = SystemTimeSource

    // Outlives any ViewModel/composition: used for fire-and-forget work that must not be cancelled
    // by a screen leaving composition (e.g. deleting a transient selfie after the gate is dismissed).
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Clear any selfie orphaned by process death between capture and its post-detection delete.
        applicationScope.launch(Dispatchers.IO) { SelfieStorage.sweep(appContext) }
    }

    override val settings: TrackingSettings = SharedPrefsTrackingSettings(prefs, timeSource)

    override val repository: CheckInRepository by lazy {
        CheckInRepository(AppDatabase.getDatabase(appContext).checkInSessionDao(), timeSource)
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)

    override val engagementSettings: EngagementSettings = SharedPrefsEngagementSettings.create(appContext)

    override val notificationFactory = NotificationFactory(appContext)

    override val notifier: Notifier = AndroidNotifier(appContext, notificationFactory)

    override val engagementLog: EngagementLog by lazy {
        RoomEngagementLog(EngagementDatabase.getDatabase(appContext).engagementEventDao())
    }

    override val nudgeDispatcher: NudgeDispatcher by lazy {
        NudgeDispatcher(
            strings = AndroidStringResolver(appContext),
            repository = repository,
            prefs = engagementSettings,
            notifier = notifier,
            log = engagementLog,
            timeSource = timeSource,
        )
    }

    override val engagementReporter: EngagementReporter by lazy {
        DefaultEngagementReporter(notifier, engagementLog)
    }

    override val sessionReminderRunner: SessionReminderRunner by lazy {
        SessionReminderRunner(
            repository = repository,
            notifier = notifier,
            strings = AndroidStringResolver(appContext),
            alarms = AndroidSessionAlarms(appContext),
            log = engagementLog,
            timeSource = timeSource,
        )
    }

    override val sessionWatchdog: SessionWatchdog by lazy {
        SessionWatchdog(repository, serviceController, sessionReminderRunner, engagementLog, timeSource)
    }
}
