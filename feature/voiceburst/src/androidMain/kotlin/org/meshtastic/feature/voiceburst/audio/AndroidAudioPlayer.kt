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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AndroidAudioPlayer"

/**
 * Android implementation of [AudioPlayer].
 *
 * Key implementation notes:
 *  - BUG: MODE_STATIC with bufferSize < minBufferSize -> STATE_NO_STATIC_DATA (state=2) -> silence.
 *    FIX: bufferSize = maxOf(minBufferSize, pcmBytes) ALWAYS, even in static mode.
 *  - Using MODE_STREAM: simpler and avoids the STATE_NO_STATIC_DATA issue.
 *    For 1 second at 8kHz (16000 bytes) MODE_STREAM is more than adequate.
 *  - USAGE_MEDIA -> main speaker (not earpiece).
 *  - [playingFilePath] StateFlow to sync play/stop icons in the UI.
 */
class AndroidAudioPlayer(
    private val scope: CoroutineScope,
) : AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var playingJob: Job? = null

    private val _playingFilePath = MutableStateFlow<String?>(null)
    override val playingFilePath: StateFlow<String?> = _playingFilePath.asStateFlow()

    override val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    override fun play(pcmData: ShortArray, filePath: String, onComplete: () -> Unit) {
        // If already playing, stop before starting a new track
        if (isPlaying) {
            Logger.d(tag = TAG) { "Stopping previous track before starting new one" }
            stopInternal()
        }

        if (pcmData.isEmpty()) {
            Logger.w(tag = TAG) { "PCM data is empty -- skipping playback" }
            onComplete()
            return
        }

        val sampleRate    = SAMPLE_RATE_HZ
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (minBufferSize <= 0) {
            Logger.e(tag = TAG) { "getMinBufferSize error: $minBufferSize" }
            onComplete()
            return
        }

        // CRITICAL: bufferSize must always be >= minBufferSize.
        // With MODE_STATIC, if bufferSize < minBufferSize -> state=STATE_NO_STATIC_DATA=2 -> silence.
        // MODE_STREAM is used for simplicity and robustness.
        val pcmBytes   = pcmData.size * Short.SIZE_BYTES
        val bufferSize = maxOf(minBufferSize, pcmBytes)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(audioEncoding)
            .setChannelMask(channelConfig)
            .build()

        val track = try {
            AudioTrack(attrs, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        } catch (e: Exception) {
            Logger.e(e, tag = TAG) { "Failed to create AudioTrack" }
            onComplete()
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Logger.e(tag = TAG) { "AudioTrack not initialized: state=${track.state} (expected ${AudioTrack.STATE_INITIALIZED})" }
            track.release()
            onComplete()
            return
        }

        audioTrack = track
        _playingFilePath.value = filePath.ifEmpty { null }

        playingJob = scope.launch(Dispatchers.IO) {
            try {
                // MODE_STREAM: call play() FIRST, then write() for streaming
                track.play()
                Logger.d(tag = TAG) { "Playback started: ${pcmData.size} samples @ ${sampleRate}Hz" }

                val written = track.write(pcmData, 0, pcmData.size)
                if (written < 0) {
                    Logger.e(tag = TAG) { "write() error: $written" }
                } else {
                    Logger.d(tag = TAG) { "Write complete: $written samples" }
                    // Wait for the DAC to drain all samples in the buffer
                    val drainMs = written.toLong() * 1000L / sampleRate + DRAIN_GUARD_MS
                    kotlinx.coroutines.delay(drainMs)
                }
            } catch (e: Exception) {
                Logger.e(e, tag = TAG) { "Playback error" }
            } finally {
                releaseTrack(track)
                _playingFilePath.value = null
                scope.launch(Dispatchers.Main) { onComplete() }
            }
        }
    }

    override fun stop() {
        if (!isPlaying && playingJob?.isActive != true) return
        Logger.d(tag = TAG) { "Stopping playback" }
        stopInternal()
    }

    private fun stopInternal() {
        playingJob?.cancel()
        playingJob = null
        audioTrack?.let { releaseTrack(it) }
        _playingFilePath.value = null
    }

    private fun releaseTrack(track: AudioTrack) {
        try { track.stop() } catch (_: Exception) {}
        try { track.flush() } catch (_: Exception) {}
        track.release()
        if (audioTrack === track) audioTrack = null
    }

    companion object {
        private const val SAMPLE_RATE_HZ  = 8000
        private const val DRAIN_GUARD_MS  = 150L  // extra margin for DAC drain
    }
}
