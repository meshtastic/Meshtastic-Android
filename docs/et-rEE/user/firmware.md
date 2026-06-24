---
title: Püsivara värskendus
parent: User Guide
nav_order: 13
last_updated: 2026-05-13
description: Raadio püsivara uuendamine sinihamba ​​kaudu – OTA protsess, versioonikanalid, lennueelsed kontrollid ja taastamine.
aliases:
  - firmware
  - värskendus
  - ota
  - flash
---

# Püsivara värskendus

Hoia oma Meshtastic raadio ajakohasena uusima püsivaraga, et saada uusi funktsioone, veaparandusi ja turvalisuse täiustusi.

## Kontrollin värskendust

1. Mine menüüsse **Seaded → Püsivara värskendus** või puuduta püsivara teavitust, kui see kuvatakse.
2. The app checks for available firmware versions.
3. Saadaval olevad värskendused näitavad versiooninumbrit ja muudatuste logi kokkuvõtet.

## Värskendamise meetod

### OTA (Over-The-Air) via Bluetooth

Kõige levinum värskendamisviis Androidi kasutajate seas:

1. Ensure your radio is connected via Bluetooth.
2. Mine püsivara värskenduse lehele.
3. Select the desired firmware version.
4. OTA alustamiseks puuduta nuppu **Uuenda**.
5. Oota, kuni värskendus on lõppenud – **ära katkesta ühendust** värskenduse ajal.

![Püsivara kontrollib värskendusi](../../assets/screenshots/firmware_checking.png)

> ⚠️ **Hoiatus:** Püsivara värskenduse katkestamine võib teie seadme rikkuda. Ensure your radio has sufficient battery (>50% recommended) and maintain Bluetooth proximity during the entire process.

![Firmware disclaimer](../../assets/screenshots/firmware_disclaimer.png)

### USB Flashing

For recovery or when OTA is unavailable:

- Kasuta [Meshtastic Web Flasherit](https://flasher.meshtastic.org)
- Või arvutil [Meshtastic CLI tööriist](https://meshtastic.org/docs/getting-started/flashing-firmware)

## Version Channels

| Kanal     | Kirjeldus                                   |
| --------- | ------------------------------------------- |
| Stabiilne | Recommended for most users; tested releases |
| Alfa      | Preview releases; may contain bugs          |

## Eelvärskenduse kontrollnimekiri

Before updating:

- [ ] Aku > 50%
- [ ] Stable Bluetooth connection
- [ ] Note your current settings (they may reset on major version changes)
- [ ] Check the release notes for breaking changes

## Eelvärskendus

Peale edukat värskendust:

- The radio will reboot automatically
- Bluetooth connection will re-establish
- Verify your settings are intact
- Check the firmware version in **Settings → About**

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
3. Flash a known-good firmware version
4. Seadmepõhiste taastamissammude kohta vaata Meshtastic Discordist

### Compatibility Warnings

The app may show warnings when:

- Connected radio firmware is below minimum supported version
- Major version mismatch between app and firmware
- Deprecated features need migration

> ⚠️ **Tähtis:** Ühilduvuse tagamiseks värskenda Meshtastic rakendust enne püsivara värskendust.

## Related Topics

- [Ühendused](connections) — ühenduse loomine pärast püsivara värskendamist
- [Püsivara uuendamise juhend](https://meshtastic.org/docs/getting-started/flashing-firmware) — täielik püsivara uuendamise juhend meshtastic.org lehel
- [Toetatud seadmed](https://meshtastic.org/docs/hardware/devices) — ühilduvate raadiote täielik loetelu on leitav aadressilt meshtastic.org
- [KKK](https://meshtastic.org/docs/about/faq) — meshtastic.org levinud küsimused

---

