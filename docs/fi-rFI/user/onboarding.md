---
title: Aloittaminen
parent: Käyttöopas
nav_order: 1
last_updated: 2026-08-29
description: Ensimmäisen käynnistyksen määritys — käyttöoikeudet, käyttöönottoprosessi ja seuraavat vaiheet radion yhdistämisen jälkeen.
aliases:
  - ensimmäinen käynnistys
  - asetukset
  - esittely
---

# Aloittaminen

Tällä sivulla kerrotaan Meshtastic Android -sovelluksen ensimmäisen käynnistyksen käyttöönotosta, mitä kukin käyttöoikeus tarkoittaa ja miten niihin voi palata myöhemmin.

## Ensimmäinen käynnistys

Kun avaat sovelluksen ensimmäisen kerran, se ohjaa sinut käyttöönottoon, jossa määritetään tarvittavat käyttöoikeudet ja asetukset. Suorita vaiheet järjestyksessä tai ohita ne — mikään täällä ei ole kertaluonteinen mahdollisuus. Kaikki käyttöoikeudet voidaan tarkistaa ja myöntää myöhemmin sovelluksen **Asetukset → Käyttöoikeudet** -kohdassa.

### Aloitusnäkymä

Tervetulonäkymä esittelee Meshtasticin kolmella ominaisuusrivillä:

|                                |                                                                                                                                        |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Pysy yhteydessä kaikkialla** | Viestitä ilman verkkoyhteyttä ystäviesi ja yhteisösi kanssa ilman matkapuhelinverkkoa.                                 |
| **Luo omat verkkosi**          | Luo vaivattomasti yksityisiä meshtastic verkkoja turvalliseen ja luotettavaan viestintään kaukana asutuista paikoista. |
| **Seuraa ja jaa sijainteja**   | Jaa sijaintisi reaaliaikaisesti ja varmista ryhmäsi yhteistoiminta GPS-toimintojen avulla.                             |

Napauta **Aloita** siirtyäksesi käyttöönottoon.

![Tervetulonäyttö](../../assets/screenshots/onboarding_welcome.png)

## Käyttöoikeudet

Sovellus pyytää määrityksen aikana useita käyttöoikeuksia. Jokaisella niistä on oma tarkoituksensa, ja osa niistä on välttämättömiä perustoimintojen kannalta.

### Bluetooth-käyttöoikeus

Bluetooth on ensisijainen yhteystapa puhelimesi ja Meshtastic-radion välillä:

- **Bluetooth-skannaus** — etsi lähellä olevia Meshtastic-radioita
- **Bluetooth-yhteys** — muodosta ja ylläpidä yhteyksiä pariliitettyihin radioihin

Myönnä molemmat käyttöoikeudet pyydettäessä. Ilman Bluetoothia sinun on käytettävä sen sijaan USB- tai TCP-yhteyksiä.

### Sijaintikäyttöoikeus

> ⚠️ **Onko Bluetoothiin tarvittu sijaintilupa?** Android 11 ja sitä vanhemmissa versioissa näkyy Bluetooth-näytössä vain yksi sijaintivaihe kahden sijaan — näissä versioissa Bluetooth-skannaus käsitellään sijaintiominaisuutena, joten sovellus pyytää **Sijainti**-käyttöoikeutta **Lähellä olevat laitteet** -käyttöoikeuden sijaan. Käyttöoikeuden pyytäminen kahdesti voisi johtaa tilanteeseen, jossa Android lakkaa näyttämästä valintaikkunaa kokonaan (toinen hylkäys Android 11:ssä, **Älä kysy uudelleen** -valintaruutu Android 10:ssä ja sitä vanhemmissa versioissa). **Android 12:ssa ja uudemmissa versioissa** nämä ovat erillisiä: "Lähistön laitteet" on määritelty asetuksella `neverForLocation`, eikä sijaintikäyttöoikeuden hylkääminen estä radion löytämistä tai siihen yhdistämistä.

Meshtastic käyttää sijaintiasi myös seuraaviin tarkoituksiin:

- Sijaintisi näyttäminen mesh-kartalla
- Etäisyyksien laskeminen muihin radioihin
- GPS-koordinaattiesi jakaminen muiden verkon jäsenten kanssa (jos käytössä)

Myönnä **Sovelluksen käytön aikana**. Sovellus ei pyydä taustasijaintia — sen manifestissa ei ole ACCESS_BACKGROUND_LOCATION-oikeutta — joten Android ei tarjoa **Aina**-vaihtoehtoa, ja sijaintipäivitykset tapahtuvat sovelluksen ollessa etualalla tai sen suorittaessa taustapalvelua.

Sijaintikäyttöoikeuden hylkääminen ei estä sovelluksen muuta toimintaa. Android 12:ssa ja uudemmissa versioissa Bluetooth toimii edelleen, ja vain kartta, sijainnin näyttäminen ja sijainnin jakaminen poistuvat käytöstä. Android 11:ssä ja uudemmissa versioissa myös Bluetooth-skannaus estyy, koska Android liittää sen tähän käyttöoikeuteen — lisäksi järjestelmän **Sijaintipalvelut** on oltava käytössä, jotta skannaus palauttaa tuloksia.

### Ilmoituskäyttöoikeus

Ilmoitukset kertovat sinulle:

- Saapuvista viesteistä kanavilta ja yksityisviesteistä
- Uusista mesh-verkkoon liittyvistä radioista
- Etäradion akun vähäisestä virrasta

> 💡 **Vinkki:** Voit myöhemmin säätää ilmoitusasetuksia Androidin järjestelmäasetuksissa. Sovellus luo jokaiselle ilmoitusluokalle oman ilmoituskanavan (sekä muutamia sisäisiä kanavia, kuten taustapalvelulle), joten voit ottaa ne käyttöön tai mykistää ne yksitellen.

### Kriittisten hälytysten käyttöoikeus

Kriittiset hälytykset ovat korkean prioriteetin ilmoituksia, jotka ohittavat Älä häiritse -tilan — niitä käytetään mesh-verkon hätähälytyksiin ja kiireellisiin viesteihin.

Tämä vaihe ei ole käyttöoikeuspyyntö. Hyväksy/hylkää-valintaikkunaa ei ole: painike avaa Androidin järjestelmäasetusten sivun sovelluksen **Hälytykset**-ilmoituskanavalle, jossa voit itse ottaa käyttöön Älä häiritse -tilan ohituksen. Voit myös **ohittaa** sen ja palata samalle sivulle myöhemmin Androidin ilmoitusasetuksista.

### Käyttöoikeuksien tarkistaminen myöhemmin

**Asetukset → Käyttöoikeudet** näyttää kaikkien käyttöaikaisten käyttöoikeuksien tilan. Siellä käsitellään viisi käyttöoikeutta: **Lähellä olevat laitteet** (Bluetooth), **Sijainti**, **Ilmoitukset**, **Kamera** (kanava- ja yhteystieto-QR-koodien skannaaminen) ja **Paikallinen verkko** (radioiden löytäminen WiFin kautta mDNS:llä) — kahta viimeistä ei koskaan pyydetä käyttöönoton aikana, vaan vasta kun jokin toiminto tarvitsee niitä ensimmäisen kerran. Jos mikään käyttöoikeus ei vaadi huomiota, siinä lukee _Kaikki sallittu_. Kun jokin käyttöoikeus vaatii huomiota, rivillä näkyy niiden määrä, ja Käyttöoikeudet-sivu avautuu automaattisesti. Napauta riviä nähdäksesi koko luettelon milloin tahansa:

| Tila                                                  | Mitä rivin napauttaminen tekee                                                                                       |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **Sallittu**                                          | Avaa järjestelmän sivun, jossa voit tarkistaa tai peruuttaa käyttöoikeuden                                           |
| **Ei kysytty vielä**                                  | Pyytää sitä                                                                                                          |
| **Estetty - napauta salliaksesi**                     | Selittää, mihin käyttöoikeutta tarvitaan, ja pyytää sitä uudelleen, jos hyväksyt                                     |
| **Estetty - napauta avataksesi järjestelmäasetukset** | Android ei enää näytä käyttöoikeusikkunaa, joten tämä avaa sivun, jossa voit ottaa käyttöoikeuden uudelleen käyttöön |
| **Ei tarvita tässä Android-versiossa**                | Ei mitään – tätä käyttöoikeutta ei ole laitteessasi                                                                  |

Tämä koskee erityisesti ilmoituksia. Jos hylkäät ne käyttöönoton aikana, tämä rivi toimii paluutienä: Android lakkaa näyttämästä valintaikkunaa, kun olet hylännyt pyynnön lopullisesti (toinen hylkäys), jolloin tämän rivin tilaksi vaihtuu **Estetty** ja se avaa sen sijaan järjestelmäasetukset. Ilmoituskäyttöoikeutta pyydetään vain Android 13:ssa ja uudemmissa versioissa — vanhemmissa versioissa ilmoitukset ovat oletusarvoisesti käytössä ja niitä hallitaan Androidin omista asetuksista.

## Määrityksen jälkeen

Kun olet myöntänyt käyttöoikeudet, sovellus avaa pääkäyttöliittymän. Ensimmäinen toimenpiteesi pitäisi olla yhteyden muodostaminen Meshtastic-radioon — katso [Yhteydet](connections) yksityiskohtaisia ohjeita varten.

> 💡 **Vinkki:** Jos ohitit käyttöoikeuksia käyttöönottovaiheessa, avaa sovelluksessa **Asetukset → Käyttöoikeudet**. Kaikki käyttöaikaiset käyttöoikeudet näkyvät siellä nykyisessä tilassaan sekä linkkinä niiden hallintaan, mukaan lukien ilmoitukset, joita järjestelmä ei pyydä toista kertaa automaattisesti.

Sovelluksen toiminnot pyytävät käyttöoikeuksia myös tarpeen mukaan. Kun **Yhteydet**-näytössä napautat **Hae** ilman Bluetooth-käyttöoikeutta, sovellus kertoo, mihin käyttöoikeutta tarvitaan, ja tarjoaa mahdollisuuden pyytää sitä. Kun Android ei enää näytä käyttöoikeusikkunaa, sama painike avaa sen sijaan järjestelmän asetussivun eikä jää toimettomaksi.

Uusi Meshtasticissa? [Aloitusopas](https://meshtastic.org/docs/getting-started) meshtastic.org-sivustolla käsittelee laitteiston valintaa, radion alkuasetuksia ja ensimmäisen verkon käyttöönottoa.

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhdistä ensimmäinen radiosi
- [Viestit ja kanavat](messages-and-channels) — lähetä ensimmäinen viestisi
- [Radiot](nodes) — katso, ketkä muut ovat mesh-verkossasi
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — määritä radio ja käyttäjäprofiili
