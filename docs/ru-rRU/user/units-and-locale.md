---
title: Единицы измерения и локаль
parent: Руководство пользователя
nav_order: 16
last_updated: 2026-08-29
description: Как приложение отображает температуру, расстояние, скорость и другие показатели в зависимости от настроек устройства.
aliases:
  - measurement
  - units
  - locale
  - metric
  - imperial
---

# Единицы измерения и локаль

The Meshtastic app automatically displays temperatures, distances, speeds, and times in the units your device is configured to use. If your device's settings can't express the units you want, an in-app **Units** setting overrides them.

## Как это работает

Радиостанции Meshtastic всегда передают данные в **метрических единицах** (метры, °C, м/с, гПа и т.д.). Когда приложение получает эти данные, оно преобразует их и показывает значения в той системе единиц, которую задает локаль устройства.

На Android твои предпочтения единиц измерений определяются настройками системы **Язык и регион**. На настольном компьютере (JVM) приложение использует стандартную `Locale` JVM.

Units follow your device's **region**, not the display language. Plain languages — like **English** in the app's own Language setting or Android's per-app language — keep the region your device is set to. A choice that names a region of its own, like **English (Canada)**, overrides it and brings that region's units with it. On Android 16+, the system-wide **Measurement system** preference overrides the region entirely.

> 💡 **Tip:** By default there is nothing to configure — change your system measurement preferences and every screen in Meshtastic updates automatically. If your device offers no working region or measurement setting (some manufacturer builds don't), set **Settings → Units** in the app instead.

## The Radio's Own Screen Is Separate

**Device → Display → Units** configures the screen on the radio, not the app. So do **Use 12-Hour Clock** and **Always Point North** — all three apply to the radio's display only. Temperature on that screen has its own setting, [**Telemetry → Display Fahrenheit**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

If your node list shows miles while the radio's screen shows kilometres, this is why: the two are set in different places. Changing the radio's setting never alters what the app displays. See the [Display Config](https://meshtastic.org/docs/configuration/radio/display) guide on meshtastic.org for the device-side options.

## Температура

Значения температуры от датчиков окружающей среды передаются в **°C** и отображаются в соответствии с предпочтительным единицами температуры твоего устройства.

![Метрики окружающей среды с температурой](../../assets/screenshots/nodes_environment_metrics.png)

| Твои настройки | Ты видишь |
| -------------- | --------- |
| Цельсий        | 22°C      |
| Фаренгейт      | 72°F      |

Это влияет на все отображения температуры в приложении: телеметрия окружающей среды ноды, температура почвы, точка росы и оси диаграммы телеметрии.

Температура следует вашим **настройкам предпочитаемой шкалы температуры**, независимо от системы измерения расстояния. Локали, где смешаны системы, работают корректно — телефон из Великобритании показывает мили для расстояния, но **°C** для температуры. На Android 14+ региональные настройки **Температуры** (Настройки → Система → Языки → Региональные предпочтения) заменяют значение по умолчанию для локали.

## Расстояние и высота

Расстояния между нодами и высоты GPS передаются в **метрах** и автоматически масштабируются и преобразуются.

![Отображение информации о расстоянии](../../assets/screenshots/nodes_distance_info.png)

| Твои настройки | Небольшое расстояние | Дальнее расстояние | Высота  |
| -------------- | -------------------- | ------------------ | ------- |
| Метрическая    | 350 м                | 2,5 км             | 1200 м  |
| Имперская      | 1148 фт              | 1,6 миль           | 3937 фт |

Приложение использует естественное масштабирование — небольшие расстояния остаются в метрах или футах, а более дальние автоматически переключаются на километры или мили.

### Где они появляются

- **Список нод** — расстояние и азимут до каждой ноды
- **Детали ноды** — высота, расстояние от твоего положения
- **Карта** — расстояния между путевыми точками, расстояния между хопами трассировки
- **Компас** — расстояние до выбранной ноды

## Скорость

Наземная скорость GPS отображается в единицах скорости, предпочитаемых в твоем регионе.

| Твоя настройка                     | Ты видишь |
| ---------------------------------- | --------- |
| Метрическая                        | 12 км/ч   |
| Имперская (США) | 7 миль/ч  |

## Ветер

Wind speed, gust and lull are transmitted by the sensor as **m/s** and converted for display — the app shows the unit weather forecasts use in your region, not the raw sensor unit.

| Твоя настройка                     | Ты видишь                 |
| ---------------------------------- | ------------------------- |
| Метрическая                        | 18.0 km/h |
| Имперская (США) | 11.2 mph  |

All three read in the same unit wherever they appear: the Node Detail environment section, the Environment Telemetry log, and the charts.

## Вес

Readings from a connected scale are transmitted in **kg** and converted for display.

| Твоя настройка                     | Ты видишь               |
| ---------------------------------- | ----------------------- |
| Метрическая                        | 1.50 kg |
| Имперская (США) | 3.31 lb |

## Осадки

Измерения осадков (за 1 час и за 24 часа) передаются в **мм** и конвертируются для отображения.

| Ваши настройки                     | Вы видите               |
| ---------------------------------- | ----------------------- |
| Метрическая                        | 12.0 mm |
| Имперская (США) | 0.47 in |

## Единицы, которые никогда не меняются

Некоторые единицы являются международными стандартами и отображаются одинаково независимо от вашего региона:

| Показатель                          | Единица                        | Почему                                   |
| ----------------------------------- | ------------------------------ | ---------------------------------------- |
| Барометрическое давление            | гПа                            | Международный метеорологический стандарт |
| Курс / азимут                       | ° (градусы) | Универсальная навигационная конвенция    |
| Радиация                            | µR/h                           | Стандартная единица дозиметрии           |
| GPS координаты                      | десятичные градусы             | Универсальный географический стандарт    |
| Влажность, батарея, влажность почвы | %                              | Универсальный                            |

## Дата и время

Все отметки времени в приложении — последняя активность, время сообщений, журналы телеметрии, оси графиков — следуют настройкам даты и времени вашего устройства.

| Настройка                     | Что это контролирует | Пример                                            |
| ----------------------------- | -------------------- | ------------------------------------------------- |
| **24-часовой формат времени** | Формат часов         | 14:30 или 2:30 PM |
| **Формат даты**               | Сортировка даты      | 09/05/2026 или 05/09/2026                         |

Приложение также использует **относительное время** в списке нод, где это имеет смысл — например, "5 минут назад" или "2 часа назад", которое автоматически локализуется на язык твоего устройства.

## Changing Your Measurement System

By default the app follows your device, and your measurement system (metric vs imperial) is tied to your region setting:

1. Откройте **Настройки Android → Система → Язык и регион**
2. Change your **Region**
3. Вернуться к Meshtastic — значения обновляются немедленно

On Android 16+, the system-wide **Measurement system** preference overrides the region for every measurement. On Android 14+, temperature can be overridden on its own under **Regional preferences → Temperature**.

Not every English region is fully metric. **English (United Kingdom)** uses miles and feet for distance, so the node list shows miles and altitude in feet. For metric distances, set the app's **Units** setting to Metric (see [Overriding the Units in the App](#overriding-the-units-in-the-app)), or choose a fully metric region such as English (Canada), English (Ireland), or English (New Zealand).

Some phones do not offer the **Regional preferences** menu at all and list only English (United States). On those devices, use the app's **Units** setting (see [Overriding the Units in the App](#overriding-the-units-in-the-app)).

### Overriding the units in the app

Not every device can express every preference — some manufacturer builds ship no regional preferences at all, some
offer only one English variant, and UK regions are imperial for distance even if you'd rather read altitude in
metres. For those cases the app has its own switch:

1. Open **Meshtastic Settings → Units**
2. Choose **System default**, **Metric**, or **Imperial**
3. Every screen updates immediately — no restart needed

**System default** follows your phone's or computer's region and measurement settings. Forcing **Metric** or **Imperial** applies to
everything, temperature included (metric → °C, imperial → °F), even where the system's own regional preferences say
otherwise. The setting exists on Android and Desktop alike.

## Связанные темы

- [Метрики ноды](node-metrics) — здесь отображаются температура, расстояние и значения датчиков
- [Телеметрия и датчики](telemetry-and-sensors) — датчики, производящие эти измерения
- [Измерение и форматирование](../developer/measurement) — ссылка на разработчика утилит форматирования
- [Настройки — Радио и Пользователь](settings-radio-user) — настройка региона, управляющая выбором единиц
- [Display Config](https://meshtastic.org/docs/configuration/radio/display) — units, clock, and compass settings for the radio's own screen, on meshtastic.org
