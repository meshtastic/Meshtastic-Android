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
package org.meshtastic.core.repository

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.sdk.ConnectionState

/** Owns canonical connection state and its compatibility projections under one publication lock. */
class ConnectionStateHolder(initialState: ConnectionState = ConnectionState.Disconnected) : ConnectionStateProvider {
    private val publicationLock = SynchronizedObject()

    private val mutableConnectionLifecycle = MutableStateFlow(ConnectionLifecycle(version = 0, state = initialState))
    private val mutableConnectionState = MutableStateFlow(initialState)

    override val connectionLifecycle: StateFlow<ConnectionLifecycle> = mutableConnectionLifecycle.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    /** Applies [newState]; the lifecycle version advances exactly once when the state changes. */
    fun setConnectionState(newState: ConnectionState) = synchronized(publicationLock) {
        val current = mutableConnectionLifecycle.value
        if (current.state != newState) publish(ConnectionLifecycle(version = current.version + 1, state = newState))
    }

    /** Restores a known state, primarily for reusable test fakes. The version always advances. */
    fun reset(state: ConnectionState = ConnectionState.Disconnected) = synchronized(publicationLock) {
        publish(ConnectionLifecycle(version = mutableConnectionLifecycle.value.version + 1, state = state))
    }

    private fun publish(next: ConnectionLifecycle) {
        mutableConnectionLifecycle.value = next
        mutableConnectionState.value = next.state
    }
}
