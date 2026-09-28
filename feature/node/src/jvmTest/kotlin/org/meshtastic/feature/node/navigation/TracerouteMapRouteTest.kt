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
package org.meshtastic.feature.node.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.traceroute_showing_nodes
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.util.LocalTracerouteMapProvider
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TracerouteMapRouteTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun tracerouteMapRouteRendersTheMapScreenAroundTheHostMap() = runComposeUiTest {
        val route = NodeDetailRoute.TracerouteMap(destNum = NODE_NUM, requestId = REQUEST_ID)
        val koinApp = koinApplication {
            modules(module { viewModel { params -> createMetricsViewModel(destNum = params.get()) } })
        }
        val viewModelStoreOwner = TestViewModelStoreOwner()
        try {
            setContent {
                val backStack = remember { NavBackStack<NavKey>().apply { add(route) } }
                KoinIsolatedContext(context = koinApp) {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModelStoreOwner,
                        LocalLifecycleOwner provides ResumedLifecycleOwner(),
                        LocalTracerouteMapProvider provides
                            { _, _, onMappableCountChanged, modifier ->
                                LaunchedEffect(Unit) { onMappableCountChanged(SHOWN_NODES, TOTAL_NODES) }
                                Text(text = FAKE_MAP, modifier = modifier)
                            },
                    ) {
                        MaterialTheme {
                            MeshtasticNavDisplay(
                                backStack = backStack,
                                entryProvider = entryProvider { nodeDetailGraph(backStack) },
                            )
                        }
                    }
                }
            }

            onNodeWithText(PLACEHOLDER).assertDoesNotExist()
            onNodeWithText(FAKE_MAP).assertExists()
            onNodeWithText(NODE_NAME).assertExists()
            onNodeWithText(getString(Res.string.traceroute_showing_nodes, SHOWN_NODES, TOTAL_NODES)).assertExists()
        } finally {
            viewModelStoreOwner.viewModelStore.clear()
            koinApp.close()
        }
    }

    private fun createMetricsViewModel(destNum: Int): MetricsViewModel {
        val tracerouteResponseProvider: TracerouteResponseProvider = mock()
        every { tracerouteResponseProvider.tracerouteResponse } returns MutableStateFlow(null)
        every { tracerouteResponseProvider.clearTracerouteResponse() } returns Unit
        val nodeRequestActions: NodeRequestActions = mock()
        every { nodeRequestActions.lastTracerouteTime } returns MutableStateFlow(null)
        every { nodeRequestActions.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        val nodeRepository: NodeRepository = mock()
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        val node = Node(num = NODE_NUM, user = User.Builder().also { wb -> wb.long_name = NODE_NAME }.build())
        val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
        every { getNodeDetailsUseCase(NODE_NUM) } returns
            flowOf(NodeDetailUiState(node = node, metricsState = MetricsState(node = node)))

        return MetricsViewModel(
            destNum = destNum,
            dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
            meshLogRepository = mock<MeshLogRepository>(),
            tracerouteResponseProvider = tracerouteResponseProvider,
            nodeRepository = nodeRepository,
            tracerouteSnapshotRepository = mock<TracerouteSnapshotRepository>(),
            nodeRequestActions = nodeRequestActions,
            alertManager = mock<AlertManager>(),
            getNodeDetailsUseCase = getNodeDetailsUseCase,
            fileService = mock<FileService>(),
        )
    }

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private companion object {
        const val NODE_NUM = 1234
        const val NODE_NAME = "Route target"
        const val REQUEST_ID = 42
        const val SHOWN_NODES = 2
        const val TOTAL_NODES = 3
        const val FAKE_MAP = "host traceroute map"
        const val PLACEHOLDER = "Traceroute Map"
    }
}
