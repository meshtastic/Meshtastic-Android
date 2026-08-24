---
title: Aloitusnäytön widget
parent: Käyttöopas
nav_order: 20
last_updated: 2026-06-25
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

Avaa sovellus napauttamalla widgetiä tai pyydä uudet tilastot sen päivityspainikkeella.

> 💡 **Vinkki:** Arvot vastaavat sitä radiota, johon olet parhaillaan yhdistetty. Jos sovellus ei ole yhdistetty radioon, widget näyttää viimeksi tunnetut tilastot, kunnes yhteys muodostuu uudelleen.

## Widgetin lisääminen

1. Paina pitkään tyhjää kohtaa Androidin aloitusnäytössä.
2. Napauta **Widgetit**.
3. Etsi luettelosta **Meshtastic** ja vedä **Paikalliset tilastot** -widget aloitusnäyttöön.
4. Muuta widgetin kokoa tarvittaessa — asettelu mukautuu käytettävissä olevaan tilaan.

> ⚠️ **Huomautus:** Widget on käytettävissä vain Androidissa. Se ei ole käytettävissä Desktop- tai iOS-versioissa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — täydellinen signaalin laatu- ja paikalliset tilastot -historia sovelluksessa
- [Yhteydet](connections) — yhdistä radioon, jotta widgetillä on näytettäviä tilastoja
- [Haku](discovery) — kanavan ja lähetysajan käyttöaste koko verkossa

---
