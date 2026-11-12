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
package org.meshtastic.feature.map.layers

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BoundedReadTest {

    private val source = ByteArray(20_000) { (it % 251).toByte() }

    @Test
    fun aStreamLongerThanTheLimitIsCutAtTheLimit() {
        val read = ByteArrayInputStream(source).readAtMost(12_345)
        assertContentEquals(source.copyOf(12_345), read)
    }

    @Test
    fun aStreamShorterThanTheLimitIsReadWhole() {
        assertContentEquals(source, ByteArrayInputStream(source).readAtMost(1_000_000))
    }

    @Test
    fun theRestOfTheStreamIsLeftUnread() {
        val stream = ByteArrayInputStream(source)
        stream.readAtMost(100)
        assertEquals(source.size - 100, stream.available())
    }

    @Test
    fun aZeroLimitReadsNothing() {
        assertEquals(0, ByteArrayInputStream(source).readAtMost(0).size)
    }
}
