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
package org.meshtastic.feature.node.domain.usecase

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeLocaleUnitsProvider
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The neighbor-info request follows the connected radio's module switch, since firmware drops the reply otherwise. */
class NeighborInfoRequestGateTest {

    private val nodeRepository =
        FakeNodeRepository().apply {
            setOurNode(Node(num = OUR_NODE))
            setNodes(listOf(Node(num = OUR_NODE), Node(num = NODE_X)))
        }
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val nodeRequestActions =
        mock<NodeRequestActions>(MockMode.autofill).also {
            every { it.lastTracerouteTime } returns MutableStateFlow(null)
            every { it.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        }

    private val useCase =
        CommonGetNodeDetailsUseCase(
            nodeRepository = nodeRepository,
            meshLogRepository = FakeMeshLogRepository(),
            radioConfigRepository = radioConfigRepository,
            deviceHardwareRepository = FakeDeviceHardwareRepository(),
            deviceLinkRepository = mock(MockMode.autofill),
            deviceModelRepository = mock(MockMode.autofill),
            firmwareReleaseRepository = FakeFirmwareReleaseRepository(),
            nodeRequestActions = nodeRequestActions,
            localeUnitsProvider = FakeLocaleUnitsProvider(),
        )

    private fun radioNeighborInfo(enabled: Boolean) = radioConfigRepository.setDeviceProfile(
        DeviceProfile.Builder()
            .also { profile ->
                profile.module_config =
                    LocalModuleConfig.Builder()
                        .also { wb ->
                            wb.neighbor_info =
                                ModuleConfig.NeighborInfoConfig.Builder().also { it.enabled = enabled }.build()
                        }
                        .build()
            }
            .build(),
    )

    @Test
    fun `the request is offered when the radio has neighbor info enabled`() = runTest {
        radioNeighborInfo(enabled = true)

        assertTrue(useCase(NODE_X).first { it.metricsState.canRequestNeighborInfo }.metricsState.canRequestNeighborInfo)
    }

    @Test
    fun `the request is withheld while the radio has neighbor info off`() = runTest {
        radioNeighborInfo(enabled = false)

        assertFalse(useCase(NODE_X).first { it.node?.num == NODE_X }.metricsState.canRequestNeighborInfo)
    }

    private companion object {
        const val OUR_NODE = 1
        const val NODE_X = 2
    }
}
