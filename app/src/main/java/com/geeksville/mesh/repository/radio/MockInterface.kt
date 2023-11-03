package com.geeksville.mesh.repository.radio

import android.app.Application
import com.geeksville.mesh.*
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.getInitials
import com.google.protobuf.ByteString
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

private val defaultLoRaConfig = ConfigKt.loRaConfig {
    usePreset = true
    region = ConfigProtos.Config.LoRaConfig.RegionCode.TW
}

private val defaultChannel = channel {
    settings = Channel.default.settings
    role = ChannelProtos.Channel.Role.PRIMARY
}

/** A simulated interface that is used for testing in the simulator */
class MockInterface @AssistedInject constructor(
    private val context: Application,
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface, Logging {

    companion object {
        private const val MY_NODE = 0x42424242
    }

    private var currentPacketId = 50

    // an infinite sequence of ints
    private val packetIdSequence = generateSequence { currentPacketId++ }.iterator()

    init {
        info("Starting the mock interface")
        service.onConnect() // Tell clients they can use the API
    }

    override fun handleSendToRadio(p: ByteArray) {
        val pr = MeshProtos.ToRadio.parseFrom(p)
        sendQueueStatus(pr.packet.id)

        val data = if (pr.hasPacket()) pr.packet.decoded else null

        when {
            pr.wantConfigId != 0 -> sendConfigResponse(pr.wantConfigId)
            data != null && data.portnum == Portnums.PortNum.ADMIN_APP -> handleAdminPacket(
                pr,
                AdminProtos.AdminMessage.parseFrom(data.payload)
            )
            pr.hasPacket() && pr.packet.wantAck -> sendFakeAck(pr)
            else -> info("Ignoring data sent to mock interface $pr")
        }
    }

    private fun handleAdminPacket(pr: MeshProtos.ToRadio, d: AdminProtos.AdminMessage) {
        when {
            d.getConfigRequest == AdminProtos.AdminMessage.ConfigType.LORA_CONFIG ->
                sendAdmin(pr.packet.to, pr.packet.from, pr.packet.id) {
                    getConfigResponse = config { lora = defaultLoRaConfig }
                }

            d.getChannelRequest != 0 ->
                sendAdmin(pr.packet.to, pr.packet.from, pr.packet.id) {
                    getChannelResponse = channel {
                        index = d.getChannelRequest - 1 // 0 based on the response
                        if (d.getChannelRequest == 1) {
                            settings = Channel.default.settings
                            role = ChannelProtos.Channel.Role.PRIMARY
                        }
                    }
                }

            else -> info("Ignoring admin sent to mock interface $d")
        }
    }

    override fun close() {
        info("Closing the mock interface")
    }

    /// Generate a fake text message from a node
    private fun makeTextMessage(numIn: Int) =
        MeshProtos.FromRadio.newBuilder().apply {
            packet = MeshProtos.MeshPacket.newBuilder().apply {
                id = packetIdSequence.next()
                from = numIn
                to = 0xffffffff.toInt() // ugly way of saying broadcast
                rxTime = (System.currentTimeMillis() / 1000).toInt()
                rxSnr = 1.5f
                decoded = MeshProtos.Data.newBuilder().apply {
                    portnum = Portnums.PortNum.TEXT_MESSAGE_APP
                    payload = ByteString.copyFromUtf8("This simulated node sends Hi!")
                }.build()
            }.build()
        }

    private fun makeDataPacket(fromIn: Int, toIn: Int, data: MeshProtos.Data.Builder) =
        MeshProtos.FromRadio.newBuilder().apply {
            packet = MeshProtos.MeshPacket.newBuilder().apply {
                id = packetIdSequence.next()
                from = fromIn
                to = toIn
                rxTime = (System.currentTimeMillis() / 1000).toInt()
                rxSnr = 1.5f
                decoded = data.build()
            }.build()
        }

    private fun makeAck(fromIn: Int, toIn: Int, msgId: Int) =
        makeDataPacket(fromIn, toIn, MeshProtos.Data.newBuilder().apply {
            portnum = Portnums.PortNum.ROUTING_APP
            payload = MeshProtos.Routing.newBuilder().apply {
            }.build().toByteString()
            requestId = msgId
        })

    private fun sendQueueStatus(msgId: Int) = service.handleFromRadio(
        fromRadio {
            queueStatus = queueStatus {
                res = 0
                free = 16
                meshPacketId = msgId
            }
        }.toByteArray()
    )

    private fun sendAdmin(
        fromIn: Int,
        toIn: Int,
        reqId: Int,
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit
    ) {
        val p = makeDataPacket(fromIn, toIn, MeshProtos.Data.newBuilder().apply {
            portnum = Portnums.PortNum.ADMIN_APP
            payload = AdminProtos.AdminMessage.newBuilder().also {
                initFn(it)
            }.build().toByteString()
            requestId = reqId
        })
        service.handleFromRadio(p.build().toByteArray())
    }

    /// Send a fake ack packet back if the sender asked for want_ack
    private fun sendFakeAck(pr: MeshProtos.ToRadio) = service.serviceScope.handledLaunch {
        delay(2000)
        service.handleFromRadio(
            makeAck(MY_NODE + 1, pr.packet.from, pr.packet.id).build().toByteArray()
        )
    }

    private fun sendConfigResponse(configId: Int) {
        debug("Sending mock config response")

        /// Generate a fake node info entry
        fun makeNodeInfo(numIn: Int, lat: Double, lon: Double) =
            MeshProtos.FromRadio.newBuilder().apply {
                nodeInfo = MeshProtos.NodeInfo.newBuilder().apply {
                    num = numIn
                    user = MeshProtos.User.newBuilder().apply {
                        id = DataPacket.nodeNumToDefaultId(numIn)
                        longName = "Sim " + Integer.toHexString(num)
                        shortName = getInitials(longName)
                        hwModel = MeshProtos.HardwareModel.ANDROID_SIM
                    }.build()
                    position = MeshProtos.Position.newBuilder().apply {
                        latitudeI = Position.degI(lat)
                        longitudeI = Position.degI(lon)
                        altitude = 35
                        time = (System.currentTimeMillis() / 1000).toInt()
                    }.build()
                }.build()
            }

        // Simulated network data to feed to our app
        val packets = arrayOf(
            // MyNodeInfo
            MeshProtos.FromRadio.newBuilder().apply {
                myInfo = MeshProtos.MyNodeInfo.newBuilder().apply {
                    myNodeNum = MY_NODE
                }.build()
            },

            MeshProtos.FromRadio.newBuilder().apply {
                metadata = deviceMetadata {
                    firmwareVersion = context.getString(R.string.cur_firmware_version)
                }
            },

            // Fake NodeDB
            makeNodeInfo(MY_NODE, 32.776665, -96.796989), // dallas
            makeNodeInfo(MY_NODE + 1, 32.960758, -96.733521), // richardson

            MeshProtos.FromRadio.newBuilder().apply {
                config = config { lora = defaultLoRaConfig }
            },

            MeshProtos.FromRadio.newBuilder().apply {
                channel = defaultChannel
            },

            MeshProtos.FromRadio.newBuilder().apply {
                configCompleteId = configId
            },

            // Done with config response, now pretend to receive some text messages

            makeTextMessage(MY_NODE + 1)
        )

        packets.forEach { p ->
            service.handleFromRadio(p.build().toByteArray())
        }
    }
}
