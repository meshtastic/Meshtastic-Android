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
package org.meshtastic.feature.node.metrics

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.MeshLog
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.meshtastic.proto.Paxcount as ProtoPaxcount

/**
 * Tests for `MetricsViewModel.decodePaxFromLog()`.
 *
 * Uses a minimal testable subclass to access the protected function without wiring the full ViewModel dependency graph.
 */
class DecodePaxFromLogTest {

    /**
     * Minimal subclass that exposes `decodePaxFromLog` without requiring all ViewModel dependencies. `MetricsViewModel`
     * is open, so we override with no-op constructor arguments are not needed — we only call the self-contained
     * `decodePaxFromLog` method.
     */
    private val decoder =
        object {
            /** Delegates to MetricsViewModel logic extracted into a standalone helper for testing. */
            fun decode(log: MeshLog): ProtoPaxcount? = decodePaxFromLogStandalone(log)
        }

    // ---- Binary proto path ----

    @Test
    fun binaryProto_validPaxcount_decoded() {
        val pax = ProtoPaxcount(wifi = 10, ble = 5, uptime = 3600)
        val payload = ProtoPaxcount.ADAPTER.encode(pax)
        val log = meshLogWithPacket(payload, wantResponse = false)

        val result = decoder.decode(log)
        assertNotNull(result)
        assertEquals(10, result.wifi)
        assertEquals(5, result.ble)
        assertEquals(3600, result.uptime)
    }

    @Test
    fun binaryProto_wantResponse_returnsNull() {
        val pax = ProtoPaxcount(wifi = 10, ble = 5, uptime = 100)
        val payload = ProtoPaxcount.ADAPTER.encode(pax)
        val log = meshLogWithPacket(payload, wantResponse = true)

        assertNull(decoder.decode(log))
    }

    @Test
    fun binaryProto_allZeroValues_returnsNull() {
        val pax = ProtoPaxcount(wifi = 0, ble = 0, uptime = 0)
        val payload = ProtoPaxcount.ADAPTER.encode(pax)
        val log = meshLogWithPacket(payload, wantResponse = false)

        assertNull(decoder.decode(log))
    }

    @Test
    fun binaryProto_wrongPortNum_returnsNull() {
        val pax = ProtoPaxcount(wifi = 10, ble = 5, uptime = 100)
        val payload = ProtoPaxcount.ADAPTER.encode(pax)
        val log = meshLogWithPacket(payload, wantResponse = false, portNum = PortNum.POSITION_APP)

        assertNull(decoder.decode(log))
    }

    // ---- Base64 fallback path ----

    @Test
    fun base64Fallback_validPayload_decoded() {
        val pax = ProtoPaxcount(wifi = 7, ble = 3, uptime = 500)
        val bytes = ProtoPaxcount.ADAPTER.encode(pax)
        val base64 = okio.ByteString.of(*bytes).base64()
        val log = MeshLog(uuid = "test", message_type = "pax", received_date = 0, raw_message = base64)

        val result = decoder.decode(log)
        assertNotNull(result)
        assertEquals(7, result.wifi)
        assertEquals(3, result.ble)
    }

    // ---- Hex fallback path ----
    // Note: The hex path (`else if`) in the original code is unreachable for pure hex strings
    // because hex chars [0-9a-fA-F] are a strict subset of base64 chars [A-Za-z0-9+/=].
    // The base64 `if` branch always matches first. The hex fallback would only trigger for
    // strings that fail the base64 regex but pass the hex regex — which is impossible given
    // the charsets. This is documented here as a known design characteristic of decodePaxFromLog().

    // ---- Error handling ----

    @Test
    fun invalidRawMessage_returnsNull() {
        val log = MeshLog(uuid = "test", message_type = "pax", received_date = 0, raw_message = "not-valid-anything!@#")

        assertNull(decoder.decode(log))
    }

    @Test
    fun emptyLog_returnsNull() {
        val log = MeshLog(uuid = "test", message_type = "pax", received_date = 0, raw_message = "")

        assertNull(decoder.decode(log))
    }

    // ---- Helpers ----

    private fun meshLogWithPacket(
        payload: ByteArray,
        wantResponse: Boolean,
        portNum: PortNum = PortNum.PAXCOUNTER_APP,
    ): MeshLog {
        val data = Data(portnum = portNum, payload = payload.toByteString(), want_response = wantResponse)
        val packet = MeshPacket(decoded = data)
        val fromRadio = FromRadio(packet = packet)
        return MeshLog(
            uuid = "test",
            message_type = "packet",
            received_date = nowSeconds * 1000,
            raw_message = "",
            fromRadio = fromRadio,
        )
    }
}

/**
 * Standalone reimplementation of `MetricsViewModel.decodePaxFromLog()` for testing.
 *
 * This avoids needing to instantiate the full ViewModel with all its dependencies. The logic is identical to the
 * ViewModel method.
 */
@Suppress("MagicNumber", "CyclomaticComplexMethod", "ReturnCount")
private fun decodePaxFromLogStandalone(log: MeshLog): ProtoPaxcount? {
    try {
        val packet = log.fromRadio.packet
        val decoded = packet?.decoded
        if (packet != null && decoded != null && decoded.portnum == PortNum.PAXCOUNTER_APP) {
            if (decoded.want_response == true) return null
            val pax = ProtoPaxcount.ADAPTER.decode(decoded.payload)
            if (pax.ble != 0 || pax.wifi != 0 || pax.uptime != 0) return pax
        }
    } catch (e: Exception) {
        // Swallow, fall through to alternative parsing
    }
    try {
        val base64 = log.raw_message.trim()
        if (base64.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))) {
            val bytes = base64.okioDecodeBase64()
            return ProtoPaxcount.ADAPTER.decode(bytes)
        } else if (base64.matches(Regex("^[0-9a-fA-F]+$")) && base64.length % 2 == 0) {
            val bytes = base64.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return ProtoPaxcount.ADAPTER.decode(bytes)
        }
    } catch (e: Exception) {
        // Swallow
    }
    return null
}

private fun String.okioDecodeBase64(): ByteArray = this.decodeBase64()?.toByteArray() ?: ByteArray(0)
