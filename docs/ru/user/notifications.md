---
title: Уведомления
nav_order: 18
last_updated: 2026-11-04
description: Для чего предназначено каждое уведомление Meshtastic, как отключить одно из них не затрагивая других и что ты можешь сделать с помощью уведомления о сообщении или часов.
aliases:
  - notifications
  - notification-channels
  - wear-os
  - smartwatch
---

# Уведомления

Meshtastic posts a notification when something on the mesh needs you while the app is out of sight: a message, a new node, a low battery, or a notice from your node. Each kind is its own Android notification category, so you can silence one and keep the rest.

## Категории уведомлений

To see the categories, tap **App Notifications** in the **Information** section of **Settings**. It opens Android's notification settings for Meshtastic, where the categories sit in three groups.

| Группа     | Категория                                                               | Отправлено на                                                                   | Нажми для открытия                 |
| ---------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------- |
| Сообщения  | Уведомления о личных сообщениях                                         | Сообщение, отправленное напрямую                                                | Обсуждение                         |
| Сообщения  | Уведомления о сообщениях в общем чате                                   | Сообщение в одном из каналов                                                    | Канал                              |
| Сообщения  | Уведомления о реакции                                                   | An emoji reaction to a message in one of your conversations                     | Обсуждение                         |
| Сообщения  | Уведомления о путевых точках                                            | A waypoint shared on the mesh, or a geofence crossing                           | The waypoint on the map            |
| Сообщения  | Служебные уведомления                                                   | A critical alert from a node                                                    | Обсуждение                         |
| Меш        | Уведомления о новых нодах                                               | A node heard for the first time                                                 | The node's details                 |
| Меш        | Уведомления о приглашениях в сеть                                       | An invitation to join a nearby mesh                                             | Локальное обнаружение сети         |
| Меш        | Уведомления о низком заряде батареи (избранные ноды) | A favorite node's battery running low                                           | The node's details                 |
| Устройство | Служебные уведомления                                                   | The connection to your node while the app runs in the background                | Приложение                         |
| Устройство | Уведомление о низком уровне заряда                                      | Your node's battery running low                                                 | The node's details                 |
| Устройство | Уведомления радио                                                       | Notices from your node, such as key verification requests and security warnings | Приложение                         |
| Устройство | Update and connection notifications                                     | A firmware update for your node, or a problem reconnecting to it                | Обновление прошивки или Соединения |

By default, direct messages, alerts and notices from your node pop up on screen, mesh invitations and the service notification arrive without a sound, and the rest make a sound. Android keeps whatever you change on a category, and the app cannot change it back.

Alert notifications play an alarm sound. To let them through Do Not Disturb as well, open the category and allow it to override Do Not Disturb, which is the step [Getting Started](onboarding#critical-alerts-permission) offers during setup.

The service notification stays in the shade while Meshtastic is connected in the background. It shows the connection state, and also shows progress while a firmware update or a preset scan runs. On Android 16 and newer you can turn it into a [Live Update](connections#live-update) that shows your mesh or one node in the status bar and in this notification, with a **Stop Live Update** action to end it.

> ℹ️ **Примечание:** В десктопном приложении нет категорий уведомлений. It switches notifications from its own settings instead, described in [Notification Preferences](desktop#notification-preferences).

## When a message notifies you

One notification collects each conversation's recent messages. Whether a new message adds to it depends on where you are:

- **Reading that conversation:** nothing is posted, since the message is already on screen.
- **In the app, on another screen:** the notification updates without a sound.
- **Anywhere else:** the notification updates and alerts you as its category is set to.

Muting a conversation or a channel stops its notifications, except for a message that mentions you. Muting a node stops its notifications even when it mentions you. How to mute is in [Messages & Channels](messages-and-channels). A critical alert still alerts you while you are reading its conversation, and only muting silences it.

## Acting from a notification

A message notification has three actions:

- **Reply** sends a message to that conversation without opening the app. The notification then updates to show your reply.
- **Mark as read** clears the conversation's unread count and dismisses the notification.
- **👍** reacts to the newest message with a thumbs-up.

On Android 11 and newer, a message notification can also open as a floating bubble, described in [Conversation Bubbles](messages-and-channels#conversation-bubbles).

## On a watch

A Wear OS watch paired with your phone shows every Meshtastic notification except the service notification, which Android keeps on the phone because it never goes away. A message notification keeps its actions on the watch, and **Reply** there can offer suggested replies. The watch's own notification settings decide whether Meshtastic appears on it at all.

## Связанные темы

- [Messages & Channels](messages-and-channels) — muting conversations and nodes, bubbles, and message states
- [Getting Started](onboarding) — the notification and critical alert steps of setup
- [Firmware Updates](firmware) — the screen an update notification opens
- [Local Mesh Discovery](discovery) — what a mesh invitation offers
