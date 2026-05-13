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

import co.touchlab.kermit.Logger
import com.juul.kable.logs.LogEngine

/**
 * Bridges Kable's internal logging to [Kermit][Logger] so BLE lifecycle events (connect, disconnect, subscribe, GATT
 * operations) appear in the standard app logs rather than going to [System.out] via Kable's default
 * [com.juul.kable.logs.SystemLogEngine].
 */
internal object KermitLogEngine : LogEngine {
    override fun verbose(throwable: Throwable?, tag: String, message: String) {
        Logger.v(throwable) { "[$tag] $message" }
    }

    override fun debug(throwable: Throwable?, tag: String, message: String) {
        Logger.d(throwable) { "[$tag] $message" }
    }

    override fun info(throwable: Throwable?, tag: String, message: String) {
        Logger.i(throwable) { "[$tag] $message" }
    }

    override fun warn(throwable: Throwable?, tag: String, message: String) {
        Logger.w(throwable) { "[$tag] $message" }
    }

    override fun error(throwable: Throwable?, tag: String, message: String) {
        Logger.e(throwable) { "[$tag] $message" }
    }

    override fun assert(throwable: Throwable?, tag: String, message: String) {
        Logger.e(throwable) { "[$tag] $message" }
    }
}
