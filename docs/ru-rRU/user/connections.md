---
title: Соединения
parent: Руководство пользователя
nav_order: 2
last_updated: 2026-08-29
description: Подключи свой телефон или компьютер к устройству Meshtastic через Bluetooth, USB или TCP/IP.
aliases:
  - bluetooth
  - usb
  - tcp
  - pairing
---

# Соединения

Meshtastic supports multiple transport methods to communicate between your phone or desktop and a radio.

## Bluetooth (BLE)

Bluetooth Low Energy является наиболее распространенным методом подключения на Android.

### Pairing a Radio

1. Убедитесь, что устройство Meshtastic включено и находится в режиме сопряжения.
2. Откройте приложение и перейдите на вкладку **Подключение**.
3. Нажми **Сканировать Bluetooth-устройства** — рядом появятся радиостанции Meshtastic.
4. Select your radio from the list.
5. Примите запрос на соединение Bluetooth, если показано.

![Сканирование устройств Bluetooth с обнаружением радиоустройств в списке](../../assets/screenshots/connections_bluetooth_scan.png)

Используйте селектор транспорта — ряд сегментированных кнопок под карточкой подключения — для переключения между транспортами Bluetooth, Network и USB (одновременно активен только один):

![Connections screen with the transport selector showing Bluetooth, Network, and USB](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Tip:** If your radio doesn't appear, check that it isn't already connected to another phone, or out of range.

The screen names anything on the app's side that is blocking a scan, with the fix attached:

| What you see                                        | Что это значит                                                                                                                                                             |
| --------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A card asking for **Nearby devices**                | The permission has not been granted. **Grant permission** requests it; once Android stops prompting, the button becomes **Open settings**. |
| **Bluetooth is off**                                | The adapter is disabled — the card opens Bluetooth settings.                                                                                               |
| **Bluetooth scanning also needs location services** | Android 11 and older only: the permission is held but the system location toggle is off.                                                   |
| No card, empty list                                 | Nothing on this side is blocking the scan — the radio is out of range, off, or already connected elsewhere.                                                |

Tapping **Scan** after you have declined the permission once explains what it is for before asking again, and lets you decline again without being cornered.

### Статус подключения

| Иконка | Состояние       | Описание                                                                                   |
| ------ | --------------- | ------------------------------------------------------------------------------------------ |
| 🟢     | Подключено      | Подключение активно                                                                        |
| 🟡     | Подключение     | Выполняется рукопожатие                                                                    |
| 🔴     | Отключено       | No active connection; the app keeps trying to reconnect                                    |
| ⚪      | Устройство спит | The radio is in light sleep — the app is waiting for it to wake and reconnect, not failing |

These are the four states the app models. "Device sleeping" is normal on power-saving configurations and needs no action.

When connecting, a status indicator shows the current connection state — tap **Stop Connecting** to abandon the attempt:

![Состояние подключения](../../assets/screenshots/connections_connecting.png)

Если устройства не найдены, приложение показывает пустое состояние с инструкциями:

![Устройства не найдены](../../assets/screenshots/connections_empty_state.png)

### Устранение неполадок Bluetooth

- **Устройство не найдено:** Выключи и снова включи Bluetooth, убедись, что включена служба определения местоположения.
- **Потеря связи:** Подойди ближе к ноде; проверь наличие помех.
- **Сопряжение отклонено:** Забыть устройство в настройках Bluetooth на Android и повторить попытку.

## Последовательный USB

Подключения через USB предоставляют проводную альтернативу, полезную для настольных компьютеров или когда Bluetooth недоступен.

### Настройка

1. Connect your radio to your phone with a USB cable.
2. The app prompts for USB permission — tap **Allow**.
3. Соединение устанавливается автоматически.

> ℹ️ **Note:** USB connections require OTG support on Android devices.

## TCP/IP (Сеть)

Some Meshtastic radios support Wi-Fi/Ethernet connectivity, allowing TCP-based connections over your local network. Get the radio onto your network first — using the radio's own Wi-Fi settings (via the firmware web interface or another connection) — then connect to it from the app.

> ℹ️ **Note:** **Settings → Wi-Fi Provisioning for mPWRD-OS** is a separate, narrower tool. It provisions Wi-Fi
> credentials over Bluetooth to **mPWRD-OS** devices only, using their own protocol — it does not
> configure Wi-Fi on an ordinary Meshtastic radio. It scans over BLE, lists the networks the device
> can see (including an option for a hidden SSID), takes the password, and reports success or
> failure. Available on both Android and Desktop.

### Подключение к сети

1. Убедись, что радио подключено к той же локальной сети, что и твой телефон/компьютер.
2. На экране "Подключение" выберите **Network** в селекторе транспорта.
3. Выбери радиоустройство одним из двух способов:
   - **Сканировать сетевые устройства** — включи эту опцию чтобы автоматически обнаруживать радиоустройства, которые объявляют о себе в локальной сети (mDNS / `_meshtastic._tcp`). Обнаруженные устройства появляются в списке; нажми на одно, чтобы подключиться.
   - **Добавить устройство вручную…** — введи IP-адрес радиостанции (или имя хоста) и порт (по умолчанию: `4403`).
4. Previously-used network addresses are remembered under **Recent Network Devices** for quick reconnection (touch & hold to remove one).

> 💡 **Совет:** Обнаружение сети использует mDNS, который работает только когда оба устройства находятся в одной подсети. На Android 17+ приложению нужно разрешение на локальную сеть для сканирования; если поиск ничего не находит, добавь устройство вручную по IP.

### Когда использовать TCP

- Радиостанция находится в той же локальной сети
- Тестирование с использованием имитирования радиоприемника
- Окружающая среда, где возникают помехи Bluetooth

## Поведение при повторном подключении

The app reconnects to the last selected radio on startup. Вы можете переключать транспорт с экрана подключения в любое время.

Чтобы отключиться, нажми кнопку отключения на экране подключения:

![Отключение от радиоприёмника](../../assets/screenshots/connections_disconnect.png)

## Подключение к компьютеру

На компьютере (Linux/macOS/Windows) приложение поддерживает:

- **Bluetooth (BLE)** — через библиотеку Kable; работает на macOS, Linux и Windows
- **Последовательный USB** — основной способ проводного подключения
- **TCP/IP** — для радиостанций, подключенных к сети

См. [десктопное приложение](desktop) для информации о платформе и сочетаниях клавиш.

## Связанные темы

- [Начало работы](onboarding) — настройка при первом запуске и разрешения
- [Настройки — Радио и пользователь](settings-radio-user) — настройка Bluetooth и сети
- [Десктопное приложение](desktop) — детали подключения, специфичные для настольных компьютеров
- [Поддерживаемые устройства](https://meshtastic.org/docs/hardware/devices) — полный список совместимых радиоустройств на meshtastic.org
