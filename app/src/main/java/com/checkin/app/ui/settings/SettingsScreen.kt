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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.BuildConfig
import com.checkin.app.R
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.notify.engagement.NudgeConfig
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

        // Debug-only: lets nudge copy and timing be iterated on without waiting for real triggers.
        if (BuildConfig.DEBUG) {
            item { NudgeHarnessCard(viewModel) }
        }
    }
}

@Composable
private fun NudgeHarnessCard(viewModel: SettingsViewModel) {
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()

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

        Spacer(modifier = Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_debug_no_events),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            events.forEach { event ->
                Text(
                    text = "${eventTimeFormat.format(Instant.ofEpochMilli(event.at))}  " +
                        "${event.event}  ${event.key}  v${event.variant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A switch and its label, with the explanation behind an (i) rather than printed underneath. Inline
 * help made every row three lines tall and pushed the controls apart; on tap it is the same words
 * with the row's label as the dialog's title, so the question it answers is never ambiguous.
 */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, info: String? = null) {
    var showInfo by remember { mutableStateOf(false) }

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
 * What a nudge's (i) explains. Any hour the copy quotes is formatted in from [NudgeConfig], which is
 * the same value the rule fires on — spelling it out in the string would let the two disagree.
 */
@Composable
private fun nudgeHelp(nudge: Nudge): String = when (nudge) {
    Nudge.NOT_CHECKED_IN_BY ->
        stringResource(R.string.nudge_help_not_checked_in, NudgeConfig().notCheckedInByHour)
}

private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ZoneId.systemDefault())
