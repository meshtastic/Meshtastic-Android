---
title: Локальное обнаружение сети
parent: Руководство пользователя
nav_order: 12
last_updated: 2026-09-19
description: Исследуйте свою mesh-сеть — сканер локального обнаружения mesh-сети, трассировка путей, карты соседей и инструменты обнаружения нодов.
aliases:
  - discovery
  - local-mesh-discovery
  - mesh-discovery
  - local-discovery
  - network-scan
  - traceroute
  - neighbor-info
---

# Локальное обнаружение сети

Инструменты обнаружения помогают понять, **как** твоя mesh-сетевая структура соединена — какие ноды могут слышать друг друга, по каким путям проходят сообщения и где существуют узкие места или слабые звенья.

Приложение предлагает два дополнительных подхода:

- **Local Mesh Discovery (Scanner)** — an automated mode that cycles your connected node through different LoRa presets, listens on each, and ranks which preset performs best at your location.
- **Ручное исследование** — трассировка, информация о соседях и список нод, которые ты можешь использовать в любое время для изучения конкретных путей и топологии.

## Обнаружение локальной сети (Сканер)

Локальное обнаружение mesh-сети — это специализированный режим сканирования, который помогает найти лучший пресет LoRa-модема для твоего местоположения и увидеть, какие ноды активны на каждом пресете. It cycles your connected node through one or more presets you choose, dwells on each one — listens for a set time — to collect packets, then analyzes and ranks the results.

Connect your node, then open **Settings → Advanced → Local Mesh Discovery**. On Android the **Advanced** section appears only for a locally connected node, never over remote admin, and stays grayed out until the app has finished reading the node's configuration. On a managed device its entries are disabled, except **Debug Panel**, which reads app-local logs and stays available. On desktop, Local Mesh Discovery has its own entry on the Settings screen, with no such gate.

> ℹ️ **Note:** Discovery temporarily changes your node's LoRa settings while it scans, then restores your original configuration when it finishes.

### Setting up a scan

Перед началом, настройте эти параметры:

| Управление             | Описание                                                                                                                                                                                                                                                                             |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Выбор пресета LoRa** | Выберите один или несколько пресетов для сканирования. Обнаружение по очереди задерживается на каждом выбранном пресете.                                                                                                                             |
| **Время задержки**     | Время прослушки каждого пресета. Выбери один из вариантов: 1, 5, 15, 30, 45, 60, 90, 120 или 180 минут. Более долгое время задержки собирает больше пакетов и даёт более чёткую картину, но занимает больше времени. |
| **Не выключать экран** | Keeps the display on for the scan. The scan itself holds a CPU wake lock for its whole run and posts a **Scanning LoRa presets…** notification, so it keeps collecting with the screen off or the app in the background.                             |

The **Start Scan** button stays disabled — with an explanation of why — until the scan can run. Распространённые причины, почему она отключена:

- The node is **not connected**.
- **Не выбраны пресеты** для сканирования.
- Выбранный пресет использует частоту **2,4 ГГц**, которую ваше оборудование не поддерживает.

### Live progress

Во время сканирования "Обнаружение" показывает текущий этап:

| Этап                                                                   | Что происходит                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Preparing scan**                                                     | Сохранение твоей текущей конфигурации и подготовка к сканированию.                                                                                                                                                |
| **Переключение на \<preset\>**              | Switching the node to the next preset to test.                                                                                                                                                                    |
| **Reconnecting on \<preset\>**              | Восстановление соединения после смены пресета.                                                                                                                                                                    |
| **Dwelling on \<preset\>**                  | Прослушивание текущего пресета для сбора пакетов с обратным отсчётом до следующего шага.                                                                                                                          |
| **Analyzing results**                                                  | Обработка собранных пакетов и ранжирование пресетов.                                                                                                                                                              |
| **Restoring home preset**                                              | Возврат твоей исходной конфигурации LoRa.                                                                                                                                                                         |
| **Cancelling scan**                                                    | You tapped **Stop Scan**; partial results are saved before the original preset is restored.                                                                                                                       |
| **Scan failed: \<reason\>** | The scan could not continue — most often the node didn't come back within a minute of a preset change. The results collected so far are saved, and the original preset is restored automatically. |

![Обратный отсчёт времени прослушивания, показывающий оставшееся время на текущем пресете](../../assets/screenshots/discovery_dwell_progress.png)

If a scan is interrupted — the app is closed, or the node goes away — the app restores your original preset the next time it reconnects to that node, and tells you it has done so. Reconnect the same node to let that happen; until you do, the node stays on whichever preset the scan left it on.

### Reading the results

По завершении сканирования "Обнаружение" показывает карточку результатов для каждого протестированного пресета, а также общую сводку.

![Карточка результатов по пресету с рейтингом и собранными метриками](../../assets/screenshots/discovery_preset_result.png)

Метрики включают:

| Метрическая                     | Что для тебя это значит                                                                            |
| ------------------------------- | -------------------------------------------------------------------------------------------------- |
| Состояние радиоэфира            | Общее качество радиообстановки на этом пресете.                                    |
| Использование канала            | Насколько загруженным был эфир во время прослушивания.                             |
| Эфирное время                   | Наблюдаемое время передачи.                                                        |
| Прямые и ретранслированные ноды | Сколько mesh-нод было услышано напрямую, а сколько через ретранслятор.             |
| Повреждённые / повторные пакеты | Количество повреждённых и повторных пакетов, указывающее на перегрузку или помехи. |

Дополнительные функции, доступные из результатов:

- **История сканирования** — сохранённые сеансы, которые ты можешь просмотреть снова; просматривайте или удаляйте прошлые сканирования.
- **Карта обнаружения** — карта нод, найденных во время сканирования.
- **Экспорт отчёта** — экспортируйте отчёт в формате PDF на Android или в виде текста на других платформах.

> 💡 **Tip:** On **Google Play** builds, Discovery can generate an on-device AI summary (Gemini Nano) of your results. F-Droid builds always use the algorithmic summary — the proprietary ML Kit dependency is deliberately excluded from that flavor — so you get a readable interpretation of the scan either way.

## Маяк сети

Маяк mesh-сети позволяет нодам приглашать другие устройства присоединиться к своей сети. A beaconing node periodically broadcasts an invitation — optionally advertising a channel, region, and modem preset — that nearby nodes can hear even before they share a configuration.

Configure it under **Settings → Module configuration → Mesh Beacon**. The entry appears only on nodes running firmware 2.8.0 or newer. A read-only **Region** row at the top of the screen shows the region the beacon advertises: that region, and the preset, are always the ones the node itself uses, so a beacon can't invite anyone onto settings your node isn't running.

- **Слушать маяки** — принимать приглашения, передаваемые другими нодами.
- **Broadcast a beacon** — periodically advertise this mesh to nearby nodes, with an optional **Beacon message** of up to 100 bytes, a **Broadcast interval** picked from fixed intervals between 1 hour and 72 hours, and an **Offered channel** chosen from your node's own channels. The offered channel is required, and defaults to your primary channel. Over remote admin the picker offers the primary channel only.
- **Broadcast targets** — where the beacon actually transmits. The list always holds at least one row: the first is the beacon's own transmission, not an extra. Each row picks a **Channel** and a **Transmit preset**. **Add target** appends a row, and **Remove target** deletes one — removing the last row replaces it with a fresh default rather than emptying the list.

Two conditions block beacon setup:

- **The node has no region set.** The screen shows nothing but _Set your node's region before setting up a beacon._ Set the region on **Settings → LoRa** first.
- **The node uses custom LoRa settings.** A beacon advertises a modem preset for others to join, so a node with **Use Preset** turned off has no standard preset to offer. In that state **Broadcast a beacon** can be turned off but not on, and the broadcast settings are read-only. Listening for beacons is unaffected.

Полученные приглашения отображаются в виде карточек **"Приглашения mesh-сети"** на экране **"Обнаружение"**. На каждой карточке показано сообщение отправителя, а также предлагаемые канал, регион, пресет и качество сигнала, и доступны следующие действия:

- **Join** — switch to the offered channel and preset (retunes the node and reboots). Если предложение совпадает с вашим текущим частотным слотом, действие **"Добавить канал"** добавляет его без перезагрузки.
- **Обнаружить** — запустить сканирование «Обнаружения» с предложенным пресетом, чтобы ты мог изучить эту сеть перед присоединением (отображается, только если маяк передаёт пресет).
- **Отклонить** — проигнорировать приглашение.

Каналы, объявленные маяками, также отображаются в настройках сканирования как **Каналы маяков** — выберите один, чтобы включить его в число целей сканирования.

An invitation to a mesh your node is already on is suppressed: no card, no notification, and no **Beacon channels** entry. A channel counts as one you already have only when both its name and its key match a channel on your node — the same name with a different key is a different mesh, so that invitation still reaches you.

## Manual exploration

The following tools are available at any time from the node list and node detail screens. Используйте их для исследования конкретных путей и построения картины топологии — вместе с полным сканированием или вместо него.

### Трассировка маршрута

Трассировка показывает точный путь, который сообщение проходит от твоей ноды до любой другой ноды в сети. Это самый полезный инструмент для отладки проблем с подключением.

#### Выполнение трассировки

1. Перейди в **Ноды** и коснись ноды, которую ты хочешь отследить.
2. On the node detail screen, find **Traceroute** in the **Telemetry** section and tap its request button. Once a result arrives, a second button on the same row opens the traceroute log, where each hop is listed with its signal quality.

#### Reading the results

Результат трассировки выглядит так:

```text
Route traced toward destination:

■ Your Node (YOUR)
⇊ 8.5 dB
■ Relay Node (RLAY)
⇊ -8.75 dB
■ Target Node (TGT1)
```

Each `⇊` line between two nodes is one relay hop, and the SNR on that line is the quality of that segment alone. The app colors it against the demodulation floor of the preset in use, not a fixed number: green above the floor, yellow within 5.5 dB below it, orange within 7.5 dB, and red beyond that. The floor is −7.5 dB on Short Fast and improves 2.5 dB per spreading-factor step, so it is −17.5 dB on Long Fast — the same SNR reads differently on different presets. See [Signal Meter](signal-meter). A request that also gets a reply adds a second block under **Route traced back to us:**.

| На что обращать внимание                                    | Что это значит                                                                      |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| All hops show Good SNR (green)           | Здоровый путь — сообщения идут без сбоев                                            |
| One hop shows a poor SNR (orange or red) | Слабое звено — этот сегмент ретрансляции хрупкий                                    |
| Много хопов (4+)                         | Длинный путь — подумай о перемещении ноды для его сокращения                        |
| Другой путь при повторе                                     | Сеть адаптируется — существуют несколько маршрутов (это хорошо!) |

> 💡 **Совет:** Запусти трассировку несколько раз в течение нескольких минут. Если путь изменяется, у твоей сети есть лишние маршруты — признак хорошо связанной сети.

#### Устранение неполадок с трассировкой

- **No Response** — The traceroute got nothing back. The target node may be offline, out of range, or on a different channel. Проверь, что обе ноды имеют хотя бы один канал с одинаковым ключом шифрования.
- **Время ожидания трассировки истекло** — путь может быть слишком длинным (превышен лимит хопов) или нода ретрансляции перегружена. Try increasing the hop limit in **Settings → LoRa**.
- **Cannot show traceroute map because the start or destination node has no position information** — The path was traced, but one end has never shared a position.
- **Асимметричные маршруты** — трассировка от A→B может проходить по другому пути, чем B→A. This is normal — radio propagation isn't always symmetric.

### Информация об окружении

Модуль информации о соседях позволяет каждой ноде передавать список нод, которые она может **слышать напрямую** (на расстоянии одного хопа). Когда несколько нод делятся своими списками соседей, ты можешь составить карту топологии всей mesh-сети.

#### Включение информации о соседях

1. Navigate to **Settings → Module configuration → Neighbor Info**.
2. Включение модуля
3. Set **Update Interval**. The app accepts whatever you type; the firmware enforces its own minimum and resets a value below it.
4. Turn on **Transmit over LoRa**. Without it, your neighbor list goes only to MQTT and to this app, never over the air.

Once enabled and transmitting over LoRa, your node periodically broadcasts its neighbor list. Другие ноды с включённым модулем информации о соседях делают то же самое.

#### Viewing neighbor data

- Open a node's detail screen and find **Neighbor Info** in the **Telemetry** section. The request button asks the node for its current neighbor list; once the app has received one, a second button on the same row opens the log of everything that node has reported. The row appears only on nodes that can answer a neighbor request, or that have already reported neighbors.
- Каждая запись о соседе показывает ноду, которая была услышана напрямую, и качество её сигнала.
- Объединяйте данные о соседях от нескольких нод, чтобы понять полную топологию mesh-сети.

> ℹ️ **Note:** Neighbor Info increases airtime usage because every enabled node periodically broadcasts its neighbor list. The firmware doesn't accept an interval shorter than 14400 seconds (4 hours) for this reason; on busy meshes, leave it at the 21600-second default or raise it further.

### Node list as a discovery tool

Сам по себе список нод — мощный инструмент обнаружения, если ты эффективно будешь использовать его возможности фильтрации и сортировки.

#### Finding new nodes

- Сортируйте по **"Последнему приёму"**, чтобы увидеть вверху списка ноды, активные в последнее время.
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on nodes.

#### Assessing connectivity

- Сортируйте по «Хопам», чтобы видеть, какие ноды доступны напрямую (0 хопов), а какие — через ретрансляцию.
- Сортируйте по **"Расстоянию"**, чтобы найти близлежащие ноды и убедиться, что они доступны.
- Use **Exclude MQTT** to focus on nodes reachable over LoRa (not via internet bridge).

#### Infrastructure audit

- Отключите **"Исключить инфраструктуру"**, чтобы увидеть ноды Router, Router Late и Client Base.
- Проверьте качество их сигнала и время последнего приёма, чтобы убедиться, что твои ноды инфраструктуры работают исправно.

См. раздел [Ноды](Nodes) для получения полной информации о параметрах фильтрации и сортировки.

## Tips for Mesh exploration

- **Начните с трассировки** — она даёт тебе немедленную, пригодную для использования информацию о конкретном пути.
- **Включите информацию о соседях на ключевых нодах** — особенно на роутерах и ретрансляторах, чтобы составить картину магистральной сети.
- **Проверьте карту** — расположение нод на [Карте](map-and-waypoints) в сочетании с данными о сигнале помогает тебе понять, почему одни соединения сильные, а другие слабые.
- **Сравнивайте сигнал с течением времени** — используйте руководство по [Измерителю сигнала](signal-meter) для правильной интерпретации значений SNR и RSSI.

## Связанные темы

- [Nodes](nodes) — the node list these scans populate
- [Map & Waypoints](map-and-waypoints) — see discovered nodes geographically
- [Signal Meter](signal-meter) — interpret the SNR and RSSI a scan reports
- [Settings — Modules & Admin](settings-module-admin) — configure the Mesh Beacon and Neighbor Info modules
- [Messages & Channels](messages-and-channels) — join a mesh you found and start talking
