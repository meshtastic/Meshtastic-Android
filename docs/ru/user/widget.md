---
title: Виджет на главный экран
nav_order: 20
last_updated: 2026-11-04
description: Добавь виджеты Meshtastic на главный экран, чтобы просматривать локальную статистику подключенных нод или следить за выбранной нодой не открывая приложение.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
  - node-tracker-widget
---

# Виджет на главный экран

На Android Meshtastic предоставляет два **виджета** для главного экрана:

- **Meshtastic** показывает в реальном времени локальную статистику с подключенной ноды.
- **Трекер нод** отслеживает выбранную тобой нооду: уровень заряда, сигнал, время последнего приема, количество хопов и расстояние до подключенной ноды.

Ты можешь разместить столько каждого, сколько захочешь. Каждый трекер нод запоминает свою собственную ноду.

## Локальная статистика

Виджет **Meshtastic** отображает текущую локальную статистику **подключенной ноды**:

- **Чип ноды**, содержащий краткое название ноды, выделенное его собственными цветами, с указанием уровня заряда **батареи** рядом с ним или _включено_ при работе от внешнего источника питания
- **ChUtil** — использование канала (насколько занят канал LoRa, в процентах)
- **AirUtil** — the percentage of airtime this node used transmitting in the last hour
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

> ℹ️ **Note:** The values reflect the connected node. If the node disconnects, the widget replaces the stats with a status line: **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Трекер нод

The **Node Tracker** widget shows one node from your node list:

- The node's **chip** and **long name**
- **Battery**: the node's last reported battery level, or _Powered_ on external power
- **Last heard**: the time your connected node last heard it, or the date if it was more than a day ago

From two rows tall it adds, in this order, as many as fit (a wide widget under two rows tall shows Hops Away beside the name):

- **Hops Away**: how many hops away the node is, or _Direct_
- **Distance**: how far the node is from your connected node. It appears only while you are connected and both nodes have a position
- **SNR** and **RSSI**: the signal of the node's last packet, shown only when the node is heard directly rather than through other nodes or MQTT

Four cells wide lays the readings out in two columns, so at the default font size every one fits in a two-row widget. A larger font size drops readings from the end of that list, RSSI first.

When a node has not been heard within the last two hours, the widget dims its readings so they do not read as current. Tap the widget to open that node's details in the app.

After you disconnect from your node in the app, the widget keeps the tracked node's last readings on screen, dimmed, until you reconnect.

If your node list does not include the node you picked, because you removed it or connected to a different node, the widget says **Node not found**. Tap it to choose another node.

## Добавление виджета

1. Touch & hold an empty area of your Android home screen.
2. Tap **Widgets**, then find **Meshtastic** in the list.
3. Drag the **Meshtastic** (local stats) or **Node Tracker** widget to your home screen.
4. Чтобы отслеживать ноды, выберите ноду, за которой нужно следить. Сначала перечисляются твои избранные, затем самые последние прослушиваемые ноды; введи в поле поиска, чтобы выполнить фильтрацию по названию или идентификатору ноды. Отмена возврата без выбора приводит к отмене виджета.
5. Изменение размера по мере необходимости — макет адаптируется к доступному пространству.

Чтобы позже отследить другую ноду, нажми и удерживай кнопку отслеживания нод и выбери настройки виджета или вариант перенастройки. Точная формулировка зависит от твоего лаунчера.

> ℹ️ **Примечание:** виджеты доступны только на Android. Они не доступны в десктопном приложении

## Связанные темы

- [Метрики нод](node-metrics) — полная статистика сигнала и локальная статистика внутри приложения
- [Ноды](nodes) — избранное, последнее обновление и сведения о нодах
- [Подключения](connections) — подключение к ноде, чтобы виджет отображал данные
- [Локальное обнаружение сети] (discovery) — использование канала и времени использования сети
