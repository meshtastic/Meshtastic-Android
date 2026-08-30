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
package org.meshtastic.core.ble

import com.juul.kable.logs.Logging

/**
 * Verbosity for Kable's internal BLE logging. Wraps [Logging.Level] so callers and the Koin DI graph don't leak Kable
 * types into modules that don't directly depend on Kable.
 */
enum class BleLogLevel {
    /** Only failures (lowest noise; default in release builds). */
    Warnings,

    /** [Warnings] plus connect / disconnect / subscribe / GATT operation events. */
    Events,

    /** [Events] plus a hex dump of every read/write/notify payload. Very noisy. */
    Data,
}

/**
 * Format for Kable's internal BLE log entries.
 *
 * [Compact] keeps each entry on a single line — strongly preferred for `adb logcat`, grep, and bug reports. [Multiline]
 * pretty-prints across several lines, which is harder to read in tooling.
 */
enum class BleLogFormat {
    Compact,
    Multiline,
}

/**
 * Verbosity and formatting controls for Kable's internal BLE logging.
 *
 * @see BleLogLevel for the verbosity scale.
 * @see BleLogFormat for the layout choices.
 */
data class BleLoggingConfig(val level: BleLogLevel, val format: BleLogFormat = BleLogFormat.Compact) {
    companion object {
        /** Quiet defaults suitable for release builds — only warnings, single-line. */
        val Release: BleLoggingConfig = BleLoggingConfig(level = BleLogLevel.Warnings)

        /** Verbose defaults suitable for debug builds — every BLE event, single-line. */
        val Debug: BleLoggingConfig = BleLoggingConfig(level = BleLogLevel.Events)
    }
}

internal fun BleLogLevel.toKable(): Logging.Level = when (this) {
    BleLogLevel.Warnings -> Logging.Level.Warnings
    BleLogLevel.Events -> Logging.Level.Events
    BleLogLevel.Data -> Logging.Level.Data
}

internal fun BleLogFormat.toKable(): Logging.Format = when (this) {
    BleLogFormat.Compact -> Logging.Format.Compact
    BleLogFormat.Multiline -> Logging.Format.Multiline
}

/** Applies this [BleLoggingConfig] to a Kable `logging { }` block, routing through [KermitLogEngine]. */
internal fun Logging.applyConfig(config: BleLoggingConfig, identifier: String? = null) {
    engine = KermitLogEngine
    level = config.level.toKable()
    format = config.format.toKable()
    if (identifier != null) {
        this.identifier = identifier
    }
}
