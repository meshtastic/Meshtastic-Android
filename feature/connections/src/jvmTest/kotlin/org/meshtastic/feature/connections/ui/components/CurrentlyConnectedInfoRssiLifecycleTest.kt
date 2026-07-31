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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.model.Node
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CurrentlyConnectedInfoRssiLifecycleTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pollingFollowsLifecycleAndConnectionState() = runComposeUiTest {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)
        val device = RecordingBleDevice()

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                MaterialTheme {
                    CurrentlyConnectedInfo(
                        node = Node(num = 1, user = User(long_name = "Test Node", short_name = "TST")),
                        text =
                        CurrentlyConnectedText(
                            unknownLabel = "Unknown",
                            rssiLabel = "RSSI",
                            disconnectLabel = "Disconnect",
                            firmwareVersion = null,
                        ),
                        onNavigateToNodeDetails = {},
                        onClickDisconnect = {},
                        bleDevice = DeviceListEntry.Ble(device),
                    )
                }
            }
        }

        waitUntil { device.readRssiCalls >= 1 }
        val firstReadCount = device.readRssiCalls

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        waitForIdle()
        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertEquals(firstReadCount, device.readRssiCalls, "RSSI reads must stop below STARTED")

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitUntil { device.readRssiCalls > firstReadCount }

        device.setState(BleConnectionState.Disconnected())
        waitForIdle()
        val disconnectedReadCount = device.readRssiCalls
        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertEquals(disconnectedReadCount, device.readRssiCalls, "disconnected devices must not be polled")

        device.setState(BleConnectionState.Connected)
        waitUntil { device.readRssiCalls > disconnectedReadCount }
        assertTrue(
            device.readRssiCalls > disconnectedReadCount,
            "reconnect must resume RSSI reads without a timer poll",
        )
    }

    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

        fun moveTo(state: Lifecycle.State) {
            lifecycle.currentState = state
        }
    }

    private class RecordingBleDevice : BleDevice {
        private val mutableState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
        override val state: StateFlow<BleConnectionState> = mutableState
        override val address: String = "00:00:00:00:00:01"
        override val name: String = "Test"
        override val isBonded: Boolean = true
        override val isConnected: Boolean
            get() = state.value == BleConnectionState.Connected

        override val rssi: Int = -60
        var readRssiCalls: Int = 0
            private set

        override suspend fun readRssi(): Int {
            readRssiCalls += 1
            return rssi
        }

        override suspend fun bond() = Unit

        fun setState(newState: BleConnectionState) {
            mutableState.value = newState
        }
    }
}
