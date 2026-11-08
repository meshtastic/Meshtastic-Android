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

import org.meshtastic.core.model.heroImageFile
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceHardwareEntityImagesTest {

    private fun heroFor(images: List<String>?) = DeviceHardwareEntity(
        activelySupported = true,
        architecture = "nrf52840",
        displayName = "Board",
        hwModel = 9,
        hwModelSlug = "BOARD",
        images = images,
        platformioTarget = "board",
        requiresDfu = null,
        supportLevel = 1,
        tags = null,
    )
        .asExternalModel()
        .heroImageFile

    @Test
    fun `a single illustration with models shows the illustration`() {
        assertEquals("a.svg", heroFor(listOf("a.svg", "a.usdz", "a.glb")))
    }

    @Test
    fun `a cased illustration with models shows the cased illustration`() {
        val images = listOf("a.svg", "a_case.svg", "a.usdz", "a.glb", "a_case.usdz", "a_case.glb")

        assertEquals("a_case.svg", heroFor(images))
    }

    @Test
    fun `entries without models are unchanged`() {
        assertEquals("a_case.svg", heroFor(listOf("a.svg", "a_case.svg")))
        assertEquals("a.svg", heroFor(listOf("a.svg")))
        assertEquals(null, heroFor(null))
    }
}
