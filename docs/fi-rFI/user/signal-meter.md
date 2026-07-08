---
title: Kuinka Meshtastic-signaalimittari toimii
parent: Käyttöopas
nav_order: 15
last_updated: 2026-06-25
description: Miten signaalimittari arvioi signaalin laadun SNR-arvon perusteella suhteessa LoRa-modeemiesiasetukseen — hajaspektri, esiasetukset ja mitä palkit todellisuudessa tarkoittavat.
aliases:
  - signaali
  - signaalimittari
  - snr
  - rssi
---

# Kuinka Meshtastic-signaalimittari toimii

Meshtasticin signaalimittari — sovelluksessa näkyvät tutut palkit tai tilan väri — lasketaan hyvin eri tavalla kuin perinteisen matkapuhelimen tai WiFi-reitittimen "palkit".

Useimmat kuluttajalaitteet mittaavat yksinkertaisesti signaalin “voimakkuutta”. Koska Meshtastic käyttää **LoRa (Long Range)** -teknologiaa, signaalimittari arvioi sen sijaan kuinka “selkeä” signaali on suhteessa käytössä olevan mesh-verkon asetuksiin.

---

## 1. Kaksi mittaria: “voimakkuus” vs “selkeys”

Aina kun LoRa-radio vastaanottaa viestin, se raportoi kaksi arvoa:

- **RSSI (Received Signal Strength Indicator):** kuinka “voimakas” antenniin saapuva signaali on.
- **SNR (Signal-to-Noise Ratio):** kuinka “selkeä” signaali on suhteessa taustakohinaan.

> 💡 **Vinkki:** Ajattele asiaa näin — yrität kuulla ystäväsi puhetta.
>
> - **RSSI** on kuinka kovaa hän puhuu.
> - **Melutaso (Noise Floor)** on huoneen taustahäly (ilmastointi, muut ihmiset, liikenne).
> - **SNR** on se, kuinka hyvin erotat ystäväsi äänen taustamelusta.

Jos ystäväsi huutaa rock-konsertissa, signaali on erittäin voimakas (korkea RSSI), mutta et silti ymmärrä häntä, koska kohina on liian suuri (huono SNR). Toisaalta, jos hän kuiskaa hiljaisessa kirjastossa, signaali voi olla heikko (matala RSSI), mutta ymmärrät silti hyvin (hyvä SNR).

---

## 2. LoRa:n “taika”: kuuleminen kohinan alta

Tavallisessa radiossa (kuten FM- tai WiFi-yhteydessä), jos taustakohina on voimakkaampi kuin signaali (negatiivinen SNR), vastaanotin kuulee vain kohinaa.

LoRa on erilainen. Se käyttää **hajaspektritekniikkaa (Spread Spectrum)**, joka mahdollistaa signaalin erottamisen myös silloin, kun se on taustakohinan alla. Siksi Meshtasticissa näkyy usein **negatiivisia SNR-arvoja** (esim. -10 dB tarkoittaa, että signaali on 10 dB heikompi kuin kohina).

Riippuen siitä, mitä Meshtastic-esiasetusta käytät (esim. “LongFast” vs. “ShortFast”), radiolla on tietty **SNR-raja** — eli suurin määrä kohinaa, jonka se kestää ennen kuin viesti alkaa kadota täysin kohinaan.

---

## 3. Kuinka signaalimittari laskee laadun

Sovellus arvioi signaalin laadun (Ei, Huono, Kohtalainen tai Hyvä) pelkästään **SNR**-arvon perusteella, suhteessa esiasetuksen **SNR-rajaan** — eli demodulaation alarajaan, joka on kuvattu aiemmin. Se ei tarkoituksella ota **RSSI**-arvoa huomioon arvioinnissa: ilman paikallista kohinatasoa RSSI ei kerro, voidaanko signaali todella purkaa, joten SNR suhteessa esiasetuksen rajaan on merkityksellinen mittari. (RSSI näytetään edelleen muualla sovelluksessa.)

Koska arviointi perustuu esiasetuksen rajaan, _sama_ SNR-arvo voi saada eri arvosanan eri esiasetuksilla — esimerkiksi `-15 dB` on hyvä arvo LongSlow-esiasetuksella, mutta käyttökelvoton ShortFast-esiasetuksella. Jos "raja" tarkoittaa käytössä olevan esiasetuksen SNR-rajaa, sovellus määrittää palkit (tai värin) seuraavasti:

| Taso        | Palkit | Kriteerit                              | Merkitys                                                                                   |
| ----------- | ------ | -------------------------------------- | ------------------------------------------------------------------------------------------ |
| Hyvä        | 3      | SNR **esiasetuksen rajan yläpuolella** | Signaali on selvästi demodulaation alarajan yläpuolella — hyvä yhteys.     |
| Kohtalainen | 2      | enintään 5,5 dB rajan alapuolella      | Signaali voidaan vielä purkaa, mutta se on lähellä demodulaation alarajaa. |
| Huono       | 1      | 5,5–7,5 dB rajan alapuolella           | Signaali on aivan esiasetuksen palautumiskyvyn rajalla.                    |
| ei mitään   | 0      | yli 7,5 dB "rajan" alapuolella         | Signaali on demodulaation alarajan alapuolella — lähetys hukkuu kohinaan.  |

> **Huomautus:** Kiinteitä SNR-rajoja, joita olet ehkä nähnyt muualla (−7 dB / −15 dB), käytetään nykyisin vain traceroute-tulosten yksittäisten hyppyjen väritykseen — ei radion yleisen signaalin laadun arviointiin.

---

## 4. Mitä tämä tarkoittaa sinulle

Koska Meshtasticin mittari toimii enemmän **selkeysmittarina**, se käyttäytyy eri tavalla kuin useimmat odottavat:

> 💡 **Vinkki:** Älä huolestu matalasta RSSI-arvosta. Saatat nähdä näennäisesti erittäin huonon RSSI-arvon, kuten `-118 dBm`. Tavallisessa puhelimessa tämä tarkoittaisi käytännössä nollakenttää. Mutta jos SNR on esimerkiksi +2 dB, Meshtastic voi silti näyttää vahvaa signaalia! _Kirjasto on hiljainen, joten kuiskaus kuuluu täydellisesti._

> ⚠️ **Varoitus:** Kiinnitä huomiota paikalliseen kohinaan. Jos liität suuren antennin ja näet hyvän RSSI-arvon (esim. `-90 dBm`), mutta signaalimittari näyttää silti vain **1 palkin (Huono)**, yhteydessä on ongelma. Se tarkoittaa paikallista häiriötä — esimerkiksi halpa virtalähde, kohiseva elektroniikka tai lähellä oleva radiolähetin voi luoda niin paljon kohinaa, että se peittää mesh-verkon.

## Missä signaalitieto näkyy

Sovelluksessa signaalidata näkyy useissa paikoissa:

- **Radiolista** — signaalipalkki jokaisen radion vieressä
- **Radion tiedot** — SNR, RSSI ja signaalin laatu laitteen mittareissa
- **Reitinselvitys** — signaalin laatu jokaisessa välivaiheessa jokaiselle välittävälle radiolle
- Signaalimittarit — historiallinen SNR- ja RSSI-data mittauskaavioissa

![Radion tietorivi, jossa näkyvät SNR- ja RSSI-arvot sekä värilliset signaalipalkit](../../assets/screenshots/nodes_signal_info.png)

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — missä signaalipalkit näkyvät radioluettelossa
- [Radion mittarit](node-metrics) — SNR ja RSSI-historia ja radion signaalin laadun viitearvot
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — modeemiesiasetukset ja niiden SNR-rajat

---

