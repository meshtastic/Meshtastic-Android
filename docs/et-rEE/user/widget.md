---
title: Home Screen Widget
parent: Kasutaja juhis
nav_order: 20
last_updated: 2026-06-25
description: Lisa Meshtastici avakuva vidin, et vaadata ühendatud raadio kohalikku statistikat ilma rakendust avamata.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Home Screen Widget

Androidis pakub Meshtastic avakuva **vidinat**, mis näitab teie ühendatud raadio reaalajas kohalikku statistikat – rakendust pole vaja avada.

## What It Shows

The widget displays the **connected radio's** current local stats:

- **Aku** – raadio aku tase või _Toitel_, kui see töötab välise toiteallikaga
- **ChUtil** — kanali kasutus (kui hõivatud on LoRa kanal protsentides)
- **AirUtil** — eetriaega (kui suurt osa töötsüklist raadio edastab)
- **Traffic** — packets transmitted / received, and duplicates seen
- **Relays** — packets relayed and relay cancellations (shown when the radio is relaying)

Tap the widget to open the app, or use its refresh control to request fresh stats.

> 💡 **Vihje:** Väärtused kajastavad raadiojaama, millega olete hetkel ühendatud. If the app isn't connected to a radio, the widget shows the last known stats until it reconnects.

## Adding the Widget

1. Long-press an empty area of your Android home screen.
2. Tap **Widgets**.
3. Leia loendist **Meshtastic** ja lohista **Kohaliku statistika** vidin oma avakuvale.
4. Resize it as needed — the layout adapts to the available space.

> ⚠️ **Märkus:** Vidin on ainult Androidile. It is not available on the Desktop or iOS builds.

## Related Topics

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Connections](connections) — connect to a radio so the widget has stats to show
- [Avasta](Discovery) — kanali ja eetriaja kasutamine kärgvõrgu ulatuses

---
