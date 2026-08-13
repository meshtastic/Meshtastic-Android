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
package org.meshtastic.core.model.util

import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChannelSetReplacementTest {

    @Test
    fun `replacement plan normalizes padding and duplicates before slot validation`() {
        val primary = ChannelSettings(name = "Primary")
        val uniqueSecondaries = (1..7).map { ChannelSettings(name = "Secondary $it") }
        val rawSettings = listOf(primary, ChannelSettings(), primary) + uniqueSecondaries

        val plan =
            ChannelSet(settings = rawSettings)
                .toChannelReplacementPlan(
                    currentSettings = emptyList(),
                    fallbackLoraConfig = null,
                    requirePrimary = true,
                )

        assertEquals(listOf(primary) + uniqueSecondaries, plan.normalizedSettings)
        assertEquals((0..7).toList(), plan.channelWrites.map(Channel::index))
        assertEquals(Channel.Role.PRIMARY, plan.channelWrites.first().role)
        assertEquals(List(7) { Channel.Role.SECONDARY }, plan.channelWrites.drop(1).map(Channel::role))
    }

    @Test
    fun `replacement plan rejects empty profile channel set before producing writes`() {
        assertFailsWith<IllegalArgumentException> {
            ChannelSet(settings = emptyList())
                .toChannelReplacementPlan(
                    currentSettings = listOf(ChannelSettings(name = "Existing")),
                    fallbackLoraConfig = null,
                    requirePrimary = true,
                )
        }
    }
}
