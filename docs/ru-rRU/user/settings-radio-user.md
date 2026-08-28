---
title: Настройки - Радио и пользователь
parent: Руководство пользователя
nav_order: 7
last_updated: 2026-08-27
description: Настройте ваше радиоустройство, пресеты LoRa, пользовательский профиль, обмен местоположением, управление питанием и безопасность.
aliases:
  - настройки
  - radio-config
  - user-config
  - lora
---

# Настройки - Радио и пользователь

Настройте радиоустройство и параметры идентификации пользователя.

## Настройки пользователя

### Профиль пользователя

| Настройка                | Описание                                                                                                                                                                                                                                     |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Полное имя               | Ваше отображаемое имя (до 39 символов)                                                                                                                                                                                    |
| Короткое имя             | 4-символьное сокращённое имя                                                                                                                                                                                                                 |
| Unmessageable            | Marks the node as one nobody should try to message — for an unmonitored or infrastructure node. Other clients hide it from the contact list. Needs supporting firmware                                       |
| Лицензированный оператор | Enable if you hold an amateur radio license (permits higher power). Turning it on relabels **Long Name** as **Call Sign** and adds a separate Long Name field, and is staged behind a confirmation dialog |

### Применение изменений

После изменения настроек нажмите **Сохранить** чтобы записать конфигурацию в ваше радиоустройство. Устройство может перезагрузиться для применения изменений.

## Настройки

### Настройки устройства

| Настройка                | Описание                                                                                                                                                                                                 | По умолчанию |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| Роль                     | Поведение ноды (Client, Router и т.д.) — each option carries its own description in the picker. Choosing Router asks for confirmation | Client       |
| Режим ретрансляции       | How the node retransmits messages; each mode is described in the picker                                                                                                                                  | Всё          |
| Передача информации ноды | Интервал для передачи информации ноды                                                                                                                                                                    | 10800        |
| Двойное нажатие кнопки   | Treat a double tap as a button press                                                                                                                                                                     | Включено     |
| Triple Click Ad Hoc Ping | Send an ad-hoc position ping on a triple click                                                                                                                                                           | Disabled     |
| LED Heartbeat            | Blink the status LED periodically                                                                                                                                                                        | Enabled      |
| Time Zone                | POSIX time-zone string for the device clock, with buttons to copy your phone's zone or clear it                                                                                                          | —            |
| Button / Buzzer GPIO     | Advanced: which pins the button and buzzer are wired to                                                                                                                                  | —            |

### Настройка LoRa

| Настройка                 | Описание                                                                                                                                                                                         | По умолчанию                                             |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------- |
| Регион / Страна           | Область регулирования для диапазонов частот                                                                                                                                                      | Не установлено (необходимо настроить) |
| Режим работы модема       | Компромисс между скоростью и дальностью                                                                                                                                                          | LongFast                                                 |
| Лимит хопов               | Максимальное количество ретрансляций                                                                                                                                                             | 3                                                        |
| Мощность передачи         | Мощность передачи (дБм); 0 = максимально разрешённая для региона                                                                                                              | 0 (максимум региона)                  |
| Frequency Override        | Overrides the computed operating frequency outright (MHz). It does not offset the calculated value — leave at 0 unless you know you need a specific frequency | 0 (use calculated)                    |
| Полоса пропускания канала | Настройка пропускной способности                                                                                                                                                                 | По умолчанию для предустановки                           |
| Use Preset                | On by default. Turn it off to set Spread Factor, Coding Rate and Bandwidth by hand instead of taking them from the modem preset                                                  | On                                                       |
| Spread Factor             | Manual mode only: 7–12. Higher spreads further but slower                                                                                                        | From preset                                              |
| Coding Rate               | Manual mode only: 5–8. More redundancy costs airtime                                                                                                             | From preset                                              |
| Frequency Slot            | Which slot within the region's band to use. 0 derives it from the primary channel name                                                                                           | 0 (automatic)                         |
| Transmit Enabled          | Turning this off makes the node receive-only                                                                                                                                                     | On                                                       |
| Override Duty Cycle       | Ignore the region's duty-cycle limit. Only legal where you are permitted to                                                                                                      | Off                                                      |
| Ignore MQTT               | Drop packets that arrived from MQTT rather than over the air                                                                                                                                     | Off                                                      |
| OK to MQTT                | Allow your packets to be forwarded to MQTT by gateways                                                                                                                                           | Off                                                      |
| RX Boosted Gain           | Extra receive gain on SX126x radios; costs a little current                                                                                                                                      | Off                                                      |
| PA fan disabled           | Turn off the power-amplifier fan on hardware that has one                                                                                                                                        | Off                                                      |

> ⚠️ **Важно:** Вы **обязаны** установить свой регион перед отправкой. Работа без правильного региона может нарушать местные правила радиопользования. Смотрите [руководство по настройке региона](https://meshtastic.org/docs/getting-started/initial-config) на сайте meshtastic.org для получения подробной информации.

### Предустановки модема

> 💡 **Совет:** Значения **порога SNR** специально отрицательные. LoRa может декодировать сигналы _ниже_ уровня шума, поэтому более отрицательный предел означает, что пресет допускает более слабый, шумный сигнал (больший радиус действия). Смотрите [Как работает измеритель сигнала](signal-meter) для полного объяснения.

| Предустановка      | Диапазон                | Скорость                  | Предел SNR               | Лучше всего для                                                                                                   |
| ------------------ | ----------------------- | ------------------------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 км   | 21.9 кб/с | −7.5 дБ  | Плотная городская застройка с прямой видимостью; приложения, требующие высокой передачи данных                    |
| Short Fast         | ~3 км   | 10.9 кб/с | −7.5 дБ  | Городские районы; здания в пределах нескольких кварталов                                                          |
| Short Slow         | ~5 км   | 5.5 кб/с  | -10 дБ                   | Пригородная зона с коротким диапазоном; умеренная плотность застройки                                             |
| Medium Fast        | ~5 км   | 5.5 кб/с  | −12.5 дБ | Пригородные районы; умеренная плотность застройки                                                                 |
| Medium Slow        | ~8 км   | 1.1 кб/с  | -15 дБ                   | Пригородный/сельский; умеренный диапазон со сниженной скоростью                                                   |
| Long Turbo         | ~10 км  | 4.4 кб/с  | −12.5 дБ | Диапазон, похожий на Long Fast, но с полосой пропускания 500 кГц; более высокая пропускная способность            |
| Long Fast          | ~10 км  | 1.1 кб/с  | −17.5 дБ | **Общее использование (по умолчанию)** — сбалансированный диапазон и скорость                  |
| Long Moderate      | ~20 км  | 0.34 кб/с | −17.5 дБ | Сельская местность с некоторым рельефом; случайное использование                                                  |
| Lite Fast          | ~5 км   | 5.5 кб/с  | −12.5 дБ | Диапазон RU 866 МГц SRD (ширина полосы 125 кГц); аналогично Medium Fast                        |
| Lite Slow          | ~10 км  | 1.1 кб/с  | -15 дБ                   | Диапазон RU 866 МГц SRD (ширина полосы 125 кГц); аналогично Long Fast                          |
| Narrow Fast        | ~5 км   | 2.7 кб/с  | -10 дБ                   | Диапазон RU 868 МГц (полоса пропускания 62,5 кГц); предотвращает помехи с другими устройствами |
| Narrow Slow        | ~10 км  | 1.1 кб/с  | −12.5 дБ | Диапазон RU 868 МГц (ширина полосы 62,5 кГц); аналогично Long Fast                             |
| ~~Long Slow~~      | ~30 км  | 0.18 кб/с | −20 дБ                   | ⚠️ **Устарел** — всё ещё доступен для выбора, но может быть удален в будущих версиях прошивки                     |
| ~~Very Long Slow~~ | ~40+ км | 0.09 кб/с | −20 дБ                   | ⚠️ **Устарел** — всё ещё доступен для выбора, но может быть удален в будущих версиях прошивки                     |

> i **Примечание:** В этой таблице используются общие короткие имена. В выпадающем меню пресетов приложения они читаются как **Short Range - Fast**, **Long Range - Fast**, **Lite - Fast**, **Narrow - Fast** и так далее.

#### Выбор предустановки модема

Предустановка модема управляет основным компромиссом между **дальностью** и **скоростью передачи данных**:

- **Медленные предустановки** используют большее распространение, что позволяет декодировать сигналы при более слабых уровнях сигнала (ниже порога SNR). Это означает большую дальность, но меньше байт в секунду.
- **Быстрые предустановки** передают больше данных за одну передачу, но требуют более сильного сигнала для декодирования.

**Практическое руководство:**

- **Городская сеть (много нод, короткие расстояния):** Используй **Long Fast** (по умолчанию) или **Short Fast**. Более высокая скорость означает меньшее переполнение канала эфирного времени, когда многие ноды используют канал.
- **Сельская/разреженная сеть (мало нод, большие расстояния):** Используй **Long Moderate**. Дальность важнее скорости, когда ноды находятся далеко друг от друга.
- **Соответствие нормативным требованиям RU 866/868 МГц:** Используй **Lite Fast**, **Lite Slow**, **Narrow Fast** или **Narrow Slow** — они оптимизированы для диапазонов RU SRD/868 МГц с более узкой полосой пропускания.
- **Фиксированные инфраструктурные связи:** Используй **Short Turbo** или **Long Turbo** для выделенных соединений точка-точка с хорошими антеннами и прямой видимостью.
- **Смешанные среды:** Используй **Long Fast** — это настройка по умолчанию в сообществе и она обеспечит совместимость с другими в вашем регионе.

> ⚠️ **Важно:** Все ноды в одном канале **должны** использовать один и тот же пресет модема. Ноды с несовпадающими предустановками не смогут обмениваться данными, даже если они используют одну частоту и ключ шифрования.

> 💡 **Совет:** Указанные выше оценки дальности предполагают ровную местность и небольшие антенны. Преимущество высоты (вершина холма, крыша) значительно увеличивает эффективную дальность. Хорошо размещённый маршрутизатор с Long Fast часто может превзойти наземную ноду с Long Slow.

### Параметры дисплея

These control the **radio's own screen**, not the app's.

| Настройка             | Описание                                                                                                                                                  |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen on for         | How long the display stays lit before sleeping                                                                                                            |
| Carousel interval     | How often the device cycles between screens on its own                                                                                                    |
| Display mode          | Screen layout/density used by the firmware                                                                                                                |
| Display units         | Metric or Imperial on the device's screen                                                                                                                 |
| Use 12h clock format  | Show the device clock as 12-hour rather than 24-hour                                                                                                      |
| Bold heading          | Draw the screen's heading text in bold                                                                                                                    |
| Flip screen           | Rotate the display 180° for an inverted mounting                                                                                                          |
| OLED type             | Авто, SSD1306, SH1106, SH1107                                                                                                                             |
| Wake on tap or motion | Light the screen when the device is tapped or moved                                                                                                       |
| Compass orientation   | Rotation offset for the compass rose (0°, 90°, 180°, 270°)                                                                             |
| Always point north    | Locks the compass rose north-up instead of rotating it with your heading. Independent of Compass orientation — neither replaces the other |

### Настройки местоположения

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Настройка                                       | Описание                                                                                                                                              |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| GPS Mode (Physical Hardware) | Three-state: GPS enabled, disabled, or not present. Not a simple on/off                                               |
| GPS Polling Interval                            | How often the radio asks its GPS for a fix                                                                                                            |
| Broadcast Interval                              | How often the position is shared with the mesh                                                                                                        |
| Умная позиция                                   | Broadcast based on movement rather than purely on the clock                                                                                           |
| Smart Interval                                  | With Smart Position on, the shortest gap between broadcasts                                                                                           |
| Smart Distance                                  | With Smart Position on, how far you must move before broadcasting                                                                                     |
| Фиксированная позиция                           | Use a manually entered latitude, longitude and altitude instead of the GPS                                                                            |
| Position Flags                                  | A group of toggles choosing which fields ride along with a position — altitude, its reference and precision, satellites in view, timestamp, and so on |
| GPS EN / Receive / Transmit GPIO                | Advanced: the pins the GPS module is wired to                                                                                         |

### Настройка питания

| Настройка                                        | Описание                                                        |
| ------------------------------------------------ | --------------------------------------------------------------- |
| Enable power saving mode                         | Let the radio sleep aggressively between activity               |
| Shutdown on power loss                           | Power the device down after external power disappears           |
| Super deep sleep duration                        | How long the deepest sleep state lasts                          |
| Minimum wake time                                | The shortest time the radio stays awake once woken              |
| Wait for Bluetooth duration                      | How long to wait for a phone to connect before sleeping         |
| ADC multiplier override                          | Turn on a manual correction for battery-voltage readings        |
| ADC multiplier override ratio                    | The correction factor itself, used only when the override is on |
| Battery INA_2XX I2C address | Address of an external INA-series power sensor, if fitted       |

### Настройка сети

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Настройка                        | Описание                                                                                                                   |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| WiFi enabled                     | Enable the WiFi radio (ESP32 devices)                                                                   |
| SSID                             | Network name to connect to. **Scan WiFi QR code** fills this and the password from a standard WiFi QR code |
| Password                         | Пароль сети                                                                                                                |
| Ethernet enabled                 | Use a wired connection on hardware that has one                                                                            |
| IPv4 mode                        | DHCP, or a static address configured with the four fields below                                                            |
| Wifi IP / Subnet / Gateway / DNS | The static address, only used when IPv4 mode is static                                                                     |
| UDP broadcasting                 | Share mesh traffic with other nodes over the local network                                                                 |
| NTP server                       | Сервер синхронизации времени                                                                                               |
| rsyslog server                   | Удалённый сервер логирования                                                                                               |

![Поле IP-адреса](../../assets/screenshots/settings_ipv4_field.png)

### Настройка Bluetooth

| Настройка             | Описание                                                                                               |
| --------------------- | ------------------------------------------------------------------------------------------------------ |
| Bluetooth включен     | Включение/отключение BLE радиостанции                                                                  |
| Режим сопряжения      | Фиксированный PIN, случайный PIN или без PIN                                                           |
| Фиксированный PIN-код | PIN code for pairing. Must be **exactly six digits** — the field rejects anything else |

### Настройки безопасности

| Настройка                        | Описание                                                                                                                                                                                                                          |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Публичный ключ                   | Публичный ключ твоей ноды (только для чтения)                                                                                                                                                                  |
| Ключ администратора              | Keys permitted to administer this node remotely — up to three                                                                                                                                                                     |
| Приватный ключ                   | Your node's private key (handle securely). Shown redacted when you are viewing another node over remote admin — the firmware does not send it                                                  |
| Regenerate Private Key           | Issues a new keypair for this node, behind a confirmation. Every peer that knew your old key must learn the new one                                                                                               |
| Direct Message Key               | The key used for direct-message encryption                                                                                                                                                                                        |
| ~~Канал администратора включен~~ | ⚠️ Удалено — теперь настраивается автоматически при установке ключа администратора                                                                                                                                                |
| Журнал отладки                   | Выводить живой отладочный лог через последовательный порт/Bluetooth                                                                                                                                                               |
| Последовательная включена        | Включить доступ к последовательной консоли (перемещено из настроек устройства)                                                                                                                                 |
| Управляемый режим                | Restrict non-admin channel changes. Only selectable once an Admin Key is set                                                                                                                                      |
| Резервное копирование            | Сохранить зашифрованную резервную копию ключей ноды на этом устройстве (только для Android)                                                                                                                    |
| Восстановить ключи               | Запишисать сохранённые ключи обратно на ноду (доступно, как только есть резервная копия)                                                                                                                       |
| Удалить резервную копию ключа    | Удалить сохранённую резервную копию ключа с этого устройства                                                                                                                                                                      |
| Уровень защиты                   | Подлинность пакета — как обрабатываются неподписанные или пересылаемые пакеты: **Строго**, **Сбалансировано** или **Совместимо** (требуется поддержка прошивкой; Строго требует подтверждения) |

#### Lockdown Mode

Lockdown encrypts the device's storage and requires a passphrase for each connection. It needs
supporting firmware; the row does not appear otherwise.

Enabling it asks you to set and confirm a passphrase, and to acknowledge that **it locks the debug
(SWD) port on hardware that supports locking**. You can turn lockdown off again at any time with
the passphrase, and a full device erase restores the hardware regardless.

Alongside the passphrase you set the limits that end a session automatically:

| Field                                    | What it does                                      |
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

![Поле пароля](../../assets/screenshots/settings_password_field.png)

Настройки используют стандартные элементы управления предпочтениями — выпадающие списки, переключатели и ползунки:

| Управление        | Снимок экрана                                                        |
| ----------------- | -------------------------------------------------------------------- |
| Выпадающий список | ![Выпадающий список](../../assets/screenshots/settings_dropdown.png) |
| Переключатель     | ![Переключатель](../../assets/screenshots/settings_switch.png)       |
| Ползунок          | ![Ползунок](../../assets/screenshots/settings_slider.png)            |

## Связанные темы

- [Настройки — Модули и администрирование](settings-module-admin) — дополнительные модули и управление устройством
- [Измеритель сигнала](signal-meter) — как предустановки модема влияют на пороги качества сигнала
- [Конфигурация LoRa](https://meshtastic.org/docs/configuration/radio/lora) — подробная справка по настройкам LoRa на meshtastic.org
- [Начальная конфигурация](https://meshtastic.org/docs/getting-started/initial-config) — руководство по настройке региона на meshtastic.org

---

