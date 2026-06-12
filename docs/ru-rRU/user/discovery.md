---
title: Обнаружение
parent: Руководство пользователя
nav_order: 12
last_updated: 2026-05-13
description: Исследуйте вашу mesh-сеть — трассировка, карты соседей и инструменты обнаружения нод.
aliases:
  - mesh-discovery
  - local-discovery
  - network-scan
  - traceroute
  - neighbor-info
---

# Обнаружение

Инструменты обнаружения помогают понять, **как** твоя mesh-сетевая структура соединена — какие ноды могут слышать друг друга, по каким путям проходят сообщения и где существуют узкие места или слабые звенья.

> 💡 **Совет:** Тебе не нужен специальный "режим обнаружения", чтобы начать изучать свою сеть. Инструменты ниже доступны прямо сейчас на экранах списка нод и деталей ноды.

---

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

| На что обращать внимание                                                                | Что это значит                                                                      |
| --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Все хопы показывают хороший SNR (> 5 дБ)                             | Здоровый путь — сообщения идут без сбоев                                            |
| Все переходы показывают плохой SNR (< 0 дБ) | Слабое звено — этот сегмент ретрансляции хрупкий                                    |
| Много хопов (4+)                                                     | Длинный путь — подумай о перемещении ноды для его сокращения                        |
| Другой путь при повторе                                                                 | Сеть адаптируется — существуют несколько маршрутов (это хорошо!) |

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

