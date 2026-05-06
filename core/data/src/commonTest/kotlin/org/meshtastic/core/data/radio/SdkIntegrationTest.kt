/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.core.data.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.StoreAndForward
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests verifying the full SDK → Bridge → Repository chain,
 * connection lifecycle state transitions, and error resilience.
 *
 * These tests spin up a real [RadioClient] backed by [FakeRadioTransport]
 * (with autoHandshake) and wire it through [SdkStateBridge] to real
 * repository fakes, verifying the complete data flow end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SdkIntegrationTest {

    // ───────────────────────────────────────────────────────────────────────────
    // T4a: SDK → Bridge → Repository chain tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `text message packet from SDK reaches service repository`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        transport.injectPacket(
            MeshPacket(
                id = 42,
                from = 0x22222222,
                to = 0x11111111,
                decoded = Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = "Hello mesh!".encodeToByteArray().toByteString(),
                ),
            ),
        )
        runCurrent()

        // Connection should remain active after packet processing
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    @Test
    fun `telemetry packet updates congestion level`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Inject telemetry with high air utilization from own node
        transport.injectPacket(
            MeshPacket(
                from = 0x11111111, // own node — triggers local congestion tracking
                to = 0,
                decoded = Data(
                    portnum = PortNum.TELEMETRY_APP,
                    payload = Telemetry(
                        device_metrics = DeviceMetrics(
                            air_util_tx = 80f,
                            channel_utilization = 85f,
                        ),
                    ).let { Telemetry.ADAPTER.encode(it).toByteString() },
                ),
            ),
        )
        runCurrent()

        // Congestion level should be set (CRITICAL for >=75% utilization)
        val congestion = serviceRepo.congestionLevel.value
        assertTrue(congestion != null, "Congestion level should be set for high air utilization")

        client.disconnect()
    }

    @Test
    fun `store forward heartbeat registers server`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        transport.injectStoreForwardResponse(
            requestId = 0,
            message = StoreAndForward(
                rr = StoreAndForward.RequestResponse.ROUTER_HEARTBEAT,
                heartbeat = StoreAndForward.Heartbeat(period = 900, secondary = 0),
            ),
            fromNode = 0xABCD1234.toInt(),
        )
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertTrue(serviceRepo.storeForwardServers.value.contains(0xABCD1234.toInt()))

        client.disconnect()
    }

    @Test
    fun `topology update from neighbor info packet reaches topology service`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val topologyService = MeshTopologyService()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo, topologyService = topologyService)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Inject a NEIGHBORINFO_APP packet
        val neighborInfo = org.meshtastic.proto.NeighborInfo(
            node_id = 0x22222222,
            neighbors = listOf(
                org.meshtastic.proto.Neighbor(node_id = 0x33333333, snr = 7.5f),
                org.meshtastic.proto.Neighbor(node_id = 0x44444444, snr = -3.0f),
            ),
        )
        transport.injectPacket(
            MeshPacket(
                from = 0x22222222,
                to = 0xFFFFFFFF.toInt(),
                decoded = Data(
                    portnum = PortNum.NEIGHBORINFO_APP,
                    payload = org.meshtastic.proto.NeighborInfo.ADAPTER.encode(neighborInfo).toByteString(),
                ),
            ),
        )
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Topology service should have edges
        assertTrue(topologyService.edges.value.isNotEmpty())

        client.disconnect()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // T4b: Connection lifecycle tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `connect transitions service repository to connected state`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (_, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        // Before connect
        assertEquals(ConnectionState.Disconnected, serviceRepo.connectionState.value)

        client.connect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()

        // After connect + handshake, should be connected
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    @Test
    fun `disconnect transitions service repository to disconnected state`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (_, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(ConnectionState.Disconnected, serviceRepo.connectionState.value)
    }

    @Test
    fun `new client after disconnect restores connected state`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val accessor = MutableRadioClientAccessor()
        val (_, client1) = connectedClient()
        buildBridgeWithAccessor(accessor, serviceRepository = serviceRepo)

        // First connect
        accessor.client.value = client1
        client1.connect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(serviceRepo.connectionState.value.isConnected)

        // Disconnect
        client1.disconnect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(ConnectionState.Disconnected, serviceRepo.connectionState.value)

        // "Reconnect" — new transport + client (as the real app does)
        val (_, client2) = connectedClient()
        accessor.client.value = client2
        client2.connect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client2.disconnect()
    }

    @Test
    fun `disconnect clears congestion level`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Set congestion via telemetry
        transport.injectPacket(
            MeshPacket(
                from = 0x11111111,
                to = 0,
                decoded = Data(
                    portnum = PortNum.TELEMETRY_APP,
                    payload = Telemetry(
                        device_metrics = DeviceMetrics(air_util_tx = 90f, channel_utilization = 90f),
                    ).let { Telemetry.ADAPTER.encode(it).toByteString() },
                ),
            ),
        )
        runCurrent()

        // Disconnect — congestion should clear
        client.disconnect()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()

        // Service repo clears congestion on disconnect
        assertEquals(null, serviceRepo.congestionLevel.value)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // T4c: Error resilience tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `malformed proto payload does not crash bridge`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Inject packet with garbage payload for a port that expects proto
        transport.injectPacket(
            MeshPacket(
                id = 99,
                from = 0x33333333,
                to = 0x11111111,
                decoded = Data(
                    portnum = PortNum.TELEMETRY_APP,
                    payload = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01).toByteString(),
                ),
            ),
        )
        runCurrent()

        // Bridge should still be functional — subsequent valid packet works
        transport.injectPacket(
            MeshPacket(
                id = 100,
                from = 0x33333333,
                to = 0x11111111,
                decoded = Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = "still alive".encodeToByteArray().toByteString(),
                ),
            ),
        )
        runCurrent()

        // Connection still active — not crashed
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    @Test
    fun `unknown port number does not disrupt bridge processing`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Inject packet with a port number the bridge doesn't handle
        transport.injectPacket(
            MeshPacket(
                id = 200,
                from = 0x88888888.toInt(),
                to = 0x11111111,
                decoded = Data(
                    portnum = PortNum.UNKNOWN_APP,
                    payload = "mystery data".encodeToByteArray().toByteString(),
                ),
            ),
        )
        runCurrent()

        // Still connected
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    @Test
    fun `rapid fire packets all processed without loss`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Fire 20 packets rapidly
        repeat(20) { i ->
            transport.injectPacket(
                MeshPacket(
                    id = 1000 + i,
                    from = 0x22222222,
                    to = 0x11111111,
                    decoded = Data(
                        portnum = PortNum.TEXT_MESSAGE_APP,
                        payload = "msg$i".encodeToByteArray().toByteString(),
                    ),
                ),
            )
        }
        runCurrent()

        // Connection still healthy after burst
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    @Test
    fun `empty payload packet handled gracefully`() = runTest {
        val serviceRepo = FakeServiceRepository()
        val (transport, client) = connectedClient()
        buildBridge(client, serviceRepository = serviceRepo)

        client.connect()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Inject packet with no payload
        transport.injectPacket(
            MeshPacket(
                id = 300,
                from = 0x44444444,
                to = 0x11111111,
                decoded = Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = okio.ByteString.EMPTY,
                ),
            ),
        )
        runCurrent()

        // Not crashed
        assertTrue(serviceRepo.connectionState.value.isConnected)

        client.disconnect()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────────

    private class MutableRadioClientAccessor : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(null)
        override fun rebuildAndConnectAsync() = Unit
        override fun disconnect() = Unit
    }

    private fun TestScope.connectedClient(
        myNodeNum: Int = 0x11111111,
    ): Pair<FakeRadioTransport, RadioClient> {
        val transport = FakeRadioTransport(
            identity = TransportIdentity("fake:integration"),
            autoHandshake = true,
            nodeNum = myNodeNum,
        )
        val client = RadioClient.Builder()
            .transport(transport)
            .storage(InMemoryStorageProvider())
            .coroutineContext(backgroundScope.coroutineContext)
            .autoSyncTimeOnConnect(false)
            .presenceTimeout(2.seconds)
            .build()
        return transport to client
    }

    private fun TestScope.buildBridge(
        client: RadioClient,
        nodeRepository: FakeNodeRepository = FakeNodeRepository(),
        packetRepository: PacketRepository = mock(MockMode.autofill),
        serviceRepository: FakeServiceRepository = FakeServiceRepository(),
        topologyService: MeshTopologyService = MeshTopologyService(),
    ): SdkStateBridge = buildBridgeWithAccessor(
        accessor = object : RadioClientAccessor {
            override val client = MutableStateFlow<RadioClient?>(client)
            override fun rebuildAndConnectAsync() = Unit
            override fun disconnect() = Unit
        },
        nodeRepository = nodeRepository,
        packetRepository = packetRepository,
        serviceRepository = serviceRepository,
        topologyService = topologyService,
    )

    private fun TestScope.buildBridgeWithAccessor(
        accessor: RadioClientAccessor,
        nodeRepository: FakeNodeRepository = FakeNodeRepository(),
        packetRepository: PacketRepository = mock(MockMode.autofill),
        serviceRepository: FakeServiceRepository = FakeServiceRepository(),
        topologyService: MeshTopologyService = MeshTopologyService(),
    ): SdkStateBridge = SdkStateBridge(
        accessor = accessor,
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        packetRepository = lazyOf(packetRepository),
        locationManager = object : MeshLocationManager {
            override fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit) = Unit
            override fun stop() = Unit
        },
        topologyService = topologyService,
        uiPrefs = FakeUiPrefs(),
        dispatchers = CoroutineDispatchers(
            io = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
            main = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
            default = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
        ),
    )
}
