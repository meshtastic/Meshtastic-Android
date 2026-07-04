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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoTXmlParserTest {

    @Test
    fun `test successful CoT XML parsing`() {
        val validXml =
            """
            <event version="2.0" uid="test-uid-123" type="a-f-G-U-C" time="2025-01-01T12:00:00Z" start="2025-01-01T12:00:00Z" stale="2025-01-01T12:05:00Z" how="m-g">
                <point lat="45.0" lon="-90.0" hae="100.0" ce="10.0" le="10.0"/>
                <detail>
                    <contact callsign="TestUser"/>
                    <__group name="Cyan" role="Team Member"/>
                    <status battery="85"/>
                    <track speed="5.0" course="180.0"/>
                </detail>
            </event>
            """
                .trimIndent()

        val parser = CoTXmlParser(validXml)
        val result = parser.parse()

        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!

        assertEquals("test-uid-123", message.uid)
        assertEquals("a-f-G-U-C", message.type)
        assertEquals(45.0, message.latitude)
        assertEquals(-90.0, message.longitude)
        assertEquals("TestUser", message.contact?.callsign)
        assertEquals("Cyan", message.group?.name)
        assertEquals("Team Member", message.group?.role)
        assertEquals(85, message.status?.battery)
        assertEquals(5.0, message.track?.speed)
        assertEquals(180.0, message.track?.course)
    }

    @Test
    fun `test invalid CoT XML parsing falls back to failure`() {
        val invalidXml = """<invalid_xml><event uid="missing-fields"></invalid_xml>"""
        val parser = CoTXmlParser(invalidXml)
        val result = parser.parse()

        assertTrue(result.isFailure, "Parsing invalid XML should fail gracefully")
    }

    @Test
    fun `test defaults applied when optional fields missing`() {
        val basicXml =
            """
            <event version="2.0" uid="" type="" time="2025-01-01T12:00:00.000Z" start="2025-01-01T12:00:00Z" stale="2025-01-01T12:05:00Z" how="">
                <point lat="0.0" lon="0.0" hae="0.0" ce="0.0" le="0.0"/>
                <detail></detail>
            </event>
            """
                .trimIndent()

        val parser = CoTXmlParser(basicXml)
        val result = parser.parse()

        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!

        assertEquals("tak-0", message.uid)
        assertEquals("a-f-G-U-C", message.type)
        assertEquals("m-g", message.how)
    }

    @Test
    fun `parsedDetailXml preserves structural elements from unmapped types`() {
        // Simulates ATAK emitting a user-drawn circle (u-d-c-c) — parsedDetailXml keeps
        // contact/group/status (sent by the receiver's structured fields too, but
        // preserved here for raw_detail fallback fidelity). The <shape>, <labels_on>,
        // and <color> bloat is stripped by CoTDetailStripper so the packet has any
        // chance of fitting in a LoRa MTU.
        val shapeXml =
            """
            <event version="2.0" uid="circle-1" type="u-d-c-c" time="2025-01-01T12:00:00Z" start="2025-01-01T12:00:00Z" stale="2025-01-01T12:05:00Z" how="h-e">
                <point lat="45.0" lon="-90.0" hae="0" ce="10.0" le="10.0"/>
                <detail>
                    <contact callsign="TestUser"/>
                    <shape>
                        <ellipse major="500" minor="500" angle="0"/>
                        <link line="#ff0000" width="3"/>
                    </shape>
                    <labels_on value="false"/>
                    <color argb="-65536"/>
                </detail>
            </event>
            """
                .trimIndent()

        val result = CoTXmlParser(shapeXml).parse()
        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!

        assertEquals("u-d-c-c", message.type)
        val detail = message.parsedDetailXml
        assertTrue(detail != null, "parsedDetailXml must be populated for unmapped types")
        // Preserved: anything the stripper doesn't explicitly match, including contact.
        assertTrue(detail.contains("<contact"), "contact must survive stripping")
        // Stripped: see CoTDetailStripper for the full list.
        assertTrue(!detail.contains("<shape"), "shape must be stripped from parsedDetailXml")
        assertTrue(!detail.contains("<labels_on"), "labels_on must be stripped")
        assertTrue(!detail.contains("<color"), "color must be stripped")
    }

    @Test
    fun `sourceEventXml captures the complete original event verbatim`() {
        val xml =
            """
            <event version="2.0" uid="circle-1" type="u-d-c-c" time="2025-01-01T12:00:00Z" start="2025-01-01T12:00:00Z" stale="2025-01-01T12:05:00Z" how="h-e">
                <point lat="45.0" lon="-90.0" hae="0" ce="10.0" le="10.0"/>
                <detail>
                    <shape><ellipse major="500" minor="500" angle="0"/></shape>
                </detail>
            </event>
            """
                .trimIndent()
        val message = CoTXmlParser(xml).parse().getOrNull()!!
        // sourceEventXml is used for diagnostic logging only — it must be the exact
        // bytes we received so operators can see what ATAK actually sent.
        assertEquals(xml, message.sourceEventXml)
        // And it MUST still contain the stripped elements (since it is untouched).
        assertTrue(message.sourceEventXml!!.contains("<shape>"), "sourceEventXml must be verbatim")
    }

    @Test
    fun `parsedDetailXml is null for self-closed detail element`() {
        val xml =
            """
            <event version="2.0" uid="x" type="a-f-G-U-C" time="2025-01-01T12:00:00Z" start="2025-01-01T12:00:00Z" stale="2025-01-01T12:05:00Z" how="m-g">
                <point lat="0.0" lon="0.0" hae="0" ce="0" le="0"/>
                <detail/>
            </event>
            """
                .trimIndent()
        val message = CoTXmlParser(xml).parse().getOrNull()!!
        assertEquals(null, message.parsedDetailXml)
    }
}
