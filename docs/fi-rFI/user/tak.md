---
title: TAK-integraatio
parent: Käyttöopas
nav_order: 10
last_updated: 2026-05-13
description: ATAK:n ja WinTAK:n yhteentoimivuus — CoT-sijaintijako, TAK-roolit ja lisäosien käyttöönotto.
aliases:
  - tak
  - atak
  - team awareness kit
---

# TAK-integraatio

Meshtastic integroituu Team Awareness Kit (TAK) -ekosysteemiin, mahdollistaen yhteentoimivuuden Meshtastic-verkkoradioiden ja TAK-sovellusten kuten ATAK:n ja WinTAK:n välillä.

## Yleiskatsaus

TAK-moduuli mahdollistaa Meshtastic-radioille:

- Sijaintidatan jakamisen TAK-yhteensopivassa CoT (Cursor on Target) -muodossa
- Näkyä tiimin jäseninä TAK-karttanäkymissä
- Vastaanottaa TAK PLI (Position Location Information) -viestejä

## Asetukset

### Edellytykset

- ATAK (Android Team Awareness Kit) tai WinTAK asennettuna
- Meshtastic ATAK -lisäosa asennettuna
- TAK-moduuli käytössä Meshtastic-radiossasi

### Asetukset

1. Siirry kohtaan **Asetukset → Moduulin asetukset → TAK**.
2. Ota TAK-moduuli käyttöön.
3. Määritä TAK-tiimin/ryhmän asetukset:

![Moduulin kytkin](../../assets/screenshots/settings_switch.png)

| Asetus   | Kuvaus                           |
| -------- | -------------------------------- |
| Käytössä | Ota TAK-yhteentoimivuus käyttöön |
| Tila     | TAK-yhteensopiva lähtötila       |

### ATAK-lisäosan asennus

1. Asenna Meshtastic ATAK -lisäosa lisäosien lähteestä.
2. Avaa ATAK ja ota Meshtastic-lisäosa käyttöön.
3. Lisäosa välittää viestejä ATAK:n ja mesh-verkkosi välillä.

## TAK-roolit

TAK-rooleilla määritetyt radiot käyttäytyvät eri tavalla kuin tavalliset client-laitteet:

| Rooli           | Kuvaus                                                                                                                                                                                                                                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **TAK**         | Täysi TAK-yhteentoimivuus — lähettää ja vastaanottaa CoT-dataa, chat-viestejä ja PLI-päivityksiä. Toimii tavallisen clientin lisäksi TAK-siltana.                                                                                                                                  |
| **TAK Tracker** | Vain sijaintitietoihin perustuva TAK-lähtö — lähettää automaattisesti PLI-dataa säännöllisin väliajoin ilman käyttäjän toimintaa. Optimoitu valvomattomiin sijaintilähettimiin (ajoneuvot, laitteet ja reittipisteet). Ei välitä chat-viestejä. |

> 💡 **Vinkki:** Käytä **TAK Tracker** -tilaa laitteille, joiden tarvitsee vain lähettää sijainti (esim. ajoneuvoon asennettu radio). Käytä **TAK**-tilaa laitteille, joissa käyttäjät osallistuvat aktiivisesti TAK-toimintaan.

### CoT (Cursor on Target) -muoto

TAK-viestit käyttävät Cursor on Target XML -muotoa — sotilaskäytössä olevaa standardia tilannetiedon jakamiseen. Meshtastic muuntaa sisäiset protobuf-viestinsä CoT-muotoon silloin, kun se välittää dataa TAK-järjestelmiin, joten manuaalista muunnosta ei tarvita.

## TAK-identiteetti

Kun käytät TAK-rooleja, radiosi lähettää identiteettitietoja, jotka näkyvät TAK-kartoilla:

| Asetus      | Kuvaus                                                                                                                              |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Tiimi       | TAK-kartan tiimiväri (esim. sininen, punainen, syaani, vihreä)                                   |
| Rooli       | Operatiivinen roolisi (tiimin jäsen, tiimin johtaja, esikunta, ensihoitaja, viestivastaava jne.) |
| Kutsutunnus | TAK-kutsutunnuksesi (oletuksena Meshtastic-laitteesi pitkä nimi)                                                 |

Nämä asetukset näkyvät kohdassa **Asetukset → Moduulin asetukset → TAK**, kun TAK-moduuli on käytössä.

> 💡 **Vinkki:** Tiimi- ja roolivärit ovat TAK-järjestelmän standardeja ryhmävärikoodauksia. Koordinoi TAK-tiimisi kanssa yhtenäisten tiimimääritysten käyttämiseksi.

## Wire Format (V1 / V2)

Meshtastic tukee kahta TAK-wire-formaattia:

| Muoto                                | Yhteensopivuus                                                  | Ominaisuudet                                               |
| ------------------------------------ | --------------------------------------------------------------- | ---------------------------------------------------------- |
| V1 (vanha versio) | ATAK Plugin v1.x, vanhempi firmware             | Perustason CoT-sijainnin jakaminen                         |
| V2 (nykyinen)     | ATAK Plugin v2.x, firmware 2.3+ | Täysi CoT-tuki sisältäen chatin, reitit ja zstd-pakkauksen |

Sovellus valitsee automaattisesti V2-version, kun molemmat puolet tukevat sitä. Manuaalista asetusta ei tarvita — TAK-moduuli neuvottelee formaatin firmware-version perusteella.

## Käyttö ATAK:n kanssa

Kun asetukset on määritetty:

- Meshtastic-radiot näkyvät ATAK-kartalla merkkeinä kutsutunnuksineen
- Viestit voivat välittyä mesh-verkon ja TAK-verkon välillä
- Sijaintipäivitykset kulkevat kaksisuuntaisesti Meshtasticin ja TAK-järjestelmän välillä
- TAK Tracker -radiot lähettävät PLI-sijaintia automaattisesti — niiden sijainti näkyy ATAK-kartoilla ilman erillistä ATAK-asetusta

> ⚠️ **Huom:** TAK-integraatio vaatii tietyt laiteroolit ja moduuliasetukset. Tavalliset client-laitteet eivät osallistu TAK-toimintoihin automaattisesti.

## Vianetsintä

| Ongelma                                 | Syy                                                | Ratkaisu                                                                                        |
| --------------------------------------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Radio ei näy ATAK-kartalla              | TAK-moduuli pois käytöstä tai väärä rooli          | Varmista, että TAK-moduuli on käytössä ja rooli on joko TAK tai TAK Tracker                     |
| Sijaintipäivitykset ovat vanhentuneita  | GPS-signaali katkennut tai lähetysväli liian pitkä | Tarkista GPS-tila; lyhennä sijaintilähetyksen väliä kohdassa Sijainnin asetukset                |
| ATAK-lisäosa näyttää “yhteys katkennut” | Bluetooth-yhteys katkennut tai lisäosa on kaatunut | Yhdistä Bluetooth uudelleen Meshtastic-sovelluksessa ja käynnistä ATAK-lisäosa uudelleen        |
| Viestit eivät välity                    | V1-muoto ei tue chatia                             | Varmista, että molemmilla laitteilla on firmware 2.3+ ja V2-wire-muoto käytössä |
| CoT-data ei kulje                       | Kanava ei täsmää                                   | Kaikkien TAK-laitteiden täytyy olla samalla kanavalla ja yhteensopivalla salauksella            |

## Tietoturvahuomiot

- TAK-data sisältää sijainti- ja kutsutunnustietoja
- Varmista, että kanavan salaus on oikein määritetty, kun käytät TAK:ia arkaluonteisissa ympäristöissä
- TAK-moduuli noudattaa samaa kanavasalausta kuin muut Meshtastic-viestit

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — TAK-moduulin asetukset
- [Radiot](nodes) — TAK- ja TAK Tracker -roolit radiolistassa
- [Kartta ja reittipisteet](map-and-waypoints) — radioiden sijainnit kartalla
- [ATAK-lisäosan opas](https://meshtastic.org/docs/software/integrations/atak-plugin) — yksityiskohtainen ATAK-asennus meshtastic.org -sivustolla

---

