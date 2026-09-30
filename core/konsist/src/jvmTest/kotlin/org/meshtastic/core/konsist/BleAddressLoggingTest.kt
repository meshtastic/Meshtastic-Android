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
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A BLE MAC address is a stable hardware identifier for the user's radio, and Kermit forwards every `Logger` call to
 * Datadog and Crashlytics on analytics the user is opted into by default. So an address must never be interpolated into
 * log or exception text raw — it goes through `Any?.anonymize()`, which keeps only a short suffix.
 *
 * This is an architecture rule rather than a review item because the failure mode is a missed site, and the easiest
 * sites to miss reach the log indirectly: a Kable logging `identifier`, which stamps the address onto every line the
 * BLE library emits, or a `tag` that a helper such as `retryBleOperation` prefixes to its own lines.
 *
 * Scoped to the BLE-adjacent modules so matching on the `address` suffix stays low-noise. That scope includes the
 * transport modules, so TCP hosts go through `anonymizePublicHost()`, which keeps a host on the user's own network
 * readable.
 */
class BleAddressLoggingTest {

    private val scannedPathFragments =
        listOf(
            "/core/ble/",
            "/core/network/",
            "/core/service/",
            "/feature/firmware/",
            "/feature/wifi-provision/",
            "/feature/connections/",
            "/feature/discovery/",
            "/androidApp/",
            "/desktopApp/",
        )

    /**
     * Files where an address is used as an identity rather than as diagnostic text — building the connection string or
     * a device label the user themselves is looking at. Anonymising these would break functionality.
     */
    private val identityUseAllowlist = listOf("DeviceListEntry.kt")

    /**
     * Files whose `address` names hardware, not a person. The Android serial transport's address is the USB
     * vendor-product pair (`usbSerialStableKey()`), which identifies the chip model, and the firmware retriever's are
     * UF2 flash offsets.
     */
    private val notPersonalAddressFiles = listOf("SerialRadioTransport.kt", "FirmwareRetriever.kt")

    /**
     * Start of a call whose text reaches a log or a crash report. A `Logger.withTag(...)` prefix is part of the start.
     */
    private val diagnosticCallStart =
        Regex(
            """Logger(\.withTag\([^)]*\))?\.\w+|\bthrow\s+\w+\s*\(|""" +
                """\b(error|check|require|checkNotNull|requireNotNull|println)\s*\(""",
        )

    /** A string template entry, `${...}` or `$name`. */
    private val interpolation = Regex("""\$\{[^}]*}|\$[A-Za-z_]\w*""")

    private val addressReference = Regex("""[aA]ddress\b""")

    /** A named log-tag argument such as `tag = address` or Kable's `identifier = ...`, or a `withTag(...)` argument. */
    private val logTagArgument = Regex("""\b(tag|logTag|identifier)\s*=(?!=)\s*([^,)\n]*)|withTag\(([^)\n]*)\)""")

    private val stringLiteral = Regex("\"(?:\\\\.|[^\"\\\\])*\"")

    /**
     * Files this rule covers.
     *
     * Extracted and asserted non-empty by [the scan actually reaches the BLE sources] because a rule whose scope
     * silently matches nothing passes for the wrong reason — which is the whole failure mode this test exists to catch.
     */
    private fun scannedFiles() = Konsist.scopeFromProject()
        .files
        .filterNot { it.isNestedAgentWorktree() }
        .filter { file -> scannedPathFragments.any { it in file.scanPath } }
        .filterNot { file -> identityUseAllowlist.any { file.scanPath.endsWith(it) } }
        .filterNot { file -> notPersonalAddressFiles.any { file.scanPath.endsWith(it) } }

    @Test
    fun `the scan actually reaches the BLE sources`() {
        val paths = scannedFiles().map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("BLE-scoped scan"))
        val expected =
            listOf(
                "KableBleConnection.kt",
                "BleRadioTransport.kt",
                "SharedRadioInterfaceService.kt",
                "DiscoveryScanEngine.kt",
            )
        for (file in expected) {
            assertTrue(
                paths.any { it.endsWith(file) },
                "expected $file in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
            )
        }
    }

    @Test
    fun `a BLE address is never interpolated into log or exception text without anonymize`() {
        val offenders =
            scannedFiles().flatMap { file ->
                diagnosticCallStart.findAll(file.text).flatMap { call ->
                    interpolation
                        .findAll(callText(file.text, call))
                        .map { it.value }
                        .filter { addressReference.containsMatchIn(it) && "anonymize" !in it }
                        .map { "${file.location(call.range.first)}: ${call.value} ... $it" }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "BLE addresses must be anonymised in diagnostic text. Offending calls:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `a BLE address is never used as a log tag without anonymize`() {
        val offenders =
            scannedFiles().flatMap { file ->
                logTagArgument.findAll(file.text).mapNotNull { argument ->
                    val value = argument.groupValues[2].ifEmpty { argument.groupValues[3] }
                    val expression =
                        stringLiteral.replace(value) { literal ->
                            interpolation.findAll(literal.value).joinToString(" ") { it.value }
                        }
                    if (addressReference.containsMatchIn(expression) && "anonymize" !in expression) {
                        "${file.location(argument.range.first)}: ${argument.value.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "A log tag or Kable logging identifier must be anonymised. Offending arguments:\n" +
                offenders.joinToString("\n"),
        )
    }

    /** The text of the call starting at [start]: its argument list and its trailing lambda, each when present. */
    private fun callText(text: String, start: MatchResult): String {
        var end = start.range.last + 1
        if (text[end - 1] == '(') {
            end = closingIndex(text, end - 1) + 1
        } else {
            val arguments = skipBlanks(text, end)
            if (arguments < text.length && text[arguments] == '(') end = closingIndex(text, arguments) + 1
        }
        val lambda = skipBlanks(text, end)
        if (lambda < text.length && text[lambda] == '{') end = closingIndex(text, lambda) + 1
        return text.substring(start.range.first, end)
    }

    private fun skipBlanks(text: String, from: Int): Int {
        var index = from
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    /** Index of the bracket that closes the one at [open], or the last index of [text] when it never closes. */
    private fun closingIndex(text: String, open: Int): Int {
        val openBracket = text[open]
        val closeBracket = if (openBracket == '(') ')' else '}'
        var depth = 0
        for (index in open until text.length) {
            if (text[index] == openBracket) depth++
            if (text[index] == closeBracket && --depth == 0) return index
        }
        return text.lastIndex
    }

    private fun KoFileDeclaration.location(offset: Int): String =
        "${scanPath.substringAfterLast("/kotlin/")}:${text.take(offset).count { it == '\n' } + 1}"
}
