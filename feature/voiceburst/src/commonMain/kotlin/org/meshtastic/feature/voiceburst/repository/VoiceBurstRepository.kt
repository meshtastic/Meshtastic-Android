/*
 * Copyright (c) 2026 Chris7X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.voiceburst.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.feature.voiceburst.model.VoiceBurstPayload

/**
 * Platform-agnostic interface for sending and receiving Voice Bursts.
 *
 * The Android implementation ([AndroidVoiceBurstRepository]) uses [RadioController]
 * to send [DataPacket] with dataType = [VoiceBurstPayload.PORT_NUM].
 */
interface VoiceBurstRepository {

    /**
     * Feature flag: Voice Burst experimental enabled by user.
     * Default: false. Readable as StateFlow for UI reactivity.
     */
    val isFeatureEnabled: StateFlow<Boolean>

    /**
     * Enable or disable the Voice Burst feature.
     * Persists in DataStore.
     */
    suspend fun setFeatureEnabled(enabled: Boolean)

    /**
     * Sends a [VoiceBurstPayload] to the recipient node via BLE/RadioController
     * and saves it in the local DB to show it in the chat.
     *
     * @param payload    the already encoded payload
     * @param contactKey contact key in the format "<channel>!<nodeId>" (e.g. "0!42424243", "8!42424243")
     * @return true if the packet was delivered to RadioController, false otherwise
     */
    suspend fun sendBurst(payload: VoiceBurstPayload, contactKey: String): Boolean

    /**
     * Flow of bursts received from other nodes.
     * Emits every time a DataPacket with PORT_NUM = 256 arrives and
     * the payload is decodable.
     */
    val incomingBursts: Flow<VoiceBurstPayload>

    /**
     * Reads Codec2 bytes from disk given the relative path saved in [Message.audioFilePath].
     * Used to play a previously received/sent voice message.
     *
     * @param relativePath path relative to filesDir, e.g. "voice_bursts/12345678.c2"
     * @return ByteArray with Codec2 bytes, or null if the file doesn't exist or I/O error
     */
    fun readAudioFile(relativePath: String): ByteArray?
}
