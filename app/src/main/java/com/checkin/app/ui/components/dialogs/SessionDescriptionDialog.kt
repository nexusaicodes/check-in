package com.checkin.app.ui.components.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun SessionDescriptionDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit
) {
    if (!showDialog) {
        return
    }

    var descriptionInput by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
            descriptionInput = ""
            descriptionError = false
        },
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
                    onStart(descriptionInput)
                    descriptionInput = ""
                    descriptionError = false
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    descriptionInput = ""
                    descriptionError = false
                }
            ) {
                Text("Cancel")
            }
        }
    )

}
