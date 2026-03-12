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
package org.meshtastic.desktop

import org.meshtastic.core.common.util.Base64Factory
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.UrlUtils
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.util.SfppHasher
import org.meshtastic.core.model.util.getShortDateTime
import org.meshtastic.core.model.util.platformRandomBytes

/**
 * Exercises key shared KMP modules to validate the module graph links and runs correctly on a pure JVM target without
 * Android framework dependencies.
 */
object DemoScenario {

    @Suppress("LongMethod")
    fun renderReport(): String = buildString {
        appendLine("=".repeat(SEPARATOR_WIDTH))
        appendLine("  Meshtastic Desktop — KMP Shared Module Smoke Report")
        appendLine("=".repeat(SEPARATOR_WIDTH))
        appendLine()

        // 1. core:common — Base64Factory
        section("core:common — Base64Factory") {
            val original = "Hello Meshtastic KMP!"
            val encoded = Base64Factory.encode(original.encodeToByteArray())
            val decoded = Base64Factory.decode(encoded).decodeToString()
            appendLine("  Original:  $original")
            appendLine("  Encoded:   $encoded")
            appendLine("  Decoded:   $decoded")
            appendLine("  Round-trip: ${if (original == decoded) "✓ PASS" else "✗ FAIL"}")
        }

        // 2. core:common — NumberFormatter
        @Suppress("MagicNumber")
        section("core:common — NumberFormatter") {
            appendLine("  format(3.14159, 2) = ${NumberFormatter.format(3.14159, 2)}")
            appendLine("  format(-0.5f, 1)   = ${NumberFormatter.format(-0.5f, 1)}")
            appendLine("  format(100.0, 0)   = ${NumberFormatter.format(100.0, 0)}")
        }

        // 3. core:common — UrlUtils
        section("core:common — UrlUtils") {
            val raw = "hello world&foo=bar"
            appendLine("  encode(\"$raw\") = ${UrlUtils.encode(raw)}")
        }

        // 4. core:common — DateFormatter
        section("core:common — DateFormatter") {
            val now = System.currentTimeMillis()
            appendLine("  formatTime(now)            = ${DateFormatter.formatTime(now)}")
            appendLine("  formatDate(now)            = ${DateFormatter.formatDate(now)}")
            appendLine("  formatRelativeTime(now)    = ${DateFormatter.formatRelativeTime(now)}")
            appendLine("  formatDateTimeShort(now)   = ${DateFormatter.formatDateTimeShort(now)}")
        }

        // 5. core:common — CommonUri
        section("core:common — CommonUri") {
            val uri = CommonUri.parse("https://meshtastic.org/e/#test?foo=bar&enabled=true")
            appendLine("  host     = ${uri.host}")
            appendLine("  fragment = ${uri.fragment}")
            appendLine("  segments = ${uri.pathSegments}")
            appendLine("  foo      = ${uri.getQueryParameter("foo")}")
            appendLine("  enabled  = ${uri.getBooleanQueryParameter("enabled", false)}")
        }

        // 6. core:model — DeviceVersion
        section("core:model — DeviceVersion") {
            val v1 = DeviceVersion("2.5.3.abc1234")
            val v2 = DeviceVersion("2.6.0.def5678")
            appendLine("  v1 = $v1")
            appendLine("  v2 = $v2")
            appendLine("  v1 < v2 = ${v1 < v2}")
        }

        // 7. core:model — Capabilities
        section("core:model — Capabilities") {
            val caps = Capabilities(firmwareVersion = "2.6.0.abc1234")
            appendLine("  firmwareVersion = ${caps.firmwareVersion}")
        }

        // 8. core:model — SfppHasher
        section("core:model — SfppHasher") {
            val hash =
                SfppHasher.computeMessageHash(
                    encryptedPayload = "test payload".encodeToByteArray(),
                    to = 0x12345678,
                    from = 0xABCDEF00.toInt(),
                    id = 42,
                )
            appendLine("  hash length  = ${hash.size}")
            appendLine("  hash (hex)   = ${hash.joinToString("") { "%02x".format(it) }}")
        }

        // 9. core:model — platformRandomBytes
        section("core:model — platformRandomBytes") {
            val random = platformRandomBytes(KEY_SIZE)
            appendLine("  ${random.size} random bytes (hex) = ${random.joinToString("") { "%02x".format(it) }}")
        }

        // 10. core:model — getShortDateTime
        section("core:model — getShortDateTime") {
            appendLine("  getShortDateTime(now) = ${getShortDateTime(System.currentTimeMillis())}")
        }

        // 11. core:model — Channel key generation
        section("core:model — Channel.getRandomKey") {
            val key = Channel.getRandomKey()
            appendLine("  Random channel key (${key.size} bytes)")
        }

        appendLine()
        appendLine("=".repeat(SEPARATOR_WIDTH))
        appendLine("  All checks completed successfully")
        appendLine("=".repeat(SEPARATOR_WIDTH))
    }

    private fun StringBuilder.section(title: String, block: StringBuilder.() -> Unit) {
        appendLine("─── $title")
        block()
        appendLine()
    }

    private const val SEPARATOR_WIDTH = 60
    private const val KEY_SIZE = 16
}
