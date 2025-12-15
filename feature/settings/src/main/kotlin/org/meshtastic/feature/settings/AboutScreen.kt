/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.acknowledgements
import org.meshtastic.core.ui.component.MainAppBar

@Composable
fun AboutScreen(onNavigateUp: () -> Unit) {
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
        val context = LocalContext.current
        val libraries = Libs.Builder().withContext(context).build()
        LibrariesContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            libraries = libraries,
        )
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    MaterialTheme { AboutScreen(onNavigateUp = {}) }
}
