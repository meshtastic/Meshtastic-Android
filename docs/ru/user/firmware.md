---
title: Обновления прошивки
nav_order: 13
last_updated: 2026-10-21
description: Update your node's firmware over Bluetooth, Wi-Fi, or USB — OTA process, version channels, pre-flight checks, and recovery.
aliases:
  - firmware
  - update
  - ota
  - flash
---

# Обновления прошивки

Keep your Meshtastic node up to date with the latest firmware for new features, bug fixes, and security improvements.

## Проверка обновлений

1. Open **Settings** for the connected node and, under **Advanced**, tap **Firmware Update**. The entry appears only for OTA-capable nodes.
2. Приложение проверяет доступные версии прошивки.
3. Available updates show the version number and release notes.

The app also tells you when the connected node is behind the latest stable release. The **Connect** tab shows a **Firmware update available** card naming the installed and the stable version, and a notification says the same once per node and release. Its button reads **Open Firmware Updates** when the app can update the node over the current connection, or **Open Meshtastic Flasher** when it can't.

## Методы обновления

The Firmware Update screen picks the method from how the node is connected, and its update button names it: **Update via BLE OTA**, **Update via Wi-Fi OTA**, or **Update via USB File Transfer**.

| Соединения | nRF52                                   | ESP32                                   | RP2040                    |
| ---------- | --------------------------------------- | --------------------------------------- | ------------------------- |
| Bluetooth  | BLE OTA (по воздуху) | BLE OTA (по воздуху) | —                         |
| Сеть       | —                                       | Wi-Fi OTA                               | —                         |
| USB        | Передача файлов через USB               | —                                       | Передача файлов через USB |

Where the table has no method, the screen says firmware updates are not supported over this connection. Connect another way, or use the [Web Flasher](https://flasher.meshtastic.org).

### OTA (беспроводное обновление) через Bluetooth

Наиболее распространённый способ обновления для пользователей Android:

> ⚠️ **Warning:** Interrupting a firmware update can leave the node unable to boot. Держите телефон рядом и оба устройства включенными до завершения обновления.

1. Ensure your node is connected via Bluetooth.
2. Перейдите на экран "Обновление прошивки".
3. Выберите нужную версию прошивки.
4. Tap **Update via BLE OTA**. An **Update Warning** dialog lists the pre-flight checks — read it, then tap **I know what I'm doing.** to start. This dialog appears for every update method, including Wi-Fi OTA, USB, and a local firmware file.
5. Wait for the update to complete. Do not disconnect during the update.

While the update downloads, uploads or verifies, you can tap **Play Chirpy Hop** to play a short runner game: tap to jump, swipe down to crouch. The update's progress stays visible above the game, and back returns to the update screen. When the update finishes or fails, the game stops and offers **Back to update**. Your best score is kept on this device.

![Проверка обновлений прошивки](../../assets/screenshots/firmware_checking.png)

Some boards ship with a bootloader that cannot take a Bluetooth update. For these, the screen shows a warning card before you start, with a hint for the board where one exists. **Don't show again for this device** hides it for that node. When a stock nRF bootloader limits how fast the upload can run, the screen says so during the upload, and again on the success screen, with a link to the OTAFIX bootloader that makes Bluetooth updates much faster.

#### Очистить устройство при обновлении

Where the app offers it, an **Erase device during update** switch appears above the update button. It is a per-update opt-in and is never remembered.

| Method          | What erasing does                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / Wi-Fi OTA | Factory-resets the device once the update is verified. All settings and Bluetooth pairing are removed. |
| USB             | Полностью стирает флэш-память устройства, а затем устанавливает выбранную прошивку с нуля.                             |

It is not offered for a local firmware file, during a recovery update, or over USB in the desktop app — the USB erase runs the Android-only maintenance sequence below. Over USB it is offered for nRF52 and RP2040 nodes only, and when the app can't erase one safely, the switch is shown disabled with the reason. Afterwards the device needs setting up — and pairing — again.

### OTA via Wi-Fi (network-connected ESP32)

When an ESP32 node is connected over the network rather than Bluetooth, the app offers **Wi-Fi OTA**, which pushes the same update over TCP:

1. Connect to the node over the network (see [Connections](connections)).
2. Open the Firmware Update screen and pick a version.
3. Tap **Update via Wi-Fi OTA**. Keep the node and phone on the same network for the whole transfer.

Wi-Fi OTA installs the ESP32 app image, a `.bin` file, rather than the `.uf2` a USB update uses. The app selects the right file from the release for you.

### Обновление внутри приложения по USB

When your nRF52 or RP2040 node is connected over **USB/serial** (rather than Bluetooth), the Firmware Update screen offers **USB File Transfer**. Приложение перезагружает устройство в режим DFU, а затем предлагает сохранить файл `.uf2` на DFU-диск устройства с помощью системного выбора файлов. This option appears only on a USB/serial connection. It is not available over Bluetooth, and ESP32 nodes have no USB update path in the app.

A vendor nRF bootloader supplied as a `.zip` (e.g. RAK WisBlock RAK4631) has to be flashed with a serial DFU tool such as `adafruit-nrfutil` — copying that `.zip` to the drive won't work. Загрузчик, поставляемый в виде `update-....uf2`, **можно** установить, скопировав его на диск; именно так работает обновление загрузчика из самого приложения. Приложение показывает подсказку, когда требуется использование последовательного способа.

### Полное стирание и обновление загрузчика

On a **USB/serial** connection, nRF52 and RP2040 devices can be wiped as part of an update — that is the **Erase device during update** opt-in described earlier on this page. nRF52 devices additionally offer **Upgrade bootloader** where an upgraded bootloader is published for the board; RP2040 devices run no Adafruit bootloader, so they never see it.

Both are Android-only. They depend on Android's update-drive checks, so the desktop app does not show them — use the [Web Flasher](https://flasher.meshtastic.org) there instead.

Select a firmware version before either one: the app hides both until a release is chosen, because the firmware is reinstalled after the device is wiped or the new bootloader is written.

Both a USB erase and a bootloader upgrade write two files in turn, so you are asked to select the device's update drive twice: once for the erase or bootloader image, then again for the firmware.

Приложение считывает `INFO_UF2.TXT` с выбранного тобою диска, чтобы убедиться, что это действительно диск обновления устройства, и определить плату до записи чего-либо.

For a bootloader upgrade, the app also reads the installed bootloader version from `INFO_UF2.TXT` and shows it next to the latest release before writing anything. The running firmware doesn't report its bootloader, so the installed version appears only once the device has restarted into update mode, never on the firmware screen while connected. If the two match, the bootloader is left as it is and the app moves straight on to reinstalling the firmware. Otherwise choose **Upgrade bootloader** to write it, or **Skip** to reinstall the firmware without changing it.

On nRF52 the app must already know which Bluetooth stack your device uses before it starts, because it can't read the bootloader until the device has rebooted. If it can't confirm the stack, it refuses to erase and points you at the [Web Flasher](https://flasher.meshtastic.org) instead. In the Web Flasher, choosing the wrong Bluetooth stack can leave the node recoverable only with a hardware programmer.

Once the drive is readable, how an nRF52 device is erased depends on its bootloader:

- **Newer bootloaders erase themselves.** A bootloader that advertises factory-erase support in `INFO_UF2.TXT` (OTAFIX 2.4 and later) takes a single, board-agnostic erase file. The bootloader wipes your settings, channels, keys and the stored node list itself, leaves the installed firmware in place, and comes back as an update drive a second or two later for the firmware file. There is no Bluetooth-stack choice to get wrong on this path.
- **Older bootloaders need a Bluetooth-stack-specific erase.** On these the app writes a small erase program matched to your device's Bluetooth stack, then starts it over the serial port. If the drive reports a different stack than expected, the app refuses rather than guessing.

The app picks the path from what the drive reports; you don't choose it.

### Другие способы прошивки

Для восстановления или когда ни OTA, ни внутри приложение USB недоступны:

- Используйте [Meshtastic Web Flasher](https://flasher.meshtastic.org)
- Или инструмент командной строки [Meshtastic CLI](https://meshtastic.org/docs/getting-started/flashing-firmware) на компьютере

## Каналы версий

| Канал          | Описание                                                                                                                                                                                          |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Стабильная     | Рекомендуется для большинства пользователей; протестированные релизы                                                                                                                              |
| Альфа          | Предварительные релизы; могут содержать ошибки                                                                                                                                                    |
| Локальный файл | Flash a firmware file you select yourself, instead of a downloaded release. The button reads **Select Local File**, and the app asks you to confirm the file name before flashing |

## Предполётная проверка

Перед обновлением:

- [ ] Battery above 50%, recommended. The app itself refuses to start an update only when the node reports 10% or less
- [ ] A stable connection over the method you're updating with: Bluetooth, Wi-Fi, or USB
- [ ] Запишите свои текущие настройки (они могут сброситься при смене мажорной версии)
- [ ] Проверьте примечания к релизу на наличие критических изменений
- [ ] Update the Meshtastic app itself, before or alongside firmware updates, to ensure compatibility

## После обновления

После записи прошивки приложение проверяет обновление и ждёт, пока устройство снова станет доступным:

![Проверка обновления и ожидание переподключения устройства](../../assets/screenshots/firmware_verifying.png)

После успешного обновления:

- The node reboots automatically
- The Bluetooth connection re-establishes
- Убедись, что твои настройки сохранились
- Проверьте новую версию в разделе **"Установленная версия"** на экране обновления прошивки — она также отображается на странице сведений о ноде и на экране "Подключения"

![Успешное обновление прошивки](../../assets/screenshots/firmware_success.png)

## Устранение неполадок

### Обновление зависло

Если обновление кажется зависшим:

- Give it a minute. After writing the image the app waits up to **60 seconds** for the node to come back and report its new version, so a pause at the verify step is expected.
- If it is still stuck after that, power-cycle the node.
- Attempt the update again.

The message **Verification timed out. Device did not reconnect in time.** means the image was written but the node did not come back within that window. Power-cycle it and check the version under **Currently Installed** before re-running the update.

![Ошибка обновления прошивки](../../assets/screenshots/firmware_error.png)

### Radio Won't Boot After Update

A Bluetooth update that stops partway can leave the node waiting in bootloader mode. While the app is disconnected, the **Connect** tab then shows a **Finish updating** card naming the node. Tap **Resume firmware update** and keep the node nearby so the app can re-flash it, or dismiss the card if the update can't be finished from the phone.

If the app told you the Bluetooth update could not be finished, follow the instruction it gave: connect the node to a computer over USB and re-flash it with the vendor's serial DFU tool, such as `adafruit-nrfutil`. A stock nRF bootloader cannot reliably complete an interrupted over-the-air update.

Otherwise, if your node fails to boot:

1. Попробуйте подключиться по USB к компьютеру
2. Используйте Web Flasher в режиме восстановления/DFU
3. Прошивайте заведомо рабочую версию прошивки
4. Обратитесь к Discord-серверу Meshtastic для получения инструкций по восстановлению, специфичных для твоего устройства

### Предупреждения о совместимости

On connecting, the app compares the node's firmware against two thresholds and reacts differently to each:

| Версия прошивки                                                                                                 | What you see                                  | What happens                                                                                                                                                                                                                                                                                         |
| --------------------------------------------------------------------------------------------------------------- | --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Below **2.3.15**                                                                | **Firmware update required.** | The app disconnects from the node. It does not operate against firmware this old.                                                                                                                                                                                    |
| **2.3.15** up to, but not including, **2.5.14** | **Firmware update required**                  | The node stays connected, but the dialog blocks the rest of the app until the firmware is updated. It names the installed, minimum, and latest stable versions. **Update firmware** opens the Firmware Update screen; **Disconnect** drops the node. |
| **2.5.14** or newer                                                             | Nothing                                       | —                                                                                                                                                                                                                                                                                                    |

A version string the app cannot parse is ignored rather than treated as too old, so a transient read never disconnects a working node.

## Связанные темы

- [Подключения](connections) — восстановление соединения после обновления прошивки
- [Руководство по прошивке](https://meshtastic.org/docs/getting-started/flashing-firmware)— полное пошаговое руководство по полной прошивке на meshtastic.org
- [Поддерживаемые устройства](https://meshtastic.org/docs/hardware/devices) — проверьте совместимость прошивки с устройством
- [FAQ](https://meshtastic.org/docs/faq/) — общие вопросы на meshtastic.org
