package com.geeksville.mesh.service

import android.content.Context
import com.geeksville.android.BuildUtils
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.*
import com.geeksville.mesh.model.getInitials
import com.google.protobuf.ByteString
import okhttp3.internal.toHexString

/** A simulated interface that is used for testing in the simulator */
class MockInterface(private val service: RadioInterfaceService) : Logging, IRadioInterface {
    companion object : Logging, InterfaceFactory('m') {
        override fun createInterface(
            service: RadioInterfaceService,
            rest: String
        ): IRadioInterface = MockInterface(service)

        override fun addressValid(context: Context, rest: String): Boolean =
            BuildUtils.isEmulator || ((context.applicationContext as GeeksvilleApplication).isInTestLab)

        init {
            registerFactory()
        }
    }

    private var messageCount = 50

    // an infinite sequence of ints
    private val messageNumSequence = generateSequence { messageCount++ }.iterator()

    init {
        info("Starting the mock interface")
        service.onConnect() // Tell clients they can use the API
    }

    override fun handleSendToRadio(p: ByteArray) {
        val pr = MeshProtos.ToRadio.parseFrom(p)

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
            d.getRadioRequest ->
                sendAdmin(pr.packet.to, pr.packet.from, pr.packet.id) {
                    getRadioResponse = RadioConfigProtos.RadioConfig.newBuilder().apply {

                        preferences =
                            RadioConfigProtos.RadioConfig.UserPreferences.newBuilder().apply {
                                region = RadioConfigProtos.RegionCode.TW
                                // FIXME set critical times?
                            }.build()
                    }.build()
                }

            d.getChannelRequest != 0 ->
                sendAdmin(pr.packet.to, pr.packet.from, pr.packet.id) {
                    getChannelResponse = ChannelProtos.Channel.newBuilder().apply {
                        index = d.getChannelRequest - 1 // 0 based on the response
                        role =
                            if (d.getChannelRequest == 1) ChannelProtos.Channel.Role.PRIMARY else ChannelProtos.Channel.Role.DISABLED
                    }.build()
                }

            else ->
                info("Ignoring admin sent to mock interface $d")
        }
    }

    override fun close() {
        info("Closing the mock interface")
    }

    /// Generate a fake text message from a node
    private fun makeTextMessage(numIn: Int) =
        MeshProtos.FromRadio.newBuilder().apply {
            packet = MeshProtos.MeshPacket.newBuilder().apply {
                id = messageNumSequence.next()
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
                id = messageNumSequence.next()
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
    private fun sendFakeAck(pr: MeshProtos.ToRadio) {
        service.handleFromRadio(
            makeAck(pr.packet.to, pr.packet.from, pr.packet.id).build().toByteArray()
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
                        longName = "Sim " + num.toHexString()
                        shortName = getInitials(longName)
                        hwModel = MeshProtos.HardwareModel.ANDROID_SIM
                    }.build()
                    position = MeshProtos.Position.newBuilder().apply {
                        latitudeI = Position.degI(lat)
                        longitudeI = Position.degI(lon)
                        batteryLevel = 42
                        altitude = 35
                        time = (System.currentTimeMillis() / 1000).toInt()
                    }.build()
                }.build()
            }

        // Simulated network data to feed to our app
        val MY_NODE = 0x42424242
        val packets = arrayOf(
            // MyNodeInfo
            MeshProtos.FromRadio.newBuilder().apply {
                myInfo = MeshProtos.MyNodeInfo.newBuilder().apply {
                    myNodeNum = MY_NODE
                    messageTimeoutMsec = 5 * 60 * 1000
                    firmwareVersion = "1.2.8" // Pretend to be running an older 1.2 version
                    numBands = 13
                    maxChannels = 8
                }.build()
            },

            // Fake NodeDB
            makeNodeInfo(MY_NODE, 32.776665, -96.796989), // dallas
            makeNodeInfo(MY_NODE + 1, 32.960758, -96.733521), // richardson

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
