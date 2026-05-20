---
title: Настройки - Радио и пользователь
parent: Руководство пользователя
nav_order: 7
last_updated: 2026-05-13
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

| Настройка                | Описание                                                                                                            |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Полное имя               | Ваше отображаемое имя (до 39 символов)                                                           |
| Короткое имя             | 4-символьное сокращённое имя                                                                                        |
| Лицензированный оператор | Включите, если у вас есть лицензия радиолюбителя (позволяет использовать более высокую мощность) |

### Применение изменений

После изменения настроек нажмите **Сохранить** чтобы записать конфигурацию в ваше радиоустройство. Устройство может перезагрузиться для применения изменений.

## Конфигурация радио

### Настройки устройства

| Настройка                                  | Описание                                                                                  | По умолчанию |
| ------------------------------------------ | ----------------------------------------------------------------------------------------- | ------------ |
| Роль                                       | Поведение ноды (Client, Router и т.д.) | Client       |
| Режим ретрансляции                         | Как нода повторно передает сообщения                                                      | Всё          |
| Node Info Broadcast (s) | Interval for broadcasting node info                                                       | 10800        |
| Двойное нажатие кнопки                     | Action for double-tap button press                                                        | Включено     |

### Настройка LoRa

| Настройка                 | Описание                                                                            | По умолчанию                              |
| ------------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------- |
| Регион / Страна           | Regulatory region for frequency bands                                               | Unset (must configure) |
| Режим работы модема       | Speed/range tradeoff                                                                | LongFast                                  |
| Лимит хопов               | Maximum retransmit hops                                                             | 3                                         |
| Мощность передачи         | Мощность передачи (дБм); 0 = максимально разрешённая для региона | 0 (максимум региона)   |
| Частотное смещение        | Точная настройка частоты (МГц)                                   | 0                                         |
| Полоса пропускания канала | Настройка пропускной способности                                                    | По умолчанию для предустановки            |

> ⚠️ **Важно:** Вы **обязаны** установить свой регион перед отправкой. Работа без правильного региона может нарушать местные правила радиопользования. Смотрите [руководство по настройке региона](https://meshtastic.org/docs/getting-started/initial-config) на сайте meshtastic.org для получения подробной информации.

### Предустановки модема

| Предустановка      | Диапазон                | Скорость                  | Предел SNR               | Лучше всего для                                                                                          |
| ------------------ | ----------------------- | ------------------------- | ------------------------ | -------------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 км   | 21.9 кб/с | −5 дБ                    | Плотная городская застройка с прямой видимостью; приложения, требующие высокой передачи данных           |
| Short Fast         | ~3 км   | 10.9 кб/с | −7.5 дБ  | Городские районы; здания в пределах нескольких кварталов                                                 |
| Short Slow         | ~5 км   | 5.5 кб/с  | -10 дБ                   | Suburban short-range; moderate building density                                                          |
| Medium Fast        | ~5 км   | 5.5 кб/с  | -10 дБ                   | Пригородные районы; умеренная плотность застройки                                                        |
| Medium Slow        | ~8 km   | 1.1 кб/с  | −12.5 дБ | Suburban/rural; moderate range with slower speed                                                         |
| Long Turbo         | ~10 км  | 4.4 kbps  | -10 дБ                   | Similar range to Long Fast but with 500 kHz bandwidth; faster throughput                                 |
| Long Fast          | ~10 км  | 1.1 кб/с  | −12.5 дБ | **Общее использование (по умолчанию)** — сбалансированный диапазон и скорость         |
| Long Moderate      | ~20 км  | 0.34 кб/с | -15 дБ                   | Сельская местность с некоторым рельефом; случайное использование                                         |
| Lite Fast          | ~5 км   | 5.5 кб/с  | -10 дБ                   | EU 866 MHz SRD band (125 kHz BW); comparable to Medium Fast                           |
| Lite Slow          | ~10 км  | 1.1 кб/с  | −12.5 дБ | EU 866 MHz SRD band (125 kHz BW); comparable to Long Fast                             |
| Narrow Fast        | ~5 км   | 2.7 kbps  | -10 дБ                   | EU 868 MHz band (62.5 kHz BW); avoids interference with other devices |
| Narrow Slow        | ~10 км  | 1.1 кб/с  | −12.5 дБ | EU 868 MHz band (62.5 kHz BW); comparable to Long Fast                |
| ~~Long Slow~~      | ~30 км  | 0.18 кб/с | −17.5 дБ | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                     |
| ~~Very Long Slow~~ | ~40+ км | 0.09 кб/с | −20 дБ                   | ⚠️ **Deprecated** — still selectable but may be removed in a future firmware release                     |

#### Выбор предустановки модема

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

### Параметры дисплея

| Настройка           | Описание                                                                             |
| ------------------- | ------------------------------------------------------------------------------------ |
| Время ожидания      | Время до перехода в спящий режим                                                     |
| Единицы измерения   | Метрическая или имперская                                                            |
| Тип OLED-дисплея    | Авто, SSD1306, SH1106, SH1107                                                        |
| Compass Orientation | Rotation offset for compass display (0°, 90°, 180°, 270°)         |
| ~~Compass North~~   | ⚠️ **Deprecated** — replaced by Compass Orientation; still visible in older firmware |

### Настройки местоположения

| Настройка               | Описание                                |
| ----------------------- | --------------------------------------- |
| GPS включен             | Включение/отключение GPS                |
| Интервал обновления GPS | Как часто получать GPS-фиксацию         |
| Вещание позиции         | How often to share position             |
| Умная позиция           | Enable movement-based broadcasting      |
| Фиксированная позиция   | Использовать вручную заданное положение |

### Настройка питания

| Настройка                               | Описание                                |
| --------------------------------------- | --------------------------------------- |
| Power Saving                            | Enable low-power sleep mode             |
| Shutdown After (s)   | Auto-shutdown idle timer                |
| Множитель ADC                           | Battery voltage calibration factor      |
| Wait Bluetooth (s)   | Time to wait for BLE connection at boot |
| Mesh SDS Timeout (s) | Super-deep-sleep timeout                |

### Настройка сети

| Настройка     | Описание                                                        |
| ------------- | --------------------------------------------------------------- |
| WiFi включен  | Включить радиомодуль WiFi (устройства ESP32) |
| WiFi SSID     | Имя сети для подключения                                        |
| WiFi PSK      | Пароль сети                                                     |
| NTP-сервер    | Сервер синхронизации времени                                    |
| Syslog-сервер | Удалённый сервер логирования                                    |

![IP address field](../../assets/screenshots/settings_ipv4_field.png)

### Настройка Bluetooth

| Настройка             | Описание                                                                  |
| --------------------- | ------------------------------------------------------------------------- |
| Bluetooth Enabled     | Enable/disable BLE radio                                                  |
| Pairing Mode          | Fixed PIN, Random PIN, or No PIN                                          |
| Фиксированный PIN-код | PIN code for pairing (default: 123456) |

### Настройки безопасности

| Настройка             | Описание                                                                   |
| --------------------- | -------------------------------------------------------------------------- |
| Публичный ключ        | Your node's public key (read-only)                      |
| Ключ администратора   | Key for remote administration                                              |
| Приватный ключ        | Your node's private key (handle securely)               |
| Admin Channel Enabled | Allow admin commands via channel                                           |
| Журнал отладки        | Output live debug logging over serial/bluetooth                            |
| Serial Enabled        | Enable serial console access (moved from Device Config) |
| Управляемый режим     | Restrict non-admin channel changes                                         |

![Password field](../../assets/screenshots/settings_password_field.png)

Settings use standard preference controls — dropdowns, toggles, and sliders:

| Control  | Screenshot                                                  |
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

