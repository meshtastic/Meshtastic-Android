package com.geeksville.mesh

import android.content.Context
import com.geeksville.mesh.service.RadioInterfaceService

class SimRadio(private val context: Context) {

    /**
     * When simulating we parse these MeshPackets as if they arrived at startup
     * Send broadcast them after we receive a ToRadio.WantNodes message.
     *
     * Our fake net has three nodes
     *
     * +16508675309, nodenum 9 - our node
     * +16508675310, nodenum 10 - some other node, name Bob One/BO
     * (eventually) +16508675311, nodenum 11 - some other node
     */
    private val simInitPackets =
        arrayOf(
            """ { "from": 10, "to": 9, "payload": { "user": { "id": "+16508675310", "longName": "Bob One", "shortName": "BO" }}}  """,
            """ { "from": 10, "to": 9, "payload": { "data": { "payload": "aGVsbG8gd29ybGQ=", "typ": 0 }}}  """, // SIGNAL_OPAQUE
            """ { "from": 10, "to": 9, "payload": { "data": { "payload": "aGVsbG8gd29ybGQ=", "typ": 1 }}}  """, // CLEAR_TEXT
            """ { "from": 10, "to": 9, "payload": { "data": { "payload": "", "typ": 2 }}}  """ // CLEAR_READACK
        )

    fun start() {
        // FIXME, do this sim startup elsewhere, because waiting for a packet from MeshService
        // isn't right, because that service can't successfully send radio packets until it knows
        // our node num
        // Instead a separate sim radio thing can come in at startup and force these broadcasts to happen
        // at the right time
        // Send a fake my_node_num response
        /* FIXME - change to use new radio info message
        RadioInterfaceService.broadcastReceivedFromRadio(
            context,
            MeshProtos.FromRadio.newBuilder().apply {
                myNodeNum = 9
            }.build().toByteArray()
        ) */

        simInitPackets.forEach { json ->
            val fromRadio = MeshProtos.FromRadio.newBuilder().apply {
                packet = MeshProtos.MeshPacket.newBuilder().apply {
                    // jsonParser.merge(json, this)
                }.build()
            }.build()

            RadioInterfaceService.broadcastReceivedFromRadio(context, fromRadio.toByteArray())
        }
    }
}