---
title: Avastamine
parent: User Guide
nav_order: 12
last_updated: 2026-07-08
description: Avasta oma kärgvõrku – kohalik kärgvõrgu avastaja skanner, traceroute'i teed, naabri-kaardid ja sõlmede avastamise tööriistad.
aliases:
  - kärgvõrgu-avastamine
  - kohalik-avastamine
  - network-scan
  - traceroute
  - neighbor-info
---

# Avastamine

Avastamistööriistad aitavad mõista, **kuidas** kärgvõrk on ühendatud – millised sõlmed üksteist kuulevad, milliseid teid sõnumid läbivad ja kus esinevad kitsaskohad või nõrgad lülid.

The app offers two complementary approaches:

- **Kohalik kärgvõrgu avastaja (skanner)** – automaatne režiim, mis perioodiliselt skaneerib ühendatud raadiol läbi erinevate LoRa eelhäälestuste, kuulab igaüht neist ja järjestab, milline eelhäälestus sinu asukohas kõige paremini toimib.
- **Manual exploration** — traceroute, Neighbor Info, and the node list, which you can use at any time to investigate specific paths and topology.

---

## Kohalik kärgvõrgu avastaja (skanner)

Kohalik kärgvõrdu avastaja on spetsiaalne skaneerimisrežiim, mis aitab leida oma asukoha jaoks parima LoRa modemi eelseadistuse ja näha, millised sõlmed on igal eelseadistusel aktiivsed. See kerib ühendatud raadio läbi ühe või mitu valitud eelseadet, kuulab (või "ootab") igaüht neist määratud aja jooksul pakettide kogumiseks ning seejärel analüüsib ja järjestab tulemused.

Ava **Sätted → Kohalik kärgvõrgu avastaja**.

> ⚠️ **Märkus:** Discovery muudab skannimise ajal ajutiselt raadio LoRa seadeid ja taastab pärast skannimise lõppu algse konfiguratsiooni. Your device must be connected to run a scan.

### Setting Up a Scan

Before starting, configure these controls:

| Control                | Kirjeldus                                                                                                                                                                                                                                |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LoRa preset picker** | Select one or more presets to scan. Otsing peatub kordamööda iga valitud eelseadistuse juures, et kuulata liiklust.                                                                                      |
| **Kuulamisaeg**        | Time to listen on each preset. Choose from 1, 5, 15, 30, 45, 60, 90, 120, or 180 minutes. Pikemad kuulamisajad koguvad rohkem pakette ja annavad selgema pildi, kuid võtavad kauem aega. |
| **Keep screen awake**  | Valikuline lüliti, mis takistab ekraani pika skannimise ajal magamaminekut.                                                                                                                                              |

The **Start** button stays disabled — with an explanation of why — until the scan can run. Common reasons it's disabled:

- The device is **not connected**.
- **No presets** have been selected to scan.
- The selected preset uses **2.4 GHz**, which your hardware doesn't support.

### Live Progress

Skanni ajal näitab Discovery selle praegust etappi:

| Stage                                                 | What's happening                                                                                       |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Preparing**                                         | Praeguste sätete salvestamine ja skannimiseks valmistumine.                            |
| **Shifting to \<preset\>** | Switching the radio to the next preset to test.                                        |
| **Reconnecting**                                      | Re-establishing the connection after the preset change.                                |
| **Kuulamine**                                         | Listening on the current preset to collect packets, with a countdown to the next step. |
| **Analysis**                                          | Processing the collected packets and ranking the presets.                              |
| **Restoring**                                         | Algsete LoRa seadete taastamine.                                                       |

![Kuulamis loendur näitab praeguse eelseadistuse järelejäänud aega](../../assets/screenshots/discovery_dwell_progress.png)

### Reading the Results

Kui skann on lõppenud, kuvab Discovery iga testitud eelseadistuse kohta tulemuste kaardi ja üldise kokkuvõtte.

![Eelmääratud tulemuste kaart koos edetabeli ja kogutud näitajatega](../../assets/screenshots/discovery_preset_result.png)

Metrics include:

| Meetriline                               | What it tells you                                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------- |
| RF health                                | Overall quality of the radio environment on that preset.                       |
| Kanali kasutus                           | Kui hõivatud olid eetrisagedused kuulamise ajal.                               |
| Airtime                                  | Transmission time observed.                                                    |
| Direct vs. relayed nodes | How many mesh nodes were heard directly versus via a relay.                    |
| Bad / duplicate packets                  | Counts of corrupt and repeated packets, indicating congestion or interference. |

Additional features available from the results:

- **Scan History** — saved sessions you can revisit; view or delete past scans.
- **Avastuskaart** – skanni käigus leitud sõlmede kaart.
- **Aruande eksport** – ekspordi aruanne PDF-failina Androidis või tekstina muudel platvormidel.

> 💡 **Vihje:** Androidis saab Discovery genereerida tulemustest seadmesisese tehisintellekti kokkuvõtte (Gemini Nano). If the on-device model isn't available, an algorithmic summary is used instead — so you always get a readable interpretation of the scan.

---

## Manual Exploration

The tools below are available at any time from the node list and node detail screens. Use them to investigate specific paths and build a topology picture, alongside or instead of a full scan.

## Marsruudi

Traceroute reveals the exact path a message takes from your node to any other node on the mesh. See on kõige kasulikum tööriist ühenduvusprobleemide tõrkeotsinguks.

### Running a Traceroute

1. Mine valikuni **Sõlmed** ja puuduta sõlme, mida soovid jälgida.
2. Sõlme üksikasjade ekraanil puuduta **Traceroute**.
3. The app sends a traceroute request and waits for the response.
4. Tulemused kuvatakse iga hüppe kohta, koos signaali kvaliteediga igal sammul.

### Reading the Results

A traceroute result looks like this:

```
You → Node A (SNR: 8.5, RSSI: -95) → Node B (SNR: 5.2, RSSI: -108) → Target
```

Iga hüpe näitab vahendussõlme, mis sõnumi edastas. The SNR and RSSI values at each hop tell you about the link quality on that specific segment.

| What to look for                                                                  | What it means                                                               |
| --------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| All hops show Good SNR (≥ −7 dB, green)                        | Healthy path — messages flow reliably                                       |
| One hop shows Bad SNR (< −15 dB, red) | Weak link — this relay segment is fragile                                   |
| Mitu hüppet (4+)                                               | Long path — consider repositioning a node to shorten it                     |
| Different path on retry                                                           | Mesh is adapting — multiple routes exist (this is good!) |

> 💡 **Vihje:** Käivita traceroute'i mitu korda mõne minuti tagant. If the path changes, your mesh has redundant routes — a sign of a well-connected network.

### Troubleshooting with Traceroute

- **"Marsruuti ei leitud"** — Sihtsõlm võib olla võrguühenduseta, leviulatusest väljas või teisel kanalil. Kontrolli, et mõlemad sõlmed jagaksid vähemalt ühte kanalit sama krüpteerimisvõtmega.
- **Traceroute aegus** — Tee võib olla liiga pikk (ületab hüppete limiidi) või on vahendussõlm ülekoormatud. Proovi hüppe limiiti suurendada menüüs **Seaded → LoRa konfiguratsioon**.
- **Asymmetric paths** — A traceroute from A→B may take a different path than B→A. This is normal — radio propagation is not always symmetric.

---

## Naabruse teave

Naabriinfo moodul võimaldab igal sõlmel levitada nimekirja sõlmedest, mida see **otse kuuleb** (üksik-hüpe). When multiple nodes share their neighbor lists, you can piece together a topology map of the entire mesh.

### Enabling Neighbor Info

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → Naabriinfo**.
2. Luba moodul.
3. Määra levintervall (vaikimisi: 900 sekundit / 15 minutit).

Kui see on lubatud, levitab sõlm perioodiliselt oma naabri-tabelit. Teised sõlmed, millel on naabriinfo lubatud, teevad sama.

### Viewing Neighbor Data

- Open any node's detail screen and look for the **Neighbors** section.
- Each neighbor entry shows the node that was directly heard and its signal quality.
- Combine neighbor data from multiple nodes to understand the full mesh topology.

> ⚠️ **Märkus:** Naabriinfo suurendab eetriaega, kuna iga lubatud sõlm levitab perioodiliselt oma naabrite nimekirja. Paljude sõlmedega tiheda liiklusega kärgvõrgu puhul kaaluge ummikute vältimiseks pikemaid levitamise intervalle (3600 sekundit või rohkem).

---

## Sõlmede loend avastusvahendina

Sõlmede loend ise on võimas avastusvahend, kui kasutada selle filtreerimis- ja sortimisfunktsioone tõhusalt.

### Finding New Nodes

- Sort by **Last heard** to see the most recently active nodes at the top.
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on devices.

### Assessing Connectivity

- Sorteeri **Hüpete arvu järgi**, et näha, millised sõlmed on otse kättesaadavad (0 hüpet) ja millised vahendatavate sõlmedega.
- Sort by **Distance** to find nearby nodes and verify they're reachable.
- Kasuta **Välista MQTT** raadio teel (mitte internetisilla kaudu) ligipääsetavatele sõlmedele keskendumiseks.

### Infrastructure Audit

- Disable **Exclude infrastructure** to see Router, Repeater, Router Late, and Client Base nodes.
- Check their signal quality and last-heard times to verify your infrastructure nodes are healthy.

See [Nodes](nodes) for full details on filtering and sorting options.

---

## Tips for Mesh Exploration

- **Start with traceroute** — it gives you immediate, actionable information about a specific path.
- **Enable Neighbor Info on key nodes** — especially routers and repeaters, to build a picture of the backbone.
- **Check the map** — node positions on the [Map](map-and-waypoints) combined with signal data help you understand why some links are strong and others are weak.
- **Compare signal over time** — use the [Signal Meter](signal-meter) guide to interpret SNR and RSSI values correctly.

---

