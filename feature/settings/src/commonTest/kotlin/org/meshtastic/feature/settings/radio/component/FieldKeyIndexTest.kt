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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.search.SettingsSearchCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldKeyIndexTest {

    @Test
    fun aFieldResolvesToTheItemThatDeclaredIt() {
        val index = FieldKeyIndex()
        index.recordItem(null)
        index.recordFields(setOf("a", "b"))
        index.recordItems(2) { "row$it" }
        index.recordFields(setOf("c"))

        assertEquals(1, index.indexOf("a"))
        assertEquals(1, index.indexOf("b"))
        assertEquals(2, index.indexOf("row0"))
        assertEquals(3, index.indexOf("row1"))
        assertEquals(4, index.indexOf("c"))
        assertNull(index.indexOf("missing"))
    }

    @Test
    fun resetForgetsEarlierItems() {
        val index = FieldKeyIndex()
        index.recordFields(setOf("a"))
        index.reset()
        index.recordFields(setOf("b"))

        assertNull(index.indexOf("a"))
        assertEquals(0, index.indexOf("b"))
    }

    @Test
    fun splitFieldKeysPartitionsWithoutLoss() {
        val (first, second) = splitFieldKeys(setOf("a", "b", "c")) { it == "b" }

        assertEquals(setOf("b"), first)
        assertEquals(setOf("a", "c"), second)
    }

    @Test
    fun everyCoveredScreenOwnsKeysFromTheCatalog() {
        val covered =
            listOf(
                ConfigRoute.BLUETOOTH.route,
                ConfigRoute.DEVICE.route,
                ConfigRoute.DISPLAY.route,
                ConfigRoute.LORA.route,
                ConfigRoute.NETWORK.route,
                ConfigRoute.POSITION.route,
                ConfigRoute.POWER.route,
                ConfigRoute.SECURITY.route,
                ModuleRoute.AMBIENT_LIGHTING.route,
                ModuleRoute.AUDIO.route,
                ModuleRoute.CANNED_MESSAGE.route,
                ModuleRoute.DETECTION_SENSOR.route,
                ModuleRoute.EXT_NOTIFICATION.route,
                ModuleRoute.MESH_BEACON.route,
                ModuleRoute.MQTT.route,
                ModuleRoute.NEIGHBOR_INFO.route,
                ModuleRoute.PAXCOUNTER.route,
                ModuleRoute.RANGE_TEST.route,
                ModuleRoute.SERIAL.route,
                ModuleRoute.STORE_FORWARD.route,
                ModuleRoute.TAK.route,
                ModuleRoute.TELEMETRY.route,
            )
        covered.forEach { route ->
            val keys = SettingsSearchCatalog.fieldKeys(route)
            assertTrue(keys.isNotEmpty(), "$route has no field keys")
        }
        val entries = SettingsSearchCatalog.entries().filter { it.fieldKey != null && it.route in covered }
        entries.forEach {
            assertTrue(it.fieldKey in SettingsSearchCatalog.fieldKeys(it.route), "${it.fieldKey} missing")
        }
    }

    @Test
    fun twoCardScreensSplitEveryKeyIntoExactlyOneCard() {
        val keys = SettingsSearchCatalog.fieldKeys(ModuleRoute.MQTT.route)
        val (map, rest) = splitFieldKeys(keys) { it.startsWith("schema_mapreportsettings_") }

        assertEquals(keys, map + rest)
        assertTrue(map.isNotEmpty() && rest.isNotEmpty())
        assertTrue(map.intersect(rest).isEmpty())
    }

    @Test
    fun theEthernetKeyLandsOnTheEthernetCardNotTheWifiCard() {
        val keys = SettingsSearchCatalog.fieldKeys(ConfigRoute.NETWORK.route)
        val (wifi, other) = splitFieldKeys(keys) { it.startsWith("schema_network_wifi_") }
        val (ethernet, advanced) = splitFieldKeys(other) { it == ETHERNET_ENABLED_KEY }

        assertEquals(setOf(ETHERNET_ENABLED_KEY), ethernet)
        assertTrue(ETHERNET_ENABLED_KEY in keys)
        assertTrue(ETHERNET_ENABLED_KEY !in wifi && ETHERNET_ENABLED_KEY !in advanced)
        assertEquals(keys, wifi + ethernet + advanced)
    }

    @Test
    fun partitionFieldKeysPutsUnclaimedKeysOnTheFirstCard() {
        val keys = setOf("p_a", "p_b", "p_c", "p_new")
        val groups = partitionFieldKeys(keys, "p_", listOf("b"), listOf("c", "absent"))

        assertEquals(listOf(setOf("p_a", "p_new"), setOf("p_b"), setOf("p_c")), groups)
    }

    @Test
    fun loraAdvancedFieldsLandOnTheAdvancedCard() {
        val keys = SettingsSearchCatalog.fieldKeys(ConfigRoute.LORA.route)
        val (options, advanced) = partitionFieldKeys(keys, "schema_lora_", LORA_ADVANCED_FIELDS)

        assertTrue("schema_lora_hop_limit" in advanced && "schema_lora_tx_power" in advanced)
        assertTrue("schema_lora_region" in options && "schema_lora_modem_preset" in options)
        assertEquals(LORA_ADVANCED_FIELDS.size, advanced.size)
        assertEquals(keys, options + advanced)
    }
}
