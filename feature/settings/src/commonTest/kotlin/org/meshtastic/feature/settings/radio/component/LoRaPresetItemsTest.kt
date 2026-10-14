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
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoRaPresetItemsTest {
    private val caps = Capabilities("2.8.0", forceEnableAll = false)
    private val restricted =
        setOf(
            ModemPreset.LITE_FAST,
            ModemPreset.LITE_SLOW,
            ModemPreset.NARROW_FAST,
            ModemPreset.NARROW_SLOW,
            ModemPreset.TINY_FAST,
            ModemPreset.TINY_SLOW,
        )

    private fun presets(
        region: RegionCode?,
        constraint: RegionPresetConstraint? = null,
        selected: ModemPreset = ModemPreset.LONG_FAST,
    ) = buildPresetItems(constraint, false, selected, caps, emptyMap(), unmappedRegion = region).map { it.value }

    @Test
    fun withoutMap_hidesLiteNarrowAndTinyOutsideTheirRegions() {
        val items = presets(RegionCode.US)

        assertTrue(items.none { it in restricted })
        assertTrue(ModemPreset.LONG_FAST in items)
    }

    @Test
    fun withoutMap_showsEachRestrictedFamilyInItsOwnRegion() {
        assertTrue(presets(RegionCode.EU_866).containsAll(listOf(ModemPreset.LITE_FAST, ModemPreset.LITE_SLOW)))
        assertTrue(presets(RegionCode.EU_N_868).containsAll(listOf(ModemPreset.NARROW_FAST, ModemPreset.NARROW_SLOW)))
        assertTrue(presets(RegionCode.ITU1_2M).containsAll(listOf(ModemPreset.TINY_FAST, ModemPreset.TINY_SLOW)))
        assertFalse(ModemPreset.TINY_FAST in presets(RegionCode.EU_866))
    }

    @Test
    fun withMap_theStaticFallbackDoesNotApply() {
        val constraint =
            RegionPresetConstraint(
                presets = listOf(ModemPreset.LONG_FAST, ModemPreset.TINY_FAST),
                defaultPreset = ModemPreset.LONG_FAST,
                licensedOnly = false,
            )

        assertEquals(listOf(ModemPreset.LONG_FAST, ModemPreset.TINY_FAST), presets(RegionCode.US, constraint))
    }

    @Test
    fun withoutRegionToApply_everySupportedPresetIsListed() {
        assertTrue(presets(null).containsAll(restricted))
    }

    @Test
    fun withoutMap_aSelectedHiddenPresetStaysListedDisabled() {
        val items =
            buildPresetItems(null, false, ModemPreset.TINY_FAST, caps, emptyMap(), unmappedRegion = RegionCode.US)
        val tiny = items.single { it.value == ModemPreset.TINY_FAST }

        assertFalse(tiny.enabled)
    }

    @Test
    fun frequencyField_showsThreeDecimalsWithADotSeparator() {
        assertEquals("906.875", formatFrequencyMhz(906.875f))
        assertEquals("869.400", formatFrequencyMhz(869.4f))
        assertEquals("2403.000", formatFrequencyMhz(2403f))
    }
}
