---
title: How the Meshtastic Signal Meter Works
parent: User Guide
nav_order: 15
last_updated: 2026-06-25
description: How the signal meter rates quality from SNR relative to the LoRa modem preset — spread spectrum, presets, and what the bars really mean.
aliases:
  - signal
  - signal-meter
  - snr
  - rssi
---

# How the Meshtastic Signal Meter Works

Die Meshtastic-Signalanzeige – die vertrauten Balken oder die Statusfarbe in der App – wird ganz anders berechnet als die „Balken“ bei einem herkömmlichen Mobiltelefon oder WLAN-Router.

Most consumer devices simply measure how "loud" a signal is. Da Meshtastic jedoch die **LoRa Technologie (Long Range)** verwendet, misst die Signalanzeige, wie **klar** das Signal ist – und zwar im Verhältnis zu den spezifischen Einstellungen, die in Ihrem Mesh-Netzwerk genutzt werden.

---

## 1. The Two Metrics: "Loudness" vs. "Clarity"

Every time the LoRa radio chip receives a message, it reports two measurements:

- **RSSI (Received Signal Strength Indicator):** The **loudness** of the raw power hitting your antenna.
- **SNR (Signal-to-Noise Ratio):** The **clarity** of the signal compared to the background static.

> 💡 **Tip:** Here's an analogy — imagine you are trying to hear a friend talking to you.
>
> - **RSSI** is how loud their voice is.
> - **The Noise Floor** is the background noise in the room (air conditioning, other people talking, traffic).
> - **SNR** is how easily you can distinguish your friend's voice from the background noise.

Wenn dich ein Freund auf einem ohrenbetäubend lauten Rockkonzert anschreit, ist das Signal zwar extrem stark (hoher RSSI), aber du kannst ihn trotzdem nicht verstehen, weil die Hintergrundgeräusche lauter sind (schlechter Signal-Rauschabstand). Conversely, if your friend whispers to you in a dead-silent library, the signal is very weak (Low RSSI), but you can understand them perfectly (Great SNR).

---

## 2. The Magic of LoRa: Hearing "Below the Noise Floor"

For standard radios (like FM or WiFi), if the background noise is louder than the signal (a negative SNR), the receiver just hears static.

LoRa is special. Es nutzt die **„Spread-Spectrum“**-Modulation, die es dem Funkgerät ermöglicht, ein Signal mathematisch aus der Luft zu extrahieren, selbst wenn es tief _unterhalb_ des Hintergrundrauschens liegt. This is why you will frequently see **negative SNR numbers** in Meshtastic (e.g., -10 dB, which means the signal is 10 decibels weaker than the background static).

Je nachdem, welche Meshtastic Voreinstellung Sie verwenden (z. B. `LongFast` vs. `ShortFast`), weist das Funkgerät einen spezifischen **Signal-Rauschabstand Grenzwert** auf – das absolute Maximum an Rauschen, das es tolerieren kann, bevor die Nachricht im Rauschen vollständig verloren geht.

---

## 3. How the Signal Meter Calculates Quality

The app rates your signal quality (None, Bad, Fair, or Good) from **SNR alone, measured relative to the preset's SNR Limit** — the demodulation floor described above. Der RSSI Wert wird bei der Bewertung bewusst **nicht** berücksichtigt: Ohne Kenntnis des lokalen Grundrauschens lässt sich anhand des RSSI nicht feststellen, ob ein Signal tatsächlich dekodierbar ist; daher ist das Verhältnis des Signal-Rauschabstandes zum festgelegten Grenzwert die aussagekräftige Messgröße. (RSSI is still displayed to you elsewhere.)

Da die Bewertung relativ zum voreingestellten Grenzwert erfolgt, kann _derselbe_ Signal-Rauschabstand je nach Voreinstellung unterschiedlich bewertet werden: `-15 dB` gelten bei `LongSlow` als guter Wert, sind jedoch bei `ShortFast` unbrauchbar. Letting `limit` be the active preset's SNR Limit, here is how the app picks the bars (or color):

| Niveau                   | Balken | Criteria                                        | Meaning                                                                                  |
| ------------------------ | ------ | ----------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Gut                      | 3      | SNR **above** the preset's `limit`              | Signal is comfortably above the demodulation floor — healthy connection. |
| Ordentliche Signalstärke | 2      | up to `5.5 dB` below the `limit`                | Decodable, but getting close to the floor.                               |
| Schlecht                 | 1      | between `5.5 dB` and `7.5 dB` below the `limit` | At the very edge of what the preset can recover.                         |
| Keins                    | 0      | more than `7.5 dB` below the `limit`            | Below the floor — transmission lost to noise.                            |

> **Hinweis:** Die festen Schwellenwerte des Signal-Rauschabstandes, die Sie möglicherweise an anderer Stelle gesehen haben (`-7 dB` / `-15 dB`), werden mittlerweile nur noch zur farblichen Kennzeichnung einzelner Sprünge in Traceroute Ergebnissen verwendet – nicht für die hier beschriebene Signalstärkeanzeige pro Knoten.

---

## 4. What This Means for You

Because Meshtastic's meter acts as a **"Clarity Meter"**, it behaves differently than what most people expect:

> 💡 **Tip:** Don't panic over low RSSI. You might see a seemingly terrible RSSI value like `-118 dBm`. On a cell phone, you would have zero bars. But if you have an SNR of `+2 dB`, Meshtastic will still show a strong signal! _The library is quiet, so the whisper is heard perfectly._

> ⚠️ **Warning:** Watch out for local noise. If you hook up a massive antenna and see a great RSSI (e.g., `-90 dBm`) but your signal meter is only showing **1 Bar (Bad)**, you have a problem. Das bedeutet, dass lokale Störquellen vorliegen – etwa ein billiges Netzteil, ein störender Computer oder ein nahegelegener Funkturm –, die so starkes Rauschen erzeugen, dass sie Ihr Netzwerk überlagern.

## Where Signal Information Appears

In the app, signal data is shown in several places:

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

