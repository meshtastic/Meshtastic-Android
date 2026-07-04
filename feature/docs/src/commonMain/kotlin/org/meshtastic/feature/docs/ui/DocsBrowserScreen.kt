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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocSection
import org.meshtastic.feature.docs.model.ModelReadiness

/** Main documentation browser screen showing a grouped TOC. */
@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsBrowserScreen(
    pages: List<DocPage>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectPage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isAiSupported: Boolean = false,
    modelReadiness: ModelReadiness = ModelReadiness.Checking,
    showFab: Boolean = false,
    showChirpy: Boolean = false,
    chirpyState: AIDocAssistantSessionState = AIDocAssistantSessionState(),
    onChirpyToggle: () -> Unit = {},
    onChirpyDismiss: () -> Unit = {},
    onChirpyDraftChange: (String) -> Unit = {},
    onChirpySubmit: () -> Unit = {},
    onChirpyNavigateToPage: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Documentation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MeshtasticIcons.ArrowBack, contentDescription = "Navigate back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (isAiSupported && showFab) {
                ChirpyFab(modelReadiness = modelReadiness, onClick = onChirpyToggle)
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            DocsSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading documentation...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                pages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No results found" else "No documentation available",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                else -> {
                    DocsTocList(pages = pages, onSelectPage = onSelectPage)
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

@Composable
private fun DocsTocList(pages: List<DocPage>, onSelectPage: (String) -> Unit, modifier: Modifier = Modifier) {
    val userGuidePages =
        remember(pages) { pages.filter { it.section == DocSection.UserGuide }.sortedBy { it.navOrder } }
    val devGuidePages =
        remember(pages) { pages.filter { it.section == DocSection.DeveloperGuide }.sortedBy { it.navOrder } }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (userGuidePages.isNotEmpty()) {
            item {
                Text(
                    text = "User Guide",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
                )
            }
            items(userGuidePages, key = { it.id }) { page -> DocPageListItem(page = page, onSelectPage = onSelectPage) }
        }

        if (devGuidePages.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Developer Guide",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
                )
            }
            items(devGuidePages, key = { it.id }) { page -> DocPageListItem(page = page, onSelectPage = onSelectPage) }
        }
    }
}

@Composable
private fun DocPageListItem(page: DocPage, onSelectPage: (String) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = page.resolveIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(page.title) },
        modifier = modifier.clickable { onSelectPage(page.id) }.semantics { contentDescription = "Open ${page.title}" },
    )
}
