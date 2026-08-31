---
title: Home Screen Widget
parent: Kasutaja juhis
nav_order: 20
last_updated: 2026-08-30
description: Lisa Meshtastici avakuva vidin, et vaadata ühendatud raadio kohalikku statistikat ilma rakendust avamata.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Home Screen Widget

Androidis pakub Meshtastic avakuva **vidinat**, mis näitab ühendatud raadio reaalajas kohalikku statistikat – rakendust pole vaja avada.

## Mida see näitab

The widget displays the **connected radio's** current local stats:

- A **node chip** across the top, carrying the radio's short name in its own colors
- **Aku** – raadio aku tase või _Toitel_, kui see töötab välise toiteallikaga
- **ChUtil** — kanali kasutus (kui hõivatud on LoRa kanal protsentides)
- **AirUtil** — eetriaega (kui suurt osa töötsüklist raadio edastab)
- **Liiklus** — edastatud/vastuvõetud paketid ja nähtud duplikaadid
- **Vahendajad** — edastatud paketid ja edastuste tühistamised (kuvatakse raadio vahendusprotsessi ajal)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the radio discarded). Bad and Dropped appear only once they are above zero, so a quiet radio may show the noise reading alone
- **Heap** — free versus total memory on the radio, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known
- **Uptime** — how long the radio has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

Tap the widget to open the app, or use its refresh control to request fresh stats.

> ℹ️ **Note:** The values reflect the connected radio. If the radio disconnects, the widget replaces the stats with a status line — **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Adding the Widget

1. Touch & hold an empty area of your Android home screen.
2. Tap **Widgets**.
3. Drag the **Meshtastic** widget to your home screen. The app ships one widget, so the picker entry is just the app name.
4. Resize it as needed — the layout adapts to the available space.

> ℹ️ **Note:** The widget is Android-only. It is not available on the Desktop or iOS builds.

## Seotud teemad

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Ühendused](connections) — loo ühendus raadioga, et vidinal oleks statistikat kuvada
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
