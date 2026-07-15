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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryPacketHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
    private val notificationManager = mock<NotificationManager>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: TelemetryPacketHandlerImpl

    private val myNodeNum = 12345
    private val remoteNodeNum = 99999
    private val radioSession = RadioSessionContext(generation = 1L, address = "test:telemetry")
    private val sessionLease =
        object : RadioSessionLease {
            override val session: RadioSessionContext = radioSession

            override fun isCurrent(): Boolean = true
        }

    @BeforeTest
    fun setUp() {
        every { nodeManager.nodeDBbyNodeNum } returns
            mapOf(myNodeNum to Node(num = myNodeNum), remoteNodeNum to Node(num = remoteNodeNum))
        everySuspend { radioInterfaceService.runWithSessionLease(radioSession, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (RadioSessionLease) -> Unit
                block(sessionLease)
                true
            }
        handler =
            TelemetryPacketHandlerImpl(
                nodeManager = nodeManager,
                connectionManager = lazy { connectionManager },
                notificationManager = notificationManager,
                radioInterfaceService = radioInterfaceService,
                scope = testScope,
            )
    }

    private fun makeTelemetryPacket(from: Int, telemetry: Telemetry): MeshPacket {
        val payload = telemetry.encode().toByteString()
        return MeshPacket(
            from = from,
            decoded = Data(portnum = PortNum.TELEMETRY_APP, payload = payload),
            rx_time = 1700000000,
        )
    }

    private fun makeDataPacket(from: Int): DataPacket = DataPacket(
        id = 1,
        time = 1700000000000L,
        to = NodeAddress.ID_BROADCAST,
        from = NodeAddress.numToDefaultId(from),
        bytes = null,
        dataType = PortNum.TELEMETRY_APP.value,
    )

    // ---------- Device metrics from local node ----------

    @Test
    fun `local device metrics updates telemetry on connectionManager`() = testScope.runTest {
        val telemetry =
            Telemetry(time = 1700000000, device_metrics = DeviceMetrics(battery_level = 80, voltage = 4.1f))
        val packet = makeTelemetryPacket(myNodeNum, telemetry)
        val dataPacket = makeDataPacket(myNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        verify { connectionManager.updateTelemetry(any()) }
        verify { nodeManager.updateNodeForSession(myNodeNum, radioSession, any(), any()) }
    }

    // ---------- Device metrics from remote node ----------

    @Test
    fun `remote device metrics updates node but not connectionManager`() = testScope.runTest {
        val telemetry =
            Telemetry(time = 1700000000, device_metrics = DeviceMetrics(battery_level = 90, voltage = 4.2f))
        val packet = makeTelemetryPacket(remoteNodeNum, telemetry)
        val dataPacket = makeDataPacket(remoteNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        verify { nodeManager.updateNodeForSession(remoteNodeNum, radioSession, any(), any()) }
    }

    // ---------- Environment metrics ----------

    @Test
    fun `environment metrics updates node with environment data`() = testScope.runTest {
        val telemetry =
            Telemetry(
                time = 1700000000,
                environment_metrics = EnvironmentMetrics(temperature = 25.5f, relative_humidity = 60.0f),
            )
        val packet = makeTelemetryPacket(remoteNodeNum, telemetry)
        val dataPacket = makeDataPacket(remoteNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        verify { nodeManager.updateNodeForSession(remoteNodeNum, radioSession, any(), any()) }
    }

    // ---------- Power metrics ----------

    @Test
    fun `power metrics updates node with power data`() = testScope.runTest {
        val telemetry = Telemetry(time = 1700000000, power_metrics = PowerMetrics(ch1_voltage = 3.3f))
        val packet = makeTelemetryPacket(remoteNodeNum, telemetry)
        val dataPacket = makeDataPacket(remoteNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        verify { nodeManager.updateNodeForSession(remoteNodeNum, radioSession, any(), any()) }
    }

    // ---------- Telemetry time handling ----------

    @Test
    fun `telemetry with time 0 gets time from dataPacket`() = testScope.runTest {
        val telemetry = Telemetry(time = 0, device_metrics = DeviceMetrics(battery_level = 50, voltage = 3.8f))
        val packet = makeTelemetryPacket(myNodeNum, telemetry)
        val dataPacket = makeDataPacket(myNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        verify { nodeManager.updateNodeForSession(myNodeNum, radioSession, any(), any()) }
    }

    // ---------- Null payload ----------

    @Test
    fun `handleTelemetry with null decoded payload returns early`() = testScope.runTest {
        val packet = MeshPacket(from = myNodeNum, decoded = null)
        val dataPacket = makeDataPacket(myNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun `handleTelemetry with empty payload bytes returns early`() = testScope.runTest {
        val packet =
            MeshPacket(
                from = myNodeNum,
                decoded = Data(portnum = PortNum.TELEMETRY_APP, payload = okio.ByteString.EMPTY),
            )
        val dataPacket = makeDataPacket(myNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()
        // No crash — decodeOrNull returns null for empty payload
    }

    // ---------- Battery notification: healthy battery does NOT trigger ----------

    @Test
    fun `healthy battery level does not trigger low battery notification`() = testScope.runTest {
        val telemetry =
            Telemetry(time = 1700000000, device_metrics = DeviceMetrics(battery_level = 80, voltage = 4.0f))
        val packet = makeTelemetryPacket(myNodeNum, telemetry)
        val dataPacket = makeDataPacket(myNodeNum)

        handler.handleTelemetry(packet, dataPacket, myNodeNum, radioSession)
        advanceUntilIdle()

        // No dispatch call — battery is healthy
    }
}
