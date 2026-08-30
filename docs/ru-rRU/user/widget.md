---
title: Виджет на главный экран
parent: Руководство пользователя
nav_order: 20
last_updated: 2026-08-29
description: Добавь виджет главного экрана Meshtastic, чтобы видеть местную статистику своего подключенного радио без открытия приложения.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Виджет на главный экран

На Android, Meshtastic предоставляет **виджет** главного экрана, который показывает статистику по живым локальным каналам с подключенного устройства без необходимости открывать приложение.

## Что он покажет

Виджет показывает текущую локальную статистику **подключённого радиоустройства**:

- **Батарея** — уровень заряда батареи радиоустройства или _Питание от сети_, когда работает от внешнего источника
- **ChUtil** — использование канала (насколько занят канал LoRa, в процентах)
- **AirUtil** — использование времени передачи (сколько рабочих циклов передаёт вашего радиоустройство)
- **Трафик** — пакеты, переданные / полученные, и дубликаты
- **Relays** — пересылаемые пакеты и отмены пересылки (показаны, когда радио действует как ретранслятор)
- **Noise floor** — the measured background noise level
- **Dropped** — packets the radio discarded
- **Heap** — free versus total memory on the radio, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known

Нажми на виджет, чтобы открыть приложение, или используй его кнопку обновления для запроса свежей статистики.

> ℹ️ **Note:** The values reflect the connected radio. If the radio disconnects, the widget replaces the stats with a status line — **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## Добавление виджета

1. Touch & hold an empty area of your Android home screen.
2. Нажми **Виджет**.
3. Drag the **Meshtastic** widget to your home screen. The app ships one widget, so the picker entry is just the app name.
4. Изменение размера по мере необходимости — макет адаптируется к доступному пространству.

> ℹ️ **Note:** The widget is Android-only. Он недоступен для ПК или сборках для iOS.

## Связанные темы

- [Метрики нод](node-metrics) — полная статистика сигнала и локальная статистика внутри приложения
- [Подключения](connections) — подключиться к радиоустройству, чтобы виджет показывал статистику
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
