package com.geeksville.mesh.model

import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus


class MessagesState(private val ui: UIViewModel) : Logging {
    private val testTexts = listOf(
        DataPacket(
            "+16508765310",
            "I found the cache"
        ),
        DataPacket(
            "+16508765311",
            "Help! I've fallen and I can't get up."
        )
    )

    // If the following (unused otherwise) line is commented out, the IDE preview window works.
    // if left in the preview always renders as empty.
    val messages =
        object : MutableLiveData<List<DataPacket>>(if (isEmulator) testTexts else emptyList()) {

        }

    /// add a message our GUI list of past msgs
    fun addMessage(m: DataPacket) {
        debug("Adding message to view id=${m.id}")
        // FIXME - don't just slam in a new list each time, it probably causes extra drawing.

        // FIXME - possible kotlin bug in 1.3.72 - it seems that if we start with the (globally shared) emptyList,
        // then adding items are affecting that shared list rather than a copy.   This was causing aliasing of
        // recentDataPackets with messages.value in the GUI.  So if the current list is empty we are careful to make a new list
        messages.value = if (messages.value.isNullOrEmpty())
            listOf(m)
        else
            messages.value!! + m
    }

    /// clean messages from our UI
    fun cleanMessages() {
        debug("cleaning messages")
        messages.value = emptyList()
    }

    fun updateStatus(id: Int, status: MessageStatus) {
        // Super inefficent but this is rare
        debug("Handling message status change $id: $status")
        val msgs = messages.value!!

        msgs.find { it.id == id }?.let { p ->
            // Note: it seems that the service is keeping only a reference to our original packet (so it has already updated p.status)
            // This seems to be an AIDL optimization when both the service and the client are in the same process.  But we still want to trigger
            // a GUI update
            // if (p.status != status) {
            p.status = status
            // Trigger an expensive complete redraw FIXME
            messages.value = msgs
            // }
        }
    }

    /// Send a message and added it to our GUI log
    fun sendMessage(str: String, dest: String = DataPacket.ID_BROADCAST) {

        val service = ui.meshService
        val p = DataPacket(dest, str)

        if (service != null)
            try {
                service.send(p)
            } catch (ex: RemoteException) {
                p.errorMessage = "Error: ${ex.message}"
            }
        else
            p.errorMessage = "Error: No Mesh service"

        // FIXME - why is the first time we are called p is already in the list at this point?
        addMessage(p)
    }
}
