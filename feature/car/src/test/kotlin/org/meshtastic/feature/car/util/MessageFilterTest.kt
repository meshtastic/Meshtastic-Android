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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageFilterTest {

    private val filter = MessageFilter()

    @Test
    fun `shouldDisplay returns true for normal text`() {
        assertTrue(filter.shouldDisplay("Hello world", DATA_TYPE_TEXT))
    }

    @Test
    fun `shouldDisplay returns false for blank messages`() {
        assertFalse(filter.shouldDisplay("", DATA_TYPE_TEXT))
        assertFalse(filter.shouldDisplay("   ", DATA_TYPE_TEXT))
    }

    @Test
    fun `shouldDisplay returns false for non-text data types`() {
        assertFalse(filter.shouldDisplay("Hello", 0))
        assertFalse(filter.shouldDisplay("Hello", 2))
    }

    @Test
    fun `shouldDisplay returns false for emoji-only messages`() {
        assertFalse(filter.shouldDisplay("👍", DATA_TYPE_TEXT))
        assertFalse(filter.shouldDisplay("🎉🎊", DATA_TYPE_TEXT))
    }

    @Test
    fun `shouldDisplay returns true for text with emoji`() {
        assertTrue(filter.shouldDisplay("Hello 👋", DATA_TYPE_TEXT))
    }

    @Test
    fun `validateOutgoing returns Valid for short messages`() {
        val result = filter.validateOutgoing("Hello")
        assertIs<MessageFilter.ValidationResult.Valid>(result)
    }

    @Test
    fun `validateOutgoing returns TooLong for oversized messages`() {
        val longMessage = "a".repeat(238)
        val result = filter.validateOutgoing(longMessage)
        assertIs<MessageFilter.ValidationResult.TooLong>(result)
        assertEquals(238, result.actualBytes)
        assertEquals(237, result.maxBytes)
    }

    @Test
    fun `validateOutgoing accounts for multi-byte UTF-8`() {
        // Each emoji is 4 bytes in UTF-8
        val emojiMessage = "🎉".repeat(60) // 240 bytes
        val result = filter.validateOutgoing(emojiMessage)
        assertIs<MessageFilter.ValidationResult.TooLong>(result)
    }

    companion object {
        private const val DATA_TYPE_TEXT = 1
    }
}
