---
title: Соединения
parent: Руководство пользователя
nav_order: 2
last_updated: 2026-05-13
description: Подключите ваш телефон или компьютер к устройству Meshtastic через Bluetooth, USB или TCP/IP.
aliases:
  - bluetooth
  - usb
  - tcp
  - pairing
---

# Соединения

Meshtastic поддерживает несколько способов передачи данных между вашим телефоном/компьютером и радионодой.

## Bluetooth (BLE)

Bluetooth Low Energy является наиболее распространенным методом подключения на Android.

### Привязка устройства

1. Убедитесь, что устройство Meshtastic включено и находится в режиме сопряжения.
2. Откройте приложение и перейдите в **Соединения**.
3. Нажмите **Сканировать устройства** — появятся ближайшие устройства Meshtastic.
4. Выберите ваше устройство из списка.
5. Примите запрос на соединение Bluetooth, если показано.

![Device list item](../../assets/screenshots/connections_bluetooth_scan.png)

Вы можете отфильтровать устройства по типу передачи данных, используя функцию фильтра сверху:

![Transport filter chips](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Подсказка:** Если ваше радио Meshtastic не отображается, проверьте, предоставлены ли разрешения на Bluetooth и геолокацию, а также не подключено ли ваше радио к другому устройству.

### Статус подключения

| Иконка | Состояние      | Описание                 |
| ------ | -------------- | ------------------------ |
| 🟢     | Подключено     | Подключение активно      |
| 🟡     | Подключение    | Выполняется рукопожатие  |
| 🔴     | Отключено      | Нет активного соединения |
| ⚪      | Not configured | Устройство не выбрано    |

При подключении индикатор состояния показывает текущее состояние соединения:

![Connecting status](../../assets/screenshots/connections_connecting.png)

If no devices are found, the app shows an empty state with instructions:

![No devices found](../../assets/screenshots/connections_empty_state.png)

### Troubleshooting Bluetooth

- **Device not found:** Toggle Bluetooth off/on, ensure location is enabled.
- **Connection drops:** Move closer to the radio; check for interference.
- **Pairing rejected:** Forget the device in Android Bluetooth settings and retry.

## USB Serial

USB connections provide a wired alternative, useful for desktop or when Bluetooth is unavailable.

### Setup

1. Connect your radio via USB cable to your device.
2. The app will prompt for USB permission — tap **Allow**.
3. The connection is established automatically.

> ⚠️ **Note:** USB connections require OTG support on Android devices.

## TCP/IP (WiFi)

Some Meshtastic radios support WiFi connectivity, allowing TCP-based connections.

### Настройки

1. Connect your radio to a WiFi network via the radio's web interface or settings.
2. In the app, go to **Connections → TCP**.
3. Enter the radio's IP address and port (default: 4403).
4. Tap **Connect**.

![WiFi scanning for devices](../../assets/screenshots/connections_wifi_scanning.png)

When a device is found, it appears in the connection list:

![WiFi device found](../../assets/screenshots/connections_wifi_device_found.png)

A successful connection is confirmed with a status indicator:

![WiFi connection success](../../assets/screenshots/connections_wifi_success.png)

### When to Use TCP

- Radio is on the same local network
- Testing with a simulated radio
- Environments where Bluetooth has interference issues

## Reconnection Behavior

The app reconnects to the **last selected device** on startup. You can manually switch transports from the connections screen at any time.

To disconnect from a radio, use the disconnect button on the connections screen:

![Disconnect from radio](../../assets/screenshots/connections_disconnect.png)

## Desktop Connections

On Desktop (Linux/macOS/Windows), the app supports:

- **Bluetooth (BLE)** — via the Kable library; works on macOS, Linux, and Windows
- **USB Serial** — primary wired connection method
- **TCP/IP** — for network-connected radios

See [Desktop App](desktop) for platform-specific details and keyboard shortcuts.

## Related Topics

- [Getting Started](onboarding) — first-launch setup and permissions
- [Settings — Radio & User](settings-radio-user) — Bluetooth and network configuration
- [Desktop App](desktop) — desktop-specific connection details
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — full list of compatible radios on meshtastic.org

---

