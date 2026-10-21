---
title: MQTT
nav_order: 11
last_updated: 2026-10-21
description: Подключите свою mesh-сеть к интернету — настройка MQTT-брокера, уровни шифрования и отчётность на карте.
aliases:
  - mqtt
  - internet-bridge
  - broker
---

# MQTT

MQTT соединяет твою mesh-сеть Meshtastic с интернетом, обеспечивая связь на больших расстояниях за пределами радиодиапазона.

## Обзор

Модуль MQTT подключает вашу ноду к MQTT-брокеру, что позволяет:

- Сообщениям достигать нод в других физических mesh-сетях через интернет
- Интегрироваться с системами домашней автоматизации и мониторинга
- Публиковать местоположения нод на публичной карте Meshtastic
- Создавать собственные каналы данных для журналирования и оповещений

## Как это работает

```text
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. Удалённые шлюзы, подписанные на тот же топик, передают эти сообщения в свою локальную mesh-сеть.

## Настройки

### Включение MQTT

1. Navigate to **Settings → Module configuration → MQTT**.
2. Turn on **MQTT Enabled**.
3. Настройте подключение к брокеру:

| Настройка               | Описание                                                                                                                                                    | По умолчанию                                                                                           |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Address**             | MQTT broker hostname. Empty means the public broker                                                                                         | mqtt.meshtastic.org                                                    |
| **Username**            | Аутентификация брокера                                                                                                                                      | meshdev                                                                                                |
| **Password**            | Аутентификация брокера                                                                                                                                      | large4cats                                                                                             |
| **Encryption Enabled**  | Publish packets still encrypted with the channel key                                                                                                        | Включено                                                                                               |
| **JSON output enabled** | Shown only for a node on firmware older than 2.8, or while the setting is still on. See [Message Formats](#message-formats) | Отключено                                                                                              |
| **TLS Enabled**         | Безопасное подключение к брокеру                                                                                                                            | Отключено                                                                                              |
| **Root Topic**          | Базовый топик для сообщений                                                                                                                                 | `msh`, which the node rewrites to `msh/<REGION>` when you set a region while it uses the public broker |
| **MQTT Client Proxy**   | Relay MQTT through the connected app                                                                                                                        | Отключено                                                                                              |
| **Map Reporting**       | Сообщать о местоположении на публичную карту                                                                                                                | Включено                                                                                               |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the status of the relay this app runs:
**Connected**, **Connecting…**, **Reconnecting…**, **Disconnected**, or **Inactive**. It reads
**Inactive** whenever the app is not relaying, which includes the normal case of a node
reaching the broker over its own Wi-Fi or Ethernet. The node's own connection to the broker is
not reported here.

When the broker accepts the connection but refuses a subscription, the status names the refused
topics and the broker's reason: _Connected, but the broker refused every topic_ when nothing was
granted, or _Connected, but the broker refused_ followed by the topics when only some were. A
broker's access-control list is the usual cause.

**Test connection** probes the broker before you commit the settings to the node, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### MQTT Proxy in This App

If your node has no internet access of its own, it can use the app as its MQTT gateway. Turn on **MQTT Enabled** and **MQTT Client Proxy** in the module config. The app then relays MQTT traffic between the node and the broker over your phone's or computer's internet connection.

The **MQTT proxy in this app** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately, without editing and re-saving the node's MQTT configuration.

### Стандартный брокер Meshtastic

Сообщество поддерживает публичный брокер по адресу `mqtt.meshtastic.org`. Он предназначен для общего использования и тестирования.

When this app relays MQTT for the node, connections to that broker always use TLS on port 8883 even if **TLS Enabled** is off. That upgrade is the app's own, and the switch says so. A node that reaches the broker over its own Wi-Fi or Ethernet forces nothing: it uses **TLS Enabled** as stored, so turn it on yourself or the node connects in the clear on port 1883. For any other broker the toggle decides in both cases. A port written in the address, as in `broker.example.com:8884`, is kept; without one, TLS uses port 8883 and plain connections use 1883.

> 🔒 **Приватность:** Сообщения на публичном брокере доступны для чтения всем, кто подписан. Всегда используйте шифрование каналов для конфиденциальной связи.

### Частный брокер

Для большей приватности и контроля ты можешь запустить собственный MQTT-брокер:

- Mosquitto (легковесный, с открытым исходным кодом)
- HiveMQ
- EMQX

Настройте свою ноду на подключение к частному брокеру с соответствующими учётными данными.

## Публикация на карте

When **Map Reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map Publish Interval** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map Reporting** off if you do not want your approximate location published.

## Uplink и Downlink

| Направление                                  | Описание                              |
| -------------------------------------------- | ------------------------------------- |
| **Uplink** (восходящий)   | Сообщения из mesh-сети → MQTT-брокер  |
| **Downlink** (нисходящий) | Сообщения от MQTT-брокера → mesh-сеть |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## Форматы сообщений

Gateways publish packets as binary Meshtastic protobufs on the `/2/e/` topic, which is what node-to-node mesh bridging uses.

Firmware 2.7 and older can also publish human-readable JSON on the `/2/json/` topic for consumers outside the mesh, such as dashboards and home automation, when `json_enabled` is on. Firmware 2.8 removed JSON support and ignores the field. The app shows **JSON output enabled** only for a node on older firmware, or while the setting is still on, and while it is on the app's own MQTT proxy also subscribes to the `/2/json/` topic and decodes those payloads.

## Шифрование и приватность

Понимание многоуровневой модели шифрования:

1. **Шифрование канала** происходит в mesh-сети _до_ MQTT. Если твой канал использует PSK, полезная нагрузка MQTT уже зашифрована — брокер и любые подписчики видят только зашифрованный текст.
2. **Encryption Enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** шифрует само TCP-соединение с брокером, предотвращая перехват на сетевом уровне.

> 🔒 **Security:** The default public channel has a well-known key. Сообщения в канале по умолчанию, отправленные через MQTT, фактически **не зашифрованы** — кто угодно может их расшифровать. Всегда используйте собственный PSK для конфиденциальной связи.

## Рекомендации

- Используйте шифрование на уровне канала (PSK) на каналах, подключённых к MQTT
- Don't enable MQTT on a node with no internet access of its own unless **MQTT Client Proxy** is on (the node queues messages it can't send and wastes memory)
- Используйте частный брокер для задач, требующих повышенной безопасности
- Учитывайте эфирное время при передаче сообщений из загруженных MQTT-топиков — каждое такое сообщение расходует радиоэфирное время в вашей локальной mesh-сети
- Рассмотрите включение режима "только uplink", если нужно лишь удалённо наблюдать за mesh-сетью, не отправляя сообщения обратно

## Устранение неполадок

### MQTT не подключается

- **Check Wi-Fi** — the gateway node must have an active internet connection (Wi-Fi or Ethernet). MQTT не работает через сам радиоканал LoRa.
- **Verify credentials** — with incorrect credentials, most brokers fail silently — double-check for trailing spaces.
- **Firewall** — port 1883 (MQTT) or 8883 (MQTT over TLS) must be reachable. Some networks allow only web traffic (ports 80 and 443).
- **Разрешение DNS** — если используется имя хоста собственного брокера, убедитесь, что нода может его разрешить. Попробуйте подключиться напрямую по IP-адресу брокера.

### Сообщения не проходят через мост

- **Проверьте настройки uplink/downlink** — если включён только uplink, сообщения идут из mesh-сети в MQTT, но не обратно. Включите downlink на принимающем шлюзе.
- **Несовпадение каналов** — оба шлюза должны использовать один и тот же канал с одинаковым PSK. Несовпадение означает, что сообщения зашифрованы разными ключами и выглядят как мусор.
- **Topic mismatch** — both gateways must use exactly the same root topic. On the public broker, setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root. Firmware 2.8.0 and older also rewrite a root starting with `msh` on a private broker.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the node turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **OK to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **OK to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## Связанные темы

- [Настройки — Модули и администрирование](settings-module-admin) — справочник по конфигурации модуля MQTT
- [Сообщения и каналы](messages-and-channels) — шифрование каналов и настройка PSK
- [Руководство по интеграции MQTT](https://meshtastic.org/docs/software/integrations/mqtt) — подробная документация по MQTT на meshtastic.org
