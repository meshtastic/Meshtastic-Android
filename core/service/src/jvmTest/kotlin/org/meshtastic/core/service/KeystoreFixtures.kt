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
package org.meshtastic.core.service

import java.io.File
import java.security.KeyStore
import kotlin.test.assertTrue

/**
 * Deletes [alias] from the store's `keystore.p12`, leaving the keystore file in place. Loading with the store's own
 * [password] and asserting the alias existed keeps a stale literal from turning the caller into a no-op.
 */
internal fun removeKeystoreEntry(storeDir: File, alias: String, password: String) {
    val file = File(storeDir, "keystore.p12")
    val keyStore = KeyStore.getInstance("PKCS12")
    file.inputStream().use { keyStore.load(it, password.toCharArray()) }
    assertTrue(keyStore.containsAlias(alias), "fixture expects $alias in ${file.name}")
    keyStore.deleteEntry(alias)
    file.outputStream().use { keyStore.store(it, password.toCharArray()) }
}
