---
title: Home Screen Widget
nav_order: 20
last_updated: 2026-11-15
description: Add Meshtastic home screen widgets to glance at your connected node's local stats, keep an eye on one node you pick, or read one node's sensors, without opening the app.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
  - node-tracker-widget
  - telemetry-widget
---

# Home Screen Widget

On Android, Meshtastic provides three home screen **widgets**:

- **Meshtastic** shows live local statistics from your connected node.
- **Node Tracker** follows one node you choose: its battery, signal, when it was last heard, how many hops away it is, and how far it is from your connected node.
- **Telemetry** shows one node's environment, air quality and power readings as tiles.

You can place as many of each as you like. Each Node Tracker and Telemetry widget remembers its own node.

## Kohalik statistika

The **Meshtastic** widget displays the **connected node's** current local stats:

- A **node chip** carrying the node's short name in its own colors, with the **battery** level beside it, or _Powered_ when running on external power
- **ChUtil** — kanali kasutus (kui hõivatud on LoRa kanal protsentides)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Nodes** — how many nodes are online, out of the total known
- **Uptime** — how long the node has been running since its last reboot
- **Sent** and **Received** — the node's packet counters
- **Noise floor** — the background noise level in dBm
- **Free memory** — memory the node has left
- **Relayed** — packets the node relayed for others
- **Bad** — corrupt packets received
- **Dropped** — packets the node discarded
- **Relays canceled** — relays the node started and then canceled
- **Updated** — the time the stats last refreshed, along the foot of a three-row widget

The widget shows as many of those as its size holds, in that order, so a small widget keeps the readings people glance at most. A two-by-two widget shows the node, battery, utilization and the first few readings; four cells wide lays the readings out in two columns, and a taller widget adds lines until every reading is on screen.

Tap the widget to open the app, or use its refresh control to request fresh stats.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Node Tracker

The **Node Tracker** widget shows one node from your node list:

- The node's **chip** and **long name**
- **Battery**: the node's last reported battery level, or _Powered_ on external power
- **Last heard**: the time your connected node last heard it, or the date if it was more than a day ago

From two rows tall it adds, in this order, as many as fit (a wide widget under two rows tall shows Hops Away beside the name):

- **Hops Away**: how many hops away the node is, or _Direct_
- **Distance**: how far the node is from your connected node. It appears only while you are connected and both nodes have a position
- **SNR** and **RSSI**: the signal of the node's last packet, shown only when the node is heard directly rather than through other nodes or MQTT

Four cells wide lays the readings out in two columns, so at the default font size every one fits in a two-row widget. A larger font size drops readings from the end of that list, RSSI first.

When a node has not been heard within the last two hours, the widget dims its readings so they do not read as current. Tap the widget to open that node's details in the app.

After you disconnect from your node in the app, the widget keeps the tracked node's last readings on screen, dimmed, until you reconnect.

If your node list does not include the node you picked, because you removed it or connected to a different node, the widget says **Node not found**. Tap it to choose another node.

## Telemeetria

The **Telemetry** widget shows one node's sensor readings as tiles, the same tiles the node's details show, with the same units and precision:

- **Environment**: temperature, humidity, pressure, dew point, gas resistance, IAQ, light, wind, rain, soil temperature, soil moisture, radiation and weight
- **Air quality**: PM1.0, PM2.5, PM10 and CO₂
- **Power**: voltage and current for each channel, named with the labels you gave the channels

It shows every reading the node reports, in that order, and you can hide any of them. The node's chip sits beside the readings, and the line under them says when the newest reading arrived.

- A widget narrower than two columns shows the first reading.
- A widget under two rows tall shows the first readings in a row: two up to three columns wide, four from four columns.
- A widget at least two columns by two rows shows four at a time in a grid. With more than four readings, tap the page button in its corner, such as **1/2**, to see the next four.

A reading the node has stopped sending shows a dash, while a reading of zero shows 0. Once the newest reading is more than two hours old, the line under the tiles says **Over 2 hours old**. The age comes from the app's packet log, so with logging turned off the widget can't tell when a reading arrived: it shows no age, and says **Over 2 hours old** only once the node itself has not been heard for two hours.

The widget shows readings the app has already saved, so it works with no radio connected, and it never sends anything over the mesh. Tap it to open the node's metrics in the app. If its node is no longer in your node list, it says **No node chosen. Tap to choose one.**, and tapping it opens the widget's setup screen, where **Change node** opens the node picker.

## Adding a Widget

1. Touch & hold an empty area of your Android home screen.
2. Tap **Widgets**, then find **Meshtastic** in the list.
3. Drag the **Meshtastic** (local stats), **Node Tracker** or **Telemetry** widget to your home screen.
4. For a Node Tracker, pick the node to follow. Your favorites are listed first, then the most recently heard nodes; type in the search field to filter by name or node ID. Backing out without picking cancels the widget.
5. For a Telemetry widget, the setup screen starts on your connected node when it reports any of these readings. Tap **Change node** to pick another; only nodes that have sent environment, air quality or power readings are listed, and the search field filters them by name or node ID. Turn off any reading you don't want, then tap **Save**.
6. Resize it as needed — the layout adapts to the available space.

To track a different node or change the readings later, touch & hold the widget and choose the widget's settings or reconfigure option. The exact wording depends on your launcher.

> ℹ️ **Note:** The widgets are Android-only. They are not available in the desktop app.

## Seotud teemad

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Nodes](nodes) — favorites, last heard, and node details
- [Connections](connections) — connect to a node so the widgets have data to show
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
