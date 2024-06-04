package com.geeksville.mesh.model

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.repository.datastore.ChannelSetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class Contact(
    val contactKey: String,
    val shortName: String,
    val longName: String,
    val lastMessageTime: String?,
    val lastMessageText: String?,
    val unreadCount: Int,
    val messageCount: Int,
    val isMuted: Boolean,
)

// return time if within 24 hours, otherwise date/time
internal fun getShortDateTime(time: Long): String? {
    val date = if (time != 0L) Date(time) else return null
    val isWithin24Hours = System.currentTimeMillis() - date.time <= 24 * 60 * 60 * 1000L

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val app: Application,
    private val nodeDB: NodeDB,
    channelSetRepository: ChannelSetRepository,
    private val packetRepository: PacketRepository,
) : ViewModel(), Logging {

    val contactList = combine(
        packetRepository.getContacts(),
        channelSetRepository.channelSetFlow,
        packetRepository.getContactSettings(),
    ) { contacts, channelSet, settings ->
        // Add empty channel placeholders (always show Broadcast contacts, even when empty)
        val placeholder = (0 until channelSet.settingsCount).associate { ch ->
            val contactKey = "$ch${DataPacket.ID_BROADCAST}"
            val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
            contactKey to Packet(0L, 1, contactKey, 0L, data)
        }

        (placeholder + contacts).values.map { packet ->
            val data = packet.data
            val contactKey = packet.contact_key

            // Determine if this is my message (originated on this device)
            val fromLocal = data.from == DataPacket.ID_LOCAL
            val toBroadcast = data.to == DataPacket.ID_BROADCAST

            // grab usernames from NodeInfo
            val node = nodeDB.nodes.value[if (fromLocal) data.to else data.from]

            val shortName = node?.user?.shortName ?: app.getString(R.string.unknown_node_short_name)
            val longName = if (toBroadcast) {
                channelSet.getChannel(data.channel)?.name ?: app.getString(R.string.channel_name)
            } else {
                node?.user?.longName ?: app.getString(R.string.unknown_username)
            }

            Contact(
                contactKey = contactKey,
                shortName = if (toBroadcast) "${data.channel}" else shortName,
                longName = longName,
                lastMessageTime = getShortDateTime(data.time),
                lastMessageText = if (fromLocal) data.text else "$shortName: ${data.text}",
                unreadCount = 0,
                messageCount = packetRepository.getMessageCount(contactKey),
                isMuted = settings[contactKey]?.isMuted == true,
            )
        }
    }.asLiveData()

    fun setMuteUntil(contacts: List<String>, until: Long) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.setMuteUntil(contacts, until)
    }

    fun deleteContacts(contacts: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteContacts(contacts)
    }
}