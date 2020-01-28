package com.geeksville.mesh

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
class RadioInterfaceService : Service(), Logging {

    companion object {
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

    val service get() = safe.gatt.services.find { it.uuid == BTM_SERVICE_UUID }!!

    private lateinit var fromRadio: BluetoothGattCharacteristic
    private lateinit var fromNum: BluetoothGattCharacteristic

    lateinit var sentPacketsLog: DebugLogFile // inited in onCreate

    // for debug logging only
    private val jsonPrinter = JsonFormat.printer()

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

    /// Attempt to read from the fromRadio mailbox, if data is found broadcast it to android apps
    private fun doReadFromRadio() {
        safe.asyncReadCharacteristic(fromRadio) {
            val b = it.getOrThrow().value

            if (b.isNotEmpty()) {
                debug("Received ${b.size} bytes from radio")
                handleFromRadio(b)

                // Queue up another read, until we run out of packets
                doReadFromRadio()
            } else
                debug("Done reading from radio, fromradio is empty")
        }
    }

    override fun onCreate() {
        super.onCreate()

        info("Creating radio interface service")

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
        // FIXME, broadcast connection lost/gained via broadcastConnecionChanged
        safe.asyncConnect(true) { connRes ->
            // This callback is invoked after we are connected 

            connRes.getOrThrow() // FIXME, instead just try to reconnect?
            info("Connected to radio!")

            // FIXME - no need to discover services, instead just hardwire the characteristics (like we do for toRadio)
            safe.asyncDiscoverServices { discRes ->
                discRes.getOrThrow() // FIXME, instead just try to reconnect?

                // we begin by setting our MTU size as high as it can go
                safe.asyncRequestMtu(512) { mtuRes ->
                    mtuRes.getOrThrow()
                    
                    fromRadio = service.getCharacteristic(BTM_FROMRADIO_CHARACTER)
                    fromNum = service.getCharacteristic(BTM_FROMNUM_CHARACTER)

                    doReadFromRadio()
                }
            }
        }

        sentPacketsLog = DebugLogFile(this, "sent_log.json")
    }

    override fun onDestroy() {
        info("Destroying radio interface service")
        sentPacketsLog.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder;
    }

    private val binder = object : IRadioInterfaceService.Stub() {
        override fun sendToRadio(a: ByteArray) {
            debug("queuing ${a.size} bytes to radio")

            // Note: we generate a new characteristic each time, because we are about to
            // change the data and we want the data stored in the closure
            val toRadio = BluetoothGattCharacteristic(
                BTM_FROMRADIO_CHARACTER,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                PERMISSION_WRITE
            )

            toRadio.value = a
            safe.asyncWriteCharacteristic(toRadio) {
                it.getOrThrow() // FIXME, handle the error better
            }
        }
    }
}