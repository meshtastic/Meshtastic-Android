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
  - flash
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

Kui raadio on ühendatud **USB/jadaühenduse** (mitte sinihamba) kaudu, pakub püsivara värskendamise ekraan **USB failiedastust**. The app reboots the device into DFU mode, then prompts you to save the `.uf2` file to the device's DFU drive using the system file picker. See valik kuvatakse ainult USB/jadaühenduse korral – see pole sinihamba ​​kaudu saadaval.

> ℹ️ **nRF alglaaduri märkus:** Mõned seadmed (nt RAK WisBlock RAK4631) vajavad alglaaduri vilkumist tootja jadaühenduse DFU tööriistaga (näiteks `adafruit-nrfutil`) – ainuüksi `.uf2` kopeerimine ei värskenda alglaadurit. The app surfaces a hint when this applies.

### Other Flashing Options

For recovery or when neither OTA nor in-app USB is available:

- Kasuta [Meshtastic Web Flasherit](https://flasher.meshtastic.org)
- Või arvutil [Meshtastic CLI tööriist](https://meshtastic.org/docs/getting-started/flashing-firmware)

## Versioonikanalid

| Kanal     | Kirjeldus                                                                  |
| --------- | -------------------------------------------------------------------------- |
| Stabiilne | Recommended for most users; tested releases                                |
| Alfa      | Preview releases; may contain bugs                                         |
| Lokaalne  | Flash a firmware file you select yourself, instead of a downloaded release |

## Eelvärskenduse kontrollnimekiri

Enne uuendamist:

- [ ] Aku > 50%
- [ ] Stabiilne sinihamba ühendus
- [ ] Note your current settings (they may reset on major version changes)
- [ ] Check the release notes for breaking changes

## Eelvärskendus

Pärast püsivara kirjutamist kontrollib rakendus värskendust ja ootab, kuni seade taas võrku lülitub:

![Uuenduse kontrollimine ja seadme taasühendumise ootamine](../../assets/screenshots/firmware_verifying.png)

Kui värskendus õnnestub:

- The radio will reboot automatically
- Sinihamba ühendus taastatakse
- Verify your settings are intact
- Kontrolli uut versiooni püsivara värskenduse ekraanil jaotises **Praegu installitud** – see kuvatakse ka sõlme üksikasjade lehel ja ühenduste ekraanil

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
2. Use the web flasher in recovery/DFU mode
3. Flashi teadaolevalt toimiv püsivara versioon
4. Seadmepõhiste taastamissammude kohta vaata Meshtastic Discordist

### Compatibility Warnings

The app may show warnings when:

- Ühendatud raadio püsivara versioon on madalam kui minimaalselt toetatud versioon
- Rakenduse ja püsivara versioonide mittevastavus
- Deprecated features need migration

> ⚠️ **Tähtis:** Ühilduvuse tagamiseks värskenda Meshtastic rakendust enne püsivara värskendust.

## Related Topics

- [Ühendused](connections) — ühenduse loomine pärast püsivara värskendamist
- [Püsivara uuendamise juhend](https://meshtastic.org/docs/getting-started/flashing-firmware) — täielik püsivara uuendamise juhend meshtastic.org lehel
- [Toetatud seadmed](https://meshtastic.org/docs/hardware/devices) — ühilduvate raadiote täielik loetelu on leitav aadressilt meshtastic.org
- [KKK](https://meshtastic.org/docs/about/faq) — meshtastic.org levinud küsimused

---

