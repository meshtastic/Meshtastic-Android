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
package org.meshtastic.feature.node.metrics

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.TracerouteSnapshotRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase

class MetricsViewModelTest {
    private val dispatchers =
        CoroutineDispatchers(
            main = kotlinx.coroutines.Dispatchers.Unconfined,
            io = kotlinx.coroutines.Dispatchers.Unconfined,
            default = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    private val meshLogRepository: MeshLogRepository = mockk(relaxed = true)
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository = mockk(relaxed = true)
    private val nodeRequestActions: NodeRequestActions = mockk(relaxed = true)
    private val alertManager: AlertManager = mockk(relaxed = true)
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mockk(relaxed = true)
    private val fileService: FileService = mockk(relaxed = true)

    private lateinit var viewModel: MetricsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatchers.main)

        viewModel =
            MetricsViewModel(
                destNum = 1234,
                dispatchers = dispatchers,
                meshLogRepository = meshLogRepository,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                tracerouteSnapshotRepository = tracerouteSnapshotRepository,
                nodeRequestActions = nodeRequestActions,
                alertManager = alertManager,
                getNodeDetailsUseCase = getNodeDetailsUseCase,
                fileService = fileService,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun testInitialization() = runTest { assertNotNull(viewModel) }
}
