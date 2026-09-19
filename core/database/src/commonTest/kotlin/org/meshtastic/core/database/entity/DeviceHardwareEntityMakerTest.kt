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
package org.meshtastic.core.database.entity

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.HardwareSupportTier
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.supportTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceHardwareEntityMakerTest {

    private val json = Json { ignoreUnknownKeys = true }

    // The registry's shape for the first maker board: flagship supportLevel, activelySupported false.
    private val makerEntry =
        """
        {"hwModel":148,"hwModelSlug":"AXIOMETA_GENESIS_MINI","platformioTarget":"axiometa-genesis-mini",
         "architecture":"esp32-s3","displayName":"Axiometa Genesis Mini","supportLevel":1,
         "activelySupported":false,"isMaker":true,"tags":["Axiometa"]}
        """
            .trimIndent()

    private val communityEntry =
        """
        {"hwModel":1,"hwModelSlug":"TLORA_V2","platformioTarget":"tlora-v2","architecture":"esp32",
         "displayName":"TLora V2","supportLevel":3,"activelySupported":false}
        """
            .trimIndent()

    @Test
    fun `isMaker survives the network to entity to model round trip`() {
        val model = json.decodeFromString<NetworkDeviceHardware>(makerEntry).asEntity().asExternalModel()

        assertTrue(model.isMaker)
        assertEquals(HardwareSupportTier.MAKER, model.supportTier)
    }

    @Test
    fun `an entry without the key reads as not maker`() {
        val model = json.decodeFromString<NetworkDeviceHardware>(communityEntry).asEntity().asExternalModel()

        assertFalse(model.isMaker)
        assertEquals(HardwareSupportTier.COMMUNITY, model.supportTier)
    }
}
