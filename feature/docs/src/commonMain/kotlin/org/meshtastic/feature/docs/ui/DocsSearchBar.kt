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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_clear_search
import org.meshtastic.core.resources.doc_search_placeholder
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search

/**
 * Tag for the collapsed field; distinct from [DOCS_SEARCH_BAR_EXPANDED_INPUT_FIELD_TAG] so tests can target it alone.
 */
const val DOCS_SEARCH_BAR_INPUT_FIELD_TAG = "DocsSearchBarInputField"

/** Tag for the field instance the full-screen overlay shows once expanded. */
const val DOCS_SEARCH_BAR_EXPANDED_INPUT_FIELD_TAG = "DocsSearchBarExpandedInputField"

/**
 * Search bar for filtering documentation pages by keywords.
 *
 * A real M3 [SearchBar] driven by [rememberSearchBarState]: collapsed it sits inline above the results, and focusing it
 * expands to a full-screen overlay showing [expandedContent] under the same field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: SearchBarScrollBehavior? = null,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    val searchPlaceholder = stringResource(Res.string.doc_search_placeholder)
    val clearSearchDescription = stringResource(Res.string.doc_clear_search)
    val backDescription = stringResource(Res.string.navigate_back)
    val textFieldState = rememberTextFieldState(query)
    val searchBarState = rememberSearchBarState()
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)

    // A caller-driven query change (e.g. an external reset) must be mirrored into the field.
    LaunchedEffect(query, textFieldState) {
        if (textFieldState.text.toString() != query) {
            textFieldState.edit {
                replace(0, length, query)
                placeCursorAtEnd()
            }
        }
    }
    // Edits made through the field are reported back to the caller.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { latestOnQueryChange(it) }
    }

    val barModifier =
        modifier.fillMaxWidth().let { base ->
            scrollBehavior?.let { behavior -> with(behavior) { base.searchBarScrollBehavior() } } ?: base
        }
    SearchBar(
        state = searchBarState,
        inputField = {
            DocsSearchInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                placeholder = searchPlaceholder,
                clearDescription = clearSearchDescription,
                backDescription = backDescription,
                testTag = DOCS_SEARCH_BAR_INPUT_FIELD_TAG,
            )
        },
        modifier = barModifier,
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            DocsSearchInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                placeholder = searchPlaceholder,
                clearDescription = clearSearchDescription,
                backDescription = backDescription,
                testTag = DOCS_SEARCH_BAR_EXPANDED_INPUT_FIELD_TAG,
            )
        },
        content = expandedContent,
    )
}

/** The [SearchBarDefaults.InputField] content shared by the collapsed bar and the expanded overlay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocsSearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    placeholder: String,
    clearDescription: String,
    backDescription: String,
    testTag: String,
) {
    val scope = rememberCoroutineScope()
    val isExpanded = searchBarState.currentValue == SearchBarValue.Expanded
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            if (isExpanded) {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(imageVector = MeshtasticIcons.ArrowBack, contentDescription = backDescription)
                }
            } else {
                Icon(imageVector = MeshtasticIcons.Search, contentDescription = null)
            }
        },
        trailingIcon = {
            if (textFieldState.text.isNotEmpty()) {
                IconButton(onClick = { textFieldState.clearText() }) {
                    Icon(imageVector = MeshtasticIcons.Close, contentDescription = clearDescription)
                }
            }
        },
        modifier = Modifier.testTag(testTag).semantics { contentDescription = placeholder },
    )
}
