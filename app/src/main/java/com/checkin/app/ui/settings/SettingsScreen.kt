package com.checkin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
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
            bottom = innerPadding.calculateBottomPadding() + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = stringResource(R.string.settings_target_section)) {
                // Committed once on release, not on every drag tick — each commit appends a dated
                // entry to the target schedule.
                var targetHours by remember(uiState.dailyTargetHours) {
                    mutableFloatStateOf(uiState.dailyTargetHours.toFloat())
                }
                Text(
                    text = stringResource(R.string.settings_daily_target, targetHours.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = targetHours,
                    onValueChange = { targetHours = it },
                    onValueChangeFinished = { viewModel.updateDailyTarget(targetHours.toInt()) },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Text(
                    text = stringResource(R.string.settings_target_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_nudges_section)) {
                ToggleRow(
                    label = stringResource(R.string.settings_nudges_master),
                    checked = uiState.nudgesEnabled,
                    onCheckedChange = { viewModel.setNudgesEnabled(it) }
                )
                Text(
                    text = stringResource(
                        R.string.settings_quiet_hours,
                        uiState.quietStartHour,
                        uiState.quietEndHour
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Individual nudges only matter once the master switch is on.
                if (uiState.nudgesEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Nudge.entries.forEach { nudge ->
                        ToggleRow(
                            label = stringResource(nudgeLabel(nudge)),
                            checked = nudge in uiState.enabledNudges,
                            onCheckedChange = { viewModel.setNudgeEnabled(nudge, it) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_tracking_section)) {
                Text(
                    text = uiState.trackingStartDate?.let {
                        stringResource(R.string.settings_tracking_start, it.toString())
                    } ?: stringResource(R.string.settings_tracking_not_started),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        Nudge.entries.forEach { nudge ->
            OutlinedButton(
                onClick = { viewModel.debugSend(nudge) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_debug_send, nudge.name))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_debug_no_events),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.forEach { event ->
                Text(
                    text = "${eventTimeFormat.format(Instant.ofEpochMilli(event.at))}  " +
                        "${event.event}  ${event.nudge}  v${event.variant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun nudgeLabel(nudge: Nudge): Int = when (nudge) {
    Nudge.NOT_CHECKED_IN_BY -> R.string.nudge_label_not_checked_in
}

private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ZoneId.systemDefault())
