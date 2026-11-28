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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleCharacteristicInfo
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartException
import org.meshtastic.core.ble.BleScanStartFailureReason
import org.meshtastic.core.ble.BleScanner
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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

    @Test
    fun `a step the printer refuses is shown as refused and the printer stays ready`() = runTest(dispatcher) {
        prefs.printerAddress.value = mine.address
        scanner.emitDevice(mine)
        // Every request gets its own reply carrying 00, which the density and label type replies use for failure.
        connection.service.beforeWrite = { _, bytes ->
            val type = bytes[2].toInt() and 0xFF
            val offset = if (type in setOf(0x21, 0x23, 0xA3, 0xA5)) 16 else 1
            connection.service.emitNotification(channel, NiimbotPacket(type + offset, byteArrayOf(0)).toBytes())
        }
        val viewModel = viewModel().apply { start() }

        viewModel.print(LabelBitmap(width = 96, height = 1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LabelPrintError.Rejected, state.error)
        assertEquals(LabelPrinterStatus.READY to false, state.status to state.printed)
    }

    // A write that fails ends the print as lost while the fake's connection still reads as connected, as when
    // notifications stop with the GATT link up: no drop is ever reported for it.
    @Test
    fun `a print that loses the link lets go of the printer and looks for it again`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        connection.service.writeException = IllegalStateException("write failed")

        viewModel.print(LabelBitmap(width = 96, height = 1))

        val state = viewModel.state.value
        assertEquals(LabelPrinterStatus.READY to LabelPrintError.NoResponse, state.status to state.error)
        assertEquals(1 to 2, connection.disconnectCalls to connection.connectAndAwaitCalls)
    }

    @Test
    fun `a second lost link is not searched for again until the user asks`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        connection.service.writeException = IllegalStateException("write failed")
        viewModel.print(LabelBitmap(width = 96, height = 1))

        viewModel.print(LabelBitmap(width = 96, height = 1))

        assertEquals(LabelPrinterStatus.IDLE to 2, viewModel.state.value.status to connection.connectAndAwaitCalls)
    }

    // The printer here never answers, so a print runs into its reply timeouts: 2 s each for the heartbeat, the status
    // data and the density request, failing at 6 s unless something ends it first.
    private fun readyOnSilentPrinter(): LabelPrintViewModel {
        prefs.printerAddress.value = mine.address
        scanner.emitDevice(mine)
        return viewModel().apply { start() }
    }

    @Test
    fun `closing the sheet ends a print in flight so it cannot write into the closed sheet`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        viewModel.print(LabelBitmap(width = 96, height = 1))

        viewModel.stop()
        advanceTimeBy(10.seconds)

        // A print left running would fail at 6 s and show the closed sheet as ready with an error.
        assertEquals(LabelPrintState(), viewModel.state.value)
    }

    @Test
    fun `a printer that drops during a print ends the print with an error at once`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        viewModel.print(LabelBitmap(width = 96, height = 1))

        connection.simulateRemoteDisconnect()

        val state = viewModel.state.value
        assertEquals(LabelPrintError.NoResponse to false, state.error to state.printed)
        assertEquals(false, state.status == LabelPrinterStatus.PRINTING)
    }

    @Test
    fun `a print from before a drop cannot end the print started after it`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        viewModel.print(LabelBitmap(width = 96, height = 1))
        advanceTimeBy(3.seconds)
        connection.simulateRemoteDisconnect()

        viewModel.print(LabelBitmap(width = 96, height = 1))
        // The first print would have failed 6 s after it started, which is now; the second has 3 s left.
        advanceTimeBy(3500.milliseconds)

        assertEquals(
            LabelPrinterStatus.PRINTING to null,
            viewModel.state.value.status to viewModel.state.value.error,
        )
    }

    @Test
    fun `a print cut off by a drop that is not searched for cannot bring the printer back to ready`() =
        runTest(dispatcher) {
            val viewModel = readyOnSilentPrinter()
            connection.simulateRemoteDisconnect()
            viewModel.print(LabelBitmap(width = 96, height = 1))

            connection.simulateRemoteDisconnect()
            advanceTimeBy(10.seconds)

            val state = viewModel.state.value
            assertEquals(LabelPrinterStatus.IDLE to LabelPrintError.NoResponse, state.status to state.error)
        }

    // Each search spends one of the scan starts Android rations, which the radio's reconnect shares.
    @Test
    fun `a printer that drops a second time is not searched for again until the user asks`() = runTest(dispatcher) {
        val viewModel = readyOnSilentPrinter()
        connection.simulateRemoteDisconnect()
        assertEquals(2, connection.connectAndAwaitCalls)

        connection.simulateRemoteDisconnect()

        assertEquals(LabelPrinterStatus.IDLE to 2, viewModel.state.value.status to connection.connectAndAwaitCalls)
        viewModel.start()
        assertEquals(LabelPrinterStatus.READY, viewModel.state.value.status)
    }

    @Test
    fun `a search Android refuses for scanning too often says so rather than blaming Bluetooth`() =
        runTest(dispatcher) {
            val refusing =
                object : BleScanner {
                    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flow {
                        throw BleScanStartException(
                            BleScanStartFailureReason.ScanningTooFrequently,
                            IllegalStateException("quota"),
                        )
                    }
                }
            val viewModel =
                LabelPrintViewModel(NiimbotPrinterFactory(refusing, FakeBleConnectionFactory(connection)), prefs)

            viewModel.start()

            val state = viewModel.state.value
            assertEquals(LabelPrintError.Scan(BleScanStartFailureReason.ScanningTooFrequently), state.error)
            assertEquals(LabelPrinterStatus.IDLE, state.status)
        }

    /**
     * A connection whose disconnects take time, 5 s for the first and 1 s for the rest, and whose first connect never
     * completes when [firstConnectHangs].
     */
    private class SlowToLetGo(private val inner: FakeBleConnection, private val firstConnectHangs: Boolean = true) :
        BleConnection by inner {
        var connects = 0
        var disconnectsInFlight = 0
        var connectedDuringDisconnect = false
        private var disconnects = 0

        override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState {
            if (connects++ == 0 && firstConnectHangs) awaitCancellation()
            connectedDuringDisconnect = connectedDuringDisconnect || disconnectsInFlight > 0
            return inner.connectAndAwait(device, timeout)
        }

        override suspend fun disconnect() {
            disconnectsInFlight++
            delay(if (disconnects++ == 0) 5.seconds else 1.seconds)
            inner.disconnect()
            disconnectsInFlight--
        }
    }

    @Test
    fun `a connect canceled by closing the sheet finishes letting go before the reopened sheet connects`() =
        runTest(dispatcher) {
            prefs.printerAddress.value = mine.address
            scanner.emitDevice(mine)
            val slow = SlowToLetGo(connection)
            val factory =
                object : BleConnectionFactory {
                    override fun create(scope: CoroutineScope, tag: String): BleConnection = slow
                }
            val viewModel = LabelPrintViewModel(NiimbotPrinterFactory(scanner, factory), prefs)
            viewModel.start()

            viewModel.stop()
            viewModel.start()
            advanceTimeBy(20.seconds)

            assertEquals(2 to false, slow.connects to slow.connectedDuringDisconnect)
            assertEquals(LabelPrinterStatus.READY, viewModel.state.value.status)
        }

    // The second close's disconnect takes 1 s and the first's 5 s, so a connect that waited out only the latest would
    // land while the first still runs, and that one would then drop the new connection.
    @Test
    fun `a reopened sheet waits out every earlier disconnect before it connects`() = runTest(dispatcher) {
        prefs.printerAddress.value = mine.address
        scanner.emitDevice(mine)
        val slow = SlowToLetGo(connection, firstConnectHangs = false)
        val factory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = slow
            }
        val viewModel = LabelPrintViewModel(NiimbotPrinterFactory(scanner, factory), prefs)
        viewModel.start()

        viewModel.stop()
        viewModel.stop()
        viewModel.start()
        advanceTimeBy(20.seconds)

        assertEquals(2 to false, slow.connects to slow.connectedDuringDisconnect)
        assertEquals(LabelPrinterStatus.READY, viewModel.state.value.status)
    }
}
