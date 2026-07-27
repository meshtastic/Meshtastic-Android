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
package org.meshtastic.core.network.repository

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlinx.io.IOException
import org.meshtastic.mqtt.MqttLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class KermitMqttLoggerTest {

    private class CapturingWriter : LogWriter() {
        val entries = mutableListOf<Triple<Severity, String, String>>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Triple(severity, tag, message)
        }
    }

    private val writer = CapturingWriter()
    private val mqttLogger = KermitMqttLogger(Logger(loggerConfigInit(writer), tag = "Test"))

    @Test
    fun `an expected connection failure logged at ERROR is downgraded to WARN`() {
        // The MQTT client reports a broker-side socket close this way; it reconnects on its own, so it must not be
        // counted as an application error.
        mqttLogger.log(
            level = MqttLogLevel.ERROR,
            tag = "MqttConnection",
            message = "Read loop error: Not enough data available",
            throwable = IOException("Not enough data available"),
        )

        assertEquals(1, writer.entries.size)
        assertEquals(Severity.Warn, writer.entries[0].first)
        assertEquals("MqttConnection", writer.entries[0].second)
    }

    @Test
    fun `a genuine error logged at ERROR stays an error`() {
        mqttLogger.log(
            level = MqttLogLevel.ERROR,
            tag = "MqttConnection",
            message = "Malformed packet",
            throwable = IllegalStateException("bad state"),
        )

        assertEquals(Severity.Error, writer.entries.single().first)
    }

    @Test
    fun `an error with no throwable stays an error`() {
        mqttLogger.log(level = MqttLogLevel.ERROR, tag = "MqttClient", message = "boom", throwable = null)

        assertEquals(Severity.Error, writer.entries.single().first)
    }

    @Test
    fun `other levels map straight through and keep the library tag`() {
        mqttLogger.log(MqttLogLevel.WARN, "MqttClient", "warn", null)
        mqttLogger.log(MqttLogLevel.INFO, "MqttClient", "info", null)
        mqttLogger.log(MqttLogLevel.DEBUG, "MqttClient", "debug", null)

        assertEquals(listOf(Severity.Warn, Severity.Info, Severity.Debug), writer.entries.map { it.first })
        assertEquals(listOf("MqttClient", "MqttClient", "MqttClient"), writer.entries.map { it.second })
    }

    @Test
    fun `NONE is dropped`() {
        mqttLogger.log(MqttLogLevel.NONE, "MqttClient", "nothing", null)

        assertEquals(0, writer.entries.size)
    }
}
