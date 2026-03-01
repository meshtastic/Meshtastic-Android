package org.meshtastic.feature.messaging.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController

class FakeRadioController : RadioController {
    
    // Mutable state flows so we can manipulate them in our tests
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    // Track sent packets to assert in tests
    val sentPackets = mutableListOf<DataPacket>()
    val favoritedNodes = mutableListOf<Int>()
    val sentSharedContacts = mutableListOf<Int>()

    override suspend fun sendMessage(packet: DataPacket) {
        sentPackets.add(packet)
    }

    override suspend fun favoriteNode(nodeNum: Int) {
        favoritedNodes.add(nodeNum)
    }

    override suspend fun sendSharedContact(nodeNum: Int) {
        sentSharedContacts.add(nodeNum)
    }

    // --- Helper methods for testing ---
    
    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
