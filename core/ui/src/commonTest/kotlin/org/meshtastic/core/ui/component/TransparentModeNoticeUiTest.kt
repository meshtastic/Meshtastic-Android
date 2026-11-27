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

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.repository.TransparentModeTurnOff
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Everything is found by test tag: the buttons' labels and the result text are string resources, which do not load in
 * the wasmJs browser tests.
 */
@OptIn(ExperimentalTestApi::class)
class TransparentModeNoticeUiTest {

    private var turnOffs = 0
    private var closes = 0
    private val settingChanges = mutableListOf<Boolean>()

    private fun ComposeUiTest.setNotice(turnOff: TransparentModeTurnOff?) = setContent {
        AppTheme { TransparentModeNotice(turnOff = turnOff, onTurnOff = { turnOffs++ }, onClose = { closes++ }) }
    }

    private fun ComposeUiTest.setSetting(checked: Boolean) = setContent {
        AppTheme {
            TransparentModeSetting(checked = checked, enabled = true, onCheckedChange = { settingChanges += it })
        }
    }

    @Test
    fun `leave on closes the notice without turning transparent mode off`() = runComposeUiTest {
        setNotice(turnOff = null)

        onNodeWithTag(TRANSPARENT_MODE_NOTICE_CHOICE_TAG).assertIsDisplayed()
        onNodeWithTag(DIALOG_DISMISS_BUTTON_TAG).performClick()

        assertEquals(0, turnOffs)
        assertEquals(1, closes)
    }

    @Test
    fun `turn off requests the turn-off and leaves the notice to report it`() = runComposeUiTest {
        setNotice(turnOff = null)

        onNodeWithTag(DIALOG_CONFIRM_BUTTON_TAG).performClick()

        assertEquals(1, turnOffs)
        assertEquals(0, closes)
    }

    @Test
    fun `a node that still reports on says so and retry turns off again`() = runComposeUiTest {
        setNotice(turnOff = TransparentModeTurnOff.STILL_ON)

        onNodeWithTag(TRANSPARENT_MODE_NOTICE_STILL_ON_TAG).assertIsDisplayed()
        onNodeWithTag(TRANSPARENT_MODE_NOTICE_UNCONFIRMED_TAG).assertDoesNotExist()
        onNodeWithTag(DIALOG_CONFIRM_BUTTON_TAG).performClick()

        assertEquals(1, turnOffs)
        assertEquals(0, closes)
    }

    @Test
    fun `turning the setting on asks first and cancel leaves it off`() = runComposeUiTest {
        setSetting(checked = false)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(emptyList(), settingChanges)
        onNodeWithTag(TRANSPARENT_MODE_ENABLE_CONFIRMATION_TAG).assertIsDisplayed()
        onNodeWithTag(DIALOG_DISMISS_BUTTON_TAG).performClick()
        assertEquals(emptyList(), settingChanges)
        onNodeWithTag(TRANSPARENT_MODE_ENABLE_CONFIRMATION_TAG).assertDoesNotExist()
    }

    @Test
    fun `confirming turns the setting on`() = runComposeUiTest {
        setSetting(checked = false)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()
        onNodeWithTag(DIALOG_CONFIRM_BUTTON_TAG).performClick()

        assertEquals(listOf(true), settingChanges)
    }

    @Test
    fun `turning the setting off applies without asking`() = runComposeUiTest {
        setSetting(checked = true)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(listOf(false), settingChanges)
        onNodeWithTag(TRANSPARENT_MODE_ENABLE_CONFIRMATION_TAG).assertDoesNotExist()
    }
}
