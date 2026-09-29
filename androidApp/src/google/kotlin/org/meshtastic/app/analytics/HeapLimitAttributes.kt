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

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * RUM attributes for the heap ceilings the device gives this process, all in the MB `ActivityManager.memoryClass` uses.
 * The memory classes are left out when `ActivityManager` is unavailable.
 */
internal fun heapLimitAttributes(
    memoryClassMb: Int?,
    largeMemoryClassMb: Int?,
    maxMemoryBytes: Long,
): Map<String, Any> = buildMap {
    memoryClassMb?.let { put("heap_memory_class_mb", it) }
    largeMemoryClassMb?.let { put("heap_large_memory_class_mb", it) }
    put("heap_max_memory_mb", maxMemoryBytes / BYTES_PER_MB)
}
