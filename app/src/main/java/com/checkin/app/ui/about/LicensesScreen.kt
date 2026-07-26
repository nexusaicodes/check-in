package com.checkin.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The license list, as its own destination because it is longer than the whole of Settings.
 *
 * The Apache-2.0 text is bundled rather than linked. Every license link here hands the URL to the
 * browser via [ExternalLinks], which needs a browser and a connection — so on an offline device a
 * link alone would leave unreadable the one license the app is obliged to reproduce in full.
 */
@Composable
fun LicensesScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)
    val noHandler = stringResource(R.string.about_no_handler)

    // Saveable: a rotation part-way through the licence would otherwise collapse it and lose the
    // reader's place. The text itself is deliberately not saved — 11 KB in the state bundle is far
    // more than it costs to read the file again.
    var showFullText by rememberSaveable { mutableStateOf(false) }

    // Read on expand, not at screen entry: 11 KB is cheap but pointless for the common visit that
    // never opens it. Off the main thread, because the read lands on the same frame as the toggle;
    // `produceState` holds the result across collapse/expand, so it happens at most once per visit.
    val paragraphs by produceState<List<String>?>(initialValue = null, showFullText) {
        if (showFullText && value == null) {
            value = withContext(Dispatchers.IO) {
                context.resources
                    .openRawResource(R.raw.apache_2_0)
                    .bufferedReader()
                    .use { it.readText() }
                    .split(PARAGRAPH_BREAK)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(OPEN_SOURCE_LIBRARIES) { library ->
            SectionCard(title = library.name) {
                Text(
                    text = library.coordinates,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = library.copyright,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                library.note?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                // Kept wide: the two CameraX licences sit side by side, and adjacent tap targets
                // this small are easy to confuse for one another.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    library.licenses.forEach { license ->
                        Text(
                            text = license.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                // A bare clickable Text is only as tall as its glyphs. The role makes
                                // TalkBack announce a link rather than read it as prose, and the
                                // padding brings the target to the 48dp minimum.
                                .clickable(role = Role.Button) {
                                    if (!ExternalLinks.openUrl(context, license.url)) {
                                        val copied = ExternalLinks.copyToClipboard(
                                            context,
                                            label = license.displayName,
                                            text = license.url,
                                        )
                                        val message = if (copied) noBrowser else noHandler
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                }
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showFullText = !showFullText },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (showFullText) {
                            R.string.licenses_hide_full_text
                        } else {
                            R.string.licenses_show_full_text
                        },
                    ),
                )
            }
        }

        if (showFullText) {
            item {
                Text(
                    text = stringResource(R.string.licenses_full_text_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // One item per paragraph rather than one Text holding all 11 KB: a single Text measures
            // the whole licence in one pass on the frame it appears, which is long enough to see.
            items(paragraphs.orEmpty()) { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Paragraphs in the licence text are separated by a blank line. */
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
