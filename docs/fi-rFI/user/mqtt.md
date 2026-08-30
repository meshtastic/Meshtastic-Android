---
title: MQTT
parent: Käyttöopas
nav_order: 11
last_updated: 2026-08-29
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
[Oma radio] → Radio → [Yhdyskäytäväradio, jossa WiFi] → MQTT-välityspalvelin → [Etäyhdyskäytävä] → Radio → [Etäradio]
```

Internet-yhteydellä varustettu yhdyskäytäväradio (WiFi tai Ethernet) julkaisee mesh-verkon viestejä MQTT-aiheeseen. Etä-yhdyskäytävät, jotka ovat tilanneet saman aiheen, syöttävät viestit omaan paikalliseen mesh-verkkoonsa.

## Asetukset

### MQTT:n käyttöönotto

1. Siirry kohtaan **Asetukset → Moduulin asetukset → MQTT**.
2. Ota MQTT-moduuli käyttöön.
3. Määritä välityspalvelimen yhteys:

![MQTT-moduulin asetukset moduuli käytössä](../../assets/screenshots/settings_switch.png)

| Asetus            | Kuvaus                                                                                                                                                                                                               | Oletus                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Palvelimen osoite | MQTT-välityspalvelimen osoite                                                                                                                                                                                        | mqtt.meshtastic.org |
| Käyttäjänimi      | Välityspalvelimen tunnistautuminen                                                                                                                                                                                   | meshdev                                             |
| Salasana          | Välityspalvelimen tunnistautuminen                                                                                                                                                                                   | large4cats                                          |
| Juuriaihe         | Viestien perusaihe                                                                                                                                                                                                   | msh                                                 |
| Salaus            | MQTT-viestisisällön salaus                                                                                                                                                                                           | Käytössä                                            |
| JSON-tuloste      | Julkaise ja vastaanota myös `/2/json/`-aihetta. Merkitty protobuf-rakenteessa vanhentuneeksi, mutta tämä on edelleen ainoa asetus tähän toimintaan — ja sovelluksen oma välityspalvelin käyttää sitä | Ei käytössä                                         |
| TLS               | Yhteyden suojaaminen välityspalvelimeen                                                                                                                                                                              | Ei käytössä                                         |
| Karttaraportointi | Sijainnin julkaisu julkiselle kartalle                                                                                                                                                                               | Ei käytössä                                         |

### Yhteyden tila ja testaa yhteys

MQTT-asetusten yläreunassa näkyy välityspalvelinyhteyden tila: **Yhdistetty**, **Yhdistetään**, **Yhdistetään uudelleen**, **Yhteys katkaistu** tai **Ei käytössä**.

**Testaa yhteys** tarkistaa välityspalvelimen ennen asetusten tallentamista radioon ja erottaa eri virhetilanteet: palvelinnimen selvitys epäonnistui, TCP-yhteys hylättiin, TLS epäonnistui, yritys aikakatkaistiin tai välityspalvelin hylkäsi tunnistetietosi syyn kera.

### MQTT-välityspalvelin tässä puhelimessa

Jos radiollasi ei ole omaa internetyhteyttä, se voi käyttää yhdistettyä puhelinta MQTT-yhdyskäytävänään: ota moduulin asetuksista käyttöön **MQTT** ja **Välityspalvelin käytössä**, jolloin sovellus välittää MQTT-liikenteen radion ja välityspalvelimen välillä puhelimesi internetyhteyden kautta.

> ℹ️ **Huomautus:** MQTT-välitys toimii vain mobiilisovelluksessa. Työpöytäsovelluksessa MQTT-asetukset ovat käytettävissä, mutta niiden taustalla ei ole välityspalvelua.

MQTT-asetusten yläreunassa oleva **MQTT-välityspalvelin tällä puhelimella** -kytkin näyttää, onko tämä välitys käytössä, ja sen avulla voit pysäyttää sen (tai käynnistää sen uudelleen) heti ilman, että radion MQTT-asetuksia tarvitsee muokata tai tallentaa uudelleen.

### Oletus Meshtastic-välityspalvelin

Yhteisö ylläpitää julkista välityspalvelinta osoitteessa `mqtt.meshtastic.org`. Tämä on tarkoitettu yleiseen käyttöön ja testaukseen. Yhteydet tähän käyttävät aina TLS:ää (portti 8883), vaikka TLS-kytkin olisi pois käytöstä. Muiden välityspalvelimien kohdalla TLS:ää käytetään vain, jos otat sen käyttöön (portti 8883 TLS:llä, 1883 ilman).

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

MQTT käyttää kahta viestisisällön muotoa:

| Muoto        | Kuvaus                                             | Käyttötarkoitus                                                                                |
| ------------ | -------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Protobuf** | Binäärinen Meshtastic protobuf -enkoodaus          | Radioiden välinen mesh-siltaus                                                                 |
| **JSON**     | Ihmisen luettavissa oleva JSON `/2/json/`-aiheessa | Mesh-verkon ulkopuolisille sovelluksille (hallintapaneelit, kotiautomaatio) |

> ℹ️ **Huomautus:** `json_enabled` on merkitty protobuf-rakenteessa vanhentuneeksi, mutta sitä ei ole korvattu eikä sitä ohiteta. Kun tämä on käytössä, sovelluksen oma MQTT-välityspalvelin tilaa `/2/json/`-aiheen ja purkaa sen viestisisällöt.

## Salaus ja yksityisyys

Kerrostetun salausmallin ymmärtäminen:

1. **Kanavasalaus** tapahtuu meshissä _ennen_ MQTT:tä. Jos kanavallasi on PSK, MQTT-viestisisältö on jo salattu — välityspalvelin ja tilaajat näkevät vain salatun datan.
2. **MQTT-salaus** (moduuliasetus) lisää ylimääräisen salauskerroksen matkalla välityspalvelimelle. Tämä suojaa metatietoja ja reititystietoja.
3. **TLS** salaa TCP-yhteyden itse välityspalvelimeen estäen verkkotason salakuuntelun.

> 🔒 **Tietoturva:** Oletusjulkisella kanavalla on tunnettu avain. Oletuskanavan MQTT:n kautta lähetetyt viestit ovat käytännössä **salaamattomia** — kuka tahansa tilaaja voi purkaa ne. Käytä aina omaa PSK-avainta yksityiseen viestintään.

## Parhaat käytännöt

- Käytä kanavatasoista salausta (PSK) kanavissa, jotka yhdistetään MQTT:hen
- Älä ota MQTT:tä käyttöön radioissa, joilla ei ole internetyhteyttä (radio puskuroi lähettämättä jääneet viestit ja tuhlaa muistia)
- Käytä yksityistä välityspalvelinta arkaluonteisissa käyttöönotossa
- Ole tarkkana lähetysajan (downlink) käyttämisessä ruuhkaisissa MQTT-aiheissa — jokainen viesti kuluttaa radio-aikaa paikallisessa meshissä
- Harkitse lähetyssuunnan vaihtamista (uplink), jos haluat vain seurata verkkoa etänä ilman viestien lähettämistä verkkoon

## Vianetsintä

### MQTT ei yhdistä

- **Tarkista WiFi** — yhdyskäytäväradiolla on oltava toimiva internetyhteys (WiFi tai Ethernet). MQTT ei toimi LoRa-radiolinkin kautta.
- **Tarkista tunnistetiedot** — virheellisillä tunnistetiedoilla useimmat välityspalvelimet epäonnistuvat ilman virheilmoitusta — tarkista erityisesti lopussa olevat välilyönnit.
- **Palomuuri** — portin 1883 (MQTT) tai 8883 (MQTT TLS:n kautta) on oltava saavutettavissa. Joissakin verkoissa sallitaan vain verkkoliikenne (portit 80 ja 443).
- **DNS-ratkaisu** – jos käytät omaa välityspalvelimen isäntänimeä, varmista että laite pystyy ratkaisemaan sen. Kokeile välityspalvelimen IP-osoitetta suoraan.

### Viestit eivät välity

- **Tarkista lähetys ja vastaanotto-asetukset** – jos vain lähetys on käytössä, viestit kulkevat meshistä MQTT:hen mutta eivät takaisin. Ota vastaanotto käyttöön vastaanottavassa yhdyskäytävän laitteessa.
- **Kanava ei täsmää** – molempien gateway-laitteiden täytyy käyttää samaa kanavaa ja samaa PSK-avainta. Eroavaisuus tarkoittaa, että viestit on salattu eri avaimilla ja näyttävät roskalta.
- **Aihe ei täsmää** – varmista, että molemmat yhdyskäytävät käyttävät samaa juuriaihetta (Root Topic). Oletus `msh` toimii julkisessa välityspalvelimessa.

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — MQTT-moduulin asetusten viite
- [Viestit ja kanavat](messages-and-channels) — kanavasalaus ja PSK-asetukset
- [MQTT-integraatio-opas](https://meshtastic.org/docs/software/integrations/mqtt) — yksityiskohtainen MQTT-dokumentaatio meshtastic.org -sivustolla
