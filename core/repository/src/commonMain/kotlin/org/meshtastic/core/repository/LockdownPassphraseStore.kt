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

/**
 * Stored passphrase entry with associated TTL parameters.
 *
 * @param maxSessionSeconds Per-boot uptime cap, in seconds. 0 = unlimited. Non-zero is firmware-side enforcement: the
 *   device revokes auth and reboots after this many seconds of uptime even if the boot-count TTL is still valid.
 */
data class StoredPassphrase(val passphrase: String, val boots: Int, val hours: Int, val maxSessionSeconds: Int = 0) {
    init {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
    }
}

/**
 * Encrypted per-device storage for lockdown passphrases.
 *
 * Platform implementations should use secure storage (e.g., EncryptedSharedPreferences on Android, Keychain on iOS).
 * Passphrase access is NOT gated behind biometric authentication so that auto-unlock can run in the background without
 * user interaction.
 */
interface LockdownPassphraseStore {
    /** Retrieves the stored passphrase for the given device address, or null if not stored. */
    fun getPassphrase(deviceAddress: String): StoredPassphrase?

    /** Saves the passphrase and TTL parameters for the given device address. */
    fun savePassphrase(deviceAddress: String, passphrase: String, boots: Int, hours: Int, maxSessionSeconds: Int = 0)

    /** Clears the stored passphrase for the given device address. */
    fun clearPassphrase(deviceAddress: String)

    companion object {
        const val DEFAULT_BOOTS = 50
    }
}
