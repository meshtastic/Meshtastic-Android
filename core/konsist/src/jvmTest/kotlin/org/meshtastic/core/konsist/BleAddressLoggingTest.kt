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
package org.meshtastic.core.konsist

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A BLE MAC address is a stable hardware identifier for the user's radio, and Kermit forwards every `Logger` call to
 * Datadog and Crashlytics on analytics the user is opted into by default. So an address must never be interpolated into
 * log or exception text raw — it goes through `Any?.anonymize()`, which keeps only a short suffix.
 *
 * This is enforced as an architecture rule rather than by review because the failure mode is missing a site: a previous
 * attempt anonymised the hand-written log statements in `core/ble` and missed the Kable `identifier`, which stamps the
 * address onto *every* line the BLE library emits, plus further sites in the DFU transports and WiFi provisioning.
 *
 * Scoped to the BLE-adjacent modules so matching on the `address` suffix stays low-noise.
 */
class BleAddressLoggingTest {

    private val scannedPathFragments =
        listOf("/core/ble/", "/feature/firmware/", "/feature/wifi-provision/", "/feature/connections/")

    /**
     * Files where an address is used as an identity rather than as diagnostic text — building the connection string or
     * a device label the user themselves is looking at. Anonymising these would break functionality.
     */
    private val identityUseAllowlist = listOf("DeviceListEntry.kt")

    /** Interpolation of anything ending in `address`, e.g. `${device.address}` or `$address`. */
    private val interpolatedAddress = Regex("""\$\{?[A-Za-z0-9_.]*[aA]ddress}?""")

    /**
     * Files this rule covers.
     *
     * Extracted and asserted non-empty by [the scan actually reaches the BLE sources] because a rule whose scope
     * silently matches nothing passes for the wrong reason — which is the whole failure mode this test exists to catch.
     */
    private fun scannedFiles() = Konsist.scopeFromProject()
        .files
        // scopeFromProject sweeps .claude/worktrees/ checkouts too; stale copies there
        // resurface long-fixed lines as phantom offenders (paths match "/core/ble/").
        .filterNot { "/.claude/" in it.path }
        .filter { file -> scannedPathFragments.any { it in file.path } }
        .filterNot { file -> identityUseAllowlist.any { file.path.endsWith(it) } }

    @Test
    fun `the scan actually reaches the BLE sources`() {
        val paths = scannedFiles().map { it.path }

        assertTrue(paths.isNotEmpty(), "scoped scan matched no files at all — the path filter is wrong")
        assertTrue(
            paths.any { it.endsWith("KableBleConnection.kt") },
            "expected core/ble sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `a BLE address is never interpolated into log or exception text without anonymize`() {
        val offenders =
            scannedFiles().flatMap { file ->
                file.text.lines().withIndex().mapNotNull { (index, line) ->
                    val isDiagnostic = "Logger." in line || "throw " in line || "check(" in line || "require(" in line
                    val interpolates = interpolatedAddress.containsMatchIn(line)
                    val anonymised = "anonymize" in line
                    if (isDiagnostic && interpolates && !anonymised) {
                        "${file.path.substringAfterLast("/kotlin/")}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "BLE addresses must be anonymised in diagnostic text. Offending lines:\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * Kable stamps its `Logging.identifier` onto every line it emits, so passing a raw address there leaks it from
     * library-internal logging that no per-call-site review would catch.
     */
    @Test
    fun `the Kable logging identifier is never a raw address`() {
        val offenders =
            Konsist.scopeFromProject()
                .files
                .filterNot { "/.claude/" in it.path } // see scannedFiles()
                .filter { "/core/ble/" in it.path }
                .flatMap { file ->
                    file.text.lines().withIndex().mapNotNull { (index, line) ->
                        if ("identifier =" in line && "address" in line && "anonymize" !in line) {
                            "${file.path.substringAfterLast("/kotlin/")}:${index + 1}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }

        assertTrue(
            offenders.isEmpty(),
            "Kable's logging identifier must be anonymised. Offending lines:\n" + offenders.joinToString("\n"),
        )
    }
}
