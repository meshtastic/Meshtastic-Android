/*
 * Copyright (c) 2026 Meshtastic LLC
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

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "AndroidAudioRecorder"

/**
 * Android implementation of [AudioRecorder] based on [AudioRecord].
 *
 * Fixed parameters for Codec2 700B:
 *   - Source:   MIC
 *   - Rate:     8000 Hz
 *   - Channel:  CHANNEL_IN_MONO
 *   - Encoding: PCM_16BIT
 *
 * PREREQUISITE: the caller must have obtained android.permission.RECORD_AUDIO
 * before invoking [startRecording].
 *
 * Stop behaviour: [stopRecording] sets a volatile flag that causes the read
 * loop to exit gracefully, then [onComplete] is called with the data collected
 * so far. The coroutine is NOT cancelled — cancellation would prevent onComplete
 * from being called.
 */
class AndroidAudioRecorder(
    private val scope: CoroutineScope,
) : AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Volatile flag: true while we want the read loop to keep running.
    @Volatile private var keepRecording = false

    override val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override fun startRecording(
        onComplete: (pcmData: ShortArray, durationMs: Int) -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Int,
    ) {
        if (isRecording) {
            Logger.w(tag = TAG) { "startRecording called while already recording — ignored" }
            return
        }

        val sampleRate    = 8000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat   = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError(IllegalStateException("AudioRecord not supported on this device"))
            return
        }

        // Buffer sized for maxDurationMs + 20% margin
        val totalSamples = (sampleRate * maxDurationMs / 1000.0 * 1.2).toInt()
        val bufferSize   = maxOf(minBufferSize, totalSamples * 2 /* bytes per short */)

        try {
            @Suppress("MissingPermission") // Permission verified by the caller
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (e: SecurityException) {
            onError(e)
            return
        }

        val record = audioRecord ?: run {
            onError(IllegalStateException("AudioRecord not initialized"))
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onError(IllegalStateException("AudioRecord initialization failed"))
            record.release()
            audioRecord = null
            return
        }

        keepRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            val maxSamples = sampleRate * maxDurationMs / 1000
            val pcmBuffer  = ShortArray(maxSamples)
            var samplesRead = 0
            val startTime   = System.currentTimeMillis()

            try {
                record.startRecording()
                Logger.d(tag = TAG) { "Recording started (max ${maxDurationMs}ms, ${sampleRate}Hz mono PCM16)" }

                // Read loop: exits when keepRecording is false OR buffer is full.
                while (keepRecording && samplesRead < pcmBuffer.size) {
                    val chunkSize = minOf(minBufferSize / 2, pcmBuffer.size - samplesRead)
                    val read = record.read(pcmBuffer, samplesRead, chunkSize)
                    if (read < 0) {
                        Logger.e(tag = TAG) { "AudioRecord.read error: $read" }
                        break
                    }
                    samplesRead += read
                }

                val durationMs = (System.currentTimeMillis() - startTime)
                    .toInt().coerceAtMost(maxDurationMs)

                Logger.d(tag = TAG) { "Recording complete: $samplesRead samples, ${durationMs}ms" }

                // Always call onComplete — even on early stop — so the ViewModel
                // can encode and send whatever was recorded.
                onComplete(pcmBuffer.copyOf(samplesRead), durationMs)

            } catch (e: Exception) {
                Logger.e(e, tag = TAG) { "Error during recording" }
                onError(e)
            } finally {
                runCatching { record.stop() } // guard against IllegalStateException on early-stop
                record.release()
                audioRecord = null
                keepRecording = false
            }
        }
    }

    override fun stopRecording() {
        if (!keepRecording) return
        Logger.d(tag = TAG) { "Early stop requested — draining remaining samples" }
        // Signal the read loop to exit. The job itself is NOT cancelled so that
        // onComplete is still called with the data collected up to this point.
        keepRecording = false
        // Stop AudioRecord so the next record.read() returns immediately.
        audioRecord?.stop()
    }
}
