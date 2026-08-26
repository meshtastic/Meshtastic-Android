---
title: Aloittaminen
parent: Käyttöopas
nav_order: 1
last_updated: 2026-08-25
description: Ensimmäisen käynnistyksen määritys — käyttöoikeudet, käyttöönottoprosessi ja seuraavat vaiheet radion yhdistämisen jälkeen.
aliases:
  - ensimmäinen käynnistys
  - asetukset
  - esittely
---

# Aloittaminen

Tervetuloa Meshtasticiin! Tämä opas opastaa sinut Meshtastic Android -sovelluksen alkuasetusten läpi.

## Ensimmäinen käynnistys

Kun avaat sovelluksen ensimmäistä kertaa, sinut ohjataan käyttöönottoprosessin läpi, joka auttaa määrittämään tarvittavat käyttöoikeudet ja asetukset. Jokainen vaihe voidaan suorittaa järjestyksessä tai ohittaa – mikään niistä ei ole kertaluonteinen mahdollisuus. Kaikki käyttöoikeudet voidaan tarkistaa ja myöntää myöhemmin sovelluksen **Asetukset → Käyttöoikeudet** -kohdassa.

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

> ⚠️ **Tarvitaanko Bluetoothiin sijaintia?** Android 11:ssä ja sitä vanhemmissa versioissa kyllä. Niissä Bluetooth-laitteiden haku käsitellään sijaintiominaisuutena, joten sovellus pyytää **Lähistön laitteet** -käyttöoikeuden sijaan sijaintikäyttöoikeutta, ja myös **Sijaintipalvelut** on oltava käytössä, jotta haku toimii. Näissä versioissa Bluetooth-näytössä näkyy kaksi sijaan vain **yksi** sijaintikäyttöoikeuden vaihe, koska kyseessä on yksi järjestelmän käyttöoikeus. Sen pyytäminen kahdesti voisi johtaa siihen, että Android lakkaa näyttämästä käyttöoikeusikkunaa kokonaan (toisen hylkäyksen jälkeen Android 11:ssä tai "Älä kysy uudelleen" -valinnan jälkeen Android 10:ssä ja vanhemmissa versioissa). **Android 12:ssa ja uudemmissa versioissa** nämä ovat erillisiä: "Lähistön laitteet" on määritelty asetuksella `neverForLocation`, eikä sijaintikäyttöoikeuden hylkääminen estä radion löytämistä tai siihen yhdistämistä.

Meshtastic käyttää sijaintiasi myös seuraaviin tarkoituksiin:

- Sijaintisi näyttäminen mesh-kartalla
- Etäisyyksien laskeminen muihin radioihin
- GPS-koordinaattiesi jakaminen muiden verkon jäsenten kanssa (jos käytössä)

Myönnä **"Sovelluksen käytön aikana"** tai **"Aina"** oman mieltymyksesi mukaan:

- **Sovelluksen käytön aikana** — sijainti päivittyy vain silloin, kun sovellus on avoinna
- **Aina** — mahdollistaa sijainnin päivittämisen taustalla jatkuvaa verkon läsnäoloa varten

Sijaintikäyttöoikeuden hylkääminen ei estä sovelluksen muuta toimintaa. Android 12:ssa ja uudemmissa versioissa Bluetooth toimii edelleen, ja vain kartta, sijainnin näyttäminen ja sijainnin jakaminen poistuvat käytöstä. Android 11:ssä ja sitä vanhemmissa versioissa myös Bluetooth-laitteiden haku lakkaa toimimasta, koska Android edellyttää siihen sijaintikäyttöoikeutta.

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

### Käyttöoikeuksien tarkistaminen myöhemmin

**Asetukset → Käyttöoikeudet** näyttää kaikkien käyttöaikaisten käyttöoikeuksien tilan. Kun mikään käyttöoikeus ei vaadi huomiotasi, siinä lukee _Kaikki sallittu_. Jos jokin käyttöoikeus vaatii huomiota, siinä näytetään niiden määrä, ja näkymä avautuu automaattisesti. Napauta riviä nähdäksesi koko luettelon milloin tahansa:

| Tila                                                  | Mitä rivin napauttaminen tekee                                                                                       |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **Sallittu**                                          | Avaa järjestelmän sivun, jossa voit tarkistaa tai peruuttaa käyttöoikeuden                                           |
| **Ei kysytty vielä**                                  | Pyytää sitä                                                                                                          |
| **Estetty - napauta salliaksesi**                     | Selittää, mihin käyttöoikeutta tarvitaan, ja pyytää sitä uudelleen, jos hyväksyt                                     |
| **Estetty - napauta avataksesi järjestelmäasetukset** | Android ei enää näytä käyttöoikeusikkunaa, joten tämä avaa sivun, jossa voit ottaa käyttöoikeuden uudelleen käyttöön |
| **Ei tarvita tässä Android-versiossa**                | Ei mitään – tätä käyttöoikeutta ei ole laitteessasi                                                                  |

Tämä koskee erityisesti ilmoituksia. Aiemmin sovellus pyysi tätä käyttöoikeutta vain yhdessä paikassa – käyttöönottovaiheessa – joten sen hylkääminen esti viesti-, uusi radio- ja akun alhaisen varaustason ilmoitukset, eikä sovellus voinut pyytää käyttöoikeutta uudelleen. Kun olet hylännyt käyttöoikeuspyynnön lopullisesti (Android 11:ssä ja uudemmissa versioissa toisen hylkäyksen jälkeen), Android ei enää näytä käyttöoikeusikkunaa. Tällöin tämä rivi muuttuu tilaan **Estetty** ja avaa sen sijaan järjestelmän asetussivun.

## Määrityksen jälkeen

Kun käyttöoikeudet on myönnetty, sovellus siirtyy pääkäyttöliittymään. Ensimmäinen toimenpiteesi pitäisi olla yhteyden muodostaminen Meshtastic-radioon — katso [Yhteydet](connections) yksityiskohtaisia ohjeita varten.

> 💡 **Vinkki:** Jos ohitit käyttöoikeuksia käyttöönottovaiheessa, avaa sovelluksessa **Asetukset → Käyttöoikeudet**. Kaikki käyttöaikaiset käyttöoikeudet näkyvät siellä nykyisessä tilassaan sekä linkkinä niiden hallintaan, mukaan lukien ilmoitukset, joita järjestelmä ei pyydä toista kertaa automaattisesti.

Sovelluksen toiminnot pyytävät käyttöoikeuksia myös tarpeen mukaan. Kun **Yhteydet**-näytössä napautat **Hae** ilman Bluetooth-käyttöoikeutta, sovellus kertoo, mihin käyttöoikeutta tarvitaan, ja tarjoaa mahdollisuuden pyytää sitä. Kun Android ei enää näytä käyttöoikeusikkunaa, sama painike avaa sen sijaan järjestelmän asetussivun eikä jää toimettomaksi.

## Mitä seuraavaksi?

Kun olet muodostanut yhteyden radioon, tutustu seuraaviin:

- [Yhteydet](connections) — yhdistä ensimmäinen radiolaitteesi
- [Viestit ja kanavat](messages-and-channels) — lähetä ensimmäinen viestisi
- [Radiot](nodes) — katso, ketkä ovat verkossasi
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja
- [Asetukset](settings-radio-user) — määritä radiosi ja käyttäjäprofiilisi

Uusi Meshtasticissa? [Aloitusopas](https://meshtastic.org/docs/getting-started) meshtastic.org-sivustolla käsittelee laitteiston valintaa, radion alkuasetuksia ja ensimmäisen verkon käyttöönottoa.

---
