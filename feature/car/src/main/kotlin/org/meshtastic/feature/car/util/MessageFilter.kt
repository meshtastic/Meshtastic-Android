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

import org.koin.core.annotation.Factory

@Factory
class MessageFilter {

    fun shouldDisplay(message: String, dataType: Int): Boolean {
        if (dataType != DATA_TYPE_TEXT) return false
        if (message.isBlank()) return false
        if (isEmojiOnly(message)) return false
        return true
    }

    fun validateOutgoing(message: String): ValidationResult {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return if (bytes.size <= MAX_OUTGOING_BYTES) {
            ValidationResult.Valid
        } else {
            ValidationResult.TooLong(bytes.size, MAX_OUTGOING_BYTES)
        }
    }

    private fun isEmojiOnly(text: String): Boolean {
        val stripped = text.replace(EMOJI_REGEX, "").trim()
        return stripped.isEmpty()
    }

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class TooLong(val actualBytes: Int, val maxBytes: Int) : ValidationResult()
    }

    companion object {
        private const val MAX_OUTGOING_BYTES = 237
        private const val DATA_TYPE_TEXT = 1
        private val EMOJI_REGEX = Regex("[\\p{So}\\p{Sk}\\p{Cs}\\s]+")
    }
}
