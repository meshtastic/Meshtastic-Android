---
title: Settings — Radio & User
parent: User Guide
nav_order: 7
last_updated: 2026-09-04
description: Configure your radio hardware, LoRa presets, user profile, position sharing, power management, and security.
aliases:
  - налаштування
  - radio-config
  - user-config
  - lora
---

# Settings — Radio & User

Configure your radio's user identity, region and LoRa parameters, position and power behavior, network and Bluetooth connectivity, and security settings.

## How These Screens Work

Everything here is on the **Settings** screen. **User**, **LoRa**, **Channels** and **Security** are
listed there directly. **Device**, **Position**, **Power**, **Network**, **Display** and
**Bluetooth** are one level down, under **Settings → Device configuration**. **Network** appears
only on radios with Wi-Fi or Ethernet, and **Bluetooth** only on radios with Bluetooth.

Settings use standard preference controls — dropdowns, toggles, and sliders:

| Control  | Screenshot                                                                                                  |
| -------- | ----------------------------------------------------------------------------------------------------------- |
| Dropdown | ![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png) |
| Toggle   | ![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)                        |
| Slider   | ![A slider setting with its current numeric value shown](../../assets/screenshots/settings_slider.png)      |

## User Settings

### User Profile

On **Settings → User**.

| Setting                                           | Опис                                                                                                                                                                                                                                                                                                                                |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Довга назва                                       | Your display name (up to 39 characters)                                                                                                                                                                                                                                                                          |
| Коротка назва                                     | 4-character abbreviated name                                                                                                                                                                                                                                                                                                        |
| Статус повідомлення                               | A short, public free-text status other nodes display alongside your node — up to 80 bytes, cleared with the **✕** in the field. The radio broadcasts it to the mesh when you change it and again every 12 hours. Needs firmware 2.8 or newer, and is absent otherwise               |
| Недоступний для повідомлень                       | Marks the node as one nobody should try to message — for an unmonitored or infrastructure node. Other clients hide it from the contact list. Needs supporting firmware                                                                                                                              |
| Ліцензований радіоаматор (Ham) | Enable if you hold an amateur radio license (permits higher power). Turning it on is staged behind a confirmation dialog. On your own radio it then relabels **Long Name** as **Call sign** and adds a separate Long Name field; over remote admin the field stays **Long Name** |

### Applying Changes

The footer appears as soon as you change something. **Discard** throws the change away, and the other button writes it to the radio: it reads **Save & restart** on the screens the firmware applies with a reboot — Position, Network, Bluetooth, Security, and most module screens — and **Save** everywhere else.

The status message is saved with the same **Save**, but it never reboots the node — and, like the
rest of this screen, it can be edited on a remote node you administer. For your own radio there is a
shortcut while it is connected: touch & hold your node in the [node list](nodes.md) and choose
**Update status**. Older firmware and a disconnected radio have no shortcut — the field above is
still the way in.

## Налаштування

### Налаштування пристрою

On **Settings → Device configuration → Device**.

| Setting                                        | Опис                                                                                                                                                                                                                                                                                                                                        | За замовчуванням |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| Роль пристрою                                  | Node behavior. The picker lists the firmware names (`CLIENT`, `ROUTER`, `ROUTER_LATE`, `TAK`, and so on), and the description of whichever role is selected appears under the field. Choosing `ROUTER` or `ROUTER_LATE` asks you to confirm you have read the device-role guidance first | `CLIENT`         |
| Режим ретрансляції                             | How the node retransmits messages. As with the role, the picker lists the firmware names and describes only the selected one                                                                                                                                                                                                | `ALL`            |
| Інтервал розсилки даних про вузол              | How often the node re-announces itself. A dropdown of fixed intervals — Unset, then 3 to 72 hours — not a value you type in seconds                                                                                                                                                                                         | 3 hours          |
| Подвійний дотик як натискання кнопки           | Treat a double tap as a button press                                                                                                                                                                                                                                                                                                        | Disabled         |
| Швидка перевірка зв'язку потрійним натисканням | Send an ad-hoc position ping on a triple click                                                                                                                                                                                                                                                                                              | Disabled         |
| Частота мигання світлодіоду                    | Blink the status LED periodically                                                                                                                                                                                                                                                                                                           | Увімкнено        |
| Часовий пояс                                   | POSIX time-zone string for the device clock, with buttons to copy your phone's zone or clear it                                                                                                                                                                                                                                             | —                |
| Button / Buzzer GPIO                           | Advanced: which pins the button and buzzer are wired to                                                                                                                                                                                                                                                                     | —                |

### Налаштування LoRa

On **Settings → LoRa**.

| Setting                                    | Опис                                                                                                                                                                                                                                                                                                                              | За замовчуванням                               |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| Регіон                                     | Regulatory region for frequency bands. You must set this before transmitting                                                                                                                                                                                                                                      | Unset (must configure)      |
| Пресети                                    | Speed/range tradeoff                                                                                                                                                                                                                                                                                                              | LongFast                                       |
| К-ть стрибків                              | Maximum retransmit hops                                                                                                                                                                                                                                                                                                           | 3                                              |
| Потужність передачі                        | Transmission power (dBm); 0 = max allowed for region                                                                                                                                                                                                                                                           | 0 (region max)              |
| Перевизначити частоту                      | Overrides the computed operating frequency outright (MHz). It does not offset the calculated value — leave at 0 unless you know you need a specific frequency                                                                                                                                  | 0 (use calculated)          |
| Використовувати пресет                     | On by default. Turn it off to set Spread Factor, Coding Rate and Bandwidth by hand instead of taking them from the modem preset                                                                                                                                                                                   | On                                             |
| Показник розширення сигналу                | Manual mode only: 7–12. Higher spreads further but slower                                                                                                                                                                                                                                         | From preset                                    |
| Швидкість кодування                        | Manual mode only: 5–8. More redundancy costs airtime                                                                                                                                                                                                                                              | From preset                                    |
| Ширина смуги пропускання                   | Manual mode only: the channel bandwidth in kHz, typed in directly. On the 2.4 GHz region the app offers a list of the bandwidths your radio supports instead, and a stored value that is not on that list shows as _Unsupported_ and blocks saving until you pick a supported one | From preset                                    |
| Слот частоти                               | Which slot within the region's band to use. 0 derives it from the primary channel name                                                                                                                                                                                                                            | 0 (automatic)               |
| Передача активована                        | Turning this off makes the node receive-only                                                                                                                                                                                                                                                                                      | On                                             |
| Ігнорувати обмеження завантаженості каналу | Ignores the region's duty-cycle limit. Illegal in most regions; turn it on only where your license permits                                                                                                                                                                                                        | Off                                            |
| Ігнорувати MQTT                            | Drop packets that arrived from MQTT rather than over the air. The firmware turns this on for you whenever you set a region that has a duty-cycle limit — the EU bands, Thailand, and Ukraine 433                                                                                                                  | Off, until you set a duty-cycle-limited region |
| MQTT: Готово               | Allow your packets to be forwarded to MQTT by gateways                                                                                                                                                                                                                                                                            | Off                                            |
| Підсилення передачі                        | Extra receive gain on SX126x radios; costs a little current                                                                                                                                                                                                                                                                       | Off                                            |
| Вентилятор вимкнений                       | Turn off the power-amplifier fan on hardware that has one                                                                                                                                                                                                                                                                         | Off                                            |

Some regions are amateur-radio allocations whose presets only licensed operators may use. On firmware 2.8 or newer the app knows which regions those are and grays the whole **Presets** list out until **Licensed amateur radio (Ham)** is turned on for the node you are configuring; the text under the field says so while it is grayed out.

> ⚠️ **Important:** Operating without the correct region may violate local radio regulations. See the [region configuration guide](https://meshtastic.org/docs/getting-started/initial-config) on meshtastic.org for details.

### Modem Presets

The Lite, Narrow, Medium Turbo, and Tiny presets need firmware 2.8 or newer — the app hides them on older radios.

> 💡 **Tip:** The **SNR Limit** values are negative on purpose. LoRa can decode signals _below_ the noise floor, so a more-negative limit means the preset tolerates a weaker, noisier signal (more range). See [How the Signal Meter Works](signal-meter) for the full explanation.

| Preset             | Range                   | Швидкість                 | SNR Limit                | Best For                                                                                                                                                                                                      |
| ------------------ | ----------------------- | ------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 km   | 21.9 kbps | −7.5 dB  | Dense urban with line-of-sight; data-heavy applications                                                                                                                                                       |
| Short Fast         | ~3 km   | 10.9 kbps | −7.5 dB  | Urban neighborhoods; buildings within a few blocks                                                                                                                                                            |
| Short Slow         | ~5 km   | 6.25 kbps | −10 dB                   | Suburban short-range; moderate building density                                                                                                                                                               |
| Medium Fast        | ~5 km   | 3.52 kbps | −12.5 dB | Suburban areas; moderate building density                                                                                                                                                                     |
| Medium Slow        | ~8 km   | 1.95 kbps | −15 dB                   | Suburban/rural; moderate range with slower speed                                                                                                                                                              |
| Long Turbo         | ~10 km  | 1.34 kbps | −12.5 dB | Similar range to Long Fast but with 500 kHz bandwidth; faster throughput                                                                                                                                      |
| Long Fast          | ~10 km  | 1.1 kbps  | −17.5 dB | **General use (default)** — balanced range and speed                                                                                                                                       |
| Long Moderate      | ~20 km  | 0.34 kbps | −17.5 dB | Rural with some terrain; occasional use                                                                                                                                                                       |
| Lite Fast          | ~5 km   | 1.76 kbps | −12.5 dB | EU 866 MHz SRD band (125 kHz BW); comparable to Medium Fast                                                                                                                                |
| Lite Slow          | ~10 km  | 0.98 kbps | −15 dB                   | EU 866 MHz SRD band (125 kHz BW); comparable to Long Fast                                                                                                                                  |
| Narrow Fast        | ~5 km   | 2.28 kbps | −10 dB                   | EU 868 MHz band (62.5 kHz BW); avoids interference with other devices                                                                                                      |
| Narrow Slow        | ~10 km  | 1.30 kbps | −12.5 dB | EU 868 MHz band (62.5 kHz BW); comparable to Long Fast                                                                                                                     |
| Medium Turbo       | ~5 km   | 7.0 kbps  | −12.5 dB | Like Medium Fast but with 500 kHz bandwidth; not legal in every region. Needs firmware 2.8 or newer                                                                           |
| Tiny Fast          | ~10 km  | 0.68 kbps | −7.5 dB  | Amateur bands that cap occupied bandwidth; these presets use 15.6 kHz. Needs firmware 2.8 or newer, an SX126x or SX127x radio, and a TCXO of ±5 ppm or better |
| Tiny Slow          | ~20 km  | 0.33 kbps | −10 dB                   | Same band restrictions as Tiny Fast, longer range. Same firmware, radio, and TCXO requirements                                                                                                |
| ~~Long Slow~~      | ~30 km  | 0.18 kbps | −20 dB                   | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                                                                                                                          |
| ~~Very Long Slow~~ | ~40+ km | 0.09 kbps | −20 dB                   | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                                                                                                                          |

> ℹ️ **Note:** This table uses the common short names. The app's **Presets** dropdown lists the raw firmware names instead — `SHORT_FAST`, `LONG_FAST`, `LITE_FAST`, `NARROW_FAST`, and so on. Local Mesh Discovery shows the same presets as _Long Fast_ and _Short Turbo_.

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

All nodes on the same channel must use the same modem preset. Nodes with mismatched presets cannot communicate even if they share the same frequency and encryption key.

The range estimates in the [Modem Presets](#modem-presets) table assume flat terrain and modest antennas. Elevation advantage (hilltop, rooftop) dramatically increases effective range. A well-placed Router with Long Fast can often outperform a ground-level node with Long Slow.

### Налаштування дисплею

On **Settings → Device configuration → Display**. These control the **radio's own screen**, not the app's.

| Setting                          | Опис                                                                                                                                                      |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Екран включено для               | How long the display stays lit before sleeping                                                                                                            |
| Інтервал гортання каруселі       | How often the radio cycles between screens on its own                                                                                                     |
| Режим екрану                     | Screen layout/density used by the firmware                                                                                                                |
| Одиниці виміру                   | Metric or Imperial on the radio's screen                                                                                                                  |
| Використовувати 12-г формат часу | Show the radio's clock as 12-hour rather than 24-hour                                                                                                     |
| Жирні заголовки                  | Draw the screen's heading text in bold                                                                                                                    |
| Перевернути екран                | Rotate the display 180° for an inverted mounting                                                                                                          |
| Тип OLED                         | Auto, SSD1306, SH1106, SH1107                                                                                                                             |
| Прокидання дотиком або рухом     | Light the screen when the radio is tapped or moved                                                                                                        |
| Орієнтація компаса               | Rotation offset for the compass rose (0°, 90°, 180°, 270°)                                                                             |
| Завжди вказувати на північ       | Locks the compass rose north-up instead of rotating it with your heading. Independent of Compass orientation — neither replaces the other |

### Position Config

On **Settings → Device configuration → Position**.

> ⚠️ **Important:** Saving this screen always reboots the radio.

| Setting                                          | Опис                                                                                                                                                  |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Режим GPS (Фізичний пристрій) | Three-state: GPS enabled, disabled, or not present. Not a simple on/off                                               |
| Інтервал опитування GPS                          | How often the radio asks its GPS for a fix                                                                                                            |
| Інтервал трансляції                              | How often the position is shared with the mesh                                                                                                        |
| Інтелектуальне передавання позиції               | Broadcast based on movement rather than purely on the clock                                                                                           |
| Інтелектуальний інтервал                         | With Smart Position on, the shortest gap between broadcasts                                                                                           |
| Інтелектуальна дистанція                         | With Smart Position on, how far you must move before broadcasting                                                                                     |
| Фіксована позиція                                | Use a manually entered latitude, longitude and altitude instead of the GPS                                                                            |
| Прапорці місцеположення                          | A group of toggles choosing which fields ride along with a position — altitude, its reference and precision, satellites in view, timestamp, and so on |
| GPS EN / Receive / Transmit GPIO                 | Advanced: the pins the GPS module is wired to                                                                                         |

### Налаштування живлення

On **Settings → Device configuration → Power**.

| Setting                                         | Опис                                                            |
| ----------------------------------------------- | --------------------------------------------------------------- |
| Увімкнути енергоощадний режим                   | Let the radio sleep aggressively between activity               |
| Вимкнути при втраті живлення                    | Power the device down after external power disappears           |
| Тривалість глибокого сну                        | How long the deepest sleep state lasts                          |
| Мінімальний час в робочому режимі               | The shortest time the radio stays awake once woken              |
| Тривалість очікування Bluetooth                 | How long to wait for a phone to connect before sleeping         |
| Корекція множника напруги                       | Turn on a manual correction for battery-voltage readings        |
| Множник корекції напруги                        | The correction factor itself, used only when the override is on |
| I2C адреса INA_2XX батареї | Address of an external INA-series power sensor, if fitted       |

### Налаштування мережі

On **Settings → Device configuration → Network**, on radios with Wi-Fi or Ethernet.

> ⚠️ **Warning:** Turning on **Wi-Fi enabled** or **Ethernet enabled** ends the Bluetooth connection between your phone and the radio. Reconnect over the network afterwards from the [Connections](connections) screen, or turn Wi-Fi off again from the radio's own screen or over USB. Saving this screen also always reboots the radio.

| Setting                           | Опис                                                                                                                                                                                                                                                                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wi-Fi enabled                     | Enable the Wi-Fi radio (ESP32 radios)                                                                                                                                                                                                                                                                                                                  |
| SSID                              | Network name to connect to. Appears only once **Wi-Fi enabled** is on, along with **Password**. **Scan Wi-Fi QR code** fills both from a standard Wi-Fi QR code; on Android, holding the phone against a Wi-Fi NFC tag while this screen is open fills them the same way, and the app offers to open system settings if NFC is turned off |
| Пароль                            | Network password                                                                                                                                                                                                                                                                                                                                                          |
| Ethernet увімкнено                | Use a wired connection on hardware that has one                                                                                                                                                                                                                                                                                                                           |
| Режим IPv4                        | DHCP, or a static address configured with the four fields that follow                                                                                                                                                                                                                                                                                                     |
| Wi-Fi IP / Subnet / Gateway / DNS | The static address, only used when IPv4 mode is static                                                                                                                                                                                                                                                                                                                    |
| UDP broadcasting                  | Share mesh traffic with other nodes over the local network                                                                                                                                                                                                                                                                                                                |
| NTP-сервер                        | Time synchronization server                                                                                                                                                                                                                                                                                                                                               |
| rsyslog-сервер                    | Remote logging server                                                                                                                                                                                                                                                                                                                                                     |

![Network Config with a static IPv4 address entered](../../assets/screenshots/settings_ipv4_field.png)

### Налаштування Bluetooth

On **Settings → Device configuration → Bluetooth**, on radios with Bluetooth.

> ⚠️ **Important:** Saving this screen always reboots the radio.

| Setting              | Опис                                                                                                   |
| -------------------- | ------------------------------------------------------------------------------------------------------ |
| Bluetooth увімкнено  | Enable/disable BLE radio                                                                               |
| Режим створення пари | Fixed PIN, Random PIN, or No PIN                                                                       |
| Фіксований PIN       | PIN code for pairing. Must be **exactly six digits** — the field rejects anything else |

### Налаштування безпеки

On **Settings → Security**. The screen is grouped into cards: **Packet authenticity**, **Direct Message Key** (your node's key pair), **Admin Keys**, **Logs**, and **Administration**.

> ⚠️ **Important:** Saving this screen always reboots the radio.

| Setting                        | Опис                                                                                                                                                                                                                                                       |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Відкритий ключ                 | Your node's public key (read-only)                                                                                                                                                                                                      |
| Ключ адміністратора            | Keys permitted to administer this node remotely — up to three                                                                                                                                                                                              |
| Приватний ключ                 | Your node's private key (handle securely). Shown redacted when you are viewing another node over remote admin — the firmware does not send it                                                                           |
| Згенерувати закритий ключ      | Issues a new keypair for this node, behind a confirmation. Every peer that knew your old key must learn the new one                                                                                                                        |
| ~~Admin Channel Enabled~~      | ⚠️ Removed — now configured automatically when an admin key is set                                                                                                                                                                                         |
| Серійна консоль                | Serial console over the Stream API                                                                                                                                                                                                                         |
| API журналу відладки увімкнено | Output live debug logging over serial, and view and export position-redacted radio logs over Bluetooth                                                                                                                                                     |
| Керований режим                | Restrict non-admin channel changes. Only selectable once an Admin Key is set                                                                                                                                                               |
| Резервні ключі                 | Save an encrypted backup of the node's keys on this phone (Android only, and only for your own node)                                                                                                                                    |
| Restore Keys                   | Write the backed-up keys back to the node (available once a backup exists)                                                                                                                                                              |
| Delete Key Backup              | Remove the stored key backup from this phone                                                                                                                                                                                                               |
| Protection level               | How unsigned or relayed packets are treated: **Strict — Require authentication**, **Balanced — Prefer authenticated**, or **Compatible — Accept unsigned** (requires supporting firmware; Strict asks for confirmation) |

#### Lockdown Mode

Lockdown encrypts the device's storage and requires a passphrase for each connection. It needs
supporting firmware; the row does not appear otherwise.

Enabling it asks you to set and confirm a passphrase, and to acknowledge that **it locks the debug
(SWD) port on hardware that supports locking**. You can turn lockdown off again at any time with
the passphrase, and a full device erase restores the hardware regardless.

Alongside the passphrase you set the limits that end a session automatically:

| Field                                    | What it does                                                                              |
| ---------------------------------------- | ----------------------------------------------------------------------------------------- |
| Boots remaining                          | How many device boots the unlocked state survives                                         |
| Hours until expiry                       | Wall-clock lifetime of the unlocked state                                                 |
| Session cap (minutes) | A per-boot uptime cap on the unlocked state. 0, the default, means no cap |

Once active, the row reads _Active — storage encrypted, this connection authenticated_ when
unlocked, or _Active — enter your passphrase to unlock this connection_ when not. **Lock Now**
ends the current session immediately. Repeated wrong passphrases are rate-limited with a
back-off before you can try again.

> ⚠️ **Warning:** There is no passphrase recovery. Losing it means erasing the device to get it
> back, which destroys its keys, channels and settings.

## Related Topics

- [Settings — Modules & Admin](settings-module-admin) — optional feature modules and device administration
- [Signal Meter](signal-meter) — how modem presets affect signal quality thresholds
- [LoRa configuration](https://meshtastic.org/docs/configuration/radio/lora) — detailed LoRa settings reference on meshtastic.org
- [Initial configuration](https://meshtastic.org/docs/getting-started/initial-config) — region setup guide on meshtastic.org
