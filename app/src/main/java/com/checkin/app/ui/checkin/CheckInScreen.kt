package com.checkin.app.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel = viewModel()
) {
    val elapsedTime by viewModel.elapsedTime.observeAsState(0L)
    val isRunning by viewModel.isRunning.observeAsState(false)
    val showDescriptionDialog by viewModel.showDescriptionDialog.observeAsState(false)
    val sessionDescription by viewModel.sessionDescription.observeAsState(null)

    var descriptionInput by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Spacer to push content to 30% from top
        Spacer(modifier = Modifier.fillMaxHeight(0.3f))

        // Dynamic heading
        Text(
            text = if (isRunning) {
                sessionDescription ?: "Session Active"
            } else {
                "Ready to Focus"
            },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Timer display card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewModel.formatTime(elapsedTime),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Start/Stop button
        Button(
            onClick = {
                if (isRunning) {
                    viewModel.stopStopwatch()
                } else {
                    viewModel.showDescriptionDialog()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            colors = if (isRunning) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = if (isRunning) "Stop" else "Start",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }

    // Session description dialog
    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDescriptionDialog() },
            title = { Text("Describe Your Session") },
            text = {
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = {
                        descriptionInput = it
                        descriptionError = false
                    },
                    label = { Text("What are you working on?") },
                    isError = descriptionError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        if (descriptionError) {
                            Text("Description is required")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (descriptionInput.isBlank()) {
                            descriptionError = true
                            return@TextButton
                        }
                        viewModel.startStopwatch(descriptionInput)
                        viewModel.hideDescriptionDialog()
                        descriptionInput = ""
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.hideDescriptionDialog()
                        descriptionInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
