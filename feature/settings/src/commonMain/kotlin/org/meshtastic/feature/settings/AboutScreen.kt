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
package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.acknowledgements
import org.meshtastic.core.resources.library_count
import org.meshtastic.core.resources.open_source_description
import org.meshtastic.core.resources.open_source_libraries
import org.meshtastic.core.ui.component.MainAppBar

/**
 * Shared About/Acknowledgements screen using the multiplatform [LibrariesContainer] composable and [produceLibraries]
 * from the AboutLibraries KMP library.
 *
 * Leverages the full M3 [LibrariesContainer] API:
 * - **header**: app branding with descriptive text
 * - **divider**: [HorizontalDivider] between library items for clean visual separation
 * - **footer**: total library count summary
 * - **contentPadding**: proper LazyColumn padding (avoids clipping during scroll)
 * - **license dialog**: built-in license dialog on library tap (default behavior)
 *
 * Each platform provides a [jsonProvider] lambda that loads the library definitions JSON
 *
 * @see <a href="https://github.com/mikepenz/AboutLibraries">AboutLibraries KMP</a>
 */
@Composable
fun AboutScreen(onNavigateUp: () -> Unit, jsonProvider: suspend () -> String) {
    val libraries by produceLibraries(jsonProvider)

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.acknowledgements),
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
            showAuthor = true,
            showVersion = true,
            showDescription = true,
            showLicenseBadges = true,
            showFundingBadges = true,
            header = {
                item {
                    AboutHeader()
                    HorizontalDivider()
                }
            },
            divider = { HorizontalDivider() },
            footer = {
                val count = libraries?.libraries?.size ?: 0
                if (count > 0) {
                    item {
                        HorizontalDivider()
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(Res.string.library_count, count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun AboutHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(Res.string.open_source_libraries),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.open_source_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
