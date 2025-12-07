package com.checkin.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Animates text changes with a typing effect (delete old text, type new text)
 *
 * @param targetText The new text to animate to
 * @param deleteDelay Delay in milliseconds between character deletions
 * @param typeDelay Delay in milliseconds between character typing
 * @return The current animated text to display
 */
@Composable
fun rememberTypingAnimatedText(
    targetText: String,
    deleteDelay: Long = 50L,
    typeDelay: Long = 80L
): String {
    var displayText by remember { mutableStateOf(targetText) }
    var previousTarget by remember { mutableStateOf(targetText) }

    LaunchedEffect(targetText) {
        if (targetText == previousTarget) return@LaunchedEffect

        val currentText = displayText

        // Phase 1: Delete current text character by character
        for (i in currentText.length downTo 0) {
            displayText = currentText.substring(0, i)
            delay(deleteDelay)
        }

        // Phase 2: Type new text character by character
        for (i in 1..targetText.length) {
            displayText = targetText.substring(0, i)
            delay(typeDelay)
        }

        previousTarget = targetText
    }

    return displayText
}
