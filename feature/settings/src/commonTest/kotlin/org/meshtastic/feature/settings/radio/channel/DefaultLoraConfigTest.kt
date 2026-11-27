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

import org.meshtastic.feature.settings.channel.defaultLoraConfigFor
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultLoraConfigTest {

    private fun device(transparent: Boolean) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.region = RegionCode.EU_868
            wb.tx_enabled = true
            wb.hop_limit = 6
            wb.transparent_mode = transparent
        }
        .build()

    @Test
    fun `reset to defaults keeps transparent mode on when the device has it on`() {
        val reset = defaultLoraConfigFor(device(transparent = true), ModemPreset.LONG_FAST)

        assertEquals(true, reset.transparent_mode)
        assertEquals(RegionCode.EU_868, reset.region)
        assertEquals(true, reset.tx_enabled)
    }

    @Test
    fun `reset to defaults does not turn transparent mode on`() {
        assertEquals(false, defaultLoraConfigFor(device(transparent = false), ModemPreset.LONG_FAST).transparent_mode)
        assertEquals(false, defaultLoraConfigFor(null, ModemPreset.LONG_FAST).transparent_mode)
    }
}
