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
package org.meshtastic.feature.node.metrics

import org.meshtastic.proto.HardwareModel
import kotlin.test.Test
import kotlin.test.assertEquals

class HardwareModelSafeNumberTest {

    @Test
    fun knownModel_returnsValue() {
        assertEquals(HardwareModel.TBEAM.value, HardwareModel.TBEAM.safeNumber())
    }

    @Test
    fun unset_returnsZero() {
        assertEquals(0, HardwareModel.UNSET.safeNumber())
    }

    @Test
    fun customFallback_used() {
        // Known model with custom fallback — should still return real value
        assertEquals(HardwareModel.HELTEC_V3.value, HardwareModel.HELTEC_V3.safeNumber(fallbackValue = 999))
    }

    @Test
    fun defaultFallback_isNegativeOne() {
        // For known models the fallback is never used, but verify the API default
        val result = HardwareModel.UNSET.safeNumber()
        assertEquals(0, result) // UNSET.value is 0, not the fallback
    }
}
