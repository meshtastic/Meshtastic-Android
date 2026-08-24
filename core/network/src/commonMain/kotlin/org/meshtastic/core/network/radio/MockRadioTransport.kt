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
import org.meshtastic.proto.HardwareModel
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
import org.meshtastic.proto.Position as ProtoPosition

private val defaultLoRaConfig =
    Config.LoRaConfig(
        use_preset = true,
        modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
        region = Config.LoRaConfig.RegionCode.US,
        hop_limit = 3,
        tx_enabled = true,
    )

private val defaultChannel = ProtoChannel(settings = Channel.default.settings, role = ProtoChannel.Role.PRIMARY)

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
                    copy(get_config_response = Config(lora = defaultLoRaConfig))
                }

            (d.get_channel_request ?: 0) != 0 ->
                sendAdmin(packet.to, packet.from, packet.id) {
                    copy(
                        get_channel_response =
                        ProtoChannel(
                            index = (d.get_channel_request ?: 0) - 1, // 0 based on the response
                            settings = if (d.get_channel_request == 1) Channel.default.settings else null,
                            role =
                            if (d.get_channel_request == 1) {
                                ProtoChannel.Role.PRIMARY
                            } else {
                                ProtoChannel.Role.DISABLED
                            },
                        ),
                    )
                }

            d.get_module_config_request == AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG ->
                sendAdmin(packet.to, packet.from, packet.id) {
                    copy(
                        get_module_config_response =
                        ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = MY_NODE_STATUS)),
                    )
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
        val metadata = DeviceMetadata(firmware_version = FIRMWARE_VERSION, hw_model = HardwareModel.ANDROID_SIM)
        val frames =
            listOf(
                FromRadio(my_info = ProtoMyNodeInfo(my_node_num = MY_NODE, reboot_count = 3)),
                FromRadio(metadata = metadata),
                FromRadio(config = Config(lora = defaultLoRaConfig)),
                FromRadio(config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))),
                FromRadio(
                    config =
                    Config(
                        position =
                        Config.PositionConfig(
                            position_broadcast_secs = 900,
                            position_broadcast_smart_enabled = true,
                            gps_enabled = true,
                        ),
                    ),
                ),
                FromRadio(channel = defaultChannel),
                FromRadio(config_complete_id = HandshakeConstants.CONFIG_NONCE),
            )
        frames.forEach { callback.handleFromRadio(it.encode()) }
    }

    /** Stage 2: the node database. This is the only window in which the app accepts `node_info`. */
    private fun sendNodeInfoStage() {
        Logger.d { "Mock transport answering node-info stage with ${SIM_PEERS.size + 1} nodes" }
        callback.handleFromRadio(FromRadio(node_info = localNodeInfo()).encode())
        SIM_PEERS.forEach { peer -> callback.handleFromRadio(FromRadio(node_info = peer.toNodeInfo()).encode()) }
        callback.handleFromRadio(FromRadio(config_complete_id = HandshakeConstants.NODE_INFO_NONCE).encode())

        if (trafficStarted.compareAndSet(expect = false, update = true)) {
            transportScope.handledLaunch { seedTraffic() }
        }
    }

    private fun localNodeInfo() = NodeInfo(
        num = MY_NODE,
        last_heard = nowSeconds.toInt(),
        user =
        User(
            id = NodeAddress.numToDefaultId(MY_NODE),
            long_name = "Demo Handset",
            short_name = "DEMO",
            hw_model = HardwareModel.ANDROID_SIM,
            role = Config.DeviceConfig.Role.CLIENT,
        ),
        position = MY_POSITION.toProto(),
        device_metrics =
        DeviceMetrics(
            battery_level = 78,
            voltage = 3.98f,
            channel_utilization = 8.4f,
            air_util_tx = 1.9f,
            uptime_seconds = 7_240,
        ),
        hops_away = 0,
    )

    private fun SimPeer.toNodeInfo() = NodeInfo(
        num = num,
        // Without last_heard every simulated node reads as offline and disappears the moment the user turns on the
        // node list's "online only" filter.
        last_heard = nowSeconds.toInt() - secondsSinceHeard,
        user =
        User(
            id = NodeAddress.numToDefaultId(num),
            long_name = longName,
            short_name = shortName,
            hw_model = hwModel,
            role = role,
        ),
        position = SimPosition(latitude, longitude, altitude).toProto(),
        device_metrics =
        DeviceMetrics(battery_level = batteryLevel, voltage = voltage, uptime_seconds = uptimeSeconds),
        snr = snr,
        hops_away = hops,
        channel = 0,
    )

    // ── Simulated traffic ─────────────────────────────────────────────────────────────────────

    /**
     * Seeds a mesh that already has history, then keeps it gently alive.
     *
     * The seed pass is spaced out because message and telemetry rows are timestamped when the app persists them, not
     * from the payload: a burst emitted inside the same millisecond collapses into one indistinguishable clump of chart
     * points and messages.
     */
    private suspend fun seedTraffic() {
        SIM_PEERS.forEach { peer ->
            lifecycle.runIfOpen { callback.handleFromRadio(peer.positionPacket(nextPacketId()).encode()) }
            delay(SEED_SPACING_MS)
        }

        SIM_PEERS.take(TELEMETRY_PEER_COUNT).forEach { peer ->
            lifecycle.runIfOpen {
                callback.handleFromRadio(peer.deviceTelemetryPacket(nextPacketId(), tick = 0).encode())
            }
            delay(SEED_SPACING_MS)
        }

        WEATHER_PEER_INDEXES.forEach { index ->
            val peer = SIM_PEERS[index]
            lifecycle.runIfOpen {
                callback.handleFromRadio(peer.environmentTelemetryPacket(nextPacketId(), tick = 0).encode())
            }
            delay(SEED_SPACING_MS)
        }

        lifecycle.runIfOpen { callback.handleFromRadio(SIM_PEERS[0].neighborInfoPacket(nextPacketId()).encode()) }
        delay(SEED_SPACING_MS)
        lifecycle.runIfOpen { callback.handleFromRadio(SIM_PEERS[1].nodeStatusPacket(nextPacketId()).encode()) }
        delay(SEED_SPACING_MS)

        // Each message is stamped progressively closer to now, so the thread reads as a conversation that unfolded over
        // the last while rather than a block of messages that all arrived in the same second.
        CHANNEL_CONVERSATION.forEachIndexed { index, (peerIndex, text) ->
            val peer = SIM_PEERS[peerIndex]
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    peer
                        .textPacket(
                            id = nextPacketId(),
                            to = BROADCAST_ADDR,
                            text = text,
                            ageSeconds = messageAgeSeconds(CHANNEL_CONVERSATION.size, index),
                        )
                        .encode(),
                )
            }
            delay(SEED_SPACING_MS)
        }

        DIRECT_CONVERSATION.forEachIndexed { index, text ->
            lifecycle.runIfOpen {
                callback.handleFromRadio(
                    SIM_PEERS[DIRECT_PEER_INDEX].textPacket(
                        id = nextPacketId(),
                        to = MY_NODE,
                        text = text,
                        ageSeconds = messageAgeSeconds(DIRECT_CONVERSATION.size, index),
                    )
                        .encode(),
                )
            }
            delay(SEED_SPACING_MS)
        }

        streamLiveTelemetry()
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
            val peer = SIM_PEERS[tick % TELEMETRY_PEER_COUNT]
            lifecycle.runIfOpen { callback.handleFromRadio(peer.deviceTelemetryPacket(nextPacketId(), tick).encode()) }
            if (tick % WEATHER_TICK_INTERVAL == 0) {
                val weatherPeer = SIM_PEERS[WEATHER_PEER_INDEXES.first()]
                lifecycle.runIfOpen {
                    callback.handleFromRadio(weatherPeer.environmentTelemetryPacket(nextPacketId(), tick).encode())
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
                SIM_PEERS[DIRECT_PEER_INDEX]
            } else {
                SIM_PEERS.firstOrNull { it.num == packet.to } ?: return
            }
        val replyTo = if (isBroadcast) BROADCAST_ADDR else MY_NODE

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
    private fun SimPeer.packet(id: Int, to: Int, ageSeconds: Int, data: Data) = MeshPacket(
        id = id,
        from = num,
        to = to,
        channel = 0,
        rx_time = (nowSeconds - ageSeconds).toInt(),
        rx_snr = snr,
        rx_rssi = rssi,
        hop_start = DEFAULT_HOP_START,
        hop_limit = DEFAULT_HOP_START - hops,
        transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
        decoded = data,
    )

    private fun SimPeer.textPacket(id: Int, to: Int, text: String, ageSeconds: Int) = FromRadio(
        packet =
        packet(
            id = id,
            to = to,
            ageSeconds = ageSeconds,
            data = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = text.encodeUtf8()),
        ),
    )

    private fun SimPeer.positionPacket(id: Int) = FromRadio(
        packet =
        packet(
            id = id,
            to = BROADCAST_ADDR,
            ageSeconds = 0,
            data =
            Data(
                portnum = PortNum.POSITION_APP,
                payload = SimPosition(latitude, longitude, altitude).toProto().encode().toByteString(),
            ),
        ),
    )

    /**
     * Device telemetry for [tick], drifted so the charts show a trend rather than a flat line.
     *
     * Both the percentage and the voltage are clamped, not just the percentage: a tick is 20s, so an unbounded slope
     * takes the voltage through 0V inside a couple of hours and the battery and telemetry views then render a cell that
     * cannot physically exist.
     */
    private fun SimPeer.deviceTelemetryPacket(id: Int, tick: Int): FromRadio {
        val driftedBattery = (batteryLevel - tick).coerceIn(MIN_BATTERY_PERCENT, MAX_BATTERY_PERCENT)
        val driftedVoltage = (voltage - tick * VOLTAGE_DRIFT_PER_TICK).coerceAtLeast(MIN_CELL_VOLTAGE)
        return FromRadio(
            packet =
            packet(
                id = id,
                to = BROADCAST_ADDR,
                ageSeconds = 0,
                data =
                Data(
                    portnum = PortNum.TELEMETRY_APP,
                    payload =
                    Telemetry(
                        device_metrics =
                        DeviceMetrics(
                            battery_level = driftedBattery,
                            voltage = driftedVoltage,
                            channel_utilization = 6f + (tick % 5) * 1.5f,
                            air_util_tx = 1.2f + (tick % 4) * 0.4f,
                            uptime_seconds = uptimeSeconds + tick * (LIVE_TICK_MS / 1000).toInt(),
                        ),
                    )
                        .encode()
                        .toByteString(),
                ),
            ),
        )
    }

    private fun SimPeer.environmentTelemetryPacket(id: Int, tick: Int) = FromRadio(
        packet =
        packet(
            id = id,
            to = BROADCAST_ADDR,
            ageSeconds = 0,
            data =
            Data(
                portnum = PortNum.TELEMETRY_APP,
                payload =
                Telemetry(
                    environment_metrics =
                    EnvironmentMetrics(
                        // Temperature AND humidity must both be present or the Environment tab
                        // stays empty.
                        temperature = 18.5f + (tick % 7) * 0.4f,
                        relative_humidity = 47f + (tick % 5) * 1.5f,
                        barometric_pressure = 1013.2f + (tick % 3) * 0.3f,
                    ),
                )
                    .encode()
                    .toByteString(),
            ),
        ),
    )

    private fun SimPeer.neighborInfoPacket(id: Int) = FromRadio(
        packet =
        packet(
            id = id,
            to = BROADCAST_ADDR,
            ageSeconds = 0,
            data =
            Data(
                portnum = PortNum.NEIGHBORINFO_APP,
                payload =
                NeighborInfo(
                    node_id = num,
                    last_sent_by_id = num,
                    node_broadcast_interval_secs = 900,
                    neighbors =
                    SIM_PEERS.drop(1).take(3).map { neighbor ->
                        Neighbor(
                            node_id = neighbor.num,
                            snr = neighbor.snr,
                            last_rx_time = nowSeconds.toInt(),
                            node_broadcast_interval_secs = 900,
                        )
                    },
                )
                    .encode()
                    .toByteString(),
            ),
        ),
    )

    private fun SimPeer.nodeStatusPacket(id: Int) = FromRadio(
        packet =
        packet(
            id = id,
            to = BROADCAST_ADDR,
            ageSeconds = 0,
            data =
            Data(
                portnum = PortNum.NODE_STATUS_APP,
                payload = StatusMessage(status = PEER_NODE_STATUS).encode().toByteString(),
            ),
        ),
    )

    private fun makeDataPacket(fromIn: Int, toIn: Int, data: Data) = FromRadio(
        packet =
        MeshPacket(
            id = nextPacketId(),
            from = fromIn,
            to = toIn,
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded = data,
        ),
    )

    private fun makeAck(fromIn: Int, toIn: Int, msgId: Int) = makeDataPacket(
        fromIn,
        toIn,
        Data(portnum = PortNum.ROUTING_APP, payload = Routing().encode().toByteString(), request_id = msgId),
    )

    private fun sendQueueStatus(msgId: Int) = callback.handleFromRadio(
        FromRadio(queueStatus = QueueStatus(res = 0, free = 16, mesh_packet_id = msgId)).encode(),
    )

    private fun sendAdmin(fromIn: Int, toIn: Int, reqId: Int, initFn: AdminMessage.() -> AdminMessage) {
        // Embed a deterministic 8-byte fake passkey so SessionManager can record a session refresh — mirrors what real
        // firmware always attaches to admin responses (see firmware/src/modules/AdminModule.cpp:1460-1481).
        val adminMsg = AdminMessage().initFn().copy(session_passkey = FAKE_SESSION_PASSKEY)
        val p =
            makeDataPacket(
                fromIn,
                toIn,
                Data(portnum = PortNum.ADMIN_APP, payload = adminMsg.encode().toByteString(), request_id = reqId),
            )
        callback.handleFromRadio(p.encode())
    }

    // / Send a fake ack packet back if the sender asked for want_ack
    private fun sendFakeAck(pr: ToRadio) {
        val packet = pr.packet ?: return
        transportScope.handledLaunch {
            delay(ACK_DELAY_MS)
            lifecycle.runIfOpen {
                callback.handleFromRadio(makeAck(SIM_PEERS[DIRECT_PEER_INDEX].num, packet.from, packet.id).encode())
            }
        }
    }

    /** One simulated peer in the demo mesh. */
    private data class SimPeer(
        val num: Int,
        val longName: String,
        val shortName: String,
        val hwModel: HardwareModel,
        val role: Config.DeviceConfig.Role,
        val latitude: Double,
        val longitude: Double,
        val altitude: Int,
        val batteryLevel: Int,
        val voltage: Float,
        val snr: Float,
        val rssi: Int,
        val hops: Int,
        val secondsSinceHeard: Int,
        val uptimeSeconds: Int,
    )

    /** Latitude/longitude/altitude triple, converted to the proto's scaled-integer representation on demand. */
    private data class SimPosition(val latitude: Double, val longitude: Double, val altitude: Int) {
        fun toProto() = ProtoPosition(
            latitude_i = org.meshtastic.core.model.Position.degI(latitude),
            longitude_i = org.meshtastic.core.model.Position.degI(longitude),
            altitude = altitude,
            time = nowSeconds.toInt(),
            // 32 bits is "full precision"; the coarse end of the scale draws a large uncertainty circle instead of
            // placing the node where it actually is.
            precision_bits = 32,
            sats_in_view = 9,
            location_source = ProtoPosition.LocSource.LOC_INTERNAL,
        )
    }

    private companion object {
        const val MY_NODE = 0x42424242
        const val BROADCAST_ADDR = -1 // 0xffffffff
        const val FIRMWARE_VERSION = "9.9.9.abcdefg"
        const val MY_NODE_STATUS = "Running Demo Mode — no radio attached."
        const val PEER_NODE_STATUS = "Solar powered, up on the ridge."
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
        const val DIRECT_PEER_INDEX = 0
        const val TELEMETRY_PEER_COUNT = 4
        val WEATHER_PEER_INDEXES = listOf(4)

        /** Spacing between seeded frames; the app timestamps rows on persist, so a burst would collapse together. */
        const val SEED_SPACING_MS = 120L
        const val MESSAGE_SPACING_SECONDS = 240
        const val LIVE_TICK_MS = 20_000L
        const val WEATHER_TICK_INTERVAL = 3
        const val REPLY_DELAY_MS = 2_500L
        const val ACK_DELAY_MS = 2_000L

        val FAKE_SESSION_PASSKEY: okio.ByteString = okio.ByteString.of(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)

        val MY_POSITION = SimPosition(latitude = 32.776665, longitude = -96.796989, altitude = 138)

        /**
         * The demo mesh. Deterministic on purpose — the same mesh every launch makes the demo reproducible for
         * screenshots, support requests and store reviews. Spread over ~15 km so the map has something to fit.
         */
        val SIM_PEERS =
            listOf(
                SimPeer(
                    num = MY_NODE + 1,
                    longName = "Riverside Base",
                    shortName = "RVSD",
                    hwModel = HardwareModel.HELTEC_V3,
                    role = Config.DeviceConfig.Role.CLIENT,
                    latitude = 32.802,
                    longitude = -96.769,
                    altitude = 152,
                    batteryLevel = 92,
                    voltage = 4.09f,
                    snr = 11.5f,
                    rssi = -62,
                    hops = 0,
                    secondsSinceHeard = 45,
                    uptimeSeconds = 128_400,
                ),
                SimPeer(
                    num = MY_NODE + 2,
                    longName = "Trail Runner",
                    shortName = "TRLR",
                    hwModel = HardwareModel.TRACKER_T1000_E,
                    role = Config.DeviceConfig.Role.TRACKER,
                    latitude = 32.7605,
                    longitude = -96.8305,
                    altitude = 145,
                    batteryLevel = 64,
                    voltage = 3.87f,
                    snr = 6.25f,
                    rssi = -84,
                    hops = 0,
                    secondsSinceHeard = 130,
                    uptimeSeconds = 41_900,
                ),
                SimPeer(
                    num = MY_NODE + 3,
                    longName = "Oak Cliff Repeater",
                    shortName = "OAKR",
                    hwModel = HardwareModel.RAK4631,
                    role = Config.DeviceConfig.Role.ROUTER,
                    latitude = 32.7395,
                    longitude = -96.8215,
                    altitude = 189,
                    batteryLevel = 100,
                    voltage = 4.14f,
                    snr = 9.0f,
                    rssi = -71,
                    hops = 0,
                    secondsSinceHeard = 20,
                    uptimeSeconds = 903_600,
                ),
                SimPeer(
                    num = MY_NODE + 4,
                    longName = "Deep Ellum Handheld",
                    shortName = "DEEP",
                    hwModel = HardwareModel.T_DECK,
                    role = Config.DeviceConfig.Role.CLIENT,
                    latitude = 32.7842,
                    longitude = -96.7845,
                    altitude = 141,
                    batteryLevel = 47,
                    voltage = 3.74f,
                    snr = -2.5f,
                    rssi = -103,
                    hops = 1,
                    secondsSinceHeard = 320,
                    uptimeSeconds = 9_800,
                ),
                SimPeer(
                    num = MY_NODE + 5,
                    longName = "Rooftop Weather",
                    shortName = "WTHR",
                    hwModel = HardwareModel.HELTEC_MESH_NODE_T114,
                    role = Config.DeviceConfig.Role.SENSOR,
                    latitude = 32.8145,
                    longitude = -96.8055,
                    altitude = 205,
                    batteryLevel = 88,
                    voltage = 4.02f,
                    snr = 4.75f,
                    rssi = -91,
                    hops = 1,
                    secondsSinceHeard = 210,
                    uptimeSeconds = 512_000,
                ),
                SimPeer(
                    num = MY_NODE + 6,
                    longName = "Lakeside Solar",
                    shortName = "LAKE",
                    hwModel = HardwareModel.STATION_G2,
                    role = Config.DeviceConfig.Role.CLIENT,
                    latitude = 32.8365,
                    longitude = -96.7325,
                    altitude = 167,
                    batteryLevel = 73,
                    voltage = 3.94f,
                    snr = 1.5f,
                    rssi = -98,
                    hops = 2,
                    secondsSinceHeard = 640,
                    uptimeSeconds = 254_300,
                ),
                SimPeer(
                    num = MY_NODE + 7,
                    longName = "Bike Courier",
                    shortName = "BIKE",
                    hwModel = HardwareModel.TBEAM,
                    role = Config.DeviceConfig.Role.TRACKER,
                    latitude = 32.7688,
                    longitude = -96.7492,
                    altitude = 134,
                    batteryLevel = 31,
                    voltage = 3.62f,
                    snr = -6.5f,
                    rssi = -112,
                    hops = 2,
                    secondsSinceHeard = 1_180,
                    uptimeSeconds = 3_600,
                ),
                SimPeer(
                    num = MY_NODE + 8,
                    longName = "Field Kit Echo",
                    shortName = "ECHO",
                    hwModel = HardwareModel.T_ECHO,
                    role = Config.DeviceConfig.Role.CLIENT_MUTE,
                    latitude = 32.7215,
                    longitude = -96.7738,
                    altitude = 158,
                    batteryLevel = 56,
                    voltage = 3.81f,
                    snr = 3.25f,
                    rssi = -95,
                    hops = 1,
                    secondsSinceHeard = 2_400,
                    uptimeSeconds = 76_500,
                ),
            )

        /** Seeded channel conversation, as (peer index, text) pairs. Oldest first. */
        val CHANNEL_CONVERSATION =
            listOf(
                0 to "Morning all — base station is back online after the power cut.",
                2 to "Copy that. Repeater on Oak Cliff is holding steady, 100% battery.",
                1 to "Out on the trail loop, signal is solid the whole way today.",
                4 to "Rooftop sensor reading 18.5C and 47% humidity if anyone cares.",
                0 to "Nice. Net check complete, everyone reporting in.",
            )

        /** Seeded direct-message thread from [DIRECT_PEER_INDEX]. Oldest first. */
        val DIRECT_CONVERSATION =
            listOf(
                "Hey, are you still planning to bring the spare antenna tomorrow?",
                "No rush — just let me know before you set off.",
            )
    }
}
