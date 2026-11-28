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

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.component.TRANSPARENT_MODE_SWITCH_TEST_TAG
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import kotlin.test.Test

/** Transparent mode drops remote admin, so only the connected node may change it. */
@OptIn(ExperimentalTestApi::class)
class TransparentModeRowTest {

    private fun ComposeUiTest.setRow(isLocal: Boolean, connected: Boolean, on: Boolean) = setContent {
        AppTheme {
            TransparentModeRow(
                isLocal = isLocal,
                connected = connected,
                formState = rememberConfigState(Config.LoRaConfig.Builder().also { it.transparent_mode = on }.build()),
            )
        }
    }

    private fun ComposeUiTest.switch() = onNodeWithTag(TRANSPARENT_MODE_SWITCH_TEST_TAG)

    @Test
    fun `the connected node gets the control`() = runComposeUiTest {
        setRow(isLocal = true, connected = true, on = false)

        switch().assertIsEnabled().assertIsOff()
    }

    @Test
    fun `a remote node with transparent mode off shows no control`() = runComposeUiTest {
        setRow(isLocal = false, connected = true, on = false)

        switch().assertDoesNotExist()
    }

    @Test
    fun `a remote node with transparent mode on shows it read-only`() = runComposeUiTest {
        setRow(isLocal = false, connected = true, on = true)

        switch().assertIsNotEnabled().assertIsOn()
    }

    @Test
    fun `the control is off limits while the connected node is disconnected`() = runComposeUiTest {
        setRow(isLocal = true, connected = false, on = false)

        switch().assertIsNotEnabled()
    }
}
