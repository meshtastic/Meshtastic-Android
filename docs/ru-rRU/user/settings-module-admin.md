---
title: Настройки — Модули и администрирование
parent: Руководство пользователя
nav_order: 8
last_updated: 2026-08-27
description: Настрой дополнительные функциональные модули (MQTT, телеметрия, готовые сообщения, TAK и другие) и выполняй администрирование устройств.
aliases:
  - modules
  - module-config
  - administration
---

# Настройки — Модули и администрирование

Настрой дополнительные функциональные модули и выполняй управление устройством. Модули расширяют Meshtastic с помощью специализированных возможностей — каждый из них можно включать или отключать отдельно.

> 💡 **Совет:** Тебе нужно включать только те модули, которые действительно используешь. Отключение неиспользуемых модулей снижает время передачи, экономит батарею и упрощает конфигурацию.

Настройки модулей используют макет на основе карточек с переключателями, выпадающими списками, текстовыми полями и ползунками:

![Переключатель](../../assets/screenshots/settings_switch.png)

![Выпадающий список](../../assets/screenshots/settings_dropdown.png)

![Текстовое поле](../../assets/screenshots/settings_text_field.png)

![Настройки расположения карточек](../../assets/screenshots/settings_titled_card.png)

## Конфигурация модуля

### Модуль MQTT

Мосты передают сообщения туда и обратно от брокера MQTT для подключения к интернету. Ты так расширишь сеть за пределы радиуса действия или интегрируешь её с системами домашней автоматизации.

| Настройка                | Описание                                                                                                                                                                                  |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Включено                 | Переключить MQTT мост                                                                                                                                                                     |
| Сервер                   | Адрес MQTT брокера                                                                                                                                                                        |
| Имя пользователя         | Имя пользователя для аутентификации                                                                                                                                                       |
| Пароль                   | Пароль аутентификации                                                                                                                                                                     |
| Шифрование               | Зашифровать MQTT-пейлоады                                                                                                                                                                 |
| JSON Output              | Publish and consume MQTT messages as JSON. Marked deprecated in the protobuf schema, but it is still the only toggle for this behaviour and the firmware still honours it |
| TLS                      | Использовать защищённое соединение                                                                                                                                                        |
| Корневая тема            | Базовый путь темы MQTT                                                                                                                                                                    |
| Proxy to client enabled  | Let a connected phone carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                       |
| MQTT proxy on this phone | The phone-side half of the above: whether _this_ phone is currently acting as that relay. See [MQTT](mqtt)                                                |
| Отчет карты              | Publish position to the public map — see below                                                                                                                                            |

**Map Report** expands into its own group:

| Настройка          | Описание                                                                                                                        |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| Включено           | Publish to the public map at all                                                                                                |
| Share location     | Explicit consent to include your position. Map reporting will not save without it                               |
| Position precision | How coarsely your position is published                                                                                         |
| Publish interval   | How often to report. Must be **at least 3600 s (1 hour)** — the app blocks saving below that |

См. [MQTT](mqtt) для подробного руководства по использованию, включая шифрование, конфиденциальность и настройку брокера.

### Последовательный модуль

Позволяет общаться через последовательный порт с внешними устройствами (GPS-модулями, датчиками или собственной техникой). Когда включено, последовательный порт ноды может отправлять и получать данные в формате protobuf или текст, что позволяет внешним микроконтроллерам или компьютерам взаимодействовать с сетью.

| Настройка                    | Описание                                                                                                                                                                                            |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Serial enabled               | Включить последовательное соединение                                                                                                                                                                |
| Echo enabled                 | Echo получил обратно последовательные данные                                                                                                                                                        |
| Serial mode                  | Which protocol the port speaks — Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, VE.Direct, MeshSolar config, Log, or Log (text only) |
| RX / TX                      | GPIO pins for the serial connection                                                                                                                                                                 |
| Serial baud rate             | Port speed                                                                                                                                                                                          |
| Timeout                      | How long to wait before considering an incoming message complete                                                                                                                                    |
| Override console serial port | Take over the port the debug console normally uses                                                                                                                                                  |

### Модуль внешних уведомлений

Управляет зуммером, светодиодом или вибрацией на вашем радиооборудовании. Полезно для устройств, которым нужно физически сигнализировать о приходе сообщения — особенно удобно для неоснащенных персоналом или уличных установок.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles.

| Настройка                                                  | Описание                                                                                            |
| ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| External notification enabled                              | Master toggle for the module                                                                        |
| Alert message LED / buzzer / vibra                         | Which outputs fire on an incoming message                                                           |
| Alert bell LED / buzzer / vibra                            | Which outputs fire on a received bell character                                                     |
| Output LED (GPIO)                       | Pin the LED is wired to                                                                             |
| Output LED active high                                     | Whether the LED pin is active high or low                                                           |
| Output buzzer (GPIO)                    | Pin the buzzer is wired to                                                                          |
| Output vibra (GPIO)                     | Pin the vibration motor is wired to                                                                 |
| Use PWM buzzer                                             | Drive the buzzer with PWM, which allows tones rather than a single pitch                            |
| Использовать I2S как буззер                                | Send the alert through an I2S audio output instead                                                  |
| Продолжительность вывода (миллисекунды) | How long a single alert lasts                                                                       |
| Таймаут Nag (в секундах)                | Keep repeating the alert for this long until it is acknowledged. 0 disables nagging |
| Рингтон                                                    | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file              |

### Модуль Store & Forward

Буферизирует сообщения для узлов, которые временно были недоступны, а затем ретранслирует их, когда эти узлы переподключаются. Важное значение для сеток, где узлы входят и выходят вне диапазона регулярно - обеспечивает отсутствие потери сообщений при коротких разъединениях.

| Настройка                                  | Описание                                                                                                                               |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| Включено                                   | Активировать режим хранения и пересылки                                                                                                |
| Heartbeat                                  | Периодически объявлять о наличии функции хранения и пересылки у этого узла                                                             |
| Записи                                     | Максимальное количество сохраненных сообщений                                                                                          |
| История возврата (макс) | Максимум сообщений для повтора                                                                                                         |
| Возврат истории (окно)  | Окно времени для повтора                                                                                                               |
| Сервер                                     | Действовать как сервер хранения и пересылки для mesh-сети (требуется большой объем памяти, например, ESP32 с PSRAM) |

> 💡 **Совет:** Хранение и пересылка лучше всего работает на узлах с достаточной памятью (ESP32 с PSRAM). Узлы маршрутизатора являются идеальными кандидатами, так как они обычно всегда включены.

### Модуль проверки дальности

> ⚠️ **Warning:** Range Test only works on a secured primary channel. While your primary channel
> still uses the default public key, the Enabled, Interval and Save-CSV controls stay disabled, and
> saving force-disables the module if the channel has reverted to public.

Автоматизированный инструмент для проверки дальности и оценки качества связи между нодами. Когда включено, нода периодически отправляет сообщения с увеличивающимся счетчиком. Приёмная нода записывает эти сообщения, что позволяет тебе уйти пешком или уехать на машине, а потом проанализировать, на каком расстоянии сообщения перестали приходить.

| Настройка                                | Описание                                      |
| ---------------------------------------- | --------------------------------------------- |
| Включено                                 | Активировать проверку дальности               |
| Интервал отправки (с) | Время между передачами проверок               |
| Сохранить в CSV                          | Журнал полученных тестовых данных на SD-карту |

### Модуль телеметрии

Контролирует какими телеметрическими данными ваш узел делится с сеткой. Телеметрия включает данные о состоянии устройства (заряд батареи, время работы) и данные с датчиков окружающей среды (температура, влажность, давление).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely.

| Настройка                                   | Описание                                                                                                                                                                          |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Отправлять телеметрию устройства            | Master toggle for device metrics. Only shown on firmware 2.7.12 and newer                                                         |
| Интервал обновления метрик устройства       | How often to report battery, uptime and channel utilisation                                                                                                                       |
| Модуль метрик окружения включен             | Report the attached environment sensors                                                                                                                                           |
| Интервал обновления метрик среды            | How often to report them                                                                                                                                                          |
| Показатели окружения на экране включены     | Also show these readings on the device's own display                                                                                                                              |
| Использовать метрику окружения в Fahrenheit | Use °F on the device's display. This is the radio's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale) |
| Air quality metrics module enabled          | Report particulate and CO₂ sensor data                                                                                                                                            |
| Интервал обновления данных качества воздуха | How often to report them                                                                                                                                                          |
| Модуль метрик питания включен               | Report the per-channel voltage and current readings                                                                                                                               |
| Интервал обновления метрик электропитания   | How often to report them                                                                                                                                                          |
| Включить метрики питания на экране          | Also show power readings on the device's display                                                                                                                                  |

Посмотрите [Телеметрия и датчики](telemetry-and-sensors) — для получения информации о поддерживаемых датчиках и рекомендациях по настройке.

### Модуль шаблонных сообщений

Предварительно настроенные сообщения, доступные через физические кнопки устройства (для радиостанций с поворотными энкодерами, кнопочными панелями или аналогичным оборудованием ввода). Определите список быстрых сообщений, которые могут быть переданы без подключённого телефона — идеально подходит для использования в поле.

| Настройка                                 | Описание                                                                                                  |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| ~~Canned message enabled~~                | ⚠️ **Deprecated** in the protobuf schema                                                                  |
| Сообщения                                 | Список сообщений, разделённых новой строкой                                                               |
| Отправить колокольчик                     | Send a bell character alongside the message, so a receiving node's External Notification module can sound |
| Rotary encoder enabled                    | Use a rotary encoder as the input device                                                                  |
| GPIO pin for rotary encoder A / B / press | The three pins the encoder is wired to                                                                    |
| Generate input event on press / CW / CCW  | Which key event each encoder action produces                                                              |
| Вверх/Вниз/Выбирать включён               | A separate, simpler input scheme using up/down/select buttons rather than an encoder                      |
| ~~Allow input source~~                    | ⚠️ **Deprecated** in the protobuf schema                                                                  |

### Звуковой модуль

Поддержка аудио Codec2 для низкополосной голосовой связи через сетку. Это **экспериментальная функция**, которая кодирует голос в очень маленькие пакеты данных с помощью кодека Codec2.

| Настройка                          | Описание                                               |
| ---------------------------------- | ------------------------------------------------------ |
| Включено                           | Активировать модуль аудио                              |
| Частота кодирования                | Компромисс между качеством звука и полосой пропускания |
| PTT Pin                            | GPIO pin for the push-to-talk button                   |
| Выбор слов I2S                     | GPIO контакт для I2S WS                                |
| I2S Вход данных                    | Пин GPIO для I2S DIN                                   |
| I2S Выход данных                   | Пин GPIO для I2S DOUT                                  |
| I2S Clock (SCK) | GPIO pin for the I2S bit clock                         |

> ℹ️ **Note:** Audio requires specific hardware (I2S microphone and speaker). Качество голоса очень низкополосное — представьте себе «разборчивую радиосвязь», а не качество телефонного звонка.

### Удаленный аппаратный модуль

Управление GPIO через mesh-сеть. Позволяет удалённому узлу читать и записывать состояния выводов GPIO на другом узле — полезно для активации реле, опроса переключателей или удалённого управления внешним оборудованием.

| Настройка                     | Описание                                                                        |
| ----------------------------- | ------------------------------------------------------------------------------- |
| Включено                      | Активировать удаленный GPIO доступ                                              |
| Разрешить неопределенные пины | Разрешить доступ к любому GPIO-пину (риск для безопасности)  |
| Доступные пины                | До 4 пинов GPIO, которые этот узел предоставляет для удалённого чтения и записи |

> ⚠️ Предупреждение: Включение опции «Разрешить неопределённые пины» даёт удалённым узлам доступ ко всем выводам GPIO, что может нарушить работу собственного аппаратного обеспечения радио. Включать только на выделенных GPIO-нодах.

### Модуль информации о соседях

Транслирует информацию о доступных услышанных соседей, включив ячейку сеточной топологии. Каждый включенный узел периодически делится списком других узлов которые он может слышать и их качество сигнала.

| Настройка                                  | Описание                                                                                                                                         |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Включено                                   | Включить трансляцию соседей                                                                                                                      |
| Интервал обновления (с) | Как часто транслировать список соседей                                                                                                           |
| Передача через LoRa                        | Также транслировать информацию соседей по LoRa, а не только MQTT/телефон. Недоступно на канале используя ключ по умолчанию и имя |

Смотрите [Discovery](discovery) за тем как использовать соседние данные для сеточно топологического исследования.

### Модуль окружающего освещения

Управляет встроенными светодиодами NeoPixel или другими адресуемыми RGB-светодиодами на поддерживаемом оборудовании. Может использоваться для визуальных статусовых индикаторов, световых уведомлений, или декоративных эффектов.

| Настройка                 | Описание                                                         |
| ------------------------- | ---------------------------------------------------------------- |
| Состояние светодиода      | Включить или выключить светодиод                                 |
| Ток                       | Текущий лимит светодиодов (0–31)              |
| Красный / Зеленый / Синий | Индивидуальные значения цветов канала (0–255) |

### Модуль определения датчика

Превращает ваш узел в систему сигнализации на основе датчика движения или открытия двери. При обнаружении изменения состояния на выводе GPIO (например, сработал датчик движения или открылась дверь) узел отправляет по меш-сети оповещение.

| Настройка                                                | Описание                                                                                                                                           |
| -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Включено                                                 | Активировать датчик обнаружения                                                                                                                    |
| Пин датчика                                              | Пин GPIO, подключенный к датчику                                                                                                                   |
| Триггер срабатывания                                     | Как состояние пина интерпретируется как событие обнаружения (например, активный высокий/низкий уровень, срабатывание по фронту) |
| Использовать режим подтяжки входа                        | Включить внутренний подтягивающий резистор пина                                                                                                    |
| Минимальное количество трансляций (с) | Минимальный интервал между оповещениями                                                                                                            |
| Трансляция состояния (с)              | Интервал периодической отправки состояния                                                                                                          |
| Отправлять 🔔                                            | Включать символ колокола в оповещения                                                                                                              |
| Имя датчика                                              | Пользовательское имя для этого датчика                                                                                                             |

### Paxcounter Модуль

Подсчёт количества людей по Wi-Fi и BLE-запросам от устройств. Засчитывает ближайшие устройства, пассивно прослушивая зондирующие запросы, чтобы телефоны и ноутбуки излучали при сканировании сетей. Доступно только на устройствах ESP32.

| Настройка                                  | Описание                                                                                                         |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| Включено                                   | Активировать подсчет людей                                                                                       |
| Интервал обновления (с) | Как часто сообщать подсчитывания                                                                                 |
| WiFi RSSI threshold                        | Ignore WiFi probes weaker than this, so distant devices are not counted (defaults to −80 dBm) |
| BLE RSSI threshold                         | The same cut-off for BLE advertisements (defaults to −80 dBm)                                 |

> 💡 **Совет:** Paxcounter полезен для приблизительной оценки пешеходного потока в местах начала маршрутов, на мероприятийных площадках или в других локациях. Счетчики приблизительны — один человек может иметь несколько устройств.

### Status Message Module

Publishes a short free-text status line for your node, which other nodes can display alongside it.

| Настройка                     | Описание                                                                                                                                                                         |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Строка фактического состояния | Up to 80 characters. The **✕** in the field clears it. (That is the app's own label for the field, verbatim.) |

Saving takes effect immediately — this is one of the few module settings that never asks the
node to reboot.

> ℹ️ **Note:** The screen only appears for firmware that reports support for the status-message
> module. If you do not see it in the module list, your node's firmware does not have it.

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. See
[Discovery](discovery) for the full walkthrough.

### Модуль TAK

> ℹ️ **Note:** This module only appears in the list once the node's **Device Role** (Device Config)
> is set to **TAK** or **TAK Tracker**. Change the role first, or the entry will not be there.

Интеграция Team Awareness Kit для совместимости с ATAK и WinTAK. См. [TAK Integration](tak) для детальной настройки и использования.

## Администрирование

### Удаленное администрирование

Удалённо настраивай ноды, которые используют твой ключ администрирования:

1. Выбери целевую ноду в списке нод.
2. Перейди в **Настройки** этой ноды.
3. Измени конфигурацию.
4. Нажми **Сохранить** — изменения отправляются через сеть.

> ⚠️ **Требуется:** Ключ администратора настроенный как на вашем узле, так и на целевом узле.

### Очистить базу данных нод

Prunes your local node database. Two independent controls:

- An **age slider** — remove nodes not heard from within that window.
- **Clean unknown nodes only** — restrict the purge to nodes that never sent their user info,
  leaving named nodes alone regardless of age.

### Сброс к заводским настройкам

Сбрасывает все настройки к заводским. **Это действие нельзя отменить.**

### Перезагрузка

Удаленно перезагрузить подключенный или управляемый узел.

### Панель отладки

Открывает вкладки **Пакет** и **Журналы приложений** для просмотра, фильтрации и экспорта диагностических выходов. См. [Отладочные журналы](debug-logs) для полного прохождения.

### About

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

### Устранение неполадок удалённого администрирования

- **"Нет ответа от целевого узла"** — цель может находиться вне диапазона, в автономном режиме или иметь несоответствующий ключ администратора. Проверьте соответствие ключа администратора на обоих узлах.
- **Изменения не применены** — чтобы некоторые настройки вступили в силу, нужно перезагрузить устройство. Попробуй перезагрузить после сохранения.
- **Не видны настройки удалённой ноды** — убедись, что твоя нода имеет админ-ключ для целевой ноды. Админ-канал настраивается автоматически, когда задан ключ администратора.

## Связанные темы

- [Настройки — Радио и Пользователь](settings-radio-user) — основные настройки радио и профиля пользователя
- [Ссылка на конфигурацию модуля](https://meshtastic.org/docs/configuration/module) — подробная документация по модулям на meshtastic.org
- [FAQ](https://meshtastic.org/docs/faq/) — общие вопросы на meshtastic.org

---

