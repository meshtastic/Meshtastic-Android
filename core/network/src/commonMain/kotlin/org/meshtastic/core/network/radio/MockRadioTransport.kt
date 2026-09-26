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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import org.meshtastic.proto.Channel as ProtoChannel
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

private val defaultLoRaConfig =
    Config.LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = true
            wb.modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST
            wb.region = Config.LoRaConfig.RegionCode.US
            wb.hop_limit = 3
            wb.tx_enabled = true
        }
        .build()

private val defaultChannel =
    ProtoChannel.Builder()
        .also { wb ->
            wb.settings = Channel.default.settings
            wb.role = ProtoChannel.Role.PRIMARY
        }
        .build()

/**
 * A simulated radio, selected as "Demo Mode" in the Connections screen.
 *
 * It answers the app's real two-stage `want_config` handshake and then feeds a small synthetic mesh — a populated node
 * list with positions, signal readings and battery levels, one channel conversation, one direct-message thread, and
 * telemetry that keeps accruing while the app is open. That makes the app fully explorable without any LoRa hardware,
 * which is what an app-store reviewer and a first-time user both need.
 *
 * Three properties of the real protocol have to be honoured or the app never leaves "Loading node list":
 * 1. The handshake is two-stage ([HandshakeConstants.CONFIG_NONCE] then [HandshakeConstants.NODE_INFO_NONCE]). Each
 *    stage must answer with only its own frames and its own `config_complete_id`. Re-sending `my_info` on stage 2
 *    resets the app's handshake state machine and the stage-2 completion is then rejected.
 * 2. `node_info` frames are only accepted inside the handshake window, so every simulated node has to ship in stage 2.
 * 3. Radio metrics (SNR/RSSI/hops) are only taken from packets that look like direct LoRa receptions —
 *    `transport_mechanism = TRANSPORT_LORA` and `hop_start == hop_limit`. Frames left at the proto defaults are treated
 *    as internal traffic and contribute no signal information at all.
 */
@Suppress("detekt:TooManyFunctions", "detekt:MagicNumber")
class MockRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    val address: String,
) : RadioTransport {

    /** The mesh this transport plays, picked by the address suffix; see [MockScenario.forAddress]. */
    private val scenario = MockScenario.forAddress(address)

    /**
     * Hands out packet ids.
     *
     * Atomic because ids are drawn concurrently from the seed pass, the live-traffic ticker, the delayed reply jobs and
     * the ack path. Two frames sharing an id is not cosmetic here: the app keys its message and node lists by packet
     * id, and a duplicate key has crashed those lists before — exactly the screens a store reviewer is looking at while
     * the demo runs.
     */
    private val packetIdCounter = atomic(FIRST_PACKET_ID)

    /** Guards against re-seeding traffic if the app repeats stage 2 (e.g. after a handshake retry). */
    private val trafficStarted = atomic(false)

    private val lifecycle = TransportLifecycleGate("Mock")
    private val transportJob = SupervisorJob(scope.coroutineContext[Job])
    private val transportScope = CoroutineScope(scope.coroutineContext + transportJob)

    private fun nextPacketId(): Int = packetIdCounter.getAndIncrement()

    override fun start() {
        lifecycle.runIfOpen {
            Logger.i { "Starting the mock transport" }
            callback.onConnect() // Tell clients they can use the API
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean = lifecycle.runIfOpen {
        val pr = runCatching { ToRadio.ADAPTER.decode(p) }.getOrNull()
        if (pr == null) {
            Logger.w { "Ignoring undecodable ToRadio sent to mock transport (${p.size} bytes)" }
            return@runIfOpen false
        }

        // Intercept the want_config handshake. Real firmware answers each stage separately and only when asked,
        // and the app's state machine depends on that: see the class doc.
        when (pr.want_config_id) {
            null,
            0,
            -> handleOutboundTraffic(pr)

            HandshakeConstants.CONFIG_NONCE -> sendConfigStage()

            HandshakeConstants.NODE_INFO_NONCE -> sendNodeInfoStage()

            else -> Logger.w { "Mock transport ignoring unknown want_config_id ${pr.want_config_id}" }
        }
        true
    } ?: false

    private fun handleOutboundTraffic(pr: ToRadio) {
        val packet = pr.packet
        if (packet != null) {
            sendQueueStatus(packet.id)
        }

        val data = packet?.decoded

        when {
            data != null && data.portnum == PortNum.ADMIN_APP -> {
                val admin = runCatching { AdminMessage.ADAPTER.decode(data.payload) }.getOrNull()
                if (admin == null) {
                    Logger.w { "Ignoring undecodable AdminMessage sent to mock transport" }
                } else {
                    handleAdminPacket(pr, admin)
                }
            }

            data != null && data.portnum == PortNum.TEXT_MESSAGE_APP -> {
                if (packet?.want_ack == true) sendFakeAck(pr)
                sendSimulatedReply(packet)
            }

            packet != null && packet.want_ack == true -> sendFakeAck(pr)

            else -> Logger.i { "Ignoring data sent to mock transport $pr" }
        }
    }

    private fun handleAdminPacket(pr: ToRadio, d: AdminMessage) {
        val packet = pr.packet ?: return
        when {
            d.get_config_request == AdminMessage.ConfigType.LORA_CONFIG ->
                sendAdmin(packet.to, packet.from, packet.id) {
                    this.newBuilder()
                        .also { wb ->
                            wb.get_config_response = Config.Builder().also { wb -> wb.lora = defaultLoRaConfig }.build()
                        }
                        .build()
                }

            (d.get_channel_request ?: 0) != 0 ->
                sendAdmin(packet.to, packet.from, packet.id) {
                    this.newBuilder()
                        .also { wb ->
                            wb.get_channel_response =
                                ProtoChannel.Builder()
                                    .also { wb ->
                                        wb.index = (d.get_channel_request ?: 0) - 1 // 0 based on the response
                                        wb.settings = if (d.get_channel_request == 1) Channel.default.settings else null
                                        wb.role =
                                            if (d.get_channel_request == 1) {
                                                ProtoChannel.Role.PRIMARY
                                            } else {
                                                ProtoChannel.Role.DISABLED
                                            }
                                    }
                                    .build()
                        }
                        .build()
                }

            d.get_module_config_request == AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG ->
                sendAdmin(packet.to, packet.from, packet.id) {
                    this.newBuilder()
                        .also { wb ->
                            wb.get_module_config_response =
                                ModuleConfig.Builder()
                                    .also { wb ->
                                        wb.statusmessage =
                                            ModuleConfig.StatusMessageConfig.Builder()
                                                .also { wb -> wb.node_status = scenario.nodeStatus }
                                                .build()
                                    }
                                    .build()
                        }
                        .build()
                }

            else -> Logger.i { "Ignoring admin sent to mock transport $d" }
        }
    }

    override suspend fun close() {
        val completed =
            lifecycle.close(
                beforeDrain = { transportJob.cancelAndJoin() },
                teardown = { Logger.i { "Closing the mock transport" } },
            )
        if (!completed) Logger.w { "Mock transport teardown did not complete within its lifecycle bounds" }
    }

    // ── Handshake ─────────────────────────────────────────────────────────────────────────────

    /** Stage 1: our own identity, device metadata, config and channels. Deliberately carries no `node_info`. */
    private fun sendConfigStage() {
        Logger.d { "Mock transport answering config stage" }
        val metadata =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.firmware_version = scenario.firmwareVersion
                    wb.hw_model = scenario.hwModel
                }
                .build()
        val frames =
            listOf(
                FromRadio.Builder()
                    .also { wb ->
                        wb.my_info =
                            ProtoMyNodeInfo.Builder()
                                .also { wb ->
                                    wb.my_node_num = scenario.myNode
                                    wb.reboot_count = 3
                                }
                                .build()
                    }
                    .build(),
                FromRadio.Builder().also { wb -> wb.metadata = metadata }.build(),
                FromRadio.Builder()
                    .also { wb -> wb.config = Config.Builder().also { wb -> wb.lora = defaultLoRaConfig }.build() }
                    .build(),
                FromRadio.Builder()
                    .also { wb ->
                        wb.config =
                            Config.Builder()
                                .also { wb ->
                                    wb.device =
                                        Config.DeviceConfig.Builder()
                                            .also { wb -> wb.role = Config.DeviceConfig.Role.CLIENT }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
                FromRadio.Builder()
                    .also { wb ->
                        wb.config =
                            Config.Builder()
                                .also { wb ->
                                    wb.position =
                                        Config.PositionConfig.Builder()
                                            .also { wb ->
                                                wb.position_broadcast_secs = 900
                                                wb.position_broadcast_smart_enabled = true
                                                wb.gps_enabled = true
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
                FromRadio.Builder().also { wb -> wb.channel = defaultChannel }.build(),
                FromRadio.Builder().also { wb -> wb.config_complete_id = HandshakeConstants.CONFIG_NONCE }.build(),
            )
        frames.forEach { callback.handleFromRadio(it.encode()) }
    }

    /** Stage 2: the node database. This is the only window in which the app accepts `node_info`. */
    private fun sendNodeInfoStage() {
        Logger.d { "Mock transport answering node-info stage with ${scenario.peers.size + 1} nodes" }
        callback.handleFromRadio(FromRadio.Builder().also { wb -> wb.node_info = localNodeInfo() }.build().encode())
        scenario.peers.forEach { peer ->
            callback.handleFromRadio(
                FromRadio.Builder().also { wb -> wb.node_info = peer.toNodeInfo() }.build().encode(),
            )
        }
        callback.handleFromRadio(
            FromRadio.Builder()
                .also { wb -> wb.config_complete_id = HandshakeConstants.NODE_INFO_NONCE }
                .build()
                .encode(),
        )

        if (trafficStarted.compareAndSet(expect = false, update = true)) {
            transportScope.handledLaunch { seedTraffic() }
        }
    }

    private fun localNodeInfo() = NodeInfo.Builder()
        .also { wb ->
            wb.num = scenario.myNode
            wb.last_heard = nowSeconds.toInt()
            wb.user =
                User.Builder()
                    .also { wb ->
                        wb.id = NodeAddress.numToDefaultId(scenario.myNode)
                        wb.long_name = scenario.longName
                        wb.short_name = scenario.shortName
                        wb.hw_model = scenario.hwModel
                        wb.role = Config.DeviceConfig.Role.CLIENT
                    }
                    .build()
            wb.position = scenario.position.toProto()
            wb.device_metrics =
                DeviceMetrics.Builder()
                    .also { wb ->
                        wb.battery_level = scenario.myBatteryLevel
                        wb.voltage = scenario.myVoltage
                        wb.channel_utilization = 8.4f
                        wb.air_util_tx = 1.9f
                        wb.uptime_seconds = 7_240
                    }
                    .build()
            wb.hops_away = 0
        }
        .build()

    private fun SimPeer.toNodeInfo() = NodeInfo.Builder()
        .also { wb ->
            wb.num = num
            // Without last_heard every simulated node reads as offline and disappears the moment the user turns on
            // the
            // node list's "online only" filter.
            wb.last_heard = nowSeconds.toInt() - secondsSinceHeard
            wb.user =
                User.Builder()
                    .also { wb ->
                        wb.id = NodeAddress.numToDefaultId(num)
                        wb.long_name = longName
                        wb.short_name = shortName
                        wb.hw_model = hwModel
                        wb.role = role
                        publicKey?.let { wb.public_key = it.decodeHex() }
                    }
                    .build()
            wb.position = SimPosition(latitude, longitude, altitude).toProto()
            wb.device_metrics =
                DeviceMetrics.Builder()
                    .also { wb ->
                        wb.battery_level = batteryLevel
                        wb.voltage = voltage
                        wb.channel_utilization = channelUtilization
                        wb.air_util_tx = airUtilTx
                        wb.uptime_seconds = uptimeSeconds
                    }
                    .build()
            wb.snr = snr
            wb.hops_away = hops
            wb.channel = 0
            wb.is_favorite = favorite
        }
        .build()

    // ── Simulated traffic ─────────────────────────────────────────────────────────────────────

    /**
     * Seeds a mesh that already has history, then keeps it gently alive.
     *
     * The seed pass is spaced out because message and telemetry rows are timestamped when the app persists them, not
     * from the payload: a burst emitted inside the same millisecond collapses into one indistinguishable clump of chart
     * points and messages.
     */
    private suspend fun seedTraffic() {
        scenario.peers.forEach { peer ->
            lifecycle.runIfOpen { callback.handleFromRadio(peer.positionPacket(nextPacketId()).encode()) }
            delay(SEED_SPACING_MS)
        }

        scenario.peers.take(scenario.telemetryPeerCount).forEach { peer ->
            lifecycle.runIfOpen {
                callback.handleFromRadio(peer.deviceTelemetryPacket(nextPacketId(), tick = 0).encode())
            }
            delay(SEED_SPACING_MS)
        }

        scenario.peers.forEach { peer ->
            val environment = peer.environment ?: return@forEach
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    peer.environmentTelemetryPacket(nextPacketId(), environment, tick = 0).encode(),
                )
            }
            delay(SEED_SPACING_MS)
        }

        lifecycle.runIfOpen { callback.handleFromRadio(scenario.peers[0].neighborInfoPacket(nextPacketId()).encode()) }
        delay(SEED_SPACING_MS)
        scenario.peers.forEach { peer ->
            val status = peer.status ?: return@forEach
            lifecycle.runIfOpen { callback.handleFromRadio(peer.nodeStatusPacket(nextPacketId(), status).encode()) }
            delay(SEED_SPACING_MS)
        }

        // Each message is stamped progressively closer to now, so the thread reads as a conversation that unfolded over
        // the last while rather than a block of messages that all arrived in the same second.
        scenario.channelConversation.forEachIndexed { index, (peerIndex, text) ->
            val peer = scenario.peers[peerIndex]
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    peer
                        .textPacket(
                            id = nextPacketId(),
                            to = BROADCAST_ADDR,
                            text = text,
                            ageSeconds = messageAgeSeconds(scenario.channelConversation.size, index),
                        )
                        .encode(),
                )
            }
            delay(SEED_SPACING_MS)
        }

        scenario.directConversation.forEachIndexed { index, text ->
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    scenario.peers[scenario.directPeerIndex]
                        .textPacket(
                            id = nextPacketId(),
                            to = scenario.myNode,
                            text = text,
                            ageSeconds = messageAgeSeconds(scenario.directConversation.size, index),
                        )
                        .encode(),
                )
            }
            delay(SEED_SPACING_MS)
        }

        if (scenario.liveTelemetry) streamLiveTelemetry()
    }

    /** How long ago the message at [index] of a [count]-message seeded thread was "received". Oldest first. */
    private fun messageAgeSeconds(count: Int, index: Int) = (count - index) * MESSAGE_SPACING_SECONDS

    /**
     * Keeps the demo mesh breathing: one peer reports in per tick, so the telemetry charts gain points with distinct
     * timestamps and the node list's "last heard" values stay fresh while the reviewer explores.
     */
    private suspend fun streamLiveTelemetry() {
        var tick = 1
        while (true) {
            delay(LIVE_TICK_MS)
            val peer = scenario.peers[tick % scenario.telemetryPeerCount]
            lifecycle.runIfOpen { callback.handleFromRadio(peer.deviceTelemetryPacket(nextPacketId(), tick).encode()) }
            val weatherPeer = scenario.peers.firstOrNull { it.environment != null }
            val environment = weatherPeer?.environment
            if (tick % WEATHER_TICK_INTERVAL == 0 && environment != null) {
                lifecycle.runIfOpen {
                    callback.handleFromRadio(
                        weatherPeer.environmentTelemetryPacket(nextPacketId(), environment, tick).encode(),
                    )
                }
            }
            tick++
        }
    }

    /**
     * Answers a text the user just sent, so the demo has a two-way conversation rather than a wall of inbound messages.
     * A broadcast gets a reply on the same channel; a direct message gets a direct reply from its addressee.
     */
    private fun sendSimulatedReply(packet: MeshPacket?) {
        if (packet == null) return
        val isBroadcast = packet.to == BROADCAST_ADDR
        val responder =
            if (isBroadcast) {
                scenario.peers[scenario.directPeerIndex]
            } else {
                scenario.peers.firstOrNull { it.num == packet.to } ?: return
            }
        val replyTo = if (isBroadcast) BROADCAST_ADDR else scenario.myNode

        transportScope.handledLaunch {
            delay(REPLY_DELAY_MS)
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    responder
                        .textPacket(id = nextPacketId(), to = replyTo, text = AUTO_REPLY_TEXT, ageSeconds = 0)
                        .encode(),
                )
            }
        }
    }

    // ── Packet builders ──────────────────────────────────────────────────────────────────────

    /**
     * Base envelope for a packet "received over the air" from [SimPeer].
     *
     * `transport_mechanism` and the matching `hop_start`/`hop_limit` are load-bearing, not decoration: the app only
     * harvests SNR, RSSI and hop count from packets that pass its direct-LoRa test.
     */
    private fun SimPeer.packet(id: Int, to: Int, ageSeconds: Int, data: Data) = MeshPacket.Builder()
        .also { wb ->
            wb.id = id
            wb.from = num
            wb.to = to
            wb.channel = 0
            wb.rx_time = (nowSeconds - ageSeconds).toInt()
            wb.rx_snr = snr
            wb.rx_rssi = rssi
            wb.hop_start = DEFAULT_HOP_START
            wb.hop_limit = DEFAULT_HOP_START - hops
            wb.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            wb.decoded = data
        }
        .build()

    private fun SimPeer.textPacket(id: Int, to: Int, text: String, ageSeconds: Int) = FromRadio.Builder()
        .also { wb ->
            wb.packet =
                packet(
                    id = id,
                    to = to,
                    ageSeconds = ageSeconds,
                    data =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.TEXT_MESSAGE_APP
                            wb.payload = text.encodeUtf8()
                        }
                        .build(),
                )
        }
        .build()

    private fun SimPeer.positionPacket(id: Int) = FromRadio.Builder()
        .also { wb ->
            wb.packet =
                packet(
                    id = id,
                    to = BROADCAST_ADDR,
                    ageSeconds = 0,
                    data =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.POSITION_APP
                            wb.payload =
                                SimPosition(latitude, longitude, altitude).toProto().encode().toByteString()
                        }
                        .build(),
                )
        }
        .build()

    /**
     * Device telemetry for [tick], drifted so the charts show a trend rather than a flat line.
     *
     * Both the percentage and the voltage are clamped, not just the percentage: a tick is 20s, so an unbounded slope
     * takes the voltage through 0V inside a couple of hours and the battery and telemetry views then render a cell that
     * cannot physically exist.
     */
    private fun SimPeer.deviceTelemetryPacket(id: Int, tick: Int): FromRadio {
        // Above 100 is no battery or charging, neither of which drains.
        val powered = batteryLevel > MAX_BATTERY_PERCENT
        val driftedBattery =
            if (powered) batteryLevel else (batteryLevel - tick).coerceIn(MIN_BATTERY_PERCENT, MAX_BATTERY_PERCENT)
        val driftedVoltage =
            if (powered) {
                voltage
            } else {
                voltage?.let { (it - tick * VOLTAGE_DRIFT_PER_TICK).coerceAtLeast(MIN_CELL_VOLTAGE) }
            }
        return FromRadio.Builder()
            .also { wb ->
                wb.packet =
                    packet(
                        id = id,
                        to = BROADCAST_ADDR,
                        ageSeconds = 0,
                        data =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.TELEMETRY_APP
                                wb.payload =
                                    Telemetry.Builder()
                                        .also { wb ->
                                            wb.device_metrics =
                                                DeviceMetrics.Builder()
                                                    .also { wb ->
                                                        wb.battery_level = driftedBattery
                                                        wb.voltage = driftedVoltage
                                                        wb.channel_utilization =
                                                            (channelUtilization ?: 6f) + (tick % 5) * 1.5f
                                                        wb.air_util_tx = (airUtilTx ?: 1.2f) + (tick % 4) * 0.4f
                                                        wb.uptime_seconds =
                                                            uptimeSeconds + tick * (LIVE_TICK_MS / 1000).toInt()
                                                    }
                                                    .build()
                                        }
                                        .build()
                                        .encode()
                                        .toByteString()
                            }
                            .build(),
                    )
            }
            .build()
    }

    private fun SimPeer.environmentTelemetryPacket(id: Int, environment: SimEnvironment, tick: Int) =
        FromRadio.Builder()
            .also { wb ->
                wb.packet =
                    packet(
                        id = id,
                        to = BROADCAST_ADDR,
                        ageSeconds = 0,
                        data =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.TELEMETRY_APP
                                wb.payload =
                                    Telemetry.Builder()
                                        .also { wb ->
                                            wb.environment_metrics =
                                                EnvironmentMetrics.Builder()
                                                    .also { wb ->
                                                        // Temperature AND humidity must both be present or the
                                                        // Environment tab
                                                        // stays empty.
                                                        wb.temperature = environment.temperature + (tick % 7) * 0.4f
                                                        wb.relative_humidity =
                                                            environment.relativeHumidity + (tick % 5) * 1.5f
                                                        wb.barometric_pressure =
                                                            environment.barometricPressure + (tick % 3) * 0.3f
                                                        wb.iaq = environment.iaq
                                                        wb.voltage = environment.voltage
                                                        wb.current = environment.current
                                                    }
                                                    .build()
                                        }
                                        .build()
                                        .encode()
                                        .toByteString()
                            }
                            .build(),
                    )
            }
            .build()

    private fun SimPeer.neighborInfoPacket(id: Int) = FromRadio.Builder()
        .also { wb ->
            wb.packet =
                packet(
                    id = id,
                    to = BROADCAST_ADDR,
                    ageSeconds = 0,
                    data =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.NEIGHBORINFO_APP
                            wb.payload =
                                NeighborInfo.Builder()
                                    .also { wb ->
                                        wb.node_id = num
                                        wb.last_sent_by_id = num
                                        wb.node_broadcast_interval_secs = 900
                                        wb.neighbors =
                                            scenario.peers.drop(1).take(3).map { neighbor ->
                                                Neighbor.Builder()
                                                    .also { wb ->
                                                        wb.node_id = neighbor.num
                                                        wb.snr = neighbor.snr
                                                        wb.last_rx_time = nowSeconds.toInt()
                                                        wb.node_broadcast_interval_secs = 900
                                                    }
                                                    .build()
                                            }
                                    }
                                    .build()
                                    .encode()
                                    .toByteString()
                        }
                        .build(),
                )
        }
        .build()

    private fun SimPeer.nodeStatusPacket(id: Int, status: String) = FromRadio.Builder()
        .also { wb ->
            wb.packet =
                packet(
                    id = id,
                    to = BROADCAST_ADDR,
                    ageSeconds = 0,
                    data =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.NODE_STATUS_APP
                            wb.payload =
                                StatusMessage.Builder()
                                    .also { wb -> wb.status = status }
                                    .build()
                                    .encode()
                                    .toByteString()
                        }
                        .build(),
                )
        }
        .build()

    private fun makeDataPacket(fromIn: Int, toIn: Int, data: Data) = FromRadio.Builder()
        .also { wb ->
            wb.packet =
                MeshPacket.Builder()
                    .also { wb ->
                        wb.id = nextPacketId()
                        wb.from = fromIn
                        wb.to = toIn
                        wb.rx_time = nowSeconds.toInt()
                        wb.rx_snr = 1.5f
                        wb.decoded = data
                    }
                    .build()
        }
        .build()

    private fun makeAck(fromIn: Int, toIn: Int, msgId: Int) = makeDataPacket(
        fromIn,
        toIn,
        Data.Builder()
            .also { wb ->
                wb.portnum = PortNum.ROUTING_APP
                wb.payload = Routing.Builder().build().encode().toByteString()
                wb.request_id = msgId
            }
            .build(),
    )

    private fun sendQueueStatus(msgId: Int) = callback.handleFromRadio(
        FromRadio.Builder()
            .also { wb ->
                wb.queueStatus =
                    QueueStatus.Builder()
                        .also { wb ->
                            wb.res = 0
                            wb.free = 16
                            wb.mesh_packet_id = msgId
                        }
                        .build()
            }
            .build()
            .encode(),
    )

    private fun sendAdmin(fromIn: Int, toIn: Int, reqId: Int, initFn: AdminMessage.() -> AdminMessage) {
        // Embed a deterministic 8-byte fake passkey so SessionManager can record a session refresh — mirrors what real
        // firmware always attaches to admin responses (see firmware/src/modules/AdminModule.cpp:1460-1481).
        val adminMsg =
            AdminMessage.Builder()
                .build()
                .initFn()
                .newBuilder()
                .also { wb -> wb.session_passkey = FAKE_SESSION_PASSKEY }
                .build()
        val p =
            makeDataPacket(
                fromIn,
                toIn,
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.ADMIN_APP
                        wb.payload = adminMsg.encode().toByteString()
                        wb.request_id = reqId
                    }
                    .build(),
            )
        callback.handleFromRadio(p.encode())
    }

    // / Send a fake ack packet back if the sender asked for want_ack
    private fun sendFakeAck(pr: ToRadio) {
        val packet = pr.packet ?: return
        transportScope.handledLaunch {
            delay(ACK_DELAY_MS)
            lifecycle.runIfOpen {
                val directPeer = scenario.peers[scenario.directPeerIndex]
                callback.handleFromRadio(makeAck(directPeer.num, packet.from, packet.id).encode())
            }
        }
    }

    private companion object {
        const val BROADCAST_ADDR = -1 // 0xffffffff
        const val AUTO_REPLY_TEXT = "Got it, thanks! Message received on the demo mesh."

        /** First packet id handed out; low enough to stay clear of ids the app generates for its own sends. */
        const val FIRST_PACKET_ID = 50

        /**
         * Floor of the simulated voltage drift, the counterpart to [MIN_BATTERY_PERCENT]: a nearly-flat Li-ion cell
         * rests around here, so the charts settle on a plausible value instead of running off the bottom of the scale.
         */
        const val MIN_CELL_VOLTAGE = 3.2f
        const val MIN_BATTERY_PERCENT = 5
        const val MAX_BATTERY_PERCENT = 100
        const val VOLTAGE_DRIFT_PER_TICK = 0.01f

        /** Hop budget the simulated nodes transmit with; `hop_limit` is derived so the app can infer hop distance. */
        const val DEFAULT_HOP_START = 3

        /** Spacing between seeded frames; the app timestamps rows on persist, so a burst would collapse together. */
        const val SEED_SPACING_MS = 120L
        const val MESSAGE_SPACING_SECONDS = 240
        const val LIVE_TICK_MS = 20_000L
        const val WEATHER_TICK_INTERVAL = 3
        const val REPLY_DELAY_MS = 2_500L
        const val ACK_DELAY_MS = 2_000L

        val FAKE_SESSION_PASSKEY: okio.ByteString = okio.ByteString.of(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)
    }
}
