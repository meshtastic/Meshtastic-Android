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

import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelFrequencyPlanTest {

    private val usLongFast =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
                wb.region = RegionCode.US
            }
            .build()

    private fun named(name: String) =
        Channel(ChannelSettings.Builder().also { wb -> wb.name = name }.build(), usLongFast)

    // Regression: a blank name not resolving to the preset name, or the US row changing.
    @Test
    fun `blank primary on US LongFast sits on slot 20 at 906 point 875`() {
        val plan = Channel(ChannelSettings.Builder().build(), usLongFast).frequencyPlan

        assertEquals(104, plan.numChannels)
        assertEquals(20, plan.slot)
        assertEquals(906.875f, plan.frequencyMHz!!, 0.0001f)
    }

    // Regression: frequencyPlan dropping or replacing the channel name.
    @Test
    fun `a named primary hashes its own name`() {
        assertEquals(52, named("HomeMesh").frequencyPlan.slot)
        assertEquals(23, named("Häme").frequencyPlan.slot)
    }
}
