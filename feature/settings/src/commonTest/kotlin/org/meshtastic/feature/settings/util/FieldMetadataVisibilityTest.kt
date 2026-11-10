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
package org.meshtastic.feature.settings.util

import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.hop_limit
import org.meshtastic.proto.metadata
import org.meshtastic.proto.rx_gpio
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldMetadataVisibilityTest {

    private val diyOnly = Config.PositionConfig.rx_gpio

    @Test
    fun aDiyOnlyFieldIsHiddenOnAnOemFixedGpsBoard() {
        assertTrue(diyOnly.diy_only == true, "the schema no longer marks rx_gpio diy_only")
        assertTrue(HardwareModel.TBEAM.metadata?.oem_fixed_gps == true, "the schema no longer flags the T-Beam")
        assertFalse(diyOnly.offeredOn(isOemFixedGps = true, holdsValue = false))
    }

    @Test
    fun aDiyOnlyFieldIsShownOnABoardWithAnAddOnGps() {
        assertTrue(HardwareModel.HELTEC_V3.metadata?.oem_fixed_gps != true, "the schema flags the Heltec V3")
        assertTrue(diyOnly.offeredOn(isOemFixedGps = false, holdsValue = false))
    }

    @Test
    fun aDiyOnlyFieldHoldingAValueStaysVisibleOnAnOemFixedGpsBoard() {
        assertTrue(diyOnly.offeredOn(isOemFixedGps = true, holdsValue = true))
    }

    @Test
    fun anOrdinaryFieldIsShownEverywhere() {
        assertTrue(Config.LoRaConfig.hop_limit.offeredOn(isOemFixedGps = true, holdsValue = false))
    }
}
