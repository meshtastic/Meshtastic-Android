---
title: MQTT
parent: Руководство пользователя
nav_order: 11
last_updated: 2026-05-13
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
[Ваша нода] → Радио → [Шлюзовая нода с WiFi] → MQTT-брокер → [Удалённый шлюз] → Радио → [Удалённая нода]
```

Шлюзовая нода с доступом в интернет (WiFi или Ethernet) публикует сообщения mesh-сети в топик MQTT. Удалённые шлюзы, подписанные на тот же топик, передают эти сообщения в свою локальную mesh-сеть.

## Настройки

### Включение MQTT

1. Перейдите в **Настройки → Конфигурация модулей → MQTT**.
2. Включите модуль MQTT.
3. Настройте подключение к брокеру:

![Переключатель MQTT](../../assets/screenshots/settings_switch.png)

| Настройка           | Описание                                                                            | По умолчанию                                        |
| ------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------- |
| Адрес сервера       | Имя хоста MQTT-брокера                                                              | mqtt.meshtastic.org |
| Имя пользователя    | Аутентификация брокера                                                              | meshdev                                             |
| Пароль              | Аутентификация брокера                                                              | large4cats                                          |
| Корневая тема       | Базовый топик для сообщений                                                         | msh                                                 |
| Шифрование          | Шифровать полезную нагрузку MQTT                                                    | Включено                                            |
| ~~Вывод в JSON~~    | ⚠️ **Устарело** — поддержка JSON-пакетов удалена из прошивки; это поле игнорируется | Отключено                                           |
| TLS                 | Безопасное подключение к брокеру                                                    | Отключено                                           |
| Публикация на карте | Сообщать о местоположении на публичную карту                                        | Отключено                                           |

### Стандартный брокер Meshtastic

Сообщество поддерживает публичный брокер по адресу `mqtt.meshtastic.org`. Он предназначен для общего использования и тестирования.

> ℹ️ **Примечание:** Подключения к `mqtt.meshtastic.org` всегда используют TLS (порт 8883), даже если переключатель TLS выключен. Для любого другого брокера TLS используется только при включении (порт 8883 с TLS, 1883 без).

> 🔒 **Приватность:** Сообщения на публичном брокере доступны для чтения всем, кто подписан. Всегда используйте шифрование каналов для конфиденциальной связи.

### Частный брокер

Для большей приватности и контроля ты можешь запустить собственный MQTT-брокер:

- Mosquitto (легковесный, с открытым исходным кодом)
- HiveMQ
- EMQX

Настройте свою ноду на подключение к частному брокеру с соответствующими учётными данными.

## Публикация на карте

Когда публикация на карте включена, твоя нода отправляет своё местоположение на карту сообщества Meshtastic:

- Доступно на [meshmap.net](https://meshmap.net) и аналогичных картографических сервисах сообщества
- Передаются только координаты и информация о ноде
- Отключи эту функцию, если не хочешь, чтобы твоё местоположение было общедоступным

## Uplink и Downlink

| Направление                                  | Описание                              |
| -------------------------------------------- | ------------------------------------- |
| **Uplink** (восходящий)   | Сообщения из mesh-сети → MQTT-брокер  |
| **Downlink** (нисходящий) | Сообщения от MQTT-брокера → mesh-сеть |

Настройте для каждого канала активные направления, чтобы управлять потоком сообщений и использованием эфирного времени.

## Форматы сообщений

MQTT использует формат сообщений protobuf:

| Формат       | Описание                                 | Сценарий использования           |
| ------------ | ---------------------------------------- | -------------------------------- |
| **Protobuf** | Бинарное кодирование Meshtastic protobuf | Соединение нод между mesh-сетями |

> ⚠️ **Примечание:** Поддержка вывода в JSON была удалена из прошивки. Настройка `json_enabled` всё ещё отображается в приложении для обратной совместимости, но не влияет на текущие версии прошивки.

## Шифрование и приватность

Понимание многоуровневой модели шифрования:

1. **Шифрование канала** происходит в mesh-сети _до_ MQTT. Если твой канал использует PSK, полезная нагрузка MQTT уже зашифрована — брокер и любые подписчики видят только зашифрованный текст.
2. **Шифрование MQTT** (настройка модуля) добавляет дополнительный уровень шифрования при передаче к брокеру. Это защищает метаданные и информацию о маршрутизации.
3. **TLS** encrypts the TCP connection to the broker itself, preventing network-level eavesdropping.

> 🔒 **Important:** The default public channel has a well-known key. Messages on the default channel sent via MQTT are effectively **unencrypted** — anyone can decode them. Always use a custom PSK for private communications.

## Best Practices

- Use channel-level encryption (PSK) on channels that bridge to MQTT
- Don't enable MQTT on nodes without internet access (it will buffer and waste memory)
- Use a private broker for sensitive deployments
- Be mindful of airtime when downlinking messages from busy MQTT topics — every downlinked message consumes radio airtime on your local mesh
- Consider enabling uplink-only if you only need to monitor your mesh remotely without injecting messages back

## Troubleshooting

### MQTT Not Connecting

- **Check WiFi** — the gateway node must have an active internet connection (WiFi or Ethernet). MQTT does not work over the LoRa radio link itself.
- **Verify credentials** — incorrect username or password will silently fail on most brokers. Double-check for trailing spaces.
- **Firewall** — port 1883 (MQTT) or 8883 (MQTT+TLS) must be open. Some networks block non-standard ports.
- **DNS resolution** — if using a custom broker hostname, verify the node can resolve it. Try the broker's IP address directly.

### Messages Not Bridging

- **Check uplink/downlink settings** — if only uplink is enabled, messages flow from mesh to MQTT but not back. Enable downlink on the receiving gateway.
- **Channel mismatch** — both gateways must share the same channel with the same PSK. A mismatch means messages are encrypted with different keys and appear as garbage.
- **Topic mismatch** — ensure both gateways use the same root topic. The default `msh` works for the public broker.

## Related Topics

- [Settings — Modules & Admin](settings-module-admin) — MQTT module configuration reference
- [Messages & Channels](messages-and-channels) — channel encryption and PSK setup
- [MQTT integration guide](https://meshtastic.org/docs/software/integrations/mqtt) — detailed MQTT documentation on meshtastic.org

---

