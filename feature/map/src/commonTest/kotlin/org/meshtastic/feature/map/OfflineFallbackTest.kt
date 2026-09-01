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
package org.meshtastic.feature.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineFallbackTest {

    @Test
    fun `switches when the network is down and a fallback exists`() {
        assertTrue(shouldAutoUseOfflineBasemap(networkAvailable = false, hasOfflineBasemap = true))
    }

    @Test
    fun `stays put when the network is down but there is nothing to fall back to`() {
        assertFalse(shouldAutoUseOfflineBasemap(networkAvailable = false, hasOfflineBasemap = false))
    }

    @Test
    fun `stays put while the network is up regardless of a fallback`() {
        assertFalse(shouldAutoUseOfflineBasemap(networkAvailable = true, hasOfflineBasemap = true))
        assertFalse(shouldAutoUseOfflineBasemap(networkAvailable = true, hasOfflineBasemap = false))
    }
}
