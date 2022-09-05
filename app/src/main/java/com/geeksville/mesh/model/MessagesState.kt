package com.geeksville.mesh.model

import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus


class MessagesState(private val ui: UIViewModel) : Logging {
    /* We now provide fake messages a via MockInterface
    private val testTexts = listOf(
        DataPacket(
            "+16508765310",
            "I found the cache"
        ),
        DataPacket(
            "+16508765311",
            "Help! I've fallen and I can't get up."
        )
    ) */

    /// This is the inner storage for messages
    private val messagesList = emptyList<DataPacket>().toMutableList()

    // If the following (unused otherwise) line is commented out, the IDE preview window works.
    // if left in the preview always renders as empty.
    val messages =
        object : MutableLiveData<List<DataPacket>>(messagesList) {

        }

    private var contactsList = emptyMap<String?, DataPacket>().toMutableMap()
    val contacts = object : MutableLiveData<MutableMap<String?, DataPacket>>() {

    }

    private fun emptyDataPacket(to: String? = DataPacket.ID_BROADCAST): DataPacket {
        return DataPacket(to, null, 1, DataPacket.ID_LOCAL, 0L)
    }

    // Map each contactId to last DataPacket message sent or received
    // Broadcast: it.to == DataPacket.ID_BROADCAST; Direct Messages: it.to != DataPacket.ID_BROADCAST
    private fun buildContacts() {
        contactsList = messagesList.associateBy {
            if (it.from == DataPacket.ID_LOCAL || it.to == DataPacket.ID_BROADCAST)
                it.to else it.from
        }.toMutableMap()

        val all = DataPacket.ID_BROADCAST // always show contacts, even when empty
        if (contactsList[all] == null)
            contactsList[all] = emptyDataPacket()

        contacts.value = contactsList
    }

    fun setMessages(m: List<DataPacket>) {
        messagesList.clear()
        messagesList.addAll(m)
        messages.value = messagesList
        buildContacts()
    }

    /// add a message our GUI list of past msgs
    fun addMessage(m: DataPacket) {
        debug("Adding message to view id=${m.id}")
        // FIXME - don't just slam in a new list each time, it probably causes extra drawing.

        messagesList.add(m)

        messages.value = messagesList
        buildContacts()
    }

    private fun removeMessages(deleteList: List<DataPacket>) {
        debug("Removing ${deleteList.size} messages from view")

        messagesList.removeAll(deleteList)
        messages.value = messagesList
        buildContacts()
    }

    private fun removeAllMessages() {
        debug("Removing all messages")

        messagesList.clear()
        messages.value = messagesList
        buildContacts()
    }

    fun updateStatus(id: Int, status: MessageStatus) {
        // Super inefficent but this is rare
        debug("Handling message status change $id: $status")

        messagesList.find { it.id == id }?.let { p ->
            // Note: it seems that the service is keeping only a reference to our original packet (so it has already updated p.status)
            // This seems to be an AIDL optimization when both the service and the client are in the same process.  But we still want to trigger
            // a GUI update
            // if (p.status != status) {
            p.status = status
            // Trigger an expensive complete redraw FIXME
            messages.value = messagesList
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

    fun deleteMessages(deleteList: List<DataPacket>) {
        val service = ui.meshService

        if (service != null) {
            try {
                service.deleteMessages(deleteList)
            } catch (ex: RemoteException) {
                debug("Error: ${ex.message}")
            }
        } else {
            debug("Error: No Mesh service")
        }
        removeMessages(deleteList)
    }

    fun deleteAllMessages() {
        val service = ui.meshService
        if (service != null) {
            try {
                service.deleteAllMessages()
            } catch (ex: RemoteException) {
                errormsg("Error: ${ex.message}")
            }
            removeAllMessages()
        }
    }
}
