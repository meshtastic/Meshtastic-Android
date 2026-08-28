---
title: Aloitusnäytön widget
parent: Käyttöopas
nav_order: 20
last_updated: 2026-08-27
description: Lisää Meshtasticin aloitusnäytön widget, jotta näet yhdellä silmäyksellä yhdistetyn radiosi paikalliset tilastot ilman, että avaat sovelluksen.
aliases:
  - widget
  - aloitusnäytön widget
  - paikalliset tilastot widget
---

# Aloitusnäytön widget

Androidissa Meshtastic tarjoaa aloitusnäytölle **widgetin**, joka näyttää yhdistetyn radiosi paikalliset tilastot yhdellä silmäyksellä — sovellusta ei tarvitse avata.

## Mitä se näyttää

Widget näyttää **yhdistetyn radion** tämänhetkiset paikalliset tilastot:

- **Akun varaustaso** — radion akun varaustaso tai **Verkkovirta**, jos radio käyttää ulkoista virtalähdettä
- **ChUtil** — kanavan käyttöaste (kuinka kuormitettu LoRa-kanava on prosentteina)
- **AirUtil** — lähetysajan käyttöaste (kuinka suuren osan lähetysajasta radiosi käyttää)
- **Liikenne** — lähetetyt ja vastaanotetut paketit sekä havaitut kaksoiskappaleet
- **Välitykset** — välitetyt paketit ja välityksen peruutukset (näytetään, kun radio toimii välittäjänä)
- **Noise floor** — the measured background noise level
- **Dropped** — packets the radio discarded
- **Heap** — free versus total memory on the radio, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known

Avaa sovellus napauttamalla widgetiä tai pyydä uudet tilastot sen päivityspainikkeella.

> 💡 **Vinkki:** Arvot vastaavat sitä radiota, johon olet parhaillaan yhdistetty. If the radio disconnects, the widget replaces the stats with a status line — **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Widgetin lisääminen

1. Paina pitkään tyhjää kohtaa Androidin aloitusnäytössä.
2. Napauta **Widgetit**.
3. Find **Meshtastic** in the list and drag its widget to your home screen. The app ships one widget, so the picker entry is just the app name.
4. Muuta widgetin kokoa tarvittaessa — asettelu mukautuu käytettävissä olevaan tilaan.

> ℹ️ **Note:** The widget is Android-only. Se ei ole käytettävissä Desktop- tai iOS-versioissa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — täydellinen signaalin laatu- ja paikalliset tilastot -historia sovelluksessa
- [Yhteydet](connections) — yhdistä radioon, jotta widgetillä on näytettäviä tilastoja
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh

---
