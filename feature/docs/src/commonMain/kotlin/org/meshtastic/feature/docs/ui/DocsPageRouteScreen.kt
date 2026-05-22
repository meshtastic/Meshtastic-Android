/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.docs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownDimens
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.ModelReadiness
import org.meshtastic.feature.docs.model.TranslationSource

/** Routes a page ID to the appropriate page renderer surface. */
@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsPageRouteScreen(
    pageId: String,
    content: DocPageContent?,
    isLoading: Boolean,
    translationSource: TranslationSource = TranslationSource.BUNDLED,
    isNonEnglish: Boolean = false,
    isAiSupported: Boolean = false,
    modelReadiness: ModelReadiness = ModelReadiness.Checking,
    showChirpy: Boolean = false,
    chirpyState: AIDocAssistantSessionState = AIDocAssistantSessionState(),
    onChirpyToggle: () -> Unit = {},
    onChirpyDismiss: () -> Unit = {},
    onChirpyDraftChange: (String) -> Unit = {},
    onChirpySubmit: () -> Unit = {},
    onChirpyNavigateToPage: (String) -> Unit = {},
    onBack: () -> Unit,
    onNavigateToPage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = content?.page?.title ?: "Documentation",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isNonEnglish && !isLoading && content != null) {
                            Text(
                                text =
                                when (translationSource) {
                                    TranslationSource.ML_KIT -> "Auto-translated"
                                    TranslationSource.BUNDLED -> "Community translated"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MeshtasticIcons.ArrowBack, contentDescription = "Navigate back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (isAiSupported) {
                ChirpyFab(modelReadiness = modelReadiness, onClick = onChirpyToggle)
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                content == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "Page not found: $pageId", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "This page may have been moved or removed.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                else -> {
                    val markdownText = content.markdown ?: "No content available."
                    val platformUriHandler = LocalUriHandler.current
                    val docsUriHandler =
                        remember(platformUriHandler) {
                            DocsLinkUriHandler(onNavigateToPage = onNavigateToPage, fallback = platformUriHandler)
                        }
                    CompositionLocalProvider(LocalUriHandler provides docsUriHandler) {
                        Markdown(
                            content = markdownText,
                            imageTransformer = ComposeResourceImageTransformer(),
                            dimens = markdownDimens(tableCellWidth = 108.dp),
                            components =
                            markdownComponents(
                                table = {
                                    MarkdownTable(
                                        content = it.content,
                                        node = it.node,
                                        style = it.typography.text,
                                        headerBlock = { c, h, tw, s ->
                                            MarkdownTableHeader(
                                                content = c,
                                                header = h,
                                                tableWidth = tw,
                                                style = s,
                                                maxLines = Int.MAX_VALUE,
                                                overflow = TextOverflow.Clip,
                                            )
                                        },
                                        rowBlock = { c, r, tw, s ->
                                            MarkdownTableRow(
                                                content = c,
                                                header = r,
                                                tableWidth = tw,
                                                style = s,
                                                maxLines = Int.MAX_VALUE,
                                                overflow = TextOverflow.Clip,
                                            )
                                        },
                                    )
                                },
                            ),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        )
                    }
                }
            }
        }

        if (showChirpy) {
            ChirpyAssistantSheet(
                state = chirpyState,
                modelReadiness = modelReadiness,
                onDraftChange = onChirpyDraftChange,
                onSubmit = onChirpySubmit,
                onDismiss = onChirpyDismiss,
                onNavigateToPage = onChirpyNavigateToPage,
            )
        }
    }
}

/**
 * Custom [UriHandler] that intercepts relative doc links and navigates in-app.
 *
 * Relative links like `connections`, `../developer/architecture`, or anchor-only `#section` are resolved to a page ID
 * and dispatched via [onNavigateToPage]. External `http(s)://` URLs are forwarded to the [fallback] platform handler.
 */
internal class DocsLinkUriHandler(private val onNavigateToPage: (String) -> Unit, private val fallback: UriHandler) :
    UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            fallback.openUri(uri)
            return
        }
        // Anchor-only links (e.g. "#permissions") — ignore, stay on current page
        if (uri.startsWith("#")) return

        // Resolve relative path to a page ID:
        //   "connections"                    -> "connections"
        //   "../developer/architecture"      -> "architecture"
        //   "mqtt.html"                      -> "mqtt"
        val cleaned =
            uri.substringBefore('#') // strip anchor
                .substringAfterLast('/') // take filename segment
                .removeSuffix(".html") // strip .html if present
                .removeSuffix(".md") // strip .md if present

        if (cleaned.isNotBlank()) {
            onNavigateToPage(cleaned)
        }
    }
}
