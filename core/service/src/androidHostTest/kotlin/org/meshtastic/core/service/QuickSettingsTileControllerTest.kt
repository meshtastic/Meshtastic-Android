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
package org.meshtastic.core.service

import androidx.test.core.app.ApplicationProvider
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.takserver.MeshToCotBroadcaster
import org.meshtastic.core.takserver.TAKMeshIntegration
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickSettingsTileControllerTest {

    @Test
    fun `a failed read of the saved radio opens the Connect tab`() = runTest {
        val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
        every { radioInterfaceService.currentDeviceAddressFlow } returns MutableStateFlow(null)
        val radioPrefs: RadioPrefs = mock(MockMode.autofill)
        everySuspend { radioPrefs.awaitDevAddr() } throws IOException("disk read failed")
        val appScope = object : ApplicationCoroutineScope, CoroutineScope by backgroundScope {}

        val controller =
            QuickSettingsTileController(
                context = ApplicationProvider.getApplicationContext(),
                radioController = FakeRadioController(),
                radioInterfaceService = radioInterfaceService,
                radioPrefs = radioPrefs,
                nodeRepository = FakeNodeRepository(),
                orchestrator = idleOrchestrator(radioInterfaceService),
                appScope = appScope,
            )

        assertTrue(controller.onClick())
    }

    private fun idleOrchestrator(radioInterfaceService: RadioInterfaceService): MeshServiceOrchestrator {
        val dispatcher = StandardTestDispatcher()
        val dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)
        return MeshServiceOrchestrator(
            radioInterfaceService = radioInterfaceService,
            serviceStateWriter = mock(MockMode.autofill),
            nodeManager = mock(MockMode.autofill),
            messageProcessor = mock(MockMode.autofill),
            serviceNotifications = mock(MockMode.autofill),
            takServerManager = mock(MockMode.autofill),
            takMeshIntegration =
            TAKMeshIntegration(
                takServerManager = mock(MockMode.autofill),
                commandSender = mock(MockMode.autofill),
                serviceRepository = mock(MockMode.autofill),
                meshConfigHandler = mock(MockMode.autofill),
                nodeRepository = mock(MockMode.autofill),
                meshToCotBroadcaster =
                MeshToCotBroadcaster(
                    takServerManager = mock(MockMode.autofill),
                    nodeRepository = mock(MockMode.autofill),
                    takPrefs = mock(MockMode.autofill),
                    dispatchers = dispatchers,
                ),
                takPrefs = mock(MockMode.autofill),
            ),
            takPrefs = mock(MockMode.autofill),
            databaseManager = mock(MockMode.autofill),
            connectionManager = mock(MockMode.autofill),
            dispatchers = dispatchers,
            localNetworkAccess = LocalNetworkAccess { true },
        )
    }
}
