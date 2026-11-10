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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.schema_bluetooth_pairingmode_random_pin
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig.SerialConfig.Serial_Mode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PickerOptionsTest {

    @Test
    fun `serial mode picker leaves out MODBUS which no firmware release implements`() {
        val options = serialModeOptions(selected = Serial_Mode.DEFAULT)

        assertFalse(Serial_Mode.MODBUS in options)
        assertTrue(Serial_Mode.CALTOPO in options)
    }

    @Test
    fun `serial mode picker keeps MODBUS while the node already holds it`() {
        assertTrue(Serial_Mode.MODBUS in serialModeOptions(selected = Serial_Mode.MODBUS))
    }

    @Test
    fun `pairing mode picker shows the schema label instead of the enum constant`() = runComposeUiTest {
        setContent {
            AppTheme {
                BluetoothPairingModePreference(
                    enabled = true,
                    mode = Config.BluetoothConfig.PairingMode.RANDOM_PIN,
                    onModeChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.schema_bluetooth_pairingmode_random_pin)).assertIsDisplayed()
        onNodeWithText("RANDOM_PIN").assertDoesNotExist()
    }
}
