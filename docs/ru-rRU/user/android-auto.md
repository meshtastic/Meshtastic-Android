---
title: Android Auto
parent: Руководство пользователя
nav_order: 18
last_updated: 2026-07-07
description: Используй Meshtastic без рук на головном устройстве Android Auto — читай сообщения вслух, отвечай голосом и проверяй ноды и состояние сети, пока ты за рулем.
aliases:
  - android-auto
  - car
  - head-unit
  - auto
---

# Android Auto

Meshtastic интегрируется с Android Auto, так что ты можешь оставаться на связи со своей сетью, пока едешь, не отрывая рук от руля и глаз от дороги.

> ⚠️ **Примечание:** поддержка Android Auto доступна только на **Android-сборках от Google**. Она не включена в сборку F-Droid и недоступна на ПК или iOS.

> ℹ️ **What ships today:** The Google Play build provides **notification-only** car messaging — incoming messages are announced on the head unit and you reply through its notification controls. The full tabbed **Messages / Nodes / Status** experience described below is a beta built on the Android Car App Library (Google's templated car UI is currently restricted to Closed/Internal Play tracks), so it appears only in builds compiled with `-PenableCarTemplates=true`. The rest of this page documents that beta experience.

## Обзор

When your phone is connected to an Android Auto head unit (or the Desktop Head Unit emulator used for development), the beta build presents Meshtastic as a messaging app built with the Android Car App Library, with a tabbed Home screen optimized for driving-safe, glanceable use:

- **Сообщения** — недавние разговоры с возможностью чтения и ответов без рук.
- **Узлы** — список нод сети с подробным просмотром каждой ноды.
- **Статус** — текущий статус подключения и сети.

Приложение для машины само по себе не добавляет новое соединение. Оно использует уже существующее соединение, ноду и состояние сообщений в приложении Meshtastic, так что отображает то, к чему твой телефон уже подключен.

> ⚠️ **Примечание:** твой телефон должен быть подключен к радиостанции Meshtastic, чтобы автомобильное приложение показывало данные в реальном времени. Если приложение отключено, экран автомобиля показывает отключенное состояние.

## Сообщения

Вкладка «Сообщения» показывает твои недавние разговоры. За рулём ты можешь:

- **Прослушивать сообщения вслух**, чтобы не приходилось смотреть на экран.
- **Отвечай голосом или текстом** с помощью кнопки ответа на своей головной панели, диктуя свой ответ без рук.

## Ноды

Вкладка «Ноды» показывает список нод вашей сетки в удобном для машины формате. Выбор ноды открывает представление с подробной информацией об этой ноде. Смотри [Ноды](nodes), чтобы полностью понять информацию, показанную здесь.

## Статус

Вкладка «Статус» позволяет одним взглядом увидеть твоё текущее соединение и состояние сети — удобно, чтобы проверить, что ты всё ещё подключен к радиостанции, не открывая телефон.

## Связанные темы

- [Сообщения и Каналы](messages-and-channels) — все функции обмена сообщениями на твоём телефоне
- [Ноды](nodes) — подробный список нод и информация о каждой ноде
- [Подключения](connections) — как приложение подключается к твоей радиостанции

---

