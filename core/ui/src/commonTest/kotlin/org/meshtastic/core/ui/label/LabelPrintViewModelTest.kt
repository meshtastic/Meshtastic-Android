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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleCharacteristicInfo
import org.meshtastic.core.ble.BleServiceInfo
import org.meshtastic.core.ble.niimbot.LabelBitmap
import org.meshtastic.core.ble.niimbot.NiimbotFault
import org.meshtastic.core.ble.niimbot.NiimbotPacket
import org.meshtastic.core.ble.niimbot.NiimbotPrinterFactory
import org.meshtastic.core.repository.LabelPrintPrefs
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private class FakeLabelPrintPrefs : LabelPrintPrefs {
    override val labelLengthMm = MutableStateFlow(LabelPrintPrefs.DEFAULT_LABEL_LENGTH_MM)
    override val printerAddress = MutableStateFlow<String?>(null)

    override fun setLabelLengthMm(millimeters: Int) {
        labelLengthMm.value = millimeters
    }

    override fun setPrinterAddress(address: String?) {
        printerAddress.value = address
    }
}

/** design#185 item 5 (remember and reconnect the printer) and item 6 (report printer errors in plain words). */
@OptIn(ExperimentalCoroutinesApi::class)
class LabelPrintViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val vendor = Uuid.parse("e7810a71-73ae-499d-8c15-faa9aef0c3f2")
    private val channel = Uuid.parse("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")
    private val connection =
        FakeBleConnection().apply {
            discoveredServices =
                listOf(
                    BleServiceInfo(
                        vendor,
                        listOf(BleCharacteristicInfo(channel, notifies = true, writesWithoutResponse = true)),
                    ),
                )
        }
    private val scanner = FakeBleScanner()
    private val prefs = FakeLabelPrintPrefs()
    private val mine = FakeBleDevice("AA:AA:AA:AA:AA:AA", name = "D110_M-MINE")
    private val other = FakeBleDevice("BB:BB:BB:BB:BB:BB", name = "D110-OTHER")

    private fun viewModel() =
        LabelPrintViewModel(NiimbotPrinterFactory(scanner, FakeBleConnectionFactory(connection)), prefs)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `printers found are listed and none connects before one was ever picked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        scanner.emitDevice(other)
        scanner.emitDevice(FakeBleDevice("CC:CC:CC:CC:CC:CC", name = "Meshtastic_1234"))

        viewModel.start()

        assertEquals(LabelPrinterStatus.SEARCHING, viewModel.state.value.status)
        assertEquals(listOf(other.address), viewModel.state.value.found.map { it.address })
    }

    @Test
    fun `picking a printer remembers it and the remembered printer reconnects when it is seen`() = runTest(dispatcher) {
        scanner.emitDevice(other)
        scanner.emitDevice(mine)
        val first = viewModel().apply { start() }
        first.connect(mine)
        assertEquals(mine.address, prefs.printerAddress.value)
        first.stop()

        val second = viewModel().apply { start() }

        assertEquals(LabelPrinterStatus.READY, second.state.value.status)
        assertEquals("D110_M-MINE", second.state.value.printerName)
    }

    @Test
    fun `a printer that drops out is looked for again`() = runTest(dispatcher) {
        prefs.printerAddress.value = mine.address
        scanner.emitDevice(mine)
        val viewModel = viewModel().apply { start() }
        assertEquals(LabelPrinterStatus.READY, viewModel.state.value.status)

        connection.simulateRemoteDisconnect()

        // The fake scanner replays the printer, so the new search finds it and connects again.
        assertEquals(LabelPrinterStatus.READY, viewModel.state.value.status)
        assertEquals(2, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a fault the printer reports is shown and the printer stays ready`() = runTest(dispatcher) {
        prefs.printerAddress.value = mine.address
        scanner.emitDevice(mine)
        // The printer answers every request with fault 0x01, cover open.
        connection.service.beforeWrite = { _, _ ->
            connection.service.emitNotification(channel, NiimbotPacket(0xDB, byteArrayOf(0x01)).toBytes())
        }
        val viewModel = viewModel().apply { start() }

        viewModel.print(LabelBitmap(width = 96, height = 1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LabelPrintError.Fault(NiimbotFault.COVER_OPEN, 0x01), state.error)
        assertEquals(LabelPrinterStatus.READY to false, state.status to state.printed)
    }
}
