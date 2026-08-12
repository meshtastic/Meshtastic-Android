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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.licensed_mode_enable_confirm
import org.meshtastic.core.resources.licensed_mode_enable_warning
import org.meshtastic.core.resources.licensed_mode_enable_warning_signed
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class LicensedModeSettingTest {

    @Test
    fun `enabling licensed mode requires confirmation`() = runComposeUiTest {
        var checked: Boolean? = null
        setContent {
            AppTheme {
                LicensedModeSetting(
                    checked = false,
                    enabled = true,
                    signingSupported = true,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()

        assertNull(checked)
        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.cancel)).performClick()
        assertNull(checked)
        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).assertDoesNotExist()
    }

    @Test
    fun `confirmed enable applies the change`() = runComposeUiTest {
        var checked: Boolean? = null
        setContent {
            AppTheme {
                LicensedModeSetting(
                    checked = false,
                    enabled = true,
                    signingSupported = true,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()
        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).performClick()

        assertEquals(true, checked)
    }

    @Test
    fun `signing firmware shows authenticated plaintext and migration copy`() = runComposeUiTest {
        setContent { AppTheme { LicensedModeSetting(checked = false, enabled = true, signingSupported = true) {} } }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()

        onNodeWithText(getString(Res.string.licensed_mode_enable_warning_signed)).assertIsDisplayed()
    }

    @Test
    fun `non-signing firmware shows legacy plaintext copy`() = runComposeUiTest {
        setContent { AppTheme { LicensedModeSetting(checked = false, enabled = true, signingSupported = false) {} } }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()

        onNodeWithText(getString(Res.string.licensed_mode_enable_warning)).assertIsDisplayed()
    }

    @Test
    fun `unknown capability shows legacy plaintext copy`() = runComposeUiTest {
        setContent { AppTheme { LicensedModeSetting(checked = false, enabled = true, signingSupported = null) {} } }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()

        onNodeWithText(getString(Res.string.licensed_mode_enable_warning)).assertIsDisplayed()
    }

    @Test
    fun `disabling licensed mode applies immediately without confirmation`() = runComposeUiTest {
        var checked: Boolean? = null
        setContent {
            AppTheme {
                LicensedModeSetting(
                    checked = true,
                    enabled = true,
                    signingSupported = true,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(false, checked)
        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).assertDoesNotExist()
    }

    @Test
    fun `connection loss dismisses confirmation without changing state`() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        var checked: Boolean? = null
        setContent {
            AppTheme {
                LicensedModeSetting(
                    checked = false,
                    enabled = enabled,
                    signingSupported = true,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithTag(LICENSED_MODE_SWITCH_TEST_TAG).performClick()
        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).assertIsDisplayed()

        enabled = false
        waitForIdle()

        onNodeWithText(getString(Res.string.licensed_mode_enable_confirm)).assertDoesNotExist()
        assertNull(checked)
    }
}
