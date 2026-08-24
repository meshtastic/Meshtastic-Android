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
package org.meshtastic.core.ui.util

/**
 * Transport-type snapshot of a single system network. Filled in by the platform-specific `isWifiUnavailable` actual
 * (Android's `ConnectivityManager.getNetworkCapabilities`); kept platform-agnostic so the "is any network-scan
 * transport present?" reduction is unit-testable from `commonTest` without an Android runtime.
 */
internal data class NetworkTransportInfo(val hasWifi: Boolean, val hasEthernet: Boolean, val hasVpn: Boolean)

/**
 * Returns `true` if any of the provided [networks] exposes a Wi-Fi, Ethernet, or VPN transport — the three transports
 * that can carry TCP traffic to a Meshtastic node and therefore serve as a valid backing path for the network-scan
 * (NSD/mDNS or direct-IP) discovery used by `ConnectionsScreen`. Drives the `wifiUnavailable` recovery banner: as long
 * as *any* current network is Wi-Fi, Ethernet, or VPN, the banner stays cleared, regardless of whether the system has
 * selected one of them as the default route.
 *
 * VPN (e.g. ZeroTier, Tailscale) is intentionally included because TCP nodes are routinely reachable over a routed
 * overlay just as over a physical LAN. Cellular is intentionally **excluded** — a carrier uplink alone does not put the
 * device on the same L2/L3 segment as a Meshtastic node, so cellular-only must keep the banner shown.
 *
 * This reduction deliberately does **not** require `NET_CAPABILITY_VALIDATED` or `NET_CAPABILITY_INTERNET`: a local
 * mesh access point or a VPN without upstream may be unvalidated yet still carry the TCP traffic the scan needs. The
 * previous implementation only inspected the default network and only recognized Wi-Fi/Ethernet, so the banner stayed
 * stuck whenever Android kept cellular as default (or Wi-Fi was connected but unvalidated), and failed to clear when a
 * VPN provided the actual reachability path.
 */
internal fun anyNetworkScanTransportAvailable(networks: List<NetworkTransportInfo>): Boolean =
    networks.any { it.hasWifi || it.hasEthernet || it.hasVpn }

/**
 * Returns `true` when the "Wi-Fi unavailable" recovery banner should render in `ConnectionsScreen`.
 *
 * Banner shows only while a network scan is actively running, local-network permission is granted, WiFi is unavailable,
 * and the scan has not yet produced any discovered TCP nodes. The auto-scan case is covered because `isNetworkScanning`
 * is true during auto-scan regardless of the user's active Connections pane.
 *
 * Gating on the scan state keeps the banner silent while discovery is idle — the user only needs the recovery hint at
 * the moment a scan cannot find a usable transport. The [localNetworkPermissionGranted] guard keeps the banner from
 * overlapping the permission-request flow on the scan toggle.
 *
 * The banner is a recovery hint for the case where the user has started a network scan but no nodes have been
 * discovered yet. Once the scan produces results, the user has found what they were looking for and the hint is no
 * longer useful — so the banner is suppressed when the discovered-TCP list is non-empty.
 */
fun shouldShowWifiUnavailableBanner(
    isNetworkScanning: Boolean,
    localNetworkPermissionGranted: Boolean,
    wifiUnavailable: Boolean,
    discoveredTcpDevicesEmpty: Boolean,
): Boolean = isNetworkScanning && localNetworkPermissionGranted && wifiUnavailable && discoveredTcpDevicesEmpty
