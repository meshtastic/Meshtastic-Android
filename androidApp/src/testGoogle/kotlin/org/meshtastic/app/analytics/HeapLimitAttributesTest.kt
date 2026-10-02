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
package org.meshtastic.app.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class HeapLimitAttributesTest {

    @Test
    fun `reports both memory classes and the max heap in MB`() {
        val attributes =
            heapLimitAttributes(memoryClassMb = 256, largeMemoryClassMb = 512, maxMemoryBytes = 512L * 1024 * 1024)

        assertEquals(
            mapOf<String, Any>(
                "heap_memory_class_mb" to 256,
                "heap_large_memory_class_mb" to 512,
                "heap_max_memory_mb" to 512L,
            ),
            attributes,
        )
    }

    @Test
    fun `leaves out the memory classes when ActivityManager is unavailable`() {
        val attributes =
            heapLimitAttributes(memoryClassMb = null, largeMemoryClassMb = null, maxMemoryBytes = 192L * 1024 * 1024)

        assertEquals(mapOf<String, Any>("heap_max_memory_mb" to 192L), attributes)
    }

    @Test
    fun `a zero reading is reported rather than dropped`() {
        val attributes = heapLimitAttributes(memoryClassMb = 0, largeMemoryClassMb = 0, maxMemoryBytes = 0)

        assertEquals(
            mapOf<String, Any>(
                "heap_memory_class_mb" to 0,
                "heap_large_memory_class_mb" to 0,
                "heap_max_memory_mb" to 0L,
            ),
            attributes,
        )
    }
}
