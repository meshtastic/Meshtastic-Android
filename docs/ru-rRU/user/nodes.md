---
title: Ноды
parent: Руководство пользователя
nav_order: 4
last_updated: 2026-09-04
description: Просматривайте, фильтруйте и сортируйте ноды сети — просматривайте подробности, качество сигнала, роли и быстрые действия.
aliases:
  - node-list
  - mesh-nodes
  - peers
  - hop-histogram
---

# Ноды

The Nodes screen lists every node visible on your mesh.

## Список узлов

Список нод показывает все ноды, которые услышало твоё радиоустройство, включая:

- **Имя ноды** — длинное имя, настроенное пользователем
- **Короткое имя** — 4-символьный идентификатор
- **Signal quality** — SNR, RSSI, and a quality word, shown only for nodes your radio heard directly. In the Complete layout a node reached through a relay shows its hop count here instead; a node heard only over MQTT shows neither
- **Последнее услышанное** — время с последнего общения
- **Расстояние** — предполагаемое расстояние (если позиции общие)
- **Батарея** — уровень заряда батареи удалённой ноды (если включена телеметрия)

### Choosing What the List Shows

The list has two densities, set at **Settings → Node Layout**. **Complete** shows every field a node has reported and hides the ones it hasn't. **Compact** fits more nodes on screen and lets you pick the fields yourself — **Power**, **Last Heard Time**, **Relative Last Heard Time**, **Distance and Bearing**, **Hops Away**, **Signal (Direct Only)**, **Channel**, and **Device & Role**. The **Environment Metrics** toggle applies to both densities. A preview above the toggles shows the effect before you leave the screen.

### Индикаторы состояния ноды

| Индикатор             | Значение                                       |
| --------------------- | ---------------------------------------------- |
| Green last-heard time | Нода слышна за последние 2 часа                |
| Plain last-heard time | Нода не отвечала больше 2 часов                |
| ⭐ Избранный           | Node you marked as a favorite. |

There is no separate "away" tier.

### Роли ноды

У нод можно настраивать разные роли, которые влияют на их поведение в сети:

| Роль              | Описание                                                                                                                                                        |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Client            | Standard end-user node                                                                                                                                          |
| Client Base       | Обрабатывает трафик избранных нод как приоритет Router Late; весь остальной трафик как Client                                                                   |
| Client Mute       | Принимает, но не ретранслирует                                                                                                                                  |
| Client Hidden     | Как Client Mute, плюс скрыт из списка нод                                                                                                                       |
| Router            | Ставит в приоритет пересылку сообщений; не засыпает чтобы передавать их                                                                                         |
| Router Late       | Инфраструктурная нода, которая ретранслирует один раз, но только после всех остальных режимов (обеспечивает дополнительное покрытие)         |
| ~~Router Client~~ | ⚠️ **Устарело** (удалено в прошивке 2.3.15) — больше не выбирается; используй вместо этого Router или Client |
| ~~Repeater~~      | ⚠️ **Устарело** (удалено в прошивке 2.7.11) — больше не выбирается; используй вместо этого Router            |
| Tracker           | Оптимизировано для передачи данных о местоположении через регулярные промежутки времени                                                                         |
| Sensor            | Оптимизировано для данных телеметрии                                                                                                                            |
| Тактический       | Взаимодействует с системами TAK (отправляет/принимает CoT)                                                                                   |
| TAK Tracker       | Только отчет о позиции TAK                                                                                                                                      |
| Lost and Found    | Sends its position to the default channel as a text message at regular intervals, to help recover a lost radio                                                  |

### Выбор роли

Большинству пользователей стоит оставить роль **Client** по умолчанию. Рассмотри другую роль, когда:

- **Router** — У тебя есть узел в фиксированном, высоком месте с надежным источником питания (крыша, вершина холма). Router постоянно бодрствуют, чтобы пересылать сообщения для других, и они необходимы для расширения покрытия сети. Don't use Router on battery-powered handheld radios.
- **Router Late** — инфраструктурная нода, которая всегда пересылает пакеты только один раз, но только после того, как все другие режимы маршрутизации выполнили свои ходы. Обеспечивает дополнительное покрытие для локальных кластеров, не конкурируя с основными роутерами.
- **Client Base** — обрабатывает трафик от/к вашим избранным нодам с приоритетом Router Late (обеспечивая этим сообщениям дополнительное ретранслирование), а всё остальное обрабатывает как обычный Client.
- **Client Mute** — хочешь принимать трафик сети, но не участвовать в его ретрансляции. Useful for monitoring-only radios or to reduce congestion in dense areas.
- **Tracker** — An unattended radio whose sole purpose is broadcasting its GPS position (e.g., a vehicle, pet, or asset). Спит между передачами для экономии батареи.
- **Sensor** — An unattended radio reporting environmental telemetry (temperature, humidity, air quality). Похожий профиль мощности на Tracker.
- **TAK / TAK Tracker** — нужно только если работать с системами ATAK/WinTAK. Смотри [Интеграция TAK](tak) для подробностей.

> 💡 **Совет:** Сеть работает лучше, когда большинство нод **Client** или **Router**. Too many Client Mute nodes reduce mesh resilience; too many Routers in a dense area can cause congestion. Хорошее практическое правило: один роутер на 5–10 клиентов в вашей зоне.

### Индикаторы шифрования

У нод рядом с именем отображаются значки статуса шифрования:

| Значок            | Значение                                                                                                                   |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------- |
| 🔒 Заблокировано  | Связь использует PKI (инфраструктуру публичных ключей) — сквозное шифрование с проверкой идентичности   |
| 🔓 Разблокировано | Связь использует общий канал PSK — зашифровано, но личность не проверяется индивидуально                                   |
| ⚠️ Несовпадение   | Несовпадение открытого ключа — ключ ноды изменился с последнего раза (разберитесь, прежде чем доверять) |

> 💡 **Совет:** Шифрование PKI (прошивка 2.5+) обеспечивает более надёжную защиту, чем общий PSK для канала, потому что у каждой ноды есть уникальная пара ключей. Если видишь предупреждение о несоответствии ключа, нода могла быть сброшена или скомпрометирована.

To clear a mismatch, first confirm through another trusted channel that the key change was intentional — a factory reset causes one. Then touch & hold the node, choose **Remove**, and let the two radios exchange keys again the next time yours hears it.

## Быстрые действия

Из списка нод ты можешь:

- **Нажмите** на ноду, чтобы увидеть её страницу с деталями
- **Touch & hold** for quick actions:
  - Отметить/убрать из избранного
  - Отключить/включить уведомления
  - Отправить личное сообщение
  - Трассировка
  - Игнорировать/разблокировать
  - Удалить

Touch & hold **your own node** instead and you get one action, **Update status**, which opens the
User settings screen with the cursor already in the Status Message field. It only appears while the
radio is connected and running firmware 2.8 or newer — see
[Settings — Radio & User](settings-radio-user.md) for the field itself.

## Sharing a Contact

On a node's detail screen, tap **Share Contact** to produce a link and a QR code for that node. From the same dialog, **Write to NFC tag** saves the link to a writable NFC tag that anyone can tap to open.

To add someone else's contact, use the import button on the node list and choose **Scan Shared Contact QR Code**, **Scan Shared Contact NFC**, or **Input Shared Contact URL**. The app asks you to confirm with **Import Shared Contact?**, and warns you when the contact is one you already have.

## Фильтрация и сортировка

### Поиск текста

Введи в поле поиска, чтобы отфильтровать ноды по имени или короткому имени. Фильтр обновляется в реальном времени по мере набора текста.

### Переключатели фильтра

| Фильтр                       | Описание                                                                                                                                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Hide offline nodes**       | Показывать только ноды, услышанные за последние 2 часа                                                                                                                                            |
| **Only show direct nodes**   | Show only nodes your radio heard directly, with no relay in between                                                                                                                               |
| **Включить неизвестные**     | Show nodes that haven't sent user info yet. **On by default**, so a node heard before its info arrives stays visible and messageable; these carry a badge marking them incomplete |
| **Исключить инфраструктуру** | Hide infrastructure-role nodes (Router, Router Late, Client Base, and legacy Repeater nodes) and any node that cannot be messaged, whatever its role                           |
| **Исключить MQTT**           | Скрыть ноды, слышимые только через интернет-мост MQTT                                                                                                                                             |
| **Only show ignored Nodes**  | Replace the list with the nodes you have ignored. Every other node is hidden while this is on, and a banner appears at the top of the list to take you back                       |

### Параметры сортировки

| Сортировка                                    | Описание                                                              |
| --------------------------------------------- | --------------------------------------------------------------------- |
| **Last heard**                                | Сначала недавно слышимые ноды                                         |
| **A-Z**                                       | Сортировать по полному имени ноды                                     |
| **Расстояние**                                | Сначала ближайшие ноды (требуется обмен позициями) |
| **Меньше хопов**                              | Сначала с наименьшим количеством ретрансляций                         |
| **Канал**                                     | Группировать по индексу канала                                        |
| **via MQTT**                                  | Сгруппировать по MQTT и радиоприему                                   |
| **via Favorite** (default) | Favorited nodes first, then the rest                                  |

## Нод на хоп

Нажми на значок гистограммы хопов в панели приложений списка нод, чтобы открыть столбчатую диаграмму того, сколько нод находится на каждом расстоянии хопа (0 = напрямую, 1 = через один ретранслятор и так далее). Отфильтруй график по окну **последнего услышанного** — за всё время, 1 час, 8 часов или 24 часа — чтобы посмотреть, как сейчас выглядит сеть по сравнению с более длительным периодом. Это быстрый способ понять, насколько занята и разветвлена твоя местная сеть.

## Детали ноды

Нажатие на ноду открывает подробный вид с полной информацией. Смотри [Метрики ноды](node-metrics) для полной информации о метриках и телеметрии.

The Details card carries the node's short name, role, IDs, last heard time, hops away, uptime, and its SNR and RSSI:

![Раздел деталей ноды](../../assets/screenshots/nodes_detail_section.png)

Встроенные индикаторы статуса показывают ключевые показатели с первого взгляда:

| Индикатор              | Снимок экрана                                                   |
| ---------------------- | --------------------------------------------------------------- |
| Качество сигнала       | ![Сигнал](../../assets/screenshots/nodes_signal_info.png)       |
| Уровень заряда батареи | ![Батарея](../../assets/screenshots/nodes_battery_info.png)     |
| Количество хопов       | ![Хопы](../../assets/screenshots/nodes_hops_info.png)           |
| Последний раз слышен   | ![Last heard](../../assets/screenshots/nodes_last_heard.png)    |
| Расстояние             | ![Расстояние](../../assets/screenshots/nodes_distance_info.png) |

### Ссылки на устройства ("Хочу такое")

Когда оборудование ноды распознано, в детальном просмотре появляется сворачиваемый раздел **"Хочу такой"**, содержащий ссылки на места, где можно купить или узнать больше об этом устройстве: страницу продукта у производителя, варианты продукта и объявления на региональных торговых площадках (например, AliExpress, Amazon и у поддерживаемых продавцов), отфильтрованные по твоей стране. Каждая ссылка открывается через сервис перенаправления `msh.to`. Устройства без подходящих ссылок не показывают этот раздел.

A full, browsable directory of every link is also available at **Settings → Device Links**. The item is hidden while you have Settings open for a remote node.

## When No Nodes Appear

The list stays empty until your radio hears another node.

- **No device connected** — the app is not connected to a radio. See [Connections](connections).
- **Searching for nodes** — the radio is connected and listening, but nothing has arrived yet. Check that its region and modem preset match the mesh around you, and leave **Include unknown** on so a node that has not yet sent its name still appears. See [Settings — Radio & User](settings-radio-user).
- A node you expect is missing — check the filter toggles. **Only show direct nodes**, **Exclude MQTT**, and **Exclude infrastructure** each hide a whole category of node.

## Связанные темы

- [Метрики ноды](node-metrics) — подробные телеметрические панели для каждой ноды
- [Сообщения и каналы](messages-and-channels) — отправь личное сообщение ноде
- [Карта и путевые точки](map-and-waypoints) — просмот позиции нод на карте
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for topology exploration
- [Индикатор сигнала](signal-meter) — пойми, что означают полоски сигнала
