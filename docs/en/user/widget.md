---
title: Home Screen Widget
parent: User Guide
nav_order: 20
last_updated: 2026-10-21
description: Add Meshtastic home screen widgets to glance at your connected node's local stats, or keep an eye on one node you pick, without opening the app.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
  - node-tracker-widget
---

# Home Screen Widget

On Android, Meshtastic provides two home screen **widgets**:

- **Meshtastic** shows live local statistics from your connected node.
- **Node Tracker** follows one node you choose: its battery, signal, when it was last heard, how many hops away it is, and how far it is from your connected node.

You can place as many of each as you like. Each Node Tracker remembers its own node.

## Local Stats

The **Meshtastic** widget displays the **connected node's** current local stats:

- A **node chip** across the top, carrying the node's short name in its own colors
- **Battery** — the node's battery level, or *Powered* when running on external power
- **ChUtil** — channel utilization (how busy the LoRa channel is, as a percentage)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Traffic** — packets transmitted / received, and duplicates seen
- **Relays** — packets relayed and relay cancellations (shown when the node is relaying)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the node discarded). Bad and Dropped appear only once they are above zero, so a quiet node may show the noise reading alone
- **Heap** — free versus total memory on the node, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known
- **Uptime** — how long the node has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

Tap the widget to open the app, or use its refresh control to request fresh stats.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Node Tracker

The **Node Tracker** widget shows one node from your node list:

- The node's **chip** and **long name**
- **Battery**: the node's last reported battery level, or *Powered* on external power
- **Last heard**: the time your connected node last heard it, or the date if it was more than a day ago

Widen the widget to add a second column:

- **SNR** and **RSSI**: the signal of the node's last packet, shown only when the node is heard directly rather than through other nodes or MQTT
- **Hops Away**: how many hops away the node is, or *Direct*
- **Distance**: how far the node is from your connected node. It appears only while you are connected and both nodes have a position

When a node has not been heard within the last two hours, the widget dims its readings so they do not read as current. Tap the widget to open that node's details in the app.

After you disconnect from your node in the app, the widget keeps the tracked node's last readings on screen, dimmed, until you reconnect.

If your node list does not include the node you picked, because you removed it or connected to a different node, the widget says **Node not found**. Tap it to choose another node.

## Adding a Widget

1. Touch & hold an empty area of your Android home screen.
2. Tap **Widgets**, then find **Meshtastic** in the list.
3. Drag the **Meshtastic** (local stats) or **Node Tracker** widget to your home screen.
4. For a Node Tracker, pick the node to follow. Your favorites are listed first, then the most recently heard nodes; type in the search field to filter by name or node ID. Backing out without picking cancels the widget.
5. Resize it as needed — the layout adapts to the available space.

To track a different node later, touch & hold the Node Tracker and choose the widget's settings or reconfigure option. The exact wording depends on your launcher.

> ℹ️ **Note:** The widgets are Android-only. They are not available in the desktop app.

## Related Topics

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Nodes](nodes) — favorites, last heard, and node details
- [Connections](connections) — connect to a node so the widgets have data to show
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
