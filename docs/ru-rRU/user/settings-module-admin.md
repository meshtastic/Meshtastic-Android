---
title: Настройки — Модули и администрирование
parent: Руководство пользователя
nav_order: 8
last_updated: 2026-05-20
description: Configure optional feature modules (MQTT, telemetry, canned messages, TAK, and more) and perform device administration.
aliases:
  - modules
  - module-config
  - administration
---

# Настройки — Модули и администрирование

Configure optional feature modules and perform device administration. Modules extend Meshtastic with specialized capabilities — each can be independently enabled or disabled.

> 💡 **Tip:** You only need to enable the modules you actually use. Disabling unused modules reduces airtime, saves battery, and simplifies your configuration.

Module settings use a card-based layout with toggle switches, dropdowns, text fields, and sliders:

![Toggle switch](../../assets/screenshots/settings_switch.png)

![Dropdown selector](../../assets/screenshots/settings_dropdown.png)

![Text field](../../assets/screenshots/settings_text_field.png)

![Settings card layout](../../assets/screenshots/settings_titled_card.png)

## Конфигурация модуля

### Модуль MQTT

Bridges mesh messages to and from an MQTT broker for internet connectivity. This is how you extend your mesh beyond radio range or integrate with home automation systems.

| Настройка        | Описание                                                                 |
| ---------------- | ------------------------------------------------------------------------ |
| Включено         | Toggle MQTT bridge                                                       |
| Сервер           | MQTT broker address                                                      |
| Имя пользователя | Authentication username                                                  |
| Пароль           | Authentication password                                                  |
| Шифрование       | Encrypt MQTT payloads                                                    |
| ~~JSON Output~~  | ⚠️ **Deprecated** — JSON support removed from firmware; field is ignored |
| TLS              | Use secure connection                                                    |
| Корневая тема    | Base MQTT topic path                                                     |
| Отчет карты      | Publish position for public map                                          |

See [MQTT](mqtt) for a detailed usage guide including encryption, privacy, and broker setup.

### Serial Module

Enables serial port communication for external device integrations (GPS modules, sensors, or custom hardware). When enabled, the node's serial port can send and receive protobuf or text data, allowing external microcontrollers or computers to interact with the mesh.

| Настройка                                  | Описание                        |
| ------------------------------------------ | ------------------------------- |
| Включено                                   | Activate serial communication   |
| Эхо                                        | Echo received serial data back  |
| Режим обмена                               | Text, Protobuf, or NMEA output  |
| RX/TX Pins                                 | GPIO pins for serial connection |
| Скорость передачи (бод) | Serial communication speed      |

### External Notification Module

Controls buzzer, LED, or vibration alerts on your radio hardware. Useful for devices that need to physically signal when a message arrives — particularly helpful for unattended or outdoor installations.

| Настройка                                 | Описание                    |
| ----------------------------------------- | --------------------------- |
| Включено                                  | Activate notifications      |
| Включить уведомление о входящем сообщении | Notify on incoming messages |
| Зуммер при уведомлении                    | Use buzzer for messages     |
| Alert Message Vibra                       | Use vibration for messages  |
| Уведомлять при 🔔                         | Notify on bell character    |
| Output (GPIO)          | Pin for notification output |
| Активный выход                            | High or Low active          |
| Duration (ms)          | Notification length         |
| Use I2S as Buzzer                         | Use I2S audio output        |

### Store & Forward Module

Buffers messages for nodes that were temporarily offline, then replays them when those nodes reconnect. Essential for meshes where nodes go in and out of range regularly — ensures messages aren't lost during brief disconnections.

| Настройка                                  | Описание                   |
| ------------------------------------------ | -------------------------- |
| Включено                                   | Activate store and forward |
| Heartbeat (s)           | Announcement interval      |
| Записи                                     | Maximum stored messages    |
| History Return (max)    | Max messages to replay     |
| History Return (window) | Time window for replay     |

> 💡 **Tip:** Store and Forward works best on nodes with ample memory (ESP32 with PSRAM). Router nodes are ideal candidates since they're typically always-on.

### Range Test Module

Automated range testing tool for evaluating link quality between nodes. When enabled, the node periodically transmits test messages with incrementing counters. A receiver node logs these messages, allowing you to walk or drive away and later analyze at what distance messages stopped arriving.

| Настройка                              | Описание                          |
| -------------------------------------- | --------------------------------- |
| Включено                               | Activate range testing            |
| Sender Interval (s) | Time between test transmissions   |
| Save CSV                               | Log received test data to SD card |

### Telemetry Module

Controls what telemetry data your node shares with the mesh. Telemetry includes device health (battery, uptime) and environmental sensor data (temperature, humidity, pressure).

| Настройка                    | Описание                                |
| ---------------------------- | --------------------------------------- |
| Device Metrics Interval      | How often to report device metrics      |
| Environment Metrics Interval | How often to report environment sensors |
| Телеметрия воздуха           | Report particulate sensor data          |
| Power Metrics Enabled        | Report power usage                      |

See [Telemetry & Sensors](telemetry-and-sensors) for supported sensors and configuration recommendations.

### Canned Message Module

Pre-configured messages accessible from the device's physical buttons (for radios with rotary encoders, keypads, or similar input hardware). Define a list of quick-send messages that can be transmitted without a phone connected — ideal for field use.

| Настройка          | Описание                                                    |
| ------------------ | ----------------------------------------------------------- |
| ~~Enabled~~        | ⚠️ **Deprecated** — current firmware may ignore this toggle |
| Сообщения          | Newline-separated list of messages                          |
| Отправлять 🔔      | Play bell sound on send                                     |
| Rotary Encoder     | Enable rotary encoder input                                 |
| Up/Down/Press Pins | GPIO pin assignments for input                              |

### Audio Module

Codec2 audio support for low-bandwidth voice communication over the mesh. This is an **experimental** feature that encodes voice into very small data packets using the Codec2 codec.

| Настройка       | Описание                         |
| --------------- | -------------------------------- |
| Включено        | Activate audio module            |
| Codec2 Rate     | Audio quality/bandwidth tradeoff |
| I2S Word Select | GPIO pin for I2S WS              |
| I2S Data In     | GPIO pin for I2S DIN             |
| I2S Data Out    | GPIO pin for I2S DOUT            |

> ⚠️ **Note:** Audio requires specific hardware (I2S microphone and speaker). Voice quality is very low-bandwidth — think "understandable radio voice," not phone-call quality.

### Remote Hardware Module

GPIO control over the mesh network. Allows a remote node to read or write GPIO pins on another node — useful for activating relays, reading switches, or controlling external hardware from a distance.

| Настройка            | Описание                                                        |
| -------------------- | --------------------------------------------------------------- |
| Включено             | Activate remote GPIO access                                     |
| Allow Undefined Pins | Allow access to any GPIO pin (security risk) |

> ⚠️ **Warning:** Enabling "Allow Undefined Pins" gives remote nodes access to all GPIO pins, which could interfere with the radio's own hardware. Only enable on dedicated GPIO nodes.

### Neighbor Info Module

Broadcasts information about directly heard neighbors, enabling mesh topology mapping. Each enabled node periodically shares a list of the other nodes it can hear and their signal quality.

| Настройка                              | Описание                             |
| -------------------------------------- | ------------------------------------ |
| Включено                               | Activate neighbor broadcasting       |
| Update Interval (s) | How often to broadcast neighbor list |

See [Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting Module

Controls onboard NeoPixel or other addressable RGB LEDs on supported hardware. Can be used for visual status indicators, notification lights, or decorative effects.

| Настройка            | Описание                                                   |
| -------------------- | ---------------------------------------------------------- |
| Включено             | Activate LED control                                       |
| Состояние светодиода | On, Off, or set specific color                             |
| Red / Green / Blue   | Individual color channel values (0–255) |

### Detection Sensor Module

Turns your node into a motion or door sensor alert system. When a GPIO pin detects a state change (motion detected, door opened), the node broadcasts an alert message over the mesh.

| Настройка                                | Описание                                                                |
| ---------------------------------------- | ----------------------------------------------------------------------- |
| Включено                                 | Activate detection sensor                                               |
| Пин датчика                              | GPIO pin connected to sensor                                            |
| Detection Triggered High                 | Trigger when pin goes high (vs. low) |
| Minimum Broadcast (s) | Minimum time between alert broadcasts                                   |
| State Broadcast (s)   | Periodic state broadcast interval                                       |
| Отправлять 🔔                            | Include bell character in alerts                                        |
| Имя датчика                              | Custom name for this sensor                                             |

### Paxcounter Module

People counter using WiFi and BLE probe requests. Counts nearby devices by passively listening for probe requests that phones and laptops emit when scanning for networks. Available only on ESP32 devices.

| Настройка                              | Описание                   |
| -------------------------------------- | -------------------------- |
| Включено                               | Activate people counting   |
| Update Interval (s) | How often to report counts |

> 💡 **Tip:** Paxcounter is useful for estimating foot traffic at trailheads, event venues, or other locations. Counts are approximate — one person may carry multiple devices.

### TAK Module

Team Awareness Kit integration for interoperability with ATAK and WinTAK. See [TAK Integration](tak) for detailed setup and usage.

## Администрирование

### Удаленное администрирование

Remotely configure nodes that share your admin key:

1. Select the target node in the node list.
2. Navigate to **Settings** for that node.
3. Modify configuration.
4. Tap **Save** — changes are sent over the mesh.

> ⚠️ **Requires:** Admin key configured on both your node and the target node.

### Очистить базу данных нод

Removes stale nodes from your local database that haven't been heard in a configurable time window.

### Factory Reset

Resets all settings to factory defaults. **This cannot be undone.**

### Перезагрузка

Remotely reboot a connected or administered node.

### Панель отладки

View detailed diagnostic information:

- Protocol buffers debug output
- Mesh packet log
- Connection state details

### Troubleshooting Remote Admin

- **"No response from target node"** — the target may be out of range, offline, or have a mismatched admin key. Verify the admin key matches on both nodes.
- **Changes not applying** — some settings require a reboot to take effect. Try the Reboot action after saving.
- **Can't see remote settings** — ensure your node has the admin key for the target node. The admin channel is configured automatically when an admin key is set.

## Related Topics

- [Settings — Radio & User](settings-radio-user) — core radio and user profile settings
- [Module configuration reference](https://meshtastic.org/docs/configuration/module) — detailed module docs on meshtastic.org
- [FAQ](https://meshtastic.org/docs/about/faq) — common questions on meshtastic.org

---

