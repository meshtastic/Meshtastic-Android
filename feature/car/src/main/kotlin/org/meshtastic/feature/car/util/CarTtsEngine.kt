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
package org.meshtastic.feature.car.util

import android.content.Context
import android.speech.tts.TextToSpeech
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import java.util.Locale
import java.util.UUID

/** TTS engine for reading messages aloud in the car. Uses Android's built-in TTS — no additional permissions needed. */
@Single
class CarTtsEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts =
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    isReady = true
                } else {
                    Logger.w(tag = "CarTtsEngine") { "TTS initialization failed with status: $status" }
                }
            }
    }

    fun readAloud(senderName: String, messageText: String) {
        if (!isReady) {
            Logger.d(tag = "CarTtsEngine") { "TTS not ready, skipping readAloud" }
            return
        }
        val utterance = "$senderName says: $messageText"
        tts?.speak(utterance, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
