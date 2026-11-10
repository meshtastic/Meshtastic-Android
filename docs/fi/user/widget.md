---
title: Aloitusnäytön widget
nav_order: 20
last_updated: 2026-11-04
description: Add Meshtastic home screen widgets to glance at your connected node's local stats, or keep an eye on one node you pick, without opening the app.
aliases:
  - widget
  - aloitusnäytön widget
  - paikalliset tilastot widget
  - node-tracker-widget
---

# Aloitusnäytön widget

On Android, Meshtastic provides two home screen **widgets**:

- **Meshtastic** shows live local statistics from your connected node.
- **Node Tracker** follows one node you choose: its battery, signal, when it was last heard, how many hops away it is, and how far it is from your connected node.

You can place as many of each as you like. Each Node Tracker remembers its own node.

## Paikalliset tilastot

The **Meshtastic** widget displays the **connected node's** current local stats:

- A **node chip** carrying the node's short name in its own colors, with the **battery** level beside it, or _Powered_ when running on external power
- **ChUtil** — kanavan käyttöaste (kuinka kuormitettu LoRa-kanava on prosentteina)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Radiot** — kuinka monta radiota on verkossa tunnetusta kokonaismäärästä
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

Avaa sovellus napauttamalla widgetiä tai pyydä uudet tilastot sen päivityspainikkeella.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. Se ei säilytä viimeisimpiä arvoja näytöllä.

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

## Adding a Widget

1. Kosketa Androidin aloitusnäytön tyhjää aluetta pitkään.
2. Tap **Widgets**, then find **Meshtastic** in the list.
3. Drag the **Meshtastic** (local stats) or **Node Tracker** widget to your home screen.
4. For a Node Tracker, pick the node to follow. Your favorites are listed first, then the most recently heard nodes; type in the search field to filter by name or node ID. Backing out without picking cancels the widget.
5. Muuta widgetin kokoa tarvittaessa — asettelu mukautuu käytettävissä olevaan tilaan.

To track a different node later, touch & hold the Node Tracker and choose the widget's settings or reconfigure option. The exact wording depends on your launcher.

> ℹ️ **Note:** The widgets are Android-only. They are not available in the desktop app.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — täydellinen signaalin laatu- ja paikalliset tilastot -historia sovelluksessa
- [Nodes](nodes) — favorites, last heard, and node details
- [Connections](connections) — connect to a node so the widgets have data to show
- [Paikallinen mesh-verkon etsintä](discovery) — kanavan ja lähetysajan käyttö koko mesh-verkossa
