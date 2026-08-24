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
package org.meshtastic.core.repository

/** A public/private key pair backed up for a single node, plus when it was saved. */
data class StoredSecurityKeys(val publicKeyBase64: String, val privateKeyBase64: String, val timestamp: Long)

/**
 * Encrypted per-node storage for security (public/private) key backups.
 *
 * Platform implementations should use secure storage (e.g., EncryptedSharedPreferences on Android), keyed by node
 * number, mirroring iOS's per-node Keychain entries ("PrivateKeyNode<nodeNum>").
 */
interface SecurityKeyBackupStore {
    /** Retrieves the stored key backup for the given node number, or null if none is stored. */
    fun get(nodeNum: Int): StoredSecurityKeys?

    /** Saves (overwriting any existing) key backup for the given node number. */
    fun save(nodeNum: Int, publicKeyBase64: String, privateKeyBase64: String, timestamp: Long)

    /** Clears the stored key backup for the given node number. */
    fun delete(nodeNum: Int)
}
