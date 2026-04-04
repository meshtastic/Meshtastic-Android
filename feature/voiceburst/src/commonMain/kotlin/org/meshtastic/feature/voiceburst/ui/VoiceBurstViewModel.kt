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
 */
package org.meshtastic.feature.voiceburst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.feature.voiceburst.audio.AudioPlayer
import org.meshtastic.feature.voiceburst.audio.AudioRecorder
import org.meshtastic.feature.voiceburst.codec.Codec2Encoder
import org.meshtastic.feature.voiceburst.model.VoiceBurstError
import org.meshtastic.feature.voiceburst.model.VoiceBurstPayload
import org.meshtastic.feature.voiceburst.model.VoiceBurstState
import org.meshtastic.feature.voiceburst.repository.VoiceBurstRepository

private const val TAG = "VoiceBurstViewModel"

/**
 * ViewModel handling the lifecycle and orchestration of Voice Burst messaging.
 *
 * Full pipeline:
 *   MIC â†’ [AudioRecorder] â†’ PCM â†’ [Codec2Encoder.encode] â†’ bytes â†’ [VoiceBurstRepository.sendBurst]
 *   RADIO â†’ [VoiceBurstRepository.incomingBursts] â†’ bytes â†’ [Codec2Encoder.decode] â†’ PCM â†’ [AudioPlayer]
 *
 * Rate limiting is enforced: minimum [RATE_LIMIT_MS] between consecutive bursts.
 *
 * @param repository  Manages feature flags, sending, and receiving bursts.
 * @param encoder     Codec2 encoding/decoding engine (may be a sine-wave stub).
 * @param audioPlayer Plays the decoded PCM audio.
 * @param audioRecorder Records audio from the microphone (8kHz mono PCM16).
 * @param destNodeId  Target hex ID for the conversation (e.g., "0!42424243").
 */
@KoinViewModel
class VoiceBurstViewModel(
    private val repository: VoiceBurstRepository,
    private val encoder: Codec2Encoder,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    @InjectedParam private val destNodeId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceBurstState>(VoiceBurstState.Idle)
    val state = _state.asStateFlow()

    /**
     * Observable state of the Voice Burst feature flag from DataStore.
     */
    val isFeatureEnabled = repository.isFeatureEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val incomingBursts = repository.incomingBursts

    /**
     * Path of the audio file currently being played.
     * Observed by the UI to display the correct Play/Stop icon.
     * Null when no audio is active.
     */
    val playingFilePath = audioPlayer.playingFilePath

    private val resolvedNodeId: String = run {
        val channelDigit = destNodeId.firstOrNull()?.digitToIntOrNull()
        if (channelDigit != null) destNodeId.substring(1) else destNodeId
    }

    /** UI timer Job: updates elapsedMs every 100ms during manual recording. */
    private var uiTimerJob: Job? = null

    private var lastSentTimestamp = 0L

    init {
        // Listen for incoming radio bursts and trigger automatic playback.
        repository.incomingBursts
            .onEach { payload -> onBurstReceived(payload) }
            .catch { e -> Logger.w(tag = TAG) { "Incoming bursts flow error: ${e.message}" } }
            .launchIn(viewModelScope)
    }

    // â”€â”€â”€ Receiver-side logic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun onBurstReceived(payload: VoiceBurstPayload) {
        Logger.i(tag = TAG) {
            "Burst received from ${payload.senderNodeId}: " +
                "${payload.durationMs}ms, ${payload.audioData.size} bytes"
        }
        _state.update { VoiceBurstState.Received(payload) }

        val pcmData = encoder.decode(payload.audioData)
        if (pcmData == null || pcmData.isEmpty()) {
            Logger.e(tag = TAG) { "Decoding failed â€” no PCM samples to play" }
            _state.update { VoiceBurstState.Idle }
            return
        }

        Logger.d(tag = TAG) { "Starting playback: ${pcmData.size} samples @ ${SAMPLE_RATE_HZ}Hz" }
        // Empty filePath indicates autoplay (not triggered by a specific UI bubble).
        audioPlayer.play(pcmData, filePath = "") {
            if (_state.value is VoiceBurstState.Received) {
                _state.update { VoiceBurstState.Idle }
            }
        }
    }

    // â”€â”€â”€ Sender-side (PTT) recording â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Initiates microphone recording if the state machine is [Idle].
     * Enforces the [RATE_LIMIT_MS] guard before starting.
     *
     * Note: Permissions (RECORD_AUDIO) must be verified by the UI before calling.
     */
    fun startRecording() {
        if (_state.value !is VoiceBurstState.Idle) return

        // Rate limit check
        val now = Clock.System.now().toEpochMilliseconds()
        val remaining = RATE_LIMIT_MS - (now - lastSentTimestamp)
        if (remaining > 0) {
            Logger.w(tag = TAG) { "Rate limit active: waiting ${remaining / 1000}s" }
            _state.update { VoiceBurstState.Error(VoiceBurstError.RATE_LIMITED) }
            viewModelScope.launch {
                delay(remaining)
                if (_state.value is VoiceBurstState.Error) {
                    _state.update { VoiceBurstState.Idle }
                }
            }
            return
        }

        Logger.d(tag = TAG) { "Starting PTT recording for $resolvedNodeId (dest=$destNodeId)" }
        _state.update { VoiceBurstState.Recording(elapsedMs = 0L) }

        // Start UI timer: updates the elapsed time for the PTT progress indicator.
        val startTime = Clock.System.now().toEpochMilliseconds()
        uiTimerJob = viewModelScope.launch {
            while (_state.value is VoiceBurstState.Recording) {
                delay(TIMER_TICK_MS)
                val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
                _state.update {
                    if (it is VoiceBurstState.Recording)
                        VoiceBurstState.Recording(elapsedMs = minOf(elapsed, MAX_DURATION_MS.toLong()))
                    else it
                }
            }
        }

        // Engage the hardware audio recorder.
        audioRecorder.startRecording(
            onComplete = { pcmData, durationMs ->
                uiTimerJob?.cancel()
                uiTimerJob = null
                Logger.d(tag = TAG) { "Recording finished: ${pcmData.size} samples, ${durationMs}ms" }
                onRecordingComplete(pcmData, durationMs)
            },
            onError = { error ->
                uiTimerJob?.cancel()
                uiTimerJob = null
                Logger.e(tag = TAG) { "Hardware recording error: ${error.message}" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.ENCODING_FAILED) }
            },
            maxDurationMs = MAX_DURATION_MS,
        )
    }

    /**
     * Mandates the recorder to stop recording immediately.
     * The recorder will then trigger the completion callback with the partial PCM data.
     */
    fun stopRecording() {
        if (_state.value !is VoiceBurstState.Recording) return
        Logger.d(tag = TAG) { "Manual recording stop triggered" }
        uiTimerJob?.cancel()
        uiTimerJob = null
        audioRecorder.stopRecording()
    }

    // â”€â”€â”€ Encoding and Dispatch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    internal fun onRecordingComplete(pcmData: ShortArray, durationMs: Int) {
        _state.update { VoiceBurstState.Encoding }

        viewModelScope.launch {
            val audioBytes = encoder.encode(pcmData)
            if (audioBytes == null) {
                Logger.e(tag = TAG) { "Codec2 encoding failed (check JNI)" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.ENCODING_FAILED) }
                return@launch
            }

            if (encoder.isStub) {
                Logger.w(tag = TAG) { "Running with Codec2 stub â€” transmission will not be intelligible" }
            } else {
                Logger.i(tag = TAG) { "Enc JNI Success: ${pcmData.size} samples â†’ ${audioBytes.size} bytes" }
            }

            val payload = VoiceBurstPayload(
                durationMs = durationMs.toShort(),
                audioData = audioBytes,
            )

            _state.update { VoiceBurstState.Sending }
            val success = repository.sendBurst(payload, destNodeId)

            if (success) {
                lastSentTimestamp = Clock.System.now().toEpochMilliseconds()
                Logger.i(tag = TAG) { "Voice Burst broadcasted: ${audioBytes.size} bytes, ${durationMs}ms" }
                _state.update { VoiceBurstState.Sent }
                delay(SENT_DISPLAY_MS)
                _state.update { VoiceBurstState.Idle }
            } else {
                Logger.e(tag = TAG) { "Failed to send burst to $destNodeId" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.SEND_FAILED) }
            }
        }
    }

    /**
     * Resets the internal state machine back to Idle.
     */
    fun reset() {
        _state.update { VoiceBurstState.Idle }
    }

    /**
     * Plays a previously recorded voice message from the local storage.
     * Invoked when tapping a Voice Burst capsule in the message list.
     *
     * @param relativePath Disk path relative to the app's files directory.
     *                     Format: "voice_bursts/<uuid>.c2"
     */
    fun playBurst(relativePath: String) {
        if (audioPlayer.isPlaying) {
            val wasPlayingThis = audioPlayer.playingFilePath.value == relativePath
            audioPlayer.stop()
            // Second tap on the same bubble = stop only.
            if (wasPlayingThis) return
        }
        viewModelScope.launch {
            val codec2Bytes = repository.readAudioFile(relativePath)
            if (codec2Bytes == null || codec2Bytes.isEmpty()) {
                Logger.e(tag = TAG) { "Audio file missing: $relativePath" }
                return@launch
            }
            val pcmData = encoder.decode(codec2Bytes)
            if (pcmData == null || pcmData.isEmpty()) {
                Logger.e(tag = TAG) { "Failed to decode audio file: $relativePath" }
                return@launch
            }
            Logger.d(tag = TAG) { "Streaming from file: $relativePath (${pcmData.size} samples)" }
            audioPlayer.play(pcmData, filePath = relativePath)
        }
    }

    companion object {
        const val RATE_LIMIT_MS   = 30_000L
        const val MAX_DURATION_MS = 1000
        const val SAMPLE_RATE_HZ  = 8000
        private const val SENT_DISPLAY_MS = 1500L
        private const val TIMER_TICK_MS   = 100L
    }
}
