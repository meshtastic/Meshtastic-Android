---
title: Kuidas Meshtastic signaalimõõtur töötab
parent: Kasutusjuhend
nav_order: 15
last_updated: 2026-07-08
description: How the signal meter rates quality from SNR relative to the LoRa modem preset — spread spectrum, presets, and what the bars really mean.
aliases:
  - signaal
  - signaali-mõõtur
  - snr
  - rssi
---

# Kuidas Meshtastic signaalimõõtur töötab

Meshtastici signaalimõõtur – rakenduses tuttavad tulbad või olekuvärv – arvutatakse väga erinevalt traditsioonilise mobiiltelefoni või WiFi-ruuteri „tulpadest”.

Most consumer devices simply measure how "loud" a signal is. Kuna Meshtastic kasutab **LoRa (Long Range)** tehnoloogiat, mõõdab selle signaalimõõtja signaali **selgust** võrreldes sinu võrgu konkreetsete sätetega.

---

## 1. The Two Metrics: "Loudness" vs. "Clarity"

Every time the LoRa radio chip receives a message, it reports two measurements:

- **RSSI (vastuvõetud signaali tugevuse indikaator):** Antennile langeva toore võimsuse **valjus**.
- **SNR (Signal-to-Noise Ratio):** The **clarity** of the signal compared to the background static.

> 💡 **Vihje:** Siin on analoogia – kujuta ette, et sa üritad kuulda sõpra sinuga rääkimas.
>
> - **RSSI** is how loud their voice is.
> - **The Noise Floor** is the background noise in the room (air conditioning, other people talking, traffic).
> - **SNR** is how easily you can distinguish your friend's voice from the background noise.

Kui su sõber karjub sulle kõrvulukustava rokkkontserdi ajal, on signaal uskumatult vali (kõrge RSSI), aga sa ei saa temast ikkagi aru, sest taustamüra on valjem (halb signaali-müra suhe). Conversely, if your friend whispers to you in a dead-silent library, the signal is very weak (Low RSSI), but you can understand them perfectly (Great SNR).

---

## 2. The Magic of LoRa: Hearing "Below the Noise Floor"

Standardraadiote (nt FM või WiFi) puhul, kui taustamüra on signaalist valjem (negatiivne signaali-müra suhe), kuuleb vastuvõtja ainult staatilist müra.

LoRa on eriline. It uses **"Spread Spectrum"** modulation, which allows the radio to mathematically pull a signal out of the air even when it is buried deep _underneath_ the background noise. Seepärast näed Meshtasticus sageli **negatiivseid SNR numbreid** (nt -10 dB, mis tähendab, et signaal on 10 detsibelli nõrgem kui taustamüra).

Sõltuvalt sellest, millist Meshtastic eelhäälestust kasutad (nt `PikkKauge` vs. `LühikeKiire`), on raadiol kindel **SNR-i piirang** – absoluutne maksimaalne müra hulk, mida see talub enne, kui sõnum staatilise müra tõttu täielikult kaob.

---

## 3. How the Signal Meter Calculates Quality

Rakendus hindab signaali kvaliteeti (puudub, halb, rahuldav või hea) ainult **signaali-müra suhte (SNR) põhjal, mõõdetuna eelseadistatud SNR-i piirväärtuse** suhtes – see on eespool kirjeldatud demodulatsiooni alumine piirväärtus. See **ei** arvesta RSSI-d hinnangus teadlikult: ilma kohaliku müratasemeta ei saa RSSI öelda, kas signaal on tegelikult dekodeeritav, seega on sisukas mõõdupuu SNR versus eelseadistatud piirväärtus. (RSSI is still displayed to you elsewhere.)

Kuna hinnang on eelseadistatud piirangu suhtes suhteline, võib _sama_ signaali-müra suhe erinevatel eelseadistustel olla erinev – „-15 dB” on „LongSlow” puhul kasutatav, kuid „ShortFast” puhul mittekasutatav. Kui aktiivse eelseadistuse signaali ja müra piiranguks on `limit`, valib rakendus ribad (või värvi) järgmiselt:

| Level    | Bars | Criteria                                        | Tähendus                                                                                 |
| -------- | ---- | ----------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Hea      | 3    | SNR **above** the preset's `limit`              | Signal is comfortably above the demodulation floor — healthy connection. |
| Rahuldav | 2    | vähem kui `5,5` dB `piirväärtusest` allpool     | Decodable, but getting close to the floor.                               |
| Halb     | 1    | `5,5 dB` kuni `7,5 dB` `piirväärtusest` allpool | At the very edge of what the preset can recover.                         |
| Puudub   | 0    | üle `7,5` dB `piirväärtusest` allpool           | Below the floor — transmission lost to noise.                            |

> **Märkus:** Fikseeritud SNR lävesid, mida võisid mujal näha (`-7 dB` / `-15 dB`), kasutatakse nüüd ainult üksikute hüpete rõhutamiseks traceroute'i tulemustes – mitte siin kirjeldatud sõlmepõhise signaalimõõtja jaoks.

---

## 4. What This Means for You

Kuna Meshtasticu mõõdik toimib **"selguse mõõturina"**, käitub see erinevalt sellest, mida enamik inimesi ootab:

> 💡 **Vihje:** Ära paanitse madala RSSI pärast. You might see a seemingly terrible RSSI value like `-118 dBm`. On a cell phone, you would have zero bars. Aga kui sinu seadme signaali-müra suhe on `+2 dB`, näitab Meshtastic ikkagi tugevat signaali! _The library is quiet, so the whisper is heard perfectly._

> ⚠️ **Warning:** Watch out for local noise. Kui ühendad tohutu antenni ja näed suurepärast RSSI-d (nt „-90 dBm”), aga signaalimõõtur näitab ainult **1 tulpa (halb)**, on sul probleem. See tähendab, et on lokaalsed häired – näiteks odav toiteallikas, lärmakas arvuti või lähedal asuv raadiomast –, mis tekitavad nii palju staatilist müra, et see uputab sinu võrgu heli.

## Where Signal Information Appears

Rakenduses kuvatakse signaaliandmeid mitmes kohas:

- **Sõlmede loend** — signaaliribade ikoon iga sõlme kõrval
- **Node detail** — SNR, RSSI, and signal quality in the device metrics section
- **Traceroute** — iga vahendussõlme signaali kvaliteet hüppe kohta
- **Signal metrics** — historical SNR and RSSI data in the metrics charts

![Sõlme kirje, mis näitab signaali ja signaali suhet SNR, RSSI väärtusi ja värvilisi signaaliribasid](../../assets/screenshots/nodes_signal_info.png)

## Seotud teemad

- [Nodes](nodes) — where signal bars appear in the node list
- [Node Metrics](node-metrics) — SNR/RSSI history and the per-node signal quality reference
- [Seaded — Raadio ja kasutaja](settings-radio-user) — modemi eelseadistused ja nende signaali-müra piirangud

---

