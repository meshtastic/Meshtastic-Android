/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.app.radio

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.ntsocial.meshlink.core.common.util.handledLaunch
import com.ntsocial.meshlink.core.database.EndpointDatabaseCatalog
import com.ntsocial.meshlink.core.database.EndpointDatabaseHandle
import com.ntsocial.meshlink.core.datastore.RadioScopedDataStoreFactory
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSession
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.repository.MeshMessageProcessor
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.repository.MeshRouter
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.scope.Scope
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Single(binds = [RadioEndpointSessionFactory::class])
class AndroidRadioEndpointSessionFactory(
    private val databaseCatalog: EndpointDatabaseCatalog,
    private val scopedDataStoreFactory: RadioScopedDataStoreFactory,
    private val meshPrefs: MeshPrefs,
    private val dispatchers: CoroutineDispatchers,
    private val scopeRegistry: RadioEndpointScopeRegistry,
    private val primaryRadio: RadioInterfaceService,
    private val primaryServiceRepository: ServiceRepository,
) : RadioEndpointSessionFactory,
    KoinComponent {

    override suspend fun create(profile: RadioEndpointProfile): RadioEndpointSession = if (profile.legacyPrimary) {
        LegacyPrimaryRadioEndpointSession(profile, primaryRadio, primaryServiceRepository)
    } else {
        createSecondarySession(profile)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createSecondarySession(profile: RadioEndpointProfile): RadioEndpointSession {
        val database = databaseCatalog.openEndpointDatabase(profile.transportAddress)
        val serviceScope = CoroutineScope(SupervisorJob() + dispatchers.default)
        var koinScope: Scope? = null
        try {
            val context =
                RadioEndpointScopeContext(
                    profile = profile,
                    database = database,
                    dataSources = scopedDataStoreFactory.get(profile.id),
                    serviceScope = serviceScope,
                    radioPrefs = FixedEndpointRadioPrefs(profile),
                    meshPrefs = FixedEndpointMeshPrefs(profile, meshPrefs),
                )
            koinScope =
                getKoin()
                    .createScope(
                        // A recreated service graph must not reuse ViewModels retained under a closed scope's key.
                        scopeId = "meshtastic:${profile.id.value}:${Uuid.random()}",
                        qualifier = radioEndpointScopeQualifier,
                        source = context,
                    )
            scopeRegistry.register(profile.id, koinScope)
            return SecondaryRadioEndpointSession(
                profile = profile,
                database = database,
                koinScope = koinScope,
                serviceScope = serviceScope,
                scopeRegistry = scopeRegistry,
                scopedDataStoreFactory = scopedDataStoreFactory,
            )
        } catch (error: Exception) {
            withContext(NonCancellable) {
                runCatching { koinScope?.close() }.onFailure(error::addSuppressed)
                serviceScope.cancel()
                scopedDataStoreFactory.release(profile.id)
                runCatching { database.close() }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }
}

private class LegacyPrimaryRadioEndpointSession(
    private val profile: RadioEndpointProfile,
    private val radio: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
) : RadioEndpointSession {
    override val endpointId: RadioEndpointId = profile.id
    private val mutableState = MutableStateFlow<EndpointSessionState>(EndpointSessionState.Registered)
    override val state: StateFlow<EndpointSessionState> = mutableState.asStateFlow()
    private val mutableGeneration = MutableStateFlow(radio.radioSessionState.value.epoch)
    override val generation: StateFlow<Long> = mutableGeneration.asStateFlow()
    private var collectorJob: Job? = null

    init {
        // A first-ever device selection is connected by the existing Android controller rather than session.start().
        // Observe the root graph from creation so its fleet card and endpoint tab cannot remain falsely Registered.
        ensureCollector()
    }

    override suspend fun start() {
        ensureCollector()
        mutableState.value = EndpointSessionState.Connecting
        radio.connect()
        val ready = withTimeoutOrNull(SESSION_READY_TIMEOUT) { radio.radioSessionState.first { it.isConfiguredReady } }
        if (ready == null) mutableState.value = EndpointSessionState.Degraded("setup_timeout")
    }

    override suspend fun stop() {
        radio.disconnect()
        mutableState.value = EndpointSessionState.Registered
    }

    override suspend fun close() {
        collectorJob?.cancel()
        collectorJob = null
        mutableState.value = EndpointSessionState.Registered
    }

    private fun ensureCollector() {
        if (collectorJob != null) return
        collectorJob =
            CoroutineScope(SupervisorJob()).launch {
                radio.radioSessionState.collectLatest { session ->
                    mutableGeneration.value = session.epoch
                    mutableState.value =
                        when {
                            session.isConfiguredReady -> EndpointSessionState.Ready(session.epoch)

                            session.transportConnectionState == ConnectionState.Connected ->
                                EndpointSessionState.Synchronizing

                            serviceRepository.connectionState.value == ConnectionState.Connecting ->
                                EndpointSessionState.Connecting

                            else -> EndpointSessionState.Registered
                        }
                }
            }
    }
}

private class SecondaryRadioEndpointSession(
    private val profile: RadioEndpointProfile,
    private val database: EndpointDatabaseHandle,
    private val koinScope: Scope,
    private val serviceScope: CoroutineScope,
    private val scopeRegistry: RadioEndpointScopeRegistry,
    private val scopedDataStoreFactory: RadioScopedDataStoreFactory,
) : RadioEndpointSession {
    override val endpointId: RadioEndpointId = profile.id
    private val mutableState = MutableStateFlow<EndpointSessionState>(EndpointSessionState.Registered)
    override val state: StateFlow<EndpointSessionState> = mutableState.asStateFlow()
    private val mutableGeneration = MutableStateFlow(0L)
    override val generation: StateFlow<Long> = mutableGeneration.asStateFlow()
    private val lifecycleMutex = Mutex()
    private var wired = false
    private var closed = false

    private val radio: RadioInterfaceService by lazy { koinScope.get() }
    private val serviceRepository: ServiceRepository by lazy { koinScope.get() }
    private val nodeManager: NodeManager by lazy { koinScope.get() }
    private val messageProcessor: MeshMessageProcessor by lazy { koinScope.get() }
    private val router: MeshRouter by lazy { koinScope.get() }

    override suspend fun start() = lifecycleMutex.withLock {
        check(!closed) { "Endpoint session is closed" }
        wireGraphOnce()
        if (radio.radioSessionState.value.isConfiguredReady) return@withLock
        mutableState.value = EndpointSessionState.Connecting
        nodeManager.loadCachedNodeDBAndAwait()
        radio.connect()
        val ready =
            withTimeoutOrNull(SESSION_READY_TIMEOUT) { radio.radioSessionState.first { it.isConfiguredReady } }
        if (ready == null) mutableState.value = EndpointSessionState.Degraded("setup_timeout")
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        if (closed) return@withLock
        radio.disconnect()
        mutableState.value = EndpointSessionState.Registered
    }

    override suspend fun close() = lifecycleMutex.withLock {
        if (closed) return@withLock
        closed = true
        runCatching { radio.disconnect() }.onFailure { Logger.w(it) { "Failed to disconnect secondary endpoint" } }
        scopeRegistry.unregister(endpointId, koinScope)
        serviceScope.cancel()
        koinScope.close()
        scopedDataStoreFactory.release(endpointId)
        database.close()
        mutableState.value = EndpointSessionState.Registered
    }

    private fun wireGraphOnce() {
        if (wired) return
        // Resolving the connection manager activates its endpoint-local transport and canonical-state collectors.
        koinScope.get<com.ntsocial.meshlink.core.repository.MeshConnectionManager>()
        radio.resetReceivedBuffer()
        koinScope.get<com.ntsocial.meshlink.core.data.manager.SessionMessageQueue>().start()
        wired = true
        serviceScope.handledLaunch {
            radio.receivedData.collect { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum.value) }
        }
        serviceScope.handledLaunch {
            radio.connectionError.collect { message -> serviceRepository.setErrorMessage(message, Severity.Warn) }
        }
        serviceScope.handledLaunch {
            serviceRepository.serviceAction.collect { action ->
                serviceScope.handledLaunch { router.actionHandler.onServiceAction(action) }
            }
        }
        serviceScope.launch {
            radio.radioSessionState.collectLatest { session ->
                mutableGeneration.value = session.epoch
                mutableState.value =
                    when {
                        session.isConfiguredReady -> EndpointSessionState.Ready(session.epoch)

                        session.transportConnectionState == ConnectionState.Connected ->
                            EndpointSessionState.Synchronizing

                        serviceRepository.connectionState.value == ConnectionState.Connecting ->
                            EndpointSessionState.Connecting

                        else -> EndpointSessionState.Registered
                    }
            }
        }
    }
}

private val SESSION_READY_TIMEOUT = 90.seconds
