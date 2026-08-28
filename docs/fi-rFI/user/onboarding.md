---
title: Aloittaminen
parent: Käyttöopas
nav_order: 1
last_updated: 2026-08-27
description: Ensimmäisen käynnistyksen määritys — käyttöoikeudet, käyttöönottoprosessi ja seuraavat vaiheet radion yhdistämisen jälkeen.
aliases:
  - ensimmäinen käynnistys
  - asetukset
  - esittely
---

# Aloittaminen

Tervetuloa Meshtasticiin! Tämä opas opastaa sinut Meshtastic Android -sovelluksen alkuasetusten läpi.

## Ensimmäinen käynnistys

Kun avaat sovelluksen ensimmäistä kertaa, sinut ohjataan käyttöönottoprosessin läpi, joka auttaa määrittämään tarvittavat käyttöoikeudet ja asetukset. Complete each step in order or skip it — nothing here is a one-time offer. Kaikki käyttöoikeudet voidaan tarkistaa ja myöntää myöhemmin sovelluksen **Asetukset → Käyttöoikeudet** -kohdassa.

### Aloitusnäkymä

The welcome screen introduces Meshtastic with three feature rows:

|                               |                                                                                                                                        |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Stay Connected Anywhere**   | Viestitä ilman verkkoyhteyttä ystäviesi ja yhteisösi kanssa ilman matkapuhelinverkkoa.                                 |
| **Create Your Own Networks**  | Luo vaivattomasti yksityisiä meshtastic verkkoja turvalliseen ja luotettavaan viestintään kaukana asutuista paikoista. |
| **Track and Share Locations** | Jaa sijaintisi reaaliaikaisesti ja varmista ryhmäsi yhteistoiminta GPS-toimintojen avulla.                             |

Tap **Get started** to proceed through the setup flow.

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

Grant **"While using the app"**. The app does not request background location — `ACCESS_BACKGROUND_LOCATION` is not in its manifest — so Android will not offer an "Always" option, and position updates happen while the app is in the foreground or running its foreground service.

Sijaintikäyttöoikeuden hylkääminen ei estä sovelluksen muuta toimintaa. Android 12:ssa ja uudemmissa versioissa Bluetooth toimii edelleen, ja vain kartta, sijainnin näyttäminen ja sijainnin jakaminen poistuvat käytöstä. Android 11:ssä ja sitä vanhemmissa versioissa myös Bluetooth-laitteiden haku lakkaa toimimasta, koska Android edellyttää siihen sijaintikäyttöoikeutta.

### Ilmoituskäyttöoikeus

Ilmoitukset kertovat sinulle:

- Saapuvista viesteistä kanavilta ja yksityisviesteistä
- Uusista mesh-verkkoon liittyvistä radioista
- Etäradion akun vähäisestä virrasta

> 💡 **Vinkki:** Voit myöhemmin säätää ilmoitusasetuksia Androidin järjestelmäasetuksissa. Sovellus luo jokaiselle ilmoitusluokalle oman ilmoituskanavan (sekä muutamia sisäisiä kanavia, kuten taustapalvelulle), joten voit ottaa ne käyttöön tai mykistää ne yksitellen.

### Kriittisten hälytysten käyttöoikeus

Critical alerts are high-priority notifications that break through Do Not Disturb — for emergency mesh alerts and urgent messages.

This step is not a runtime permission prompt. There is no grant/deny dialog: the button opens the Android system settings page for the app's **Alerts** notification channel, where you turn the breakthrough behaviour on yourself. You can **skip** it, and reach the same page later from Android notification settings.

### Käyttöoikeuksien tarkistaminen myöhemmin

**Asetukset → Käyttöoikeudet** näyttää kaikkien käyttöaikaisten käyttöoikeuksien tilan. It covers five: **Nearby devices** (Bluetooth), **Location**, **Notifications**, **Camera** (scanning channel and contact QR codes) and **Local network** (finding radios over Wi-Fi by mDNS) — the last two are never asked for during setup, only when a feature first needs them. Kun mikään käyttöoikeus ei vaadi huomiotasi, siinä lukee _Kaikki sallittu_. Jos jokin käyttöoikeus vaatii huomiota, siinä näytetään niiden määrä, ja näkymä avautuu automaattisesti. Napauta riviä nähdäksesi koko luettelon milloin tahansa:

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

Uusi Meshtasticissa? [Aloitusopas](https://meshtastic.org/docs/getting-started) meshtastic.org-sivustolla käsittelee laitteiston valintaa, radion alkuasetuksia ja ensimmäisen verkon käyttöönottoa.

## Aiheeseen liittyvät aiheet

- [Connections](connections) — pair your first radio
- [Viestit ja kanavat](messages-and-channels) — lähetä ensimmäinen viestisi
- [Nodes](nodes) — see who else is on your mesh
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja
- [Settings — Radio & User](settings-radio-user) — configure your radio and user profile

---
