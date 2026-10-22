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
package org.meshtastic.core.takserver

/**
 * The pieces of the per-install TAK PKI that go into the exported ATAK data package.
 *
 * - [clientP12]: the one client identity the server accepts (certificate + private key).
 * - [truststoreP12]: the issuing CA certificate only, so ATAK can trust the server; it carries no private key.
 * - [password]: protects both containers and is written to the `.pref` so ATAK can open them.
 */
class TakDataPackageCredentials(val clientP12: ByteArray, val truststoreP12: ByteArray, val password: String)

/**
 * Access to the local TAK server's per-install certificates.
 *
 * The server generates a private CA, a server identity and a single client identity the first time it starts (or the
 * first time a data package is exported) and keeps them in encrypted storage. Nothing is shared between installs, so
 * presenting the client certificate proves possession of this install's exported data package.
 */
interface TakCertificateProvider {
    /** Credentials for the data package, generating them on first use. Null on platforms without a local server. */
    suspend fun dataPackageCredentials(): TakDataPackageCredentials?

    /**
     * Discards the current PKI and issues a fresh one, restarting the listener if it is running. Every previously
     * exported data package stops working; the user must export and re-import a new one.
     */
    suspend fun regenerate(): Result<Unit>
}
