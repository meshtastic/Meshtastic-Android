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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_clear_search
import org.meshtastic.core.resources.doc_search_placeholder
import org.meshtastic.core.ui.component.MeshtasticSearchBar
import org.meshtastic.core.ui.component.SEARCH_BAR_EXPANDED_TAG_SUFFIX

/**
 * Tag for the collapsed field; distinct from [DOCS_SEARCH_BAR_EXPANDED_INPUT_FIELD_TAG] so tests can target it alone.
 */
const val DOCS_SEARCH_BAR_INPUT_FIELD_TAG = "DocsSearchBarInputField"

/** Tag for the field instance the full-screen overlay shows once expanded. */
const val DOCS_SEARCH_BAR_EXPANDED_INPUT_FIELD_TAG = DOCS_SEARCH_BAR_INPUT_FIELD_TAG + SEARCH_BAR_EXPANDED_TAG_SUFFIX

/** Search bar for filtering documentation pages by keywords. */
@Composable
fun DocsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: SearchBarScrollBehavior? = null,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    MeshtasticSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(Res.string.doc_search_placeholder),
        modifier = modifier,
        clearDescription = stringResource(Res.string.doc_clear_search),
        inputFieldTag = DOCS_SEARCH_BAR_INPUT_FIELD_TAG,
        scrollBehavior = scrollBehavior,
        expandedContent = expandedContent,
    )
}
