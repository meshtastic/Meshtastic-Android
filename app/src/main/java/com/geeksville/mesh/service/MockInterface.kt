package com.geeksville.mesh.service

import com.geeksville.android.Logging
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R

/** A simulated interface that is used for testing in the simulator */
class MockInterface(private val service: RadioInterfaceService) : Logging, IRadioInterface {
    companion object : Logging {

        val interfaceName = "m"
    }

    init {
        info("Starting the mock interface")
        service.onConnect() // Tell clients they can use the API
    }

    override fun handleSendToRadio(b: ByteArray) {
        val p = MeshProtos.ToRadio.parseFrom(b)

        if (p.wantConfigId != 0)
            sendConfigResponse(p.wantConfigId)
        else
            info("Ignoring data sent to mock interface $p")
    }

    override fun close() {
        info("Closing the mock interface")
    }

    private fun sendConfigResponse(configId: Int) {
        debug("Sending mock config response")

        // Simulated network data to feed to our app
        val MY_NODE = 0x42424242
        val packets = arrayOf(
            // MyNodeInfo
            MeshProtos.FromRadio.newBuilder().apply {
                myInfo = MeshProtos.MyNodeInfo.newBuilder().apply {
                    myNodeNum = MY_NODE
                    region = "TW"
                    numChannels = 7
                    hwModel = "Sim"
                    packetIdBits = 32
                    nodeNumBits = 32
                    currentPacketId = 1
                    messageTimeoutMsec = 5 * 60 * 1000
                    firmwareVersion = service.getString(R.string.cur_firmware_version)
                }.build()
            },

            // RadioConfig
            MeshProtos.FromRadio.newBuilder().apply {
                radio = MeshProtos.RadioConfig.newBuilder().apply {

                    preferences = MeshProtos.RadioConfig.UserPreferences.newBuilder().apply {
                        region = MeshProtos.RegionCode.TW
                        // FIXME set critical times
                    }.build()

                    channel = MeshProtos.ChannelSettings.newBuilder().apply {
                        // fixme() // fix channel display
                        // fix testlab // (application as GeeksvilleApplication).isInTestLab
                    }.build()
                }.build()
            },

            // Fake NodeDB
            MeshProtos.FromRadio.newBuilder().apply {
                nodeInfo = MeshProtos.NodeInfo.newBuilder().apply {
                    num = MY_NODE
                    user = MeshProtos.User.newBuilder().apply {
                        id = "!0x42424242"
                        longName = "Sim User"
                        shortName = "SU"
                    }.build()
                    position = MeshProtos.Position.newBuilder().apply {
                        latitudeI = 42
                        longitudeI = 42 // FIXME
                        time = 42 // FIXME
                    }.build()
                }.build()
            },

            MeshProtos.FromRadio.newBuilder().apply {
                configCompleteId = configId
            }
        )

        packets.forEach { p ->
            service.handleFromRadio(p.build().toByteArray())
        }
    }
}