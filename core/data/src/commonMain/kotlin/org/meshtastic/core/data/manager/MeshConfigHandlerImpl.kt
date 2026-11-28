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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig

@Single
class MeshConfigHandlerImpl(radioConfigRepository: RadioConfigRepository, scope: ServiceScope) : MeshConfigHandler {

    private val _localConfig = MutableStateFlow(LocalConfig.Builder().build())
    override val localConfig = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig.Builder().build())
    override val moduleConfig = _moduleConfig.asStateFlow()

    init {
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }
}
