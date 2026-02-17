/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.repository.radio

import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.getInitials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.nowSeconds
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
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
import kotlin.random.Random
import org.meshtastic.proto.Channel as ProtoChannel
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

private val defaultLoRaConfig = Config.LoRaConfig(use_preset = true, region = Config.LoRaConfig.RegionCode.TW)

private val defaultChannel = ProtoChannel(settings = Channel.default.settings, role = ProtoChannel.Role.PRIMARY)

/** A simulated interface that is used for testing in the simulator */
@Suppress("detekt:TooManyFunctions", "detekt:MagicNumber")
class MockInterface
@AssistedInject
constructor(
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface {

    companion object {
        private const val MY_NODE = 0x42424242
    }

    private var currentPacketId = 50

    // an infinite sequence of ints
    private val packetIdSequence = generateSequence { currentPacketId++ }.iterator()

    init {
        Logger.i { "Starting the mock interface" }
        service.onConnect() // Tell clients they can use the API
    }

    override fun handleSendToRadio(p: ByteArray) {
        val pr = ToRadio.ADAPTER.decode(p)
        val packet = pr.packet
        if (packet != null) {
            sendQueueStatus(packet.id)
        }

        val data = packet?.decoded

        when {
            (pr.want_config_id ?: 0) != 0 -> sendConfigResponse(pr.want_config_id ?: 0)
            data != null && data.portnum == PortNum.ADMIN_APP ->
                handleAdminPacket(pr, AdminMessage.ADAPTER.decode(data.payload))
            packet != null && packet.want_ack == true -> sendFakeAck(pr)
            else -> Logger.i { "Ignoring data sent to mock interface $pr" }
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
                        ModuleConfig(
                            statusmessage =
                            ModuleConfig.StatusMessageConfig(node_status = "Going to the farm.. to grow wheat."),
                        ),
                    )
                }

            else -> Logger.i { "Ignoring admin sent to mock interface $d" }
        }
    }

    override fun close() {
        Logger.i { "Closing the mock interface" }
    }

    // / Generate a fake text message from a node
    private fun makeTextMessage(numIn: Int) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
            from = numIn,
            to = 0xffffffff.toInt(), // broadcast
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded =
            Data(
                portnum = PortNum.TEXT_MESSAGE_APP,
                payload = "This simulated node sends Hi!".encodeUtf8(),
            ),
        ),
    )

    private fun makeNeighborInfo(numIn: Int) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
            from = numIn,
            to = 0xffffffff.toInt(), // broadcast
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded =
            Data(
                portnum = PortNum.NEIGHBORINFO_APP,
                payload =
                NeighborInfo(
                    node_id = numIn,
                    last_sent_by_id = numIn,
                    node_broadcast_interval_secs = 60,
                    neighbors =
                    listOf(
                        Neighbor(
                            node_id = numIn + 1,
                            snr = 10.0f,
                            last_rx_time = nowSeconds.toInt(),
                            node_broadcast_interval_secs = 60,
                        ),
                        Neighbor(
                            node_id = numIn + 2,
                            snr = 12.0f,
                            last_rx_time = nowSeconds.toInt(),
                            node_broadcast_interval_secs = 60,
                        ),
                    ),
                )
                    .encode()
                    .toByteString(),
            ),
        ),
    )

    private fun makePosition(numIn: Int) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
            from = numIn,
            to = 0xffffffff.toInt(), // broadcast
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded =
            Data(
                portnum = PortNum.POSITION_APP,
                payload =
                ProtoPosition(
                    latitude_i = org.meshtastic.core.model.Position.degI(32.776665),
                    longitude_i = org.meshtastic.core.model.Position.degI(-96.796989),
                    altitude = 150,
                    time = nowSeconds.toInt(),
                    precision_bits = 15,
                )
                    .encode()
                    .toByteString(),
            ),
        ),
    )

    private fun makeTelemetry(numIn: Int) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
            from = numIn,
            to = 0xffffffff.toInt(), // broadcast
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded =
            Data(
                portnum = PortNum.TELEMETRY_APP,
                payload =
                Telemetry(
                    time = nowSeconds.toInt(),
                    device_metrics =
                    DeviceMetrics(
                        battery_level = 85,
                        voltage = 4.1f,
                        channel_utilization = 0.12f,
                        air_util_tx = 0.05f,
                        uptime_seconds = 123456,
                    ),
                )
                    .encode()
                    .toByteString(),
            ),
        ),
    )

    private fun makeNodeStatus(numIn: Int) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
            from = numIn,
            to = 0xffffffff.toInt(), // broadcast
            rx_time = nowSeconds.toInt(),
            rx_snr = 1.5f,
            decoded =
            Data(
                portnum = PortNum.NODE_STATUS_APP,
                payload =
                StatusMessage(status = "Going to the farm.. to grow wheat.").encode().toByteString(),
            ),
        ),
    )

    private fun makeDataPacket(fromIn: Int, toIn: Int, data: Data) = FromRadio(
        packet =
        MeshPacket(
            id = packetIdSequence.next(),
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

    private fun sendQueueStatus(msgId: Int) = service.handleFromRadio(
        FromRadio(queueStatus = QueueStatus(res = 0, free = 16, mesh_packet_id = msgId)).encode(),
    )

    private fun sendAdmin(fromIn: Int, toIn: Int, reqId: Int, initFn: AdminMessage.() -> AdminMessage) {
        val adminMsg = AdminMessage().initFn()
        val p =
            makeDataPacket(
                fromIn,
                toIn,
                Data(portnum = PortNum.ADMIN_APP, payload = adminMsg.encode().toByteString(), request_id = reqId),
            )
        service.handleFromRadio(p.encode())
    }

    // / Send a fake ack packet back if the sender asked for want_ack
    private fun sendFakeAck(pr: ToRadio) = service.serviceScope.handledLaunch {
        val packet = pr.packet ?: return@handledLaunch
        delay(2000)
        service.handleFromRadio(makeAck(MY_NODE + 1, packet.from ?: 0, packet.id).encode())
    }

    private fun sendConfigResponse(configId: Int) {
        Logger.d { "Sending mock config response" }

        // / Generate a fake node info entry
        @Suppress("MagicNumber")
        fun makeNodeInfo(numIn: Int, lat: Double, lon: Double) = FromRadio(
            node_info =
            NodeInfo(
                num = numIn,
                user =
                User(
                    id = DataPacket.nodeNumToDefaultId(numIn),
                    long_name = "Sim " + Integer.toHexString(numIn),
                    short_name = getInitials("Sim " + Integer.toHexString(numIn)),
                    hw_model = HardwareModel.ANDROID_SIM,
                ),
                position =
                ProtoPosition(
                    latitude_i = org.meshtastic.core.model.Position.degI(lat),
                    longitude_i = org.meshtastic.core.model.Position.degI(lon),
                    altitude = 35,
                    time = nowSeconds.toInt(),
                    precision_bits = Random.nextInt(10, 19),
                ),
            ),
        )

        // Simulated network data to feed to our app
        val packets =
            arrayOf(
                // MyNodeInfo
                FromRadio(my_info = ProtoMyNodeInfo(my_node_num = MY_NODE)),
                FromRadio(
                    metadata = DeviceMetadata(firmware_version = "9.9.9.abcdefg", hw_model = HardwareModel.ANDROID_SIM),
                ),

                // Fake NodeDB
                makeNodeInfo(MY_NODE, 32.776665, -96.796989), // dallas
                makeNodeInfo(MY_NODE + 1, 32.960758, -96.733521), // richardson
                FromRadio(config = Config(lora = defaultLoRaConfig)),
                FromRadio(config = Config(lora = defaultLoRaConfig)),
                FromRadio(channel = defaultChannel),
                FromRadio(config_complete_id = configId),

                // Done with config response, now pretend to receive some text messages
                makeTextMessage(MY_NODE + 1),
                makeNeighborInfo(MY_NODE + 1),
                makePosition(MY_NODE + 1),
                makeTelemetry(MY_NODE + 1),
                makeNodeStatus(MY_NODE + 1),
            )

        packets.forEach { p -> service.handleFromRadio(p.encode()) }
    }
}
