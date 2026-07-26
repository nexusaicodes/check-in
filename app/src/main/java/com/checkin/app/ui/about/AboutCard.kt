package com.checkin.app.ui.about

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.checkin.app.BuildConfig
import com.checkin.app.R
import com.checkin.app.ui.components.SectionCard

/**
 * App identity, the privacy stance, and the four meta links.
 *
 * A card rather than its own screen: it is six rows, and a dedicated destination for that would add
 * a tap without adding anything to read. Only the license list — which is longer than the whole of
 * Settings — earns a route of its own.
 *
 * [showMessage] is supplied by the host rather than launched from a scope in here: this card is a
 * `LazyColumn` item, so a scope remembered locally dies the moment the card scrolls out of view —
 * taking the fallback snackbar with it, exactly when the user needs to read it.
 */
@Composable
fun AboutCard(onOpenLicenses: () -> Unit, showMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Built once: none of these inputs can change while the process is alive.
    val draft = remember {
        Feedback.draft(
            app = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            device = DeviceBuild(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
            ),
        )
    }

    val noBrowser = stringResource(R.string.about_no_browser)
    val noEmailApp = stringResource(R.string.about_no_email_app, Feedback.ADDRESS)
    val noHandler = stringResource(R.string.about_no_handler)

    SectionCard(title = stringResource(R.string.about_section), modifier = modifier) {
        Text(
            text = stringResource(
                R.string.about_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.about_developer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_privacy_stance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        MetaRow(
            label = stringResource(R.string.about_privacy_policy),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            if (!ExternalLinks.openUrl(context, ExternalLinks.PRIVACY_POLICY_URL)) {
                val copied = ExternalLinks.copyToClipboard(
                    context,
                    label = "Privacy policy",
                    text = ExternalLinks.PRIVACY_POLICY_URL,
                )
                showMessage(if (copied) noBrowser else noHandler)
            }
        }
        MetaRow(
            label = stringResource(R.string.about_feedback),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            if (!ExternalLinks.sendFeedback(context, draft)) {
                val copied =
                    ExternalLinks.copyToClipboard(context, label = "Email", text = Feedback.ADDRESS)
                showMessage(if (copied) noEmailApp else noHandler)
            }
        }
        MetaRow(
            label = stringResource(R.string.about_rate),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            // Both the Play app and the web listing are missing on some emulators; the listing URL
            // is already the fallback inside openPlayListing, so a failure here means neither.
            if (!ExternalLinks.openPlayListing(context)) {
                val copied = ExternalLinks.copyToClipboard(
                    context,
                    label = "Play listing",
                    text = ExternalLinks.playListingUrl(context),
                )
                showMessage(if (copied) noBrowser else noHandler)
            }
        }
        MetaRow(
            label = stringResource(R.string.about_licenses),
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            onClick = onOpenLicenses,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_feedback_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaRow(label: String, icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 48dp of height is the minimum tap target; the vertical padding gets it there without
            // pinning the row, so it still grows with a large font scale.
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
