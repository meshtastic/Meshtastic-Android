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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.testing.FakeDisplayMirrorManager
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository

/** A [RadioSessionProjection] over in-repo fakes; collaborators a test does not exercise fail loudly if reached. */
@Suppress("LongParameterList")
internal fun radioSessionProjection(
    nodeManager: NodeManager,
    radio: RadioInterfaceService,
    scope: CoroutineScope,
    dataHandler: MeshDataHandler = mock(MockMode.autofill),
    meshLogRepository: MeshLogRepository = FakeMeshLogRepository(),
    nodeRepository: NodeRepository = FakeNodeRepository(),
    serviceStateWriter: ServiceStateWriter = FakeServiceRepository(),
    configFlowManager: Lazy<MeshConfigFlowManagerImpl> = lazy { error("config install is not under test") },
    eventHandler: Lazy<RadioSessionEventHandler> = lazy { error("session events are not under test") },
    displayMirrorManager: DisplayMirrorManager = FakeDisplayMirrorManager(),
): RadioSessionProjection = RadioSessionProjection(
    nodeManager = nodeManager,
    nodeRepository = nodeRepository,
    serviceStateWriter = serviceStateWriter,
    meshLogRepository = lazy { meshLogRepository },
    dataHandler = lazy { dataHandler },
    configFlowManager = configFlowManager,
    eventHandler = eventHandler,
    displayMirrorManager = lazy { displayMirrorManager },
    radioInterfaceService = radio,
    scope = scope.asServiceScope(),
)
