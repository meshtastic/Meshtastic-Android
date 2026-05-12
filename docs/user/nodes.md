---
title: Nodes
nav_order: 4
aliases:
  - node-list
  - mesh-nodes
  - peers
---

# Nodes

The Nodes screen displays all devices visible on your mesh network.

## Node List

The node list shows every node your radio has heard, including:
- **Node name** — user-configured long name
- **Short name** — 4-character identifier
- **Signal quality** — last heard signal strength
- **Last heard** — time since last communication
- **Distance** — estimated distance (if positions are shared)
- **Battery** — remote node battery level (if telemetry is enabled)

### Node Status Indicators

| Badge | Meaning |
|-------|---------|
| 🟢 Online | Node heard within the last 15 minutes |
| 🟡 Away | Node heard within the last 2 hours |
| 🔴 Offline | Node not heard for over 2 hours |
| ⭐ Favorite | Node marked as favorite by the user |

### Node Roles

Nodes can be configured with different roles that affect their mesh behavior:

| Role | Description |
|------|-------------|
| Client | Standard end-user device |
| Client Mute | Receives but doesn't retransmit |
| Router | Prioritizes message forwarding |
| Router Client | Routes and operates as a client |
| Repeater | Retransmits only; no user interface |
| Tracker | Optimized for position reporting |
| Sensor | Optimized for telemetry reporting |
| TAK | Interoperates with TAK systems |
| TAK Tracker | TAK position reporting |
| Lost & Found | Continuous position beacon |

## Quick Actions

From the node list, you can:
- **Tap** a node to view its detail page
- **Long-press** for quick actions:
  - Send a direct message
  - View on map
  - Request position
  - Mark as favorite
  - Traceroute

## Filtering & Sorting

### Text Search

Type in the search field to filter nodes by name or short name. The filter updates in real time as you type.

### Filter Toggles

| Filter | Description |
|--------|-------------|
| **Only online** | Show only nodes heard within the last 15 minutes |
| **Only direct** | Show only nodes with direct (non-relayed) connections |
| **Include unknown** | Show nodes that haven't sent user info yet |
| **Exclude infrastructure** | Hide infrastructure-role nodes (Router, Repeater, Router Client) |
| **Exclude MQTT** | Hide nodes heard only via MQTT internet bridge |
| **Show ignored** | Show nodes you've previously dismissed or muted |

### Sort Options

| Sort | Description |
|------|-------------|
| **Last heard** (default) | Most recently heard nodes first |
| **Alphabetical** | Sorted by node long name |
| **Distance** | Nearest nodes first (requires position sharing) |
| **Hops away** | Fewest relay hops first |
| **Channel** | Grouped by channel index |
| **Via MQTT** | Grouped by MQTT vs. radio-heard |
| **Favorites** | Favorited nodes first |

## Node Detail

Tapping a node opens the detail view with comprehensive information. See [Node Metrics](node-metrics) for full details on metrics and telemetry.

![Node detail view](/assets/screenshots/nodes_node_list.png)

The detail screen includes device info, position, and action buttons:

![Node detail section](/assets/screenshots/nodes_detail_section.png)

Inline status indicators show key metrics at a glance:

| Indicator | Screenshot |
|-----------|------------|
| Signal quality | ![Signal](/assets/screenshots/nodes_signal_info.png) |
| Battery level | ![Battery](/assets/screenshots/nodes_battery_info.png) |
| Hop count | ![Hops](/assets/screenshots/nodes_hops_info.png) |
| Last heard | ![Last heard](/assets/screenshots/nodes_last_heard.png) |
| Distance | ![Distance](/assets/screenshots/nodes_distance_info.png) |

---

