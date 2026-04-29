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
package org.meshtastic.feature.voiceburst.audio

/**
 * Platform-agnostic interface for audio recording.
 *
 * The Android implementation ([AndroidAudioRecorder]) uses [android.media.AudioRecord]
 * with optimal parameters for Codec2:
 *   - sample rate: 8000 Hz
 *   - encoding: PCM 16-bit
 *   - channel: CHANNEL_IN_MONO
 *   - maximum duration: 1000ms (MVP)
 *
 * Requires the android.permission.RECORD_AUDIO permission.
 * The UI must verify the permission before calling [startRecording].
 */
interface AudioRecorder {

    /**
     * Starts recording.
     *
     * @param onComplete callback invoked on completion with PCM data and the effective duration.
     *                   Invoked on the caller's thread via coroutine.
     * @param onError callback invoked in case of a recording error.
     * @param maxDurationMs maximum duration in milliseconds (default: 1000ms MVP).
     */
    fun startRecording(
        onComplete: (pcmData: ShortArray, durationMs: Int) -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Int = 1000,
    )

    /**
     * Stops the recording early.
     * If no recording is in progress, this is a no-op.
     * On completion, [onComplete] is still called with the data collected so far.
     */
    fun stopRecording()

    /** True if a recording is currently in progress. */
    val isRecording: Boolean
}
