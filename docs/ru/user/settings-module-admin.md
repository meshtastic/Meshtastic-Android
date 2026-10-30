---
title: Настройки — Модули и администрирование
nav_order: 8
last_updated: 2026-10-28
description: Настрой дополнительные функциональные модули (MQTT, телеметрия, готовые сообщения, TAK и другие) и выполняй администрирование устройств.
aliases:
  - modules
  - module-config
  - administration
---

# Настройки — Модули и администрирование

Настрой дополнительные функциональные модули и выполняй управление устройством. Модули расширяют Meshtastic с помощью специализированных возможностей — каждый из них можно включать или отключать отдельно.

Enable only the modules you use. Отключение неиспользуемых модулей снижает время передачи, экономит батарею и упрощает конфигурацию. A module you expect can be missing for three reasons: your node's role doesn't enable it, your firmware is older than the release that added it, or the firmware build excludes it for this hardware.

Настройки модулей используют макет на основе карточек с переключателями, выпадающими списками, текстовыми полями и ползунками:

![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)

![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png)

![A text field setting with a value entered](../../assets/screenshots/settings_text_field.png)

![A module settings card with its title and grouped controls](../../assets/screenshots/settings_titled_card.png)

## Настройки модуля

Every module lives under **Settings → Module configuration**.

Saving a module screen restarts the node: the button reads **Save & restart**, and the node is unreachable for a few seconds afterwards. External Notification and Mesh Beacon are the exceptions: their button reads **Save**. External Notification may still restart the node for some changes, while a Mesh Beacon change applies without a restart.

### MQTT module

Мосты передают сообщения туда и обратно от брокера MQTT для подключения к интернету. This is how you extend your mesh beyond LoRa range or integrate with home automation systems.

| Настройка              | Описание                                                                                                                                                                                                                                                                         | По умолчанию          |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| Адрес                  | Адрес MQTT брокера                                                                                                                                                                                                                                                               | `mqtt.meshtastic.org` |
| Шифрование включено    | Publish packets still encrypted with the channel key                                                                                                                                                                                                                             | On                    |
| Вывод JSON включен     | Publish and consume MQTT messages as JSON. Shown only for a node on firmware older than 2.8, which removed JSON support, or while the setting is still on                                                                                        | Выкл                  |
| Публикация на карте    | Publish position to the public map — see the map reporting card that follows                                                                                                                                                                                                     | Выкл                  |
| Прокси клиенту включен | Let the connected app carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                                                                                                              | Выкл                  |
| MQTT включен           | Turn the MQTT bridge on                                                                                                                                                                                                                                                          | Выкл                  |
| MQTT proxy in this app | The app-side half of **MQTT Client Proxy**: whether this app acts as that relay. It shows whether the relay is running, and can be turned on only while **MQTT Enabled** and **MQTT Client Proxy** are both on. See [MQTT](mqtt) | —                     |
| Пароль                 | Пароль аутентификации                                                                                                                                                                                                                                                            | The public broker's   |
| Корневая тема          | Базовый путь темы MQTT                                                                                                                                                                                                                                                           | `msh`                 |
| Включить TLS           | Use a secure connection                                                                                                                                                                                                                                                          | Выкл                  |
| Имя пользователя       | Имя пользователя для аутентификации                                                                                                                                                                                                                                              | The public broker's   |

Turning **Map Reporting** on reveals a consent card headed _Consent to Share Unencrypted Node Data
via MQTT_, with an **I agree.** switch under it. The rest of the card doesn't exist on screen
until you agree:

| Настройка                   | Описание                                                                                                                                                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Я согласен. | Explicit consent to transmit your node data, including an approximate position, unencrypted. Map reporting doesn't start without it                                      |
| Map Publish Interval        | How often to report. A dropdown of fixed intervals from 1 hour to 72 hours — nothing shorter is offered                                                                  |
| Precision slider            | The slider has no label of its own. It sets how coarsely your position is published, from 12 to 15; the line beneath reads _±_ the resulting distance, in your own units |

См. [MQTT](mqtt) для подробного руководства по использованию, включая шифрование, конфиденциальность и настройку брокера.

### Serial module

Позволяет общаться через последовательный порт с внешними устройствами (GPS-модулями, датчиками или собственной техникой). Когда включено, последовательный порт ноды может отправлять и получать данные в формате protobuf или текст, что позволяет внешним микроконтроллерам или компьютерам взаимодействовать с сетью.

| Настройка                                                                                        | Описание                                                                                                                                                                                                                            | По умолчанию |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| Скорость COM-порта                                                                               | Port speed, from **110 Baud** to **921600 Baud**                                                                                                                                                                                    | По умолчанию |
| Echo включен                                                                                     | Echo packets you send back over the serial port                                                                                                                                                                                     | Выкл         |
| Режим COM-порта                                                                                  | Which protocol the port speaks: **Default**, **Simple**, **Protobufs**, **Text Message**, **NMEA Positions**, or **CalTopo**, plus `WS85`, `VE_DIRECT`, `MS_CONFIG`, `LOG` and `LOGTEXT` under their firmware names | По умолчанию |
| Переопределить COM-порт консоли                                                                  | Take over the port the debug console normally uses                                                                                                                                                                                  | Выкл         |
| Receive data (rxd) GPIO pin / Transmit data (txd) GPIO pin | GPIO pins for the serial connection                                                                                                                                                                                                 | 0            |
| COM-порт включен                                                                                 | Turn serial communication on                                                                                                                                                                                                        | Выкл         |
| Время ожидания истекло                                                                           | How long to wait before treating received data as a complete packet. 0 uses 250 ms                                                                                                                                  | 0            |

### External Notification module

Controls buzzer, LED, or vibration alerts on your node hardware. Полезно для устройств, которым нужно физически сигнализировать о приходе сообщения — особенно удобно для неоснащенных персоналом или уличных установок.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles. The
firmware turns the module on, and picks the outputs, on hardware that has a buzzer, vibration motor
or notification LED; elsewhere everything starts off.

| Настройка                                                                                                            | Описание                                                                                                                               |
| -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Вывод светодиода активный высокий                                                                                    | Whether the LED pin is active high or low. Shown once **Output pin GPIO** is set                                       |
| Alert when receiving a bell / Alert GPIO buzzer when receiving a bell / Alert GPIO vibra motor when receiving a bell | Which outputs fire on a received bell character                                                                                        |
| Alert when receiving a message / Alert GPIO buzzer when receiving a message / Vibra Motor Alert                      | Which outputs fire on an incoming message                                                                                              |
| Внешние уведомления включены                                                                                         | Master toggle for the module                                                                                                           |
| GPIO Output Duration                                                                                                 | How long a single alert lasts                                                                                                          |
| Интервал повтора                                                                                                     | Keep repeating the alert for this long until it is acknowledged. **Unset** disables nagging                            |
| Выход LED (GPIO)                                                                                  | Pin the LED is wired to                                                                                                                |
| Выход Буззера (GPIO)                                                                              | Pin the buzzer is wired to                                                                                                             |
| Вибросигнал (GPIO)                                                                                | Pin the vibration motor is wired to                                                                                                    |
| Рингтон                                                                                                              | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file                                                 |
| Использовать I2S как буззер                                                                                          | Send the alert through an I2S audio output instead                                                                                     |
| Использовать PWM-звукоизлучатель                                                                                     | Drive the buzzer with PWM, which allows tones rather than a single pitch. Shown once **Output pin buzzer GPIO** is set |

### Store & Forward module

Буферизирует сообщения для узлов, которые временно были недоступны, а затем ретранслирует их, когда эти узлы переподключаются. Важное значение для сеток, где узлы входят и выходят вне диапазона регулярно - обеспечивает отсутствие потери сообщений при коротких разъединениях. A server needs an ESP32 with PSRAM or a Linux `meshtasticd` node. A node in the Router or Router Late role acts as a server even with the server setting off.

| Настройка                                        | Описание                                                                           | По умолчанию |
| ------------------------------------------------ | ---------------------------------------------------------------------------------- | ------------ |
| Макс возврат истории                             | Most stored messages returned per request. 0 uses 25               | 0            |
| Окно возврата истории                            | How far back, in minutes, stored messages are returned. 0 uses 240 | 0            |
| Количество записей                               | Most messages to store. 0 uses three quarters of the free PSRAM    | 0            |
| Heartbeat                                        | Periodically announce that this node is a store-and-forward server                 | Выкл         |
| Сервер                                           | Act as a store-and-forward server for the mesh                                     | Выкл         |
| Store &amp; Forward включена | Turn store and forward on                                                          | Выкл         |

### Range Test module

Автоматизированный инструмент для проверки дальности и оценки качества связи между нодами. Когда включено, нода периодически отправляет сообщения с увеличивающимся счетчиком. Приёмная нода записывает эти сообщения, что позволяет тебе уйти пешком или уехать на машине, а потом проанализировать, на каком расстоянии сообщения перестали приходить.

Range Test only works on a secured primary channel. As long as your primary channel still uses the
default channel key, the interval and CSV controls stay disabled — you can still switch an
already-enabled module off — and saving force-disables the module if the channel has reverted to
public.

| Настройка                                                                    | Описание                                                                                                            | По умолчанию   |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | -------------- |
| Проверка дальности включена                                                  | Turn range testing on                                                                                               | Выкл           |
| Сохранить .CSV в хранилище (только ESP32) | Log received test data as a CSV on the node's own filesystem. ESP32 nodes only                      | Выкл           |
| Sender Interval                                                              | Time between test transmissions, chosen from a dropdown of fixed intervals. **Unset** stops sending | Не установлена |

### Telemetry module

Контролирует какими телеметрическими данными ваш узел делится с сеткой. Телеметрия включает данные о состоянии устройства (заряд батареи, время работы) и данные с датчиков окружающей среды (температура, влажность, давление).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely. The intervals run from 30 minutes to 72 hours.

| Настройка                                   | Описание                                                                                                                                                                                    | По умолчанию |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| Модуль измерения качества воздуха включен   | Report particulate and CO₂ sensor data                                                                                                                                                      | Выкл         |
| Интервал обновления данных качества воздуха | How often to report them                                                                                                                                                                    | —            |
| Отправлять телеметрию устройства            | Send device metrics to the mesh. When it is off, they go only to connected clients. Only shown on firmware 2.7.13 and newer | Выкл         |
| Интервал обновления метрик устройства       | How often to report battery, uptime and channel utilization                                                                                                                                 | —            |
| Использовать метрику окружения в Fahrenheit | Use °F on the device's display. This is the node's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale)            | Выкл         |
| Модуль метрик окружения включен             | Report the attached environment sensors                                                                                                                                                     | Выкл         |
| Интервал обновления метрик среды            | How often to report them                                                                                                                                                                    | —            |
| Модуль метрик питания включен               | Report the per-channel voltage and current readings                                                                                                                                         | Выкл         |
| Интервал обновления метрик электропитания   | How often to report them                                                                                                                                                                    | —            |
| Включить метрики питания на экране          | Also show power readings on the device's display                                                                                                                                            | Выкл         |
| Показатели окружения на экране включены     | Also show environment readings on the device's display                                                                                                                                      | Выкл         |

Посмотрите [Телеметрия и датчики](telemetry-and-sensors) — для получения информации о поддерживаемых датчиках и рекомендациях по настройке.

### Canned Message module

Pre-configured messages accessible from the node's physical buttons (for nodes with rotary encoders, keypads, or similar input hardware). Определите список быстрых сообщений, которые могут быть переданы без подключённого телефона — идеально подходит для использования в поле.

| Настройка                                                                    | Описание                                                                                                                                           | По умолчанию |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| Clockwise Rotary Event / Counterclockwise Rotary Event / Encoder Press Event | Which key event each encoder action produces: **None**, **Up**, **Down**, **Left**, **Right**, **Select**, **Back**, or **Cancel** | Отсутствует  |
| Сообщения                                                                    | Список сообщений, разделённых новой строкой                                                                                                        | Пусто        |
| Pin A / Pin B / Press Pin                                                    | The three pins the rotary encoder is wired to                                                                                                      | 0            |
| Поворотный энкодер                                                           | Use a rotary encoder as the input device                                                                                                           | Выкл         |
| Отправить колокольчик                                                        | Send a bell character alongside the message, so a receiving node's External Notification module can sound                                          | Выкл         |
| Up/Down/Select Input                                                         | A separate, simpler input scheme using up/down/select buttons rather than an encoder                                                               | Выкл         |

Some boards with built-in encoders or keys come with the input settings filled in. Two older
fields, **Canned message enabled** and **Allow input source**, appear only for a node on firmware
older than 2.7, or while they still hold a value.

### Audio module

Поддержка аудио Codec2 для низкополосной голосовой связи через сетку. Это **экспериментальная функция**, которая кодирует голос в очень маленькие пакеты данных с помощью кодека Codec2. It needs specific hardware, an I2S microphone and speaker, and the voice quality is low.

| Настройка                    | Описание                                                                                                               | По умолчанию |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------ |
| Частота дискретизации CODEC2 | Codec2 bitrate, from **450 bps** to **3200 bps**. Lower rates use less bandwidth and give poorer audio | По умолчанию |
| CODEC 2 включен              | Turn the audio module on                                                                                               | Выкл         |
| I2S Clock Pin                | GPIO pin for the I2S bit clock                                                                                         | 0            |
| I2S Data In Pin              | GPIO pin for I2S data in                                                                                               | 0            |
| I2S Data Out Pin             | GPIO pin for I2S data out                                                                                              | 0            |
| I2S Word Select Pin          | GPIO pin for I2S word select                                                                                           | 0            |
| Контакт PTT                  | GPIO pin for the push-to-talk button. 0 uses GPIO 39                                                   | 0            |

### Remote Hardware module

Управление GPIO через mesh-сеть. Позволяет удалённому узлу читать и записывать состояния выводов GPIO на другом узле — полезно для активации реле, опроса переключателей или удалённого управления внешним оборудованием.

> ⚠️ **Warning:** Turning on **Allow undefined pin access** gives remote nodes access to all GPIO pins, which could interfere with the node's own hardware. Turn it on only on dedicated GPIO nodes.

| Настройка                        | Описание                                                                        | По умолчанию |
| -------------------------------- | ------------------------------------------------------------------------------- | ------------ |
| Разрешить неопределённый контакт | Разрешить доступ к любому GPIO-пину (риск для безопасности)  | Выкл         |
| Доступные контакты               | До 4 пинов GPIO, которые этот узел предоставляет для удалённого чтения и записи | Отсутствует  |
| Удаленное оборудование включено  | Turn remote GPIO access on                                                      | Выкл         |

### Neighbor Info module

Транслирует информацию о доступных услышанных соседей, включив ячейку сеточной топологии. Каждый включенный узел периодически делится списком других узлов которые он может слышать и их качество сигнала.

| Настройка                     | Описание                                                                                                                                            | По умолчанию                            |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| Информация о соседях включена | Turn neighbor broadcasting on                                                                                                                       | Выкл                                    |
| Передать через LoRa           | Also broadcast neighbor info over LoRa, not just MQTT and the connected app. Недоступно на канале используя ключ по умолчанию и имя | Выкл                                    |
| Интервал опроса GPS           | How often to broadcast the neighbor list, in seconds. Minimum 4 hours                                                               | 0 (firmware default) |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting module

Управляет встроенными светодиодами NeoPixel или другими адресуемыми RGB-светодиодами на поддерживаемом оборудовании. Может использоваться для визуальных статусовых индикаторов, световых уведомлений, или декоративных эффектов.

Pick the color with the swatch's **Preset colors** row or the **Red**, **Green** and **Blue** sliders.
**Advanced** opens number fields for the same three values.

| Настройка                 | Описание                                  | По умолчанию                 |
| ------------------------- | ----------------------------------------- | ---------------------------- |
| Ток                       | LED drive current, 0 to 31                | 10                           |
| Состояние LED             | Включить или выключить светодиод          | Выкл                         |
| Красный / Зеленый / Синий | Individual color channel values, 0 to 255 | Derived from the node number |

### Detection Sensor module

Превращает ваш узел в систему сигнализации на основе датчика движения или открытия двери. При обнаружении изменения состояния на выводе GPIO (например, сработал датчик движения или открылась дверь) узел отправляет по меш-сети оповещение.

| Настройка                                 | Описание                                                                                                                                                          | По умолчанию                                                                         |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Датчик определения включен                | Turn the detection sensor on. It also has to be on for nodes that receive its alerts or chart its log                                             | Выкл                                                                                 |
| GPIO контакт для мониторинга              | GPIO pin connected to the sensor. 0 disables the module                                                                                           | 0                                                                                    |
| Minimum time between detection broadcasts | Минимальный интервал между оповещениями                                                                                                                           | 0, which the firmware treats as 1 hour (12 hours on Router roles) |
| Понятное имя                              | Custom name for this sensor, used in its messages                                                                                                                 | Пусто                                                                                |
| Отправить колокольчик                     | Include a bell character in alerts                                                                                                                                | Выкл                                                                                 |
| State Broadcast Interval                  | How often to send the sensor's state whether or not anything was detected. **Unset** sends only on detection                                      | Не установлена                                                                       |
| Trigger Type                              | How the pin's state maps to a detection event: **High**, **Low**, **Rising Edge**, **Falling Edge**, **Either Edge High**, or **Either Edge Low** | Высокий                                                                              |
| Use Internal Pull-up                      | Включить внутренний подтягивающий резистор пина                                                                                                                   | Выкл                                                                                 |

### Paxcounter module

People counter using Wi-Fi and BLE probe requests. Засчитывает ближайшие устройства, пассивно прослушивая зондирующие запросы, чтобы телефоны и ноутбуки излучали при сканировании сетей. Доступно только на устройствах ESP32. Counts are approximate, since one person may carry several devices, so the module suits estimating foot traffic at trailheads or event venues.

| Настройка                               | Описание                                                                                                 | По умолчанию                            |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| BLE Threshold (dBm)  | The same cut-off for BLE advertisements                                                                  | 0, which the firmware treats as −80 dBm |
| Paxcounter включен                      | Turn people counting on. Wi-Fi and Bluetooth must both be off on the node for it to work | Выкл                                    |
| Интервал опроса GPS                     | Как часто сообщать подсчитывания                                                                         | —                                       |
| WiFi Threshold (dBm) | Ignore Wi-Fi probes weaker than this, so distant devices aren't counted                                  | 0, which the firmware treats as −80 dBm |

### Status Message module

The status message has no module screen. It is edited with the rest of the node's identity, on
[Settings — Radio & User](settings-radio-user#user-profile).

### Mesh Beacon module

Broadcasts an invitation to your mesh, and receives invitations from others. The entry appears only
on nodes running firmware 2.8.0 or newer. See [Local Mesh Discovery](discovery) for the full
walkthrough.

### TAK module

Интеграция Team Awareness Kit для совместимости с ATAK и WinTAK. The entry is labeled
**TAK (ATAK)**. Two things have to be true before it appears in the module list: the node runs
firmware 2.8.0 or newer, and its **Device Role** on **Settings → Device configuration → Device** is
set to **TAK** or **TAK Tracker**. См. [TAK Integration](tak) для детальной настройки и использования.

## Администрирование

### Удаленное администрирование

Remote administration changes another node's settings over the mesh. The target node must list your node's public key as one of its **Admin Key** entries, set on its **Settings → Security** screen.

1. Tap the target node in the node list to open its detail screen.
2. In the **Administration** card, tap **Remote Administration**. The app first establishes a session with the node, showing _Establishing remote session…_, then opens its **Settings**.
3. Измени конфигурацию.
4. Tap **Save** or **Save & restart**. The changes are sent over the mesh.

### Device Actions

**Settings → Administration** holds one-shot actions, each behind a confirmation dialog:

| Действие                    | Что она делает                                                                                                                                                                                                                                                                                                          |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Установить время            | Sends your phone's clock to the node                                                                                                                                                                                                                                                                                    |
| Перезагрузка                | Restarts the node                                                                                                                                                                                                                                                                                                       |
| Reboot into DFU mode        | Restarts an nRF52 node into its bootloader, where it stays off the mesh until new firmware is flashed or it is reset. On bootloader 2.6.0 and later it boots the installed firmware after 6 minutes with no update activity. Shown only for nRF52 nodes |
| Выключение                  | Powers the node down                                                                                                                                                                                                                                                                                                    |
| Сброс до заводских настроек | Returns every setting to its factory default                                                                                                                                                                                                                                                                            |
| Очистка списка нод сети     | Clears the node database. This dialog carries a **Preserve Favorites?** switch                                                                                                                                                                                                                          |

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys, and cannot be undone. Before you reset, use **Export configuration** to save the node's settings and **Backup Keys** on the Security screen to save its keys, so you can put both back afterwards.

### Бэкап & Восстановление

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

### Расширенные

**Settings → Advanced** collects the tools that read or rewrite local state, and is likewise shown
for your own node only: **Firmware Update** on OTA-capable hardware, **Clean Node Database**,
**TAK Server**, **Local Mesh Discovery**, and the **Debug Panel**.

#### Очистить базу данных нод

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

#### Панель отладки

Открывает вкладки **Пакет** и **Журналы приложений** для просмотра, фильтрации и экспорта диагностических выходов. См. [Отладочные журналы](debug-logs) для полного прохождения.

### App Settings

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

### О приложении

**Settings → About** carries the app's own identity rather than the node's, in three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one. It also lists the GitHub repository, the running app version, and
  **Acknowledgements** (see the next section).
- **Project information** — links to the website, to this documentation, and to the GNU GPL v3
  license the app is released under.

The footer states the copyright and that the app is free software with no warranty, which you
may redistribute under the same license.

### Благодарности

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries.

### Troubleshooting remote admin

- **"Could not reach node — try again or move closer."** The target may be out of range or offline, or it doesn't list your node's public key as an admin key. Check the **Admin Key** entries on the target node.
- **"Connect to a radio to administer remote nodes."** Your own node isn't connected. Reconnect it from the [Connections](connections) screen.
- **Изменения не применены** — чтобы некоторые настройки вступили в силу, нужно перезагрузить устройство. Попробуй перезагрузить после сохранения.

## Связанные темы

- [Settings — Radio & User](settings-radio-user) — core node and user profile settings
- [Ссылка на конфигурацию модуля](https://meshtastic.org/docs/configuration/module) — подробная документация по модулям на meshtastic.org
- [FAQ](https://meshtastic.org/docs/faq/) — общие вопросы на meshtastic.org
