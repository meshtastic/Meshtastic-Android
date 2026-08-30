---
title: Kuinka Meshtastic-signaalimittari toimii
parent: Käyttöopas
nav_order: 15
last_updated: 2026-08-29
description: Miten signaalimittari arvioi signaalin laadun SNR-arvon perusteella suhteessa LoRa-modeemiesiasetukseen — hajaspektri, esiasetukset ja mitä palkit todellisuudessa tarkoittavat.
aliases:
  - signaali
  - signaalimittari
  - snr
  - rssi
---

# Kuinka Meshtastic-signaalimittari toimii

Meshtasticin signaalimittari — radion vieressä näkyvät palkit tai tilaväri — lasketaan eri tavalla kuin matkapuhelimen tai WiFi-reitittimen signaalipalkit. Tällä sivulla kerrotaan, mitä se mittaa ja miksi sama lukema voi tarkoittaa eri asiaa toisessa esiasetuksessa.

## RSSI ja SNR

Joka kerta kun LoRa-radio vastaanottaa viestin, se raportoi kaksi mittausta:

- **RSSI (Received Signal Strength Indicator)** — antenniin saapuvan signaalin raakateho.
- **SNR (Signal-to-Noise Ratio)** — kuinka paljon signaali erottuu taustakohinan yläpuolelle.

> 💡 **Vinkki:** Ajattele RSSI:tä ystävän puheen voimakkuutena ja SNR:ää siinä, kuinka helposti erotat hänen äänensä huoneen taustamelusta. Ystävä, joka huutaa rock-konsertissa, voi kuulua kovaa (korkea RSSI) mutta olla epäselvä (huono SNR), kun taas hiljaisessa kirjastossa kuiskattu puhe voi olla vaimea (matala RSSI) mutta täysin selkeä (erinomainen SNR).

## Vastaanotto kohinatason alapuolelta

Tavalliset radiot, kuten FM tai WiFi, menettävät signaalin kohinaan, kun taustakohina on sitä voimakkaampi (negatiivinen SNR). LoRan hajaspektrimodulaatio mahdollistaa signaalin vastaanottamisen kohinasta myös silloin, kun kohina on signaalia voimakkaampi, joten negatiiviset SNR-arvot ovat Meshtasticissa tavallisia ja odotettuja — esimerkiksi −10 dB tarkoittaa, että signaali on 10 desibeliä taustakohinaa heikompi.

Jokaisella modeemiesiasetuksella on SNR-raja: pienin SNR-arvo, jolla kyseinen esiasetus pystyy vielä purkamaan viestin. Hitaammat esiasetukset sietävät heikomman ja kohinaisemman signaalin (negatiivisempi raja, pidempi kantama); nopeammat esiasetukset tarvitsevat voimakkaamman signaalin (vähemmän negatiivinen raja, lyhyempi kantama).

## Signaalin laadun arviointi

Sovellus arvioi signaalin laadun (Ei mitään, Huono, Tyydyttävä tai Hyvä) pelkästään SNR:n perusteella vertaamalla sitä käytössä olevan esiasetuksen SNR-rajaan. RSSI:tä ei oteta huomioon: ilman tietoa paikallisesta kohinatasosta pelkkä RSSI ei kerro, voidaanko signaali purkaa. RSSI on silti käytettävissä — radion tiedoissa ja mittarikaavioissa.

Koska arviointi suhteutetaan esiasetukseen, sama SNR saa eri arvioinnin eri esiasetuksilla. SNR −16 dB arvioidaan Hyväksi Long Fast -esiasetuksella (SNR-raja −17.5 dB), mutta Ei mitään -tasolle Short Fast -esiasetuksella (SNR-raja −7.5 dB). Jos `raja` tarkoittaa käytössä olevan esiasetuksen SNR-rajaa:

| Taso        | Palkit | Kriteerit                                                      | Merkitys                                                                                                              |
| ----------- | ------ | -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Hyvä        | 3      | SNR yli `rajan`                                                | Selvästi demodulointirajan yläpuolella — yhteys on hyvä.                                              |
| Kohtalainen | 2      | alle 5.5 dB `rajan` alapuolella                | Voidaan vielä purkaa, mutta lähellä rajaa.                                                            |
| Huono       | 1      | 5.5–7.5 dB `rajan` alapuolella | Esiasetuksen vastaanottokyvyn rajalla.                                                                |
| ei mitään   | 0      | yli 7.5 dB `rajan` alapuolella                 | Selvästi esiasetuksen rajan alapuolella; tältä radiolta tulevia lisäpaketteja todennäköisesti katoaa. |

> ℹ️ **Huomautus:** Reitiselvityksen hyppyjen värit käyttävät kiinteitä raja-arvoja (−7 dB / −15 dB); radion signaalimittari käyttää sen sijaan esiasetukseen suhteutettua arviointia.

## Paikallisten häiriöiden tunnistaminen

Hyvä RSSI mutta vain yksi palkki (Huono) viittaa paikallisiin häiriöihin, ei etäisyyteen. Halpa virtalähde, häiriöinen tietokone tai lähellä oleva lähetin voi aiheuttaa niin paljon kohinaa, että muuten vahva signaali peittyy.

## Missä signaalitieto näkyy

Sovelluksessa signaalitiedot näkyvät useassa paikassa:

- **Radioluettelo** — signaalipalkkikuvake jokaisen radion vieressä
- **Radion tiedot** — SNR, RSSI ja signaalin laatu laitteen mittareissa
- **Reitinselvitys** — signaalin laatu jokaisessa välivaiheessa jokaiselle välittävälle radiolle
- Signaalimittarit — historiallinen SNR- ja RSSI-data mittauskaavioissa

![Radioluettelon merkintä, jossa näkyy hyvä signaali: 12.5 dB SNR, −42 dBm RSSI ja vihreä signaalipalkkikuvake](../../assets/screenshots/nodes_signal_info.png)

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — missä signaalipalkit näkyvät radioluettelossa
- [Radion mittarit](node-metrics) — SNR ja RSSI-historia ja radion signaalin laadun viitearvot
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — modeemiesiasetukset ja niiden SNR-rajat
