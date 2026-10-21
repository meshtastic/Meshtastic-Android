---
title: Интеграция TAK
nav_order: 10
last_updated: 2026-10-21
description: Взаимодействие с ATAK и WinTAK — передача данных CoT о местоположении, роли TAK и настройка плагина.
aliases:
  - tak
  - atak
  - team-awareness-kit
---

# Интеграция TAK

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic nodes and TAK applications like ATAK and WinTAK.

## Обзор

Модуль TAK позволяет нодам Meshtastic:

- Делиться данными о местоположении в формате CoT (Cursor on Target), совместимом с TAK
- Отображаться как члены команды на картах TAK
- Получать сообщения PLI (информация о местоположении) TAK

## Настройка

### Необходимые условия

- ATAK (Android Team Awareness Kit), iTAK, or WinTAK installed
- Your node's **Device Role** (Device configuration) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module configuration at all
- Firmware 2.8.0 or newer on the node. Earlier firmware accepts a TAK config write and never
  stores it, so the app hides the TAK module entry below that version even when the role is set
  correctly. See [Firmware Updates](firmware)

> ⚠️ **Warning:** The old **Meshtastic ATAK Plugin** is no longer part of this path and cannot
> work. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service runs in-process only. Do not install it. Interop runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described in the following sections, with stock ATAK/iTAK/WinTAK.

### Настройки

Navigate to **Settings → Module configuration → TAK (ATAK)**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Device Role** setting in Device configuration is what
turns TAK on. Your node broadcasts this identity, which appears on TAK maps.

| Настройка | Описание                                                                                                                       | По умолчанию                                       |
| --------- | ------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| Роль      | Your operational role within the team: Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, or K9 | По умолчанию (Участник команды) |
| Командой  | Your team color on the TAK map, one of the 14 standard TAK team colors such as Blue, Red, Cyan, or Green                       | Default (Cyan)                  |

Your TAK callsign isn't a separate setting. The firmware uses your node's long name.

Team colors are the standard TAK team colors, so coordinate with your TAK team to use consistent team assignments.

### Локальный TAK-сервер

The app can also run a **local TAK server** so ATAK on the **same phone**, or WinTAK on the same computer running the desktop app, can connect directly without a remote TAK server. Сервер привязывается только к локальному хосту (`127.0.0.1:8089`) и использует TLS с взаимной аутентификацией по сертификатам (mTLS), поэтому он недоступен с других устройств в сети.

Open **Settings → Advanced → TAK Server**. These are app settings stored on this phone, which is why they sit next to **Firmware Update** and **Debug Panel** rather than under the TAK module config — and why they need neither a TAK role nor firmware 2.8.0. On Android 17 and newer, turning the server on asks for the local network permission it needs to open its socket. If that permission is denied for good, the app turns the server back off and offers to open app settings:

![Настройки локального TAK-сервера с переключателем включения и опцией экспорта](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK connections from the same device.
- **Status** — under the toggle: _Off_, _Starting TAK Server_, _Listening on 127.0.0.1:8089 — waiting for ATAK_, the count of local client connections, or _Unable to start TAK Server. Turn it off and on to retry._
- **TAK Mesh Channel** — выбирает, в какой канал Meshtastic отправляется исходящий трафик TAK (по умолчанию: основной канал, индекс 0). Входящий трафик TAK принимается с любого канала. Соответствует аналогичной настройке на iOS и в устаревшем плагине ATAK.
- **Mesh to CoT Converter** — off by default, and shown under the server toggle. With the server
  running, this synthesizes a CoT contact for every node in your node database, so ordinary
  Meshtastic nodes appear on the ATAK map as contacts. **This is what replaced the old plugin's
  node visibility** — without it, only TAK-role nodes show up. When the connected node runs
  firmware older than 2.8.0, a notice under the switch says that only location and chat messages
  bridge to ATAK.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK can import to connect to this server. The share icon in the top bar does the same.

## Роли TAK

Ноды, настроенные с ролями, связанными с TAK, ведут себя иначе, чем стандартные клиенты:

| Роль            | Описание                                                                                                                                                                                                                          |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**         | For a node paired with an ATAK device. Turns off many routine broadcasts to favor the CoT packet stream.                                                                                          |
| **TAK Tracker** | Broadcasts TAK PLI automatically, at the interval set by the position configuration, and turns off many routine broadcasts. Suited to unattended position beacons such as vehicles and equipment. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a node mounted in a vehicle). Используйте **TAK** для устройств, где пользователи активно участвуют в операциях TAK.

### Формат CoT (Cursor on Target)

Сообщения TAK используют формат XML Cursor on Target — военный стандарт для обмена данными о ситуационной осведомлённости. Meshtastic преобразует свои внутренние сообщения protobuf в формат CoT при соединении с системами TAK, поэтому ручное преобразование формата не требуется.

## Формат передачи (V1 / V2)

Meshtastic supports two TAK wire formats, chosen automatically based on the connected node's firmware — no manual configuration needed:

| Формат                             | Совместимость                                           | Особенности                                                                                                                                                                                                                                    |
| ---------------------------------- | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V1 (Устаревший) | Прошивка 2.7.x и старше | Простое кодирование protobuf на порт 72. Поддерживает только передачу местоположения (PLI) и чат (GeoChat) — фигуры, маркеры, маршруты и другие типизированные события CoT отбрасываются |
| V2 (Текущий)    | Прошивка 2.8.0 и новее  | Компактное кодирование со сжатием zstd на порт 78. Добавляет типы CoT: фигуры, маркеры, маршруты, воздушные суда, эвакуацию, чрезвычайные ситуации и задачи — в дополнение ко всему, что поддерживает V1       |

Нода продолжает ретранслировать устаревшие пакеты V1 от старых нод, даже если сама работает на V2, поэтому смешанные по прошивке mesh-сети продолжают функционировать.

## Использование с ATAK

После настройки:

- Ноды Meshtastic отображаются как маркеры на карте ATAK с подписями позывных
- Сообщения чата могут передаваться между mesh-сетью и сетями TAK
- Обновления местоположения передаются двунаправленно между Meshtastic и TAK
- Ноды TAK Tracker автоматически передают PLI — их координаты отображаются на картах ATAK без какой-либо настройки на стороне ATAK
- On Android, routes received from the mesh are also saved as a data package (`.zip`) in **Downloads**; import it in ATAK to add the route. An update to the same route overwrites its file rather than adding a copy. After the app is reinstalled, the first update to a route saves one renamed copy, and later updates overwrite that. On Android 9 and older the file goes to the app's own folder under `Android/data` instead. The desktop app doesn't save route files

> ℹ️ **Note:** TAK integration requires specific node roles. Standard client nodes don't automatically participate in TAK operations — though with **Mesh to CoT Converter** enabled they still appear on the ATAK map as contacts.

## Устранение неполадок

| Проблема                                   | Причина                                                                                                                   | Решение                                                                                                                                                                                                                |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Нода не отображается на карте ATAK         | Wrong Device Role setting, or Mesh to CoT Converter off                                                                   | Set the node's **Device Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under **Settings → Advanced → TAK Server** |
| Данные о местоположении устарели           | Потерян сигнал GPS или слишком большой интервал                                                                           | Проверьте состояние GPS; уменьшите интервал передачи координат в настройках Position Config                                                                                                                            |
| ATAK shows "disconnected"                  | The local TAK server is off, or ATAK is pointed elsewhere                                                                 | Check **Enable Local TAK Server** is on, and that ATAK is connecting to `127.0.0.1:8089` — re-import the exported data package if unsure                                                                               |
| Фигуры, маркеры или маршруты не передаются | Отправляющая нода использует устаревший V1 (прошивка 2.7.x или старше) | Обновите прошивку отправляющей ноды до 2.8.0+ для использования формата передачи V2                                                                                                    |
| Данные CoT не передаются                   | Несовпадение каналов                                                                                                      | Все ноды TAK должны находиться на одном канале с одинаковым шифрованием                                                                                                                                                |

## Соображения безопасности

> 🔒 **Privacy:** TAK data shares your position and callsign information. The TAK module respects
> the same channel encryption as other Meshtastic messages — in sensitive environments, use a
> channel with a non-default key.

## Связанные темы

- [Настройки — Модули и администрирование](settings-module-admin) — конфигурация модуля TAK
- [Ноды](nodes) — роли TAK и TAK Tracker в списке нод
- [Карта и путевые точки](map-and-waypoints) — расположение нод на карте
