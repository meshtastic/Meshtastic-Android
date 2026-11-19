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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlinx.io.IOException
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.mqtt.MqttLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class KermitMqttLoggerTest {

    private val writer = CapturingLogWriter()
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
        assertEquals(Severity.Warn, writer.entries[0].severity)
        assertEquals("MqttConnection", writer.entries[0].tag)
    }

    @Test
    fun `a genuine error logged at ERROR stays an error`() {
        mqttLogger.log(
            level = MqttLogLevel.ERROR,
            tag = "MqttConnection",
            message = "Malformed packet",
            throwable = IllegalStateException("bad state"),
        )

        assertEquals(Severity.Error, writer.entries.single().severity)
    }

    @Test
    fun `an error with no throwable is downgraded to WARN`() {
        // A bare message carries no stack or type to triage, so it must not count as an application error.
        mqttLogger.log(level = MqttLogLevel.ERROR, tag = "MqttClient", message = "boom", throwable = null)

        assertEquals(Severity.Warn, writer.entries.single().severity)
    }

    @Test
    fun `a connection teardown log is not reported as an application error`() {
        mqttLogger.log(
            level = MqttLogLevel.ERROR,
            tag = "MqttConnection",
            message = "Fatal error — tearing down connection",
            throwable = null,
        )

        assertEquals(Severity.Warn, writer.entries.single().severity)
    }

    @Test
    fun `a broker rejection is a user configuration problem rather than an application error`() {
        // Surfaced to the user through MqttProbeStatus, so it is not an app defect.
        mqttLogger.log(
            level = MqttLogLevel.ERROR,
            tag = "MqttConnection",
            message = "Connection refused: BAD_USER_NAME_OR_PASSWORD",
            throwable = null,
        )

        assertEquals(Severity.Warn, writer.entries.single().severity)
    }

    @Test
    fun `other levels map straight through and keep the library tag`() {
        mqttLogger.log(MqttLogLevel.WARN, "MqttClient", "warn", null)
        mqttLogger.log(MqttLogLevel.INFO, "MqttClient", "info", null)
        mqttLogger.log(MqttLogLevel.DEBUG, "MqttClient", "debug", null)

        assertEquals(listOf(Severity.Warn, Severity.Info, Severity.Debug), writer.entries.map { it.severity })
        assertEquals(listOf("MqttClient", "MqttClient", "MqttClient"), writer.entries.map { it.tag })
    }

    @Test
    fun `NONE is dropped`() {
        mqttLogger.log(MqttLogLevel.NONE, "MqttClient", "nothing", null)

        assertEquals(0, writer.entries.size)
    }
}
