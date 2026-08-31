---
title: MQTT
parent: Руководство пользователя
nav_order: 11
last_updated: 2026-08-30
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

```
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. Удалённые шлюзы, подписанные на тот же топик, передают эти сообщения в свою локальную mesh-сеть.

## Настройки

### Включение MQTT

1. Navigate to **Settings → Module configuration → MQTT**.
2. Включите модуль MQTT.
3. Настройте подключение к брокеру:

| Настройка                   | Описание                                                                                                                                                                          | По умолчанию                                                            |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| **Address**                 | Имя хоста MQTT-брокера                                                                                                                                                            | mqtt.meshtastic.org                     |
| **Username**                | Аутентификация брокера                                                                                                                                                            | meshdev                                                                 |
| **Password**                | Аутентификация брокера                                                                                                                                                            | large4cats                                                              |
| **Root topic**              | Базовый топик для сообщений                                                                                                                                                       | `msh`, which the radio rewrites to `msh/<REGION>` once you set a region |
| **Encryption enabled**      | Шифровать полезную нагрузку MQTT                                                                                                                                                  | Включено                                                                |
| **JSON output enabled**     | Also publish and consume the `/2/json/` topic. Deprecated in the protobuf schema, but still the only toggle for this behavior — and the app's own proxy honors it | Отключено                                                               |
| **TLS enabled**             | Безопасное подключение к брокеру                                                                                                                                                  | Отключено                                                               |
| **Map reporting**           | Сообщать о местоположении на публичную карту                                                                                                                                      | Отключено                                                               |
| **Proxy to client enabled** | Relay MQTT through the connected phone                                                                                                                                            | Включено                                                                |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the status of the relay this phone runs —
**Connected**, **Connecting**, **Reconnecting**, **Disconnected**, or **Inactive**. It reads
**Inactive** whenever the phone is not relaying, which includes the normal case of a radio
reaching the broker over its own Wi-Fi or Ethernet. The radio's own connection to the broker is
not reported here.

**Test connection** probes the broker before you commit the settings to the radio, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### MQTT-прокси на этом телефоне

If your radio has no internet access of its own, it can use the connected phone as its MQTT gateway: enable **MQTT** and **Proxy to client enabled** in the module config, and the app relays MQTT traffic between the radio and the broker over your phone's internet connection.

> ℹ️ **Note:** The proxy relay is mobile-only. On the Desktop app the MQTT settings are present, but no relay runs behind them.

The **MQTT proxy on this phone** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately — without editing and re-saving the radio's MQTT configuration.

### Стандартный брокер Meshtastic

Сообщество поддерживает публичный брокер по адресу `mqtt.meshtastic.org`. Он предназначен для общего использования и тестирования.

When this phone relays MQTT for the radio, connections to that broker always use TLS on port 8883 even if **TLS enabled** is off — the app forces the switch on and grays it out. A radio that reaches the broker over its own Wi-Fi or Ethernet forces nothing: turn **TLS enabled** on yourself, or it connects in the clear on port 1883. For any other broker the toggle decides in both cases (port 8883 with TLS, 1883 without).

> 🔒 **Приватность:** Сообщения на публичном брокере доступны для чтения всем, кто подписан. Всегда используйте шифрование каналов для конфиденциальной связи.

### Частный брокер

Для большей приватности и контроля ты можешь запустить собственный MQTT-брокер:

- Mosquitto (легковесный, с открытым исходным кодом)
- HiveMQ
- EMQX

Настройте свою ноду на подключение к частному брокеру с соответствующими учётными данными.

## Публикация на карте

When **Map reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map reporting interval (seconds)** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map reporting** off if you do not want your approximate location published.

## Uplink и Downlink

| Направление                                  | Описание                              |
| -------------------------------------------- | ------------------------------------- |
| **Uplink** (восходящий)   | Сообщения из mesh-сети → MQTT-брокер  |
| **Downlink** (нисходящий) | Сообщения от MQTT-брокера → mesh-сеть |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## Форматы сообщений

MQTT carries two payload formats:

| Формат       | Описание                                    | Сценарий использования                                                      |
| ------------ | ------------------------------------------- | --------------------------------------------------------------------------- |
| **Protobuf** | Бинарное кодирование Meshtastic protobuf    | Соединение нод между mesh-сетями                                            |
| **JSON**     | Human-readable JSON on the `/2/json/` topic | Consumers outside the mesh (dashboards, home automation) |

> ℹ️ **Note:** `json_enabled` is marked deprecated in the protobuf schema, but it has not been
> replaced and it is not ignored. When it is on, the app's own MQTT proxy subscribes to the
> `/2/json/` topic and decodes those payloads.

## Шифрование и приватность

Понимание многоуровневой модели шифрования:

1. **Шифрование канала** происходит в mesh-сети _до_ MQTT. Если твой канал использует PSK, полезная нагрузка MQTT уже зашифрована — брокер и любые подписчики видят только зашифрованный текст.
2. **Encryption enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** шифрует само TCP-соединение с брокером, предотвращая перехват на сетевом уровне.

> 🔒 **Security:** The default public channel has a well-known key. Сообщения в канале по умолчанию, отправленные через MQTT, фактически **не зашифрованы** — кто угодно может их расшифровать. Всегда используйте собственный PSK для конфиденциальной связи.

## Рекомендации

- Используйте шифрование на уровне канала (PSK) на каналах, подключённых к MQTT
- Don't enable MQTT on nodes without internet access (the radio buffers unsendable messages and wastes memory)
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
- **Topic mismatch** — both gateways must use exactly the same root topic. Setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the radio turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **Ok to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **Ok to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## Связанные темы

- [Настройки — Модули и администрирование](settings-module-admin) — справочник по конфигурации модуля MQTT
- [Сообщения и каналы](messages-and-channels) — шифрование каналов и настройка PSK
- [Руководство по интеграции MQTT](https://meshtastic.org/docs/software/integrations/mqtt) — подробная документация по MQTT на meshtastic.org
