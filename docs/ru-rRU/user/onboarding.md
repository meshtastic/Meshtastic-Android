---
title: Начало работы
parent: Руководство пользователя
nav_order: 1
last_updated: 2026-05-13
description: Настройка при первом запуске — разрешения, процесс знакомства с приложением и следующие шаги после подключения твоей радиостанции.
aliases:
  - first-launch
  - setup
  - intro
---

# Начало работы

Добро пожаловать в Meshtastic! Это руководство проведет тебя через начальную настройку приложения Meshtastic для Android.

## Первый запуск

Когда ты запускаешь приложение впервые, то будет предложено пройти через начальный процесс, который поможет настроить основные разрешения и параметры. Каждый шаг нужно выполнить по порядку, или же можешь пропустить его и настроить разрешения позже в настройках Android.

### Экран приветствия

Экран приветствия представляет Meshtastic и его основные возможности:

- Автономная mesh-связь
- Не требуется мобильная связь или интернет
- Сквозное шифрование сообщений

Нажми **Начать** для перехода к настройке.

![Экран приветствия](../../assets/screenshots/onboarding_welcome.png)

## Разрешения

Приложение запрашивает несколько разрешений во время настройки. Каждое из них служит определенной цели, и некоторые необходимы для основной функциональности.

### Разрешения Bluetooth

Bluetooth является основным методом соединения между телефоном и радиостанцией Meshtastic:

- **Сканирование Bluetooth** — обнаружение ближайших радиостанций Meshtastic
- **Подключение по Bluetooth** — установка и поддержание соединения с сопряжёнными радиоустройствами

Предоставь оба разрешения при запросе. Без Bluetooth тебе придется использовать USB или TCP соединения.

### Разрешение на доступ к местоположению

> ⚠️ **Почему для Bluetooth требуется местоположение?** Android требует разрешение на определение местоположения для обнаружения устройств Bluetooth Low Energy поблизости. Это требование системы Android, а не специфический выбор Meshtastic.

Meshtastic also uses your location for:

- Showing your position on the mesh map
- Calculating distances to other nodes
- Sharing your GPS coordinates with other mesh members (if enabled)

Grant **"While using the app"** or **"Always"** depending on your preference:

- **While using the app** — position updates only when the app is open
- **Always** — enables background position updates for always-on mesh presence

If denied, Bluetooth scanning will not function and your node will not report a position.

### Notifications Permission

Notifications alert you to:

- Incoming messages from channels and direct messages
- Connection status changes (connected, disconnected, reconnecting)
- Firmware update availability

> 💡 **Tip:** You can fine-tune notification preferences later in Android system settings. The app creates separate notification channels for messages, connection events, and background service status.

### Critical Alerts Permission

On supported devices, the app may request permission for critical alerts:

- These are high-priority notifications that can break through Do Not Disturb mode
- Useful for emergency mesh alerts or urgent messages
- You can **skip** this step if you don't need breakthrough notifications
- Configure or revoke later in Android notification settings

## After Setup

Once permissions are granted, the app transitions to the main interface. Your first action should be connecting to a Meshtastic radio — see [Connections](connections) for detailed instructions.

> 💡 **Tip:** If you skipped any permissions during setup, you can grant them later through **Android Settings → Apps → Meshtastic → Permissions**. The app will prompt you again if a missing permission blocks a feature you try to use.

## What's Next?

Once connected to a radio, explore:

- [Connections](connections) — pair your first radio device
- [Messages & Channels](messages-and-channels) — send your first message
- [Nodes](nodes) — see who's on your mesh
- [Map & Waypoints](map-and-waypoints) — view node positions
- [Settings](settings-radio-user) — configure your radio and user profile

New to Meshtastic? The [getting started guide](https://meshtastic.org/docs/getting-started) on meshtastic.org covers hardware selection, initial radio configuration, and your first mesh setup.

---
