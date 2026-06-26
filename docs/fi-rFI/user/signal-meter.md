---
title: Kuinka Meshtastic-signaalimittari toimii
parent: Käyttöopas
nav_order: 15
last_updated: 2026-06-25
description: How the signal meter rates quality from SNR relative to the LoRa modem preset — spread spectrum, presets, and what the bars really mean.
aliases:
  - signaali
  - signaalimittari
  - snr
  - rssi
---

# Kuinka Meshtastic-signaalimittari toimii

The Meshtastic signal meter — the familiar bars or status color in the app — is calculated very differently than the "bars" on a traditional cell phone or WiFi router.

Useimmat kuluttajalaitteet mittaavat yksinkertaisesti signaalin “voimakkuutta”. Koska Meshtastic käyttää **LoRa (Long Range)** -teknologiaa, signaalimittari arvioi sen sijaan kuinka “selkeä” signaali on suhteessa käytössä olevan mesh-verkon asetuksiin.

---

## 1. Kaksi mittaria: “voimakkuus” vs “selkeys”

Aina kun LoRa-radio vastaanottaa viestin, se raportoi kaksi arvoa:

- **RSSI (Received Signal Strength Indicator):** kuinka “voimakas” antenniin saapuva signaali on.
- **SNR (Signal-to-Noise Ratio):** kuinka “selkeä” signaali on suhteessa taustakohinaan.

> 💡 **Tip:** Here's an analogy — imagine you are trying to hear a friend talking to you.
>
> - **RSSI** on kuinka kovaa hän puhuu.
> - **Melutaso (Noise Floor)** on huoneen taustahäly (ilmastointi, muut ihmiset, liikenne).
> - **SNR** on se, kuinka hyvin erotat ystäväsi äänen taustamelusta.

Jos ystäväsi huutaa rock-konsertissa, signaali on erittäin voimakas (korkea RSSI), mutta et silti ymmärrä häntä, koska kohina on liian suuri (huono SNR). Toisaalta, jos hän kuiskaa hiljaisessa kirjastossa, signaali voi olla heikko (matala RSSI), mutta ymmärrät silti hyvin (hyvä SNR).

---

## 2. LoRa:n “taika”: kuuleminen kohinan alta

For standard radios (like FM or WiFi), if the background noise is louder than the signal (a negative SNR), the receiver just hears static.

LoRa on erilainen. Se käyttää **hajaspektritekniikkaa (Spread Spectrum)**, joka mahdollistaa signaalin erottamisen myös silloin, kun se on taustakohinan alla. Siksi Meshtasticissa näkyy usein **negatiivisia SNR-arvoja** (esim. -10 dB tarkoittaa, että signaali on 10 dB heikompi kuin kohina).

Riippuen siitä, mitä Meshtastic-esiasetusta käytät (esim. “LongFast” vs. “ShortFast”), radiolla on tietty **SNR-raja** — eli suurin määrä kohinaa, jonka se kestää ennen kuin viesti alkaa kadota täysin kohinaan.

---

## 3. Kuinka signaalimittari laskee laadun

The app rates your signal quality (None, Bad, Fair, or Good) from **SNR alone, measured relative to the preset's SNR Limit** — the demodulation floor described above. It deliberately does **not** factor RSSI into the rating: without the local noise floor, RSSI cannot tell you whether a signal is actually decodable, so SNR-versus-the-preset-limit is the meaningful measure. (RSSI is still displayed to you elsewhere.)

Because the rating is relative to the preset limit, the _same_ SNR can rate differently on different presets — `-15 dB` is healthy on `LongSlow` but unusable on `ShortFast`. Letting `limit` be the active preset's SNR Limit, here is how the app picks the bars (or color):

| Taso        | Palkit | Kriteerit                            | Merkitys                                                                                 |
| ----------- | ------ | ------------------------------------ | ---------------------------------------------------------------------------------------- |
| Hyvä        | 3      | SNR **above** the preset's `limit`   | Signal is comfortably above the demodulation floor — healthy connection. |
| Kohtalainen | 2      | within `5.5 dB` below the `limit`    | Decodable, but getting close to the floor.                               |
| Huono       | 1      | within `7.5 dB` below the `limit`    | At the very edge of what the preset can recover.                         |
| ei mitään   | 0      | more than `7.5 dB` below the `limit` | Below the floor — transmission lost to noise.                            |

> **Note:** The fixed SNR thresholds you may have seen elsewhere (`-7 dB` / `-15 dB`) are now only used for coloring individual hops in traceroute results — not for the per-node signal meter described here.

---

## 4. Mitä tämä tarkoittaa sinulle

Koska Meshtasticin mittari toimii enemmän **selkeysmittarina**, se käyttäytyy eri tavalla kuin useimmat odottavat:

> 💡 **Tip:** Don't panic over low RSSI. You might see a seemingly terrible RSSI value like `-118 dBm`. Tavallisessa puhelimessa tämä tarkoittaisi käytännössä nollakenttää. Mutta jos SNR on esimerkiksi +2 dB, Meshtastic voi silti näyttää vahvaa signaalia! _Kirjasto on hiljainen, joten kuiskaus kuuluu täydellisesti._

> ⚠️ **Warning:** Watch out for local noise. If you hook up a massive antenna and see a great RSSI (e.g., `-90 dBm`) but your signal meter is only showing **1 Bar (Bad)**, you have a problem. Se tarkoittaa paikallista häiriötä — esimerkiksi halpa virtalähde, kohiseva elektroniikka tai lähellä oleva radiolähetin voi luoda niin paljon kohinaa, että se peittää mesh-verkon.

## Missä signaalitieto näkyy

Sovelluksessa signaalidata näkyy useissa paikoissa:

- **Radiolista** — signaalipalkki jokaisen radion vieressä
- **Radion tiedot** — SNR, RSSI ja signaalin laatu laitteen mittareissa
- **Reitinselvitys** — signaalin laatu jokaisessa välivaiheessa jokaiselle välittävälle radiolle
- Signaalimittarit — historiallinen SNR- ja RSSI-data mittauskaavioissa

![Radion tietorivi, jossa näkyvät SNR- ja RSSI-arvot sekä värilliset signaalipalkit](../../assets/screenshots/nodes_signal_info.png)

## Aiheeseen liittyvät aiheet

- [Nodes](nodes) — where signal bars appear in the node list
- [Node Metrics](node-metrics) — SNR/RSSI history and the per-node signal quality reference
- [Settings — Radio & User](settings-radio-user) — modem presets and their SNR limits

---

