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
package org.meshtastic.feature.voiceburst.model

/**
 * States of the lifecycle of a Voice Burst.
 *
 * Valid transitions:
 *   Idle → Recording → Encoding → Sending → Sent
 *   Any state → Error
 *   Any state → Unsupported (if incompatible preset detected)
 */
sealed class VoiceBurstState {
    /** Ready to record. No operation in progress. */
    data object Idle : VoiceBurstState()

    /**
     * Audio recording in progress.
     * @param elapsedMs milliseconds elapsed since the start of recording.
     */
    data class Recording(val elapsedMs: Long = 0L) : VoiceBurstState()

    /** Codec2 encoding in progress (fast operation, typically < 50ms). */
    data object Encoding : VoiceBurstState()

    /**
     * Packet queued for sending via RadioController.
     * Enters this state if the node is temporarily disconnected.
     */
    data object Queued : VoiceBurstState()

    /** Packet delivered to the node via BLE. Waiting for ACK (optional). */
    data object Sending : VoiceBurstState()

    /** Send completed successfully. */
    data object Sent : VoiceBurstState()

    /** Burst received from remote. Ready for playback. */
    data class Received(val payload: VoiceBurstPayload) : VoiceBurstState()

    /**
     * Error during the burst lifecycle.
     * @param reason cause of the error.
     */
    data class Error(val reason: VoiceBurstError) : VoiceBurstState()

    /**
     * Feature not available in the current context.
     * Shown when: slow sub-1GHz preset, feature flag disabled,
     * or recipient does not support the portnum.
     */
    data class Unsupported(val reason: String) : VoiceBurstState()
}

/** Error causes for [VoiceBurstState.Error]. */
enum class VoiceBurstError {
    /** Microphone permission denied by the user. */
    MICROPHONE_PERMISSION_DENIED,

    /** Error during audio recording. */
    RECORDING_FAILED,

    /** Codec2 encoding failed (stub or library not available). */
    ENCODING_FAILED,

    /** Destination node not reachable. */
    SEND_FAILED,

    /** Rate limit: too many bursts in a short time. Wait at least 30s. */
    RATE_LIMITED,
}
