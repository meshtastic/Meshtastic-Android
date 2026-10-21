---
title: Виджет на главный экран
nav_order: 20
last_updated: 2026-10-21
description: Add the Meshtastic home screen widget to glance at your connected node's local stats without opening the app.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Виджет на главный экран

On Android, Meshtastic provides a home screen **widget** that shows live local statistics from your connected node at a glance, without opening the app.

## Что он покажет

The widget displays the **connected node's** current local stats:

- A **node chip** across the top, carrying the node's short name in its own colors
- **Battery** — the node's battery level, or _Powered_ when running on external power
- **ChUtil** — использование канала (насколько занят канал LoRa, в процентах)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
- **Трафик** — пакеты, переданные / полученные, и дубликаты
- **Relays** — packets relayed and relay cancellations (shown when the node is relaying)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the node discarded). Bad and Dropped appear only once they are above zero, so a quiet node may show the noise reading alone
- **Heap** — free versus total memory on the node, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known
- **Uptime** — how long the node has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

Нажми на виджет, чтобы открыть приложение, или используй его кнопку обновления для запроса свежей статистики.

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Добавление виджета

1. Touch & hold an empty area of your Android home screen.
2. Нажми **Виджет**.
3. Drag the **Meshtastic** widget to your home screen. The app ships one widget, so the picker entry is the app name.
4. Изменение размера по мере необходимости — макет адаптируется к доступному пространству.

> ℹ️ **Note:** The widget is Android-only. It is not available in the desktop app.

## Связанные темы

- [Метрики нод](node-metrics) — полная статистика сигнала и локальная статистика внутри приложения
- [Connections](connections) — connect to a node so the widget has stats to show
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
