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
package org.meshtastic.feature.settings.search

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.model.schemaEnumValuePrefixes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.WifiProvisionRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.about
import org.meshtastic.core.resources.acknowledgements
import org.meshtastic.core.resources.allStringResources
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.debug_panel
import org.meshtastic.core.resources.device_links
import org.meshtastic.core.resources.filter_settings
import org.meshtastic.core.resources.node_layout_section_title
import org.meshtastic.core.resources.wifi_devices
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute

/**
 * One thing the user can search for: a settings screen, or a single control on one.
 *
 * @param title the row's own name. For a proto-backed control this is the schema's label.
 * @param description the schema's one-sentence explanation, where it has one. Matched as well as shown.
 * @param route where tapping the result goes.
 * @param screenTitle the destination's name, shown beside the result so "Enabled" says which Enabled it is.
 */
data class SettingsSearchEntry(
    val title: StringResource,
    val description: StringResource?,
    val route: Route,
    val screenTitle: StringResource,
    /** True for settings that belong to this phone rather than to a radio, which a remote session must not offer. */
    val isAppLocal: Boolean = false,
)

/**
 * The searchable surface of Settings.
 *
 * Proto-backed entries are not listed here. They are read at runtime out of the generated `schema_strings.xml`, keyed
 * by the prefix the schema path derives (`Config.LoRaConfig.hop_limit` is `schema_lora_hop_limit`), so a field the
 * schema gains becomes searchable with no edit here. What is declared is only the part the schema cannot know: which
 * settings destination owns a message. `SettingsSearchCatalogTest` fails when a prefix stops matching the registry, so
 * the mapping cannot drift silently.
 *
 * App-level settings have no schema behind them and are listed by hand.
 */
object SettingsSearchCatalog {

    /** Suffix the generator gives a field's explanation; the base key is its title. */
    private const val DESCRIPTION_SUFFIX = "_description"

    private const val PREFIX = "schema_"

    /**
     * Schema message prefixes owned by each configuration destination.
     *
     * A screen may own more than one message: the MQTT screen edits `MQTTConfig` and the `MapReportSettings` nested in
     * it, and the TAK screen edits `TAKConfig` plus the two top-level enums its fields take.
     */
    private val schemaPrefixes: Map<Route, List<String>> =
        mapOf(
            ConfigRoute.DEVICE.route to listOf("device"),
            ConfigRoute.POSITION.route to listOf("position"),
            ConfigRoute.POWER.route to listOf("power"),
            ConfigRoute.NETWORK.route to listOf("network"),
            ConfigRoute.DISPLAY.route to listOf("display"),
            ConfigRoute.LORA.route to listOf("lora"),
            ConfigRoute.BLUETOOTH.route to listOf("bluetooth"),
            ConfigRoute.SECURITY.route to listOf("security"),
            ModuleRoute.MQTT.route to listOf("mqtt", "mapreportsettings"),
            ModuleRoute.SERIAL.route to listOf("serial"),
            ModuleRoute.EXT_NOTIFICATION.route to listOf("externalnotification"),
            ModuleRoute.STORE_FORWARD.route to listOf("storeforward"),
            ModuleRoute.RANGE_TEST.route to listOf("rangetest"),
            ModuleRoute.TELEMETRY.route to listOf("telemetry"),
            ModuleRoute.CANNED_MESSAGE.route to listOf("cannedmessage"),
            ModuleRoute.AUDIO.route to listOf("audio"),
            ModuleRoute.REMOTE_HARDWARE.route to listOf("remotehardware"),
            ModuleRoute.NEIGHBOR_INFO.route to listOf("neighborinfo"),
            ModuleRoute.AMBIENT_LIGHTING.route to listOf("ambientlighting"),
            ModuleRoute.DETECTION_SENSOR.route to listOf("detectionsensor"),
            ModuleRoute.PAXCOUNTER.route to listOf("paxcounter"),
            ModuleRoute.TAK.route to listOf("tak", "team", "memberrole"),
            ModuleRoute.MESH_BEACON.route to listOf("meshbeacon"),
        )

    /**
     * Message prefixes deliberately left out of the index, with the reason.
     *
     * `SettingsSearchCatalogTest` fails when a prefix in the generated file is neither claimed above nor excused here,
     * so a message the schema starts labelling cannot quietly go missing from search.
     */
    private val notSearchable: Map<String, String> =
        mapOf("trafficmanagement" to "no settings screen offers these fields yet")

    /**
     * Keys that name an enum *value* rather than a field, so search does not offer "Long Fast" as though it were a
     * setting. Meshtastic-Apple's settings search excludes them the same way (spec 019, FR-004).
     *
     * Generated alongside the enum labels, because nothing in a key's spelling separates `schema_bluetooth_fixed_pin`
     * (a field) from `schema_bluetooth_pairingmode_fixed_pin` (a value of one). An enum the schema starts or stops
     * labelling moves this set on the next sync rather than leaving search filtering on a stale copy.
     */
    private val enumValuePrefixes: Set<String>
        get() = schemaEnumValuePrefixes

    /** The settings destinations themselves, so a query for a screen's own name finds it. */
    private fun screenEntries(): List<SettingsSearchEntry> = (
        ConfigRoute.entries.map {
            it.title to it.route
        } + ModuleRoute.entries.map { it.title to it.route }
        ).map { (title, route) ->
        SettingsSearchEntry(title = title, description = null, route = route, screenTitle = title)
    }

    /** App-level settings, which have no schema behind them and so are listed by hand. */
    private val appSettings: List<Pair<StringResource, Route>> =
        listOf(
            Res.string.node_layout_section_title to SettingsRoute.NodeList,
            Res.string.wifi_devices to WifiProvisionRoute.WifiProvision(),
            Res.string.filter_settings to SettingsRoute.FilterSettings,
            Res.string.device_links to SettingsRoute.DeviceLinks,
            Res.string.debug_panel to SettingsRoute.DebugPanel,
            Res.string.about to SettingsRoute.About,
            Res.string.acknowledgements to SettingsRoute.Acknowledgements,
        )

    private fun appEntries(): List<SettingsSearchEntry> = appSettings.map { (title, route) ->
        SettingsSearchEntry(
            title = title,
            description = null,
            route = route,
            screenTitle = Res.string.app_settings,
            isAppLocal = true,
        )
    }

    /** Every proto-backed control the schema labels, attributed to the screen that owns its message. */
    private fun schemaEntries(): List<SettingsSearchEntry> {
        val all = Res.allStringResources
        val screenTitleByRoute =
            (
                ConfigRoute.entries.associate { it.route to it.title } +
                    ModuleRoute.entries.associate { it.route to it.title }
                )

        return schemaPrefixes.flatMap { (route, messages) ->
            val screenTitle = screenTitleByRoute[route] ?: return@flatMap emptyList()
            messages.flatMap { message ->
                val messagePrefix = PREFIX + message + "_"
                all.keys
                    .asSequence()
                    .filter { it.startsWith(messagePrefix) }
                    .filterNot { it.endsWith(DESCRIPTION_SUFFIX) }
                    .filterNot { key -> enumValuePrefixes.any { key.startsWith(it) } }
                    .sorted()
                    .map { key ->
                        SettingsSearchEntry(
                            title = all.getValue(key),
                            description = all[key + DESCRIPTION_SUFFIX],
                            route = route,
                            screenTitle = screenTitle,
                        )
                    }
                    .toList()
            }
        }
    }

    /** Everything searchable, screens before the controls on them. */
    fun entries(): List<SettingsSearchEntry> = screenEntries() + appEntries() + schemaEntries()

    /** Exposed for the test that pins the mapping against the generated file. */
    internal fun declaredMessagePrefixes(): Set<String> = schemaPrefixes.values.flatten().toSet()

    internal fun excusedMessagePrefixes(): Set<String> = notSearchable.keys

    internal fun declaredEnumValuePrefixes(): Set<String> = enumValuePrefixes
}
