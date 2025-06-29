package com.geeksville.mesh.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.geeksville.mesh.model.Message
import com.ustadmobile.codec2.Codec2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

val MAX_AUDIO_PAYLOAD = 210
const val sampleRate: Int = 8000
const val channelConfig = AudioFormat.CHANNEL_OUT_MONO
const val audioFormat = AudioFormat.ENCODING_PCM_16BIT
const val maxRecordDurationInSeconds = 8

class Codec2Player {
    val isPlaying = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    suspend fun play(messages: Collection<Message>) {
        isPlaying.set(true)
        val deferred = CompletableDeferred<Unit>()
        playbackThread = thread(start = true, isDaemon = true) {
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioTrack.play()
            var c2con: Long = -1
            var c2mode: Int = -1
            var audioFrameSize = 0
            var codec2FrameSize = 0
            try {
                for (message in messages) {
                    if (message.raw == null) {
                        continue
                    }
                    val data = message.raw
                    val mode = data[3].toInt()
                    if (mode != c2mode) {
                        if (c2con != -1L) {
                            Codec2.destroy(c2con)
                        }
                        c2con = Codec2.create(mode)
                        audioFrameSize = Codec2.getSamplesPerFrame(c2con);
                        codec2FrameSize = Codec2.getBitsSize(c2con);
                        c2mode = mode
                    }
                    val audioBuffer = data.drop(4).chunked(codec2FrameSize).map { chunk ->
                        val decodeBuffer = ShortArray(audioFrameSize);
                        Codec2.decode(c2con, decodeBuffer, chunk.toByteArray())
                        decodeBuffer
                    }.flatMap { it.toList() }.toShortArray()
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException()
                    }
                    audioTrack.write(audioBuffer, 0, audioBuffer.size)
                }
            } catch (_: InterruptedException) {
            } finally {
                audioTrack.stop()
                if (c2con != -1L) {
                    Codec2.destroy(c2con)
                }
                isPlaying.set(false)
                deferred.complete(Unit)
            }
        }
        deferred.await()
    }

    fun stop() {
        playbackThread?.interrupt()
    }
}

class Codec2Recorder {
    private val isRecording = AtomicBoolean(false)
    private var audioData = mutableListOf<Short>()
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun startRecording(
        context: Context,
    ): Result<Unit> {
        // Request audio focus
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        val focusResult = audioManager.requestAudioFocus(audioFocusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return Result.failure(Exception("Failed to gain audio focus"))
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                return Result.failure(Exception("failed to initialise audioRecord with sampleRate: $sampleRate"))
            }
        } catch (e: SecurityException) {
            Log.e("AudioRecord", "SecurityException for sample rate: $sampleRate", e)
            audioRecord?.release()
            return Result.failure(e)
        }

        val maxSamples = sampleRate * maxRecordDurationInSeconds
        val pcmArray = ShortArray(bufferSize)
        isRecording.set(true)
        audioData.clear()
        recordingJob = coroutineScope.launch {
            try {
                audioRecord.startRecording()
                var samplesRead = 0
                while (isRecording.get() && samplesRead < maxSamples) {
                    var bufferPos = 0
                    while (bufferPos < pcmArray.size) {
                        val n = audioRecord.read(pcmArray, bufferPos, bufferSize - bufferPos)
                        if (n < 0) {
                            Log.e("AudioRecord", "Error reading audio data, read=$n")
                            break
                        }
                        bufferPos += n
                    }
                    samplesRead += bufferPos
                    audioData.addAll(pcmArray.take(bufferPos))
                }
                audioRecord.stop()
            } catch (e: Exception) {
                Log.e("AudioRecord", "Recording failed", e)
            } finally {
                audioRecord.release()
            }
        }
        return Result.success(Unit)
    }

    suspend fun stopRecording(): ShortArray {
        isRecording.set(false)
        recordingJob?.cancelAndJoin()
        return audioData.toShortArray()
    }

}


fun modeToBitrate(mode: Int): Int {
    return when (mode) {
        0 -> 3200
        1 -> 2400
        2 -> 1600
        3 -> 1400
        4 -> 1300
        5 -> 1200
        6 -> 700
        7 -> 700
        8 -> 700
        else -> -1
    }
}
