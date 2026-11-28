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
package org.meshtastic.core.ui.label

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.niimbot.NiimbotPrinterFactory
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The printer search spends one of the few scan starts Android allows, so it runs only while the sheet can use it. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class LabelPrintSheetTest {
    private val label =
        LabelSpec.Channels(
            content = ChannelLabelContent("Hiking", "US · LongFast", "Adds 1 channel"),
            url = "https://meshtastic.org/e/#x",
            band = "JOIN MY MESH",
        )
    private val connection = FakeBleConnection()
    private val viewModel =
        LabelPrintViewModel(
            NiimbotPrinterFactory(FakeBleScanner(), FakeBleConnectionFactory(connection)),
            FakeLabelPrintPrefs(),
        )

    private fun permission(status: PermissionStatus) = PermissionUiState(status, request = {}, openAppSettings = {})

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun statusShownWith(permission: PermissionStatus, bluetoothOff: Boolean): LabelPrinterStatus {
        var status = LabelPrinterStatus.IDLE
        runComposeUiTest {
            setContent {
                AppTheme { LabelPrintSheet(label, onDismiss = {}, viewModel, permission(permission), bluetoothOff) }
            }
            waitForIdle()
            status = viewModel.state.value.status
        }
        return status
    }

    @Test
    fun `the sheet searches for printers once Bluetooth is allowed and on`() {
        assertEquals(LabelPrinterStatus.SEARCHING, statusShownWith(PermissionStatus.GRANTED, bluetoothOff = false))
    }

    @Test
    fun `the sheet does not search without the Bluetooth permission`() {
        assertEquals(
            LabelPrinterStatus.IDLE,
            statusShownWith(PermissionStatus.DENIED_CAN_RETRY, bluetoothOff = false),
        )
    }

    @Test
    fun `the sheet does not search with Bluetooth off`() {
        assertEquals(LabelPrinterStatus.IDLE, statusShownWith(PermissionStatus.GRANTED, bluetoothOff = true))
    }

    @Test
    fun `closing the sheet ends the search and the printer connection`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        setContent {
            AppTheme {
                if (shown) {
                    LabelPrintSheet(label, onDismiss = {}, viewModel, permission(PermissionStatus.GRANTED), false)
                }
            }
        }
        waitForIdle()
        val searching = viewModel.state.value.status

        shown = false
        waitForIdle()

        assertEquals(
            Triple(LabelPrinterStatus.SEARCHING, LabelPrinterStatus.IDLE, 1),
            Triple(searching, viewModel.state.value.status, connection.disconnectCalls),
        )
    }
}
