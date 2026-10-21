---
title: Widget na domovské obrazovce
nav_order: 20
last_updated: 2026-10-21
description: Add the Meshtastic home screen widget to glance at your connected node's local stats without opening the app.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Widget na domovské obrazovce

On Android, Meshtastic provides a home screen **widget** that shows live local statistics from your connected node at a glance, without opening the app.

## Co zobrazuje

The widget displays the **connected node's** current local stats:

- A **node chip** across the top, carrying the node's short name in its own colors
- **Battery** — the node's battery level, or _Powered_ when running on external power
- **ChUtil** — využití kanálu (jak je kanál LoRa vytížený, v procentech)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Provoz** – pakety odeslané / přijaté a zaznamenané duplikáty
- **Relays** — packets relayed and relay cancellations (shown when the node is relaying)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the node discarded). Bad and Dropped appear only once they are above zero, so a quiet node may show the noise reading alone
- **Heap** — free versus total memory on the node, drawn as a bar
- **Uzly** — počet uzlů online z celkového počtu známých uzlů
- **Uptime** — how long the node has been running since its last reboot, shown beside Nodes
- **Aktualizováno** – čas poslední aktualizace statistik, zobrazený v dolní části widgetu

Klepnutím na widget otevřete aplikaci nebo pomocí tlačítka obnovení vyžádejte aktuální statistiky.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. Nezobrazuje poslední známé hodnoty.

## Přidání widgetu

1. Dotkněte se prázdného místa na domovské obrazovce Androidu a podržte jej.
2. Klepněte na položku **Widgety**.
3. Přetáhněte widget Meshtastic na domovskou obrazovku. The app ships one widget, so the picker entry is the app name.
4. Podle potřeby změňte jeho velikost – rozložení se přizpůsobí dostupnému prostoru.

> ℹ️ **Poznámka:** Widget je k dispozici pouze pro Android. It is not available in the desktop app.

## Související témata

- [Metriky uzlu](node-metrics) — kompletní historie kvality signálu a místních statistik přímo v aplikaci
- [Connections](connections) — connect to a node so the widget has stats to show
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
