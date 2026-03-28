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
@file:Suppress("LoopWithTooManyJumpStatements")

package org.meshtastic.core.takserver

import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

internal class CoTXmlFrameBuffer(private val maxMessageSize: Long = DEFAULT_MAX_TAK_MESSAGE_SIZE) {
    private val buffer = Buffer()
    private var discardingUntilNextEvent = false

    fun append(data: ByteArray): List<String> {
        buffer.write(data)

        if (discardingUntilNextEvent) {
            val nextEventIdx = buffer.indexOf(EVENT_START_BYTES)
            if (nextEventIdx == -1L) {
                // Keep the last few bytes in case the start tag is split across chunks
                if (buffer.size > EVENT_START_BYTES.size) {
                    buffer.skip(buffer.size - EVENT_START_BYTES.size.toLong())
                }
                return emptyList()
            }
            discardingUntilNextEvent = false
            buffer.skip(nextEventIdx)
        }

        val messages = mutableListOf<String>()

        while (true) {
            val startIdx = buffer.indexOf(EVENT_START_BYTES)
            if (startIdx == -1L) {
                if (buffer.size > maxMessageSize) {
                    buffer.clear()
                    discardingUntilNextEvent = true
                }
                break
            }

            if (startIdx > 0L) {
                buffer.skip(startIdx)
            }

            val endIdx = buffer.indexOf(EVENT_END_BYTES)
            if (endIdx == -1L) {
                if (buffer.size > maxMessageSize) {
                    buffer.clear()
                    discardingUntilNextEvent = true
                }
                break
            }

            val xmlEnd = endIdx + EVENT_END_BYTES.size
            if (xmlEnd <= maxMessageSize) {
                messages += buffer.readUtf8(xmlEnd)
            } else {
                buffer.skip(xmlEnd)
            }
        }

        return messages
    }

    fun clear() {
        buffer.clear()
        discardingUntilNextEvent = false
    }

    companion object {
        private val EVENT_START_BYTES = "<event".encodeUtf8()
        private val EVENT_END_BYTES = "</event>".encodeUtf8()
        private const val DEFAULT_MAX_TAK_MESSAGE_SIZE = 8L * 1024 * 1024
    }
}
