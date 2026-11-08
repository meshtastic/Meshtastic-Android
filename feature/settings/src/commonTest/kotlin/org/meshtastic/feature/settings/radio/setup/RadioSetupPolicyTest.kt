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
package org.meshtastic.feature.settings.radio.setup

import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LoRaRegionPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioSetupPolicyTest {

    private fun lora(
        region: RegionCode = RegionCode.UNSET,
        preset: ModemPreset = ModemPreset.LONG_FAST,
        usePreset: Boolean = true,
    ) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.region = region
            wb.modem_preset = preset
            wb.use_preset = usePreset
        }
        .build()

    private val usMap =
        LoRaRegionPresetMap.Builder()
            .also { wb ->
                wb.groups =
                    listOf(
                        LoRaPresetGroup.Builder()
                            .also { gb ->
                                gb.presets = listOf(ModemPreset.LONG_FAST, ModemPreset.LONG_TURBO)
                                gb.default_preset = ModemPreset.LONG_FAST
                            }
                            .build(),
                    )
                wb.region_groups =
                    listOf(
                        LoRaRegionPresets.Builder()
                            .also { rb ->
                                rb.region = RegionCode.US
                                rb.group_index = 0
                            }
                            .build(),
                    )
            }
            .build()

    @Test
    fun `licensed operators skip the name page`() {
        assertEquals(
            listOf(RadioSetupStep.INTRO, RadioSetupStep.REGION, RadioSetupStep.PRESET, RadioSetupStep.DONE),
            radioSetupSteps(isLicensed = true),
        )
        assertEquals(RadioSetupStep.entries, radioSetupSteps(isLicensed = false))
    }

    @Test
    fun `a factory fresh US radio moves to Long Turbo`() {
        val edited = lora().withSetupRegion(RegionCode.US, usMap)

        assertEquals(RegionCode.US, edited.region)
        assertEquals(ModemPreset.LONG_TURBO, edited.modem_preset)
    }

    @Test
    fun `custom modem settings survive a region change until a preset is picked`() {
        val custom = lora(preset = ModemPreset.MEDIUM_FAST, usePreset = false)

        assertFalse(custom.withSetupRegion(RegionCode.EU_868, null).use_preset)
        assertTrue(custom.withSetupPreset(ModemPreset.LONG_FAST).use_preset)
    }

    @Test
    fun `only a region or preset change needs a write`() {
        val stored = lora(region = RegionCode.US)

        assertFalse(loraSetupChanged(stored, stored))
        assertTrue(loraSetupChanged(stored, stored.withSetupRegion(RegionCode.EU_868, null)))
        assertTrue(loraSetupChanged(stored, stored.withSetupPreset(ModemPreset.SHORT_FAST)))
        assertTrue(loraSetupChanged(stored, lora(region = RegionCode.US, usePreset = false)))
    }
}
