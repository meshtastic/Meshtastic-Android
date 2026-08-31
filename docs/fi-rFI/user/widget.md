---
title: Aloitusnäytön widget
parent: Käyttöopas
nav_order: 20
last_updated: 2026-08-30
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

- A **node chip** across the top, carrying the radio's short name in its own colors
- **Akun varaustaso** — radion akun varaustaso tai **Verkkovirta**, jos radio käyttää ulkoista virtalähdettä
- **ChUtil** — kanavan käyttöaste (kuinka kuormitettu LoRa-kanava on prosentteina)
- **AirUtil** — lähetysajan käyttöaste (kuinka suuren osan lähetysajasta radiosi käyttää)
- **Liikenne** — lähetetyt ja vastaanotetut paketit sekä havaitut kaksoiskappaleet
- **Välitykset** — välitetyt paketit ja välityksen peruutukset (näytetään, kun radio toimii välittäjänä)
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the radio discarded). Bad and Dropped appear only once they are above zero, so a quiet radio may show the noise reading alone
- **Muistin käyttö** — radion vapaa ja kokonaismuisti palkkina esitettynä
- **Radiot** — kuinka monta radiota on verkossa tunnetusta kokonaismäärästä
- **Uptime** — how long the radio has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

Avaa sovellus napauttamalla widgetiä tai pyydä uudet tilastot sen päivityspainikkeella.

> ℹ️ **Huomautus:** Arvot vastaavat yhdistettyä radiota. Jos yhteys radioon katkeaa, pienoissovellus korvaa tilastot tilarivillä — **Yhteys katkaistu**, **Yhdistetään** tai **Laite lepotilassa**. Se ei säilytä viimeisimpiä arvoja näytöllä.

## Widgetin lisääminen

1. Kosketa Androidin aloitusnäytön tyhjää aluetta pitkään.
2. Napauta **Widgetit**.
3. Vedä **Meshtastic**-pienoissovellus aloitusnäyttöön. Sovelluksessa on vain yksi pienoissovellus, joten valitsimessa näkyy vain sovelluksen nimi.
4. Muuta widgetin kokoa tarvittaessa — asettelu mukautuu käytettävissä olevaan tilaan.

> ℹ️ **Huomautus:** Pienoissovellus on käytettävissä vain Androidissa. Se ei ole käytettävissä Desktop- tai iOS-versioissa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — täydellinen signaalin laatu- ja paikalliset tilastot -historia sovelluksessa
- [Yhteydet](connections) — yhdistä radioon, jotta widgetillä on näytettäviä tilastoja
- [Paikallinen mesh-verkon etsintä](discovery) — kanavan ja lähetysajan käyttö koko mesh-verkossa
