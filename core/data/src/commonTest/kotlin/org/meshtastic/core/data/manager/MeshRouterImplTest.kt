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
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.XModemManager
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshRouterImplTest {
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
    private val tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill)
    private val neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill)
    private val configFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)
    private val mqttManager = mock<MqttManager>(MockMode.autofill)
    private val actionHandler = mock<MeshActionHandler>(MockMode.autofill)
    private val xmodemManager = mock<XModemManager>(MockMode.autofill)

    private val configHandler =
        object : MeshConfigHandler {
            override val localConfig = MutableStateFlow(LocalConfig())
            override val moduleConfig = MutableStateFlow(LocalModuleConfig())

            override fun handleDeviceConfig(config: org.meshtastic.proto.Config) = Unit

            override fun handleModuleConfig(config: org.meshtastic.proto.ModuleConfig) = Unit

            override fun handleChannel(channel: org.meshtastic.proto.Channel) = Unit

            override fun handleDeviceUIConfig(config: org.meshtastic.proto.DeviceUIConfig) = Unit
        }

    private lateinit var dataHandlerLazy: TrackingLazy<MeshDataHandler>
    private lateinit var configHandlerLazy: TrackingLazy<MeshConfigHandler>
    private lateinit var tracerouteHandlerLazy: TrackingLazy<TracerouteHandler>
    private lateinit var neighborInfoHandlerLazy: TrackingLazy<NeighborInfoHandler>
    private lateinit var configFlowManagerLazy: TrackingLazy<MeshConfigFlowManager>
    private lateinit var mqttManagerLazy: TrackingLazy<MqttManager>
    private lateinit var actionHandlerLazy: TrackingLazy<MeshActionHandler>
    private lateinit var xmodemManagerLazy: TrackingLazy<XModemManager>

    private lateinit var router: MeshRouterImpl

    @BeforeTest
    fun setUp() {
        dataHandlerLazy = TrackingLazy { dataHandler }
        configHandlerLazy = TrackingLazy { configHandler }
        tracerouteHandlerLazy = TrackingLazy { tracerouteHandler }
        neighborInfoHandlerLazy = TrackingLazy { neighborInfoHandler }
        configFlowManagerLazy = TrackingLazy { configFlowManager }
        mqttManagerLazy = TrackingLazy { mqttManager }
        actionHandlerLazy = TrackingLazy { actionHandler }
        xmodemManagerLazy = TrackingLazy { xmodemManager }

        router =
            MeshRouterImpl(
                dataHandlerLazy = dataHandlerLazy,
                configHandlerLazy = configHandlerLazy,
                tracerouteHandlerLazy = tracerouteHandlerLazy,
                neighborInfoHandlerLazy = neighborInfoHandlerLazy,
                configFlowManagerLazy = configFlowManagerLazy,
                mqttManagerLazy = mqttManagerLazy,
                actionHandlerLazy = actionHandlerLazy,
                xmodemManagerLazy = xmodemManagerLazy,
            )
    }

    @Test
    fun `send message routing uses the action handler lazily`() {
        val packet = DataPacket(to = "!deadbeef", dataType = 1, bytes = null, channel = 0)

        assertAllHandlersUninitialized()

        router.actionHandler.handleSend(packet, 12345)

        assertTrue(actionHandlerLazy.isInitialized())
        assertFalse(dataHandlerLazy.isInitialized())
        assertFalse(tracerouteHandlerLazy.isInitialized())
        verify { actionHandler.handleSend(packet, 12345) }
    }

    @Test
    fun `request position routing uses the action handler lazily`() {
        val position = Position(latitude = 37.7749, longitude = -122.4194, altitude = 10)

        router.actionHandler.handleRequestPosition(destNum = 67890, position = position, myNodeNum = 12345)

        assertTrue(actionHandlerLazy.isInitialized())
        assertFalse(tracerouteHandlerLazy.isInitialized())
        verify { actionHandler.handleRequestPosition(67890, position, 12345) }
    }

    @Test
    fun `traceroute routing uses the traceroute handler lazily`() {
        assertAllHandlersUninitialized()

        router.tracerouteHandler.recordStartTime(77)

        assertTrue(tracerouteHandlerLazy.isInitialized())
        assertFalse(actionHandlerLazy.isInitialized())
        verify { tracerouteHandler.recordStartTime(77) }
    }

    @Test
    fun `admin command routing uses the action handler lazily`() {
        assertAllHandlersUninitialized()

        router.actionHandler.handleGetRemoteConfig(id = 42, destNum = 67890, config = 7)

        assertTrue(actionHandlerLazy.isInitialized())
        assertFalse(configHandlerLazy.isInitialized())
        verify { actionHandler.handleGetRemoteConfig(42, 67890, 7) }
    }

    @Test
    fun `service actions are passed through unchanged to the action handler`() = runTest {
        val action = ServiceAction.Favorite(Node(num = 67890))

        router.actionHandler.onServiceAction(action)

        assertTrue(actionHandlerLazy.isInitialized())
        assertFalse(dataHandlerLazy.isInitialized())
        assertFalse(tracerouteHandlerLazy.isInitialized())
        verifySuspend { actionHandler.onServiceAction(action) }
    }

    private fun assertAllHandlersUninitialized() {
        assertFalse(dataHandlerLazy.isInitialized())
        assertFalse(configHandlerLazy.isInitialized())
        assertFalse(tracerouteHandlerLazy.isInitialized())
        assertFalse(neighborInfoHandlerLazy.isInitialized())
        assertFalse(configFlowManagerLazy.isInitialized())
        assertFalse(mqttManagerLazy.isInitialized())
        assertFalse(actionHandlerLazy.isInitialized())
        assertFalse(xmodemManagerLazy.isInitialized())
    }

    private class TrackingLazy<T>(private val initializer: () -> T) : Lazy<T> {
        private var cached: Any? = Uninitialized

        override val value: T
            get() {
                if (cached === Uninitialized) {
                    cached = initializer()
                }

                @Suppress("UNCHECKED_CAST")
                return cached as T
            }

        override fun isInitialized(): Boolean = cached !== Uninitialized

        private object Uninitialized
    }
}
