---
title: Laiteohjelmiston päivitykset
parent: Käyttöopas
nav_order: 13
last_updated: 2026-08-27
description: Päivitä radiosi laiteohjelmisto bluetoothin tai USB:n kautta — OTA-päivitys, versiokanavat, tarkistukset ennen päivitystä ja palautus.
aliases:
  - firmware
  - päivitä
  - ota
  - ohjelmoi
---

# Laiteohjelmiston päivitykset

Pidä Meshtastic-radiosi ajan tasalla uusimmalla firmwarella, jossa on uusia ominaisuuksia, virhekorjauksia ja tietoturvaparannuksia.

## Päivitysten tarkistaminen

1. Avaa yhdistetyn radion asetukset ja valitse **Lisäasetukset → Laiteohjelmiston päivitys**. Tämä vaihtoehto näkyy vain laitteilla, jotka tukevat OTA-päivityksiä.
2. Sovellus tarkistaa saatavilla olevat firmware-versiot.
3. Saatavilla olevat päivitykset näyttävät versionumeron ja muutoslokin yhteenvedon.

## Päivitysmenetelmät

### OTA (Over-The-Air) Bluetooth-yhteyden kautta

Yleisin päivitystapa Android-käyttäjille:

1. Varmista, että radiosi on yhdistetty Bluetoothilla.
2. Siirry Firmware-päivitys -näkymään.
3. Valitse haluamasi firmware-versio.
4. Napauta **Päivitä** aloittaaksesi OTA-päivityksen.
5. Odota, että päivitys valmistuu — älä katkaise yhteyttä päivityksen aikana.

![Päivitysten tarkistaminen](../../assets/screenshots/firmware_checking.png)

> ⚠️ Varoitus: firmware-päivityksen keskeyttäminen voi rikkoa laitteen. Pidä radio ladattuna ja Bluetooth-yhteyden kantaman sisällä koko päivityksen ajan. Sovellus estää päivityksen vain, jos akun varaustaso on alle **10 %**. Suositeltava vähimmäisvaraustaso on 50 %, mutta sitä ei vaadita.

#### Tyhjennä laite päivityksen aikana

Jos sovellus tukee sitä, päivityspainikkeen vieressä näkyy **Tyhjennä laite päivityksen aikana** -valintaruutu. Asetus koskee vain tätä päivitystä eikä sitä muisteta myöhemmin.

| Menetelmä       | Mitä tyhjennys tekee                                                                                                                                     |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / Wi-Fi OTA | Laite palautetaan tehdasasetuksiin, kun päivitys on vahvistettu. Kaikki asetukset ja Bluetooth-pariliitokset poistetaan. |
| USB             | Tyhjentää laitteen flash-muistin kokonaan ja asentaa sen jälkeen valitun laiteohjelmiston alusta alkaen.                                 |

Tätä ei tarjota paikallisen laiteohjelmistotiedoston yhteydessä, palautuspäivityksessä eikä USB-laitteille, joiden piirilevy ei tue tyhjennysvaihetta. Tämän jälkeen laite on määritettävä uudelleen ja pariliitos muodostettava uudelleen.

### OTA Wi-Fi:n kautta (verkkoon yhdistetty ESP32)

Kun ESP32-radio on yhdistetty verkkoon Bluetoothin sijaan, sovellus tarjoaa **WiFi OTA** -vaihtoehdon, joka siirtää saman päivityksen TCP-yhteyden kautta.

1. Muodosta yhteys radioon verkon kautta (katso [Yhteydet](connections)).
2. Avaa Laiteohjelmiston päivitys -näkymä ja valitse versio.
3. Napauta **Päivitä**. Pidä radio ja puhelin samassa verkossa koko siirron ajan.

WiFi OTA käyttää ESP32:n `update.bin`-tiedostoa USB-päivityksen `.uf2`-tiedoston sijaan. Sovellus valitsee oikean tiedoston automaattisesti.

![Laiteohjelmiston vastuuvapauslauseke](../../assets/screenshots/firmware_disclaimer.png)

### USB-päivitys sovelluksesta

Kun radio on yhdistetty **USB:n tai sarjayhteyden** kautta (bluetoothin sijaan), **laiteohjelmiston päivitys** -näkymässä on käytettävissä **USB-tiedostonsiirto**. Sovellus käynnistää laitteen uudelleen DFU-tilaan ja pyytää sitten tallentamaan `.uf2`-tiedoston laitteen DFU-asemaan järjestelmän tiedostonvalitsimen avulla. Tämä vaihtoehto näkyy vain USB tai sarjayhteydellä — sitä ei voi käyttää bluetoothin kautta.

> ℹ️ \*\* nFR käynnistyslataimen huomautus:\*\* Valmistajan toimittama .zip-muotoinen käynnistyslatain (esimerkiksi RAK WisBlock RAK4631) on asennettava sarjaliitäntäisen DFU-työkalun, kuten adafruit-nrfutilin, avulla. Pelkkä .zip-tiedoston kopioiminen asemalle ei toimi. Päivitysmuotoinen .uf2-käynnistyslatain voidaan asentaa kopioimalla se asemalle. Näin myös tämän sovelluksen käynnistyslataimen päivitystoiminto toimii. Sovellus näyttää huomautuksen, kun käytettävissä on vain sarjaliitäntäinen menetelmä.

### Tyhjennys tehdasasetuksiin ja käynnistyslataimen päivitys

**USB-/sarjaliitäntäyhteydellä** NRF52- ja RP2040-laitteet tarjoavat myös vaihtoehdot **Tyhjennys tehdasasetuksiin ja uudelleenasennus** sekä, jos laitteelle on julkaistu päivitetty käynnistyslatain, **Päivitä käynnistyslatain**.

Tyhjennys tehdasasetuksiin poistaa laitteesta kaiken – kanavat, avaimet ja kaikki asetukset – eikä varmuuskopiota ole, joten sovellus pyytää ensin vahvistuksen. Molemmat toiminnot kirjoittavat vuorollaan kaksi tiedostoa, joten sinua pyydetään valitsemaan laitteen päivitysasema kahdesti: ensin tyhjennystiedostoa tai käynnistyslatainkuvaa varten ja sen jälkeen laiteohjelmistoa varten.

Sovellus lukee valitsemaltasi asemalta tiedoston `INFO_UF2.TXT` varmistaakseen, että kyseessä on todella laitteen päivitysasema, sekä tunnistaakseen laitteen ennen kuin mitään kirjoitetaan. Jos sovellus ei pysty varmistamaan, mitä Bluetooth-pinoa laitteesi käyttää, se kieltäytyy suorittamasta tyhjennystä ja ohjaa sinut sen sijaan **Web Flasheriin** (https://flasher.meshtastic.org). Väärän vaihtoehdon valitseminen siellä voi johtaa siihen, että laitteen palauttaminen edellyttää erillistä laiteohjelmointilaitetta.

### Muut päivitysvaihtoehdot

Käytä näitä palautukseen tai silloin, kun OTA- tai sovelluksen USB-päivitys ei ole käytettävissä:

- Käytä [Meshtastic Web Flasheriä](https://flasher.meshtastic.org)
- Tai [Meshtastic CLI -työkalua](https://meshtastic.org/docs/getting-started/flashing-firmware) työpöytäympäristössä

## Versiokanavat

| Kanava               | Kuvaus                                                                   |
| -------------------- | ------------------------------------------------------------------------ |
| Vakaa                | Suositeltu useimmille käyttäjille: testatut julkaisut    |
| Alpha                | Esijulkaisut voivat sisältää virheitä                                    |
| Paikallinen tiedosto | Asenna itse valitsemasi laiteohjelmistotiedosto ladatun julkaisun sijaan |

## Päivitystä edeltävä tarkistuslista

Ennen päivityksen aloitamista:

- [ ] Akku > 50 %
- [ ] Vakaa Bluetooth-yhteys
- [ ] Kirjaa ylös nykyiset asetuksesi (ne voivat nollautua suurissa versiomuutoksissa)
- [ ] Tarkista julkaisumuistiinpanot mahdollista yhteensopivuutta rikkovien muutosten varalta

## Päivityksen jälkeen

Kun laiteohjelmisto on kirjoitettu, sovellus varmistaa päivityksen ja odottaa laitteen palaavan takaisin verkkoon:

![Päivityksen varmistus ja laitteen uudelleenyhdistymisen odottaminen](../../assets/screenshots/firmware_verifying.png)

Kun päivitys onnistuu:

- Radio käynnistyy uudelleen automaattisesti
- Bluetooth-yhteys muodostuu uudelleen
- Varmista, että asetuksesi ovat säilyneet
- Vahvista uusi versio **Asennettu tällä hetkellä** -kohdasta **Laiteohjelmiston päivitys** -näkymässä — sama tieto näkyy myös radion tiedoissa ja **Yhteydet**-näkymässä

![Firmware-päivitys onnistui](../../assets/screenshots/firmware_success.png)

## Vianetsintä

### Päivitys jumissa

Jos päivitys näyttää jumiutuneen:

- Anna sille hetki aikaa. Kun tiedosto on kirjoitettu, sovellus odottaa enintään **60 sekuntia**, että radio käynnistyy uudelleen ja ilmoittaa uuden versionsa. Lyhyt odotus vahvistusvaiheessa on siis normaalia.
- Jos radio on tämän jälkeen edelleen jumissa, käynnistä se uudelleen katkaisemalla virta.
- Yritä päivitystä uudelleen.

![Laiteohjelmiston päivitysvirhe](../../assets/screenshots/firmware_error.png)

### Laite ei käynnisty päivityksen jälkeen

Jos laite ei käynnisty:

1. Kokeile yhdistää USB:llä tietokoneeseen
2. Käytä web-flasheria palautus tai DFU-tilassa
3. Asenna tunnetusti toimiva firmware-versio
4. Tarkista Meshtastic Discordista laitekohtaiset palautusohjeet

### Yhteensopivuutta koskevat varoitukset

Yhteyden muodostamisen yhteydessä sovellus vertaa radion laiteohjelmistoversiota kahteen rajaan ja toimii niiden perusteella eri tavoin:

| Laiteohjelmistoversio                                                                                         | Näet seuraavan                                                | Mitä tapahtuu                                                                                                                |
| ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Alle **2.3.15**                                                               | **Laiteohjelmiston päivitys vaaditaan.**      | Sovellus katkaisee yhteyden radioon. Se ei toimi näin vanhan laiteohjelmiston kanssa.        |
| **2.3.15** tai uudempi, mutta alle **2.5.14** | **Laiteohjelmiston päivitystä suositellaan.** | Vain suositus – voit ohittaa sen ja jatkaa. Valintaikkunassa kerrotaan uusin vakaa julkaisu. |
| **2.5.14** tai uudempi                                                        | Ei mitään                                                     | —                                                                                                                            |

Jos sovellus ei pysty tulkitsemaan versionumeroa, se ohitetaan sen sijaan, että sitä pidettäisiin liian vanhana. Näin tilapäinen lukuvirhe ei katkaise yhteyttä toimivaan radioon.

> ⚠️ **Tärkeää:** Päivitä Meshtastic-sovellus aina ennen firmware-päivitystä tai sen yhteydessä varmistaaksesi yhteensopivuuden.

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhdistetään uudelleen laiteohjelmiston päivityksen jälkeen
- [Laiteohjelmiston päivitysopas](https://meshtastic.org/docs/getting-started/flashing-firmware) — täydellinen firmware-päivityksen ohjeistus meshtastic.org -sivustolla
- [Tuetut laitteet](https://meshtastic.org/docs/hardware/devices) — tarkista firmware-yhteensopivuus laitekohtaisesti
- [UKK](https://meshtastic.org/docs/faq/) – usein kysytyt kysymykset meshtastic.org -sivustolla

---

