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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_clear_search
import org.meshtastic.core.resources.doc_search_placeholder
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search

/** Search bar for filtering documentation pages by keywords. */
@Composable
fun DocsSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val searchPlaceholder = stringResource(Res.string.doc_search_placeholder)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(searchPlaceholder) },
        leadingIcon = { Icon(imageVector = MeshtasticIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MeshtasticIcons.Close,
                        contentDescription = stringResource(Res.string.doc_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = searchPlaceholder },
    )
}
