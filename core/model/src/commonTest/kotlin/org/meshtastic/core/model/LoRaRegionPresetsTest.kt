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

import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LoRaRegionPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoRaRegionPresetsTest {

    // group 0: standard US presets (unlicensed). group 1: a licensed-only region.
    private val map =
        LoRaRegionPresetMap(
            groups =
            listOf(
                LoRaPresetGroup(
                    presets = listOf(ModemPreset.LONG_FAST, ModemPreset.LONG_SLOW, ModemPreset.SHORT_FAST),
                    default_preset = ModemPreset.LONG_FAST,
                    licensed_only = false,
                ),
                LoRaPresetGroup(
                    presets = listOf(ModemPreset.TINY_FAST, ModemPreset.TINY_SLOW),
                    default_preset = ModemPreset.TINY_FAST,
                    licensed_only = true,
                ),
            ),
            region_groups =
            listOf(
                LoRaRegionPresets(region = RegionCode.US, group_index = 0),
                LoRaRegionPresets(region = RegionCode.UA_433, group_index = 1),
            ),
        )

    @Test
    fun `null map imposes no constraint`() {
        val map: LoRaRegionPresetMap? = null
        assertNull(map.constraintFor(RegionCode.US))
        // repair leaves the current preset untouched when unconstrained
        assertEquals(ModemPreset.LONG_FAST, map.repairPresetFor(RegionCode.US, ModemPreset.LONG_FAST))
    }

    @Test
    fun `region absent from region_groups is unconstrained`() {
        assertNull(map.constraintFor(RegionCode.JP))
        assertEquals(ModemPreset.SHORT_TURBO, map.repairPresetFor(RegionCode.JP, ModemPreset.SHORT_TURBO))
    }

    @Test
    fun `out of range group_index is treated as unconstrained`() {
        val broken =
            LoRaRegionPresetMap(
                groups = emptyList(),
                region_groups = listOf(LoRaRegionPresets(region = RegionCode.US, group_index = 5)),
            )
        assertNull(broken.constraintFor(RegionCode.US))
    }

    @Test
    fun `a group with no presets is treated as unconstrained`() {
        // A degenerate/malformed group must degrade to the full preset list, not an empty picker.
        val empty =
            LoRaRegionPresetMap(
                groups = listOf(LoRaPresetGroup(presets = emptyList(), licensed_only = false)),
                region_groups = listOf(LoRaRegionPresets(region = RegionCode.US, group_index = 0)),
            )
        assertNull(empty.constraintFor(RegionCode.US))
        assertEquals(ModemPreset.LONG_FAST, empty.repairPresetFor(RegionCode.US, ModemPreset.LONG_FAST))
    }

    @Test
    fun `constraint resolves the region's preset group`() {
        val constraint = map.constraintFor(RegionCode.US)
        assertEquals(listOf(ModemPreset.LONG_FAST, ModemPreset.LONG_SLOW, ModemPreset.SHORT_FAST), constraint?.presets)
        assertEquals(ModemPreset.LONG_FAST, constraint?.defaultPreset)
        assertFalse(constraint?.licensedOnly == true)
    }

    @Test
    fun `unlicensed group is never gated`() {
        val constraint = map.constraintFor(RegionCode.US)!!
        assertFalse(constraint.isGated(isLicensed = false))
        assertFalse(constraint.isGated(isLicensed = true))
    }

    @Test
    fun `licensed_only group is gated only for an unlicensed operator`() {
        val constraint = map.constraintFor(RegionCode.UA_433)!!
        assertTrue(constraint.licensedOnly)
        assertTrue(constraint.isGated(isLicensed = false))
        assertFalse(constraint.isGated(isLicensed = true))
    }

    @Test
    fun `repair keeps a preset that is legal in the region`() {
        assertEquals(ModemPreset.SHORT_FAST, map.repairPresetFor(RegionCode.US, ModemPreset.SHORT_FAST))
    }

    @Test
    fun `repair swaps an illegal preset to the region default`() {
        // SHORT_TURBO is not in the US group, so it should snap to the group's default (LONG_FAST).
        assertEquals(ModemPreset.LONG_FAST, map.repairPresetFor(RegionCode.US, ModemPreset.SHORT_TURBO))
    }

    @Test
    fun `repair falls back to the first legal preset when the default is not in the group`() {
        val oddMap =
            LoRaRegionPresetMap(
                groups =
                listOf(
                    LoRaPresetGroup(
                        presets = listOf(ModemPreset.MEDIUM_FAST, ModemPreset.MEDIUM_SLOW),
                        default_preset = ModemPreset.LONG_FAST, // not in presets
                        licensed_only = false,
                    ),
                ),
                region_groups = listOf(LoRaRegionPresets(region = RegionCode.US, group_index = 0)),
            )
        assertEquals(ModemPreset.MEDIUM_FAST, oddMap.repairPresetFor(RegionCode.US, ModemPreset.SHORT_FAST))
    }
}
