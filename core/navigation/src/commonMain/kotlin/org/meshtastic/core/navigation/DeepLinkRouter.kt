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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.BackStackMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.UriMatchResult
import androidx.navigation3.runtime.deeplink.withBackStack
import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import org.meshtastic.core.common.util.CommonUri

/** Placeholder values a pattern captured, keyed by placeholder name. */
private typealias DeepLinkArgs = Map<String, List<String>>

/**
 * Deep-link router for KMP Navigation 3.
 *
 * Maps an incoming OS intent URI to the back stack it should open, so a link into a detail view lands with its logical
 * "up" hierarchy already populated. Every supported link is a [UriDeepLinkMatcher] pattern whose placeholders decode
 * straight into the target route's fields, wrapped with [withBackStack] to synthesize the parents.
 *
 * Path literals match case-insensitively. The scheme and host are not part of the contract: `meshtastic://meshtastic`
 * and `http(s)://meshtastic.org` links are routed identically, because the OS intent-filter already decides which hosts
 * reach the app.
 *
 * Supports both legacy query-parameter URIs and modern RESTful path patterns:
 * - `/nodes` -> List of all nodes
 * - `/nodes/{destNum}` -> Node details
 * - `/nodes/{destNum}/{metric}` -> Specific node metric (e.g., `/nodes/1234/device-metrics`)
 * - `/messages` -> Conversation list
 * - `/messages/{contactKey}` -> Specific conversation
 * - `/settings` -> Settings root
 * - `/settings/{destNum}/{page}` -> Specific settings page for a node
 * - `/wifi-provision` -> WiFi provisioning screen
 * - `/wifi-provision?address={mac}` -> WiFi provisioning targeting a specific device MAC address
 * - `/connections?address={prefixedAddress}` -> Connections screen, auto-connecting to a prefixed device address (e.g.
 *   `t192.168.1.1:4403` for TCP, `xAA:BB:CC:DD:EE:FF` for BLE) — lets external tooling trigger a connection.
 *   `address=n` disconnects instead of connecting.
 */
object DeepLinkRouter {
    /**
     * Canonical set of top-level path segments this router dispatches on. [route] refuses segments outside this set, so
     * a new pattern stays dead (and its feature tests fail) until its segment is added here. Every entry must also be
     * declared as an `android:pathPrefix` in the https App Links intent-filter in
     * `androidApp/src/main/AndroidManifest.xml` — DeepLinkManifestConsistencyTest (androidApp unit tests) enforces that
     * directly from this set.
     */
    val topLevelPathSegments: Set<String> =
        setOf(
            "share",
            "messages",
            "quickchat",
            "connections",
            "discovery",
            "map",
            "nodes",
            "settings",
            "channels",
            "firmware",
            "wifi-provision",
        )

    /**
     * Legacy import path segments (`/e/` = channel set, `/v/` = shared contact, matched case-insensitively). These are
     * handled by the `dispatchMeshtasticUri` fallback rather than this router, so [route] returns null for them without
     * logging a warning.
     */
    private val legacyImportSegments = setOf("e", "v")

    private val baseScheme = DEEP_LINK_BASE_URI.substringBefore("://")
    private val baseAuthority = DEEP_LINK_BASE_URI.substringAfter("://")

    /**
     * Synthesizes a backstack list from an incoming Meshtastic URI.
     *
     * @param uri The incoming OS intent URI (e.g. "meshtastic://meshtastic/share?message=hello")
     * @return A list of strongly-typed NavKeys representing the backstack, or null if the URI is not recognized.
     */
    fun route(uri: CommonUri): List<NavKey>? {
        val firstSegment = uri.pathSegments.firstOrNull { it.isNotBlank() }?.lowercase()

        if (firstSegment !in topLevelPathSegments) {
            // /e/ and /v/ are channel-set/contact import links, not navigation routes: returning null here lets
            // callers fall back to dispatchMeshtasticUri (see UIViewModel.handleDeepLink), so don't warn on them.
            if (firstSegment != null && firstSegment !in legacyImportSegments) {
                Logger.w { "Unrecognized deep link segment: $firstSegment" }
            }
            return null
        }

        // Patterns are written against DEEP_LINK_BASE_URI only; the matcher compares scheme and authority.
        val canonical = uri.buildUpon().scheme(baseScheme).encodedAuthority(baseAuthority).build()
        val request = DeepLinkRequest(DeepLinkUri(canonical.toString()))
        val backStack = matchers.firstNotNullOfOrNull { it.match(request) }?.backStack
        if (backStack == null) Logger.w { "No deep link pattern matches under /$firstSegment" }
        return backStack
    }

    /**
     * Ordered: the first pattern that matches wins, so a specific pattern precedes any fallback that also matches it. A
     * placeholder decodes into the same-named field of the matcher's key; a placeholder the key has no field for
     * (`{page}`, `{metric}`, `{pageId}`, `{sessionId}`) reaches the builder through its args.
     *
     * Built on first [route]: a pattern is a platform URI, so reading [topLevelPathSegments] alone never parses one.
     */
    private val matchers: List<BackStackMatcher<NavKey, NavKey>> by lazy {
        listOf(
            pattern("share?message={message}", ContactsRoute.Share.serializer()) { key, _ ->
                listOf(ContactsRoute.Contacts, key)
            },
            pattern("share", ContactsRoute.Contacts.serializer()) { key, _ -> listOf(key, ContactsRoute.Share("")) },
            pattern("quickchat", ContactsRoute.Contacts.serializer()) { key, _ ->
                listOf(key, ContactsRoute.QuickChat)
            },
            pattern("messages/{contactKey}?message={message}", ContactsRoute.Messages.serializer()) { key, _ ->
                conversation(key)
            },
            pattern("messages?contactKey={contactKey}&message={message}", ContactsRoute.Messages.serializer()) {
                    key,
                    _,
                ->
                conversation(key)
            },
            pattern("messages", ContactsRoute.Contacts.serializer()) { key, _ -> listOf(key) },
            pattern("connections?address={address}", ConnectionsRoute.Connections.serializer()) { key, _ ->
                listOf(key)
            },
            pattern("discovery", DiscoveryRoute.DiscoveryGraph.serializer()) { key, _ -> listOf(key) },
            pattern("map/{waypointId}", MapRoute.Map.serializer()) { key, _ -> listOf(key) },
            pattern("map?waypointId={waypointId}", MapRoute.Map.serializer()) { key, _ -> listOf(key) },
            // A non-numeric waypointId fails to decode above and opens the plain map.
            pattern("map/.*", MapRoute.Map.serializer()) { key, _ -> listOf(key) },
            pattern("nodes/{destNum}/{metric}", NodesRoute.NodeDetail.serializer()) { key, args ->
                val metric = key.destNum?.let { nodeDetailSubRoutes[args.value("metric").lowercase()]?.invoke(it) }
                listOf(NodesRoute.Nodes, key) + listOfNotNull(metric)
            },
            pattern("nodes/{destNum}", NodesRoute.NodeDetail.serializer()) { key, _ -> listOf(NodesRoute.Nodes, key) },
            pattern("nodes?destNum={destNum}", NodesRoute.NodeDetail.serializer()) { key, _ ->
                if (key.destNum == null) listOf(NodesRoute.Nodes) else listOf(NodesRoute.Nodes, key)
            },
            // A non-numeric destNum fails to decode above and opens the node list.
            pattern("nodes/.*", NodesRoute.Nodes.serializer()) { key, _ -> listOf(key) },
            pattern("channels", ChannelsRoute.Channels.serializer()) { key, _ -> listOf(key) },
            pattern("firmware/update", FirmwareRoute.FirmwareUpdate.serializer()) { key, _ ->
                listOf(FirmwareRoute.FirmwareGraph, key)
            },
            pattern("firmware", FirmwareRoute.FirmwareGraph.serializer()) { key, _ -> listOf(key) },
            pattern("wifi-provision?address={address}", WifiProvisionRoute.WifiProvision.serializer()) { key, _ ->
                listOf(key)
            },
        ) +
            // `/settings/{destNum}` must precede `/settings/{page}`: a page slug fails the Int decode and falls
            // through.
            settings("") { root, _ -> listOf(root) } +
            settings("{page}") { root, args ->
                listOfNotNull(root, settingsSubRoutes[args.value("page").lowercase()])
            } +
            settings("helpdocs/{pageId}") { root, args -> helpDocPage(root, args) } +
            settings("help-docs/{pageId}") { root, args -> helpDocPage(root, args) } +
            settings("local-mesh-discovery/session/{sessionId}") { root, args -> discoverySession(root, args) } +
            settings("localmeshdiscovery/session/{sessionId}") { root, args -> discoverySession(root, args) }
    }

    /**
     * Registers [tail] under both `/settings/{destNum}/<tail>` and `/settings/<tail>`; the root carries the destNum.
     */
    private fun settings(
        tail: String,
        backStack: (root: SettingsRoute.Settings, args: DeepLinkArgs) -> List<NavKey>,
    ): List<BackStackMatcher<NavKey, NavKey>> = listOf("settings/{destNum}", "settings").map { prefix ->
        pattern("$prefix/$tail".trimEnd('/'), SettingsRoute.Settings.serializer(), backStack)
    }

    private fun <T : NavKey> pattern(
        path: String,
        serializer: KSerializer<T>,
        backStack: (key: T, args: DeepLinkArgs) -> List<NavKey>,
    ): BackStackMatcher<T, NavKey> =
        UriDeepLinkMatcher(DeepLinkUri("$DEEP_LINK_BASE_URI/$path"), serializer).withBackStack { result ->
            // A UriDeepLinkMatcher only ever yields a UriMatchResult; its arguments hold the placeholders the key
            // has no field for.
            backStack(result.key, (result as UriMatchResult<*>).arguments)
        }

    private fun DeepLinkArgs.value(name: String): String = this[name]?.firstOrNull().orEmpty()

    private fun conversation(key: ContactsRoute.Messages): List<NavKey> =
        if (key.contactKey.isBlank()) listOf(ContactsRoute.Contacts) else listOf(ContactsRoute.Contacts, key)

    private fun helpDocPage(root: SettingsRoute.Settings, args: DeepLinkArgs): List<NavKey> =
        listOf(root, SettingsRoute.HelpDocs, SettingsRoute.HelpDocPage(args.value("pageId")))

    /** A session id that is not a Long still opens the discovery graph. */
    private fun discoverySession(root: SettingsRoute.Settings, args: DeepLinkArgs): List<NavKey> {
        val summary = args.value("sessionId").toLongOrNull()?.let { DiscoveryRoute.DiscoverySummary(it) }
        return listOf(root, DiscoveryRoute.DiscoveryGraph) + listOfNotNull(summary)
    }

    private val settingsSubRoutes: Map<String, Route> =
        mapOf(
            "device-config" to SettingsRoute.DeviceConfiguration,
            "module-config" to SettingsRoute.ModuleConfiguration,
            "admin" to SettingsRoute.Administration,
            "user" to SettingsRoute.User,
            "channel" to SettingsRoute.ChannelConfig,
            "device" to SettingsRoute.Device,
            "position" to SettingsRoute.Position,
            "power" to SettingsRoute.Power,
            "network" to SettingsRoute.Network,
            "display" to SettingsRoute.Display,
            "lora" to SettingsRoute.LoRa,
            "bluetooth" to SettingsRoute.Bluetooth,
            "security" to SettingsRoute.Security,
            "mqtt" to SettingsRoute.MQTT,
            "serial" to SettingsRoute.Serial,
            "ext-notification" to SettingsRoute.ExtNotification,
            "store-forward" to SettingsRoute.StoreForward,
            "range-test" to SettingsRoute.RangeTest,
            "telemetry" to SettingsRoute.Telemetry,
            "canned-message" to SettingsRoute.CannedMessage,
            "audio" to SettingsRoute.Audio,
            "remote-hardware" to SettingsRoute.RemoteHardware,
            "neighbor-info" to SettingsRoute.NeighborInfo,
            "ambient-lighting" to SettingsRoute.AmbientLighting,
            "detection-sensor" to SettingsRoute.DetectionSensor,
            "paxcounter" to SettingsRoute.Paxcounter,
            // The status message editor folded into the user screen; keep the published path working.
            "status-message" to SettingsRoute.User,
            "tak" to SettingsRoute.TAK,
            "clean-node-db" to SettingsRoute.CleanNodeDb,
            "debug-panel" to SettingsRoute.DebugPanel,
            "about" to SettingsRoute.About,
            "acknowledgements" to SettingsRoute.Acknowledgements,
            "attributions" to SettingsRoute.Acknowledgements,
            "filter-settings" to SettingsRoute.FilterSettings,
            "helpdocs" to SettingsRoute.HelpDocs,
            "help-docs" to SettingsRoute.HelpDocs,
            "local-mesh-discovery" to DiscoveryRoute.DiscoveryGraph,
            "localmeshdiscovery" to DiscoveryRoute.DiscoveryGraph,
        )

    private val nodeDetailSubRoutes: Map<String, (Int) -> Route> =
        mapOf(
            "device-metrics" to { destNum -> NodeDetailRoute.DeviceMetrics(destNum) },
            "map" to { destNum -> NodeDetailRoute.PositionLog(destNum) },
            "position" to { destNum -> NodeDetailRoute.PositionLog(destNum) },
            "environment" to { destNum -> NodeDetailRoute.EnvironmentMetrics(destNum) },
            "signal" to { destNum -> NodeDetailRoute.SignalMetrics(destNum) },
            "power" to { destNum -> NodeDetailRoute.PowerMetrics(destNum) },
            "traceroute" to { destNum -> NodeDetailRoute.TracerouteLog(destNum) },
            "host-metrics" to { destNum -> NodeDetailRoute.HostMetricsLog(destNum) },
            "pax" to { destNum -> NodeDetailRoute.PaxMetrics(destNum) },
            "neighbors" to { destNum -> NodeDetailRoute.NeighborInfoLog(destNum) },
        )
}
