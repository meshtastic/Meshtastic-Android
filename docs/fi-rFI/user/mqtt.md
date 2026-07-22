---
title: MQTT
parent: Käyttöopas
nav_order: 11
last_updated: 2026-05-13
description: Siltaa mesh-verkko internetiin — MQTT-välityspalvelimen käyttöönotto, salauskerrokset ja karttadatan välitys.
aliases:
  - mqtt
  - internet-silta
  - välityspalvelin
---

# MQTT

MQTT yhdistää Meshtastic-mesh-verkkosi internetiin ja mahdollistaa pitkän kantaman viestinnän radioalueen ulkopuolella.

## Yleiskatsaus

MQTT-moduuli yhdistää radion MQTT-välityspalvelimeen, mahdollistaen:

- Viestien välittymisen eri fyysisten mesh-verkkojen välillä internetin kautta
- Integraation kotiautomaatio- ja valvontajärjestelmiin
- Radioiden sijaintien julkaisemisen julkiseen Meshtastic-karttaan
- Mukautetut dataputket tiedonkeruuta ja hälytyksiä varten

## Kuinka se toimii

```
[Oma radiot] → Radio → Gateway-radio WiFi:llä → MQTT-välityspalvelin → Etä-gateway → Radio → Etä-radio
```

Internet-yhteydellinen gateway-radio (WiFi tai Ethernet) julkaisee mesh-viestit MQTT-aiheeseen. Etä-yhdyskäytävät, jotka ovat tilanneet saman aiheen, syöttävät viestit omaan paikalliseen mesh-verkkoonsa.

## Asetukset

### MQTT:n käyttöönotto

1. Siirry kohtaan **Asetukset → Moduulin asetukset → MQTT**.
2. Ota MQTT-moduuli käyttöön.
3. Määritä välityspalvelimen yhteys:

![MQTT kytkin](../../assets/screenshots/settings_switch.png)

| Asetus            | Kuvaus                                                                                                | Oletus                                              |
| ----------------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Palvelimen osoite | MQTT-välityspalvelimen osoite                                                                         | mqtt.meshtastic.org |
| Käyttäjänimi      | Välityspalvelimen tunnistautuminen                                                                    | meshdev                                             |
| Salasana          | Välityspalvelimen tunnistautuminen                                                                    | large4cats                                          |
| Juuriaihe         | Viestien perusaihe                                                                                    | msh                                                 |
| Salaus            | MQTT-viestisisällön salaus                                                                            | Käytössä                                            |
| ~~JSON Output~~   | ⚠️ **Poistettu käytöstä** — JSON-pakettituki on poistettu firmwaresta; tämä kenttä jätetään huomiotta | Ei käytössä                                         |
| TLS               | Yhteyden suojaaminen välityspalvelimeen                                                               | Ei käytössä                                         |
| Karttaraportointi | Sijainnin julkaisu julkiselle kartalle                                                                | Ei käytössä                                         |

### MQTT Proxy on This Phone

If your node has no internet access of its own, it can use the connected phone as its MQTT gateway: enable **MQTT** and **Proxy to client enabled** in the module config, and the app relays MQTT traffic between the radio and the broker over your phone's internet connection.

The **MQTT proxy on this phone** toggle at the top of the MQTT settings screen shows whether this relay is currently running and lets you cut it off (or restart it) immediately — without editing and re-saving the device's MQTT configuration.

### Oletus Meshtastic-välityspalvelin

Yhteisö ylläpitää julkista välityspalvelinta osoitteessa `mqtt.meshtastic.org`. Tämä on tarkoitettu yleiseen käyttöön ja testaukseen.

> ℹ️ **Huomautus:** Yhteydet palvelimeen `mqtt.meshtastic.org` käyttävät aina TLS-salausta (portti 8883), vaikka TLS-kytkin olisi poistettu käytöstä. Kaikkien muiden välityspalvelimien kohdalla TLS-salausta käytetään vain, kun otat sen käyttöön (TLS:llä portti 8883, ilman TLS:ää portti 1883).

> 🔒 **Tietosuoja:** Julkisen välityspalvelimen viestit ovat kaikkien tilaajien luettavissa. Käytä aina kanavasalausta yksityiseen viestintään.

### Oma välityspalvelin

Parempaa yksityisyyttä ja hallintaa varten voit käyttää omaa MQTT-välityspalvelinta:

- Mosquitto (kevyt, avoimen lähdekoodin)
- HiveMQ
- EMQX

Määritä radiosi osoittamaan omaan välityspalvelimeesi oikeilla tunnistetiedoilla.

## Karttaraportointi

Kun karttajako (Map Reporting) on käytössä, radiosi julkaisee sijaintinsa Meshtastic-yhteisökartalle:

- Näkyvissä osoitteessa [meshmap.net](https://meshmap.net) ja vastaavissa yhteisökarttapalveluissa
- Jaetaan vain sijainti- ja laitetiedot
- Poista käytöstä, jos et halua sijaintisi näkyvän julkisesti

## Lähetys vs vastaanotto

| Suunta          | Kuvaus                                           |
| --------------- | ------------------------------------------------ |
| **Lähetys**     | Viestit mesh-verkosta → MQTT-välityspalvelimeen  |
| **Vastaanotto** | Viestit MQTT-välityspalvelimesta → mesh-verkkoon |

Määritä kanavakohtaisesti, mitkä suunnat ovat käytössä viestiliikenteen ja lähetysajan käytön hallintaan.

## Viestiformaatit

MQTT käyttää protobuf-viestimuotoa:

| Muoto        | Kuvaus                                    | Käyttötarkoitus                |
| ------------ | ----------------------------------------- | ------------------------------ |
| **Protobuf** | Binäärinen Meshtastic protobuf -enkoodaus | Radioiden välinen mesh-siltaus |

> ⚠️ **Huom:** JSON-ulostulotuki on poistettu firmwaresta. `json_enabled`-asetus näkyy yhä sovelluksessa taaksepäinyhteensopivuuden vuoksi, mutta sillä ei ole vaikutusta nykyisissä firmware-versioissa.

## Salaus ja yksityisyys

Kerrostetun salausmallin ymmärtäminen:

1. **Kanavasalaus** tapahtuu meshissä _ennen_ MQTT:tä. Jos kanavallasi on PSK, MQTT-viestisisältö on jo salattu — välityspalvelin ja tilaajat näkevät vain salatun datan.
2. **MQTT-salaus** (moduuliasetus) lisää ylimääräisen salauskerroksen matkalla välityspalvelimelle. Tämä suojaa metatietoja ja reititystietoja.
3. **TLS** salaa TCP-yhteyden itse välityspalvelimeen estäen verkkotason salakuuntelun.

> 🔒 **Tärkeää:** julkisella oletuskanavalla on tunnettu avain. Oletuskanavan MQTT:n kautta lähetetyt viestit ovat käytännössä **salaamattomia** — kuka tahansa tilaaja voi purkaa ne. Käytä aina omaa PSK-avainta yksityiseen viestintään.

## Parhaat käytännöt

- Käytä kanavatasoista salausta (PSK) kanavissa, jotka yhdistetään MQTT:hen
- Älä ota MQTT:tä käyttöön laitteissa, joilla ei ole internet-yhteyttä (se puskuroidaan ja kuluttaa muistia)
- Käytä yksityistä välityspalvelinta arkaluonteisissa käyttöönotossa
- Ole tarkkana lähetysajan (downlink) käyttämisessä ruuhkaisissa MQTT-aiheissa — jokainen viesti kuluttaa radio-aikaa paikallisessa meshissä
- Harkitse lähetyssuunnan vaihtamista (uplink), jos haluat vain seurata verkkoa etänä ilman viestien lähettämistä verkkoon

## Vianetsintä

### MQTT ei yhdistä

- Tarkista WiFi — yhdyskäytävän radiolla täytyy olla aktiivinen internet-yhteys (WiFi tai Ethernet). MQTT ei toimi LoRa-radiolinkin kautta.
- Tarkista tunnukset — väärä käyttäjänimi tai salasana epäonnistuu useimmilla välityspalvelimilla hiljaisesti Tarkista ylimääräiset välilyönnit
- Palomuuri — portti 1883 (MQTT) tai 8883 (MQTT+TLS) täytyy olla auki. Jotkin verkot estävät ei-standardit portit.
- **DNS-ratkaisu** – jos käytät omaa välityspalvelimen isäntänimeä, varmista että laite pystyy ratkaisemaan sen. Kokeile välityspalvelimen IP-osoitetta suoraan.

### Viestit eivät välity

- **Tarkista lähetys ja vastaanotto-asetukset** – jos vain lähetys on käytössä, viestit kulkevat meshistä MQTT:hen mutta eivät takaisin. Ota vastaanotto käyttöön vastaanottavassa yhdyskäytävän laitteessa.
- **Kanava ei täsmää** – molempien gateway-laitteiden täytyy käyttää samaa kanavaa ja samaa PSK-avainta. Eroavaisuus tarkoittaa, että viestit on salattu eri avaimilla ja näyttävät roskalta.
- **Aihe ei täsmää** – varmista, että molemmat yhdyskäytävät käyttävät samaa juuriaihetta (Root Topic). Oletus `msh` toimii julkisessa välityspalvelimessa.

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — MQTT-moduulin asetusten viite
- [Viestit ja kanavat](messages-and-channels) — kanavasalaus ja PSK-asetukset
- [MQTT-integraatio-opas](https://meshtastic.org/docs/software/integrations/mqtt) — yksityiskohtainen MQTT-dokumentaatio meshtastic.org -sivustolla

---

