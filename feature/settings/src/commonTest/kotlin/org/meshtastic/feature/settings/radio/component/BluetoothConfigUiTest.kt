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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.discard_changes
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save_and_restart
import org.meshtastic.core.resources.schema_bluetooth_enabled
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.proto.Config.BluetoothConfig
import org.meshtastic.proto.Config.BluetoothConfig.PairingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Drives [BluetoothConfigContent] directly, so no [org.meshtastic.feature.settings.radio.RadioConfigViewModel]. */
@OptIn(ExperimentalTestApi::class)
class BluetoothConfigUiTest {

    private class Harness(val saved: MutableList<BluetoothConfig> = mutableListOf())

    private fun ComposeUiTest.show(
        stored: BluetoothConfig,
        harness: Harness,
        saveRestore: SaveRestoreHarness = SaveRestoreHarness(),
    ) = setContent {
        saveRestore.Content {
            AppTheme {
                BluetoothConfigContent(
                    bluetoothConfig = stored,
                    connected = true,
                    responseState = ResponseState.Empty,
                    connectPromptVisible = false,
                    onDismissPacketResponse = {},
                    onBack = {},
                    onSave = { harness.saved += it },
                )
            }
        }
    }

    private fun stored(mode: PairingMode, pin: Int) = BluetoothConfig.Builder()
        .also { wb ->
            wb.enabled = true
            wb.mode = mode
            wb.fixed_pin = pin
        }
        .build()

    private fun ComposeUiTest.enterPin(text: String) {
        onNode(hasSetTextAction()).performTextReplacement(text)
        waitForIdle()
    }

    @Test
    fun `leading zero PINs disable Save in fixed PIN mode`() = runComposeUiTest {
        show(stored(PairingMode.FIXED_PIN, 123456), Harness())
        listOf("012345", "000000").forEach { pin ->
            enterPin(pin)
            onNodeWithText(getString(Res.string.save_and_restart)).assertIsNotEnabled()
        }
    }

    @Test
    fun `boundary PINs save the typed fixed_pin`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness)
        enterPin("100000")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(100000, harness.saved.single().fixed_pin)
    }

    @Test
    fun `999999 saves as typed`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness)
        enterPin("999999")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(999999, harness.saved.single().fixed_pin)
    }

    @Test
    fun `an edited invalid PIN disables Save outside fixed PIN mode too`() = runComposeUiTest {
        show(stored(PairingMode.RANDOM_PIN, 123456), Harness())
        enterPin("012345")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsNotEnabled()
    }

    @Test
    fun `returning to an invalid stored PIN restores the form and clears the dirty state`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.RANDOM_PIN, 0), harness)
        enterPin("123456")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsEnabled()
        enterPin("000000")
        onAllNodesWithText(getString(Res.string.save_and_restart)).assertCountEquals(0)
        assertNull(harness.saved.firstOrNull())
    }

    @Test
    fun `a valid draft survives save and restore and still saves`() = runComposeUiTest {
        val harness = Harness()
        val saveRestore = SaveRestoreHarness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness, saveRestore)
        enterPin("654321")

        saveRestore.saveAndRestore(this)

        onNode(hasSetTextAction()).assertTextContains("654321")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(654321, harness.saved.single().fixed_pin)
    }

    @Test
    fun `an invalid draft survives save and restore and keeps Save disabled`() = runComposeUiTest {
        val saveRestore = SaveRestoreHarness()
        show(stored(PairingMode.FIXED_PIN, 123456), Harness(), saveRestore)
        enterPin("012345")

        saveRestore.saveAndRestore(this)

        onNode(hasSetTextAction()).assertTextContains("012345")
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsNotEnabled()
    }

    @Test
    fun `discard resets the field to the stored PIN`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness)
        enterPin("654321")

        onNodeWithText(getString(Res.string.discard_changes)).performClick()
        waitForIdle()

        onNode(hasSetTextAction()).assertTextContains("123456")
        onAllNodesWithText(getString(Res.string.save_and_restart)).assertCountEquals(0)
        assertNull(harness.saved.firstOrNull())
    }

    private fun ComposeUiTest.toggleBluetoothEnabled() {
        onNodeWithText(getString(Res.string.schema_bluetooth_enabled)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.save() {
        onNodeWithText(getString(Res.string.save_and_restart)).performClick()
        waitForIdle()
    }

    @Test
    fun `clicking a disabled Save with an invalid PIN saves nothing`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness)
        enterPin("012345")
        save()
        assertNull(harness.saved.firstOrNull())
    }

    @Test
    fun `an unchanged invalid stored PIN blocks an unrelated save in fixed PIN mode`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 0), harness)
        toggleBluetoothEnabled()
        onNodeWithText(getString(Res.string.save_and_restart)).assertIsNotEnabled()
        save()
        assertNull(harness.saved.firstOrNull())
    }

    @Test
    fun `an unchanged invalid stored PIN is saved untouched outside fixed PIN mode`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.RANDOM_PIN, 0), harness)
        toggleBluetoothEnabled()
        save()
        assertEquals(0, harness.saved.single().fixed_pin)
        assertEquals(false, harness.saved.single().enabled)
    }

    @Test
    fun `an unrelated save after discard submits the stored PIN`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.FIXED_PIN, 123456), harness)
        enterPin("654321")
        onNodeWithText(getString(Res.string.discard_changes)).performClick()
        waitForIdle()
        toggleBluetoothEnabled()
        save()
        assertEquals(123456, harness.saved.single().fixed_pin)
    }

    @Test
    fun `an unrelated save after retyping the stored PIN submits the stored PIN`() = runComposeUiTest {
        val harness = Harness()
        show(stored(PairingMode.RANDOM_PIN, 0), harness)
        enterPin("123456")
        enterPin("000000")
        toggleBluetoothEnabled()
        save()
        assertEquals(0, harness.saved.single().fixed_pin)
    }
}
