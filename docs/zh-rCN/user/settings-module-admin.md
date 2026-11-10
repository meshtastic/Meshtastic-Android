---
title: Settings — Modules & Admin
nav_order: 8
last_updated: 2026-10-28
description: Configure optional feature modules (MQTT, telemetry, canned messages, TAK, and more) and perform device administration.
aliases:
  - modules
  - module-config
  - administration
---

# Settings — Modules & Admin

Configure optional feature modules and perform device administration. Modules extend Meshtastic with specialized capabilities — each can be independently enabled or disabled.

Enable only the modules you use. Disabling unused modules reduces airtime, saves battery, and simplifies your configuration. A module you expect can be missing for three reasons: your node's role doesn't enable it, your firmware is older than the release that added it, or the firmware build excludes it for this hardware.

Module settings use a card-based layout with toggle switches, dropdowns, text fields, and sliders:

![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)

![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png)

![A text field setting with a value entered](../../assets/screenshots/settings_text_field.png)

![A module settings card with its title and grouped controls](../../assets/screenshots/settings_titled_card.png)

## 模块设定

Every module lives under **Settings → Module configuration**.

Saving a module screen restarts the node: the button reads **Save & restart**, and the node is unreachable for a few seconds afterwards. External Notification and Mesh Beacon are the exceptions: their button reads **Save**. External Notification may still restart the node for some changes, while a Mesh Beacon change applies without a restart.

### MQTT module

Bridges mesh messages to and from an MQTT broker for internet connectivity. This is how you extend your mesh beyond LoRa range or integrate with home automation systems.

| Setting                | 说明                                                                                                                                                                                                                                                                               | 默认                    |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| 地址                     | MQTT broker address                                                                                                                                                                                                                                                              | `mqtt.meshtastic.org` |
| 启用加密                   | Publish packets still encrypted with the channel key                                                                                                                                                                                                                             | On                    |
| 启用JSON输出               | Publish and consume MQTT messages as JSON. Shown only for a node on firmware older than 2.8, which removed JSON support, or while the setting is still on                                                                                        | 关闭                    |
| Map Reporting          | Publish position to the public map — see the map reporting card that follows                                                                                                                                                                                                     | 关闭                    |
| 启用客户端代理                | Let the connected app carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                                                                                                              | 关闭                    |
| 启用MQTT                 | Turn the MQTT bridge on                                                                                                                                                                                                                                                          | 关闭                    |
| MQTT proxy in this app | The app-side half of **MQTT Client Proxy**: whether this app acts as that relay. It shows whether the relay is running, and can be turned on only while **MQTT Enabled** and **MQTT Client Proxy** are both on. See [MQTT](mqtt) | —                     |
| 密码                     | Authentication password                                                                                                                                                                                                                                                          | The public broker's   |
| 根主题                    | Base MQTT topic path                                                                                                                                                                                                                                                             | `msh`                 |
| 启用 TLS                 | Use a secure connection                                                                                                                                                                                                                                                          | 关闭                    |
| 用户名称                   | Authentication username                                                                                                                                                                                                                                                          | The public broker's   |

Turning **Map Reporting** on reveals a consent card headed _Consent to Share Unencrypted Node Data
via MQTT_, with an **I agree.** switch under it. The rest of the card doesn't exist on screen
until you agree:

| Setting          | 说明                                                                                                                                                                                       |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 我同意。             | Explicit consent to transmit your node data, including an approximate position, unencrypted. Map reporting doesn't start without it                                      |
| 地图发布间隔           | How often to report. A dropdown of fixed intervals from 1 hour to 72 hours — nothing shorter is offered                                                                  |
| Precision slider | The slider has no label of its own. It sets how coarsely your position is published, from 12 to 15; the line beneath reads _±_ the resulting distance, in your own units |

See [MQTT](mqtt) for a detailed usage guide including encryption, privacy, and broker setup.

### Serial module

Enables serial port communication for external device integrations (GPS modules, sensors, or custom hardware). When enabled, the node's serial port can send and receive protobuf or text data, allowing external microcontrollers or computers to interact with the mesh.

| Setting                                                                                          | 说明                                                                                                                                                                                                                                  | 默认 |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -- |
| 串口波特率                                                                                            | Port speed, from **110 Baud** to **921600 Baud**                                                                                                                                                                                    | 默认 |
| 启用Echo                                                                                           | Echo packets you send back over the serial port                                                                                                                                                                                     | 关闭 |
| 串口模式                                                                                             | Which protocol the port speaks: **Default**, **Simple**, **Protobufs**, **Text Message**, **NMEA Positions**, or **CalTopo**, plus `WS85`, `VE_DIRECT`, `MS_CONFIG`, `LOG` and `LOGTEXT` under their firmware names | 默认 |
| 覆盖控制台串口端口                                                                                        | Take over the port the debug console normally uses                                                                                                                                                                                  | 关闭 |
| Receive data (rxd) GPIO pin / Transmit data (txd) GPIO pin | GPIO pins for the serial connection                                                                                                                                                                                                 | 0  |
| 启用串口                                                                                             | Turn serial communication on                                                                                                                                                                                                        | 关闭 |
| 超时                                                                                               | How long to wait before treating received data as a complete packet. 0 uses 250 ms                                                                                                                                  | 0  |

### External Notification module

Controls buzzer, LED, or vibration alerts on your node hardware. Useful for devices that need to physically signal when a message arrives — particularly helpful for unattended or outdoor installations.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles. The
firmware turns the module on, and picks the outputs, on hardware that has a buzzer, vibration motor
or notification LED; elsewhere everything starts off.

| Setting                                                                                                              | 说明                                                                                                                                     |
| -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 输出 LED 活动高                                                                                                           | Whether the LED pin is active high or low. Shown once **Output pin GPIO** is set                                       |
| Alert when receiving a bell / Alert GPIO buzzer when receiving a bell / Alert GPIO vibra motor when receiving a bell | Which outputs fire on a received bell character                                                                                        |
| Alert when receiving a message / Alert GPIO buzzer when receiving a message / Vibra Motor Alert                      | Which outputs fire on an incoming message                                                                                              |
| 启用外部通知                                                                                                               | Master toggle for the module                                                                                                           |
| GPIO Output Duration                                                                                                 | How long a single alert lasts                                                                                                          |
| 超时提醒                                                                                                                 | Keep repeating the alert for this long until it is acknowledged. **Unset** disables nagging                            |
| 输出 LED (GPIO)                                                                                     | Pin the LED is wired to                                                                                                                |
| 输出震动(GPIO)                                                                                        | Pin the buzzer is wired to                                                                                                             |
| 输出振动 (GPIO)                                                                                       | Pin the vibration motor is wired to                                                                                                    |
| 铃声                                                                                                                   | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file                                                 |
| 使用 I2S 作为蜂鸣器                                                                                                         | Send the alert through an I2S audio output instead                                                                                     |
| 使用 PWM 蜂鸣器                                                                                                           | Drive the buzzer with PWM, which allows tones rather than a single pitch. Shown once **Output pin buzzer GPIO** is set |

### Store & Forward module

Buffers messages for nodes that were temporarily offline, then replays them when those nodes reconnect. Essential for meshes where nodes go in and out of range regularly — ensures messages aren't lost during brief disconnections. A server needs an ESP32 with PSRAM or a Linux `meshtasticd` node. A node in the Router or Router Late role acts as a server even with the server setting off.

| Setting                            | 说明                                                                                 | 默认 |
| ---------------------------------- | ---------------------------------------------------------------------------------- | -- |
| 历史记录最大返回值                          | Most stored messages returned per request. 0 uses 25               | 0  |
| 历史记录返回窗口                           | How far back, in minutes, stored messages are returned. 0 uses 240 | 0  |
| 记录数                                | Most messages to store. 0 uses three quarters of the free PSRAM    | 0  |
| 心跳                                 | Periodically announce that this node is a store-and-forward server                 | 关闭 |
| 服务器                                | Act as a store-and-forward server for the mesh                                     | 关闭 |
| 存储 &amp; 已启用转发 | Turn store and forward on                                                          | 关闭 |

### Range Test module

Automated range testing tool for evaluating link quality between nodes. When enabled, the node periodically transmits test messages with incrementing counters. A receiver node logs these messages, allowing you to walk or drive away and later analyze at what distance messages stopped arriving.

Range Test only works on a secured primary channel. As long as your primary channel still uses the
default channel key, the interval and CSV controls stay disabled — you can still switch an
already-enabled module off — and saving force-disables the module if the channel has reverted to
public.

| Setting                                | 说明                                                                                                                  | 默认  |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | --- |
| 启用范围测试                                 | Turn range testing on                                                                                               | 关闭  |
| 保存 CSV 到存储 (仅ESP32) | Log received test data as a CSV on the node's own filesystem. ESP32 nodes only                      | 关闭  |
| 发送间隔                                   | Time between test transmissions, chosen from a dropdown of fixed intervals. **Unset** stops sending | 未设置 |

### Telemetry module

Controls what telemetry data your node shares with the mesh. Telemetry includes device health (battery, uptime) and environmental sensor data (temperature, humidity, pressure).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely. The intervals run from 30 minutes to 72 hours.

| Setting    | 说明                                                                                                                                                                                          | 默认 |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -- |
| 启用空气质量计量模块 | Report particulate and CO₂ sensor data                                                                                                                                                      | 关闭 |
| 空气质量计量更新间隔 | How often to report them                                                                                                                                                                    | —  |
| 发送设备远程数据   | Send device metrics to the mesh. When it is off, they go only to connected clients. Only shown on firmware 2.7.13 and newer | 关闭 |
| 设备计量更新间隔   | How often to report battery, uptime and channel utilization                                                                                                                                 | —  |
| 环境测量值使用华氏度 | Use °F on the device's display. This is the node's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale)            | 关闭 |
| 启用环境计量模块   | Report the attached environment sensors                                                                                                                                                     | 关闭 |
| 环境计量更新间隔   | How often to report them                                                                                                                                                                    | —  |
| 启用电源计量模块   | Report the per-channel voltage and current readings                                                                                                                                         | 关闭 |
| 电量计更新间隔    | How often to report them                                                                                                                                                                    | —  |
| 在屏幕上启用电源指标 | Also show power readings on the device's display                                                                                                                                            | 关闭 |
| 屏幕显示环境指标   | Also show environment readings on the device's display                                                                                                                                      | 关闭 |

See [Telemetry & Sensors](telemetry-and-sensors) for supported sensors and configuration recommendations.

### Canned Message module

Pre-configured messages accessible from the node's physical buttons (for nodes with rotary encoders, keypads, or similar input hardware). Define a list of quick-send messages that can be transmitted without a phone connected — ideal for field use.

| Setting                                                                      | 说明                                                                                                                                                 | 默认 |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | -- |
| Clockwise Rotary Event / Counterclockwise Rotary Event / Encoder Press Event | Which key event each encoder action produces: **None**, **Up**, **Down**, **Left**, **Right**, **Select**, **Back**, or **Cancel** | 无  |
| 消息                                                                           | Newline-separated list of messages                                                                                                                 | 空  |
| Pin A / Pin B / Press Pin                                                    | The three pins the rotary encoder is wired to                                                                                                      | 0  |
| Rotary Encoder                                                               | Use a rotary encoder as the input device                                                                                                           | 关闭 |
| 发送响铃                                                                         | Send a bell character alongside the message, so a receiving node's External Notification module can sound                                          | 关闭 |
| Up/Down/Select Input                                                         | A separate, simpler input scheme using up/down/select buttons rather than an encoder                                                               | 关闭 |

Some boards with built-in encoders or keys come with the input settings filled in. Two older
fields, **Canned message enabled** and **Allow input source**, appear only for a node on firmware
older than 2.7, or while they still hold a value.

### Audio module

Codec2 audio support for low-bandwidth voice communication over the mesh. This is an **experimental** feature that encodes voice into very small data packets using the Codec2 codec. It needs specific hardware, an I2S microphone and speaker, and the voice quality is low.

| Setting             | 说明                                                                                                                     | 默认 |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------- | -- |
| CODEC2 采样率          | Codec2 bitrate, from **450 bps** to **3200 bps**. Lower rates use less bandwidth and give poorer audio | 默认 |
| 启用CODEC2            | Turn the audio module on                                                                                               | 关闭 |
| I2S Clock Pin       | GPIO pin for the I2S bit clock                                                                                         | 0  |
| I2S Data In Pin     | GPIO pin for I2S data in                                                                                               | 0  |
| I2S Data Out Pin    | GPIO pin for I2S data out                                                                                              | 0  |
| I2S Word Select Pin | GPIO pin for I2S word select                                                                                           | 0  |
| PTT 引脚              | GPIO pin for the push-to-talk button. 0 uses GPIO 39                                                   | 0  |

### Remote Hardware module

GPIO control over the mesh network. Allows a remote node to read or write GPIO pins on another node — useful for activating relays, reading switches, or controlling external hardware from a distance.

> ⚠️ **Warning:** Turning on **Allow undefined pin access** gives remote nodes access to all GPIO pins, which could interfere with the node's own hardware. Turn it on only on dedicated GPIO nodes.

| Setting    | 说明                                                              | 默认 |
| ---------- | --------------------------------------------------------------- | -- |
| 允许未定义的引脚访问 | Allow access to any GPIO pin (security risk) | 关闭 |
| 可用引脚       | Up to 4 GPIO pins this node exposes for remote read/write       | 无  |
| 启用远程硬件     | Turn remote GPIO access on                                      | 关闭 |

### Neighbor Info module

Broadcasts information about directly heard neighbors, enabling mesh topology mapping. Each enabled node periodically shares a list of the other nodes it can hear and their signal quality.

| Setting    | 说明                                                                                                                                                   | 默认                                      |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| 启用邻居信息     | Turn neighbor broadcasting on                                                                                                                        | 关闭                                      |
| 通过 LoRa 传输 | Also broadcast neighbor info over LoRa, not just MQTT and the connected app. Unavailable on a channel using the default key and name | 关闭                                      |
| GPS 轮询间隔   | How often to broadcast the neighbor list, in seconds. Minimum 4 hours                                                                | 0 (firmware default) |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting module

Controls onboard NeoPixel or other addressable RGB LEDs on supported hardware. Can be used for visual status indicators, notification lights, or decorative effects.

Pick the color with the swatch's **Preset colors** row or the **Red**, **Green** and **Blue** sliders.
**Advanced** opens number fields for the same three values.

| Setting            | 说明                                        | 默认                           |
| ------------------ | ----------------------------------------- | ---------------------------- |
| 电流                 | LED drive current, 0 to 31                | 10                           |
| LED 状态             | Turn the LED on or off                    | 关闭                           |
| Red / Green / Blue | Individual color channel values, 0 to 255 | Derived from the node number |

### Detection Sensor module

Turns your node into a motion or door sensor alert system. When a GPIO pin detects a state change (motion detected, door opened), the node broadcasts an alert message over the mesh.

| Setting                                   | 说明                                                                                                                                                                | 默认                                                                                   |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| 启用检测传感器                                   | Turn the detection sensor on. It also has to be on for nodes that receive its alerts or chart its log                                             | 关闭                                                                                   |
| 显示器的 GPIO 引脚                              | GPIO pin connected to the sensor. 0 disables the module                                                                                           | 0                                                                                    |
| Minimum time between detection broadcasts | Minimum time between alert broadcasts                                                                                                                             | 0, which the firmware treats as 1 hour (12 hours on Router roles) |
| 易记名称                                      | Custom name for this sensor, used in its messages                                                                                                                 | 空                                                                                    |
| 发送响铃                                      | Include a bell character in alerts                                                                                                                                | 关闭                                                                                   |
| State Broadcast Interval                  | How often to send the sensor's state whether or not anything was detected. **Unset** sends only on detection                                      | 未设置                                                                                  |
| Trigger Type                              | How the pin's state maps to a detection event: **High**, **Low**, **Rising Edge**, **Falling Edge**, **Either Edge High**, or **Either Edge Low** | 高                                                                                    |
| Use Internal Pull-up                      | Enable the pin's internal pull-up resistor                                                                                                                        | 关闭                                                                                   |

### Paxcounter module

People counter using Wi-Fi and BLE probe requests. Counts nearby devices by passively listening for probe requests that phones and laptops emit when scanning for networks. Available only on ESP32 devices. Counts are approximate, since one person may carry several devices, so the module suits estimating foot traffic at trailheads or event venues.

| Setting                                 | 说明                                                                                                       | 默认                                      |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| BLE Threshold (dBm)  | The same cut-off for BLE advertisements                                                                  | 0, which the firmware treats as −80 dBm |
| 启用 Paxcount                             | Turn people counting on. Wi-Fi and Bluetooth must both be off on the node for it to work | 关闭                                      |
| GPS 轮询间隔                                | How often to report counts                                                                               | —                                       |
| WiFi Threshold (dBm) | Ignore Wi-Fi probes weaker than this, so distant devices aren't counted                                  | 0, which the firmware treats as −80 dBm |

### Status Message module

The status message has no module screen. It is edited with the rest of the node's identity, on
[Settings — Radio & User](settings-radio-user#user-profile).

### Mesh Beacon module

Broadcasts an invitation to your mesh, and receives invitations from others. The entry appears only
on nodes running firmware 2.8.0 or newer. See [Local Mesh Discovery](discovery) for the full
walkthrough.

### TAK module

Team Awareness Kit integration for interoperability with ATAK and WinTAK. The entry is labeled
**TAK (ATAK)**. Two things have to be true before it appears in the module list: the node runs
firmware 2.8.0 or newer, and its **Device Role** on **Settings → Device configuration → Device** is
set to **TAK** or **TAK Tracker**. See [TAK Integration](tak) for detailed setup and usage.

## 管理员

### 远程管理

Remote administration changes another node's settings over the mesh. The target node must list your node's public key as one of its **Admin Key** entries, set on its **Settings → Security** screen.

1. Tap the target node in the node list to open its detail screen.
2. In the **Administration** card, tap **Remote Administration**. The app first establishes a session with the node, showing _Establishing remote session…_, then opens its **Settings**.
3. Modify configuration.
4. Tap **Save** or **Save & restart**. The changes are sent over the mesh.

### Device Actions

**Settings → Administration** holds one-shot actions, each behind a confirmation dialog:

| Action               | What it does                                                                                                                                                                                                                                                                                                            |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 设置时间                 | Sends your phone's clock to the node                                                                                                                                                                                                                                                                                    |
| 重启                   | Restarts the node                                                                                                                                                                                                                                                                                                       |
| Reboot into DFU mode | Restarts an nRF52 node into its bootloader, where it stays off the mesh until new firmware is flashed or it is reset. On bootloader 2.6.0 and later it boots the installed firmware after 6 minutes with no update activity. Shown only for nRF52 nodes |
| 关机                   | Powers the node down                                                                                                                                                                                                                                                                                                    |
| 恢复出厂设置               | Returns every setting to its factory default                                                                                                                                                                                                                                                                            |
| 重置节点数据库              | Clears the node database. This dialog carries a **Preserve Favorites?** switch                                                                                                                                                                                                                          |

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys, and cannot be undone. Before you reset, use **Export configuration** to save the node's settings and **Backup Keys** on the Security screen to save its keys, so you can put both back afterwards.

### 备份与恢复

**Settings → Backup & Restore** writes the connected node's whole configuration to a file with
**Export configuration**, and reads a saved file back in with **Import configuration**.
Traffic Management settings are exported but not applied on import. Export before a factory
reset, or to copy one node's setup onto another. The section is shown for your
own node only, not over remote admin.

The same section backs up the app's own data for a node: its messages and nodes as stored in the
app, not the node's settings.

- **Back up app data** saves that data to a file named after the node and the date.
- **Restore app data** replaces the app's messages and nodes for that node with a backup, after a
  confirmation. It accepts only an undamaged backup made by an app with the same database version.
  The restore takes effect after you restart the app and reconnect to that node.

Both disconnect the node first, after asking, and act on the data of the node that was connected.
With no node connected they act on the most recently connected node. They stay disabled, with
the line _Connect to a radio first. There is no app data to back up or restore yet._, until the
app has stored data for some node.

### 高级

**Settings → Advanced** collects the tools that read or rewrite local state, and is likewise shown
for your own node only: **Firmware Update** on OTA-capable hardware, **Clean Node Database**,
**TAK Server**, **Local Mesh Discovery**, and the **Debug Panel**.

#### 清理节点数据库

Prunes nodes from your node database — from the app's copy _and_ from the node's own, so this is
not a display-only cleanup. The two filters combine rather than acting separately; the screen puts
it as _Selections are additive_.

- **Clean up nodes last seen older than N days** — always applied. The slider runs from 7 days to
  365 and starts at 30; with **Clean up only unknown nodes** turned on, its floor drops to 0.
- **Clean up only unknown nodes** — narrows the same purge to nodes that never sent their user
  info. The age limit still applies on top of it.

The screen lists the nodes queued for deletion as you move the filters. **Clean Now** carries the
purge out, after one more confirmation, and it can't be undone. Favorited nodes, ignored nodes,
and nodes with a public key heard in the last seven days are never removed, whatever the filters
say — that is why the queued list can be shorter than you expect.

#### 调试面板

Opens the **Packets** and **App logs** tabs for viewing, filtering, and exporting diagnostic output. See [Debug Logs](debug-logs) for the full walkthrough.

### App 设置

The **App** section of the **Settings** screen configures the app rather than the node, and
appears only when your own node is selected. It holds, in order:

- **Allow analytics and crash reporting.** — opt in or out of diagnostics. Google Play build only.
- **Provide phone location to mesh** — share this phone's position when the node has no GPS fix.
  It needs precise location: with approximate location allowed, turning it on asks for precise.
- **Compact encoding for Cyrillic** — how look-alike characters in names are handled.
- **Language**, **Theme**, and **Units**. **Units** is metric, imperial, or follow the system, and
  is the entry [Units & Locale](units-and-locale) sends you to.
- **Show full message timestamps**.
- **Device DB cache limit**, which sets how much history is kept.
- **Export rangetest packets**, **Export all packets** and **Export node database**.
- **Node Layout** — how much detail each row of the node list shows.
- **Wi-Fi Provisioning for mPWRD-OS** — see [Connections](connections).
- **Message Filter** — hides incoming messages that contain words you list. With no words
  configured it does nothing.
- **System AI** — appears only where app functions are available; see
  [App Functions](app-functions).

A **Permissions** section sits above **App**, so a user who skipped a permission during
onboarding can get back to it. **Information** sits below it, with **Show Introduction**,
**App Notifications**, **System Settings**, **About**, and the app version. **Help &
Documentation** closes the screen.

### 关于

**Settings → About** carries the app's own identity rather than the node's, in three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one. It also lists the GitHub repository, the running app version, and
  **Acknowledgements** (see the next section).
- **Project information** — links to the website, to this documentation, and to the GNU GPL v3
  license the app is released under.

The footer states the copyright and that the app is free software with no warranty, which you
may redistribute under the same license.

### 开源

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries.

### Troubleshooting remote admin

- **"Could not reach node — try again or move closer."** The target may be out of range or offline, or it doesn't list your node's public key as an admin key. Check the **Admin Key** entries on the target node.
- **"Connect to a radio to administer remote nodes."** Your own node isn't connected. Reconnect it from the [Connections](connections) screen.
- **Changes not applying** — some settings require a reboot to take effect. Try the Reboot action after saving.

## Related Topics

- [Settings — Radio & User](settings-radio-user) — core node and user profile settings
- [Module configuration reference](https://meshtastic.org/docs/configuration/module) — detailed module docs on meshtastic.org
- [FAQ](https://meshtastic.org/docs/faq/) — common questions on meshtastic.org
