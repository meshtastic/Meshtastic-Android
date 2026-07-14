---
title: Функции приложения
parent: Руководство пользователя
nav_order: 19
last_updated: 2026-06-11
description: Предоставьте возможности mesh системе Android и помощникам с ИИ на устройстве (например, Gemini), чтобы они могли выполнять mesh-воркфлоу без открытия приложения.
aliases:
  - app-functions
  - system-ai
  - gemini
  - assistant-functions
---

# Функции приложения

Функции приложения предоставляют возможности Meshtastic системе Android и встроенным AI-помощникам (таким как Gemini) через API функций приложения Android. С их включением помощник может находить и запускать сетевые рабочие процессы за тебя — например, отправлять сообщение или проверять статус сети — не утруждая тебя открытием приложения.

> ⚠️ **Примечание:** Функции приложения доступны только на **Android-версиях от Google**.

> ⚠️ **Примечание:** Это отдельно от встроенного в приложении помощника **Chirpy**. Функции приложения позволяют _системному_ AI-помощнику действовать с вашей сетью; Chirpy — это голосовой помощник прямо внутри приложения Meshtastic.

## Включение функций приложения

Функции приложения управляются через **Настройки → Системный ИИ** (на экране в приложении указано "Системный ИИ"). Экран содержит:

- A **master toggle** labeled **"Allow AI access"**, with the subtitle _"Let system AI assistants (e.g. Gemini) discover and use mesh functions"_. Когда выключено, системе не доступны никакие функции.
- An **individual toggle for each function**, so you can expose only the capabilities you want.

The functions are grouped into a **Write** section (functions that change something or send data to your mesh) and a **Read** section (functions that only return information).

![App Functions screen with master and per-function toggles](../../assets/screenshots/app-functions_settings.png)

### Функции записи

| Функция                 | Что она делает                                                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **Отправить сообщение** | Отправляет текстовое сообщение контакту (личное сообщение) или каналу размером до 237 байт. |

### Функции записи

| Функция                           | Что она возвращает                                       |
| --------------------------------- | -------------------------------------------------------- |
| **Проверить состояние сети**      | Общее состояние сети.                    |
| **Получить список нод**           | Список узлов в твоей сети.               |
| **Получить информацию о канале**  | Информация о твоих каналах.              |
| **Получить состояние устройства** | Состояние подключенного радиоустройства. |
| **Получить детали ноды**          | Подробная информация о конкретной ноде.  |
| **Получить последние сообщения**  | Последние сообщения из твоих разговоров. |
| **Получить обзор непрочитанного** | Сводка непрочитанных сообщений.          |
| **Получить метрики сети**         | Телеметрия и метрики твоей сети.         |

## Приватность

> 🔒 **Приватность:** Функция **Отправить сообщение** позволяет помощнику отправлять сообщения в твоей сети от твоего имени. Включай только те функции, по которым ты доверяешь ассистенту. The read functions expose node, message, and metric data to the assistant — enable only what you're comfortable sharing. Each function has its own toggle, and the master toggle turns all of them off at once.

## Связанные темы

- [Messages & Channels](messages-and-channels) — sending messages directly in the app
- [Nodes](nodes) — the node list the read functions draw from
- [Node Metrics](node-metrics) — the telemetry behind Get Mesh Metrics

---

