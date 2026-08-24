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

/** A test double for [RadioTransport] that tracks sent data. */
class FakeRadioTransport : RadioTransport {
    private val lock = SynchronizedObject()
    private val mutableSentData = mutableListOf<ByteArray>()
    val sentData: List<ByteArray>
        get() = synchronized(lock) { mutableSentData.map { it.copyOf() } }

    /** Clears the recorded payload history without changing transport lifecycle state. */
    fun clearSentData() {
        synchronized(lock) { mutableSentData.clear() }
    }

    /** Clears the recorded heartbeat without changing transport lifecycle state. */
    fun clearKeepAlive() {
        synchronized(lock) { keepAliveCalledState = false }
    }

    private var closeCalledState = false
    val closeCalled: Boolean
        get() = synchronized(lock) { closeCalledState }

    private var closeCountState = 0
    val closeCount: Int
        get() = synchronized(lock) { closeCountState }

    private var keepAliveCalledState = false
    val keepAliveCalled: Boolean
        get() = synchronized(lock) { keepAliveCalledState }

    override fun handleSendToRadio(p: ByteArray): Boolean = synchronized(lock) {
        if (closeCalledState) return@synchronized false
        mutableSentData.add(p.copyOf())
        true
    }

    override fun keepAlive() {
        synchronized(lock) { if (!closeCalledState) keepAliveCalledState = true }
    }

    override suspend fun close() {
        synchronized(lock) {
            closeCalledState = true
            closeCountState++
        }
    }
}
