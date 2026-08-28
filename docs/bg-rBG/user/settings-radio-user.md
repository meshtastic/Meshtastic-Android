---
title: Settings — Radio & User
parent: Ръководство за потребители
nav_order: 7
last_updated: 2026-08-27
description: Configure your radio hardware, LoRa presets, user profile, position sharing, power management, and security.
aliases:
  - настройки
  - radio-config
  - user-config
  - lora
---

# Settings — Radio & User

Configure your radio hardware and user identity parameters.

## Потребителски настройки

### User Profile

| Настройка         | Описание                                                                                                                                                                                                                                     |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Дълго име         | Your display name (up to 39 characters)                                                                                                                                                                                   |
| Кратко име        | 4-character abbreviated name                                                                                                                                                                                                                 |
| Без съобщения     | Marks the node as one nobody should try to message — for an unmonitored or infrastructure node. Other clients hide it from the contact list. Needs supporting firmware                                       |
| Licensed Operator | Enable if you hold an amateur radio license (permits higher power). Turning it on relabels **Long Name** as **Call Sign** and adds a separate Long Name field, and is staged behind a confirmation dialog |

### Applying Changes

After modifying settings, tap **Save** to write the configuration to your radio. The device may reboot to apply changes.

## Конфигурация

### Конфигуриране на устройството

| Настройка                                  | Описание                                                                                                                                                                               | По подразбиране |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------- |
| Роля                                       | Node behavior (Client, Router, etc.) — each option carries its own description in the picker. Choosing Router asks for confirmation | Клиент          |
| Режим на препредаване                      | How the node retransmits messages; each mode is described in the picker                                                                                                                | Всички          |
| Node Info Broadcast (s) | Interval for broadcasting node info                                                                                                                                                    | 10800           |
| Double-tap Button                          | Treat a double tap as a button press                                                                                                                                                   | Disabled        |
| Triple Click Ad Hoc Ping                   | Send an ad-hoc position ping on a triple click                                                                                                                                         | Disabled        |
| LED Heartbeat                              | Blink the status LED periodically                                                                                                                                                      | Активиран       |
| Часова зона                                | POSIX time-zone string for the device clock, with buttons to copy your phone's zone or clear it                                                                                        | —               |
| Button / Buzzer GPIO                       | Advanced: which pins the button and buzzer are wired to                                                                                                                | —               |

### Конфигуриране на LoRa

| Настройка                                      | Описание                                                                                                                                                                                         | По подразбиране                           |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------- |
| Регион                                         | Regulatory region for frequency bands                                                                                                                                                            | Unset (must configure) |
| Предварително настроен модем                   | Speed/range tradeoff                                                                                                                                                                             | LongFast                                  |
| Лимит на отскоци                               | Maximum retransmit hops                                                                                                                                                                          | 3                                         |
| TX Power                                       | Transmission power (dBm); 0 = max allowed for region                                                                                                                          | 0 (region max)         |
| Frequency Override                             | Overrides the computed operating frequency outright (MHz). It does not offset the calculated value — leave at 0 unless you know you need a specific frequency | 0 (use calculated)     |
| Channel Bandwidth                              | Bandwidth setting                                                                                                                                                                                | Default for preset                        |
| Използване на предварително зададени настройки | On by default. Turn it off to set Spread Factor, Coding Rate and Bandwidth by hand instead of taking them from the modem preset                                                  | On                                        |
| Spread Factor                                  | Manual mode only: 7–12. Higher spreads further but slower                                                                                                        | From preset                               |
| Coding Rate                                    | Manual mode only: 5–8. More redundancy costs airtime                                                                                                             | From preset                               |
| Честотен слот                                  | Which slot within the region's band to use. 0 derives it from the primary channel name                                                                                           | 0 (automatic)          |
| Предаването е активирано                       | Turning this off makes the node receive-only                                                                                                                                                     | On                                        |
| Override Duty Cycle                            | Ignore the region's duty-cycle limit. Only legal where you are permitted to                                                                                                      | Off                                       |
| Игнориране на MQTT                             | Drop packets that arrived from MQTT rather than over the air                                                                                                                                     | Off                                       |
| OK to MQTT                                     | Allow your packets to be forwarded to MQTT by gateways                                                                                                                                           | Off                                       |
| RX Boosted Gain                                | Extra receive gain on SX126x radios; costs a little current                                                                                                                                      | Off                                       |
| PA fan disabled                                | Turn off the power-amplifier fan on hardware that has one                                                                                                                                        | Off                                       |

> ⚠️ **Important:** You **must** set your region before transmitting. Operating without the correct region may violate local radio regulations. See the [region configuration guide](https://meshtastic.org/docs/getting-started/initial-config) on meshtastic.org for details.

### Modem Presets

> 💡 **Tip:** The **SNR Limit** values are negative on purpose. LoRa can decode signals _below_ the noise floor, so a more-negative limit means the preset tolerates a weaker, noisier signal (more range). See [How the Signal Meter Works](signal-meter) for the full explanation.

| Preset             | Диапазон                | Скорост                   | SNR Limit                | Best For                                                                                                 |
| ------------------ | ----------------------- | ------------------------- | ------------------------ | -------------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 km   | 21.9 kbps | −7.5 dB  | Dense urban with line-of-sight; data-heavy applications                                                  |
| Short Fast         | ~3 km   | 10.9 kbps | −7.5 dB  | Urban neighborhoods; buildings within a few blocks                                                       |
| Short Slow         | ~5 km   | 5.5 kbps  | −10 dB                   | Suburban short-range; moderate building density                                                          |
| Medium Fast        | ~5 km   | 5.5 kbps  | −12.5 dB | Suburban areas; moderate building density                                                                |
| Medium Slow        | ~8 km   | 1.1 kbps  | −15 dB                   | Suburban/rural; moderate range with slower speed                                                         |
| Long Turbo         | ~10 km  | 4.4 kbps  | −12.5 dB | Similar range to Long Fast but with 500 kHz bandwidth; faster throughput                                 |
| Long Fast          | ~10 km  | 1.1 kbps  | −17.5 dB | **General use (default)** — balanced range and speed                                  |
| Long Moderate      | ~20 km  | 0.34 kbps | −17.5 dB | Rural with some terrain; occasional use                                                                  |
| Lite Fast          | ~5 km   | 5.5 kbps  | −12.5 dB | EU 866 MHz SRD band (125 kHz BW); comparable to Medium Fast                           |
| Lite Slow          | ~10 km  | 1.1 kbps  | −15 dB                   | EU 866 MHz SRD band (125 kHz BW); comparable to Long Fast                             |
| Narrow Fast        | ~5 km   | 2.7 kbps  | −10 dB                   | EU 868 MHz band (62.5 kHz BW); avoids interference with other devices |
| Narrow Slow        | ~10 km  | 1.1 kbps  | −12.5 dB | EU 868 MHz band (62.5 kHz BW); comparable to Long Fast                |
| ~~Long Slow~~      | ~30 km  | 0.18 kbps | −20 dB                   | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                     |
| ~~Very Long Slow~~ | ~40+ km | 0.09 kbps | −20 dB                   | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                     |

> ℹ️ **Note:** This table uses the common short names. In the app's preset dropdown they read as **Short Range - Fast**, **Long Range - Fast**, **Lite - Fast**, **Narrow - Fast**, and so on.

#### Choosing a Modem Preset

The modem preset controls the fundamental tradeoff between **range** and **data rate**:

- **Slower presets** use more spreading, making signals decodable at weaker signal levels (lower SNR limit). This means longer range but fewer bytes per second.
- **Faster presets** pack more data per transmission but require a stronger signal to decode.

**Practical guidance:**

- **Urban mesh (many nodes, short distances):** Use **Long Fast** (default) or **Short Fast**. Higher speed means less airtime congestion when many nodes share the channel.
- **Rural/sparse mesh (few nodes, long distances):** Use **Long Moderate**. Range matters more than speed when nodes are far apart.
- **EU 866/868 MHz regulatory compliance:** Use **Lite Fast**, **Lite Slow**, **Narrow Fast**, or **Narrow Slow** — these are optimized for the EU SRD/868 MHz bands with narrower bandwidths.
- **Fixed infrastructure links:** Use **Short Turbo** or **Long Turbo** for dedicated point-to-point links with good antennas and line-of-sight.
- **Mixed environments:** Stick with **Long Fast** — it's the community default and ensures compatibility with others in your area.

> ⚠️ **Important:** All nodes on the same channel **must** use the same modem preset. Nodes with mismatched presets cannot communicate even if they share the same frequency and encryption key.

> 💡 **Tip:** The range estimates above assume flat terrain and modest antennas. Elevation advantage (hilltop, rooftop) dramatically increases effective range. A well-placed Router with Long Fast can often outperform a ground-level node with Long Slow.

### Конфигуриране на дисплея

These control the **radio's own screen**, not the app's.

| Настройка                            | Описание                                                                                                                                                  |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Екранът е включен за                 | How long the display stays lit before sleeping                                                                                                            |
| Carousel interval                    | How often the device cycles between screens on its own                                                                                                    |
| Режим на дисплея                     | Screen layout/density used by the firmware                                                                                                                |
| Показвани единици                    | Metric or Imperial on the device's screen                                                                                                                 |
| Използване на 12ч формат             | Show the device clock as 12-hour rather than 24-hour                                                                                                      |
| Bold heading                         | Draw the screen's heading text in bold                                                                                                                    |
| Обръщане на екрана                   | Rotate the display 180° for an inverted mounting                                                                                                          |
| Тип на OLED                          | Auto, SSD1306, SH1106, SH1107                                                                                                                             |
| Събуждане при докосване или движение | Light the screen when the device is tapped or moved                                                                                                       |
| Ориентация на компаса                | Rotation offset for the compass rose (0°, 90°, 180°, 270°)                                                                             |
| Always point north                   | Locks the compass rose north-up instead of rotating it with your heading. Independent of Compass orientation — neither replaces the other |

### Конфигуриране на позицията

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Настройка                                           | Описание                                                                                                                                              |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Режим на GPS (физически хардуер) | Three-state: GPS enabled, disabled, or not present. Not a simple on/off                                               |
| GPS Polling Interval                                | How often the radio asks its GPS for a fix                                                                                                            |
| Интервал на излъчване                               | How often the position is shared with the mesh                                                                                                        |
| Интелигентна позиция                                | Broadcast based on movement rather than purely on the clock                                                                                           |
| Smart Interval                                      | With Smart Position on, the shortest gap between broadcasts                                                                                           |
| Smart Distance                                      | With Smart Position on, how far you must move before broadcasting                                                                                     |
| Фиксирана позиция                                   | Use a manually entered latitude, longitude and altitude instead of the GPS                                                                            |
| Position Flags                                      | A group of toggles choosing which fields ride along with a position — altitude, its reference and precision, satellites in view, timestamp, and so on |
| GPS EN / Receive / Transmit GPIO                    | Advanced: the pins the GPS module is wired to                                                                                         |

### Конфигуриране на захранването

| Настройка                                           | Описание                                                        |
| --------------------------------------------------- | --------------------------------------------------------------- |
| Активиране на енергоспестяващ режим                 | Let the radio sleep aggressively between activity               |
| Изключване при загуба на захранване                 | Power the device down after external power disappears           |
| Продължителност на супер дълбок сън                 | How long the deepest sleep state lasts                          |
| Минимално време за събуждане                        | The shortest time the radio stays awake once woken              |
| Wait for Bluetooth duration                         | How long to wait for a phone to connect before sleeping         |
| ADC multiplier override                             | Turn on a manual correction for battery-voltage readings        |
| ADC multiplier override ratio                       | The correction factor itself, used only when the override is on |
| I2C адрес на батерията INA_2XX | Address of an external INA-series power sensor, if fitted       |

### Конфигуриране на мрежата

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Настройка                        | Описание                                                                                                                   |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Wi-Fi е активиран                | Enable the WiFi radio (ESP32 devices)                                                                   |
| SSID                             | Network name to connect to. **Scan WiFi QR code** fills this and the password from a standard WiFi QR code |
| Парола                           | Парола за мрежата                                                                                                          |
| Ethernet е активиран             | Use a wired connection on hardware that has one                                                                            |
| Режим на IPv4                    | DHCP, or a static address configured with the four fields below                                                            |
| Wifi IP / Subnet / Gateway / DNS | The static address, only used when IPv4 mode is static                                                                     |
| UDP broadcasting                 | Share mesh traffic with other nodes over the local network                                                                 |
| NTP сървър                       | Time synchronization server                                                                                                |
| rsyslog сървър                   | Remote logging server                                                                                                      |

![IP address field](../../assets/screenshots/settings_ipv4_field.png)

### Конфигуриране на Bluetooth

| Настройка          | Описание                                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| Bluetooth Enabled  | Enable/disable BLE radio                                                                               |
| Режим на сдвояване | Fixed PIN, Random PIN, or No PIN                                                                       |
| Фиксиран ПИН       | PIN code for pairing. Must be **exactly six digits** — the field rejects anything else |

### Конфигуриране на сигурността

| Настройка                    | Описание                                                                                                                                                                                                       |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Публичен ключ                | Your node's public key (read-only)                                                                                                                                                          |
| Администраторски ключ        | Keys permitted to administer this node remotely — up to three                                                                                                                                                  |
| Частен ключ                  | Your node's private key (handle securely). Shown redacted when you are viewing another node over remote admin — the firmware does not send it                               |
| Регенериране на частния ключ | Issues a new keypair for this node, behind a confirmation. Every peer that knew your old key must learn the new one                                                                            |
| Direct Message Key           | The key used for direct-message encryption                                                                                                                                                                     |
| ~~Admin Channel Enabled~~    | ⚠️ Removed — now configured automatically when an admin key is set                                                                                                                                             |
| Debug Log                    | Output live debug logging over serial/bluetooth                                                                                                                                                                |
| Serial Enabled               | Enable serial console access (moved from Device Config)                                                                                                                                     |
| Управляем режим              | Restrict non-admin channel changes. Only selectable once an Admin Key is set                                                                                                                   |
| Backup Keys                  | Save an encrypted backup of the node's keys on this device (Android only)                                                                                                                   |
| Restore Keys                 | Write the backed-up keys back to the node (available once a backup exists)                                                                                                                  |
| Delete Key Backup            | Remove the stored key backup from this device                                                                                                                                                                  |
| Protection Level             | Packet authenticity — how unsigned or relayed packets are treated: **Strict**, **Balanced**, or **Compatible** (requires supporting firmware; Strict asks for confirmation) |

#### Lockdown Mode

Lockdown encrypts the device's storage and requires a passphrase for each connection. It needs
supporting firmware; the row does not appear otherwise.

Enabling it asks you to set and confirm a passphrase, and to acknowledge that **it locks the debug
(SWD) port on hardware that supports locking**. You can turn lockdown off again at any time with
the passphrase, and a full device erase restores the hardware regardless.

Alongside the passphrase you set the limits that end a session automatically:

| Field                                    | Какво прави                                       |
| ---------------------------------------- | ------------------------------------------------- |
| Boots remaining                          | How many device boots the unlocked state survives |
| Hours until expiry                       | Wall-clock lifetime of the unlocked state         |
| Session cap (minutes) | Maximum length of a single unlocked connection    |

Once active, the row reads _Active — storage encrypted, this connection authenticated_ when
unlocked, or _Active — enter your passphrase to unlock this connection_ when not. **Lock Now**
ends the current session immediately. Repeated wrong passphrases are rate-limited with a
back-off before you can try again.

> ⚠️ **Warning:** There is no passphrase recovery. Losing it means erasing the device to get it
> back, which destroys its keys, channels and settings.

![Password field](../../assets/screenshots/settings_password_field.png)

Settings use standard preference controls — dropdowns, toggles, and sliders:

| Control  | Екранна снимка                                              |
| -------- | ----------------------------------------------------------- |
| Dropdown | ![Dropdown](../../assets/screenshots/settings_dropdown.png) |
| Toggle   | ![Toggle](../../assets/screenshots/settings_switch.png)     |
| Slider   | ![Slider](../../assets/screenshots/settings_slider.png)     |

## Related Topics

- [Settings — Modules & Admin](settings-module-admin) — optional feature modules and device administration
- [Signal Meter](signal-meter) — how modem presets affect signal quality thresholds
- [LoRa configuration](https://meshtastic.org/docs/configuration/radio/lora) — detailed LoRa settings reference on meshtastic.org
- [Initial configuration](https://meshtastic.org/docs/getting-started/initial-config) — region setup guide on meshtastic.org

---

