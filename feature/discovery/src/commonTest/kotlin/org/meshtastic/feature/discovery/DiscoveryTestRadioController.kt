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
package org.meshtastic.feature.discovery

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config

/** Discovery-local radio fake with deterministic transient local-config write failures. */
internal class DiscoveryTestRadioController(private val delegate: FakeRadioController = FakeRadioController()) :
    RadioController by delegate {
    private val deviceSwitchMutex = Mutex()

    val configWrites: List<Config>
        get() = delegate.localConfigs

    val channelWrites: List<Channel>
        get() = delegate.localChannels

    val lastLocalConfig: Config?
        get() = delegate.lastLocalConfig

    var throwOnSetLocalConfig: Boolean
        get() = delegate.throwOnSetLocalConfig
        set(value) {
            delegate.throwOnSetLocalConfig = value
        }

    var requestNeighborInfoFailure: Exception? = null
    val neighborInfoRequests = mutableListOf<Pair<Int, Int>>()

    var selectedDeviceAddress: String?
        get() = delegate.selectedDeviceAddress
        set(value) {
            delegate.selectedDeviceAddress = value
        }

    fun setSessionGeneration(generation: Long) {
        delegate.setSessionGeneration(generation)
    }

    var failLocalConfigWritesRemaining: Int = 0
    var failChannelWriteAfter: Int?
        get() = delegate.failChannelWriteAfter
        set(value) {
            delegate.failChannelWriteAfter = value
        }

    /**
     * Invoked on each local-config write attempt. Conditional restore holds a non-reentrant device-selection mutex, so
     * this hook must not call [setDeviceAddress] or [restoreLocalConfiguration].
     */
    var onLocalConfigWriteAttempt: (suspend () -> Unit)? = null
    var localConfigWriteAttempts: Int = 0
        private set

    var acceptConditionalRestore: Boolean = true

    override suspend fun setLocalConfig(config: Config) {
        localConfigWriteAttempts++
        onLocalConfigWriteAttempt?.invoke()
        if (failLocalConfigWritesRemaining > 0) {
            failLocalConfigWritesRemaining--
            error("Fake local config write failure")
        }
        delegate.setLocalConfig(config)
    }

    override suspend fun restoreLocalConfiguration(
        expectedDeviceAddress: String?,
        config: Config,
        primaryChannel: Channel?,
    ): Boolean = deviceSwitchMutex.withLock {
        if (
            expectedDeviceAddress == null ||
            selectedDeviceAddress != expectedDeviceAddress ||
            !acceptConditionalRestore
        ) {
            return@withLock false
        }
        primaryChannel?.let { channel -> delegate.setLocalChannel(channel) }
        setLocalConfig(config)
        true
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        neighborInfoRequests += requestId to destNum
        requestNeighborInfoFailure?.let { throw it }
        delegate.requestNeighborInfo(requestId, destNum)
    }

    override suspend fun setDeviceAddress(address: String) {
        deviceSwitchMutex.withLock { delegate.setDeviceAddress(address) }
    }
}
