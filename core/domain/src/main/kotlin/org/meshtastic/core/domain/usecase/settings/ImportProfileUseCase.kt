/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.meshtastic.proto.DeviceProfile
import java.io.InputStream
import javax.inject.Inject

/** Use case for importing a device profile from an input stream. */
open class ImportProfileUseCase @Inject constructor() {
    /**
     * Imports a [DeviceProfile] from the provided [InputStream].
     *
     * @param inputStream The stream to read the profile from.
     * @return A [Result] containing the imported [DeviceProfile] or an error.
     */
    operator fun invoke(inputStream: InputStream): Result<DeviceProfile> = runCatching {
        val bytes = inputStream.readBytes()
        DeviceProfile.ADAPTER.decode(bytes)
    }
}
