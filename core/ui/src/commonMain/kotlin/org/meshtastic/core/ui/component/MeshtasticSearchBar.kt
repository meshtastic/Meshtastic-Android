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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedDockedSearchBar
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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search

/** Appended to the caller's tag for the field the expanded overlay shows, so a test can target one of the two. */
const val SEARCH_BAR_EXPANDED_TAG_SUFFIX = "Expanded"

/**
 * The Material 3 search bar every Meshtastic search field is built from: a collapsed [SearchBar] that expands into a
 * results surface over the screen.
 *
 * The expanded half follows Material's own rule rather than the caller's: full-screen on compact width, docked on
 * anything wider, because a full-screen dialog over a tablet or desktop window hides context the user was reading.
 *
 * @param query the text the field starts with, so a query that survived process death restores into the bar. Later
 *   changes to it are ignored; the field is the source of truth once it exists. See [resetKey].
 * @param onQueryChange called for every edit made through the field.
 * @param placeholder shown in the empty field, and the field's accessibility name.
 * @param resetKey change this to make the field take [query] again, for a caller that clears the search from outside.
 * @param inputFieldTag test tag for the collapsed field; the expanded one takes it plus
 *   [SEARCH_BAR_EXPANDED_TAG_SUFFIX].
 * @param scrollBehavior lets the bar react to the content scrolling under it; see
 *   [SearchBarDefaults.enterAlwaysSearchBarScrollBehavior].
 * @param trailingActions extra icons after the clear button, for controls that belong to the search itself (a sort
 *   menu, a filter menu). They render in the collapsed and expanded field alike.
 * @param expandedContent the results shown under the field once expanded.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MeshtasticSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearDescription: String = stringResource(Res.string.clear),
    resetKey: Any? = Unit,
    inputFieldTag: String = SEARCH_BAR_INPUT_FIELD_TAG,
    scrollBehavior: SearchBarScrollBehavior? = null,
    trailingActions: @Composable RowScope.() -> Unit = {},
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    // The field owns the text; [query] only seeds it, and [resetKey] is how a caller replaces it.
    //
    // Feeding [query] back in on every change would lose keystrokes wherever it makes a round trip that lags the
    // typing - the node list's does, through SavedStateHandle and a combined flow - because a stale value arriving
    // while the field is further ahead overwrites what was typed.
    val textFieldState = rememberTextFieldState(query)
    val searchBarState = rememberSearchBarState()
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)
    val latestQuery by rememberUpdatedState(query)

    LaunchedEffect(resetKey) {
        if (textFieldState.text.toString() != latestQuery) {
            textFieldState.edit {
                replace(0, length, latestQuery)
                placeCursorAtEnd()
            }
        }
    }
    // Edits made through the field are reported back to the caller.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { latestOnQueryChange(it) }
    }

    val inputField: @Composable (String) -> Unit = { tag ->
        SearchInputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            placeholder = placeholder,
            clearDescription = clearDescription,
            testTag = tag,
            trailingActions = trailingActions,
        )
    }

    val barModifier =
        modifier.fillMaxWidth().let { base ->
            scrollBehavior?.let { behavior -> with(behavior) { base.searchBarScrollBehavior() } } ?: base
        }
    SearchBar(state = searchBarState, inputField = { inputField(inputFieldTag) }, modifier = barModifier)

    val expandedTag = inputFieldTag + SEARCH_BAR_EXPANDED_TAG_SUFFIX
    // Material docks the expanded bar on medium and larger windows and only goes full-screen on compact ones. The
    // pane directive is the width test the rest of the app already adapts on, so search agrees with the panes.
    val splitsHorizontally = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions > 1
    if (splitsHorizontally) {
        ExpandedDockedSearchBar(
            state = searchBarState,
            inputField = { inputField(expandedTag) },
            content = expandedContent,
        )
    } else {
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = { inputField(expandedTag) },
            content = expandedContent,
        )
    }
}

/** Default tag for the collapsed field, for callers with only one search bar on screen. */
const val SEARCH_BAR_INPUT_FIELD_TAG = "SearchBarInputField"

/** The [SearchBarDefaults.InputField] shared by the collapsed bar and the expanded overlay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    placeholder: String,
    clearDescription: String,
    testTag: String,
    trailingActions: @Composable RowScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isExpanded = searchBarState.currentValue == SearchBarValue.Expanded
    val backDescription = stringResource(Res.string.navigate_back)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(imageVector = MeshtasticIcons.Close, contentDescription = clearDescription)
                    }
                }
                trailingActions()
            }
        },
        modifier = Modifier.testTag(testTag).semantics { contentDescription = placeholder },
    )
}
