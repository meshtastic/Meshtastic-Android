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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.core.model.Capabilities
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class LoRaSaveConfigTest {

    private fun reported(transparent: Boolean) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = true
            wb.modem_preset = ModemPreset.LONG_FAST
            wb.coding_rate = 5
            wb.hop_limit = 3
            wb.tx_enabled = true
            wb.transparent_mode = transparent
        }
        .build()

    private fun saveAfterEditingHopLimit(reported: Config.LoRaConfig, firmware: String?): Config.LoRaConfig? =
        reported.newBuilder().also { wb -> wb.hop_limit = 5 }.build().toSaveConfig(Capabilities(firmware)).lora

    @Test
    fun `saving another LoRa setting keeps transparent mode on when the device reported it on`() {
        listOf("2.7.0", "2.8.3").forEach { firmware ->
            val saved = saveAfterEditingHopLimit(reported(transparent = true), firmware)

            assertEquals(true, saved?.transparent_mode, "firmware $firmware")
            assertEquals(5, saved?.hop_limit, "firmware $firmware")
        }
    }

    @Test
    fun `saving another LoRa setting does not turn transparent mode on`() {
        val saved = saveAfterEditingHopLimit(reported(transparent = false), "2.8.3")

        assertEquals(false, saved?.transparent_mode)
    }
}
