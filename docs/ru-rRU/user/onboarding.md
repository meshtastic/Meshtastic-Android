---
title: Начало работы
parent: Руководство пользователя
nav_order: 1
last_updated: 2026-08-29
description: Настройка при первом запуске — разрешения, процесс знакомства с приложением и следующие шаги после подключения твоей радиостанции.
aliases:
  - first-launch
  - setup
  - intro
---

# Начало работы

This page covers the first-launch flow of the Meshtastic Android app, what each permission is for, and how to revisit them later.

## Первый запуск

When you open the app for the first time, the app guides you through an introductory flow that configures essential permissions and settings. Complete each step in order or skip it — nothing here is a one-time offer. Every permission can be reviewed and granted later from **Settings → Permissions** inside the app.

### Экран приветствия

The welcome screen introduces Meshtastic with three feature rows:

|                               |                                                                                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **Stay Connected Anywhere**   | Общайтесь вне сети со своими друзьями и сообществом без использования сотовой связи.                     |
| **Create Your Own Networks**  | Легко создать частные сети для защищённой и надежной связи в удаленных районах.                          |
| **Track and Share Locations** | Делитесь своим местоположением в режиме реального времени и поддерживайте работу группы с функциями GPS. |

Tap **Get started** to proceed through the setup flow.

![Экран приветствия](../../assets/screenshots/onboarding_welcome.png)

## Разрешения

Приложение запрашивает несколько разрешений во время настройки. Каждое из них служит определенной цели, и некоторые необходимы для основной функциональности.

### Разрешения Bluetooth

Bluetooth является основным методом соединения между телефоном и радиостанцией Meshtastic:

- **Сканирование Bluetooth** — обнаружение ближайших радиостанций Meshtastic
- **Подключение по Bluetooth** — установка и поддержание соединения с сопряжёнными радиоустройствами

Предоставь оба разрешения при запросе. Без Bluetooth тебе придется использовать USB или TCP соединения.

### Разрешение на доступ к местоположению

> ⚠️ **Is location required for Bluetooth?** **Android 11 and older** show one location step, on the Bluetooth screen, rather than two — those releases treat a Bluetooth scan as a location capability, so the app asks for Location instead of "Nearby devices". Asking twice would push you toward the point where Android stops offering the dialog at all (a second denial on Android 11; the "Don't ask again" checkbox on Android 10 and older). On **Android 12 and newer** the two are separate: "Nearby devices" is declared `neverForLocation`, and declining Location does not stop you finding or connecting to a radio.

Meshtastic также использует местоположение для:

- Показ вашего положения на карте
- Вычисление расстояний до других нод
- Обмен GPS-координатами с другими участниками сети (если включено)

Grant **"While using the app"**. The app does not request background location — `ACCESS_BACKGROUND_LOCATION` is not in its manifest — so Android will not offer an "Always" option, and position updates happen while the app is in the foreground or running its foreground service.

Declining leaves the rest of the app working: on Android 12 and newer, Bluetooth is unaffected and only the map position and position sharing are disabled. On Android 11 and older, Bluetooth scanning also stops, because that is the permission Android gates it behind — and system **Location Services** must also be switched on for a scan to return anything.

### Разрешение на уведомления

Уведомления оповещают тебя о:

- Входящие сообщения из каналов и личных сообщений
- Новые ноды, присоединившиеся к сети
- Низкий заряд на удаленной ноде

> 💡 **Совет:** Ты можешь тонко настроить уведомления позже в настройках системы — приложение создает отдельный канал уведомлений по категориям (плюс несколько внутренних, как фоновая служба), поэтому ты можешь включить или заглушить их по отдельности.

### Разрешение на критические уведомления

Critical alerts are high-priority notifications that break through Do Not Disturb — for emergency mesh alerts and urgent messages.

This step is not a runtime permission prompt. There is no grant/deny dialog: the button opens the Android system settings page for the app's **Alerts** notification channel, where you turn the breakthrough behavior on yourself. You can **skip** it, and reach the same page later from Android notification settings.

### Reviewing permissions later

**Settings → Permissions** summarizes where every runtime permission stands. It covers five: **Nearby devices** (Bluetooth), **Location**, **Notifications**, **Camera** (scanning channel and contact QR codes) and **Local network** (finding radios over Wi-Fi by mDNS) — the last two are never asked for during setup, only when a feature first needs them. It reads _All allowed_ when no permission needs attention; the row names the count and the Permissions screen opens automatically when something does. Tap the row to see the full list at any time:

| Состояние                                   | What tapping the row does                                                                    |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Allowed**                                 | Opens the system page, so you can review or revoke it                                        |
| **Not asked yet**                           | Requests it                                                                                  |
| **Denied — tap to allow**                   | Explains what the permission is for, then asks again if you agree                            |
| **Blocked — tap to open system settings**   | Android will no longer show its dialog, so this opens the page where you can turn it back on |
| **Not required on this version of Android** | Nothing — the permission does not exist on your device                                       |

This matters most for notifications. If you decline them during setup, this row is the way back: Android stops showing the dialog once you have declined firmly (a second denial), at which point this row switches to **Blocked** and sends you to the system settings page instead. The notification prompt exists only on Android 13 and newer — on older versions notifications are on by default and managed from Android's own settings.

## После настройки

After you grant permissions, the app opens the main interface. Ваше первое действие должно заключаться в подключении к радиостанции Meshtastic — см. [Подключения](connections) для подробных инструкций.

> 💡 **Tip:** If you skipped any permissions during setup, open **Settings → Permissions** in the app. Every runtime permission is listed there with its current state and a way back to it — including notifications, which the system will not prompt for a second time on its own.

Features also ask in context. Tapping **Scan** on the Connections screen with Bluetooth permission missing explains what it is for and offers to request it; once Android stops prompting, the same control opens the system settings page instead of doing nothing.

Новичок в Meshtastic? Руководство [по началу работы](https://meshtastic.org/docs/getting-started) на meshtastic.org охватывает выбор оборудования, начальную настройку радиостанции и первую установку сети.

## Связанные темы

- [Connections](connections) — pair your first radio
- [Сообщения и каналы](messages-and-channels) — отправь  своё первое сообщение
- [Nodes](nodes) — see who else is on your mesh
- [Карта и контрольные точки](map-and-waypoints) — просмотр позиций нод
- [Settings — Radio & User](settings-radio-user) — configure your radio and user profile
