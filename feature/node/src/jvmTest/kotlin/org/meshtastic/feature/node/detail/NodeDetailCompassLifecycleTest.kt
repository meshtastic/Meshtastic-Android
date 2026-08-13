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
package org.meshtastic.feature.node.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.ObserveRemoteAdminSessionStatusUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.compass_title
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.open_compass
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.compass.HeadingState
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider
import org.meshtastic.feature.node.compass.PhoneLocationState
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NodeDetailCompassLifecycleTest {

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
    fun compassSelectionFollowsScreenLifecycleAndDismissal() = runComposeUiTest {
        val viewModelStore = ViewModelStore()
        try {
            val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)
            val headingProvider = RecordingHeadingProvider()
            val node =
                Node(
                    num = 1234,
                    user = User(id = "!000004d2", long_name = "Compass target"),
                    position = Position(latitude_i = 10000000, longitude_i = 10000000),
                )
            val nodeDetailViewModel = createNodeDetailViewModel(node)
            viewModelStore.put("node-detail", nodeDetailViewModel)
            val compassViewModel =
                CompassViewModel(
                    headingProvider = headingProvider,
                    phoneLocationProvider = StaticPhoneLocationProvider,
                    magneticFieldProvider = ZeroMagneticFieldProvider,
                    dispatchers =
                    CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
                )
            viewModelStore.put("compass", compassViewModel)

            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    MaterialTheme {
                        NodeDetailScreen(
                            nodeId = node.num,
                            viewModel = nodeDetailViewModel,
                            compassViewModel = compassViewModel,
                        )
                    }
                }
            }

            val openCompass = getString(Res.string.open_compass)
            onNodeWithText(openCompass).performScrollTo().performClick()
            onNodeWithText(getString(Res.string.compass_title)).assertExists()
            waitUntil("compass started", SETTLE_TIMEOUT_MS) { headingProvider.starts == 1 }

            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            waitUntil("compass stopped on STOPPED", SETTLE_TIMEOUT_MS) { headingProvider.stops == 1 }

            lifecycleOwner.moveTo(Lifecycle.State.STARTED)
            waitUntil("compass restarted on STARTED", SETTLE_TIMEOUT_MS) { headingProvider.starts == 2 }

            onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.Dismiss)
            waitUntil("compass stopped on dismissal", SETTLE_TIMEOUT_MS) { headingProvider.stops == 2 }
            onNodeWithText(getString(Res.string.compass_title)).assertDoesNotExist()

            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            lifecycleOwner.moveTo(Lifecycle.State.STARTED)
            waitForIdle()
            assertEquals(2, headingProvider.starts, "a dismissed compass must not restart")
            assertEquals(2, headingProvider.stops, "dismissal must clean up exactly once")
        } finally {
            viewModelStore.clear()
        }
    }

    private fun createNodeDetailViewModel(node: Node): NodeDetailViewModel {
        val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
        val observeSessionStatus: ObserveRemoteAdminSessionStatusUseCase = mock()
        every { getNodeDetailsUseCase(any()) } returns
            flowOf(
                NodeDetailUiState(
                    node = node,
                    nodeName = UiText.DynamicString(node.user.long_name),
                    metricsState = MetricsState(),
                ),
            )
        every { observeSessionStatus(any()) } returns flowOf(SessionStatus.NoSession)

        return NodeDetailViewModel(
            savedStateHandle = SavedStateHandle(),
            nodeManagementActions = mock<NodeManagementActions>(),
            nodeRequestActions = mock<NodeRequestActions>(),
            queryController = mock<QueryController>(),
            getNodeDetailsUseCase = getNodeDetailsUseCase,
            ensureRemoteAdminSession = mock<EnsureRemoteAdminSessionUseCase>(),
            observeRemoteAdminSessionStatus = observeSessionStatus,
            snackbarManager = SnackbarManager(),
        )
    }

    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

        fun moveTo(state: Lifecycle.State) {
            lifecycle.currentState = state
        }
    }

    private class RecordingHeadingProvider : CompassHeadingProvider {
        var starts = 0
            private set

        var stops = 0
            private set

        override fun headingUpdates(): Flow<HeadingState> = callbackFlow {
            starts += 1
            trySend(HeadingState(heading = 0f))
            awaitClose { stops += 1 }
        }
    }

    private data object StaticPhoneLocationProvider : PhoneLocationProvider {
        override fun locationUpdates(): Flow<PhoneLocationState> =
            MutableStateFlow(PhoneLocationState(permissionGranted = true, providerEnabled = true))
    }

    private data object ZeroMagneticFieldProvider : MagneticFieldProvider {
        override fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long): Float = 0f
    }

    private companion object {
        /**
         * `waitUntil` defaults to 1s, which is a rendering-speed assertion rather than a liveness bound: CI runners
         * take ~12s for this test against ~2.3s locally, and `waitForIdle` alone has been observed taking 6s on runs
         * that pass. Budget for the slowest runner — a genuine regression still fails, just later.
         */
        const val SETTLE_TIMEOUT_MS = 30_000L
    }
}
