---
title: Aloitusnäytön widget
nav_order: 20
last_updated: 2026-10-21
description: Add the Meshtastic home screen widget to glance at your connected node's local stats without opening the app.
aliases:
  - widget
  - aloitusnäytön widget
  - paikalliset tilastot widget
---

# Aloitusnäytön widget

On Android, Meshtastic provides a home screen **widget** that shows live local statistics from your connected node at a glance, without opening the app.

## Mitä se näyttää

The widget displays the **connected node's** current local stats:

- A **node chip** across the top, carrying the node's short name in its own colors
- **Battery** — the node's battery level, or _Powered_ when running on external power
- **ChUtil** — kanavan käyttöaste (kuinka kuormitettu LoRa-kanava on prosentteina)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Liikenne** — lähetetyt ja vastaanotetut paketit sekä havaitut kaksoiskappaleet
- **Relays** — packets relayed and relay cancellations (shown when the node is relaying)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the node discarded). Bad and Dropped appear only once they are above zero, so a quiet node may show the noise reading alone
- **Heap** — free versus total memory on the node, drawn as a bar
- **Radiot** — kuinka monta radiota on verkossa tunnetusta kokonaismäärästä
- **Uptime** — how long the node has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

Avaa sovellus napauttamalla widgetiä tai pyydä uudet tilastot sen päivityspainikkeella.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. Se ei säilytä viimeisimpiä arvoja näytöllä.

## Widgetin lisääminen

1. Kosketa Androidin aloitusnäytön tyhjää aluetta pitkään.
2. Napauta **Widgetit**.
3. Vedä **Meshtastic**-pienoissovellus aloitusnäyttöön. The app ships one widget, so the picker entry is the app name.
4. Muuta widgetin kokoa tarvittaessa — asettelu mukautuu käytettävissä olevaan tilaan.

> ℹ️ **Huomautus:** Pienoissovellus on käytettävissä vain Androidissa. It is not available in the desktop app.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — täydellinen signaalin laatu- ja paikalliset tilastot -historia sovelluksessa
- [Connections](connections) — connect to a node so the widget has stats to show
- [Paikallinen mesh-verkon etsintä](discovery) — kanavan ja lähetysajan käyttö koko mesh-verkossa
