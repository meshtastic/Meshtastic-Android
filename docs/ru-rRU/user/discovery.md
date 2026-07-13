---
title: Обнаружение
parent: Руководство пользователя
nav_order: 12
last_updated: 2026-07-08
description: Explore your mesh network — the Local Mesh Discovery scanner, traceroute paths, neighbor maps, and node discovery tools.
aliases:
  - mesh-discovery
  - local-discovery
  - network-scan
  - traceroute
  - neighbor-info
---

# Обнаружение

Инструменты обнаружения помогают понять, **как** твоя mesh-сетевая структура соединена — какие ноды могут слышать друг друга, по каким путям проходят сообщения и где существуют узкие места или слабые звенья.

Приложение предлагает два дополнительных подхода:

- **Local Mesh Discovery (Scanner)** — an automated mode that cycles your connected radio through different LoRa presets, listens on each, and ranks which preset performs best at your location.
- **Manual exploration** — traceroute, Neighbor Info, and the node list, which you can use at any time to investigate specific paths and topology.

---

## Обнаружение локальной сети (Сканер)

Local Mesh Discovery is a dedicated scanning mode that helps you find the best LoRa modem preset for your location and see which nodes are active on each preset. It cycles your connected radio through one or more presets you choose, listens (or "dwells") on each one for a set time to collect packets, then analyzes and ranks the results.

Open it from **Settings → Local Mesh Discovery**.

> ⚠️ **Note:** Discovery temporarily changes your radio's LoRa settings while it scans, then restores your original configuration when it finishes. Your device must be connected to run a scan.

### Настройка сканирования

Before starting, configure these controls:

| Управление                  | Описание                                                                                                                                                                                                                                                    |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| \*\*Выбор пресета LoRa \*\* | Select one or more presets to scan. Discovery dwells on each selected preset in turn.                                                                                                                                       |
| **Dwell time**              | Время послушки каждого пресета. Выбери один из вариантов: 1, 5, 15, 30, 45, 60, 90, 120 или 180 минут. Longer dwell times collect more packets and give a clearer picture, but take longer. |
| **Не выключать экран**      | Optional toggle that prevents the screen from sleeping during a long scan.                                                                                                                                                                  |

The **Start** button stays disabled — with an explanation of why — until the scan can run. Common reasons it's disabled:

- The device is **not connected**.
- **No presets** have been selected to scan.
- The selected preset uses **2.4 GHz**, which your hardware doesn't support.

### Текущий прогресс

While a scan runs, Discovery shows its current stage:

| Этап                                                  | What's happening                                                                                       |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Подготовка**                                        | Saving your current configuration and getting ready to scan.                           |
| **Shifting to \<preset\>** | Switching the radio to the next preset to test.                                        |
| **Reconnecting**                                      | Re-establishing the connection after the preset change.                                |
| **Dwell**                                             | Listening on the current preset to collect packets, with a countdown to the next step. |
| **Analysis**                                          | Processing the collected packets and ranking the presets.                              |
| **Restoring**                                         | Putting your original LoRa configuration back.                                         |

![Dwell countdown showing time remaining on the current preset](../../assets/screenshots/discovery_dwell_progress.png)

### Чтение результатов

When the scan completes, Discovery presents a per-preset result card for each preset it tested, plus an overall summary.

![Per-preset result card with ranking and collected metrics](../../assets/screenshots/discovery_preset_result.png)

Metrics include:

| Метрическая                              | What it tells you                                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------- |
| RF health                                | Overall quality of the radio environment on that preset.                       |
| Использование канала                     | How busy the airwaves were during the dwell.                                   |
| Время вещания                            | Transmission time observed.                                                    |
| Direct vs. relayed nodes | How many mesh nodes were heard directly versus via a relay.                    |
| Bad / duplicate packets                  | Counts of corrupt and repeated packets, indicating congestion or interference. |

Additional features available from the results:

- **Scan History** — saved sessions you can revisit; view or delete past scans.
- **Discovery Map** — a map of the nodes found during the scan.
- **Report export** — export a report as a PDF on Android, or as text on other platforms.

> 💡 **Tip:** On Android, Discovery can generate an on-device AI summary (Gemini Nano) of your results. If the on-device model isn't available, an algorithmic summary is used instead — so you always get a readable interpretation of the scan.

---

## Manual Exploration

The tools below are available at any time from the node list and node detail screens. Use them to investigate specific paths and build a topology picture, alongside or instead of a full scan.

## Трассировка маршрута

Трассировка показывает точный путь, который сообщение проходит от твоей ноды до любой другой ноды в сети. Это самый полезный инструмент для отладки проблем с подключением.

### Выполнение трассировки

1. Перейди в **Ноды** и коснись ноды, которую ты хочешь отследить.
2. На экране деталей ноды нажми **Трассировка**.
3. Приложение отправляет запрос трассировки и ожидает ответа.
4. Результаты отображают каждую ноду по порядку с качеством сигнала на каждом этапе.

### Чтение результатов

Результат трассировки выглядит так:

```
Ты → Нода A (SNR: 8.5, RSSI: -95) → Нода B (SNR: 5.2, RSSI: -108) → Цель
```

Каждый хоп представляет собой ретранслирующую ноду, которая переслала сообщение. Значения SNR и RSSI на каждой ноде говорят о качестве соединения на этом конкретном участке.

| На что обращать внимание                                                          | Что это значит                                                                      |
| --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| All hops show Good SNR (≥ −7 dB, green)                        | Здоровый путь — сообщения идут без сбоев                                            |
| One hop shows Bad SNR (< −15 dB, red) | Слабое звено — этот сегмент ретрансляции хрупкий                                    |
| Много хопов (4+)                                               | Длинный путь — подумай о перемещении ноды для его сокращения                        |
| Другой путь при повторе                                                           | Сеть адаптируется — существуют несколько маршрутов (это хорошо!) |

> 💡 **Совет:** Запусти трассировку несколько раз в течение нескольких минут. Если путь изменяется, у твоей сети есть лишние маршруты — признак хорошо связанной сети.

### Устранение неполадок с трассировкой

- **''Маршрут не найден''** — Целевая нода может быть оффлайн, находиться вне зоны действия или на другом канале. Проверь, что обе ноды имеют хотя бы один канал с одинаковым ключом шифрования.
- **Время ожидания трассировки истекло** — путь может быть слишком длинным (превышен лимит хопов) или нода ретрансляции перегружена. Попробуй увеличить лимит хопов в **Настройки → Конфигурация LoRa**.
- **Асимметричные маршруты** — трассировка от A→B может проходить по другому пути, чем B→A. This is normal — radio propagation is not always symmetric.

---

## Информация об окружении

The Neighbor Info module lets each node broadcast a list of the nodes it can **directly hear** (single-hop). When multiple nodes share their neighbor lists, you can piece together a topology map of the entire mesh.

### Включение информации о соседях

1. Перейдите в **Настройки → Конфигурация модуля → Информация о соседях**.
2. Включение модуля
3. Set the broadcast interval (default: 900 seconds / 15 minutes).

Once enabled, your node periodically broadcasts its neighbor table. Other nodes with Neighbor Info enabled do the same.

### Просмотр данных соседа

- Open any node's detail screen and look for the **Neighbors** section.
- Each neighbor entry shows the node that was directly heard and its signal quality.
- Combine neighbor data from multiple nodes to understand the full mesh topology.

> ⚠️ **Note:** Neighbor Info increases airtime usage because every enabled node periodically broadcasts its neighbor list. On busy meshes with many nodes, consider longer broadcast intervals (3600 seconds or more) to avoid congestion.

---

## Список узлов как инструмент для обзора

The node list itself is a powerful discovery tool when you use its filtering and sorting features effectively.

### Поиск новых узлов

- Sort by **Last heard** to see the most recently active nodes at the top.
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on devices.

### Оценка подключения

- Sort by **Hops away** to see which nodes are directly reachable (0 hops) versus relayed.
- Sort by **Distance** to find nearby nodes and verify they're reachable.
- Use **Exclude MQTT** to focus on nodes reachable over radio (not via internet bridge).

### Аудит инфраструктуры

- Disable **Exclude infrastructure** to see Router, Repeater, Router Late, and Client Base nodes.
- Check their signal quality and last-heard times to verify your infrastructure nodes are healthy.

See [Nodes](nodes) for full details on filtering and sorting options.

---

## Советы по исследованию сети

- **Start with traceroute** — it gives you immediate, actionable information about a specific path.
- **Enable Neighbor Info on key nodes** — especially routers and repeaters, to build a picture of the backbone.
- **Check the map** — node positions on the [Map](map-and-waypoints) combined with signal data help you understand why some links are strong and others are weak.
- **Compare signal over time** — use the [Signal Meter](signal-meter) guide to interpret SNR and RSSI values correctly.

---

