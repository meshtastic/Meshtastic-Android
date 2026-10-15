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
package org.meshtastic.feature.connections.ui.components

import org.meshtastic.proto.Config.LoRaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoraSummaryTest {

    private fun lora(usePreset: Boolean, preset: LoRaConfig.ModemPreset, region: LoRaConfig.RegionCode) =
        LoRaConfig.Builder()
            .also {
                it.use_preset = usePreset
                it.modem_preset = preset
                it.region = region
            }
            .build()

    @Test
    fun `summary is absent while the lora config is unknown`() {
        assertNull(loraSummary(null, "Long Fast", "United States", "Custom"))
    }

    @Test
    fun `summary carries the preset label and region label`() {
        val config = lora(true, LoRaConfig.ModemPreset.LONG_FAST, LoRaConfig.RegionCode.US)
        assertEquals(
            LoraSummary("Long Fast", "United States"),
            loraSummary(config, "Long Fast", "United States", "Custom"),
        )
    }

    @Test
    fun `summary shows custom when no preset is in use`() {
        val config = lora(false, LoRaConfig.ModemPreset.LONG_FAST, LoRaConfig.RegionCode.US)
        assertEquals(
            LoraSummary("Custom", "United States"),
            loraSummary(config, "Long Fast", "United States", "Custom"),
        )
    }

    @Test
    fun `summary omits an unset region`() {
        val config = lora(true, LoRaConfig.ModemPreset.LONG_FAST, LoRaConfig.RegionCode.UNSET)
        assertEquals(LoraSummary("Long Fast", null), loraSummary(config, "Long Fast", "Unset", "Custom"))
    }

    @Test
    fun `summary falls back to enum names when the schema has no label`() {
        val config = lora(true, LoRaConfig.ModemPreset.SHORT_FAST, LoRaConfig.RegionCode.EU_868)
        assertEquals(LoraSummary("SHORT_FAST", "EU_868"), loraSummary(config, null, null, "Custom"))
    }
}
