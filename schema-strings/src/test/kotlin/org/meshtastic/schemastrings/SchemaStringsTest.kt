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
package org.meshtastic.schemastrings

import org.meshtastic.proto.Config
import org.meshtastic.proto.hop_limit
import org.meshtastic.proto.position_flags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaStringsTest {

    private val catalog by lazy { SchemaCatalog.all() }

    @Test
    fun `a key drops the container and the Config suffix and lowercases the rest`() {
        assertEquals("schema_lora_hop_limit", SchemaCatalog.keyFor("Config.LoRaConfig", "hop_limit"))
        assertEquals("schema_mqtt_address", SchemaCatalog.keyFor("ModuleConfig.MQTTConfig", "address"))
        assertEquals("schema_network_ipv4_ip", SchemaCatalog.keyFor("Config.NetworkConfig.IpV4Config", "ip"))
        assertEquals(
            "schema_position_positionflags_dop",
            SchemaCatalog.keyFor("Config.PositionConfig.PositionFlags", "DOP"),
        )
        assertEquals("schema_team_white", SchemaCatalog.keyFor("Team", "White"))
    }

    @Test
    fun `a field's label and description resolve to what the companion accessor returns`() {
        assertEquals(Config.PositionConfig.position_flags.label, catalog["schema_position_position_flags"])
        assertEquals(Config.LoRaConfig.hop_limit.description, catalog["schema_lora_hop_limit_description"])
    }

    @Test
    fun `an enum value with a label is emitted and one without is not`() {
        assertEquals("DOP", catalog["schema_position_positionflags_dop"])
        assertFalse("schema_position_positionflags_unset" in catalog, "UNSET carries no label")
    }

    @Test
    fun `an unannotated field is absent rather than blank`() {
        assertFalse(catalog.keys.any { it == "schema_device_serial_enabled" })
        assertTrue(catalog.values.none { it.isBlank() })
    }

    @Test
    fun `the catalogue is large enough to be the whole registry`() {
        assertTrue(catalog.size > 400, "only ${catalog.size} strings: the class walk missed generated types")
    }

    @Test
    fun `the notice carries the pin and reads back`() {
        val pin = "2.8.0.111-g45f6b7e-SNAPSHOT"
        val xml = StringsXml.render("", emptyMap(), notice = SchemaStringsSync.notice(pin))

        assertEquals(pin, SchemaStringsSync.recordedPin(xml))
        assertEquals(null, SchemaStringsSync.recordedPin("<resources>\n</resources>\n"))
    }

    @Test
    fun `rendering escapes markup and leaves quotes bare`() {
        val xml = StringsXml.render("<?xml?>\n", mapOf("a" to StringsXml.escape("Tom's <b> & \"c\"")), notice = null)

        assertEquals(
            "<?xml?>\n<resources>\n    <string name=\"a\">Tom's &lt;b&gt; &amp; \"c\"</string>\n</resources>\n",
            xml,
        )
    }

    @Test
    fun `reading bodies keeps a multi-line element whole`() {
        val xml =
            "<resources>\n    <string name=\"keep\">k</string>\n    <string name=\"go\">multi\n line</string>\n</resources>\n"

        assertEquals(mapOf("keep" to "k", "go" to "multi\n line"), StringsXml.bodies(xml))
    }
}
