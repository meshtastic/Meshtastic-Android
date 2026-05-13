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
package org.meshtastic.core.service

import org.koin.core.annotation.Single
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.StoredPassphrase

/**
 * No-op passphrase store for JVM/Desktop. Desktop lockdown passphrase storage
 * is not yet implemented — passphrases are not persisted across sessions.
 */
@Single(binds = [LockdownPassphraseStore::class])
class LockdownPassphraseStoreImpl : LockdownPassphraseStore {
    override fun getPassphrase(deviceAddress: String): StoredPassphrase? = null
    override fun savePassphrase(deviceAddress: String, passphrase: String, boots: Int, hours: Int) = Unit
    override fun clearPassphrase(deviceAddress: String) = Unit
}
