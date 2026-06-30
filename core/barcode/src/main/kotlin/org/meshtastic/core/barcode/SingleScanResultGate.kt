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
package org.meshtastic.core.barcode

import java.util.concurrent.atomic.AtomicBoolean

/** Allows a scanner session to complete exactly once, either with a scan result or a dismiss result. */
internal class SingleScanResultGate {
    private val delivered = AtomicBoolean(false)

    /**
     * Attempts to deliver a scanner result once.
     *
     * The gate is consumed before [onResult] runs. If [onResult] throws, later delivery attempts are still ignored.
     */
    fun tryDeliver(result: String?, onResult: (String?) -> Unit): Boolean {
        if (!delivered.compareAndSet(false, true)) return false
        onResult(result)
        return true
    }
}
