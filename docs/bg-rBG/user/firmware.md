---
title: Актуализации на фърмуера
parent: Ръководство за потребители
nav_order: 13
last_updated: 2026-07-07
description: Update your radio firmware over Bluetooth or USB — OTA process, version channels, pre-flight checks, and recovery.
aliases:
  - firmware
  - update
  - ota
  - flash
---

# Актуализации на фърмуера

Поддържайте вашето Meshtastic радио актуално с най-новия фърмуер за нови функции, корекции на грешки и подобрения в сигурността.

## Проверка за актуализации

1. Open the connected radio's configuration and, under **Advanced**, tap **Firmware Update**. The entry appears only for OTA-capable devices.
2. Приложението проверява за налични версии на фърмуера.
3. Available updates show the version number and changelog summary.

## Методи за актуализиране

### OTA (Over-The-Air) чрез Bluetooth

Най-често срещаният метод за актуализиране за потребителите на Android:

1. Уверете се, че радиото ви е свързано чрез Bluetooth.
2. Отидете до екрана за актуализация на фърмуера.
3. Изберете желаната версия на фърмуера.
4. Tap **Update** to begin the OTA process.
5. Wait for the update to complete — **do not disconnect** during the update.

![Firmware checking for updates](../../assets/screenshots/firmware_checking.png)

> ⚠️ **Warning:** Interrupting a firmware update can brick your device. Ensure your radio has sufficient battery (>50% recommended) and maintain Bluetooth proximity during the entire process.

![Firmware disclaimer](../../assets/screenshots/firmware_disclaimer.png)

### In-App USB Update

When your radio is connected over **USB/serial** (rather than Bluetooth), the Firmware Update screen offers **USB File Transfer**. The app reboots the device into DFU mode, then prompts you to save the `.uf2` file to the device's DFU drive using the system file picker. This option appears only on a USB/serial connection — it is not available over Bluetooth.

> ℹ️ **nRF bootloader note:** Some devices (e.g. RAK WisBlock RAK4631) need their bootloader flashed with the vendor's serial DFU tool (such as `adafruit-nrfutil`) — copying the `.uf2` alone won't update the bootloader. The app surfaces a hint when this applies.

### Other Flashing Options

For recovery or when neither OTA nor in-app USB is available:

- Use the [Meshtastic Web Flasher](https://flasher.meshtastic.org)
- Or the [Meshtastic CLI tool](https://meshtastic.org/docs/getting-started/flashing-firmware) on desktop

## Канали на версиите

| Канал        | Описание                                                                   |
| ------------ | -------------------------------------------------------------------------- |
| Стабилен     | Препоръчва се за повечето потребители; тествани версии                     |
| Алфа         | Предварителни издания; може да съдържат грешки                             |
| Локален файл | Flash a firmware file you select yourself, instead of a downloaded release |

## Контролен списък преди актуализация

Преди актуализиране:

- [ ] Батерия > 50%
- [ ] Стабилна Bluetooth връзка
- [ ] Запишете текущите си настройки (те могат да се нулират при големи промени във версията)
- [ ] Проверете бележките към изданието за важни промени

## След актуализация

After the firmware is written, the app verifies the update and waits for the device to come back online:

![Verifying update and waiting for the device to reconnect](../../assets/screenshots/firmware_verifying.png)

Once the update succeeds:

- Радиото ще се рестартира автоматично
- Bluetooth връзката ще се възстанови
- Проверете дали настройките ви са непокътнати
- Confirm the new version under **Currently Installed** on the Firmware Update screen — it's also shown on the node's detail page and the Connections screen

![Актуализацията на фърмуера е успешна](../../assets/screenshots/firmware_success.png)

## Отстраняване на неизправности

### Update Stuck

If the update appears frozen:

- Изчакайте поне 5 минути, преди да се намесите
- If truly stuck, power-cycle the radio
- Опитайте актуализацията отново

![Грешка при актуализация на фърмуера](../../assets/screenshots/firmware_error.png)

### Device Won't Boot After Update

If your device fails to boot:

1. Try connecting via USB to a computer
2. Use the web flasher in recovery/DFU mode
3. Flash a known-good firmware version
4. Check the Meshtastic Discord for device-specific recovery steps

### Compatibility Warnings

The app may show warnings when:

- Connected radio firmware is below minimum supported version
- Major version mismatch between app and firmware
- Deprecated features need migration

> ⚠️ **Important:** Always update the Meshtastic app before or alongside firmware updates to ensure compatibility.

## Related Topics

- [Connections](connections) — reconnecting after a firmware update
- [Flashing firmware guide](https://meshtastic.org/docs/getting-started/flashing-firmware) — full firmware flashing walkthrough on meshtastic.org
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — check firmware compatibility by device
- [FAQ](https://meshtastic.org/docs/about/faq) — common questions on meshtastic.org

---

