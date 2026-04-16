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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the allowed/stripped element contract documented on [CoTDetailStripper]. If
 * a test here starts failing because a new element type was added to the strip list,
 * update the strip-list KDoc in [CoTDetailStripper] in the same change.
 */
class CoTDetailStripperTest {

    @Test
    fun empty_input_returns_empty() {
        assertEquals("", CoTDetailStripper.strip(""))
    }

    @Test
    fun preserves_contact_group_status_track() {
        val input = """
            <contact callsign="Alice"/>
            <__group name="Cyan" role="Team Member"/>
            <status battery="82"/>
            <track speed="5.0" course="180.0"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<contact"), "contact must be preserved")
        assertTrue(stripped.contains("<__group"), "__group must be preserved")
        assertTrue(stripped.contains("<status"), "status must be preserved")
        assertTrue(stripped.contains("<track"), "track must be preserved")
    }

    @Test
    fun strips_cosmetic_elements() {
        val input = """
            <contact callsign="Alice"/>
            <color argb="-65536"/>
            <strokeColor value="#ffffff"/>
            <strokeWeight value="3"/>
            <fillColor value="#000000"/>
            <labels_on value="false"/>
            <usericon iconsetpath="COT_MAPPING_2525B/a-u-G"/>
            <model path="foo.obj"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<contact"), "contact must survive")
        assertFalse(stripped.contains("<color"), "color must be stripped")
        assertFalse(stripped.contains("<strokeColor"), "strokeColor must be stripped")
        assertFalse(stripped.contains("<strokeWeight"), "strokeWeight must be stripped")
        assertFalse(stripped.contains("<fillColor"), "fillColor must be stripped")
        assertFalse(stripped.contains("<labels_on"), "labels_on must be stripped")
        assertFalse(stripped.contains("<usericon"), "usericon must be stripped")
        assertFalse(stripped.contains("<model"), "model must be stripped")
    }

    @Test
    fun strips_geometric_detail_including_nested_content() {
        // <shape> is the biggest single bloat contributor for u-d-c-c events — it
        // contains an <ellipse> and usually a <link> styling child. Make sure the
        // entire subtree goes, not just the opening tag.
        val input = """
            <contact callsign="Alice"/>
            <shape>
                <ellipse major="500" minor="500" angle="0"/>
                <link line="#ff0000" width="3"/>
            </shape>
            <height value="100"/>
            <height_unit value="m"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<contact"), "contact must survive")
        assertFalse(stripped.contains("shape"), "shape subtree must be stripped: $stripped")
        assertFalse(stripped.contains("ellipse"), "ellipse must be stripped with its parent")
        // Note: <link> inside <shape> is also gone because we strip the whole subtree.
        assertFalse(stripped.contains("<height"), "height must be stripped")
    }

    @Test
    fun strips_resource_references_and_flags() {
        val input = """
            <contact callsign="Alice"/>
            <archive/>
            <precisionlocation altsrc="GPS" geopointsrc="GPS"/>
            <fileshare filename="foo.zip" senderUrl="http://example.com/foo.zip"/>
            <__video url="rtsp://example.com/stream"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<contact"), "contact must survive")
        assertFalse(stripped.contains("<archive"), "archive must be stripped")
        assertFalse(stripped.contains("<precisionlocation"), "precisionlocation must be stripped")
        assertFalse(stripped.contains("<fileshare"), "fileshare must be stripped")
        assertFalse(stripped.contains("<__video"), "__video must be stripped")
    }

    @Test
    fun preserves_chat_related_elements() {
        // These are all critical for GeoChat round-tripping and must survive stripping.
        val input = """
            <__chat parent="RootContactGroup" groupOwner="false" messageId="abc" chatroom="All Chat Rooms" id="All Chat Rooms" senderCallsign="Alice">
                <chatgrp uid0="abc-123" uid1="All Chat Rooms" id="All Chat Rooms"/>
            </__chat>
            <link uid="abc-123" type="a-f-G-U-C" relation="p-p"/>
            <__serverdestination destinations="0.0.0.0:4242:tcp:abc-123"/>
            <remarks source="BAO.F.ATAK.abc-123" to="All Chat Rooms" time="2025-01-01T12:00:00.000Z">hello world</remarks>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<__chat"), "__chat must survive stripping")
        assertTrue(stripped.contains("<chatgrp"), "chatgrp must survive stripping")
        assertTrue(stripped.contains("<link"), "link must survive stripping")
        assertTrue(stripped.contains("<__serverdestination"), "__serverdestination must survive")
        assertTrue(stripped.contains("<remarks"), "remarks must survive")
        assertTrue(stripped.contains("hello world"), "remarks text content must survive")
    }

    @Test
    fun collapses_inter_element_whitespace() {
        val input = """
            <contact callsign="Alice"/>
            <status battery="82"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        // No leading/trailing whitespace.
        assertEquals(stripped, stripped.trim())
        // No line breaks / indentation between elements.
        assertFalse(stripped.contains("\n"), "output must not contain newlines: $stripped")
        // Elements should be directly concatenated.
        assertTrue(
            stripped.contains("/><"),
            "adjacent elements must be directly concatenated: $stripped",
        )
    }

    @Test
    fun handles_interleaved_strip_and_keep_elements() {
        val input = """
            <contact callsign="Alice"/>
            <color argb="-65536"/>
            <__group name="Cyan" role="Team Member"/>
            <shape><ellipse major="500" minor="500" angle="0"/></shape>
            <status battery="82"/>
            <labels_on value="false"/>
            <track speed="5.0" course="180.0"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        // All four keep-elements survive in order.
        val contactIdx = stripped.indexOf("<contact")
        val groupIdx = stripped.indexOf("<__group")
        val statusIdx = stripped.indexOf("<status")
        val trackIdx = stripped.indexOf("<track")
        assertTrue(contactIdx >= 0, "contact missing")
        assertTrue(groupIdx >= 0, "group missing")
        assertTrue(statusIdx >= 0, "status missing")
        assertTrue(trackIdx >= 0, "track missing")
        assertTrue(contactIdx < groupIdx, "contact must come before group")
        assertTrue(groupIdx < statusIdx, "group must come before status")
        assertTrue(statusIdx < trackIdx, "status must come before track")
        // None of the stripped elements linger.
        assertFalse(stripped.contains("color"), "color stripped")
        assertFalse(stripped.contains("shape"), "shape stripped")
        assertFalse(stripped.contains("ellipse"), "ellipse stripped")
        assertFalse(stripped.contains("labels_on"), "labels_on stripped")
    }

    @Test
    fun strips_tog_and_flow_tags() {
        // <tog> is the rectangle "toggle" flag ATAK emits; <_flow-tags_> is TAK
        // Server routing metadata. Both are pure bloat over the mesh. These are
        // specifically tested because their names contain regex-special characters
        // (`-`, `_`) and it's easy to typo the strip-list pattern.
        val input = """
            <contact callsign="Alice"/>
            <tog enabled="0"/>
            <_flow-tags_ marti1="2014-10-28T22:40:15.341Z"/>
        """.trimIndent()
        val stripped = CoTDetailStripper.strip(input)
        assertTrue(stripped.contains("<contact"), "contact must survive")
        assertFalse(stripped.contains("<tog"), "tog must be stripped: $stripped")
        assertFalse(stripped.contains("_flow-tags_"), "_flow-tags_ must be stripped: $stripped")
    }

    @Test
    fun real_world_u_d_c_c_event_shrinks_dramatically() {
        // Synthetic reproduction of what ATAK actually emits for a drawn circle —
        // this is the 800-byte payload the user's logs were choking on.
        val realistic =
            """<contact callsign='ALPHA01'/><__group name='Cyan' role='Team Member'/>""" +
                """<status battery='85'/><precisionlocation altsrc='GPS' geopointsrc='GPS'/>""" +
                """<shape><ellipse major='500' minor='500' angle='0'/><link line='#ff0000' width='3'/></shape>""" +
                """<color argb='-65536'/><labels_on value='false'/><archive/>""" +
                """<usericon iconsetpath='COT_MAPPING_2525B/a-u-G/a-u-G-U-C-I-M/a-u-G-U-C-I-M-N-S'/>""" +
                """<strokeColor value='-65536'/><strokeWeight value='3'/><fillColor value='1157562368'/>""" +
                """<height value='100'/><height_unit value='m'/>""" +
                """<fileshare filename='overlay.kml' senderUrl='http://10.0.0.1/overlay.kml' sizeInBytes='2048' sha256='deadbeef'/>""" +
                """<__video url='rtsp://10.0.0.1:8554/stream'/>"""
        val stripped = CoTDetailStripper.strip(realistic)
        val before = realistic.length
        val after = stripped.length
        // Should shrink by at least 60% — most of the bytes were bloat.
        assertTrue(
            after < before * 0.4,
            "expected >60% reduction; before=$before after=$after stripped='$stripped'",
        )
        // Only the three "essential" elements survive.
        assertTrue(stripped.contains("<contact"), "contact must survive")
        assertTrue(stripped.contains("<__group"), "__group must survive")
        assertTrue(stripped.contains("<status"), "status must survive")
        assertFalse(stripped.contains("shape"), "shape must be gone")
        assertFalse(stripped.contains("fileshare"), "fileshare must be gone")
    }
}
