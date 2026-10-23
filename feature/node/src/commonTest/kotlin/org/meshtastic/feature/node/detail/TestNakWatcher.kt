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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing

internal fun testNakWatcher(
    radioController: RadioController = mock(MockMode.autofill),
    packets: Flow<MeshPacket> = MutableSharedFlow(),
    snackbarManager: SnackbarManager = SnackbarManager(),
    resolveUiText: suspend (UiText) -> String = { "" },
    scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
): RequestNakWatcher {
    val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    every { serviceRepository.meshPacketFlow } returns packets
    return RequestNakWatcher(radioController, serviceRepository, scope.asServiceScope(), snackbarManager, resolveUiText)
}

internal fun routingNakPacket(requestId: Int, from: Int, error: Routing.Error): MeshPacket {
    val routing = Routing.Builder().also { wb -> wb.error_reason = error }.build()
    val data =
        Data.Builder()
            .also { wb ->
                wb.portnum = PortNum.ROUTING_APP
                wb.request_id = requestId
                wb.payload = routing.encode().toByteString()
            }
            .build()
    return MeshPacket.Builder()
        .also { wb ->
            wb.from = from
            wb.decoded = data
        }
        .build()
}
