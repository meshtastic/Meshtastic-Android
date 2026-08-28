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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traceroute
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

private const val HOP_COUNT = 80
private const val LAST_HOP_MARKER = "LAST_HOP_MARKER"

/**
 * #6701: a traceroute with many hops scrolled fine live, but was cut off with no way to scroll when reopened from
 * history. Root cause: [MetricsViewModel.showTracerouteDetail] and [MetricsViewModel.showLogDetail] passed
 * [MeshtasticDialog] a bare `SelectionContainer { Text(...) }` as `composableMessage` - [MeshtasticDialog] only adds
 * `verticalScroll` to its own wrapping column when the dialog has `choices` (a button list), so a plain text dialog's
 * scrolling is entirely the caller's responsibility.
 *
 * A screenshot can't catch this regression: a single frame of "scrolled to top, more content below" looks identical
 * whether or not scrolling actually works. [performScrollTo] is the right tool instead - it throws when the target node
 * has no scrollable ancestor, so it fails exactly when the `verticalScroll` wrapper is missing.
 *
 * The dialog content under test is the real one: the ViewModel is driven through the production path and the
 * `composableMessage` it hands [AlertManager] is what gets rendered, so reverting the fix in [MetricsViewModel] fails
 * these tests.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DetailDialogScrollTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val meshLogRepository: MeshLogRepository = mock()
    private val tracerouteResponseProvider: TracerouteResponseProvider = mock()
    private val nodeRepository: NodeRepository = mock()
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository = mock()
    private val nodeRequestActions: NodeRequestActions = mock()
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
    private val fileService: FileService = mock()

    private val alertManager = AlertManager()

    private lateinit var viewModel: MetricsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { tracerouteResponseProvider.tracerouteResponse } returns MutableStateFlow(null)
        every { nodeRequestActions.lastTracerouteTime } returns MutableStateFlow(null)
        every { nodeRequestActions.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { getNodeDetailsUseCase(any()) } returns flowOf(NodeDetailUiState())
        every { tracerouteSnapshotRepository.getSnapshotPositions(any()) } returns flowOf(emptyMap())

        viewModel =
            MetricsViewModel(
                destNum = 1234,
                dispatchers = dispatchers,
                meshLogRepository = meshLogRepository,
                tracerouteResponseProvider = tracerouteResponseProvider,
                nodeRepository = nodeRepository,
                tracerouteSnapshotRepository = tracerouteSnapshotRepository,
                nodeRequestActions = nodeRequestActions,
                alertManager = alertManager,
                getNodeDetailsUseCase = getNodeDetailsUseCase,
                fileService = fileService,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showLogDetailContentScrollsToRevealTrailingText() {
        viewModel.showLogDetail(titleRes = Res.string.traceroute, annotatedMessage = longRoute())
        assertShownAlertScrolls()
    }

    @Test
    fun showTracerouteDetailContentScrollsToRevealTrailingText() {
        viewModel.showTracerouteDetail(
            annotatedMessage = longRoute(),
            requestId = 1,
            responseLogUuid = "uuid",
            overlay = null,
            onViewOnMap = { _, _ -> },
        )
        assertShownAlertScrolls()
    }

    private fun longRoute(): AnnotatedString = buildAnnotatedString {
        repeat(HOP_COUNT) { append("Hop $it -> ") }
        append(LAST_HOP_MARKER)
    }

    /** Renders whatever `composableMessage` the ViewModel just handed [AlertManager], then scrolls to the last hop. */
    private fun assertShownAlertScrolls() = runComposeUiTest {
        val message = alertManager.currentAlert.value?.composableMessage
        assertNotNull(message, "ViewModel did not raise an alert with composable content")

        setContent { AppTheme { MeshtasticDialog(title = "Traceroute", text = { message.Content() }, onDismiss = {}) } }

        onNodeWithText(LAST_HOP_MARKER, substring = true).performScrollTo().assertIsDisplayed()
    }
}
