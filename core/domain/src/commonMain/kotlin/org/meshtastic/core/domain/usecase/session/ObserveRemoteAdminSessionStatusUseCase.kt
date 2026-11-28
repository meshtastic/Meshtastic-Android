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
package org.meshtastic.core.domain.usecase.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.NodeId

/** Projects the selected SDK client's session status into the UI model. */
@Single
open class ObserveRemoteAdminSessionStatusUseCase(private val radio: RadioInterfaceService) {
    open operator fun invoke(destNum: Int): Flow<SessionStatus> = radio
        .adminSessionStatus(NodeId(destNum))
        .map { status ->
            when (status) {
                AdminSessionStatus.NoSession -> SessionStatus.NoSession
                is AdminSessionStatus.Active -> SessionStatus.Active(status.refreshedAt)
                is AdminSessionStatus.Stale -> SessionStatus.Stale(status.refreshedAt)
            }
        }
        .distinctUntilChanged()
}
