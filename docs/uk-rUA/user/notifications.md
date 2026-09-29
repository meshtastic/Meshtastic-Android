---
title: Notifications
nav_order: 18
last_updated: 2026-09-28
description: What each Meshtastic notification is for, how to silence one kind without the others, and what you can do from a message notification or a watch.
aliases:
  - notifications
  - notification-channels
  - wear-os
  - smartwatch
---

# Notifications

Meshtastic posts a notification when something on the mesh needs you while the app is out of sight: a message, a new node, a low battery, or a notice from your node. Each kind is its own Android notification category, so you can silence one and keep the rest.

## Notification categories

To see the categories, tap **App Notifications** in the **Information** section of **Settings**. It opens Android's notification settings for Meshtastic, where the categories sit in three groups.

| Group        | Category                                                                             | Posted for                                                                      | Tapping it opens                |
| ------------ | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- | ------------------------------- |
| Повідомлення | Сповіщення особистих повідомлень                                                     | A message sent directly to you                                                  | The conversation                |
| Повідомлення | Сповіщення загального каналу                                                         | A message on one of your channels                                               | The channel                     |
| Повідомлення | Сповіщення про точки маршруту                                                        | A waypoint shared on the mesh, or a geofence crossing                           | The waypoint on the map         |
| Повідомлення | Сповіщення про тривоги                                                               | A critical alert from a node                                                    | The conversation                |
| Mesh         | Сповіщення про нові вузли                                                            | A node heard for the first time                                                 | The node's details              |
| Mesh         | Mesh invitation notifications                                                        | An invitation to join a nearby mesh                                             | Local Mesh Discovery            |
| Mesh         | Сповіщення про низький рівень заряду акумулятора (улюблені вузли) | A favorite node's battery running low                                           | The node's details              |
| Device       | Service notifications                                                                | The connection to your node while the app runs in the background                | The app                         |
| Device       | Low battery notifications                                                            | Your node's battery running low                                                 | The node's details              |
| Device       | Radio notifications                                                                  | Notices from your node, such as key verification requests and security warnings | The app                         |
| Device       | Update and connection notifications                                                  | A firmware update for your node, or a problem reconnecting to it                | Firmware update, or Connections |

By default, direct messages, alerts and radio notices pop up on screen, mesh invitations and the service notification arrive without a sound, and the rest make a sound. Android keeps whatever you change on a category, and the app cannot change it back.

Alert notifications play an alarm sound. To let them through Do Not Disturb as well, open the category and allow it to override Do Not Disturb, which is the step [Getting Started](onboarding#critical-alerts-permission) offers during setup.

The service notification stays in the shade while Meshtastic is connected in the background. It shows the connection state, and also shows progress while a firmware update or a preset scan runs.

> ℹ️ **Note:** The Desktop app has no notification categories. It switches notifications from its own settings instead, described in [Notification Preferences](desktop#notification-preferences).

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

## Related Topics

- [Messages & Channels](messages-and-channels) — muting conversations and nodes, bubbles, and message states
- [Getting Started](onboarding) — the notification and critical alert steps of setup
- [Firmware Updates](firmware) — the screen an update notification opens
- [Local Mesh Discovery](discovery) — what a mesh invitation offers
