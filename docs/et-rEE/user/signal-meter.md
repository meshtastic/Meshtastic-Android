---
title: Kuidas Meshtastic signaalimõõtur töötab
parent: User Guide
nav_order: 15
last_updated: 2026-05-13
description: How the signal meter calculates quality from RSSI and SNR — LoRa spread spectrum, presets, and what the bars really mean.
aliases:
  - signal
  - signal-meter
  - snr
  - rssi
---

# Kuidas Meshtastic signaalimõõtur töötab

Meshtastic signaalimõõtur – rakenduses tuttavad tulbad või olekuvärv – arvutatakse väga erinevalt traditsioonilise mobiiltelefoni või WiFi-ruuteri „tulpadest”.

Most consumer devices simply measure how "loud" a signal is. Kuna Meshtastic kasutab **LoRa (Long Range)** tehnoloogiat, mõõdab selle signaalimõõtja signaali **selgust** võrreldes sinu võrgu konkreetsete sätetega.

---

## 1. The Two Metrics: "Loudness" vs. "Clarity"

Every time the LoRa radio chip receives a message, it reports two measurements:

- **RSSI (Received Signal Strength Indicator):** The **loudness** of the raw power hitting your antenna.
- **SNR (Signal-to-Noise Ratio):** The **clarity** of the signal compared to the background static.

> **Tip — The Analogy:** Imagine you are trying to hear a friend talking to you.
>
> - **RSSI** is how loud their voice is.
> - **The Noise Floor** is the background noise in the room (air conditioning, other people talking, traffic).
> - **SNR** is how easily you can distinguish your friend's voice from the background noise.

If your friend shouts at you at a deafening rock concert, the signal is incredibly loud (High RSSI), but you still can't understand them because the background noise is louder (Bad SNR). Conversely, if your friend whispers to you in a dead-silent library, the signal is very weak (Low RSSI), but you can understand them perfectly (Great SNR).

---

## 2. The Magic of LoRa: Hearing "Below the Noise Floor"

For standard radios (like FM or Wi-Fi), if the background noise is louder than the signal (a negative SNR), the receiver just hears static.

LoRa is special. It uses **"Spread Spectrum"** modulation, which allows the radio to mathematically pull a signal out of the air even when it is buried deep _underneath_ the background noise. Seepärast näed Meshtasticus sageli **negatiivseid SNR numbreid** (nt -10 dB, mis tähendab, et signaal on 10 detsibelli nõrgem kui taustamüra).

Sõltuvalt sellest, millist Meshtastic eelhäälestust kasutad (nt `PikkKauge` vs. `LühikeKiire`), on raadiol kindel **SNR-i piirang** – absoluutne maksimaalne müra hulk, mida see talub enne, kui sõnum staatilise müra tõttu täielikult kaob.

---

## 3. How the Signal Meter Calculates Quality

Meshtastici rakendused võtavad nii RSSI kui ka SNR-i ning käivitavad need kindla valemi abil, et määrata sinu signaalile kvaliteedihinnang (puudub, halb, rahuldav või hea). It specifically scales these values based on the physical limits of the radio preset you are using.

Here is exactly how the app decides how many bars (or what color) to show you:

| Level    | Bars | Criteria                                                                             | Meaning                                                                 |
| -------- | ---- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| Hea      | 3    | RSSI parem kui `-115 dBm` **JA** SNR parem kui `-7 dB`                               | Signal is both loud and clear — healthy connection.     |
| Rahuldav | 2    | RSSI parem kui `-126 dBm` koos hea SNR, **VÕI** SNR parem kui `-15 dB` koos hea RSSI | Signal getting quieter or noisier, but still decodable. |
| Halb     | 1    | Falls between Fair and None thresholds                                               | At the edge of range or experiencing interference.      |
| Puudub   | 0    | RSSI worse than `-126 dBm` **AND** SNR worse than `-15 dB`                           | Transmission completely buried in noise.                |

---

## 4. What This Means for You

Kuna Meshtasticu mõõdik toimib **"selguse mõõturina"**, käitub see erinevalt sellest, mida enamik inimesi ootab:

> **Vihje – ära paanitse madala RSSI pärast:** Võid näha pealtnäha kohutavat RSSI väärtust, näiteks `-118 dBm`. On a cell phone, you would have zero bars. Aga kui sinu seadme signaali-müra suhe on `+2 dB`, näitab Meshtastic ikkagi tugevat signaali! _The library is quiet, so the whisper is heard perfectly._

> **Hoiatus – pöörake tähelepanu kohalikule mürale:** Kui ühendad võimsa antenni ja näete head RSSI-d (nt „-90 dBm”), aga signaalimõõtur näitab ainult **1 tulp (halb)**, on sul probleem. It means you have local interference — perhaps a cheap power supply, a noisy computer, or a nearby radio tower — creating so much static that it is drowning out your mesh.

## Where Signal Information Appears

In the app, signal data is shown in several places:

- **Node list** — signal bars icon next to each node
- **Node detail** — SNR, RSSI, and signal quality in the device metrics section
- **Traceroute** — per-hop signal quality for each relay node
- **Signal metrics** — historical SNR and RSSI data in the metrics charts

![Node entry showing SNR, RSSI values and colored signal bars](../../assets/screenshots/nodes_signal_info.png)

