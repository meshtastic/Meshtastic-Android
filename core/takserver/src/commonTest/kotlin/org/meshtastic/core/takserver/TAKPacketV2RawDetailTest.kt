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
package org.meshtastic.core.takserver

import org.meshtastic.core.takserver.TAKPacketV2Conversion.toCoTMessage
import org.meshtastic.core.takserver.TAKPacketV2Conversion.toTAKPacketV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the `raw_detail` fallback round-trip for CoT types that don't fit any structured
 * [org.meshtastic.proto.TAKPacketV2] payload (PLI, GeoChat, Aircraft).
 *
 * Prior to this, ATAK user-drawn elements like `u-d-c-c` would be silently dropped by
 * [TAKPacketV2Conversion.toTAKPacketV2] with `"Cannot convert CoT to TAKPacketV2 for type ..."`.
 */
class TAKPacketV2RawDetailTest {

    @Test
    fun udcc_round_trips_via_raw_detail() {
        // Note: `<shape>` / `<labels_on>` / `<color>` in the input are deliberately
        // stripped by [CoTDetailStripper] before being placed in raw_detail, because
        // they blow up the wire size beyond the LoRa MTU. We keep `<contact>` here so
        // we have something non-trivial to verify round-tripped.
        val shapeXml = """
            <event version="2.0" uid="circle-abc" type="u-d-c-c" time="2025-01-01T12:00:00.000Z" start="2025-01-01T12:00:00.000Z" stale="2025-01-01T13:00:00.000Z" how="h-e">
                <point lat="45.5" lon="-90.25" hae="0" ce="10.0" le="10.0"/>
                <detail>
                    <contact callsign="ALPHA01"/>
                    <shape>
                        <ellipse major="500" minor="500" angle="0"/>
                        <link line="#ff0000" width="3"/>
                    </shape>
                    <labels_on value="false"/>
                </detail>
            </event>
        """.trimIndent()

        // Parse → convert to TAKPacketV2
        val cotMessage = CoTXmlParser(shapeXml).parse().getOrNull()
        assertNotNull(cotMessage, "CoT XML must parse successfully")
        val takPacketV2 = cotMessage.toTAKPacketV2()
        assertNotNull(takPacketV2, "u-d-c-c must convert to TAKPacketV2 (not drop)")

        // raw_detail must be populated; structured payloads must be null.
        assertNotNull(takPacketV2.raw_detail, "raw_detail must hold the detail bytes")
        assertNull(takPacketV2.pli, "PLI payload must not be set for u-d-c-c")
        assertNull(takPacketV2.chat, "chat payload must not be set for u-d-c-c")
        assertEquals("u-d-c-c", takPacketV2.cot_type_str.ifEmpty { "u-d-c-c" })
        // Stripping must have fired: the raw_detail bytes must NOT contain the
        // shape/labels_on fragments we put in the input.
        val rawDetailBytes = takPacketV2.raw_detail!!.utf8()
        assertFalse(rawDetailBytes.contains("shape"), "shape must be stripped from raw_detail: $rawDetailBytes")
        assertFalse(rawDetailBytes.contains("labels_on"), "labels_on must be stripped: $rawDetailBytes")
        assertTrue(rawDetailBytes.contains("contact"), "contact must survive: $rawDetailBytes")

        // Convert back to CoTMessage
        val roundTripped = takPacketV2.toCoTMessage()
        assertNotNull(roundTripped, "TAKPacketV2 with raw_detail must convert back to CoTMessage")
        assertEquals("u-d-c-c", roundTripped.type)
        assertEquals(45.5, roundTripped.latitude, 0.0001)
        assertEquals(-90.25, roundTripped.longitude, 0.0001)

        // Serialize to XML; the surviving (stripped) content must be present.
        val xmlOut = roundTripped.toXml()
        assertTrue(xmlOut.contains("type='u-d-c-c'"), "type must survive: $xmlOut")
        assertTrue(xmlOut.contains("ALPHA01"), "contact callsign must survive: $xmlOut")
        assertFalse(xmlOut.contains("<shape"), "shape must not reappear on receive: $xmlOut")
        assertFalse(xmlOut.contains("<labels_on"), "labels_on must not reappear: $xmlOut")
    }

    @Test
    fun raw_detail_path_emits_only_the_raw_bytes_inside_detail_no_duplicate_structured_elements() {
        // If toCoTMessage populated contact/group/status on the raw_detail path, toXml would
        // double-emit them alongside the rawDetailXml content. Guard against that regression.
        val xml = """
            <event version="2.0" uid="marker-1" type="b-m-p-s-p-i" time="2025-01-01T12:00:00.000Z" start="2025-01-01T12:00:00.000Z" stale="2025-01-01T13:00:00.000Z" how="h-e">
                <point lat="10.0" lon="20.0" hae="0" ce="0" le="0"/>
                <detail>
                    <contact callsign="DROP-1"/>
                    <__group name="Red" role="Team Member"/>
                    <color argb="-65536"/>
                </detail>
            </event>
        """.trimIndent()

        val cotMessage = CoTXmlParser(xml).parse().getOrNull()!!
        val takPacketV2 = cotMessage.toTAKPacketV2()!!
        val roundTripped = takPacketV2.toCoTMessage()!!

        assertNull(roundTripped.contact, "contact must be null on raw_detail path (lives inside rawDetailXml)")
        assertNull(roundTripped.group, "group must be null on raw_detail path")
        assertNull(roundTripped.status, "status must be null on raw_detail path")

        val xmlOut = roundTripped.toXml()
        // Exactly one <contact> (from the round-tripped raw detail), not two.
        assertEquals(1, xmlOut.split("<contact").size - 1, "only one contact element allowed: $xmlOut")
        assertEquals(1, xmlOut.split("<__group").size - 1, "only one group element allowed: $xmlOut")
    }

    @Test
    fun CoTMessage_without_parsed_detail_returns_null() {
        // CoTMessage created in-app (no XML round trip) for an unmapped type has no parsed
        // detail to fall back on — conversion should return null.
        val cot = CoTMessage(
            uid = "manual-1",
            type = "u-d-c-c",
            stale = kotlin.time.Clock.System.now() + kotlin.time.Duration.parse("1h"),
            latitude = 0.0,
            longitude = 0.0,
        )
        assertNull(cot.toTAKPacketV2(), "no parsed detail → no raw_detail fallback possible")
    }
}
