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

Androidis pakub Meshtastic avakuva **vidinat**, mis näitab ühendatud raadio reaalajas kohalikku statistikat – rakendust pole vaja avada.

## Mida see näitab

The widget displays the **connected radio's** current local stats:

- **Aku** – raadio aku tase või _Toitel_, kui see töötab välise toiteallikaga
- **ChUtil** — kanali kasutus (kui hõivatud on LoRa kanal protsentides)
- **AirUtil** — eetriaega (kui suurt osa töötsüklist raadio edastab)
- **Liiklus** — edastatud/vastuvõetud paketid ja nähtud duplikaadid
- **Vahendajad** — edastatud paketid ja edastuste tühistamised (kuvatakse raadio vahendusprotsessi ajal)

Tap the widget to open the app, or use its refresh control to request fresh stats.

> 💡 **Vihje:** Väärtused kajastavad raadiojaama, millega olete hetkel ühendatud. Kui rakendus pole raadioga ühendatud, kuvab vidin viimaseid teadaolevaid andmeid kuni ühenduse taastamiseni.

## Adding the Widget

1. Long-press an empty area of your Android home screen.
2. Tap **Widgets**.
3. Leia loendist **Meshtastic** ja lohista **Kohaliku statistika** vidin oma avakuvale.
4. Resize it as needed — the layout adapts to the available space.

> ⚠️ **Märkus:** Vidin on ainult Androidile. It is not available on the Desktop or iOS builds.

## Related Topics

- [Node Metrics](node-metrics) — the full Signal Quality and Local Stats history inside the app
- [Ühendused](connections) — loo ühendus raadioga, et vidinal oleks statistikat kuvada
- [Avasta](Discovery) — kanali ja eetriaja kasutamine kärgvõrgu ulatuses

---
