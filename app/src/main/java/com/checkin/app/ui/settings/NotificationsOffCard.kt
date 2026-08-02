package com.checkin.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.R
import com.checkin.app.ui.components.SectionCard

/**
 * Warns when notifications are off, because nothing else in the app does.
 *
 * A denied POST_NOTIFICATIONS takes out the running timer, the session reminder and every nudge —
 * and leaves every screen looking exactly as it does when all of it is working. The permission is
 * asked for in two places (first open, then the presence gate at the first check-in) and Android
 * stops showing the dialog after two refusals, so an install can sit in this state permanently with
 * no way to find out.
 *
 * Read on resume rather than held in the ViewModel: the only route to fixing it is system settings,
 * which returns with no result, so the grant has to be re-read on the way back.
 */
@Composable
internal fun NotificationsOffCard() {
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
