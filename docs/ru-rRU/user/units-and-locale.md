---
title: Единицы измерения и локаль
parent: Руководство пользователя
nav_order: 16
last_updated: 2026-05-12
---

# Единицы измерения и локаль

Приложение Meshtastic автоматически отображает температуру, расстояние, скорость и время в единицах, настроенных на вашем устройстве — никаких настроек внутри приложения менять не нужно.

---

## Как это работает

Радиостанции Meshtastic всегда передают данные в **метрических единицах** (метры, °C, км/ч, гПа и т.д.). Когда приложение получает эти данные, оно использует утилиту `MetricFormatter` для преобразования и отображения значений в той системе единиц, которую задают региональные настройки устройства.

На Android твои предпочтения единиц измерений определяются настройками системы **Язык и регион**. На настольном компьютере (JVM) приложение использует стандартную `Locale` JVM.

> **Совет — вам никогда не нужно переключать единицы измерения внутри приложения.** Измените системные настройки единиц измерений, и все отображения в Meshtastic обновятся автоматически — детали ноды, графики телеметрии, погода, высота и многое другое.

---

## Температура

Значения температуры от датчиков окружающей среды передаются в **°C** и отображаются в соответствии с предпочтительным единицами температуры твоего устройства.

![Метрики окружающей среды с температурой](../../assets/screenshots/nodes_environment_metrics.png)

| Твои настройки | Ты видишь |
| -------------- | --------- |
| Цельсий        | 22°C      |
| Фаренгейт      | 72°F      |

Это влияет на все отображения температуры в приложении: телеметрия окружающей среды ноды, температура почвы, точка росы и оси диаграммы телеметрии.

## Расстояние и высота

Расстояния между нодами и высоты GPS передаются в **метрах** и автоматически масштабируются и преобразуются.

![Отображение информации о расстоянии](../../assets/screenshots/nodes_distance_info.png)

| Твои настройки | Небольшое расстояние | Дальнее расстояние | Высота  |
| -------------- | -------------------- | ------------------ | ------- |
| Метрическая    | 350 м                | 2,5 км             | 1200 м  |
| Имперская      | 1148 фт              | 1,6 миль           | 3937 фт |

Приложение использует естественное масштабирование — небольшие расстояния остаются в метрах или футах, а более дальние автоматически переключаются на километры или мили.

### Где они появляются

- **Node list** — distance and bearing to each node
- **Node detail** — altitude, distance from your position
- **Map** — waypoint distances, traceroute hop distances
- **Compass** — distance to selected node

## Скорость

GPS ground speed is displayed in your locale's preferred speed unit.

| Your Setting                     | You See |
| -------------------------------- | ------- |
| Metric                           | 12 km/h |
| Imperial (US) | 7 mph   |

## Ветер

Wind speed and gust data from environment sensors are transmitted as **m/s** and converted for display.

| Your Setting                     | You See |
| -------------------------------- | ------- |
| Metric                           | 5 m/s   |
| Imperial (US) | 11 mph  |

Wind readings appear in the **Node Detail** environment section and the **Environment Telemetry** charts.

## Rainfall

Rainfall measurements (1-hour and 24-hour totals) are transmitted as **mm** and converted for display.

| Your Setting                     | You See                |
| -------------------------------- | ---------------------- |
| Metric                           | 12 mm                  |
| Imperial (US) | 0.5 in |

## Units That Never Change

Some units are international standards and are displayed the same way regardless of your locale:

| Measurement                      | Unit                           | Why                                   |
| -------------------------------- | ------------------------------ | ------------------------------------- |
| Barometric pressure              | hPa                            | International meteorological standard |
| Heading / bearing                | ° (degrees) | Universal navigation convention       |
| Радиация                         | μR/hr                          | Standard dosimetry unit               |
| GPS coordinates                  | decimal degrees                | Universal geographic standard         |
| Humidity, battery, soil moisture | %                              | Universal                             |

## Date & Time

All timestamps throughout the app — last heard, message times, telemetry logs, chart axes — follow your device's date and time preferences.

| Настройка        | What It Controls | Example                                          |
| ---------------- | ---------------- | ------------------------------------------------ |
| **24-Hour Time** | Clock format     | 14:30 vs 2:30 PM |
| **Date Format**  | Date ordering    | 09/05/2026 vs 05/09/2026                         |

The app also uses **relative time** where it makes sense — for example, "5 min ago" or "2 hours ago" in the node list — which is automatically localised into your device language.

## Changing Your Measurement System (Android)

On Android, your measurement system (metric vs imperial) is tied to your region setting:

1. Open **Android Settings → System → Language & Region**
2. Change your **Region** or **Measurement units** preference
3. Return to Meshtastic — values update immediately

> **Tip — The app uses `MetricFormatter` from `core:common`.** All measurement formatting is handled by a shared KMP utility that respects your platform's locale. Developers adding new measurement displays should use `MetricFormatter` rather than hard-coding unit conversions.

