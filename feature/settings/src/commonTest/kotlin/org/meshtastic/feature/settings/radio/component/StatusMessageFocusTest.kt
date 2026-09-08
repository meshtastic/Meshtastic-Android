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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

private const val OTHER_FIELD_TAG = "other_field"

/**
 * The node list's shortcut opens this screen on the status message field. The field is gated on an async capability
 * read, so it composes after the first frame — and the operator must keep focus once they move off it.
 *
 * The container is a [LazyColumn] because the real screen's is: its items are subcomposed at measure time, so a request
 * issued from the enclosing scope reaches an unattached node and fails silently. A plain Column does not reproduce
 * that.
 */
@OptIn(ExperimentalTestApi::class)
class StatusMessageFocusTest {

    /** The real field, in the real container, beside a plain one, over hoisted state the test drives. */
    private fun ComposeUiTest.showFields(
        requested: Boolean,
        supported: MutableState<Boolean>,
        connected: MutableState<Boolean>,
    ) {
        setContent {
            AppTheme {
                val focus =
                    rememberStatusMessageFocus(
                        requested = requested,
                        supported = supported.value,
                        connected = connected.value,
                    )
                LazyColumn {
                    item {
                        EditTextPreference(
                            title = "Other",
                            value = "",
                            enabled = true,
                            isError = false,
                            keyboardOptions = KeyboardOptions.Default,
                            keyboardActions = KeyboardActions.Default,
                            onValueChanged = {},
                            modifier = Modifier.testTag(OTHER_FIELD_TAG),
                        )
                        if (supported.value) {
                            StatusMessageField(value = "", enabled = connected.value, focus = focus, onValueChange = {})
                        }
                    }
                }
            }
        }
    }

    /** The text field itself: [EditTextPreference] tags its wrapping column, which carries no text actions. */
    private fun ComposeUiTest.field(tag: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun `the field is focused once the capability read lands`() = runComposeUiTest {
        val supported = mutableStateOf(false)
        showFields(requested = true, supported = supported, connected = mutableStateOf(true))

        supported.value = true
        waitForIdle()

        field(USER_STATUS_MESSAGE_TEST_TAG).assertIsFocused()
    }

    @Test
    fun `the field is focused when the capability is known from the first frame`() = runComposeUiTest {
        showFields(requested = true, supported = mutableStateOf(true), connected = mutableStateOf(true))

        field(USER_STATUS_MESSAGE_TEST_TAG).assertIsFocused()
    }

    @Test
    fun `arriving without the shortcut leaves the field alone`() = runComposeUiTest {
        showFields(requested = false, supported = mutableStateOf(true), connected = mutableStateOf(true))

        field(USER_STATUS_MESSAGE_TEST_TAG).assertIsNotFocused()
    }

    @Test
    fun `focus is not stolen back after the operator moves on`() = runComposeUiTest {
        val connected = mutableStateOf(true)
        showFields(requested = true, supported = mutableStateOf(true), connected = connected)

        field(OTHER_FIELD_TAG).performClick()
        // A connection flap re-runs the request; the one-shot latch has to hold.
        connected.value = false
        waitForIdle()
        connected.value = true
        waitForIdle()

        field(USER_STATUS_MESSAGE_TEST_TAG).assertIsNotFocused()
        field(OTHER_FIELD_TAG).assertIsFocused()
    }
}
