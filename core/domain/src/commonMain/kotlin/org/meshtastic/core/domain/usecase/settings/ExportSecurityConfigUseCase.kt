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

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.BufferedSink
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.proto.Config

/** Use case for exporting security configuration to a JSON format. */
@Single
open class ExportSecurityConfigUseCase {
    /**
     * Exports the provided [Config.SecurityConfig] as a JSON string to the given [BufferedSink].
     *
     * @param sink The sink to write the JSON to.
     * @param securityConfig The security configuration to export.
     * @return A [Result] indicating success or failure.
     */
    open operator fun invoke(sink: BufferedSink, securityConfig: Config.SecurityConfig): Result<Unit> = runCatching {
        // Convert ByteStrings to Base64 strings
        val publicKeyBase64 = securityConfig.public_key.base64()
        val privateKeyBase64 = securityConfig.private_key.base64()

        // Create a JSON object
        val jsonObject = buildJsonObject {
            put("timestamp", nowMillis)
            put("public_key", publicKeyBase64)
            put("private_key", privateKeyBase64)
        }

        val jsonString = jsonObject.toString()
        sink.writeUtf8(jsonString)
        sink.flush()
    }
}
