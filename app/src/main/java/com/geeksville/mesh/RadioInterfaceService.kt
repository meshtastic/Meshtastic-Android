package com.geeksville.mesh

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.geeksville.android.DebugLogFile
import com.geeksville.android.Logging

const val EXTRA_CONNECTED = "$prefix.Connected"
const val EXTRA_PAYLOAD = "$prefix.Payload"
const val EXTRA_SENDER = "$prefix.Sender"
const val EXTRA_ID = "$prefix.Id"
const val EXTRA_ONLINE = "$prefix.Online"

/**
 * Handles the bluetooth link with a mesh radio device.  Does not cache any device state,
 * just does bluetooth comms etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb.  It doesn't understand protobuf framing etc...
 * It is designed to be simple so it can be stubbed out with a simulated version as needed.
 */
class RadioInterfaceService : JobIntentService(), Logging {

    companion object {
        /**
         * Unique job ID for this service.  Must be the same for all work.
         */
        private const val JOB_ID = 1001

        /**
         * The SEND_TORADIO
         * Payload will be the raw bytes which were contained within a MeshProtos.ToRadio protobuf
         */
        const val SEND_TORADIO_ACTION = "$prefix.SEND_TORADIO"

        /**
         * The RECEIVED_FROMRADIO
         * Payload will be the raw bytes which were contained within a MeshProtos.FromRadio protobuf
         */
        const val RECEIVE_FROMRADIO_ACTION = "$prefix.RECEIVE_FROMRADIO"


        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(
                context,
                RadioInterfaceService::class.java, JOB_ID, work
            )
        }

        /// Helper function to send a packet to the radio
        fun sendToRadio(context: Context, a: ByteArray) {
            val i = Intent(SEND_TORADIO_ACTION)
            i.putExtra(EXTRA_PAYLOAD, a)
            enqueueWork(context, i)
        }
    }

    val sentPacketsLog = DebugLogFile(this, "sent_log.json")

    private fun broadcastReceivedFromRadio(payload: ByteArray) {
        val intent = Intent(RECEIVE_FROMRADIO_ACTION)
        intent.putExtra("$prefix.Payload", payload)
        sendBroadcast(intent)
    }

    fun broadcastConnectionChanged(isConnected: Boolean) {
        val intent = Intent("$prefix.CONNECTION_CHANGED")
        intent.putExtra(EXTRA_CONNECTED, isConnected)
        sendBroadcast(intent)
    }

    /// Send a packet/command out the radio link
    private fun sendToRadio(p: ByteArray) {

        // For debugging/logging purposes ONLY we convert back into a protobuf for readability
        val proto = MeshProtos.ToRadio.parseFrom(p)
        info("TODO sending to radio: $proto")
        sentPacketsLog.log("FIXME JSON")
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    private fun handleFromRadio(p: ByteArray) {
        broadcastReceivedFromRadio(p)
    }

    override fun onDestroy() {
        sentPacketsLog.close()
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) { // We have received work to do.  The system or framework is already
        // holding a wake lock for us at this point, so we can just go.
        debug("Executing work: $intent")
        when (intent.action) {
            SEND_TORADIO_ACTION -> sendToRadio(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
            else -> TODO("Unhandled case")
        }
    }

}