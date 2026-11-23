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
package org.meshtastic.core.testing

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.meshtastic.core.repository.RadioTransport

/** A test double for [RadioTransport] that records its close calls. */
class FakeRadioTransport : RadioTransport {
    private val lock = SynchronizedObject()

    private var closeCalledState = false
    val closeCalled: Boolean
        get() = synchronized(lock) { closeCalledState }

    private var closeCountState = 0
    val closeCount: Int
        get() = synchronized(lock) { closeCountState }

    override suspend fun close() {
        synchronized(lock) {
            closeCalledState = true
            closeCountState++
        }
    }
}
