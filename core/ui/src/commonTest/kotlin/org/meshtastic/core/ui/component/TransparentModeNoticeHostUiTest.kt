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
package org.meshtastic.core.ui.component

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.TransparentModeController
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.TransparentModeViewModel
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Found by test tag: string resources do not load in the wasmJs browser tests. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TransparentModeNoticeHostUiTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model over a node that has just connected in transparent mode, so the notice is due. */
    private fun transparentNodeViewModel(): TransparentModeViewModel {
        val radioConfigRepository =
            FakeRadioConfigRepository().apply {
                val lora = Config.LoRaConfig.Builder().also { wb -> wb.transparent_mode = true }.build()
                setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = lora }.build())
            }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(1L, ADDRESS, NODE_NUM, null))
        val controller =
            TransparentModeController(
                radioConfigRepository,
                serviceRepository,
                FakeRadioController(),
                mock<NodeManager>(MockMode.autofill) { every { connectionIdentity } returns identity },
                CoroutineScope(testDispatcher),
            )
        return TransparentModeViewModel(controller)
    }

    private fun ComposeUiTest.setHost(suppressed: Boolean, viewModel: TransparentModeViewModel) = setContent {
        AppTheme { TransparentModeNoticeHost(suppressed = suppressed, viewModel = viewModel) }
    }

    @Test
    fun `the notice stays back while the firmware update holds it`() = runComposeUiTest {
        val viewModel = transparentNodeViewModel()

        setHost(suppressed = true, viewModel = viewModel)
        waitForIdle()

        assertTrue(viewModel.state.value.noticeVisible)
        onNodeWithTag(TRANSPARENT_MODE_NOTICE_CHOICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `the notice shows over any screen once nothing holds it back`() = runComposeUiTest {
        setHost(suppressed = false, viewModel = transparentNodeViewModel())

        onNodeWithTag(TRANSPARENT_MODE_NOTICE_CHOICE_TAG).assertIsDisplayed()
    }

    private companion object {
        const val NODE_NUM = 0x1234
        const val ADDRESS = "x12:34"
    }
}
