---
title: Aloittaminen
parent: Käyttöopas
nav_order: 1
last_updated: 2026-07-08
description: Ensimmäisen käynnistyksen määritys — käyttöoikeudet, käyttöönottoprosessi ja seuraavat vaiheet radion yhdistämisen jälkeen.
aliases:
  - ensimmäinen käynnistys
  - asetukset
  - esittely
---

# Aloittaminen

Tervetuloa Meshtasticiin! Tämä opas opastaa sinut Meshtastic Android -sovelluksen alkuasetusten läpi.

## Ensimmäinen käynnistys

Kun avaat sovelluksen ensimmäistä kertaa, sinut ohjataan käyttöönottoprosessin läpi, joka auttaa määrittämään tarvittavat käyttöoikeudet ja asetukset. Voit suorittaa jokaisen vaiheen järjestyksessä tai ohittaa ne ja määrittää käyttöoikeudet myöhemmin Androidin asetuksissa.

### Aloitusnäkymä

Tervetulonäyttö esittelee Meshtasticin ja sen tärkeimmät ominaisuudet:

- Matkapuhelinverkosta riippumaton mesh-viestintä
- Ei vaadi matkapuhelinverkkoa tai internetyhteyttä
- Päästä päähän salattu viestintä

Napauta **"Aloita"** jatkaaksesi käyttöönottoprosessia.

![Tervetulonäyttö](../../assets/screenshots/onboarding_welcome.png)

## Käyttöoikeudet

Sovellus pyytää määrityksen aikana useita käyttöoikeuksia. Jokaisella niistä on oma tarkoituksensa, ja osa niistä on välttämättömiä perustoimintojen kannalta.

### Bluetooth-käyttöoikeus

Bluetooth on ensisijainen yhteystapa puhelimesi ja Meshtastic-radion välillä:

- **Bluetooth-skannaus** — etsi lähellä olevia Meshtastic-radioita
- **Bluetooth-yhteys** — muodosta ja ylläpidä yhteyksiä pariliitettyihin radioihin

Myönnä molemmat käyttöoikeudet pyydettäessä. Ilman Bluetoothia sinun on käytettävä sen sijaan USB- tai TCP-yhteyksiä.

### Sijaintikäyttöoikeus

> ⚠️ **Miksi sijaintikäyttöoikeus tarvitaan Bluetoothille?** Android vaatii sijaintikäyttöoikeuden lähellä olevien Bluetooth Low Energy -laitteiden löytämiseen. Tämä on Android-järjestelmän vaatimus, eikä Meshtasticin erityinen valinta.

Meshtastic käyttää sijaintiasi myös seuraaviin tarkoituksiin:

- Sijaintisi näyttäminen mesh-kartalla
- Etäisyyksien laskeminen muihin radioihin
- GPS-koordinaattiesi jakaminen muiden verkon jäsenten kanssa (jos käytössä)

Myönnä **"Sovelluksen käytön aikana"** tai **"Aina"** oman mieltymyksesi mukaan:

- **Sovelluksen käytön aikana** — sijainti päivittyy vain silloin, kun sovellus on avoinna
- **Aina** — mahdollistaa sijainnin päivittämisen taustalla jatkuvaa verkon läsnäoloa varten

Jos käyttöoikeus evätään, Bluetooth-skannaus ei toimi eikä radiosi raportoi sijaintia.

### Ilmoituskäyttöoikeus

Ilmoitukset kertovat sinulle:

- Saapuvista viesteistä kanavilta ja yksityisviesteistä
- Uusista mesh-verkkoon liittyvistä radioista
- Etäradion akun vähäisestä virrasta

> 💡 **Vinkki:** Voit myöhemmin säätää ilmoitusasetuksia Androidin järjestelmäasetuksissa. Sovellus luo jokaiselle ilmoitusluokalle oman ilmoituskanavan (sekä muutamia sisäisiä kanavia, kuten taustapalvelulle), joten voit ottaa ne käyttöön tai mykistää ne yksitellen.

### Kriittisten hälytysten käyttöoikeus

Tuetuilla laitteilla sovellus voi pyytää käyttöoikeutta kriittisiin hälytyksiin:

- Nämä ovat korkean prioriteetin ilmoituksia, jotka voivat ohittaa Älä häiritse -tilan
- Hyödyllinen hätätilanteiden verkkohälytyksille tai kiireellisille viesteille
- Voit **ohittaa** tämän vaiheen, jos et tarvitse ilmoituksia, jotka ohittavat **Älä häiritse** -tilan
- Voit määrittää tai peruuttaa tämän myöhemmin Androidin ilmoitusasetuksissa

## Määrityksen jälkeen

Kun käyttöoikeudet on myönnetty, sovellus siirtyy pääkäyttöliittymään. Ensimmäinen toimenpiteesi pitäisi olla yhteyden muodostaminen Meshtastic-radioon — katso [Yhteydet](connections) yksityiskohtaisia ohjeita varten.

> 💡 **Vinkki:** Jos ohitit käyttöoikeuksia määrityksen aikana, voit myöntää ne myöhemmin kohdassa **Androidin asetukset → Sovellukset → Meshtastic → Käyttöoikeudet**. Sovellus pyytää sinulta uudelleen käyttöoikeutta, jos puuttuva käyttöoikeus estää käyttämästä ominaisuutta, jota yrität käyttää.

## Mitä seuraavaksi?

Kun olet muodostanut yhteyden radioon, tutustu seuraaviin:

- [Yhteydet](connections) — yhdistä ensimmäinen radiolaitteesi
- [Viestit ja kanavat](messages-and-channels) — lähetä ensimmäinen viestisi
- [Radiot](nodes) — katso, ketkä ovat verkossasi
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja
- [Asetukset](settings-radio-user) — määritä radiosi ja käyttäjäprofiilisi

Uusi Meshtasticissa? [Aloitusopas](https://meshtastic.org/docs/getting-started) meshtastic.org-sivustolla käsittelee laitteiston valintaa, radion alkuasetuksia ja ensimmäisen verkon käyttöönottoa.

---
