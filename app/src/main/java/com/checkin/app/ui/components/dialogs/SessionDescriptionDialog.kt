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
import androidx.compose.ui.res.stringResource
import com.checkin.app.R

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
        title = { Text(stringResource(R.string.dialog_title_describe_session)) },
        text = {
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = {
                    descriptionInput = it
                    descriptionError = false
                },
                label = { Text(stringResource(R.string.dialog_label_what_working_on)) },
                isError = descriptionError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (descriptionError) {
                        Text(stringResource(R.string.dialog_error_description_required))
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
                Text(stringResource(R.string.dialog_button_start))
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
                Text(stringResource(R.string.dialog_button_cancel))
            }
        }
    )

}
