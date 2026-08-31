---
title: Settings — Modules & Admin
parent: User Guide
nav_order: 8
last_updated: 2026-08-30
description: Configure optional feature modules (MQTT, telemetry, canned messages, TAK, and more) and perform device administration.
aliases:
  - modules
  - module-config
  - administration
---

# Settings — Modules & Admin

Configure optional feature modules and perform device administration. Modules extend Meshtastic with specialized capabilities — each can be independently enabled or disabled.

> 💡 **Tip:** You only need to enable the modules you actually use. Disabling unused modules reduces airtime, saves battery, and simplifies your configuration. A module you expect can be missing for three reasons: your node's role does not enable it, your firmware is older than the release that added it, or the firmware build excludes it for this hardware.

Module settings use a card-based layout with toggle switches, dropdowns, text fields, and sliders:

![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)

![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png)

![A text field setting with a value entered](../../assets/screenshots/settings_text_field.png)

![A module settings card with its title and grouped controls](../../assets/screenshots/settings_titled_card.png)

## 模块配置

Every module lives under **Settings → Module configuration**.

> ⚠️ **Important:** Saving a module screen restarts the radio — the button reads **Save & restart**, and the radio is unreachable for a few seconds afterwards. External Notification and Mesh Beacon are the exceptions: their button reads **Save**, and the radio may still restart for some changes.

### MQTT Module

Bridges mesh messages to and from an MQTT broker for internet connectivity. This is how you extend your mesh beyond radio range or integrate with home automation systems.

| Setting         | 说明                                                                                                                                                                                      |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 启用MQTT          | Toggle MQTT bridge                                                                                                                                                                      |
| 地址              | MQTT broker address                                                                                                                                                                     |
| 用户名称            | Authentication username                                                                                                                                                                 |
| 密码              | Authentication password                                                                                                                                                                 |
| 启用加密            | Encrypt MQTT payloads                                                                                                                                                                   |
| 启用JSON输出        | Publish and consume MQTT messages as JSON. Marked deprecated in the protobuf schema, but it is still the only toggle for this behavior and the firmware still honors it |
| 启用TLS           | Use secure connection                                                                                                                                                                   |
| 根主题             | Base MQTT topic path                                                                                                                                                                    |
| 启用客户端代理         | Let a connected phone carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                     |
| 在此手机上开启 MQTT 代理 | The phone-side half of **Proxy to client enabled**: whether this phone acts as that relay. See [MQTT](mqtt)                                             |
| 地图报告            | Publish position to the public map — see the Map reporting group that follows                                                                                                           |

Turning **Map reporting** on reveals a consent card headed _Consent to Share Unencrypted Node Data
via MQTT_, with an **I agree.** switch under it. The rest of the card does not exist on screen
until you agree:

| Setting                       | 说明                                                                                                                                                                                       |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 我同意。                          | Explicit consent to transmit your node data, including an approximate position, unencrypted. Map reporting does not start without it                                     |
| Precision slider              | The slider has no label of its own. It sets how coarsely your position is published, from 12 to 15; the line beneath reads _±_ the resulting distance, in your own units |
| 地图报告间隔 (秒) | How often to report. A dropdown of fixed intervals from 1 hour to 72 hours — nothing shorter is offered                                                                  |

See [MQTT](mqtt) for a detailed usage guide including encryption, privacy, and broker setup.

### Serial Module

Enables serial port communication for external device integrations (GPS modules, sensors, or custom hardware). When enabled, the node's serial port can send and receive protobuf or text data, allowing external microcontrollers or computers to interact with the mesh.

| Setting   | 说明                                                                                                                                                                                                  |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 启用串口      | Activate serial communication                                                                                                                                                                       |
| 启用Echo    | Echo received serial data back                                                                                                                                                                      |
| 串口模式      | Which protocol the port speaks — Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, VE.Direct, MeshSolar config, Log, or Log (text only) |
| RX / TX   | GPIO pins for the serial connection                                                                                                                                                                 |
| 串口波特率     | Port speed                                                                                                                                                                                          |
| 超时        | How long to wait before considering an incoming message complete                                                                                                                                    |
| 覆盖控制台串口端口 | Take over the port the debug console normally uses                                                                                                                                                  |

### External Notification Module

Controls buzzer, LED, or vibration alerts on your radio hardware. Useful for devices that need to physically signal when a message arrives — particularly helpful for unattended or outdoor installations.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles.

| Setting                            | 说明                                                                                                  |
| ---------------------------------- | --------------------------------------------------------------------------------------------------- |
| 启用外部通知                             | Master toggle for the module                                                                        |
| Alert message LED / buzzer / vibra | Which outputs fire on an incoming message                                                           |
| Alert bell LED / buzzer / vibra    | Which outputs fire on a received bell character                                                     |
| 输出 LED (GPIO)   | Pin the LED is wired to                                                                             |
| 输出 LED 活动高                         | Whether the LED pin is active high or low                                                           |
| 输出震动(GPIO)      | Pin the buzzer is wired to                                                                          |
| 输出振动 (GPIO)     | Pin the vibration motor is wired to                                                                 |
| 使用 PWM 蜂鸣器                         | Drive the buzzer with PWM, which allows tones rather than a single pitch                            |
| 使用 I2S 作为蜂鸣器                       | Send the alert through an I2S audio output instead                                                  |
| 输出持续时间 (毫秒)     | How long a single alert lasts                                                                       |
| 屏幕超时(秒)         | Keep repeating the alert for this long until it is acknowledged. 0 disables nagging |
| 铃声                                 | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file              |

### Store & Forward Module

Buffers messages for nodes that were temporarily offline, then replays them when those nodes reconnect. Essential for meshes where nodes go in and out of range regularly — ensures messages aren't lost during brief disconnections.

| Setting   | 说明                                                                                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 启用存储和转发   | Activate store and forward                                                                                                                       |
| Heartbeat | Periodically announce this node's store-and-forward capability                                                                                   |
| 记录数       | Maximum stored messages                                                                                                                          |
| 历史记录最大返回值 | Max messages to replay                                                                                                                           |
| 历史记录返回窗口  | Time window for replay                                                                                                                           |
| 服务器       | Act as a store-and-forward server for the mesh (requires ample memory, e.g. ESP32 with PSRAM) |

> 💡 **Tip:** Store and Forward works best on nodes with ample memory (ESP32 with PSRAM). Router nodes are ideal candidates since they're typically always-on.

### Range Test Module

> ⚠️ **Warning:** Range Test only works on a secured primary channel. As long as your primary channel
> still uses the default channel key, the interval and CSV controls stay disabled — you can still
> switch an already-enabled module off — and saving force-disables the module if the channel has
> reverted to public.

Automated range testing tool for evaluating link quality between nodes. When enabled, the node periodically transmits test messages with incrementing counters. A receiver node logs these messages, allowing you to walk or drive away and later analyze at what distance messages stopped arriving.

| Setting                                | 说明                                                                                        |
| -------------------------------------- | ----------------------------------------------------------------------------------------- |
| 启用范围测试                                 | Activate range testing                                                                    |
| 发件人消息间隔(秒)          | Time between test transmissions, chosen from a dropdown of fixed intervals                |
| 保存 CSV 到存储 (仅ESP32) | Log received test data to the radio's own filesystem. ESP32 hardware only |

### Telemetry Module

Controls what telemetry data your node shares with the mesh. Telemetry includes device health (battery, uptime) and environmental sensor data (temperature, humidity, pressure).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely.

| Setting    | 说明                                                                                                                                                                                |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 发送设备远程数据   | Master toggle for device metrics. Only shown on firmware 2.7.12 and newer                                                         |
| 设备计量更新间隔   | How often to report battery, uptime and channel utilization                                                                                                                       |
| 启用环境计量模块   | Report the attached environment sensors                                                                                                                                           |
| 环境计量更新间隔   | How often to report them                                                                                                                                                          |
| 屏幕显示环境指标   | Also show these readings on the device's own display                                                                                                                              |
| 环境测量值使用华氏度 | Use °F on the device's display. This is the radio's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale) |
| 启用空气质量计量模块 | Report particulate and CO₂ sensor data                                                                                                                                            |
| 空气质量计量更新间隔 | How often to report them                                                                                                                                                          |
| 启用电源计量模块   | Report the per-channel voltage and current readings                                                                                                                               |
| 电量计更新间隔    | How often to report them                                                                                                                                                          |
| 在屏幕上启用电源指标 | Also show power readings on the device's display                                                                                                                                  |

See [Telemetry & Sensors](telemetry-and-sensors) for supported sensors and configuration recommendations.

### Canned Message Module

Pre-configured messages accessible from the radio's physical buttons (for radios with rotary encoders, keypads, or similar input hardware). Define a list of quick-send messages that can be transmitted without a phone connected — ideal for field use.

| Setting                                        | 说明                                                                                                        |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| ~~Canned message enabled~~                     | ⚠️ **Deprecated** in the protobuf schema                                                                  |
| 消息                                             | Newline-separated list of messages                                                                        |
| 发送响铃                                           | Send a bell character alongside the message, so a receiving node's External Notification module can sound |
| 启用旋转编码器 #1                                     | Use a rotary encoder as the input device                                                                  |
| GPIO pin for rotary encoder A / B / Press port | The three pins the encoder is wired to                                                                    |
| Generate input event on Press / CW / CCW       | Which key event each encoder action produces                                                              |
| 启用Up/Down/select 输入                            | A separate, simpler input scheme using up/down/select buttons rather than an encoder                      |
| ~~Allow input source~~                         | ⚠️ **Deprecated** in the protobuf schema                                                                  |

### Audio Module

Codec2 audio support for low-bandwidth voice communication over the mesh. This is an **experimental** feature that encodes voice into very small data packets using the Codec2 codec.

| Setting    | 说明                                   |
| ---------- | ------------------------------------ |
| 启用CODEC2   | Activate audio module                |
| CODEC2 采样率 | Audio quality/bandwidth tradeoff     |
| PTT 引脚     | GPIO pin for the push-to-talk button |
| I2S 字选择    | GPIO pin for I2S WS                  |
| I2S 数据 IN  | GPIO pin for I2S DIN                 |
| I2S 数据 OUT | GPIO pin for I2S DOUT                |
| I2S 时钟     | GPIO pin for the I2S bit clock       |

> ℹ️ **Note:** Audio requires specific hardware (I2S microphone and speaker). Voice quality is very low-bandwidth — think "understandable radio voice," not phone-call quality.

### Remote Hardware Module

GPIO control over the mesh network. Allows a remote node to read or write GPIO pins on another node — useful for activating relays, reading switches, or controlling external hardware from a distance.

> ⚠️ **Warning:** Turning on **Allow undefined pin access** gives remote nodes access to all GPIO pins, which could interfere with the radio's own hardware. Turn it on only on dedicated GPIO nodes.

| Setting    | 说明                                                              |
| ---------- | --------------------------------------------------------------- |
| 启用远程硬件     | Activate remote GPIO access                                     |
| 允许未定义的引脚访问 | Allow access to any GPIO pin (security risk) |
| 可用引脚       | Up to 4 GPIO pins this node exposes for remote read/write       |

### Neighbor Info Module

Broadcasts information about directly heard neighbors, enabling mesh topology mapping. Each enabled node periodically shares a list of the other nodes it can hear and their signal quality.

| Setting    | 说明                                                                                                                                   |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 启用邻居信息     | Activate neighbor broadcasting                                                                                                       |
| 更新间隔（秒）    | How often to broadcast neighbor list                                                                                                 |
| 通过 LoRa 传输 | Also broadcast neighbor info over LoRa, not just MQTT/phone. Unavailable on a channel using the default key and name |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting Module

Controls onboard NeoPixel or other addressable RGB LEDs on supported hardware. Can be used for visual status indicators, notification lights, or decorative effects.

| Setting            | 说明                                                         |
| ------------------ | ---------------------------------------------------------- |
| LED 状态             | Turn the LED on or off                                     |
| 电流                 | LED current limit (0–31)                |
| Red / Green / Blue | Individual color channel values (0–255) |

### Detection Sensor Module

Turns your node into a motion or door sensor alert system. When a GPIO pin detects a state change (motion detected, door opened), the node broadcasts an alert message over the mesh.

| Setting                      | 说明                                                                                                                                      |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| 启用检测传感器                      | Activate detection sensor                                                                                                               |
| 显示器的 GPIO 引脚                 | GPIO pin connected to sensor                                                                                                            |
| 检测触发器类型                      | How the pin's state maps to a detection event (e.g. active high/low, edge-triggered) |
| 使用 输入上拉 模式                   | Enable the pin's internal pull-up resistor                                                                                              |
| 最小广播时间(秒) | Minimum time between alert broadcasts                                                                                                   |
| 状态广播(秒)   | Periodic state broadcast interval                                                                                                       |
| 发送带有警报消息的响铃声                 | Include bell character in alerts                                                                                                        |
| 友好名称                         | Custom name for this sensor                                                                                                             |

### Paxcounter Module

People counter using Wi-Fi and BLE probe requests. Counts nearby devices by passively listening for probe requests that phones and laptops emit when scanning for networks. Available only on ESP32 devices.

| Setting              | 说明                                                                                                                |
| -------------------- | ----------------------------------------------------------------------------------------------------------------- |
| 启用 Paxcount          | Activate people counting                                                                                          |
| 更新间隔（秒）              | How often to report counts                                                                                        |
| Wi-Fi RSSI threshold | Ignore Wi-Fi probes weaker than this, so distant devices are not counted (defaults to −80 dBm) |
| BLE RSSI threshold   | The same cut-off for BLE advertisements (defaults to −80 dBm)                                  |

> 💡 **Tip:** Paxcounter is useful for estimating foot traffic at trailheads, event venues, or other locations. Counts are approximate — one person may carry multiple devices.

### Status Message Module

The status message has no module screen. It is edited with the rest of the node's identity, on
[Settings — Radio & User](settings-radio-user#user-profile).

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. The entry appears only
on radios running firmware 2.8.0 or newer. See [Local Mesh Discovery](discovery) for the full
walkthrough.

### TAK Module

Team Awareness Kit integration for interoperability with ATAK and WinTAK. Two things have to be
true before the entry appears in the module list: the radio runs firmware 2.8.0 or newer, and its
**Device Role** on **Settings → Device configuration → Device** is set to `TAK` or `TAK_TRACKER`.
See [TAK Integration](tak) for detailed setup and usage.

## 管理员

### 远程管理

Remotely configure nodes that share your admin key:

1. Select the target node in the node list.
2. Navigate to **Settings** for that node.
3. Modify configuration.
4. Tap **Save** — changes are sent over the mesh.

> ⚠️ **Important:** Requires an admin key configured on both your node and the target node.

### Device Actions

**Settings → Administration** holds five one-shot actions, each behind a confirmation dialog:

| Action  | What it does                                                                                           |
| ------- | ------------------------------------------------------------------------------------------------------ |
| 设置时间    | Sends your phone's clock to the radio                                                                  |
| 重启      | Restarts the radio                                                                                     |
| 关机      | Powers the radio down                                                                                  |
| 恢复出厂设置  | Returns every setting to its factory default                                                           |
| 重置节点数据库 | Clears the radio's node database. This dialog carries a **Preserve Favorites?** switch |

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys, and cannot be undone. Before you reset, use **Export configuration** to save the radio's settings and **Backup Keys** on the Security screen to save its keys, so you can put both back afterwards.

### 备份与恢复

**Settings → Backup & Restore** writes the connected radio's whole configuration to a file with
**Export configuration**, and reads a saved file back in with **Import configuration**. Export
before a factory reset, or to copy one radio's setup onto another. The section is shown for your
own radio only, not over remote admin.

### 高级

**Settings → Advanced** collects the tools that read or rewrite local state, and is likewise shown
for your own radio only: **Firmware Update** on OTA-capable hardware, **Clean Node Database**,
**TAK Server**, **Local Mesh Discovery**, and the **Debug Panel**.

#### 清理节点数据库

Prunes nodes from your node database — from the app's copy _and_ from the radio's own, so this is
not a display-only cleanup. The two filters combine rather than acting separately; the screen puts
it as _Selections are additive_.

- **Clean up nodes last seen older than N days** — always applied. The slider runs from 7 days to
  365 and starts at 30; with **Clean up only unknown nodes** turned on, its floor drops to 0.
- **Clean up only unknown nodes** — narrows the same purge to nodes that never sent their user
  info. The age limit still applies on top of it.

The screen lists the nodes queued for deletion as you move the filters. **Clean Now** carries the
purge out, after one more confirmation, and it cannot be undone. Favorited nodes, ignored nodes,
and nodes with a public key heard in the last seven days are never removed, whatever the filters
say — that is why the queued list can be shorter than you expect.

#### 调试面板

Opens the **Packets** and **App logs** tabs for viewing, filtering, and exporting diagnostic output. See [Debug Logs](debug-logs) for the full walkthrough.

### App 设置

Two easy-to-miss entries on the **Settings** screen configure the app rather than the radio, and
appear only when your own node is selected:

- **Node Layout** — how much detail each row of the node list shows.
- **Message Filter** — hides incoming messages that contain words you list. With no words
  configured it does nothing.

### 关于

**Settings → About** carries the app's own identity rather than the radio's:

Three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one. It also lists the GitHub repository, the running app version, and
  **Acknowledgements** (see the next section).
- **Project information** — links to the website and to this documentation.

### 开源

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
