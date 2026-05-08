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

Filter the node list by:
- Online/offline status
- Favorites only
- Role type
- Distance range

Sort by:
- Last heard (default)
- Name (alphabetical)
- Distance
- Signal quality

## Node Detail

Tapping a node opens the detail view with comprehensive information. See [Node Metrics](node-metrics.md) for full details on metrics and telemetry.

---

*Screenshots will be added when the screenshot automation pipeline is operational.*

