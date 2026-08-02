package com.checkin.app.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.BuildConfig
import com.checkin.app.R
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
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
 * The event log, available in **release** builds — deliberately outside the debug-only harness above.
 *
 * Every failure mode in the notification and service layers is silent by nature: a refused post, a
 * service killed in the night, an alarm that outlived its session. Without this card a user hitting
 * one has nothing to report but the wrong number it left behind.
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
 * A switch and its label, with the explanation behind an (i) rather than printed underneath. Inline
 * help would make every row three lines tall and push the controls apart; on tap it is the same
 * words with the row's label as the dialog's title, so the question it answers is never ambiguous.
 */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, info: String? = null) {
    // Saveable: the dialog is where a row's whole explanation lives, and rotating to finish reading
    // it must not be what closes it.
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
internal fun HelpText(text: String) {
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
 * Deliberately describes *when* only in general terms. The pass that sends a nudge is an hourly,
 * deferrable background job, so naming a trigger hour here would promise a time the delivery cannot
 * keep — and it would let a change to the eligibility rule silently make a translated string wrong.
 */
@Composable
private fun nudgeHelp(nudge: Nudge): String = when (nudge) {
    Nudge.NOT_CHECKED_IN_BY -> stringResource(R.string.nudge_help_not_checked_in)
}

private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ZoneId.systemDefault())
