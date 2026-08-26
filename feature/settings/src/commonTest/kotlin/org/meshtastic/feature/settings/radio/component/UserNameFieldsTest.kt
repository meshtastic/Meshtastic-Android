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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A licensed node's owner long name is the `CALLSIGN//Long name` firmware composes, edited here as two fields. These
 * lock down that the split and the rejoin stay inverses through the UI — a regression would silently send the operator
 * a name they never typed.
 */
@OptIn(ExperimentalTestApi::class)
class UserNameFieldsTest {

    /** Renders [UserNameFields] over [user] and returns an accessor for the edited message. */
    private fun ComposeUiTest.showNameFields(user: User, hamMode: Boolean): () -> User {
        lateinit var formState: ConfigState<User>
        setContent {
            AppTheme {
                formState = rememberConfigState(user)
                UserNameFields(
                    formState = formState,
                    hamMode = hamMode,
                    enabled = true,
                    isLongNameError = false,
                    isShortNameError = false,
                )
            }
        }
        return { formState.value }
    }

    /** The text field itself: [EditTextPreference] tags its wrapping column, which carries no text actions. */
    private fun ComposeUiTest.field(tag: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun ComposeUiTest.fieldText(tag: String): String =
        field(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun `the ham long name field only exists while licensed`() = runComposeUiTest {
        showNameFields(User(long_name = "Attic Heltec", short_name = "ATTC"), hamMode = false)

        onNodeWithTag(HAM_LONG_NAME_TEST_TAG).assertDoesNotExist()
        assertEquals("Attic Heltec", fieldText(USER_LONG_NAME_TEST_TAG))
    }

    @Test
    fun `the call sign field shows only the call sign half of a composed name`() = runComposeUiTest {
        showNameFields(User(long_name = "KD2ABC//Attic Heltec", short_name = "ABC"), hamMode = true)

        assertEquals("KD2ABC", fieldText(USER_LONG_NAME_TEST_TAG))
        assertEquals("Attic Heltec", fieldText(HAM_LONG_NAME_TEST_TAG))
    }

    @Test
    fun `editing the call sign keeps the long name half`() = runComposeUiTest {
        val user = showNameFields(User(long_name = "KD2ABC//Attic Heltec", short_name = "ABC"), hamMode = true)

        field(USER_LONG_NAME_TEST_TAG).performTextClearance()
        field(USER_LONG_NAME_TEST_TAG).performTextInput("N0CALL")

        assertEquals("N0CALL//Attic Heltec", user().long_name)
    }

    @Test
    fun `editing the long name keeps the call sign`() = runComposeUiTest {
        val user = showNameFields(User(long_name = "KD2ABC", short_name = "ABC"), hamMode = true)

        field(HAM_LONG_NAME_TEST_TAG).performTextInput("Garage")

        assertEquals("KD2ABC//Garage", user().long_name)
    }

    @Test
    fun `clearing the long name leaves the node named after the call sign alone`() = runComposeUiTest {
        val user = showNameFields(User(long_name = "KD2ABC//Attic Heltec", short_name = "ABC"), hamMode = true)

        field(HAM_LONG_NAME_TEST_TAG).performTextClearance()

        assertEquals("KD2ABC", user().long_name)
    }

    @Test
    fun `the widest pair the proto can carry composes whole`() = runComposeUiTest {
        val user = showNameFields(User(long_name = "KD2ABCD", short_name = "ABC"), hamMode = true)

        field(HAM_LONG_NAME_TEST_TAG).performTextInput("Attic Heltec 3")

        assertEquals("KD2ABCD//Attic Heltec 3", user().long_name)
    }
}
