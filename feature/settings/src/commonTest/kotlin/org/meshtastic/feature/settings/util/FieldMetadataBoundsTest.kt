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
import org.meshtastic.proto.FieldMetadata
import org.meshtastic.proto.FieldMetadataRegistry
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.current
import org.meshtastic.proto.hop_limit
import org.meshtastic.proto.red
import org.meshtastic.proto.spread_factor
import org.meshtastic.proto.tx_power
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FieldMetadataBoundsTest {

    @Test
    fun hopLimit_readsItsBoundsFromTheRegistry() {
        assertEquals(0..7, Config.LoRaConfig.hop_limit.intRange)
    }

    @Test
    fun ambientLighting_readsItsBoundsFromTheRegistry() {
        assertEquals(0..31, ModuleConfig.AmbientLightingConfig.current.intRange)
        assertEquals(0..255, ModuleConfig.AmbientLightingConfig.red.intRange)
    }

    @Test
    fun aFieldWithoutBounds_isUnbounded() {
        assertNull(FieldMetadata.Builder().build().intRange)
    }

    @Test
    fun spreadFactor_readsItsBoundsFromTheRegistry() {
        assertEquals(5..12, Config.LoRaConfig.spread_factor.intRange)
    }

    @Test
    fun boundsBeyondTheIntDomain_areUnbounded() {
        val metadata = FieldMetadata.Builder().min_value(1e18).max_value(2e18).build()
        assertNull(metadata.intRange)
    }

    @Test
    fun aMaxAboveIntMax_clampsInsteadOfSaturatingToAPoint() {
        val metadata = FieldMetadata.Builder().min_value(0.0).max_value(4294967295.0).build()
        assertEquals(0..Int.MAX_VALUE, metadata.intRange)
    }

    @Test
    fun fractionalBounds_roundInward() {
        val metadata = FieldMetadata.Builder().min_value(1.5).max_value(2.5).build()
        assertEquals(2..2, metadata.intRange)
    }

    @Test
    fun aOneSidedBound_isUnbounded() {
        assertNull(FieldMetadata.Builder().max_value(7.0).build().intRange)
    }

    @Test
    fun unit_isCarriedAlongsideTheBounds() {
        assertEquals("dBm", Config.LoRaConfig.tx_power.unit)
    }

    @Test
    fun companionAccessor_isTheRegistryEntry() {
        assertEquals(FieldMetadataRegistry.get("meshtastic.Config.LoRaConfig", 8), Config.LoRaConfig.hop_limit)
    }
}
