/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

package org.meshtastic.core.proto

import org.meshtastic.proto.DiyOnlyOption

/**
 * DEMO: Wire 6 natively generates `@DiyOnlyOption(true)` on proto fields that carry
 * `[(meshtastic.diy_only) = true]` — zero custom build code needed.
 *
 * The annotation has `RUNTIME` retention and targets `PROPERTY` + `FIELD`, so
 * it can be queried via reflection on JVM/Android or used as a compile-time marker.
 *
 * Generated code in `Config.PositionConfig`:
 * ```kotlin
 * @DiyOnlyOption(true)
 * @field:WireField(tag = 8, ...)
 * public val rx_gpio: Int = 0,
 * ```
 *
 * See: https://github.com/meshtastic/protobufs/pull/905
 */
object FieldMetadataDemo {

    /**
     * The [DiyOnlyOption] annotation is available at compile time for reference.
     * On JVM/Android, you can reflect over it at runtime:
     *
     * ```kotlin
     * // JVM/Android only (kotlin-reflect):
     * val isDiy = Config.PositionConfig::rx_gpio
     *     .findAnnotation<DiyOnlyOption>()?.value ?: false
     * ```
     *
     * For KMP-safe access without reflection, use a constants map:
     */
    val diyOnlyFields: Set<String> = setOf("rx_gpio", "tx_gpio")

    /**
     * UI gating — hide settings that are diy_only when device isn't DIY:
     *
     * ```kotlin
     * if ("rx_gpio" !in FieldMetadataDemo.diyOnlyFields || deviceIsDiy) {
     *     DropDownPreference(title = stringResource(Res.string.gps_receive_gpio), ...)
     * }
     * ```
     */
    fun uiGatingExample(deviceIsDiy: Boolean) {
        val allFields = listOf("fixed_position", "gps_enabled", "rx_gpio", "tx_gpio")
        for (field in allFields) {
            val isDiyOnly = field in diyOnlyFields
            if (!isDiyOnly || deviceIsDiy) {
                println("Showing $field setting")
            }
        }
    }
}