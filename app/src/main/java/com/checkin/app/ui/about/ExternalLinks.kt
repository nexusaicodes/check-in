package com.checkin.app.ui.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService

/**
 * Every outbound link in the app.
 *
 * The manifest declares no INTERNET permission and this is what makes that possible: the browser
 * fetches the policy, the mail app sends the feedback, the Play app handles the review. CheckIn only
 * ever hands an intent to the system. Adding a network call here would mean a new permission and a
 * new Data Safety declaration — don't.
 *
 * Each launcher returns `false` rather than throwing when nothing on the device can handle the
 * intent, so the caller can fall back to [copyToClipboard]. A device with no browser or no mail app
 * is unusual but entirely legal, and [ActivityNotFoundException] would otherwise crash the app.
 */
object ExternalLinks {

    const val PRIVACY_POLICY_URL = "https://nexusai.world/checkin/privacy"

    fun openUrl(context: Context, url: String): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /**
     * Prefers the Play app's own scheme so the rating sheet opens in place; falls back to the web
     * listing when Play is absent, which is the case on many emulators.
     */
    fun openPlayListing(context: Context): Boolean {
        val id = context.packageName
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))) ||
            openUrl(context, playListingUrl(context))
    }

    /** The web listing, for the clipboard fallback when neither Play nor a browser is present. */
    fun playListingUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    /**
     * Opens a pre-filled draft. The subject and body travel as extras rather than encoded into the
     * mailto URI: extras survive every mail client, whereas percent-encoded newlines in a `body=`
     * parameter do not.
     */
    fun sendFeedback(context: Context, draft: FeedbackDraft): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            // ACTION_SENDTO with a mailto: URI resolves to mail apps only — unlike ACTION_SEND,
            // which would offer every share target on the device.
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(Feedback.ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
        }
        return launch(context, intent)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
