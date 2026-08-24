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
package org.meshtastic.core.domain.usecase.settings

import okio.BufferedSource
import org.koin.core.annotation.Single
import org.meshtastic.proto.DeviceProfile

/** Use case for importing a device profile from an input stream. */
@Single
open class ImportProfileUseCase {
    /**
     * Imports a [DeviceProfile] from the provided [BufferedSource].
     *
     * @param source The source to read the profile from.
     * @return A [Result] containing the imported [DeviceProfile] or an error.
     */
    open operator fun invoke(source: BufferedSource): Result<DeviceProfile> = runCatching {
        val bytes = source.readByteArray()
        DeviceProfile.ADAPTER.decode(bytes)
    }
}
