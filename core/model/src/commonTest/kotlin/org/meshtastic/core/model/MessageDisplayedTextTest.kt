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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDisplayedTextTest {

    private val message =
        Message(
            uuid = 1L,
            receivedTime = 0L,
            node = Node(num = 1),
            text = "Hola",
            fromLocal = false,
            time = "10:00",
            read = true,
            status = MessageStatus.RECEIVED,
            routingError = 0,
            packetId = 1,
            emojis = emptyList(),
            snr = 0f,
            rssi = 0,
            hopsAway = 0,
            replyId = null,
        )

    @Test
    fun displaysOriginalWhenNoTranslation() {
        assertEquals("Hola", message.displayedText())
    }

    @Test
    fun displaysTranslationWhenToggledOn() {
        val translated = message.copy(translatedText = "Hello", showTranslated = true)
        assertEquals("Hello", translated.displayedText())
    }

    @Test
    fun displaysOriginalWhenTranslationToggledOff() {
        val toggledOff = message.copy(translatedText = "Hello", showTranslated = false)
        assertEquals("Hola", toggledOff.displayedText())
    }

    @Test
    fun displaysOriginalWhileSearchingEvenWhenTranslationShown() {
        val translated = message.copy(translatedText = "Hello", showTranslated = true)
        assertEquals("Hola", translated.displayedText(searching = true))
    }
}
