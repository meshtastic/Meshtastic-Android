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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the platform-agnostic reduction that drives `isWifiUnavailable()` on Android. `ConnectivityManager`
 * itself is not unit-testable here (it needs an Android runtime), so these cases exercise the pure helper that the
 * Android actual delegates to. The predicate: network-scan transport is available when any current network has Wi-Fi,
 * Ethernet, or VPN; cellular-only is insufficient. Each case mirrors a real connectivity snapshot the
 * `ConnectionsScreen` recovery banner must react to correctly.
 */
class NetworkTransportTest {
    @Test
    fun wifi_network_present_then_local_available() {
        // Single Wi-Fi network: banner should clear.
        assertTrue(
            anyNetworkScanTransportAvailable(
                listOf(NetworkTransportInfo(hasWifi = true, hasEthernet = false, hasVpn = false)),
            ),
        )
    }

    @Test
    fun ethernet_network_present_then_local_available() {
        // Ethernet (e.g. desktop dock / Android tablet on wired LAN) also carries NSD/mDNS traffic.
        assertTrue(
            anyNetworkScanTransportAvailable(
                listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = true, hasVpn = false)),
            ),
        )
    }

    @Test
    fun only_cellular_network_then_local_unavailable() {
        // Cellular-only: no LAN, banner should show.
        assertFalse(
            anyNetworkScanTransportAvailable(
                listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false)),
            ),
        )
    }

    @Test
    fun wifi_present_as_non_default_alongside_cellular_then_local_available() {
        // The regression case: cellular is the system default (or Wi-Fi is unvalidated), so the
        // previous `activeNetwork` check missed Wi-Fi. With allNetworks scanning, the banner clears.
        val cellular = NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false)
        val wifi = NetworkTransportInfo(hasWifi = true, hasEthernet = false, hasVpn = false)
        assertTrue(anyNetworkScanTransportAvailable(listOf(cellular, wifi)))
    }

    @Test
    fun no_networks_then_local_unavailable() {
        // Airplane mode / no connectivity at all.
        assertFalse(anyNetworkScanTransportAvailable(emptyList()))
    }

    @Test
    fun wifi_lost_across_all_networks_then_local_unavailable() {
        // Previously had Wi-Fi, now every tracked network lacks every scan transport: banner returns.
        val allDropped =
            listOf(
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false),
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false),
            )
        assertFalse(anyNetworkScanTransportAvailable(allDropped))
    }

    @Test
    fun wifi_restored_as_non_default_after_loss_then_local_available() {
        // Symmetric to `wifi_lost_...`: after Wi-Fi returns (even as a non-default network), banner
        // clears again. Encoded as a state transition through the pure function.
        val duringOutage = listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false))
        assertFalse(anyNetworkScanTransportAvailable(duringOutage))
        val afterRecovery =
            listOf(
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false),
                NetworkTransportInfo(hasWifi = true, hasEthernet = false, hasVpn = false),
            )
        assertTrue(anyNetworkScanTransportAvailable(afterRecovery))
    }

    @Test
    fun vpn_only_then_local_available() {
        // ZeroTier/Tailscale carry TCP traffic to a node over the routed overlay; banner should clear
        // even when no physical LAN transport is present.
        assertTrue(
            anyNetworkScanTransportAvailable(
                listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true)),
            ),
        )
    }

    @Test
    fun wifi_and_vpn_then_local_available() {
        // Wi-Fi plus an active VPN overlay: both transports independently clear the banner.
        val wifi = NetworkTransportInfo(hasWifi = true, hasEthernet = false, hasVpn = false)
        val vpn = NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true)
        assertTrue(anyNetworkScanTransportAvailable(listOf(wifi, vpn)))
    }

    @Test
    fun ethernet_and_vpn_then_local_available() {
        // Wired plus VPN: same as the Wi-Fi+VPN case for a docked desktop/tablet.
        val ethernet = NetworkTransportInfo(hasWifi = false, hasEthernet = true, hasVpn = false)
        val vpn = NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true)
        assertTrue(anyNetworkScanTransportAvailable(listOf(ethernet, vpn)))
    }

    @Test
    fun cellular_and_vpn_then_local_available() {
        // The original bug report: cellular is the only physical uplink, but a VPN rides on top of it
        // and that VPN is the reachability path to the node. VPN clears the banner despite cellular.
        val cellular = NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false)
        val vpn = NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true)
        assertTrue(anyNetworkScanTransportAvailable(listOf(cellular, vpn)))
    }

    @Test
    fun vpn_lost_leaving_cellular_only_then_local_unavailable() {
        // Symmetric to `cellular_and_vpn_...`: when the VPN drops, only cellular remains, and the
        // banner must return. Encoded as a state transition through the pure function.
        val withVpn =
            listOf(
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false),
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true),
            )
        assertTrue(anyNetworkScanTransportAvailable(withVpn))
        val afterVpnLost = listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false))
        assertFalse(anyNetworkScanTransportAvailable(afterVpnLost))
    }

    @Test
    fun wifi_lost_leaving_vpn_then_local_available() {
        // Wi-Fi drops but the VPN (now riding cellular) still reaches the node: banner stays cleared.
        val duringWifi =
            listOf(
                NetworkTransportInfo(hasWifi = true, hasEthernet = false, hasVpn = false),
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true),
            )
        assertTrue(anyNetworkScanTransportAvailable(duringWifi))
        val afterWifiLost = listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true))
        assertTrue(anyNetworkScanTransportAvailable(afterWifiLost))
    }

    @Test
    fun vpn_disabled_leaving_cellular_only_then_local_unavailable() {
        // The closing bug-report case: ZeroTier disabled, only cellular remains — banner returns.
        val withVpn =
            listOf(
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false),
                NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true),
            )
        assertTrue(anyNetworkScanTransportAvailable(withVpn))
        val afterVpnDisabled = listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = false))
        assertFalse(anyNetworkScanTransportAvailable(afterVpnDisabled))
    }
}

/**
 * Coverage for the [shouldShowWifiUnavailableBanner] gate consumed by `ConnectionsScreen`. The banner surfaces "no
 * usable transport for NSD/mDNS scan" only while a network scan is actively running, and is suppressed once the scan
 * has produced discovered TCP nodes — at that point the user has found what they were looking for and the recovery hint
 * is no longer useful. Each case mirrors one of the reported combinations of (scan-active, results-empty) plus the
 * permission and transport-availability guards.
 */
class WifiUnavailableBannerTest {
    private fun transports(wifi: Boolean = false) =
        // Reuses the predicate's encoding so the banner tests stay in lock-step with transport semantics.
        listOf(NetworkTransportInfo(hasWifi = wifi, hasEthernet = false, hasVpn = false))

    @Test
    fun scan_inactive_permission_granted_cellular_only_then_banner_hidden() {
        // Pins the user-reported bug: no scan is running, permission granted, cellular-only
        // transport. The banner must NOT render — the user is not actively trying to discover, so
        // the recovery hint is noise.
        assertFalse(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = false,
                localNetworkPermissionGranted = true,
                wifiUnavailable = !anyNetworkScanTransportAvailable(transports()),
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun scan_active_permission_granted_cellular_only_then_banner_shows() {
        // The auto-scan case: scan running in the background with cellular-only transport. Chip
        // visibility no longer gates this surface — `isNetworkScanning` is the only activity
        // signal — so the banner surfaces the missing-transport hint to the user.
        assertTrue(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = true,
                wifiUnavailable = !anyNetworkScanTransportAvailable(transports()),
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun scan_active_permission_granted_wifi_unavailable_then_banner_shows() {
        // Clean positive case: scan actively running, permission granted, WiFi unavailable, no
        // discovered nodes yet — banner fires. This is the only combination the banner is meant
        // to surface.
        assertTrue(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = true,
                wifiUnavailable = true,
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun permission_denied_then_banner_hidden_even_when_scanning() {
        // Permission-recovery flow on the scan toggle owns this surface; banner must not overlap.
        assertFalse(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = false,
                wifiUnavailable = !anyNetworkScanTransportAvailable(transports()),
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun wifi_present_then_banner_hidden_even_when_scanning() {
        // VPN off + Wi-Fi on + scanning → no banner (preserves the existing "transport available" outcome).
        assertFalse(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = true,
                wifiUnavailable = !anyNetworkScanTransportAvailable(transports(wifi = true)),
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun vpn_only_then_banner_hidden_even_when_scanning() {
        // VPN on + Wi-Fi off + scanning → no banner (VPN is a valid reachability path).
        val vpnOnly = listOf(NetworkTransportInfo(hasWifi = false, hasEthernet = false, hasVpn = true))
        assertFalse(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = true,
                wifiUnavailable = !anyNetworkScanTransportAvailable(vpnOnly),
                discoveredTcpDevicesEmpty = true,
            ),
        )
    }

    @Test
    fun scan_active_permission_granted_wifi_unavailable_results_found_then_banner_hidden() {
        // Once the live scan has produced discovered TCP nodes, the recovery hint is no longer
        // useful — the user has found what they were looking for. Banner stays hidden even though
        // scan is active, permission is granted, and WiFi is reported unavailable.
        assertFalse(
            shouldShowWifiUnavailableBanner(
                isNetworkScanning = true,
                localNetworkPermissionGranted = true,
                wifiUnavailable = true,
                discoveredTcpDevicesEmpty = false,
            ),
        )
    }
}
