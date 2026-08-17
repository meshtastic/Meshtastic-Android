---
title: Püsivara värskendus
parent: User Guide
nav_order: 13
last_updated: 2026-07-07
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

1. Ava ühendatud raadio konfiguratsioon ja puuduta jaotises **Täpsemalt** valikut **Püsivara värskendus**. The entry appears only for OTA-capable devices.
2. Rakendus kontrollib saadaolevaid püsivara versioone.
3. Saadaval olevad värskendused näitavad versiooninumbrit ja muudatuste logi kokkuvõtet.

## Värskendamise meetod

### OTA (Over-The-Air) sinihamba abil

Kõige levinum värskendamisviis Androidi kasutajate seas:

1. Veendu, et raadio on sinihamba ​​kaudu ühendatud.
2. Mine püsivara värskenduse lehele.
3. Vali soovitud püsivara versioon.
4. OTA alustamiseks puuduta nuppu **Uuenda**.
5. Oota, kuni värskendus on lõppenud – **ära katkesta ühendust** värskenduse ajal.

![Püsivara kontrollib värskendusi](../../assets/screenshots/firmware_checking.png)

> ⚠️ **Hoiatus:** Püsivara värskenduse katkestamine võib sinu seadme rikkuda. Veendu, et raadiol oleks piisav aku (soovitatav on >50%) ja säilita kogu protsessi vältel sinihamba ​​​​lähedus.

[Püsivara hoiatus](../../assets/screenshots/firmware_disclaimer.png)

### Rakendusesisene USB värskendus

Kui raadio on ühendatud **USB/jadaühenduse** (mitte sinihamba) kaudu, pakub püsivara värskendamise ekraan **USB failiedastust**. Rakendus taaskäivitab seadme DFU-režiimis ja seejärel palub süsteemifailide valija abil salvestada `.uf2`-fail seadme DFU-draivi. See valik kuvatakse ainult USB/jadaühenduse korral – see pole sinihamba ​​kaudu saadaval.

> ℹ️ **nRF bootloader note:** A vendor bootloader supplied as a `.zip` (e.g. RAK WisBlock RAK4631) has to be flashed with a serial DFU tool such as `adafruit-nrfutil` — copying that `.zip` to the drive won't work. A bootloader supplied as an `update-....uf2` **can** be installed by copying it to the drive; that is how the app's own bootloader upgrade works. The app surfaces a hint when the serial-only route applies.

### Factory Erase and Bootloader Upgrade

On a **USB/serial** connection, nRF52 and RP2040 devices also offer **Erase and reinstall** and, where an upgraded bootloader is published for the board, **Upgrade bootloader**.

Erasing wipes everything on the device — channels, keys and all settings — and there is no backup, so the app asks for confirmation first. Both operations write two files in turn, so you will be asked to select the device's update drive twice: once for the erase or bootloader image, then again for the firmware.

The app reads `INFO_UF2.TXT` from the drive you select to confirm it really is the device's update drive and to identify the board before writing anything. If it can't confirm which Bluetooth stack your device uses it refuses to erase and points you at the [Web Flasher](https://flasher.meshtastic.org) instead — picking wrong there can leave the device needing a hardware programmer to recover.

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

## Eelvärskendus

Pärast püsivara kirjutamist kontrollib rakendus värskendust ja ootab, kuni seade taas võrku lülitub:

![Uuenduse kontrollimine ja seadme taasühendumise ootamine](../../assets/screenshots/firmware_verifying.png)

Kui värskendus õnnestub:

- Raadio taaskäivitub automaatselt
- Sinihamba ühendus taastatakse
- Veendu, et seaded on puutumatud
- Kontrolli uut versiooni püsivara värskenduse ekraanil jaotises **Praegu paigaldatud** – see kuvatakse ka sõlme üksikasjade lehel ja ühenduste ekraanil

![Püsivara värskendus õnnestus](/assets/screenshots/firmware_success.png)

## Troubleshooting

### Värskendus on ummikus

Kui värskendus näib olevat hangunud:

- Wait at least 5 minutes before intervening
- If truly stuck, power-cycle the radio
- Proovi uuesti värskendada

![Püsivara uuendamise viga](../../assets/screenshots/firmware_error.png)

### Seade ei käivitu pärast värskendamist

If your device fails to boot:

1. Try connecting via USB to a computer
2. Kasuta veebi püsivarauuendust taaste/DFU režiimis
3. Püsivarauuenda teadaolevalt toimiva püsivara versiooniga
4. Seadmepõhiste taastamissammude kohta vaata Meshtastic Discordist

### Compatibility Warnings

Rakendus võib kuvada hoiatusi järgmistel juhtudel:

- Ühendatud raadio püsivara versioon on madalam kui minimaalselt toetatud versioon
- Rakenduse ja püsivara versioonide mittevastavus
- Deprecated features need migration

> ⚠️ **Tähtis:** Ühilduvuse tagamiseks värskenda Meshtastic rakendust enne püsivara värskendust.

## Related Topics

- [Ühendused](connections) — ühenduse loomine pärast püsivara värskendamist
- [Püsivara uuendamise juhend](https://meshtastic.org/docs/getting-started/flashing-firmware) — täielik püsivara uuendamise juhend meshtastic.org lehel
- [Toetatud seadmed](https://meshtastic.org/docs/hardware/devices) — ühilduvate raadiote täielik loetelu on leitav aadressilt meshtastic.org
- [KKK](https://meshtastic.org/docs/faq/) — meshtastic.org sageli esitatavad küsimused

---

