/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.util.Base64
import org.json.JSONObject
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.proto.Config
import java.io.OutputStream
import javax.inject.Inject

/**
 * Use case for exporting security configuration to a JSON format.
 */
class ExportSecurityConfigUseCase @Inject constructor() {
    /**
     * Exports the provided [Config.SecurityConfig] as a JSON string to the given [OutputStream].
     *
     * @param outputStream The stream to write the JSON to.
     * @param securityConfig The security configuration to export.
     * @return A [Result] indicating success or failure.
     */
    operator fun invoke(outputStream: OutputStream, securityConfig: Config.SecurityConfig): Result<Unit> = runCatching {
        val publicKeyBytes = securityConfig.public_key.toByteArray()
        val privateKeyBytes = securityConfig.private_key.toByteArray()

        // Convert byte arrays to Base64 strings
        val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        val privateKeyBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)

        // Create a JSON object
        val jsonObject = JSONObject().apply {
            put("timestamp", nowMillis)
            put("public_key", publicKeyBase64)
            put("private_key", privateKeyBase64)
        }

        val jsonString = jsonObject.toString(4)
        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
    }
}
