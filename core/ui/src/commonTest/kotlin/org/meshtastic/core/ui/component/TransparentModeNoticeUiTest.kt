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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.repository.TransparentModeTurnOff
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.retry
import org.meshtastic.core.resources.transparent_mode_enable_confirm
import org.meshtastic.core.resources.transparent_mode_leave_on
import org.meshtastic.core.resources.transparent_mode_still_on
import org.meshtastic.core.resources.transparent_mode_turn_off
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

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

        onNodeWithText(getString(Res.string.transparent_mode_leave_on)).performClick()

        assertEquals(0, turnOffs)
        assertEquals(1, closes)
    }

    @Test
    fun `turn off requests the turn-off and leaves the notice to report it`() = runComposeUiTest {
        setNotice(turnOff = null)

        onNodeWithText(getString(Res.string.transparent_mode_turn_off)).performClick()

        assertEquals(1, turnOffs)
        assertEquals(0, closes)
    }

    @Test
    fun `a node that still reports on says so and retry turns off again`() = runComposeUiTest {
        setNotice(turnOff = TransparentModeTurnOff.STILL_ON)

        onNodeWithText(getString(Res.string.transparent_mode_still_on)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.retry)).performClick()

        assertEquals(1, turnOffs)
        assertEquals(0, closes)
    }

    @Test
    fun `turning the setting on asks first and cancel leaves it off`() = runComposeUiTest {
        setSetting(checked = false)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(emptyList(), settingChanges)
        onNodeWithText(getString(Res.string.cancel)).performClick()
        assertEquals(emptyList(), settingChanges)
        onNodeWithText(getString(Res.string.transparent_mode_enable_confirm)).assertDoesNotExist()
    }

    @Test
    fun `confirming turns the setting on`() = runComposeUiTest {
        setSetting(checked = false)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()
        onNodeWithText(getString(Res.string.transparent_mode_enable_confirm)).performClick()

        assertEquals(listOf(true), settingChanges)
    }

    @Test
    fun `turning the setting off applies without asking`() = runComposeUiTest {
        setSetting(checked = true)

        onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(listOf(false), settingChanges)
        onNodeWithText(getString(Res.string.transparent_mode_enable_confirm)).assertDoesNotExist()
    }
}
