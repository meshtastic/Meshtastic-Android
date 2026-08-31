---
title: Laiteohjelmiston päivitykset
parent: Käyttöopas
nav_order: 13
last_updated: 2026-08-30
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

1. Avaa yhdistetyn radion asetukset ja valitse **Lisäasetukset → Laiteohjelmiston päivitys**. Kohta näkyy vain OTA-päivitystä tukeville radioille.
2. Sovellus tarkistaa saatavilla olevat firmware-versiot.
3. Saatavilla olevat päivitykset näyttävät versionumeron ja muutoslokin yhteenvedon.

## Päivitysmenetelmät

### OTA (Over-The-Air) Bluetooth-yhteyden kautta

Yleisin päivitystapa Android-käyttäjille:

> ⚠️ **Varoitus:** Laiteohjelmistopäivityksen keskeyttäminen voi estää radion käynnistymisen. Pidä puhelin lähellä ja molemmat laitteet käynnissä, kunnes päivitys on valmis.

1. Varmista, että radiosi on yhdistetty Bluetoothilla.
2. Siirry Firmware-päivitys -näkymään.
3. Valitse haluamasi firmware-versio.
4. Napauta **Päivitä**. An **Update Warning** dialog lists the pre-flight checks — read it, then tap **I know what I'm doing.** to start. This dialog appears for every update method, including Wi-Fi OTA, USB, and a local firmware file.
5. Odota, että päivitys valmistuu — älä katkaise yhteyttä päivityksen aikana.

![Päivitysten tarkistaminen](../../assets/screenshots/firmware_checking.png)

#### Tyhjennä laite päivityksen aikana

Jos sovellus tukee sitä, päivityspainikkeen vieressä näkyy **Tyhjennä laite päivityksen aikana** -valintaruutu. Asetus koskee vain tätä päivitystä eikä sitä muisteta myöhemmin.

| Menetelmä      | Mitä tyhjennys tekee                                                                                                                                     |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / WiFi OTA | Laite palautetaan tehdasasetuksiin, kun päivitys on vahvistettu. Kaikki asetukset ja Bluetooth-pariliitokset poistetaan. |
| USB            | Tyhjentää laitteen flash-muistin kokonaan ja asentaa sen jälkeen valitun laiteohjelmiston alusta alkaen.                                 |

It is not offered for a local firmware file, during a recovery update, on USB devices whose board does not support the erase step, or over USB in the desktop app — the USB erase runs the Android-only maintenance sequence below. Tämän jälkeen laite on määritettävä uudelleen ja pariliitos muodostettava uudelleen.

### OTA WiFi:n kautta (verkkoon yhdistetty ESP32)

Kun ESP32-radio on yhdistetty verkkoon Bluetoothin sijaan, sovellus tarjoaa **WiFi OTA** -vaihtoehdon, joka siirtää saman päivityksen TCP-yhteyden kautta.

1. Muodosta yhteys radioon verkon kautta (katso [Yhteydet](connections)).
2. Avaa Laiteohjelmiston päivitys -näkymä ja valitse versio.
3. Napauta **Päivitä**. Pidä radio ja puhelin samassa verkossa koko siirron ajan.

WiFi OTA käyttää ESP32:n `update.bin`-tiedostoa USB-päivityksen `.uf2`-tiedoston sijaan. Sovellus valitsee oikean tiedoston automaattisesti.

### USB-päivitys sovelluksesta

Kun radio on yhdistetty **USB:n tai sarjayhteyden** kautta (bluetoothin sijaan), **laiteohjelmiston päivitys** -näkymässä on käytettävissä **USB-tiedostonsiirto**. Sovellus käynnistää laitteen uudelleen DFU-tilaan ja pyytää sitten tallentamaan `.uf2`-tiedoston laitteen DFU-asemaan järjestelmän tiedostonvalitsimen avulla. Tämä vaihtoehto näkyy vain USB tai sarjayhteydellä — sitä ei voi käyttää bluetoothin kautta.

> ℹ️ **Huomautus:** Valmistajan nRF-käynnistyslatain, joka toimitetaan `.zip`-tiedostona (esim. RAK WisBlock RAK4631), on asennettava sarjaliitäntäisen DFU-työkalun, kuten `adafruit-nrfutil`-ohjelman avulla — `.zip`-tiedoston kopioiminen asemalle ei toimi. Päivitysmuotoinen .uf2-käynnistyslatain voidaan asentaa kopioimalla se asemalle. Näin myös tämän sovelluksen käynnistyslataimen päivitystoiminto toimii. Sovellus näyttää huomautuksen, kun käytettävissä on vain sarjaliitäntäinen menetelmä.

### Tyhjennys tehdasasetuksiin ja käynnistyslataimen päivitys

On a **USB/serial** connection, nRF52 and RP2040 devices can be wiped as part of an update — that is the **Erase device during update** opt-in described earlier on this page. nRF52 devices additionally offer **Upgrade bootloader** where an upgraded bootloader is published for the board; RP2040 devices run no Adafruit bootloader, so they never see it.

Both are Android-only. They depend on Android's update-drive checks, so the desktop app does not show them — use the [Web Flasher](https://flasher.meshtastic.org) there instead.

Select a firmware version before either one: the app hides both until a release is chosen, because the firmware is reinstalled after the device is wiped or the new bootloader is written.

Both a USB erase and a bootloader upgrade write two files in turn, so you are asked to select the device's update drive twice: once for the erase or bootloader image, then again for the firmware.

Sovellus lukee valitsemaltasi asemalta tiedoston `INFO_UF2.TXT` varmistaakseen, että kyseessä on todella laitteen päivitysasema, sekä tunnistaakseen laitteen ennen kuin mitään kirjoitetaan. Jos sovellus ei pysty tunnistamaan laitteesi Bluetooth-pinoa, se kieltäytyy tyhjentämästä laitetta ja ohjaa sinut sen sijaan [Web Flasheriin](https://flasher.meshtastic.org). Web Flasherissa väärän Bluetooth-pinon valitseminen voi johtaa siihen, että radion palauttaminen onnistuu vain laitteisto-ohjelmoijan avulla.

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
- [ ] Päivitä myös Meshtastic-sovellus joko ennen laiteohjelmiston päivitystä tai sen yhteydessä yhteensopivuuden varmistamiseksi

## Päivityksen jälkeen

Kun laiteohjelmisto on kirjoitettu, sovellus varmistaa päivityksen ja odottaa laitteen palaavan takaisin verkkoon:

![Päivityksen varmistus ja laitteen uudelleenyhdistymisen odottaminen](../../assets/screenshots/firmware_verifying.png)

Kun päivitys onnistuu:

- Radio käynnistyy uudelleen automaattisesti
- Bluetooth-yhteys muodostetaan uudelleen
- Varmista, että asetuksesi ovat säilyneet
- Vahvista uusi versio **Asennettu tällä hetkellä** -kohdasta **Laiteohjelmiston päivitys** -näkymässä — sama tieto näkyy myös radion tiedoissa ja **Yhteydet**-näkymässä

![Firmware-päivitys onnistui](../../assets/screenshots/firmware_success.png)

## Vianetsintä

### Päivitys jumissa

Jos päivitys näyttää jumiutuneen:

- Anna sille hetki aikaa. Kun tiedosto on kirjoitettu, sovellus odottaa enintään **60 sekuntia**, että radio käynnistyy uudelleen ja ilmoittaa uuden versionsa. Lyhyt odotus vahvistusvaiheessa on siis normaalia.
- Jos radio on tämän jälkeen edelleen jumissa, käynnistä se uudelleen katkaisemalla virta.
- Yritä päivitystä uudelleen.

The message **Verification timed out. Device did not reconnect in time.** means the image was written but the radio did not come back within that window — power-cycle it and check the version under **Currently Installed** before re-running the update.

![Laiteohjelmiston päivitysvirhe](../../assets/screenshots/firmware_error.png)

### Radio ei käynnisty päivityksen jälkeen

If the app told you the Bluetooth update could not be finished, follow the instruction it gave: connect the radio to a computer over USB and re-flash it with the vendor's serial DFU tool, such as `adafruit-nrfutil`. A stock nRF bootloader cannot reliably complete an interrupted over-the-air update.

Otherwise, if your radio fails to boot:

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

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhdistetään uudelleen laiteohjelmiston päivityksen jälkeen
- [Laiteohjelmiston päivitysopas](https://meshtastic.org/docs/getting-started/flashing-firmware) — täydellinen firmware-päivityksen ohjeistus meshtastic.org -sivustolla
- [Tuetut laitteet](https://meshtastic.org/docs/hardware/devices) — tarkista firmware-yhteensopivuus laitekohtaisesti
- [UKK](https://meshtastic.org/docs/faq/) – usein kysytyt kysymykset meshtastic.org -sivustolla
