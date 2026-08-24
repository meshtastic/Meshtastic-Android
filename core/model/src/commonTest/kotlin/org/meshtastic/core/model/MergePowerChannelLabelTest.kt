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

class MergePowerChannelLabelTest {

    @Test
    fun setsLabelAndTrims() {
        assertEquals(listOf("Solar"), mergePowerChannelLabel(emptyList(), channelIndex = 0, label = "  Solar  "))
    }

    @Test
    fun padsEarlierChannelsWithBlanks() {
        assertEquals(listOf("", "", "Load"), mergePowerChannelLabel(emptyList(), channelIndex = 2, label = "Load"))
    }

    @Test
    fun keepsExistingChannelsWhenSettingAnother() {
        assertEquals(
            listOf("Solar", "Battery"),
            mergePowerChannelLabel(listOf("Solar"), channelIndex = 1, label = "Battery"),
        )
    }

    @Test
    fun clearingLastLabelDropsTrailingBlanks() {
        assertEquals(listOf("Solar"), mergePowerChannelLabel(listOf("Solar", "Battery"), channelIndex = 1, label = ""))
        assertEquals(emptyList(), mergePowerChannelLabel(listOf("Load"), channelIndex = 0, label = ""))
    }

    @Test
    fun clearingAMiddleLabelKeepsLaterOnesAsBlankSlots() {
        assertEquals(
            listOf("", "Battery"),
            mergePowerChannelLabel(listOf("Solar", "Battery"), channelIndex = 0, label = ""),
        )
    }
}
