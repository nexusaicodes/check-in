package com.checkin.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.BuildConfig
import com.checkin.app.R
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import com.checkin.app.ui.components.SectionDivider
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Screen-scoped, not item-scoped: the About card that posts these messages is a lazy item, and a
    // scope remembered inside it is cancelled the moment the card scrolls away.
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // First, and only when it applies: everything below it silently does nothing without this.
        item { NotificationsOffCard() }

        item {
            SectionCard(title = stringResource(R.string.settings_target_section)) {
                // Committed once on release, not on every drag tick — each commit appends a dated
                // entry to the target schedule.
                var targetHours by remember(uiState.dailyTargetHours) {
                    mutableFloatStateOf(uiState.dailyTargetHours.toFloat())
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_daily_target,
                        targetHours.toInt(),
                        targetHours.toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = targetHours,
                    onValueChange = { targetHours = it },
                    onValueChangeFinished = { viewModel.updateDailyTarget(targetHours.toInt()) },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                HelpText(stringResource(R.string.settings_target_help))
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_nudges_section)) {
                ToggleRow(
                    label = stringResource(R.string.settings_nudges_master),
                    checked = uiState.nudgesEnabled,
                    onCheckedChange = { viewModel.setNudgesEnabled(it) },
                    info = stringResource(R.string.settings_nudges_master_help),
                )
                // Individual nudges only matter once the master switch is on.
                if (uiState.nudgesEnabled) {
                    Nudge.entries.forEach { nudge ->
                        ToggleRow(
                            label = nudgeLabel(nudge),
                            checked = nudge in uiState.enabledNudges,
                            onCheckedChange = { viewModel.setNudgeEnabled(nudge, it) },
                            info = nudgeHelp(nudge),
                        )
                    }
                }

                // A sibling of the master switch, not a child of it. Turning off encouragement must
                // not also change how worked time is counted.
                SectionDivider()
                ToggleRow(
                    label = stringResource(R.string.settings_presence_check),
                    checked = uiState.presenceCheckEnabled,
                    onCheckedChange = { viewModel.setPresenceCheckEnabled(it) },
                    info = stringResource(R.string.settings_presence_check_help),
                )

                if (uiState.presenceCheckEnabled) {
                    ToggleRow(
                        label = stringResource(R.string.settings_presence_check_pauses),
                        checked = uiState.presenceCheckPauses,
                        onCheckedChange = { viewModel.setPresenceCheckPauses(it) },
                        // States both outcomes, not just the active one: the cost of each answer is
                        // the whole substance of the choice, and a dialog is read before deciding.
                        info = stringResource(R.string.settings_presence_pauses_help),
                    )
                }
            }
        }

        item { AboutCard(onOpenLicenses = onOpenLicenses, showMessage = showMessage) }

        // Ships in release: the layers it records fail silently, so without it a user has nothing to
        // report and the app has nothing to look at.
        item { DiagnosticsCard(viewModel) }

        // Debug-only: lets nudge copy and timing be iterated on without waiting for real triggers.
        if (BuildConfig.DEBUG) {
            item { NudgeHarnessCard(viewModel) }
        }
    }
}

@Composable
private fun NudgeHarnessCard(viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.settings_debug_section)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.debugRunPass() }) {
                Text(stringResource(R.string.settings_debug_run_pass))
            }
            OutlinedButton(onClick = { viewModel.debugClearLog() }) {
                Text(stringResource(R.string.settings_debug_clear))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // One button per variant, not per nudge: this install always buckets to the same wording, so
        // every other variant would otherwise be impossible to see on this device.
        Nudge.entries.forEach { nudge ->
            NudgeCatalog.variants(nudge).forEachIndexed { variant, _ ->
                OutlinedButton(
                    onClick = { viewModel.debugSend(nudge, variant) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_debug_send, nudge.name, variant))
                }
            }
        }
    }
}

/**
 * The event log, available in **release** builds.
 *
 * It used to sit inside the debug-only harness above, which meant that on the build people actually
 * run there was no record of anything the notification and service layers did. Every failure mode
 * down there is silent by nature — a refused post, a service killed in the night, an alarm that
 * outlived its session — so a user hitting one had nothing to report but the wrong number it left
 * behind, and diagnosing it meant reasoning backwards from that number alone.
 *
 * Collapsed by default: it is diagnostic output, not something to read daily.
 */
@Composable
private fun DiagnosticsCard(viewModel: SettingsViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_diagnostics_section)) {
        HelpText(stringResource(R.string.settings_diagnostics_help))
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(
                    if (expanded) R.string.settings_diagnostics_hide else R.string.settings_diagnostics_show,
                ),
            )
        }
        // The collection lives inside the branch, not above it. `recentEvents` is
        // `WhileSubscribed`, so leaving it collected here would keep a Room query live on every
        // visit to Settings to feed a list that is collapsed by default and rarely opened.
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            DiagnosticsEvents(viewModel)
        }
    }
}

@Composable
private fun DiagnosticsEvents(viewModel: SettingsViewModel) {
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()

    if (events.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_diagnostics_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    events.forEach { event ->
        Text(
            text = "${eventTimeFormat.format(Instant.ofEpochMilli(event.at))}  ${event.event}  ${event.key}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Warns when notifications are off, because nothing else in the app does.
 *
 * A denied POST_NOTIFICATIONS takes out the running timer, the presence check, the pause it applies
 * and every reminder — and leaves every screen looking exactly as it does when all of it is working.
 * The permission is only ever *asked* for inside the presence gate, and Android stops showing that
 * dialog after two refusals, so an install can sit in this state permanently with no way to find out.
 *
 * Read on resume rather than held in the ViewModel: the only route to fixing it is system settings,
 * which returns with no result, so the grant has to be re-read on the way back.
 */
@Composable
private fun NotificationsOffCard() {
    val context = LocalContext.current
    var block by remember { mutableStateOf(context.notificationBlock()) }

    LifecycleResumeEffect(Unit) {
        block = context.notificationBlock()
        onPauseOrDispose { }
    }
    if (block == NotificationBlock.NONE) return

    SectionCard(title = stringResource(block.titleRes)) {
        HelpText(stringResource(block.helpRes))
        OutlinedButton(onClick = { context.openNotificationSettings() }) {
            Text(stringResource(R.string.settings_notifications_off_action))
        }
    }
}

/**
 * How much of the app's notification surface the system is currently swallowing.
 *
 * Three switches silence a notification and only one of them is the runtime permission: the user can
 * grant it and still turn notifications off for the whole app, or set an individual channel to
 * "None". Checking the permission alone made this card blind to the two settings a user is most
 * likely to have reached for, and they are the ones behind "I had everything enabled".
 */
private enum class NotificationBlock(val titleRes: Int, val helpRes: Int) {
    NONE(0, 0),
    ALL(R.string.settings_notifications_off, R.string.settings_notifications_off_help),
    CHANNELS(R.string.settings_notifications_partial, R.string.settings_notifications_partial_help),
}

private fun Context.notificationBlock(): NotificationBlock {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val manager = NotificationManagerCompat.from(this)
    if (!granted || !manager.areNotificationsEnabled()) return NotificationBlock.ALL

    // Only the two channels a session depends on. A muted engagement channel is a preference, not a
    // fault — nudges are opt-in and losing them costs the user nothing they were relying on.
    // A channel that reads as absent is left alone here: the app creates all three at startup, so
    // null means something odd rather than something the user chose, and a card that cries wolf on
    // this screen is worse than one that stays quiet.
    val silenced = listOf(NotificationChannels.TIMER, NotificationChannels.REMINDER).any {
        manager.getNotificationChannelCompat(it)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
    }
    return if (silenced) NotificationBlock.CHANNELS else NotificationBlock.NONE
}

/**
 * Opens this app's notification settings, falling back to its app-details page.
 *
 * The direct screen is one tap closer to the switch that matters, but it is not guaranteed to be
 * handled on every device, and the app-details page always is. Both are wrapped: an unhandled intent
 * throws, and a warning card that crashes the app is worse than the state it is warning about.
 */
@Suppress("SwallowedException")
private fun Context.openNotificationSettings() {
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))

    for (intent in listOf(direct, fallback)) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one; there is nothing useful to tell the user if neither resolves.
        }
    }
}

/**
 * A switch and its label, with the explanation behind an (i) rather than printed underneath. Inline
 * help would make every row three lines tall and push the controls apart; on tap it is the same
 * words with the row's label as the dialog's title, so the question it answers is never ambiguous.
 */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, info: String? = null) {
    // Saveable: the dialog for the pause setting is the only place the cost to the user's recorded
    // hours is spelled out, and rotating to read it must not be what closes it.
    var showInfo by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            // Takes the leftover width so a long label wraps instead of squeezing the switch.
            modifier = Modifier.weight(1f),
        )
        if (info != null) {
            IconButton(onClick = { showInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.settings_info_about, label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }

    if (info != null && showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(label) },
            text = { Text(info, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.settings_info_dismiss))
                }
            },
        )
    }
}

/** Secondary copy under a control, explaining what it does to the user's data. */
@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The name of a nudge's toggle. What it does, and when, is in [nudgeHelp] behind the row's (i). */
@Composable
private fun nudgeLabel(nudge: Nudge): String = when (nudge) {
    Nudge.NOT_CHECKED_IN_BY -> stringResource(R.string.nudge_label_not_checked_in)
}

/**
 * What a nudge's (i) explains.
 *
 * Deliberately describes *when* only in general terms. The copy used to format the trigger hour in
 * from [com.checkin.app.notify.engagement.NudgeConfig], which read as a promise of a specific time —
 * but the pass that sends a nudge is an hourly, deferrable background job, so the message can arrive
 * well after the hour it named. Saying less is the honest option, and it also stops a change to the
 * rule from silently making a translated string wrong.
 */
@Composable
private fun nudgeHelp(nudge: Nudge): String = when (nudge) {
    Nudge.NOT_CHECKED_IN_BY -> stringResource(R.string.nudge_help_not_checked_in)
}

private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ZoneId.systemDefault())
