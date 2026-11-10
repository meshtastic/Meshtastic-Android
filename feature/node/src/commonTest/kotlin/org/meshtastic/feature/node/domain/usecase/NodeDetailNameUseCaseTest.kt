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
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeLocaleUnitsProvider
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The detail title is the display name when set, else the broadcast long name, else the fallback. */
class NodeDetailNameUseCaseTest {

    private val nodeRepository = FakeNodeRepository().apply { setOurNode(Node(num = OUR_NODE)) }
    private val nodeRequestActions =
        mock<NodeRequestActions>(MockMode.autofill).also {
            every { it.lastTracerouteTime } returns MutableStateFlow(null)
            every { it.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        }

    private val useCase =
        CommonGetNodeDetailsUseCase(
            nodeRepository = nodeRepository,
            meshLogRepository = FakeMeshLogRepository(),
            radioConfigRepository = FakeRadioConfigRepository(),
            deviceHardwareRepository = FakeDeviceHardwareRepository(),
            deviceLinkRepository = mock(MockMode.autofill),
            deviceModelRepository = mock(MockMode.autofill),
            firmwareReleaseRepository = FakeFirmwareReleaseRepository(),
            nodeRequestActions = nodeRequestActions,
            localeUnitsProvider = FakeLocaleUnitsProvider(),
        )

    private suspend fun nodeNameFor(longName: String, displayName: String): UiText {
        nodeRepository.setNodes(
            listOf(
                Node(
                    num = NODE_X,
                    user =
                    User.Builder()
                        .also { wb ->
                            wb.id = "!00000002"
                            wb.long_name = longName
                        }
                        .build(),
                    displayName = displayName,
                ),
            ),
        )
        return useCase(NODE_X).first { it.node?.num == NODE_X }.nodeName
    }

    @Test
    fun `the display name titles the detail screen when set`() = runTest {
        assertEquals(UiText.DynamicString("Mine"), nodeNameFor(longName = "Broadcast", displayName = "Mine"))
    }

    @Test
    fun `the broadcast long name titles the detail screen without a display name`() = runTest {
        assertEquals(UiText.DynamicString("Broadcast"), nodeNameFor(longName = "Broadcast", displayName = ""))
    }

    @Test
    fun `the fallback names a node with neither name`() = runTest {
        assertIs<UiText.Resource>(nodeNameFor(longName = "", displayName = ""))
    }

    private companion object {
        const val OUR_NODE = 1
        const val NODE_X = 2
    }
}
