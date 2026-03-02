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
import java.io.OutputStream
import javax.inject.Inject

/** Use case for exporting a device profile to an output stream. */
class ExportProfileUseCase @Inject constructor() {
    /**
     * Exports the provided [DeviceProfile] to the given [OutputStream].
     *
     * @param outputStream The stream to write the profile to.
     * @param profile The device profile to export.
     * @return A [Result] indicating success or failure.
     */
    operator fun invoke(outputStream: OutputStream, profile: DeviceProfile): Result<Unit> = runCatching {
        outputStream.write(profile.encode())
    }
}
