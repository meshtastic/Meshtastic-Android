---
title: Püsivara värskendus
parent: Kasutusjuhend
nav_order: 13
last_updated: 2026-08-29
description: Raadio püsivara uuendamine sinihamba ​​või USB kaudu – OTA protsess, versioonikanalid, lennueelsed kontrollid ja taastamine.
aliases:
  - püsivara
  - värskendus
  - ota
  - püsivarauuendus
---

# Püsivara värskendus

Hoia oma Meshtastic raadio ajakohasena uusima püsivaraga, et saada uusi funktsioone, veaparandusi ja turvalisuse täiustusi.

## Kontrollin värskendust

1. Ava ühendatud raadio konfiguratsioon ja puuduta jaotises **Täpsemalt** valikut **Püsivara värskendus**. The entry appears only for OTA-capable radios.
2. Rakendus kontrollib saadaolevaid püsivara versioone.
3. Saadaval olevad värskendused näitavad versiooninumbrit ja muudatuste logi kokkuvõtet.

## Värskendamise meetod

### OTA (Over-The-Air) sinihamba abil

Kõige levinum värskendamisviis Androidi kasutajate seas:

> ⚠️ **Warning:** Interrupting a firmware update can leave the radio unable to boot. Keep the phone nearby and both devices powered until the update completes.

1. Veendu, et raadio on sinihamba ​​kaudu ühendatud.
2. Mine püsivara värskenduse lehele.
3. Vali soovitud püsivara versioon.
4. OTA alustamiseks puuduta nuppu **Uuenda**.
5. Oota, kuni värskendus on lõppenud – **ära katkesta ühendust** värskenduse ajal.

![Püsivara kontrollib värskendusi](../../assets/screenshots/firmware_checking.png)

#### Tühjendada seade uuendamise käigus

Where the app offers it, an **Erase device during update** checkbox appears next to the update button. It is a per-update opt-in and is never remembered.

| Method          | What erasing does                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / Wi-Fi OTA | Factory-resets the device once the update is verified. All settings and Bluetooth pairing are removed. |
| USB             | Puhastab seadme välkmälu täielikult ja paigaldab seejärel valitud püsivara nullist.                                    |

It is not offered for a local firmware file, during a recovery update, or on USB devices whose board does not support the erase step. Afterwards the device needs setting up — and pairing — again.

### OTA via Wi-Fi (network-connected ESP32)

When an ESP32 radio is connected over the network rather than Bluetooth, the app offers **Wi-Fi OTA**, which pushes the same update over TCP:

1. Connect to the radio over the network (see [Connections](connections)).
2. Open the Firmware Update screen and pick a version.
3. Tap **Update**. Keep the radio and phone on the same network for the whole transfer.

Wi-Fi OTA takes the ESP32 `-update.bin` image rather than the `.uf2` a USB update uses; the app selects the right artifact for you.

[Püsivara hoiatus](../../assets/screenshots/firmware_disclaimer.png)

### Rakendusesisene USB värskendus

Kui raadio on ühendatud **USB/jadaühenduse** (mitte sinihamba) kaudu, pakub püsivara värskendamise ekraan **USB failiedastust**. Rakendus taaskäivitab seadme DFU-režiimis ja seejärel palub süsteemifailide valija abil salvestada `.uf2`-fail seadme DFU kettale. See valik kuvatakse ainult USB/jadaühenduse korral – see pole sinihamba ​​kaudu saadaval.

> ℹ️ **Note:** A vendor nRF bootloader supplied as a `.zip` (e.g. RAK WisBlock RAK4631) has to be flashed with a serial DFU tool such as `adafruit-nrfutil` — copying that `.zip` to the drive won't work. Failina pakutavat alglaadur `update-....uf2` **saab** installida selle kettale kopeerimise teel; nii töötabki rakenduse enda alglaaduri uuendamine. The app surfaces a hint when the serial-only route applies.

### Factory Erase and Bootloader Upgrade

**USB/jadapordi** ühenduse korral pakuvad nRF52 ja RP2040 seadmed ka **Kustuta ja installi uuesti** ning kui plaadile on avaldatud uuendatud alglaadur, siis **Alguslaaduri uuendamine**.

Kustutamine puhastab seadmest kõik – kanalid, klahvid ja kõik seaded – ning varukoopiat ei tehta, seega küsib rakendus kõigepealt kinnitust. Mõlemad toimingud kirjutavad kordamööda kaks faili, seega palutakse teil seadme uuendusdraiv valida kaks korda: üks kord kustutus- või alglaaduri kujutise jaoks ja seejärel uuesti püsivara jaoks.

Rakendus loeb valitud kettalt faili `INFO_UF2.TXT`, et veenduda, kas see on tõepoolest seadme uuendusketas ja enne millegi kirjutamist plaat tuvastada. If it can't confirm which Bluetooth stack your device uses, it refuses to erase and points you at the [Web Flasher](https://flasher.meshtastic.org) instead. In the Web Flasher, choosing the wrong Bluetooth stack can leave the radio recoverable only with a hardware programmer.

### Muud püsivarauuenduse valikud

For recovery or when neither OTA nor in-app USB is available:

- Kasuta [Meshtastic Web Flasherit](https://flasher.meshtastic.org)
- Või arvutil [Meshtastic CLI tööriist](https://meshtastic.org/docs/getting-started/flashing-firmware)

## Versioonikanalid

| Kanal     | Kirjeldus                                                               |
| --------- | ----------------------------------------------------------------------- |
| Stabiilne | Soovitatav enamusele kasutajatele; testitud versioonid                  |
| Alfa      | Eelvaateversioonid; võivad sisaldada vigu                               |
| Lokaalne  | Püsivarauuenda ise valitud püsivara failga, allalaetud versiooni asemel |

## Eelvärskenduse kontrollnimekiri

Enne uuendamist:

- [ ] Aku > 50%
- [ ] Stabiilne sinihamba ühendus
- [ ] Pane tähele oma praeguseid seadeid (need võivad oluliste versioonimuudatuste korral lähtestuda)
- [ ] Check the release notes for breaking changes
- [ ] Update the Meshtastic app itself, before or alongside firmware updates, to ensure compatibility

## Eelvärskendus

Pärast püsivara kirjutamist kontrollib rakendus värskendust ja ootab, kuni seade taas võrku lülitub:

![Uuenduse kontrollimine ja seadme taasühendumise ootamine](../../assets/screenshots/firmware_verifying.png)

Kui värskendus õnnestub:

- The radio reboots automatically
- The Bluetooth connection re-establishes
- Veendu, et seaded on puutumatud
- Kontrolli uut versiooni püsivara värskenduse ekraanil jaotises **Praegu paigaldatud** – see kuvatakse ka sõlme üksikasjade lehel ja ühenduste ekraanil

![Püsivara värskendus õnnestus](/assets/screenshots/firmware_success.png)

## Veaotsing

### Värskendus on ummikus

Kui värskendus näib olevat hangunud:

- Give it a minute. After writing the image the app waits up to **60 seconds** for the radio to come back and report its new version, so a pause at the verify step is expected.
- If it is still stuck after that, power-cycle the radio.
- Attempt the update again.

![Püsivara uuendamise viga](../../assets/screenshots/firmware_error.png)

### Radio Won't Boot After Update

If your radio fails to boot:

1. Try connecting via USB to a computer
2. Kasuta veebi püsivarauuendust taaste/DFU režiimis
3. Püsivarauuenda teadaolevalt toimiva püsivara versiooniga
4. Seadmepõhiste taastamissammude kohta vaata Meshtastic Discordist

### Compatibility Warnings

On connecting, the app compares the radio's firmware against two thresholds and reacts differently to each:

| Püsivara versioon                                                                                               | What you see                                     | What happens                                                                                                         |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Below **2.3.15**                                                                | **Firmware update required.**    | The app disconnects from the radio. It does not operate against firmware this old.   |
| **2.3.15** up to, but not including, **2.5.14** | **Firmware Update Recommended.** | Advisory only — dismiss it and carry on. The dialog names the latest stable release. |
| **2.5.14** or newer                                                             | Nothing                                          | —                                                                                                                    |

A version string the app cannot parse is ignored rather than treated as too old, so a transient read never disconnects a working radio.

## Seotud teemad

- [Ühendused](connections) — ühenduse loomine pärast püsivara värskendamist
- [Püsivara uuendamise juhend](https://meshtastic.org/docs/getting-started/flashing-firmware) — täielik püsivara uuendamise juhend meshtastic.org lehel
- [Toetatud seadmed](https://meshtastic.org/docs/hardware/devices) — ühilduvate raadiote täielik loetelu on leitav aadressilt meshtastic.org
- [KKK](https://meshtastic.org/docs/faq/) — meshtastic.org sageli esitatavad küsimused
