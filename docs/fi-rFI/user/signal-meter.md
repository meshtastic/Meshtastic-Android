---
title: Kuinka Meshtastic-signaalimittari toimii
parent: Käyttöopas
nav_order: 15
last_updated: 2026-05-13
description: Kuinka signaalimittari laskee laadun RSSI- ja SNR-arvoista — LoRa-hajaspektri, esiasetukset ja mitä palkit todella tarkoittavat.
aliases:
  - signaali
  - signaalimittari
  - snr
  - rssi
---

# Kuinka Meshtastic-signaalimittari toimii

Meshtastic-signaalimittari — sovelluksen tutut palkit tai tilaväri — lasketaan hyvin eri tavalla kuin perinteisessä matkapuhelimessa tai Wi-Fi-reitittimessä.

Useimmat kuluttajalaitteet mittaavat yksinkertaisesti signaalin “voimakkuutta”. Koska Meshtastic käyttää **LoRa (Long Range)** -teknologiaa, signaalimittari arvioi sen sijaan kuinka “selkeä” signaali on suhteessa käytössä olevan mesh-verkon asetuksiin.

---

## 1. Kaksi mittaria: “voimakkuus” vs “selkeys”

Aina kun LoRa-radio vastaanottaa viestin, se raportoi kaksi arvoa:

- **RSSI (Received Signal Strength Indicator):** kuinka “voimakas” antenniin saapuva signaali on.
- **SNR (Signal-to-Noise Ratio):** kuinka “selkeä” signaali on suhteessa taustakohinaan.

> 💡 **Vinkki — vertauskuva:** Kuvittele, että yrität kuulla ystävääsi meluisassa ympäristössä.
>
> - **RSSI** on kuinka kovaa hän puhuu.
> - **Melutaso (Noise Floor)** on huoneen taustahäly (ilmastointi, muut ihmiset, liikenne).
> - **SNR** on se, kuinka hyvin erotat ystäväsi äänen taustamelusta.

Jos ystäväsi huutaa rock-konsertissa, signaali on erittäin voimakas (korkea RSSI), mutta et silti ymmärrä häntä, koska kohina on liian suuri (huono SNR). Toisaalta, jos hän kuiskaa hiljaisessa kirjastossa, signaali voi olla heikko (matala RSSI), mutta ymmärrät silti hyvin (hyvä SNR).

---

## 2. LoRa:n “taika”: kuuleminen kohinan alta

Tavallisissa radioissa (kuten FM tai Wi-Fi), jos kohina on signaalia voimakkaampi (negatiivinen SNR), vastaanotin kuulee vain kohinaa.

LoRa on erilainen. Se käyttää **hajaspektritekniikkaa (Spread Spectrum)**, joka mahdollistaa signaalin erottamisen myös silloin, kun se on taustakohinan alla. Siksi Meshtasticissa näkyy usein **negatiivisia SNR-arvoja** (esim. -10 dB tarkoittaa, että signaali on 10 dB heikompi kuin kohina).

Riippuen siitä, mitä Meshtastic-esiasetusta käytät (esim. “LongFast” vs. “ShortFast”), radiolla on tietty **SNR-raja** — eli suurin määrä kohinaa, jonka se kestää ennen kuin viesti alkaa kadota täysin kohinaan.

---

## 3. Kuinka signaalimittari laskee laadun

Meshtastic-sovellus käyttää sekä RSSI- että SNR-arvoja ja laskee niiden perusteella signaalille laatuluokan (Ei mitään, Huono, Kohtalainen tai Hyvä). Se skaalaa nämä arvot suoraan käytettävän radio-esiasetuksen fyysisten rajojen mukaan.

Näin sovellus päättää, montako palkkia (tai minkä värin) näet:

| Taso        | Palkit | Kriteerit                                                       | Merkitys                                                                                        |
| ----------- | ------ | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Hyvä        | 3      | RSSI parempi kuin “-115 dBm” **JA** SNR parempi kuin “-7 dB”    | Signaali on sekä voimakas että selkeä — yhteys on hyvä.                         |
| Kohtalainen | 2      | RSSI parempi kuin “-126 dBm” **TAI** SNR parempi kuin “-15 dB”  | Signaali heikkenee tai muuttuu kohinaisemmaksi, mutta on edelleen purettavissa. |
| Huono       | 1      | Osuu “Kohtalainen” ja “Ei mitään” -rajojen väliin               | Verkon kantaman reunalla tai häiriöitä esiintyy.                                |
| ei mitään   | 0      | RSSI heikompi kuin “-126 dBm” **JA** SNR heikompi kuin “-15 dB” | Lähetys on täysin hautautunut kohinaan.                                         |

---

## 4. Mitä tämä tarkoittaa sinulle

Koska Meshtasticin mittari toimii enemmän **selkeysmittarina**, se käyttäytyy eri tavalla kuin useimmat odottavat:

> **Vinkki — älä hätäänny heikosta RSSI-arvosta:** Voit nähdä näennäisesti huonon RSSI-arvon kuten “-118 dBm”. Tavallisessa puhelimessa tämä tarkoittaisi käytännössä nollakenttää. Mutta jos SNR on esimerkiksi +2 dB, Meshtastic voi silti näyttää vahvaa signaalia! _Kirjasto on hiljainen, joten kuiskaus kuuluu täydellisesti._

> **Varoitus — paikallinen kohina:** Jos käytössä on hyvä antenni ja saat silti hyvän RSSI-arvon (esim. -90 dBm), mutta signaalimittari näyttää vain “1 palkki (Huono)”, sinulla on todennäköisesti ongelma. Se tarkoittaa paikallista häiriötä — esimerkiksi halpa virtalähde, kohiseva elektroniikka tai lähellä oleva radiolähetin voi luoda niin paljon kohinaa, että se peittää mesh-verkon.

## Missä signaalitieto näkyy

Sovelluksessa signaalidata näkyy useissa paikoissa:

- **Radiolista** — signaalipalkki jokaisen radion vieressä
- **Radion tiedot** — SNR, RSSI ja signaalin laatu laitteen mittareissa
- **Reitinselvitys** — signaalin laatu jokaisessa välivaiheessa jokaiselle välittävälle radiolle
- Signaalimittarit — historiallinen SNR- ja RSSI-data mittauskaavioissa

![Radion tietorivi, jossa näkyvät SNR- ja RSSI-arvot sekä värilliset signaalipalkit](../../assets/screenshots/nodes_signal_info.png)

