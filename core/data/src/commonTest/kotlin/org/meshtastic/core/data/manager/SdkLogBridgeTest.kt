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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.meshtastic.sdk.LogLevel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdkLogBridgeTest {
    private class Line(val severity: Severity, val message: String, val throwable: Throwable?)

    private val lines = mutableListOf<Line>()

    @BeforeTest
    fun install() {
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    lines += Line(severity, message, throwable)
                }
            },
        )
        Logger.setMinSeverity(Severity.Verbose)
    }

    @AfterTest
    fun uninstall() {
        Logger.setLogWriters(platformLogWriter())
    }

    @Test
    fun `an SDK error is logged at warn without its throwable so it never becomes a non-fatal`() {
        sdkLogSink.log(LogLevel.ERROR, "Engine", "Transport error", IllegalStateException("tcp:203.0.113.9:4403"))

        val line = lines.single()
        // Severity.Error is what GooglePlatformAnalytics reports to Crashlytics and Datadog.
        assertEquals(Severity.Warn, line.severity)
        assertNull(line.throwable)
        assertEquals("Transport error (IllegalStateException)", line.message)
    }

    @Test
    fun `a BLE identity keeps only the last characters anonymize keeps`() {
        sdkLogSink.log(LogLevel.INFO, "Engine", "Connect requested (transport=ble:AA:BB:CC:DD:EE:F1)", null)

        assertEquals("Connect requested (transport=ble:...:F1)", lines.single().message)
    }

    @Test
    fun `a public host is anonymized while a LAN address stays whole`() {
        sdkLogSink.log(LogLevel.WARN, "Engine", "could not reach node.example.com:4403: refused", null)
        sdkLogSink.log(LogLevel.INFO, "Engine", "Connect requested (transport=tcp:203.0.113.9:4403)", null)
        sdkLogSink.log(LogLevel.INFO, "Engine", "Connect requested (transport=ws:ws://node.example.com:80/api/v1/ws)", null)
        sdkLogSink.log(LogLevel.INFO, "Engine", "Connect requested (transport=tcp:192.168.1.20:4403)", null)

        val messages = lines.map { it.message }
        assertTrue(messages.none { "node.example" in it || "203.0.113" in it }, "$messages")
        assertEquals("Connect requested (transport=tcp:192.168.1.20:4403)", messages.last())
    }

    @Test
    fun `a dropped FromRadio is logged by type without its contents`() {
        val dump = "FromRadio{packet=MeshPacket{from=1, decoded=Data{portnum=TEXT_MESSAGE_APP, payload=[text=meet at 5]}}}"
        sdkLogSink.log(LogLevel.WARN, "Engine", "Dropped $dump", null)

        assertEquals("Dropped FromRadio{…}", lines.single().message)
        assertFalse(lines.single().message.contains("meet"))
    }
}
