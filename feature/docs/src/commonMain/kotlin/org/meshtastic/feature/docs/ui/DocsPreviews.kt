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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.meshtastic.core.resources.img_chirpy
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocSection
import org.meshtastic.feature.docs.model.SourceRef
import org.meshtastic.core.resources.Res as CoreRes

private val sampleUserGuidePages =
    listOf(
        DocPage(
            "onboarding",
            "Getting Started",
            DocSection.UserGuide,
            1,
            "user/onboarding.md",
            listOf("setup", "intro"),
            charCount = 3200,
            iconId = "onboarding",
        ),
        DocPage(
            "connections",
            "Connections",
            DocSection.UserGuide,
            2,
            "user/connections.md",
            listOf("bluetooth", "usb"),
            charCount = 4100,
            iconId = "connections",
        ),
        DocPage(
            "messages-and-channels",
            "Messages & Channels",
            DocSection.UserGuide,
            3,
            "user/messages-and-channels.md",
            listOf("chat"),
            charCount = 5200,
            iconId = "messages",
        ),
        DocPage(
            "nodes",
            "Nodes",
            DocSection.UserGuide,
            4,
            "user/nodes.md",
            listOf("node list"),
            charCount = 3800,
            iconId = "nodes",
        ),
        DocPage(
            "node-metrics",
            "Node Metrics",
            DocSection.UserGuide,
            5,
            "user/node-metrics.md",
            listOf("telemetry"),
            charCount = 6200,
            iconId = "node-metrics",
        ),
        DocPage(
            "map-and-waypoints",
            "Map & Waypoints",
            DocSection.UserGuide,
            6,
            "user/map-and-waypoints.md",
            listOf("map"),
            charCount = 4500,
            iconId = "map",
        ),
        DocPage(
            "settings-radio-user",
            "Radio & User Settings",
            DocSection.UserGuide,
            7,
            "user/settings-radio-user.md",
            listOf("config"),
            charCount = 5800,
            iconId = "settings-radio",
        ),
        DocPage(
            "firmware",
            "Firmware Updates",
            DocSection.UserGuide,
            9,
            "user/firmware.md",
            listOf("ota"),
            charCount = 3400,
            iconId = "firmware",
        ),
        DocPage(
            "signal-meter",
            "Signal Meter",
            DocSection.UserGuide,
            15,
            "user/signal-meter.md",
            listOf("rssi", "snr"),
            charCount = 3500,
            iconId = "signal-meter",
        ),
        DocPage(
            "units-and-locale",
            "Units & Locale",
            DocSection.UserGuide,
            16,
            "user/units-and-locale.md",
            listOf("metric", "imperial"),
            charCount = 3800,
            iconId = "units-locale",
        ),
    )

private val sampleDevGuidePages =
    listOf(
        DocPage(
            "architecture",
            "Architecture",
            DocSection.DeveloperGuide,
            1,
            "developer/architecture.md",
            listOf("kmp"),
            charCount = 4800,
            iconId = "architecture",
        ),
        DocPage(
            "codebase",
            "Codebase Layout",
            DocSection.DeveloperGuide,
            2,
            "developer/codebase.md",
            listOf("structure"),
            charCount = 3600,
            iconId = "codebase",
        ),
        DocPage(
            "contributing",
            "Contributing",
            DocSection.DeveloperGuide,
            8,
            "developer/contributing.md",
            listOf("pr"),
            charCount = 2900,
            iconId = "contributing",
        ),
    )

private val sampleAllPages = sampleUserGuidePages + sampleDevGuidePages

private val sampleMarkdown =
    """
    # Getting Started

    Welcome to Meshtastic! This guide walks you through the initial setup.

    ## First Launch

    When you open the app for the first time, you'll be guided through an introductory
    flow that helps configure essential permissions and settings.

    ### Permissions

    The app requires several permissions to operate correctly:

    - **Location** — Required for Bluetooth scanning
    - **Bluetooth** — Primary connection method
    - **Notifications** — Incoming message alerts

    > 💡 **Tip:** You can change notification preferences later in Android system settings.
    """
        .trimIndent()

private val sampleChirpyMessages =
    listOf(
        ChirpyMessage("1", ChirpyRole.USER, "How do I connect to my radio?"),
        ChirpyMessage(
            "2",
            ChirpyRole.ASSISTANT,
            "To connect to your Meshtastic radio via Bluetooth:\n\n" +
                "1. Power on your radio\n2. Open the app → Connections\n" +
                "3. Tap Scan for Devices\n4. Select your device from the list\n\n" +
                "Make sure Bluetooth and Location permissions are granted.",
            sources = listOf(SourceRef("connections", "Connections"), SourceRef("onboarding", "Getting Started")),
        ),
        ChirpyMessage("3", ChirpyRole.USER, "What if my device doesn't appear in the scan?"),
    )

// region DocsBrowserScreen Previews

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsBrowserScreenPreview() {
    AppTheme {
        DocsBrowserScreen(
            pages = sampleAllPages,
            isLoading = false,
            searchQuery = "",
            onSearchQueryChange = {},
            onSelectPage = {},
            onBack = {},
        )
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsBrowserScreenEmptyPreview() {
    AppTheme {
        DocsBrowserScreen(
            pages = emptyList(),
            isLoading = false,
            searchQuery = "xyzzy",
            onSearchQueryChange = {},
            onSelectPage = {},
            onBack = {},
        )
    }
}

// endregion

// region DocsPageRouteScreen Previews

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsPageContentPreview() {
    AppTheme {
        DocsPageRouteScreen(
            pageId = "onboarding",
            content =
            org.meshtastic.feature.docs.model.DocPageContent(
                page = sampleUserGuidePages.first(),
                markdown = sampleMarkdown,
            ),
            isLoading = false,
            onBack = {},
        )
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsPageNotFoundPreview() {
    AppTheme { DocsPageRouteScreen(pageId = "deleted-page", content = null, isLoading = false, onBack = {}) }
}

// endregion

// region ChirpyAssistant Previews

/**
 * Previews the Chirpy assistant chat content without ModalBottomSheet wrapper, since ModalBottomSheet requires a sheet
 * host that is unavailable in previews.
 */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun ChirpyAssistantContentPreview() {
    AppTheme {
        Surface {
            ChirpyAssistantContent(
                state =
                AIDocAssistantSessionState(messages = sampleChirpyMessages, isLoading = false, draftQuestion = ""),
                onDraftChange = {},
                onSubmit = {},
                onNavigateToPage = {},
            )
        }
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun ChirpyAssistantLoadingPreview() {
    AppTheme {
        Surface {
            ChirpyAssistantContent(
                state =
                AIDocAssistantSessionState(
                    messages = sampleChirpyMessages,
                    isLoading = true,
                    draftQuestion = "What channels should I use?",
                ),
                onDraftChange = {},
                onSubmit = {},
                onNavigateToPage = {},
            )
        }
    }
}

/** Standalone chat content layout extracted for preview compatibility. */
@Composable
fun ChirpyAssistantContent(
    state: AIDocAssistantSessionState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Chirpy Assistant",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.messages, key = { it.id }) { message ->
                ChirpyBubble(message = message, onNavigateToPage = onNavigateToPage)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.isLoading) {
                item { PreviewThinkingBubble() }
            }
        }

        OutlinedTextField(
            value = state.draftQuestion,
            onValueChange = onDraftChange,
            placeholder = { Text("Ask about Meshtastic...") },
            trailingIcon = {
                TextButton(onClick = onSubmit, enabled = state.draftQuestion.isNotBlank() && !state.isLoading) {
                    Text("Send")
                }
            },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private val PreviewBubbleCorner = 8.dp

@Suppress("LongMethod")
@Composable
private fun ChirpyBubble(message: ChirpyMessage, onNavigateToPage: (String) -> Unit, modifier: Modifier = Modifier) {
    val isUser = message.role == ChirpyRole.USER

    if (isUser) {
        val bubbleColor = MaterialTheme.colorScheme.primaryContainer
        val borderColor = MaterialTheme.colorScheme.primary
        Column(modifier = modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(PreviewBubbleCorner, PreviewBubbleCorner, 0.dp, PreviewBubbleCorner),
                color = bubbleColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(0.5.dp, borderColor),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    } else {
        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
        val borderColor = MaterialTheme.colorScheme.outline
        Column(modifier = modifier.fillMaxWidth().padding(end = 48.dp), horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.Top) {
                Image(
                    painter = painterResource(CoreRes.drawable.img_chirpy),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(0.dp, PreviewBubbleCorner, PreviewBubbleCorner, PreviewBubbleCorner),
                    color = bubbleColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(0.5.dp, borderColor),
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            if (message.sources.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp, start = 30.dp),
                ) {
                    message.sources.forEach { source ->
                        Surface(
                            shape = RoundedCornerShape(PreviewBubbleCorner),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            onClick = { onNavigateToPage(source.id) },
                        ) {
                            Text(
                                text = source.title,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Preview-safe thinking bubble without infinite animations. */
@Composable
private fun PreviewThinkingBubble(modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.fillMaxWidth().padding(end = 48.dp), horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(CoreRes.drawable.img_chirpy),
                contentDescription = null,
                modifier = Modifier.size(24.dp).padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(0.dp, PreviewBubbleCorner, PreviewBubbleCorner, PreviewBubbleCorner),
                color = bubbleColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(0.5.dp, borderColor),
            ) {
                Text(
                    text = "Chirpy is thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// endregion

// region DocsSearchBar Previews

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsSearchBarEmptyPreview() {
    AppTheme { Surface { DocsSearchBar(query = "", onQueryChange = {}, modifier = Modifier.padding(16.dp)) } }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun DocsSearchBarWithQueryPreview() {
    AppTheme {
        Surface { DocsSearchBar(query = "bluetooth settings", onQueryChange = {}, modifier = Modifier.padding(16.dp)) }
    }
}

// endregion
