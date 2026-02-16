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
package org.meshtastic.core.model.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Telemetry

class ExtensionsTest {

    @Test
    fun `isDirectSignal returns true for valid LoRa non-MQTT packets with matching hops`() {
        val packet =
            MeshPacket(
                rx_time = 123456,
                hop_start = 3,
                hop_limit = 3,
                via_mqtt = false,
                transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
            )
        assertTrue(packet.isDirectSignal())
    }

    @Test
    fun `isDirectSignal returns false if via MQTT`() {
        val packet =
            MeshPacket(
                rx_time = 123456,
                hop_start = 3,
                hop_limit = 3,
                via_mqtt = true,
                transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
            )
        assertFalse(packet.isDirectSignal())
    }

    @Test
    fun `isDirectSignal returns false if hops do not match`() {
        val packet =
            MeshPacket(
                rx_time = 123456,
                hop_start = 3,
                hop_limit = 2,
                via_mqtt = false,
                transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
            )
        assertFalse(packet.isDirectSignal())
    }

    @Test
    fun `isDirectSignal returns false if rx_time is zero`() {
        val packet =
            MeshPacket(
                rx_time = 0,
                hop_start = 3,
                hop_limit = 3,
                via_mqtt = false,
                transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
            )
        assertFalse(packet.isDirectSignal())
    }

    @Test
    fun `hasValidEnvironmentMetrics returns true when temperature and humidity are present and valid`() {
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(temperature = 25.0f, relative_humidity = 50.0f))
        assertTrue(telemetry.hasValidEnvironmentMetrics())
    }

    @Test
    fun `hasValidEnvironmentMetrics returns false if temperature is NaN`() {
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(temperature = Float.NaN, relative_humidity = 50.0f))
        assertFalse(telemetry.hasValidEnvironmentMetrics())
    }

    @Test
    fun `hasValidEnvironmentMetrics returns false if humidity is missing`() {
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(temperature = 25.0f, relative_humidity = null))
        assertFalse(telemetry.hasValidEnvironmentMetrics())
    }
}
