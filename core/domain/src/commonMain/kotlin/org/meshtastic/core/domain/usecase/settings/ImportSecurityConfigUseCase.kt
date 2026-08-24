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

import okio.ByteString.Companion.decodeBase64
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.proto.Config

/** Use case for restoring security configuration (public/private keys) from a [StoredSecurityKeys] backup. */
@Single
open class ImportSecurityConfigUseCase {
    /**
     * Decodes a key backup previously saved via [org.meshtastic.core.repository.SecurityKeyBackupStore] into a
     * [Config.SecurityConfig], ready to apply to a node via the admin-config-write path.
     *
     * @param stored The stored key backup to decode.
     * @return A [Result] containing the decoded [Config.SecurityConfig], or a failure if the backup is malformed.
     */
    open operator fun invoke(stored: StoredSecurityKeys): Result<Config.SecurityConfig> = runCatching {
        val publicKey = stored.publicKeyBase64.decodeBase64()
        val privateKey = stored.privateKeyBase64.decodeBase64()
        requireNotNull(publicKey) { "public_key is not valid base64" }
        requireNotNull(privateKey) { "private_key is not valid base64" }
        Config.SecurityConfig(public_key = publicKey, private_key = privateKey)
    }
}
