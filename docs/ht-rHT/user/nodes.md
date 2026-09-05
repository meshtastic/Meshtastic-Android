---
title: Nodes
parent: User Guide
nav_order: 4
last_updated: 2026-09-04
description: Browse, filter, and sort mesh nodes — view details, signal quality, roles, and quick actions.
aliases:
  - node-list
  - mesh-nodes
  - peers
  - hop-histogram
---

# Nodes

The Nodes screen lists every node visible on your mesh.

## Node List

The node list shows every node your radio has heard, including:

- **Node name** — user-configured long name
- **Short name** — 4-character identifier
- **Signal quality** — SNR, RSSI, and a quality word, shown only for nodes your radio heard directly. In the Complete layout a node reached through a relay shows its hop count here instead; a node heard only over MQTT shows neither
- **Last heard** — time since last communication
- **Distance** — estimated distance (if positions are shared)
- **Battery** — remote node battery level (if telemetry is enabled)

### Choosing What the List Shows

The list has two densities, set at **Settings → Node Layout**. **Complete** shows every field a node has reported and hides the ones it hasn't. **Compact** fits more nodes on screen and lets you pick the fields yourself — **Power**, **Last Heard Time**, **Relative Last Heard Time**, **Distance and Bearing**, **Hops Away**, **Signal (Direct Only)**, **Channel**, and **Device & Role**. The **Environment Metrics** toggle applies to both densities. A preview above the toggles shows the effect before you leave the screen.

### Node Status Indicators

| Indicator             | Meaning                                        |
| --------------------- | ---------------------------------------------- |
| Green last-heard time | Node heard within the last 2 hours             |
| Plain last-heard time | Node not heard for over 2 hours                |
| ⭐ Favorite            | Node you marked as a favorite. |

There is no separate "away" tier.

### Node Roles

Nodes can be configured with different roles that affect their mesh behavior:

| Role              | Deskripsyon                                                                                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Client            | Standard end-user node                                                                                                                                 |
| Client Base       | Treats favorited-node traffic as Router Late priority; all other traffic as Client                                                                     |
| Client Mute       | Receives but doesn't retransmit                                                                                                                        |
| Client Hidden     | Like Client Mute, plus hides from node list                                                                                                            |
| Router            | Prioritizes message forwarding; stays awake to relay                                                                                                   |
| Router Late       | Infrastructure node that rebroadcasts once, but only after all other modes (provides supplemental coverage)                         |
| ~~Router Client~~ | ⚠️ **Deprecated** (removed in firmware 2.3.15) — no longer selectable; use Router or Client instead |
| ~~Repeater~~      | ⚠️ **Deprecated** (removed in firmware 2.7.11) — no longer selectable; use Router instead           |
| Tracker           | Optimized for position reporting at regular intervals                                                                                                  |
| Sensor            | Optimized for telemetry reporting                                                                                                                      |
| TAK               | Interoperates with TAK systems (sends/receives CoT)                                                                                 |
| TAK Tracker       | TAK position reporting only                                                                                                                            |
| Lost and Found    | Sends its position to the default channel as a text message at regular intervals, to help recover a lost radio                                         |

### Choosing a Role

Most users should keep the default **Client** role. Consider a different role when:

- **Router** — You have a node in a fixed, elevated location with reliable power (rooftop, hilltop). Routers stay awake continuously to relay messages for others and are essential for extending mesh coverage. Don't use Router on battery-powered handheld radios.
- **Router Late** — An infrastructure node that always rebroadcasts packets once but only after all other routing modes have had their turn. Provides supplemental coverage for local clusters without competing with primary routers.
- **Client Base** — Treats traffic from/to your favorited nodes with Router Late priority (ensuring those messages get extra relay coverage) while handling everything else as a normal Client.
- **Client Mute** — You want to receive mesh traffic but not contribute to relaying. Useful for monitoring-only radios or to reduce congestion in dense areas.
- **Tracker** — An unattended radio whose sole purpose is broadcasting its GPS position (e.g., a vehicle, pet, or asset). Sleeps between broadcasts to conserve battery.
- **Sensor** — An unattended radio reporting environmental telemetry (temperature, humidity, air quality). Similar power profile to Tracker.
- **TAK / TAK Tracker** — Only needed if interoperating with ATAK/WinTAK systems. See [TAK Integration](tak) for details.

> 💡 **Tip:** The mesh works best when most nodes are **Client** or **Router**. Too many Client Mute nodes reduce mesh resilience; too many Routers in a dense area can cause congestion. A good rule of thumb: one Router per 5–10 Clients in your area.

### Encryption Indicators

Nodes display encryption status icons next to their name:

| Icon        | Meaning                                                                                                             |
| ----------- | ------------------------------------------------------------------------------------------------------------------- |
| 🔒 Locked   | Communication uses PKI (public key infrastructure) — end-to-end encrypted with verified identity |
| 🔓 Unlocked | Communication uses shared channel PSK — encrypted but identity not individually verified                            |
| ⚠️ Mismatch | Public key mismatch — the node's key has changed since last seen (investigate before trusting)   |

> 💡 **Tip:** PKI encryption (firmware 2.5+) provides stronger security than channel PSK because each node has a unique key pair. If you see a key mismatch warning, the node may have been reset or compromised.

To clear a mismatch, first confirm through another trusted channel that the key change was intentional — a factory reset causes one. Then touch & hold the node, choose **Remove**, and let the two radios exchange keys again the next time yours hears it.

## Quick Actions

From the node list, you can:

- **Tap** a node to view its detail page
- **Touch & hold** for quick actions:
  - Mark/remove favorite
  - Mute/unmute notifications
  - Send a direct message
  - Trace route
  - Ignore/unignore
  - Retire

Touch & hold **your own node** instead and you get one action, **Update status**, which opens the
User settings screen with the cursor already in the Status Message field. It only appears while the
radio is connected and running firmware 2.8 or newer — see
[Settings — Radio & User](settings-radio-user.md) for the field itself.

## Sharing a Contact

On a node's detail screen, tap **Share Contact** to produce a link and a QR code for that node. From the same dialog, **Write to NFC tag** saves the link to a writable NFC tag that anyone can tap to open.

To add someone else's contact, use the import button on the node list and choose **Scan Shared Contact QR Code**, **Scan Shared Contact NFC**, or **Input Shared Contact URL**. The app asks you to confirm with **Import Shared Contact?**, and warns you when the contact is one you already have.

## Filtering & Sorting

### Text Search

Type in the search field to filter nodes by name or short name. The filter updates in real time as you type.

### Filter Toggles

| Filtre                      | Deskripsyon                                                                                                                                                                                       |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Hide offline nodes**      | Show only nodes heard within the last 2 hours                                                                                                                                                     |
| **Only show direct nodes**  | Show only nodes your radio heard directly, with no relay in between                                                                                                                               |
| **Include unknown**         | Show nodes that haven't sent user info yet. **On by default**, so a node heard before its info arrives stays visible and messageable; these carry a badge marking them incomplete |
| **Exclude infrastructure**  | Hide infrastructure-role nodes (Router, Router Late, Client Base, and legacy Repeater nodes) and any node that cannot be messaged, whatever its role                           |
| **Exclude MQTT**            | Hide nodes heard only via MQTT internet bridge                                                                                                                                                    |
| **Only show ignored Nodes** | Replace the list with the nodes you have ignored. Every other node is hidden while this is on, and a banner appears at the top of the list to take you back                       |

### Sort Options

| Sort                                          | Deskripsyon                                                        |
| --------------------------------------------- | ------------------------------------------------------------------ |
| **Last heard**                                | Most recently heard nodes first                                    |
| **A-Z**                                       | Sorted by node long name                                           |
| **Distance**                                  | Nearest nodes first (requires position sharing) |
| **Hops away**                                 | Fewest relay hops first                                            |
| **Channel**                                   | Grouped by channel index                                           |
| **via MQTT**                                  | Grouped by MQTT vs. radio-heard                    |
| **via Favorite** (default) | Favorited nodes first, then the rest                               |

## Nodes per Hop

Tap the hop-histogram icon in the node list's app bar to open a bar chart of how many nodes sit at each hop distance (0 = direct, 1 = one relay away, and so on). Filter the chart to a **last heard** window — All time, 1 hour, 8 hours, or 24 hours — to see how the mesh looks right now versus over a longer period. It's a quick way to gauge how busy and spread out your local mesh is.

## Node Detail

Tapping a node opens the detail view with comprehensive information. See [Node Metrics](node-metrics) for full details on metrics and telemetry.

The Details card carries the node's short name, role, IDs, last heard time, hops away, uptime, and its SNR and RSSI:

![Node detail section](../../assets/screenshots/nodes_detail_section.png)

Inline status indicators show key metrics at a glance:

| Indicator          | Screenshot                                                    |
| ------------------ | ------------------------------------------------------------- |
| Signal quality     | ![Signal](../../assets/screenshots/nodes_signal_info.png)     |
| Battery level      | ![Battery](../../assets/screenshots/nodes_battery_info.png)   |
| Hop count          | ![Hops](../../assets/screenshots/nodes_hops_info.png)         |
| Dènye fwa li tande | ![Last heard](../../assets/screenshots/nodes_last_heard.png)  |
| Distans            | ![Distance](../../assets/screenshots/nodes_distance_info.png) |

### Device Links ("I want one")

When a node's hardware is recognized, the detail view shows a collapsible **"I want one"** section linking to places to buy or learn more about that device: the vendor's product page, product variants, and regional marketplace listings (such as AliExpress, Amazon, and supported retailers), filtered to your country. Each link opens through the `msh.to` redirect service. Devices with no matching links don't show the section.

A full, browsable directory of every link is also available at **Settings → Device Links**. The item is hidden while you have Settings open for a remote node.

## When No Nodes Appear

The list stays empty until your radio hears another node.

- **No device connected** — the app is not connected to a radio. See [Connections](connections).
- **Searching for nodes** — the radio is connected and listening, but nothing has arrived yet. Check that its region and modem preset match the mesh around you, and leave **Include unknown** on so a node that has not yet sent its name still appears. See [Settings — Radio & User](settings-radio-user).
- A node you expect is missing — check the filter toggles. **Only show direct nodes**, **Exclude MQTT**, and **Exclude infrastructure** each hide a whole category of node.

## Related Topics

- [Node Metrics](node-metrics) — detailed telemetry dashboards for each node
- [Messages & Channels](messages-and-channels) — send a direct message to a node
- [Map & Waypoints](map-and-waypoints) — view node positions geographically
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for topology exploration
- [Signal Meter](signal-meter) — understand what the signal bars mean
