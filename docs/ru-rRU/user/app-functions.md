---
title: Функции приложения
parent: Руководство пользователя
nav_order: 19
last_updated: 2026-08-28
description: Предоставьте возможности mesh системе Android и помощникам с ИИ на устройстве (например, Gemini), чтобы они могли выполнять mesh-воркфлоу без открытия приложения.
aliases:
  - app-functions
  - system-ai
  - gemini
  - assistant
---

# Функции приложения

Функции приложения предоставляют возможности Meshtastic системе Android и встроенным AI-помощникам (таким как Gemini) через API функций приложения Android. С их включением помощник может находить и запускать сетевые рабочие процессы за тебя — например, отправлять сообщение или проверять статус сети — не утруждая тебя открытием приложения.

> ℹ️ **Note:** App Functions are available on **Google-flavor Android builds only**.
>
> This is separate from the in-app **Chirpy** assistant. Функции приложения позволяют _системному_ AI-помощнику действовать с вашей сетью; Chirpy — это голосовой помощник прямо внутри приложения Meshtastic.

## Включение функций приложения

Функции приложения управляются через **Настройки → Системный ИИ** (на экране в приложении указано "Системный ИИ"). Экран содержит:

- **Главный переключатель** с надписью **"Разрешить доступ ИИ"** и подзаголовком _"Позволь системным ИИ-ассистентам (например, Gemini) обнаруживать и использовать функции сети"_. Когда выключено, системе не доступны никакие функции.
- **Отдельный переключатель для каждой функции**, чтобы включать только те возможности, которые хочешь.

Функции разделены на секцию **Запись** (функции, которые что-то меняют или отправляют данные в сеть) и **Чтение** (только возвращают информацию).

![Экран функций приложения с переключателями для всех функций и отдельных функций](../../assets/screenshots/app-functions_settings.png)

### Функции записи

| Функция                 | Что она делает                                                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **Отправить сообщение** | Отправляет текстовое сообщение контакту (личное сообщение) или каналу размером до 237 байт. |

### Функции записи

| Функция                          | Что она возвращает                                          |
| -------------------------------- | ----------------------------------------------------------- |
| **Получить состояние сети**      | Общее состояние сети.                       |
| **Получить список нод**          | Список узлов в твоей сети.                  |
| **Получить информацию о канале** | Информация о твоих каналах.                 |
| **Get Device Status**            | Status of your connected radio.             |
| **Get Node Details**             | Detailed information about a specific node. |
| **Get Recent Messages**          | Recent messages from your conversations.    |
| **Get Unread Summary**           | A summary of unread messages.               |
| **Get Mesh Metrics**             | Telemetry and metrics from your mesh.       |

## Privacy

> 🔒 **Privacy:** The **Send Message** function lets an assistant send messages to your mesh on your behalf. Only enable functions you trust the assistant to use. The read functions expose node, message, and metric data to the assistant — enable only what you're comfortable sharing. Each function has its own toggle, and the master toggle turns all of them off at once.

## Related Topics

- [Messages & Channels](messages-and-channels) — sending messages directly in the app
- [Nodes](nodes) — the node list the read functions draw from
- [Node Metrics](node-metrics) — the telemetry behind Get Mesh Metrics

---

