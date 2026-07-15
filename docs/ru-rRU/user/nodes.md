---
title: Ноды
parent: Руководство пользователя
nav_order: 4
last_updated: 2026-07-08
description: Просматривайте, фильтруйте и сортируйте ноды сети — просматривайте подробности, качество сигнала, роли и быстрые действия.
aliases:
  - node-list
  - mesh-nodes
  - peers
  - hop-histogram
---

# Ноды

Экран нод показывает все устройства, видимые в твоей mesh-сети.

## Список узлов

Список нод показывает все ноды, которые услышало твоё радиоустройство, включая:

- **Имя ноды** — длинное имя, настроенное пользователем
- **Короткое имя** — 4-символьный идентификатор
- **Качество сигнала** — последний уровень принимаемого сигнала
- **Последнее услышанное** — время с последнего общения
- **Расстояние** — предполагаемое расстояние (если позиции общие)
- **Батарея** — уровень заряда батареи удалённой ноды (если включена телеметрия)

### Индикаторы состояния ноды

| Значок      | Значение                                  |
| ----------- | ----------------------------------------- |
| 🟢 Онлайн   | Нода слышна за последние 2 часа           |
| ⚪ Оффлайн   | Нода не отвечала больше 2 часов           |
| ⭐ Избранный | Нода отмечена пользователем как избранная |

Нода считается **онлайн**, если с ней связывались в последние 2 часа, и **офлайн** в противном случае — отдельного статуса «отошёл» нет.

### Роли ноды

У нод можно настраивать разные роли, которые влияют на их поведение в сети:

| Роль                             | Описание                                                                                                                                                        |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Client                           | Стандартное пользовательское устройство                                                                                                                         |
| Client Base                      | Обрабатывает трафик избранных нод как приоритет Router Late; весь остальной трафик как Client                                                                   |
| Client Mute                      | Принимает, но не ретранслирует                                                                                                                                  |
| Client Hidden                    | Как Client Mute, плюс скрыт из списка нод                                                                                                                       |
| Router                           | Ставит в приоритет пересылку сообщений; не засыпает чтобы передавать их                                                                                         |
| Router Late                      | Инфраструктурная нода, которая ретранслирует один раз, но только после всех остальных режимов (обеспечивает дополнительное покрытие)         |
| ~~Router Client~~                | ⚠️ **Устарело** (удалено в прошивке 2.3.15) — больше не выбирается; используй вместо этого Router или Client |
| ~~Repeater~~                     | ⚠️ **Устарело** (удалено в прошивке 2.7.11) — больше не выбирается; используй вместо этого Router            |
| Tracker                          | Оптимизировано для передачи данных о местоположении через регулярные промежутки времени                                                                         |
| Sensor                           | Оптимизировано для данных телеметрии                                                                                                                            |
| Тактический                      | Взаимодействует с системами TAK (отправляет/принимает CoT)                                                                                   |
| TAK Tracker                      | Только отчет о позиции TAK                                                                                                                                      |
| Lost & Found | Непрерывный маяк для поиска                                                                                                                                     |

### Выбор роли

Большинству пользователей стоит оставить роль **Client** по умолчанию. Рассмотри другую роль, когда:

- **Router** — У тебя есть узел в фиксированном, высоком месте с надежным источником питания (крыша, вершина холма). Router постоянно бодрствуют, чтобы пересылать сообщения для других, и они необходимы для расширения покрытия сети. Не используй Router на портативных устройствах с батареей.
- **Router Late** — инфраструктурная нода, которая всегда пересылает пакеты только один раз, но только после того, как все другие режимы маршрутизации выполнили свои ходы. Обеспечивает дополнительное покрытие для локальных кластеров, не конкурируя с основными роутерами.
- **Client Base** — обрабатывает трафик от/к вашим избранным нодам с приоритетом Router Late (обеспечивая этим сообщениям дополнительное ретранслирование), а всё остальное обрабатывает как обычный Client.
- **Client Mute** — хочешь принимать трафик сети, но не участвовать в его ретрансляции. Полезно для устройств только для мониторинга или чтобы уменьшить нагрузку в густонаселённых местах.
- **Tracker** — это устройство, которое работает само по себе и его единственная цель — передавать местоположение по GPS (например, автомобиль, питомец или имущество). Спит между передачами для экономии батареи.
- **Sensor** — это автономное устройство, которое отслеживает показатели окружающей среды (температуру, влажность, качество воздуха). Похожий профиль мощности на Tracker.
- **TAK / TAK Tracker** — нужно только если работать с системами ATAK/WinTAK. Смотри [Интеграция TAK](tak) для подробностей.

> 💡 **Совет:** Сеть работает лучше, когда большинство нод **Client** или **Router**. Слишком много нод Mute снижает устойчивость сети; слишком много роутеров в плотной зоне может вызвать перегрузку. A good rule of thumb: one Router per 5–10 Clients in your area.

### Индикаторы шифрования

У нод рядом с именем отображаются значки статуса шифрования:

| Значок            | Значение                                                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------------------------------- |
| 🔒 Заблокировано  | Communication uses PKI (public key infrastructure) — end-to-end encrypted with verified identity |
| 🔓 Разблокировано | Communication uses shared channel PSK — encrypted but identity not individually verified                            |
| ⚠️ Несовпадение   | Public key mismatch — the node's key has changed since last seen (investigate before trusting)   |

> 💡 **Tip:** PKI encryption (firmware 2.5+) provides stronger security than channel PSK because each node has a unique key pair. If you see a key mismatch warning, the node may have been reset or compromised.

## Быстрые действия

From the node list, you can:

- **Tap** a node to view its detail page
- **Long-press** for quick actions:
  - Mark/remove favorite
  - Mute/unmute notifications
  - Send a direct message
  - Трассировка
  - Ignore/unignore
  - Удалить ноду

## Фильтрация и сортировка

### Text Search

Type in the search field to filter nodes by name or short name. The filter updates in real time as you type.

### Переключатели фильтра

| Фильтр                       | Описание                                                                                           |
| ---------------------------- | -------------------------------------------------------------------------------------------------- |
| **Только онлайн**            | Show only nodes heard within the last 2 hours                                                      |
| **Только прямые**            | Show only nodes with direct (non-relayed) connections                           |
| **Включить неизвестные**     | Показать ноды, которые еще не отправили информацию о пользователе                                  |
| **Исключить инфраструктуру** | Скрыть ноды с ролью инфраструктуры (Router, Repeater, Router Late, Client Base) |
| **Исключить MQTT**           | Скрыть ноды, слышимые только через интернет-мост MQTT                                              |
| **Показать игнорируемые**    | Показать ноды, которые ты раньше скрывал или заглушал                                              |

### Параметры сортировки

| Сортировка                                  | Описание                                                              |
| ------------------------------------------- | --------------------------------------------------------------------- |
| **Last heard** (default) | Сначала недавно слышимые узлы                                         |
| **По алфавиту**                             | Сортировать по полному имени ноды                                     |
| **Расстояние**                              | Сначала ближайшие ноды (требуется обмен позициями) |
| **Hops away**                               | Сначала с наименьшим количеством ретрансляций                         |
| **Канал**                                   | Группировать по индексу канала                                        |
| **Через MQTT**                              | Grouped by MQTT vs. radio-heard                       |
| **Избранное**                               | Сначала избранные ноды                                                |

## Нод на хоп

Tap the hop-histogram icon in the node list's app bar to open a bar chart of how many nodes sit at each hop distance (0 = direct, 1 = one relay away, and so on). Filter the chart to a **last heard** window — All time, 1 hour, 8 hours, or 24 hours — to see how the mesh looks right now versus over a longer period. It's a quick way to gauge how busy and spread out your local mesh is.

## Детали ноды

Tapping a node opens the detail view with comprehensive information. See [Node Metrics](node-metrics) for full details on metrics and telemetry.

![Node detail view](../../assets/screenshots/nodes_node_list.png)

The detail screen includes device info, position, and action buttons:

![Node detail section](../../assets/screenshots/nodes_detail_section.png)

Inline status indicators show key metrics at a glance:

| Индикатор              | Снимок экрана                                                   |
| ---------------------- | --------------------------------------------------------------- |
| Качество сигнала       | ![Сигнал](../../assets/screenshots/nodes_signal_info.png)       |
| Уровень заряда батареи | ![Батарея](../../assets/screenshots/nodes_battery_info.png)     |
| Количество хопов       | ![Хопы](../../assets/screenshots/nodes_hops_info.png)           |
| Последний раз слышен   | ![Last heard](../../assets/screenshots/nodes_last_heard.png)    |
| Расстояние             | ![Расстояние](../../assets/screenshots/nodes_distance_info.png) |

### Ссылки на устройства ("Хочу такое")

When a node's hardware is recognized, the detail view shows a collapsible **"I want one"** section linking to places to buy or learn more about that device: the vendor's product page, product variants, and regional marketplace listings (such as AliExpress, Amazon, and supported retailers), filtered to your country. Each link opens through the `msh.to` redirect service. Устройства без подходящих ссылок не показывают этот раздел.

Полный каталог всех ссылок, который можно просматривать, также доступен в **Настройки → Ссылки устройства**.

## Связанные темы

- [Node Metrics](node-metrics) — detailed telemetry dashboards for each node
- [Messages & Channels](messages-and-channels) — send a direct message to a node
- [Map & Waypoints](map-and-waypoints) — view node positions geographically
- [Discovery](discovery) — traceroute and neighbor info for topology exploration
- [Индикатор сигнала](signal-meter) — пойми, что означают полоски сигнала

---

