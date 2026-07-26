package com.checkin.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import kotlinx.coroutines.launch

/**
 * The license list, as its own destination because it is longer than the whole of Settings.
 *
 * The Apache-2.0 text is bundled rather than linked: the app cannot reach the network, so a link
 * alone would leave the license unreadable on a device that is offline.
 */
@Composable
fun LicensesScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)

    var showFullText by remember { mutableStateOf(false) }
    // Read on expand, not at screen entry: 11 KB is cheap but pointless for the common visit that
    // never opens it. Keyed on the toggle and held here rather than inside the lazy item, which
    // would re-read the file every time the section scrolled back into view.
    val licenseText = remember(showFullText) {
        if (!showFullText) null else {
            context.resources.openRawResource(R.raw.apache_2_0).bufferedReader().use { it.readText() }
        }
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
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(OPEN_SOURCE_LIBRARIES) { library ->
            SectionCard(title = library.name) {
                Text(
                    text = library.coordinates,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = library.copyright,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                library.note?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    library.licenses.forEach { license ->
                        Text(
                            text = license.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                if (!ExternalLinks.openUrl(context, license.url)) {
                                    ExternalLinks.copyToClipboard(
                                        context,
                                        label = license.displayName,
                                        text = license.url
                                    )
                                    scope.launch { snackbarHostState.showSnackbar(noBrowser) }
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showFullText = !showFullText },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (showFullText) R.string.licenses_hide_full_text
                        else R.string.licenses_show_full_text
                    )
                )
            }
        }

        licenseText?.let { text ->
            item {
                SectionCard(title = stringResource(R.string.licenses_full_text_heading)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
