package com.checkin.app.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.ui.components.dialogs.SessionDescriptionDialog
import com.checkin.app.ui.components.rememberTypingAnimatedText

@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel = viewModel()
) {
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val showDescriptionDialog by viewModel.showDescriptionDialog.collectAsState()
    val sessionDescription by viewModel.sessionDescription.collectAsState()

    // Animate the description text with typing effect
    val headingText = if (isRunning) {
        sessionDescription ?: stringResource(R.string.session_active)
    } else {
        stringResource(R.string.ready_to_focus)
    }
    val animatedHeading = rememberTypingAnimatedText(
        targetText = headingText,
        deleteDelay = 30L,
        typeDelay = 50L
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Spacer to push content to 30% from top
        Spacer(modifier = Modifier.fillMaxHeight(0.3f))

        // Fixed height container for the title to prevent pushing clock down
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp), // Fixed height for up to 3 lines of displayMedium
            contentAlignment = Alignment.BottomCenter
        ) {
            // Dynamic heading with typing animation
            Text(
                text = animatedHeading,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Visible,
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                text = if (isRunning) stringResource(R.string.button_stop) else stringResource(R.string.button_start),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }

    // Session description dialog
    SessionDescriptionDialog(
        showDialog = showDescriptionDialog,
        onDismiss = { viewModel.hideDescriptionDialog() },
        onStart = { description ->
            viewModel.startStopwatch(description)
            viewModel.hideDescriptionDialog()
        }
    )
}
