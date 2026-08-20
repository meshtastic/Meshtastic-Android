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
package org.meshtastic.app.firmware

import org.meshtastic.core.data.repository.MaintenanceUf2RepositoryImpl
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Guards `maintenance_uf2.json` against [MaintenanceUf2RepositoryImpl.EXPECTED_MANIFEST_SHA256].
 *
 * The whole point of the digest pin is that a client only trusts a fetched (or bundled) manifest whose raw bytes hash
 * to a compile-time constant. If someone edits the bundled asset without updating the constant — or vice versa —
 * [MaintenanceUf2RepositoryImpl] would silently refuse to seed from it (logged, not crashed), which is safe but would
 * go unnoticed without this test making the drift loud in CI.
 */
class MaintenanceUf2ManifestDigestTest {

    @Test
    fun `bundled maintenance_uf2 json matches the compile-time digest pin`() {
        val bytes = asset("maintenance_uf2.json").readBytes()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        assertEquals(
            MaintenanceUf2RepositoryImpl.EXPECTED_MANIFEST_SHA256,
            actual,
            "androidApp/src/main/assets/maintenance_uf2.json no longer matches " +
                "MaintenanceUf2RepositoryImpl.EXPECTED_MANIFEST_SHA256 — update the pin (and re-verify the manifest " +
                "against meshtastic/api's data/maintenanceUf2.json) if this edit was deliberate.",
        )
    }

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "androidApp/src/main/assets/$name").map(::File).firstOrNull(File::exists)
            ?: fail("Could not locate $name from working directory ${File(".").absolutePath}")
}
