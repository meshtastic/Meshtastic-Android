package com.geeksville.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.geeksville.android.DebugLogFile
import com.geeksville.android.Logging
import com.google.protobuf.util.JsonFormat
import java.util.*

/* Info for the esp32 device side code.  See that source for the 'gold' standard docs on this interface.

MeshBluetoothService UUID 6ba1b218-15a8-461f-9fa8-5dcae273eafd

FIXME - notify vs indication for fromradio output.  Using notify for now, not sure if that is best
FIXME - in the esp32 mesh management code, occasionally mirror the current net db to flash, so that if we reboot we still have a good guess of users who are out there.
FIXME - make sure this protocol is guaranteed robust and won't drop packets

"According to the BLE specification the notification length can be max ATT_MTU - 3. The 3 bytes subtracted is the 3-byte header(OP-code (operation, 1 byte) and the attribute handle (2 bytes)).
In BLE 4.1 the ATT_MTU is 23 bytes (20 bytes for payload), but in BLE 4.2 the ATT_MTU can be negotiated up to 247 bytes."

MAXPACKET is 256? look into what the lora lib uses. FIXME

Characteristics:
UUID
properties
description

8ba2bcc2-ee02-4a55-a531-c525c5e454d5
read
fromradio - contains a newly received packet destined towards the phone (up to MAXPACKET bytes? per packet).
After reading the esp32 will put the next packet in this mailbox.  If the FIFO is empty it will put an empty packet in this
mailbox.

f75c76d2-129e-4dad-a1dd-7866124401e7
write
toradio - write ToRadio protobufs to this charstic to send them (up to MAXPACKET len)

ed9da18c-a800-4f66-a670-aa7547e34453
read|notify|write
fromnum - the current packet # in the message waiting inside fromradio, if the phone sees this notify it should read messages
until it catches up with this number.
  The phone can write to this register to go backwards up to FIXME packets, to handle the rare case of a fromradio packet was dropped after the esp32
callback was called, but before it arrives at the phone.  If the phone writes to this register the esp32 will discard older packets and put the next packet >= fromnum in fromradio.
When the esp32 advances fromnum, it will delay doing the notify by 100ms, in the hopes that the notify will never actally need to be sent if the phone is already pulling from fromradio.
  Note: that if the phone ever sees this number decrease, it means the esp32 has rebooted.

Re: queue management
Not all messages are kept in the fromradio queue (filtered based on SubPacket):
* only the most recent Position and User messages for a particular node are kept
* all Data SubPackets are kept
* No WantNodeNum / DenyNodeNum messages are kept
A variable keepAllPackets, if set to true will suppress this behavior and instead keep everything for forwarding to the phone (for debugging)

 */

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

        private val BTM_SERVICE_UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val BTM_FROMRADIO_CHARACTER =
            UUID.fromString("8ba2bcc2-ee02-4a55-a531-c525c5e454d5")
        private val BTM_TORADIO_CHARACTER =
            UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        private val BTM_FROMNUM_CHARACTER =
            UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

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

        // for debug logging only
        private val jsonPrinter = JsonFormat.printer()

        /// This is public only so that SimRadio can bootstrap our message flow
        fun broadcastReceivedFromRadio(context: Context, payload: ByteArray) {
            val intent = Intent(RECEIVE_FROMRADIO_ACTION)
            intent.putExtra(EXTRA_PAYLOAD, payload)
            context.sendBroadcast(intent)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter!!
    }

    // Both of these are created in onCreate()
    private lateinit var device: BluetoothDevice
    private lateinit var safe: SafeBluetooth

    lateinit var sentPacketsLog: DebugLogFile // inited in onCreate

    fun broadcastConnectionChanged(isConnected: Boolean) {
        val intent = Intent("$prefix.CONNECTION_CHANGED")
        intent.putExtra(EXTRA_CONNECTED, isConnected)
        sendBroadcast(intent)
    }

    /// Send a packet/command out the radio link
    private fun handleSendToRadio(p: ByteArray) {

        // For debugging/logging purposes ONLY we convert back into a protobuf for readability
        val proto = MeshProtos.ToRadio.parseFrom(p)

        val json = jsonPrinter.print(proto).replace('\n', ' ')
        info("TODO sending to radio: $json")
        sentPacketsLog.log(json)
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    private fun handleFromRadio(p: ByteArray) {
        broadcastReceivedFromRadio(this, p)
    }

    override fun onCreate() {
        super.onCreate()

        // FIXME, the lifecycle is wrong for jobintentservice, change to a regular service
        // FIXME, let user GUI select which device we are talking to

        // Note: this call does no comms, it just creates the device object (even if the
        // device is off/not connected)
        device = bluetoothAdapter.getRemoteDevice("B4:E6:2D:EA:32:B7")
        // Note this constructor also does no comm
        safe = SafeBluetooth(this, device)

        // FIXME, pass in true for autoconnect - so we will autoconnect whenever the radio
        // comes in range (even if we made this connect call long ago when we got powered on)
        // see https://stackoverflow.com/questions/40156699/which-correct-flag-of-autoconnect-in-connectgatt-of-ble for
        // more info
        // FIXME, can't use sync connect here - because it could take a LONG time
        // FIXME, don't use sync api at all - because our operations are so simple and atomic
        safe.connect(true)

        sentPacketsLog = DebugLogFile(this, "sent_log.json")
    }

    override fun onDestroy() {
        sentPacketsLog.close()
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) { // We have received work to do.  The system or framework is already
        // holding a wake lock for us at this point, so we can just go.
        debug("Executing work: $intent")
        when (intent.action) {
            SEND_TORADIO_ACTION -> handleSendToRadio(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
            else -> TODO("Unhandled case")
        }
    }

}