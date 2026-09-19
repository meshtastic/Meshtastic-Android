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
package org.meshtastic.feature.settings.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceProfileExportFileNameTest {

    private val date = "20260919"

    @Test
    fun `prefers the long name over the short name`() {
        assertEquals(
            "Meshtastic_BaseStation_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "BaseStation", shortName = "BASE", dateStamp = date),
        )
    }

    @Test
    fun `falls back to the short name when the long name is missing or blank`() {
        val expected = "Meshtastic_BASE_20260919_nodeConfig.cfg"
        assertEquals(expected, deviceProfileExportFileName(longName = null, shortName = "BASE", dateStamp = date))
        assertEquals(expected, deviceProfileExportFileName(longName = "   ", shortName = "BASE", dateStamp = date))
    }

    @Test
    fun `falls back to a placeholder when neither name is usable`() {
        assertEquals(
            "Meshtastic_node_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = null, shortName = null, dateStamp = date),
        )
        assertEquals(
            "Meshtastic_node_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "", shortName = "", dateStamp = date),
        )
    }

    @Test
    fun `replaces characters a storage provider would mangle`() {
        assertEquals(
            "Meshtastic_James_s_Roof_Node_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "James's Roof/Node", shortName = "ROOF", dateStamp = date),
        )
    }

    @Test
    fun `keeps letters and digits of any script`() {
        assertEquals(
            "Meshtastic_Küche_東京_2_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "Küche 東京 2", shortName = "KU", dateStamp = date),
        )
    }

    @Test
    fun `keeps a supplementary-plane letter`() {
        // U+10400 DESERET CAPITAL LETTER LONG I is one code point across two UTF-16 units.
        assertEquals(
            "Meshtastic_\uD801\uDC00_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "\uD801\uDC00", shortName = "DS", dateStamp = date),
        )
    }

    @Test
    fun `drops a supplementary-plane emoji`() {
        // U+1F4CD ROUND PUSHPIN is also two units, but it is a symbol rather than a letter.
        assertEquals(
            "Meshtastic_PIN_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "\uD83D\uDCCD", shortName = "PIN", dateStamp = date),
        )
    }

    @Test
    fun `collapses a run of unsafe characters into one separator`() {
        assertEquals(
            "Meshtastic_Roof_Node_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "  Roof  //  Node  ", shortName = "ROOF", dateStamp = date),
        )
    }

    @Test
    fun `falls back to the short name when sanitizing empties the long name`() {
        assertEquals(
            "Meshtastic_ROOF_20260919_nodeConfig.cfg",
            deviceProfileExportFileName(longName = "📡🌲", shortName = "ROOF", dateStamp = date),
        )
    }

    @Test
    fun `caps an over-long name without leaving a trailing separator`() {
        val fileName = deviceProfileExportFileName(longName = "a ".repeat(60), shortName = "AAAA", dateStamp = date)
        val nodeName = fileName.removePrefix("Meshtastic_").removeSuffix("_${date}_nodeConfig.cfg")
        assertTrue(nodeName.length <= 48, "expected the name segment to be capped, was ${nodeName.length}")
        assertFalse(nodeName.endsWith("_"), "expected no trailing separator, was '$nodeName'")
    }
}
