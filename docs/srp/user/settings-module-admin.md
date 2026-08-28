---
title: Settings — Modules & Admin
parent: User Guide
nav_order: 8
last_updated: 2026-08-27
description: Configure optional feature modules (MQTT, telemetry, canned messages, TAK, and more) and perform device administration.
aliases:
  - modules
  - module-config
  - administration
---

# Settings — Modules & Admin

Configure optional feature modules and perform device administration. Modules extend Meshtastic with specialized capabilities — each can be independently enabled or disabled.

> 💡 **Tip:** You only need to enable the modules you actually use. Disabling unused modules reduces airtime, saves battery, and simplifies your configuration.

Module settings use a card-based layout with toggle switches, dropdowns, text fields, and sliders:

![Toggle switch](../../assets/screenshots/settings_switch.png)

![Dropdown selector](../../assets/screenshots/settings_dropdown.png)

![Text field](../../assets/screenshots/settings_text_field.png)

![Settings card layout](../../assets/screenshots/settings_titled_card.png)

## Конфигурација модула

### MQTT Module

Bridges mesh messages to and from an MQTT broker for internet connectivity. This is how you extend your mesh beyond radio range or integrate with home automation systems.

| Setting                  | Опис                                                                                                                                                                                      |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Омогућено                | Toggle MQTT bridge                                                                                                                                                                        |
| Сервер                   | MQTT broker address                                                                                                                                                                       |
| Корисничко име           | Authentication username                                                                                                                                                                   |
| Лозинка                  | Authentication password                                                                                                                                                                   |
| Encryption               | Encrypt MQTT payloads                                                                                                                                                                     |
| JSON Output              | Publish and consume MQTT messages as JSON. Marked deprecated in the protobuf schema, but it is still the only toggle for this behaviour and the firmware still honours it |
| TLS                      | Use secure connection                                                                                                                                                                     |
| Корен тема               | Base MQTT topic path                                                                                                                                                                      |
| Proxy to client enabled  | Let a connected phone carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                       |
| MQTT proxy on this phone | The phone-side half of the above: whether _this_ phone is currently acting as that relay. See [MQTT](mqtt)                                                |
| Извештај мапе            | Publish position to the public map — see below                                                                                                                                            |

**Map Report** expands into its own group:

| Setting            | Опис                                                                                                                            |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| Омогућено          | Publish to the public map at all                                                                                                |
| Share location     | Explicit consent to include your position. Map reporting will not save without it                               |
| Position precision | How coarsely your position is published                                                                                         |
| Publish interval   | How often to report. Must be **at least 3600 s (1 hour)** — the app blocks saving below that |

See [MQTT](mqtt) for a detailed usage guide including encryption, privacy, and broker setup.

### Serial Module

Enables serial port communication for external device integrations (GPS modules, sensors, or custom hardware). When enabled, the node's serial port can send and receive protobuf or text data, allowing external microcontrollers or computers to interact with the mesh.

| Setting                      | Опис                                                                                                                                                                                                |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Serial enabled               | Activate serial communication                                                                                                                                                                       |
| Echo enabled                 | Echo received serial data back                                                                                                                                                                      |
| Serial mode                  | Which protocol the port speaks — Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, VE.Direct, MeshSolar config, Log, or Log (text only) |
| RX / TX                      | GPIO pins for the serial connection                                                                                                                                                                 |
| Serial baud rate             | Port speed                                                                                                                                                                                          |
| Временско ограничење         | How long to wait before considering an incoming message complete                                                                                                                                    |
| Override console serial port | Take over the port the debug console normally uses                                                                                                                                                  |

### External Notification Module

Controls buzzer, LED, or vibration alerts on your radio hardware. Useful for devices that need to physically signal when a message arrives — particularly helpful for unattended or outdoor installations.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles.

| Setting                                           | Опис                                                                                                |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| External notification enabled                     | Master toggle for the module                                                                        |
| Alert message LED / buzzer / vibra                | Which outputs fire on an incoming message                                                           |
| Alert bell LED / buzzer / vibra                   | Which outputs fire on a received bell character                                                     |
| Output LED (GPIO)              | Pin the LED is wired to                                                                             |
| Output LED active high                            | Whether the LED pin is active high or low                                                           |
| Output buzzer (GPIO)           | Pin the buzzer is wired to                                                                          |
| Output vibra (GPIO)            | Pin the vibration motor is wired to                                                                 |
| Use PWM buzzer                                    | Drive the buzzer with PWM, which allows tones rather than a single pitch                            |
| Use I2S as buzzer                                 | Send the alert through an I2S audio output instead                                                  |
| Output duration (milliseconds) | How long a single alert lasts                                                                       |
| Nag timeout (seconds)          | Keep repeating the alert for this long until it is acknowledged. 0 disables nagging |
| Мелодија звона                                    | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file              |

### Store & Forward Module

Buffers messages for nodes that were temporarily offline, then replays them when those nodes reconnect. Essential for meshes where nodes go in and out of range regularly — ensures messages aren't lost during brief disconnections.

| Setting                                    | Опис                                                                                                                                             |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Омогућено                                  | Activate store and forward                                                                                                                       |
| Heartbeat                                  | Periodically announce this node's store-and-forward capability                                                                                   |
| Records                                    | Maximum stored messages                                                                                                                          |
| History Return (max)    | Max messages to replay                                                                                                                           |
| History Return (window) | Time window for replay                                                                                                                           |
| Сервер                                     | Act as a store-and-forward server for the mesh (requires ample memory, e.g. ESP32 with PSRAM) |

> 💡 **Tip:** Store and Forward works best on nodes with ample memory (ESP32 with PSRAM). Router nodes are ideal candidates since they're typically always-on.

### Range Test Module

> ⚠️ **Warning:** Range Test only works on a secured primary channel. While your primary channel
> still uses the default public key, the Enabled, Interval and Save-CSV controls stay disabled, and
> saving force-disables the module if the channel has reverted to public.

Automated range testing tool for evaluating link quality between nodes. When enabled, the node periodically transmits test messages with incrementing counters. A receiver node logs these messages, allowing you to walk or drive away and later analyze at what distance messages stopped arriving.

| Setting                                | Опис                              |
| -------------------------------------- | --------------------------------- |
| Омогућено                              | Activate range testing            |
| Sender Interval (s) | Time between test transmissions   |
| Save CSV                               | Log received test data to SD card |

### Telemetry Module

Controls what telemetry data your node shares with the mesh. Telemetry includes device health (battery, uptime) and environmental sensor data (temperature, humidity, pressure).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely.

| Setting                               | Опис                                                                                                                                                                              |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Send Device Telemetry                 | Master toggle for device metrics. Only shown on firmware 2.7.12 and newer                                                         |
| Device metrics update interval        | How often to report battery, uptime and channel utilisation                                                                                                                       |
| Environment metrics module enabled    | Report the attached environment sensors                                                                                                                                           |
| Environment metrics update interval   | How often to report them                                                                                                                                                          |
| Environment metrics on-screen enabled | Also show these readings on the device's own display                                                                                                                              |
| Environment metrics use Fahrenheit    | Use °F on the device's display. This is the radio's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale) |
| Air quality metrics module enabled    | Report particulate and CO₂ sensor data                                                                                                                                            |
| Air quality metrics update interval   | How often to report them                                                                                                                                                          |
| Power metrics module enabled          | Report the per-channel voltage and current readings                                                                                                                               |
| Power metrics update interval         | How often to report them                                                                                                                                                          |
| Power metrics on-screen enabled       | Also show power readings on the device's display                                                                                                                                  |

See [Telemetry & Sensors](telemetry-and-sensors) for supported sensors and configuration recommendations.

### Canned Message Module

Pre-configured messages accessible from the device's physical buttons (for radios with rotary encoders, keypads, or similar input hardware). Define a list of quick-send messages that can be transmitted without a phone connected — ideal for field use.

| Setting                                   | Опис                                                                                                      |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| ~~Canned message enabled~~                | ⚠️ **Deprecated** in the protobuf schema                                                                  |
| Поруке                                    | Newline-separated list of messages                                                                        |
| Send bell                                 | Send a bell character alongside the message, so a receiving node's External Notification module can sound |
| Rotary encoder enabled                    | Use a rotary encoder as the input device                                                                  |
| GPIO pin for rotary encoder A / B / press | The three pins the encoder is wired to                                                                    |
| Generate input event on press / CW / CCW  | Which key event each encoder action produces                                                              |
| Up/Down/Select input enabled              | A separate, simpler input scheme using up/down/select buttons rather than an encoder                      |
| ~~Allow input source~~                    | ⚠️ **Deprecated** in the protobuf schema                                                                  |

### Audio Module

Codec2 audio support for low-bandwidth voice communication over the mesh. This is an **experimental** feature that encodes voice into very small data packets using the Codec2 codec.

| Setting                            | Опис                                 |
| ---------------------------------- | ------------------------------------ |
| Омогућено                          | Activate audio module                |
| Codec2 Rate                        | Audio quality/bandwidth tradeoff     |
| PTT Pin                            | GPIO pin for the push-to-talk button |
| I2S Word Select                    | GPIO pin for I2S WS                  |
| I2S Data In                        | GPIO pin for I2S DIN                 |
| I2S Data Out                       | GPIO pin for I2S DOUT                |
| I2S Clock (SCK) | GPIO pin for the I2S bit clock       |

> ℹ️ **Note:** Audio requires specific hardware (I2S microphone and speaker). Voice quality is very low-bandwidth — think "understandable radio voice," not phone-call quality.

### Remote Hardware Module

GPIO control over the mesh network. Allows a remote node to read or write GPIO pins on another node — useful for activating relays, reading switches, or controlling external hardware from a distance.

| Setting              | Опис                                                            |
| -------------------- | --------------------------------------------------------------- |
| Омогућено            | Activate remote GPIO access                                     |
| Allow Undefined Pins | Allow access to any GPIO pin (security risk) |
| Available Pins       | Up to 4 GPIO pins this node exposes for remote read/write       |

> ⚠️ **Warning:** Enabling "Allow Undefined Pins" gives remote nodes access to all GPIO pins, which could interfere with the radio's own hardware. Only enable on dedicated GPIO nodes.

### Neighbor Info Module

Broadcasts information about directly heard neighbors, enabling mesh topology mapping. Each enabled node periodically shares a list of the other nodes it can hear and their signal quality.

| Setting                                | Опис                                                                                                                                 |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Омогућено                              | Activate neighbor broadcasting                                                                                                       |
| Update Interval (s) | How often to broadcast neighbor list                                                                                                 |
| Transmit Over LoRa                     | Also broadcast neighbor info over LoRa, not just MQTT/phone. Unavailable on a channel using the default key and name |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting Module

Controls onboard NeoPixel or other addressable RGB LEDs on supported hardware. Can be used for visual status indicators, notification lights, or decorative effects.

| Setting            | Опис                                                       |
| ------------------ | ---------------------------------------------------------- |
| LED статус         | Turn the LED on or off                                     |
| Струја             | LED current limit (0–31)                |
| Red / Green / Blue | Individual color channel values (0–255) |

### Detection Sensor Module

Turns your node into a motion or door sensor alert system. When a GPIO pin detects a state change (motion detected, door opened), the node broadcasts an alert message over the mesh.

| Setting                                  | Опис                                                                                                                                    |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Омогућено                                | Activate detection sensor                                                                                                               |
| Monitor Pin                              | GPIO pin connected to sensor                                                                                                            |
| Detection Trigger Type                   | How the pin's state maps to a detection event (e.g. active high/low, edge-triggered) |
| Use Input Pullup Mode                    | Enable the pin's internal pull-up resistor                                                                                              |
| Minimum Broadcast (s) | Minimum time between alert broadcasts                                                                                                   |
| State Broadcast (s)   | Periodic state broadcast interval                                                                                                       |
| Пошаљи звоно                             | Include bell character in alerts                                                                                                        |
| Friendly Name                            | Custom name for this sensor                                                                                                             |

### Paxcounter Module

People counter using WiFi and BLE probe requests. Counts nearby devices by passively listening for probe requests that phones and laptops emit when scanning for networks. Available only on ESP32 devices.

| Setting                                | Опис                                                                                                             |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Омогућено                              | Activate people counting                                                                                         |
| Update Interval (s) | How often to report counts                                                                                       |
| WiFi RSSI threshold                    | Ignore WiFi probes weaker than this, so distant devices are not counted (defaults to −80 dBm) |
| BLE RSSI threshold                     | The same cut-off for BLE advertisements (defaults to −80 dBm)                                 |

> 💡 **Tip:** Paxcounter is useful for estimating foot traffic at trailheads, event venues, or other locations. Counts are approximate — one person may carry multiple devices.

### Status Message Module

Publishes a short free-text status line for your node, which other nodes can display alongside it.

| Setting                  | Опис                                                                                                                                                                             |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| The actual status string | Up to 80 characters. The **✕** in the field clears it. (That is the app's own label for the field, verbatim.) |

Saving takes effect immediately — this is one of the few module settings that never asks the
node to reboot.

> ℹ️ **Note:** The screen only appears for firmware that reports support for the status-message
> module. If you do not see it in the module list, your node's firmware does not have it.

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. See
[Local Mesh Discovery](discovery) for the full walkthrough.

### TAK Module

> ℹ️ **Note:** This module only appears in the list once the node's **Device Role** (Device Config)
> is set to **TAK** or **TAK Tracker**. Change the role first, or the entry will not be there.

Team Awareness Kit integration for interoperability with ATAK and WinTAK. See [TAK Integration](tak) for detailed setup and usage.

## Администрација

### Удаљена администрација

Remotely configure nodes that share your admin key:

1. Select the target node in the node list.
2. Navigate to **Settings** for that node.
3. Modify configuration.
4. Tap **Save** — changes are sent over the mesh.

> ⚠️ **Requires:** Admin key configured on both your node and the target node.

### Clean Node Database

Prunes your local node database. Two independent controls:

- An **age slider** — remove nodes not heard from within that window.
- **Clean unknown nodes only** — restrict the purge to nodes that never sent their user info,
  leaving named nodes alone regardless of age.

### Ресетовање на фабричка подешавања

Resets all settings to factory defaults. **This cannot be undone.**

### Поновно покретање

Remotely reboot a connected or administered node.

### Панел за отклањање грешака

Opens the **Packets** and **App logs** tabs for viewing, filtering, and exporting diagnostic output. See [Debug Logs](debug-logs) for the full walkthrough.

### О

**Settings → About** carries the app's own identity rather than the radio's:

Three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one, then the GitHub repository, the running app version, and
  **Acknowledgements** (below).
- **Project information** — links to the website and to this documentation.

### Acknowledgements

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries. It was previously called the license screen.

### Troubleshooting Remote Admin

- **"No response from target node"** — the target may be out of range, offline, or have a mismatched admin key. Verify the admin key matches on both nodes.
- **Changes not applying** — some settings require a reboot to take effect. Try the Reboot action after saving.
- **Can't see remote settings** — ensure your node has the admin key for the target node. The admin channel is configured automatically when an admin key is set.

## Related Topics

- [Settings — Radio & User](settings-radio-user) — core radio and user profile settings
- [Module configuration reference](https://meshtastic.org/docs/configuration/module) — detailed module docs on meshtastic.org
- [FAQ](https://meshtastic.org/docs/faq/) — common questions on meshtastic.org

---

