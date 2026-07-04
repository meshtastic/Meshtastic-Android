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
package org.meshtastic.core.common.util

/** Represents the system's preferred measurement system. */
enum class MeasurementSystem {
    METRIC,
    IMPERIAL,
}

/** returns the system's preferred measurement system. */
expect fun getSystemMeasurementSystem(): MeasurementSystem

/** Returns the device's current locale as a 2-letter ISO 639-1 language code (e.g. "en", "es", "fr"). */
expect fun currentLocaleCode(): String

/**
 * Returns the device locale as a CMP resource qualifier string. Examples: "pt-rBR", "zh-rCN", "fr" (no region when not
 * specified). Use this to construct locale-qualified file resource paths like "files-$qualifier/docs/...".
 */
expect fun currentLocaleQualifier(): String
