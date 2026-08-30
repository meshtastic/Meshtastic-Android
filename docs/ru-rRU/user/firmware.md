---
title: Обновления прошивки
parent: Руководство пользователя
nav_order: 13
last_updated: 2026-08-29
description: Обновляйте прошивку своего радио по Bluetooth или USB — процесс OTA, каналы версий, предполётные проверки и восстановление.
aliases:
  - firmware
  - update
  - ota
  - flash
---

# Обновления прошивки

Поддерживайте своё радио Meshtastic в актуальном состоянии с помощью последней прошивки для получения новых функций, исправлений ошибок и улучшений безопасности.

## Проверка обновлений

1. Откройте конфигурацию подключённого радио и в разделе **"Дополнительно"** нажмите **"Обновление прошивки"**. The entry appears only for OTA-capable radios.
2. Приложение проверяет доступные версии прошивки.
3. Доступные обновления показывают номер версии и сводку изменений.

## Методы обновления

### OTA (беспроводное обновление) через Bluetooth

Наиболее распространённый способ обновления для пользователей Android:

> ⚠️ **Warning:** Interrupting a firmware update can leave the radio unable to boot. Keep the phone nearby and both devices powered until the update completes.

1. Убедитесь, что твоё радио подключено по Bluetooth.
2. Перейдите на экран "Обновление прошивки".
3. Выберите нужную версию прошивки.
4. Нажмите **"Обновить"**, чтобы начать процесс OTA.
5. Дождитесь завершения обновления — **не отключайте устройство** во время обновления.

![Проверка обновлений прошивки](../../assets/screenshots/firmware_checking.png)

#### Очистить устройство при обновлении

Where the app offers it, an **Erase device during update** checkbox appears next to the update button. It is a per-update opt-in and is never remembered.

| Method          | What erasing does                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / Wi-Fi OTA | Factory-resets the device once the update is verified. All settings and Bluetooth pairing are removed. |
| USB             | Полностью стирает флэш-память устройства, а затем устанавливает выбранную прошивку с нуля.                             |

It is not offered for a local firmware file, during a recovery update, or on USB devices whose board does not support the erase step. Afterwards the device needs setting up — and pairing — again.

### OTA via Wi-Fi (network-connected ESP32)

When an ESP32 radio is connected over the network rather than Bluetooth, the app offers **Wi-Fi OTA**, which pushes the same update over TCP:

1. Connect to the radio over the network (see [Connections](connections)).
2. Open the Firmware Update screen and pick a version.
3. Tap **Update**. Keep the radio and phone on the same network for the whole transfer.

Wi-Fi OTA takes the ESP32 `-update.bin` image rather than the `.uf2` a USB update uses; the app selects the right artifact for you.

![Предупреждение о прошивке](../../assets/screenshots/firmware_disclaimer.png)

### Обновление внутри приложения по USB

Когда твоё радио подключено по **USB/seria**l (а не по Bluetooth), на экране обновления прошивки появляется опция **"Передача файла по USB"**. Приложение перезагружает устройство в режим DFU, а затем предлагает сохранить файл `.uf2` на DFU-диск устройства с помощью системного выбора файлов. Эта опция появляется только при подключении по USB/serial — она недоступна по Bluetooth.

> ℹ️ **Note:** A vendor nRF bootloader supplied as a `.zip` (e.g. RAK WisBlock RAK4631) has to be flashed with a serial DFU tool such as `adafruit-nrfutil` — copying that `.zip` to the drive won't work. Загрузчик, поставляемый в виде `update-....uf2`, **можно** установить, скопировав его на диск; именно так работает обновление загрузчика из самого приложения. Приложение показывает подсказку, когда требуется использование последовательного способа.

### Полное стирание и обновление загрузчика

При подключении по **USB/serial** устройства на базе nRF52 и RP2040 также предлагают **"Стереть и переустановить"** и, если для платы опубликован обновлённый загрузчик, **"Обновить загрузчик"**.

Стирание удаляет всё на устройстве — каналы, ключи и все настройки — и резервной копии нет, поэтому приложение сначала запрашивает подтверждение. Обе операции записывают два файла по очереди, поэтому тебе будет предложено дважды выбрать диск обновления устройства: один раз для образа стирания или загрузчика, затем снова для прошивки.

Приложение считывает `INFO_UF2.TXT` с выбранного тобою диска, чтобы убедиться, что это действительно диск обновления устройства, и определить плату до записи чего-либо. If it can't confirm which Bluetooth stack your device uses, it refuses to erase and points you at the [Web Flasher](https://flasher.meshtastic.org) instead. In the Web Flasher, choosing the wrong Bluetooth stack can leave the radio recoverable only with a hardware programmer.

### Другие способы прошивки

Для восстановления или когда ни OTA, ни внутри приложение USB недоступны:

- Используйте [Meshtastic Web Flasher](https://flasher.meshtastic.org)
- Или инструмент командной строки [Meshtastic CLI](https://meshtastic.org/docs/getting-started/flashing-firmware) на компьютере

## Каналы версий

| Канал          | Описание                                                                    |
| -------------- | --------------------------------------------------------------------------- |
| Стабильная     | Рекомендуется для большинства пользователей; протестированные релизы        |
| Альфа          | Предварительные релизы; могут содержать ошибки                              |
| Локальный файл | Прошить файл прошивки, который ты выбираешь сам, вместо загруженного релиза |

## Предполётная проверка

Перед обновлением:

- [ ] Заряд батареи > 50%
- [ ] Стабильное соединение Bluetooth
- [ ] Запишите свои текущие настройки (они могут сброситься при смене мажорной версии)
- [ ] Проверьте примечания к релизу на наличие критических изменений
- [ ] Update the Meshtastic app itself, before or alongside firmware updates, to ensure compatibility

## После обновления

После записи прошивки приложение проверяет обновление и ждёт, пока устройство снова станет доступным:

![Проверка обновления и ожидание переподключения устройства](../../assets/screenshots/firmware_verifying.png)

После успешного обновления:

- The radio reboots automatically
- The Bluetooth connection re-establishes
- Убедись, что твои настройки сохранились
- Проверьте новую версию в разделе **"Установленная версия"** на экране обновления прошивки — она также отображается на странице сведений о ноде и на экране "Подключения"

![Успешное обновление прошивки](../../assets/screenshots/firmware_success.png)

## Устранение неполадок

### Обновление зависло

Если обновление кажется зависшим:

- Give it a minute. After writing the image the app waits up to **60 seconds** for the radio to come back and report its new version, so a pause at the verify step is expected.
- If it is still stuck after that, power-cycle the radio.
- Attempt the update again.

![Ошибка обновления прошивки](../../assets/screenshots/firmware_error.png)

### Radio Won't Boot After Update

If your radio fails to boot:

1. Попробуйте подключиться по USB к компьютеру
2. Используйте Web Flasher в режиме восстановления/DFU
3. Прошивайте заведомо рабочую версию прошивки
4. Обратитесь к Discord-серверу Meshtastic для получения инструкций по восстановлению, специфичных для твоего устройства

### Предупреждения о совместимости

On connecting, the app compares the radio's firmware against two thresholds and reacts differently to each:

| Версия прошивки                                                                                                 | What you see                                     | What happens                                                                                                         |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Below **2.3.15**                                                                | **Firmware update required.**    | The app disconnects from the radio. It does not operate against firmware this old.   |
| **2.3.15** up to, but not including, **2.5.14** | **Firmware Update Recommended.** | Advisory only — dismiss it and carry on. The dialog names the latest stable release. |
| **2.5.14** or newer                                                             | Nothing                                          | —                                                                                                                    |

A version string the app cannot parse is ignored rather than treated as too old, so a transient read never disconnects a working radio.

## Связанные темы

- [Подключения](connections) — восстановление соединения после обновления прошивки
- [Руководство по прошивке](https://meshtastic.org/docs/getting-started/flashing-firmware)— полное пошаговое руководство по полной прошивке на meshtastic.org
- [Поддерживаемые устройства](https://meshtastic.org/docs/hardware/devices) — проверьте совместимость прошивки с устройством
- [FAQ](https://meshtastic.org/docs/faq/) — общие вопросы на meshtastic.org
