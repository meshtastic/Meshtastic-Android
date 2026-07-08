---
title: Kuidas Meshtastic signaalimõõtur töötab
parent: User Guide
nav_order: 15
last_updated: 2026-07-08
description: How the signal meter rates quality from SNR relative to the LoRa modem preset — spread spectrum, presets, and what the bars really mean.
aliases:
  - signal
  - signal-meter
  - snr
  - rssi
---

# Kuidas Meshtastic signaalimõõtur töötab

The Meshtastic signal meter — the familiar bars or status color in the app — is calculated very differently than the "bars" on a traditional cell phone or WiFi router.

Most consumer devices simply measure how "loud" a signal is. Kuna Meshtastic kasutab **LoRa (Long Range)** tehnoloogiat, mõõdab selle signaalimõõtja signaali **selgust** võrreldes sinu võrgu konkreetsete sätetega.

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

Kui su sõber karjub sulle kõrvulukustava rokkkontserdi ajal, on signaal uskumatult vali (kõrge RSSI), aga sa ei saa temast ikkagi aru, sest taustamüra on valjem (halb signaali-müra suhe). Conversely, if your friend whispers to you in a dead-silent library, the signal is very weak (Low RSSI), but you can understand them perfectly (Great SNR).

---

## 2. The Magic of LoRa: Hearing "Below the Noise Floor"

For standard radios (like FM or WiFi), if the background noise is louder than the signal (a negative SNR), the receiver just hears static.

LoRa is special. It uses **"Spread Spectrum"** modulation, which allows the radio to mathematically pull a signal out of the air even when it is buried deep _underneath_ the background noise. Seepärast näed Meshtasticus sageli **negatiivseid SNR numbreid** (nt -10 dB, mis tähendab, et signaal on 10 detsibelli nõrgem kui taustamüra).

Sõltuvalt sellest, millist Meshtastic eelhäälestust kasutad (nt `PikkKauge` vs. `LühikeKiire`), on raadiol kindel **SNR-i piirang** – absoluutne maksimaalne müra hulk, mida see talub enne, kui sõnum staatilise müra tõttu täielikult kaob.

---

## 3. How the Signal Meter Calculates Quality

The app rates your signal quality (None, Bad, Fair, or Good) from **SNR alone, measured relative to the preset's SNR Limit** — the demodulation floor described above. It deliberately does **not** factor RSSI into the rating: without the local noise floor, RSSI cannot tell you whether a signal is actually decodable, so SNR-versus-the-preset-limit is the meaningful measure. (RSSI is still displayed to you elsewhere.)

Because the rating is relative to the preset limit, the _same_ SNR can rate differently on different presets — `-15 dB` is healthy on `LongSlow` but unusable on `ShortFast`. Letting `limit` be the active preset's SNR Limit, here is how the app picks the bars (or color):

| Level    | Bars | Criteria                               | Meaning                                                                                  |
| -------- | ---- | -------------------------------------- | ---------------------------------------------------------------------------------------- |
| Hea      | 3    | SNR **above** the preset's `limit`     | Signal is comfortably above the demodulation floor — healthy connection. |
| Rahuldav | 2    | less than `5.5 dB` below the `limit`   | Decodable, but getting close to the floor.                               |
| Halb     | 1    | `5.5 dB` to `7.5 dB` below the `limit` | At the very edge of what the preset can recover.                         |
| Puudub   | 0    | more than `7.5 dB` below the `limit`   | Below the floor — transmission lost to noise.                            |

> **Märkus:** Fikseeritud SNR lävesid, mida võisid mujal näha (`-7 dB` / `-15 dB`), kasutatakse nüüd ainult üksikute hüpete rõhutamiseks traceroute'i tulemustes – mitte siin kirjeldatud sõlmepõhise signaalimõõtja jaoks.

---

## 4. What This Means for You

Kuna Meshtasticu mõõdik toimib **"selguse mõõturina"**, käitub see erinevalt sellest, mida enamik inimesi ootab:

> 💡 **Tip:** Don't panic over low RSSI. You might see a seemingly terrible RSSI value like `-118 dBm`. On a cell phone, you would have zero bars. Aga kui sinu seadme signaali-müra suhe on `+2 dB`, näitab Meshtastic ikkagi tugevat signaali! _The library is quiet, so the whisper is heard perfectly._

> ⚠️ **Warning:** Watch out for local noise. If you hook up a massive antenna and see a great RSSI (e.g., `-90 dBm`) but your signal meter is only showing **1 Bar (Bad)**, you have a problem. It means you have local interference — perhaps a cheap power supply, a noisy computer, or a nearby radio tower — creating so much static that it is drowning out your mesh.

## Where Signal Information Appears

In the app, signal data is shown in several places:

- **Node list** — signal bars icon next to each node
- **Node detail** — SNR, RSSI, and signal quality in the device metrics section
- **Traceroute** — iga vahendussõlme signaali kvaliteet hüppe kohta
- **Signal metrics** — historical SNR and RSSI data in the metrics charts

![Node entry showing SNR, RSSI values and colored signal bars](../../assets/screenshots/nodes_signal_info.png)

## Related Topics

- [Nodes](nodes) — where signal bars appear in the node list
- [Node Metrics](node-metrics) — SNR/RSSI history and the per-node signal quality reference
- [Settings — Radio & User](settings-radio-user) — modem presets and their SNR limits

---

