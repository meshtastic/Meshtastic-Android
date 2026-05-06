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
package org.meshtastic.feature.settings.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.repository.FileService
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.User

/**
 * Encapsulates device-profile import/export/install operations.
 * Injected into [RadioConfigViewModel] to keep file I/O logic self-contained.
 */
class ProfileCoordinator(
    private val fileService: FileService,
    private val importProfileUseCase: ImportProfileUseCase,
    private val exportProfileUseCase: ExportProfileUseCase,
    private val exportSecurityConfigUseCase: ExportSecurityConfigUseCase,
    private val installProfileUseCase: InstallProfileUseCase,
    private val scope: CoroutineScope,
) {
    fun importProfile(uri: CommonUri, onResult: (DeviceProfile) -> Unit) {
        scope.launch {
            try {
                var profile: DeviceProfile? = null
                fileService.read(uri) { source ->
                    importProfileUseCase(source).onSuccess { profile = it }.onFailure { throw it }
                }
                profile?.let { onResult(it) }
            } catch (e: Exception) {
                Logger.e(e) { "[importProfile] failed" }
            }
        }
    }

    fun exportProfile(uri: CommonUri, profile: DeviceProfile) {
        scope.launch {
            try {
                fileService.write(uri) { sink ->
                    exportProfileUseCase(sink, profile).onSuccess { /* Success */ }.onFailure { throw it }
                }
            } catch (e: Exception) {
                Logger.e(e) { "[exportProfile] failed" }
            }
        }
    }

    fun exportSecurityConfig(uri: CommonUri, securityConfig: Config.SecurityConfig) {
        scope.launch {
            try {
                fileService.write(uri) { sink ->
                    exportSecurityConfigUseCase(sink, securityConfig).onSuccess { /* Success */ }.onFailure { throw it }
                }
            } catch (e: Exception) {
                Logger.e(e) { "[exportSecurityConfig] failed" }
            }
        }
    }

    fun installProfile(destNum: Int, protobuf: DeviceProfile, user: User?) {
        scope.launch {
            try {
                installProfileUseCase(destNum, protobuf, user)
            } catch (e: Exception) {
                Logger.e(e) { "[installProfile] failed" }
            }
        }
    }
}
