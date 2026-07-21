---
title: Home Screen Widget
parent: Kasutaja juhis
nav_order: 20
last_updated: 2026-06-25
description: Add the Meshtastic home screen widget to glance at your connected radio's local stats without opening the app.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Home Screen Widget

On Android, Meshtastic provides a home screen **widget** that shows live local statistics from your connected radio at a glance — no need to open the app.

## What It Shows

The widget displays the **connected radio's** current local stats:

- **Aku** – raadio aku tase või _Toitel_, kui see töötab välise toiteallikaga
- **ChUtil** — kanali kasutus (kui hõivatud on LoRa kanal protsentides)
- **AirUtil** — eetriaega (kui suurt osa töötsüklist raadio edastab)
- **Traffic** — packets transmitted / received, and duplicates seen
- **Relays** — packets relayed and relay cancellations (shown when the radio is relaying)

Tap the widget to open the app, or use its refresh control to request fresh stats.

> 💡 **Tip:** The values reflect the radio you are currently connected to. If the app isn't connected to a radio, the widget shows the last known stats until it reconnects.

## Adding the Widget

1. Long-press an empty area of your Android home screen.
2. Tap **Widgets**.
3. Find **Meshtastic** in the list and drag the **Local Stats** widget to your home screen.
4. Resize it as needed — the layout adapts to the available space.

> ⚠️ **Note:** The widget is Android-only. It is not available on the Desktop or iOS builds.

## Related Topics

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Connections](connections) — connect to a radio so the widget has stats to show
- [Avasta](Discovery) — kanali ja eetriaja kasutamine kärgvõrgu ulatuses

---
