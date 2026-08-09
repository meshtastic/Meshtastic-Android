---
title: Wie das Meshtastic Signal Meter funktioniert
parent: User Guide
nav_order: 15
last_updated: 2026-07-08
description: Wie das Signal Meter die Qualität von SNR relativ zum Modem preset bewertet - spread spectrum, presets, und was die Balken wirklich bedeuten.
aliases:
  - Signal
  - Signal-Meter
  - snr
  - rssi
---

# Wie das Meshtastic Signal Meter funktioniert

Die Meshtastic-Signalanzeige – die vertrauten Balken oder die Statusfarbe in der App – wird ganz anders berechnet als die „Balken“ bei einem herkömmlichen Mobiltelefon oder WLAN-Router.

Die meisten Endgeräte messen einfach, wie "laut" ein Signal ist. Da Meshtastic jedoch die **LoRa Technologie (Long Range)** verwendet, misst die Signalanzeige, wie **klar** das Signal ist – und zwar im Verhältnis zu den spezifischen Einstellungen, die in Ihrem Mesh-Netzwerk genutzt werden.

---

## 1. Die beiden Metriken: "Lautstärke" vs. "Klarheit"

Jedes Mal, wenn der LoRa Chip eine Nachricht empfängt, meldet er zwei Messwerte:

- **RSSI (Received Signal Stength Indicator):** die **Lautstärke** der rohen Energie, die deine Antenne trifft.
- **SNR (Signal-to-Noise Ratio)** Die **Klarheit** des Signals im Vergleich zum Hintergrundrauschen.

> **Tipp:** Hier ist eine Analogie - stell dir vor, du versuchst einen Freund zu hören, der mit dir redet.
>
> - **RSSI** ist wie laut seine Stimme ist.
> - **Der Rauschboden** ist der Hintergrundlärm im Raum (Klimaanlage, andere sprechende Personen, Verkehr).
> - **SNR** ist wie einfach du die Stimme deines Freundes vom Hintergrundlärm unterscheiden kannst.

Wenn dich ein Freund auf einem ohrenbetäubend lauten Rockkonzert anschreit, ist das Signal zwar extrem stark (hoher RSSI), aber du kannst ihn trotzdem nicht verstehen, weil die Hintergrundgeräusche lauter sind (schlechter Signal-Rauschabstand). Umgekehrt, wenn dein Freund dir etwas in einer todstillen Bibliothek zuflüstert, ist das Signal sehr schwach (geringer RSSI), aber du kannst ihn perfekt verstehen (Guter SNR).

---

## 2. Die Magie von LoRa: Empfangen "Unter dem Lärmboden"

Für standard Radios (wie FM oder WLAN) gilt, wenn das Hintergrundrauschen lauter ist, als das Signal (ein negativer SNR), hört der Empfänger nur Rauschen.

LoRa ist speziell. Es nutzt die **„Spread-Spectrum“**-Modulation, die es dem Funkgerät ermöglicht, ein Signal mathematisch aus der Luft zu extrahieren, selbst wenn es tief _unterhalb_ des Hintergrundrauschens liegt. Das ist der Grund weshalb du häufig **negative SNR Werte** (z.B. -10 dB, was bedeutet, dass das Signal 10 Dezibel schwächer ist, als das Hintergrundrauschen) in Meshtastic sehen wirst.

Je nachdem, welche Meshtastic Voreinstellung Sie verwenden (z. B. `LongFast` vs. `ShortFast`), weist das Funkgerät einen spezifischen **Signal-Rauschabstand Grenzwert** auf – das absolute Maximum an Rauschen, das es tolerieren kann, bevor die Nachricht im Rauschen vollständig verloren geht.

---

## 3. Wie das Signal Meter die Qualität berechnet

The app rates your signal quality (None, Bad, Fair, or Good) from **SNR alone, measured relative to the preset's SNR Limit** — the demodulation floor described above. Der RSSI Wert wird bei der Bewertung bewusst **nicht** berücksichtigt: Ohne Kenntnis des lokalen Grundrauschens lässt sich anhand des RSSI nicht feststellen, ob ein Signal tatsächlich dekodierbar ist; daher ist das Verhältnis des Signal-Rauschabstandes zum festgelegten Grenzwert die aussagekräftige Messgröße. (RSSI is still displayed to you elsewhere.)

Da die Bewertung relativ zum voreingestellten Grenzwert erfolgt, kann _derselbe_ Signal-Rauschabstand je nach Voreinstellung unterschiedlich bewertet werden: `-15 dB` gelten bei `LongSlow` als guter Wert, sind jedoch bei `ShortFast` unbrauchbar. Letting `limit` be the active preset's SNR Limit, here is how the app picks the bars (or color):

| Niveau                   | Balken | Kriterium                              | Bedeutung                                                                                |
| ------------------------ | ------ | -------------------------------------- | ---------------------------------------------------------------------------------------- |
| Gut                      | 3      | SNR **above** the preset's `limit`     | Signal is comfortably above the demodulation floor — healthy connection. |
| Ordentliche Signalstärke | 2      | less than `5.5 dB` below the `limit`   | Decodable, but getting close to the floor.                               |
| Schlecht                 | 1      | `5.5 dB` to `7.5 dB` below the `limit` | At the very edge of what the preset can recover.                         |
| Keins                    | 0      | more than `7.5 dB` below the `limit`   | Below the floor — transmission lost to noise.                            |

> **Hinweis:** Die festen Schwellenwerte des Signal-Rauschabstandes, die Sie möglicherweise an anderer Stelle gesehen haben (`-7 dB` / `-15 dB`), werden mittlerweile nur noch zur farblichen Kennzeichnung einzelner Sprünge in Traceroute Ergebnissen verwendet – nicht für die hier beschriebene Signalstärkeanzeige pro Knoten.

---

## 4. Was das für dich bedeutet

Because Meshtastic's meter acts as a **"Clarity Meter"**, it behaves differently than what most people expect:

> 💡 **Tip:** Don't panic over low RSSI. You might see a seemingly terrible RSSI value like `-118 dBm`. Mit einem Handy hättest du Null Balken. But if you have an SNR of `+2 dB`, Meshtastic will still show a strong signal! _The library is quiet, so the whisper is heard perfectly._

> ⚠️ **Warning:** Watch out for local noise. If you hook up a massive antenna and see a great RSSI (e.g., `-90 dBm`) but your signal meter is only showing **1 Bar (Bad)**, you have a problem. Das bedeutet, dass lokale Störquellen vorliegen – etwa ein billiges Netzteil, ein störender Computer oder ein nahegelegener Funkturm –, die so starkes Rauschen erzeugen, dass sie Ihr Netzwerk überlagern.

## Where Signal Information Appears

In der App werden Signaldaten an mehreren Orten angezeigt:

- **Node list** — signal bars icon next to each node
- **Node detail** — SNR, RSSI, and signal quality in the device metrics section
- **Traceroute** — per-hop signal quality for each relay node
- **Signal metrics** — historical SNR and RSSI data in the metrics charts

![Node entry showing SNR, RSSI values and colored signal bars](../../assets/screenshots/nodes_signal_info.png)

## Related Topics

- [Nodes](nodes) — where signal bars appear in the node list
- [Node Metrics](node-metrics) — SNR/RSSI history and the per-node signal quality reference
- [Settings — Radio & User](settings-radio-user) — modem presets and their SNR limits

---

