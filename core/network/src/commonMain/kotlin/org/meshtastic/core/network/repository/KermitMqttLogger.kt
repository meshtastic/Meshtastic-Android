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
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.MqttLogger

/**
 * Adapter that implements [MqttLogger] to send MQTT client logs to Kermit's [Logger].
 *
 * This allows the MQTTastic library logging to integrate with the app's logging infrastructure, including any
 * configured log sinks (Crashlytics, Datadog, etc.).
 *
 * The library's [tag] (e.g. "MqttClient", "MqttConnection") is forwarded as a structured Kermit tag so that Datadog
 * receives it as an indexed attribute rather than freetext in the message body, enabling per-component filtering in log
 * queries.
 *
 * Note: The production log level should be set to [MqttLogLevel.WARN] (not INFO) to prevent the library's own
 * INFO-level messages (which include endpoint addresses and topic strings) from reaching remote analytics sinks.
 */
class KermitMqttLogger : MqttLogger {
    override fun log(level: MqttLogLevel, tag: String, message: String, throwable: Throwable?) {
        val logger = Logger.withTag(tag)
        when (level) {
            MqttLogLevel.TRACE -> logger.v(throwable) { message }
            MqttLogLevel.DEBUG -> logger.d(throwable) { message }
            MqttLogLevel.INFO -> logger.i(throwable) { message }
            MqttLogLevel.WARN -> logger.w(throwable) { message }
            MqttLogLevel.ERROR -> logger.e(throwable) { message }
            MqttLogLevel.NONE -> return
        }
    }
}
