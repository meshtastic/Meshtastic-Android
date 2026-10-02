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

import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.ExcludedModules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExcludedModuleTest {

    private fun metadata(excluded: Int) = DeviceMetadata.Builder().also { wb -> wb.excluded_modules = excluded }.build()

    @Test
    fun `metadata not read yet excludes nothing`() {
        val none: DeviceMetadata? = null

        assertFalse(none.excludes(ExcludedModules.MQTT_CONFIG))
        assertFalse(none.excludes(ExcludedModules.STATUSMESSAGE_CONFIG))
    }

    @Test
    fun `a set bit excludes only its own module`() {
        val metadata = metadata(ExcludedModules.STATUSMESSAGE_CONFIG.value or ExcludedModules.TAK_CONFIG.value)

        assertTrue(metadata.excludes(ExcludedModules.STATUSMESSAGE_CONFIG))
        assertTrue(metadata.excludes(ExcludedModules.TAK_CONFIG))
        assertFalse(metadata.excludes(ExcludedModules.MESHBEACON_CONFIG))
        assertFalse(metadata.excludes(ExcludedModules.MQTT_CONFIG))
    }

    @Test
    fun `no bits set excludes nothing`() {
        assertFalse(metadata(ExcludedModules.EXCLUDED_NONE.value).excludes(ExcludedModules.MESHBEACON_CONFIG))
    }
}
