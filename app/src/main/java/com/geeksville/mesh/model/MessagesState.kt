package com.geeksville.mesh.model

import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.utf8


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
        object : MutableLiveData<List<DataPacket>>(if (isEmulator) testTexts else listOf()) {

        }

    /// add a message our GUI list of past msgs
    fun addMessage(m: DataPacket) {
        // FIXME - don't just slam in a new list each time, it probably causes extra drawing.
        messages.value = messages.value!! + m
    }

    fun updateStatus(id: Int, status: MessageStatus) {
        // Super inefficent but this is rare
        val msgs = messages.value!!

        msgs.find { it.id == id }?.let { p ->
            if (p.status != status) {
                p.status = status
                // Trigger an expensive complete redraw FIXME
                messages.value = msgs
            }
        }
    }

    /// Send a message and added it to our GUI log
    fun sendMessage(str: String, dest: String = DataPacket.ID_BROADCAST) {

        val service = ui.meshService
        val p = DataPacket(
            dest,
            str.toByteArray(utf8),
            MeshProtos.Data.Type.CLEAR_TEXT_VALUE
        )
        if (service != null)
            try {
                service.send(p)
            } catch (ex: RemoteException) {
                p.errorMessage = "Error: ${ex.message}"
            }
        else
            p.errorMessage = "Error: No Mesh service"

        addMessage(p)
    }
}
