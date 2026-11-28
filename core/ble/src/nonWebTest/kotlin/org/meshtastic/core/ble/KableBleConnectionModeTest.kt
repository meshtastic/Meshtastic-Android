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
package org.meshtastic.core.ble

import com.juul.kable.Characteristic
import com.juul.kable.Descriptor
import com.juul.kable.DiscoveredService
import com.juul.kable.ExperimentalKableApi
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A peripheral that is connected from the start. */
@OptIn(ExperimentalKableApi::class)
private class ConnectedPeripheral : Peripheral {
    override val scope = CoroutineScope(Job())
    override val state = MutableStateFlow<State>(State.Connected(scope))
    override val identifier: Identifier
        get() = error("not read")

    override val name: String? = null
    override val services = MutableStateFlow<List<DiscoveredService>?>(emptyList())

    override suspend fun connect(): CoroutineScope = scope

    override suspend fun disconnect() {
        state.value = State.Disconnected()
    }

    override suspend fun maximumWriteValueLengthForType(writeType: WriteType): Int = 244

    override suspend fun rssi(): Int = 0

    override suspend fun read(characteristic: Characteristic): ByteArray = ByteArray(0)

    override suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType) = Unit

    override suspend fun read(descriptor: Descriptor): ByteArray = ByteArray(0)

    override suspend fun write(descriptor: Descriptor, data: ByteArray) = Unit

    override fun observe(characteristic: Characteristic, onSubscription: suspend () -> Unit): Flow<ByteArray> =
        emptyFlow()

    override fun close() = scope.cancel()
}

class KableBleConnectionModeTest {
    private val device = MeshtasticBleDevice("AA:BB:CC:DD:EE:FF", name = "D110-TEST")
    private var saved: ActiveConnection? = null

    @BeforeTest
    fun saveActive() {
        saved = ActiveBleConnection.active
    }

    @AfterTest
    fun restoreActive() {
        ActiveBleConnection.active = saved
    }

    // MeshtasticBleDevice.isConnected and the bond receiver read ActiveBleConnection as the radio's link.
    @Test
    fun `a connection to a second device leaves the radio's active connection alone`() = runTest {
        val radio = ActiveConnection(ConnectedPeripheral(), "radio")
        ActiveBleConnection.active = radio
        val printer =
            KableBleConnection(backgroundScope, BleLoggingConfig.Release, tracksActiveConnection = false) { _, _ ->
                ConnectedPeripheral()
            }

        printer.connect(device)
        val whileConnected = ActiveBleConnection.active
        printer.disconnect()

        assertSame(radio, whileConnected)
        assertSame(radio, ActiveBleConnection.active)
    }

    @Test
    fun `a radio connection becomes the active connection`() = runTest {
        val peripheral = ConnectedPeripheral()
        val radio =
            KableBleConnection(backgroundScope, BleLoggingConfig.Release, tracksActiveConnection = true) { _, _ ->
                peripheral
            }

        radio.connect(device)

        assertSame(peripheral, ActiveBleConnection.active?.peripheral)
        assertEquals(device.address, ActiveBleConnection.active?.address)
    }

    @Test
    fun `the factory's auxiliary connections never track as the radio and its own connections do`() = runTest {
        val factory = KableBleConnectionFactory(BleLoggingConfig.Release)

        val modes =
            listOf(factory.createAuxiliary(backgroundScope, "printer"), factory.create(backgroundScope, "radio")).map {
                (it as KableBleConnection).tracksActiveConnection
            }

        assertEquals(listOf(false, true), modes)
    }
}
