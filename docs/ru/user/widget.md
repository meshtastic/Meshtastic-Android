---
title: Виджет на главный экран
nav_order: 20
last_updated: 2026-11-15
description: Добавь виджеты главного экрана Meshtastic, чтобы просматривать локальную статистику подключенной ноды, следить за выбранной тобой нодой или считывать показания датчиков одной ноды, не открывая приложение.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
  - node-tracker-widget
  - telemetry-widget
---

# Виджет на главный экран

На Android, Meshtastic предоставляет три **виджета** домашнего экрана:

- **Meshtastic** показывает в реальном времени локальную статистику с подключенной ноды.
- **Трекер нод** отслеживает выбранную тобой нооду: уровень заряда, сигнал, время последнего приема, количество хопов и расстояние до подключенной ноды.
- **Телеметрия** отображает окружающую среду одной ноды, качество воздуха и показатели мощности как плитки.

Ты можешь разместить столько каждого, сколько захочешь. Каждая нода трекера и виджет Telemetry запоминают свою собственную ноду.

## Локальная статистика

Виджет **Meshtastic** отображает текущую локальную статистику **подключенной ноды**:

- **Чип ноды**, содержащий краткое название ноды, выделенное его собственными цветами, с указанием уровня заряда **батареи** рядом с ним или _включено_ при работе от внешнего источника питания
- **ChUtil** — использование канала (насколько занят канал LoRa, в процентах)
- **AirUtil** — процент времени, в течение которого нода использовала передачу за последний час
- **Ноды** — сколько нод из общего количества известных в сети
- **Uptime** — время работы ноды с момента её последней перезагрузки
- **Отправлено** и **Получено** — счетчики пакетов ноды
- **Уровень шума** — уровень фонового шума в дБм
- **Свободная память** — остаточная память ноды
- **Передано** — пакеты, передаваемые нодой другим
- **Плохо** — полученные пакеты повреждены
- **Отброшено** — пакеты, отброшенные нодой
- **Ретрансляции отменены** — запущенные ретрансляции, но после отмененные
- **Updated** — the time the stats last refreshed, along the foot of a three-row widget

The widget shows as many of those as its size holds, in that order, so a small widget keeps the readings people glance at most. A two-by-two widget shows the node, battery, utilization and the first few readings; four cells wide lays the readings out in two columns, and a taller widget adds lines until every reading is on screen.

Нажми на виджет, чтобы открыть приложение, или используй его кнопку обновления для запроса свежей статистики.

> ℹ️ **Примечание:** Значения соответствуют подключенной ноде. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Трекер нод

The **Node Tracker** widget shows one node from your node list:

- **Микросхема** ноды и **Длинное имя**
- **Батарея**: последний уровень батареи ноды или _включено_ при внешнем питании
- **Last heard**: the time your connected node last heard it, or the date if it was more than a day ago

From two rows tall it adds, in this order, as many as fit (a wide widget under two rows tall shows Hops Away beside the name):

- **Hops Away**: how many hops away the node is, or _Direct_
- **Distance**: how far the node is from your connected node. It appears only while you are connected and both nodes have a position
- **SNR** and **RSSI**: the signal of the node's last packet, shown only when the node is heard directly rather than through other nodes or MQTT

Four cells wide lays the readings out in two columns, so at the default font size every one fits in a two-row widget. A larger font size drops readings from the end of that list, RSSI first.

When a node has not been heard within the last two hours, the widget dims its readings so they do not read as current. Tap the widget to open that node's details in the app.

After you disconnect from your node in the app, the widget keeps the tracked node's last readings on screen, dimmed, until you reconnect.

If your node list does not include the node you picked, because you removed it or connected to a different node, the widget says **Node not found**. Tap it to choose another node.

## Телеметрия

The **Telemetry** widget shows one node's sensor readings as tiles, the same tiles the node's details show, with the same units and precision:

- **Environment**: temperature, humidity, pressure, dew point, gas resistance, IAQ, light, wind, rain, soil temperature, soil moisture, radiation and weight
- **Air quality**: PM1.0, PM2.5, PM10 and CO₂
- **Power**: voltage and current for each channel, named with the labels you gave the channels

It shows every reading the node reports, in that order, and you can hide any of them. The node's chip sits beside the readings, and the line under them says when the newest reading arrived.

- A widget narrower than two columns shows the first reading.
- A widget under two rows tall shows the first readings in a row: two up to three columns wide, four from four columns.
- A widget at least two columns by two rows shows four at a time in a grid. With more than four readings, tap the page button in its corner, such as **1/2**, to see the next four.

A reading the node has stopped sending shows a dash, while a reading of zero shows 0. Once the newest reading is more than two hours old, the line under the tiles says **Over 2 hours old**. The age comes from the app's packet log, so with logging turned off the widget can't tell when a reading arrived: it shows no age, and says **Over 2 hours old** only once the node itself has not been heard for two hours.

The widget shows readings the app has already saved, so it works with no radio connected, and it never sends anything over the mesh. Tap it to open the node's metrics in the app. If its node is no longer in your node list, it says **No node chosen. Tap to choose one.**, and tapping it opens the widget's setup screen, where **Change node** opens the node picker.

## Добавление виджета

1. Touch & hold an empty area of your Android home screen.
2. Tap **Widgets**, then find **Meshtastic** in the list.
3. Drag the **Meshtastic** (local stats), **Node Tracker** or **Telemetry** widget to your home screen.
4. Чтобы отслеживать ноды, выберите ноду, за которой нужно следить. Сначала перечисляются твои избранные, затем самые последние прослушиваемые ноды; введи в поле поиска, чтобы выполнить фильтрацию по названию или идентификатору ноды. Отмена возврата без выбора приводит к отмене виджета.
5. For a Telemetry widget, the setup screen starts on your connected node when it reports any of these readings. Tap **Change node** to pick another; only nodes that have sent environment, air quality or power readings are listed, and the search field filters them by name or node ID. Turn off any reading you don't want, then tap **Save**.
6. Изменение размера по мере необходимости — макет адаптируется к доступному пространству.

To track a different node or change the readings later, touch & hold the widget and choose the widget's settings or reconfigure option. Точная формулировка зависит от твоего лаунчера.

> ℹ️ **Примечание:** виджеты доступны только на Android. Они не доступны в десктопном приложении

## Связанные темы

- [Метрики нод](node-metrics) — полная статистика сигнала и локальная статистика внутри приложения
- [Ноды](nodes) — избранное, последнее обновление и сведения о нодах
- [Подключения](connections) — подключение к ноде, чтобы виджет отображал данные
- [Локальное обнаружение сети] (discovery) — использование канала и времени использования сети
