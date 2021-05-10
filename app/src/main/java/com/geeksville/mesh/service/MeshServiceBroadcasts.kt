package com.geeksville.mesh.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.NodeInfo

class MeshServiceBroadcasts(
    private val context: Context,
    private val clientPackages: MutableMap<String, String>,
    private val getConnectionState: () -> MeshService.ConnectionState
) {
    /**
     * Broadcast some received data
     * Payload will be a DataPacket
     */
    fun broadcastReceivedData(payload: DataPacket) {

        explicitBroadcast(
            Intent(MeshService.actionReceived(payload.dataType)).putExtra(
                EXTRA_PAYLOAD,
                payload
            )
        )

        /*
        // For the time being we ALSO broadcast using old ACTION_RECEIVED_DATA field for any oldschool opaque packets
        // newer packets (that have a non zero portnum) are only broadcast using the standard mechanism.
        if(payload.dataType == Portnums.PortNum.UNKNOWN_APP_VALUE)
            explicitBroadcast(Intent(MeshService.ACTION_RECEIVED_DATA).putExtra(EXTRA_PAYLOAD, payload))
         */
    }

    fun broadcastNodeChange(info: NodeInfo) {
        MeshService.debug("Broadcasting node change $info")
        val intent = Intent(MeshService.ACTION_NODE_CHANGE).putExtra(EXTRA_NODEINFO, info)
        explicitBroadcast(intent)
    }

    fun broadcastMessageStatus(p: DataPacket) {
        if (p.id == 0) {
            MeshService.debug("Ignoring anonymous packet status")
        } else {
            // Do not log, contains PII possibly
            // MeshService.debug("Broadcasting message status $p")
            val intent = Intent(MeshService.ACTION_MESSAGE_STATUS).apply {
                putExtra(EXTRA_PACKET_ID, p.id)
                putExtra(EXTRA_STATUS, p.status as Parcelable)
            }
            explicitBroadcast(intent)
        }
    }

    /**
     * Broadcast our current connection status
     */
    fun broadcastConnection() {
        val intent = Intent(MeshService.ACTION_MESH_CONNECTED).putExtra(
            EXTRA_CONNECTED,
            getConnectionState().toString()
        )
        explicitBroadcast(intent)
    }

    /**
     * See com.geeksville.mesh broadcast intents.
     *
     *     RECEIVED_OPAQUE  for data received from other nodes
     *     NODE_CHANGE  for new IDs appearing or disappearing
     *     ACTION_MESH_CONNECTED for losing/gaining connection to the packet radio
     *         Note: this is not the same as RadioInterfaceService.RADIO_CONNECTED_ACTION,
     *         because it implies we have assembled a valid node db.
     */
    private fun explicitBroadcast(intent: Intent) {
        context.sendBroadcast(intent) // We also do a regular (not explicit broadcast) so any context-registered rceivers will work
        clientPackages.forEach {
            intent.setClassName(it.value, it.key)
            context.sendBroadcast(intent)
        }
    }
}
