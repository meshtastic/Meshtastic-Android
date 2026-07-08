---
title: Püsivara värskendus
parent: User Guide
nav_order: 13
last_updated: 2026-07-07
description: Update your radio firmware over Bluetooth or USB — OTA process, version channels, pre-flight checks, and recovery.
aliases:
  - firmware
  - värskendus
  - ota
  - flash
---

# Püsivara värskendus

Hoia oma Meshtastic raadio ajakohasena uusima püsivaraga, et saada uusi funktsioone, veaparandusi ja turvalisuse täiustusi.

## Kontrollin värskendust

1. Open the connected radio's configuration and, under **Advanced**, tap **Firmware Update** — or tap the firmware notification if one is shown. The entry appears only for OTA-capable devices.
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

> ⚠️ **Hoiatus:** Püsivara värskenduse katkestamine võib sinu seadme rikkuda. Ensure your radio has sufficient battery (>50% recommended) and maintain Bluetooth proximity during the entire process.

![Firmware disclaimer](../../assets/screenshots/firmware_disclaimer.png)

### In-App USB Update

When your radio is connected over **USB/serial** (rather than Bluetooth), the Firmware Update screen offers **USB File Transfer**. The app reboots the device into DFU mode, then prompts you to save the `.uf2` file to the device's DFU drive using the system file picker. This option appears only on a USB/serial connection — it is not available over Bluetooth.

> ℹ️ **nRF bootloader note:** Some devices (e.g. RAK WisBlock RAK4631) need their bootloader flashed with the vendor's serial DFU tool (such as `adafruit-nrfutil`) — copying the `.uf2` alone won't update the bootloader. The app surfaces a hint when this applies.

### Other Flashing Options

For recovery or when neither OTA nor in-app USB is available:

- Kasuta [Meshtastic Web Flasherit](https://flasher.meshtastic.org)
- Või arvutil [Meshtastic CLI tööriist](https://meshtastic.org/docs/getting-started/flashing-firmware)

## Version Channels

| Kanal     | Kirjeldus                                                                  |
| --------- | -------------------------------------------------------------------------- |
| Stabiilne | Recommended for most users; tested releases                                |
| Alfa      | Preview releases; may contain bugs                                         |
| Lokaalne  | Flash a firmware file you select yourself, instead of a downloaded release |

## Eelvärskenduse kontrollnimekiri

Before updating:

- [ ] Aku > 50%
- [ ] Stable Bluetooth connection
- [ ] Note your current settings (they may reset on major version changes)
- [ ] Check the release notes for breaking changes

## Eelvärskendus

After the firmware is written, the app verifies the update and waits for the device to come back online:

![Verifying update and waiting for the device to reconnect](../../assets/screenshots/firmware_verifying.png)

Kui värskendus õnnestub:

- The radio will reboot automatically
- Bluetooth connection will re-establish
- Verify your settings are intact
- Confirm the new version under **Currently Installed** on the Firmware Update screen — it's also shown on the node's detail page and the Connections screen

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

