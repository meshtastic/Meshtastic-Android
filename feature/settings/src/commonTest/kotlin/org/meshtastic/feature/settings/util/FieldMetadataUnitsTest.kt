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

import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.unit_dbm
import org.meshtastic.core.resources.unit_khz
import org.meshtastic.core.resources.unit_meters
import org.meshtastic.proto.Config
import org.meshtastic.proto.FieldMetadata
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.bandwidth
import org.meshtastic.proto.ble_threshold
import org.meshtastic.proto.broadcast_smart_minimum_distance
import org.meshtastic.proto.tx_power
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FieldMetadataUnitsTest {

    @Test
    fun theUnitComesFromTheSchema_notTheCallSite() {
        assertEquals(Res.string.unit_dbm, Config.LoRaConfig.tx_power.unitLabelRes)
        assertEquals(Res.string.unit_dbm, ModuleConfig.PaxcounterConfig.ble_threshold.unitLabelRes)
        assertEquals(Res.string.unit_meters, Config.PositionConfig.broadcast_smart_minimum_distance.unitLabelRes)
        assertEquals(Res.string.unit_khz, Config.LoRaConfig.bandwidth.unitLabelRes)
    }

    @Test
    fun aFieldWithNoUnit_namesNone() {
        assertNull(FieldMetadata.Builder().build().unitLabelRes)
    }

    @Test
    fun aSymbolTheAppDoesNotName_rendersNothingRatherThanTheSymbol() {
        assertNull(FieldMetadata.Builder().unit("furlong").build().unitLabelRes)
    }
}
