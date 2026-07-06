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
package org.meshtastic.feature.settings.radio.channel

import org.meshtastic.feature.settings.radio.channel.component.ChannelPskEditState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelConfigScreenTest {
    @Test
    fun `psk edit state round-trips through saveable flags for all boolean combinations`() {
        for (canGenerate in listOf(false, true)) {
            for (generated in listOf(false, true)) {
                for (edited in listOf(false, true)) {
                    val original =
                        ChannelPskEditState(
                            canGeneratePskForName = canGenerate,
                            generatedPskForName = generated,
                            pskExplicitlyEdited = edited,
                        )
                    val restored = original.toSaveableFlags().toChannelPskEditState()
                    assertEquals(original, restored)
                }
            }
        }
    }

    @Test
    fun `move forward shifts element to higher index`() {
        val list = mutableListOf("A", "B", "C", "D")
        list.move(fromIndex = 1, toIndex = 3)
        assertEquals(listOf("A", "C", "D", "B"), list)
    }

    @Test
    fun `move backward shifts element to lower index`() {
        val list = mutableListOf("A", "B", "C", "D")
        list.move(fromIndex = 3, toIndex = 1)
        assertEquals(listOf("A", "D", "B", "C"), list)
    }

    @Test
    fun `replaceAll clears and refills with given size and value`() {
        val list =
            mutableListOf(
                ChannelPskEditState(canGeneratePskForName = true),
                ChannelPskEditState(generatedPskForName = true),
            )
        val fillValue = ChannelPskEditState(pskExplicitlyEdited = true)
        list.replaceAll(size = 3, value = fillValue)
        assertEquals(3, list.size)
        assertTrue(list.all { it == fillValue })
    }
}
