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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

private const val FIELD_TAG = "focus_target"

@Composable
private fun FocusableField(focusRequester: FocusRequester?) {
    EditTextPreference(
        title = "Status Message",
        value = "",
        enabled = true,
        isError = false,
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = KeyboardActions.Default,
        onValueChanged = {},
        modifier = Modifier.testTag(FIELD_TAG),
        focusRequester = focusRequester,
    )
}

/**
 * [EditTextPreference] applies the caller's modifier to its wrapping column, so a caller that wants the text field
 * itself focused has to be handed a route to it.
 */
@OptIn(ExperimentalTestApi::class)
class EditTextPreferenceFocusTest {

    @Test
    fun `a focus requester focuses the text field rather than its wrapping column`() = runComposeUiTest {
        setContent {
            AppTheme {
                val requester = remember { FocusRequester() }
                FocusableField(requester)
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }

        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(FIELD_TAG))).assertIsFocused()
    }

    @Test
    fun `a field with no focus requester is left alone`() = runComposeUiTest {
        setContent { AppTheme { FocusableField(focusRequester = null) } }

        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(FIELD_TAG))).assertIsNotFocused()
    }
}
