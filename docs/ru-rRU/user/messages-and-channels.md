---
title: Сообщения и каналы
parent: Руководство пользователя
nav_order: 3
last_updated: 2026-08-27
description: Отправляйте и получайте сообщения, управляйте каналами, настраивайте шифрование, ищите по перепискам, а также используйте быстрый чат, реакции и действия с сообщениями.
aliases:
  - channels
  - direct-messages
  - messaging
  - conversations
---

# Сообщения и каналы

Meshtastic поддерживает два режима связи: **вещание по каналам** и **личные сообщения**.

## Каналы

Каналы - это общие группы для связи. Все узлы, настроенные с одинаковым ключом канала, могут читать и отправлять сообщения в этом канале.

### Канал по умолчанию

Каждое устройство Meshtastic имеет канал **LongFast** по умолчанию. Это незашифрованный канал, используемый для общей связи в ячеистой сети.

### Безопасность канала

Каналы поддерживают несколько уровней шифрования:

| Иконка | Уровень безопасности                 | Описание                                                                                                                                                                  |
| ------ | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 🔒     | PSK (256-bit AES) | Полностью зашифрован надёжным предварительно согласованным ключом. Только узлы с соответствующим ключом могут читать сообщения.           |
| 🔐     | PSK (128-bit AES) | Зашифрован более коротким ключом. Безопасен для большинства задач, но 256-битное шифрование предпочтительнее для конфиденциальных данных. |
| 🔓     | По умолчанию / Открытый              | Использует общеизвестный ключ по умолчанию. **Любое устройство Meshtastic** с той же пред установкой может читать эти сообщения.          |
| ⚠️     | Незащищённый + Местоположение        | Открытый канал, который также передаёт ваше GPS-местоположение. Используйте с осторожностью в общественных ячеистых сетях.                |

> 🔒 **Security:** Always configure a unique PSK for private communications. Канал по умолчанию намеренно открыт, чтобы новые пользователи могли обнаружить mesh-сеть — но вам следует создать отдельный зашифрованный канал для любой конфиденциальной информации.

### Добавление канала

1. Перейдите в **Настройки → Каналы**.
2. Tap the **+** button to add a channel, or import one by scanning a channel QR code.
3. Настройте имя канала и ключ шифрования.
4. Поделитесь URL-адресом/QR-кодом канала с теми, кому нужен доступ.

При нажатии на канал отображаются его сведения и опции для отправки приглашения.

## Личные сообщения

Личные сообщения (DMs) — это зашифрованная связь точка - к - точке между двумя конкретными узлами.

### Отправка личного сообщения

1. Откройте вкладку **Сообщения**.
2. Выберите ноду из списка контактов или нажмите на ноду в списке нод.
3. Введите сообщение и нажмите **"Отправить"**.

### Managing the Conversation List

The **Messages** tab lists your conversations. Each row carries what you need to triage it at a
glance, and the list itself is directly actionable:

- **Unsent drafts survive.** Type into a conversation and leave without sending, and the text is
  still there when you come back. The row shows it as `Draft: …` in place of the last message —
  an unsent draft is the thing the row is waiting on _you_ for.
- **Unread badge.** A count sits on the row until you open the conversation.
- **Swipe right to mute** (swipe again to unmute) and **swipe left to delete**. Deleting asks
  first; muting shows a snackbar with **Undo**.
- **Long-press to select** one or more conversations, then use the action bar to **Pin**,
  **Mark unread**, mute or delete them together. Pinned conversations carry a pin marker and rise
  to the top of **their own section**.
- **The list is split into Channels and Direct Messages**, each with a collapsible header and each
  sorted independently — so a pinned direct message rises within its own section, not above the
  Channels one.

### Conversation Bubbles

On Android 11 and later, a message notification can be opened as a floating **bubble** that
stays on top of whatever else you are doing. Tap the bubble icon on the notification to promote
a conversation; Android remembers the choice per conversation, and the system Bubbles settings
control whether they are offered at all.

### Состояние сообщения

Метка статуса отображается только **под твоими** исходящими сообщениями (входящие сообщения от других не имеют метки статуса):

| Состояние                             | Значение                                                                                                                                                                                                                                  |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Отправка…                             | Queued or already handed to the radio, not yet resolved either way. Both stages share this text, but the icon and colour change as it progresses — a yellow upload cloud while queued, a blue arrow once the radio has it |
| Доставлено получателю                 | Самое надёжное подтверждение для личного сообщения — получено подтверждение о доставке                                                                                                                                                    |
| Отправлено в сеть                     | Для широковещательного сообщения в канале — сообщение достигло mesh-сети (у широковещательных сообщений нет подтверждений для каждого получателя)                                                                      |
| Передано, не подтверждено получателем | Для личного сообщения, отображается предупреждающим цветом — сообщение было ретранслировано, но подтверждение ещё не получено                                                                                                             |
| Маршрутизация по SF++ цепочке…        | Находится в процессе маршрутизации/буферизации в цепочке Store & Forward Plus Plus                                                                                                                                    |
| Подтверждено в цепочке SF++           | Подтверждена доставка через цепочку SF++                                                                                                                                                                                                  |
| Ошибки                                | Ошибка доставки — нажмите на статус, чтобы узнать конкретную причину (см. раздел «Ошибки доставки» ниже)                                                                                               |

### Ошибки доставки

Когда сообщение не удаётся доставить, индикатор ошибки показывает, что пошло не так:

| Ошибки                                                     | Значение                                                                                                                                                                      | Что делать                                                                                                                                    |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Нет маршрута                                               | Путь к целевой ноде отсутствует                                                                                                                                               | Получатель может быть не в сети или вне зоны действия mesh-сети. Повторите попытку позже или подойдите ближе. |
| Нет радиоинтерфейса                                        | Нет доступного радиоинтерфейса для отправки                                                                                                                                   | Проверь, подключено ли твоё радио и доступно ли оно.                                                                          |
| Не удалось доставить в сеть                                | Retries exhausted. The same label covers three underlying causes — a relay refusing (NAK), a plain timeout, and running out of retransmits | Move closer, improve signal, or wait for conditions to improve. Tap the error for the specific cause.         |
| Rate limited                                               | The mesh is throttling you for sending too fast                                                                                                                               | Wait before sending again.                                                                                                    |
| Not authorized                                             | The destination refused the request                                                                                                                                           | Check you have the right channel and keys for that node.                                                                      |
| Recipient needs your key                                   | Direct-message encryption could not complete because the other node does not have your public key yet                                                                         | Exchange node info — the key travels with it. Common on a first DM to a new contact.                          |
| Recipient key unavailable                                  | You do not have the recipient's public key                                                                                                                                    | Wait for their node info to arrive, or ask them to broadcast it.                                                              |
| Could not send encrypted message                           | Encryption failed for this direct message                                                                                                                                     | Verify both nodes have exchanged keys and are on compatible firmware.                                                         |
| Admin session expired                                      | A remote-admin session timed out                                                                                                                                              | Reopen the remote node's settings to start a new session.                                                                     |
| Admin key not authorized                                   | The target node does not accept your admin key                                                                                                                                | Verify the admin key matches on both nodes.                                                                                   |
| Несовпадение канала/ключа                                  | Канал/ключ назначения не совпадает                                                                                                                                            | Убедитесь, что обе ноды используют один и тот же канал и PSK.                                                                 |
| Слишком большое сообщение для отправки                     | Сообщение превышает максимальный размер полезной нагрузки                                                                                                                     | Сократи сообщение и повтори попытку.                                                                                          |
| Нет ответа приложения                                      | Приложение или плагин не ответили на запрос                                                                                                                                   | Повтори попытку или проверь состояние приложения или модуля назначения.                                                       |
| Ограничение рабочего цикла (Dity cycle) | Достигнут региональный лимит эфирного времени                                                                                                                                 | Дождись сброса окна рабочего цикла.                                                                                           |
| Неверный запрос                                            | Повреждённый или неверный запрос                                                                                                                                              | Если проблема сохраняется, повтори попытку после обновления или перезапуска приложения.                                       |

> 💡 **Совет:** Большинство ошибок доставки разрешаются сами собой. Если нода доступна с перебоями, mesh-сеть будет повторять попытки. При постоянных ошибках «Нет маршрута» проверьте, что промежуточные ноды-роутеры находятся в сети.

## Функции сообщений

### Быстрый чат

Заранее подготовленные сообщения для быстрой связи:

- Доступ через кнопку "Быстрый чат" в области ввода сообщения
- Выберите из встроенных фраз или собственных сообщений
- Настройте сообщения быстрого чата в **Настройки → Быстрый чат**
- Полезно, когда набирать текст неудобно (перчатки, маленький экран, срочность)

![Опция быстрого чата](../../assets/screenshots/messages_quick_chat.png)

Каждая запись быстрого чата имеет короткое **Имя** (надпись на кнопке), **Сообщение**, которое она вставляет, и переключатель **"Отправить сразу"** — когда он включён, нажатие кнопки немедленно отправляет сообщение, а не помещает его в поле ввода для редактирования:

![Диалог создания быстрого чата с именем, сообщением и переключателем мгновенной отправки](../../assets/screenshots/messages_edit_quick_chat.png)

Список каналов показывает каждый канал с предпросмотром последнего сообщения.

### Поиск сообщений

Ты можешь искать по всей истории любой переписки прямо на экране чата:

1. Откройте переписку (канал или личное сообщение).
2. Нажмите **значок поиска** в верхней панели.
3. Введите текст в поле **"Поиск сообщений…"**. Поиск выполняется по мере твоего ввода по всем сохранённым сообщениям в этой переписке.
4. Используйте счётчик результатов **N / M** и стрелки **вперёд / назад** для перехода между совпадениями, которые подсвечиваются в переписке.

![Панель поиска сообщений со счётчиком результатов и стрелками вперёд/назад](../../assets/screenshots/messages_search_bar.png)

> 💡 **Совет:** Поиск полнотекстовый и ограничивается перепиской, из которого ты его открыл — он не ищет по другим каналам или контактам. Он сопоставляет текст с сообщениями, уже сохранёнными на твоём устройстве, поэтому работает полностью офлайн.

### Пузырьки сообщения

Сообщения отображаются в виде пузырьков чата — отправленные справа, полученные слева. В каждом пузырьке отображаются отправитель, время и статус доставки. Сообщения с ответами содержат цитируемый предпросмотр исходного сообщения над ответом.

### Форматирование текста

Сообщения поддерживают облегчённую встроенную разметку Markdown. Полученные сообщения отображают оформление со скрытыми символами синтаксиса:

| Тип            | Синтаксис                      | Отображается как      |
| -------------- | ------------------------------ | --------------------- |
| Жирный         | `**жирный**`                   | **жирный**            |
| Курсив         | `*курсив*`                     | _курсив_              |
| Зачёркнутый    | `~~зачёркнутый~~`              | ~~зачёркнутый~~       |
| Встроенный код | `` `код` ``                    | моноширинный `код`    |
| Ссылка         | `[текст](https://example.com)` | нажимаемая **ссылка** |

При создании сообщения установите фокус на поле ввода и введите не менее трёх символов — под полем появится **панель форматирования**. Выделите текст и нажмите на стиль, чтобы применить его (повторное нажатие убирает форматирование); если текст не выделен, будет вставлена пустая пара символов разметки, а курсор окажется между ними. Кнопка вставки ссылки открывает диалог для ввода URL-адреса. В процессе твоего набора черновые стили отображаются в поле, но в самом тексте сохраняются символы Markdown.

> 💡 **Совет:** Форматирование передаётся по mesh-сети как есть — теми же байтами, что и iOS. Клиенты, не поддерживающие Markdown (старые приложения, простые клиенты на прошивке), отобразят сырые символы `**`/`~~`. URL-адреса, адреса электронной почты и номера телефонов всё равно автоматически становятся ссылками независимо от того, используете ли вы Markdown.

### Упоминания

Введите `@` при написании сообщения, чтобы упомянуть ноду — по мере твоего ввода будет появляться список подходящих контактов. В полученном сообщении упоминание отображается как выделенный элемент с именем ноды; нажмите на него, чтобы сразу перейти на страницу сведений о ноде.

### Реакции

Реагируйте на сообщения с помощью эмодзи:

- **Double-tap** a message — or long-press it — to raise a quick reaction bar above the bubble
- Tap an emoji in the bar to send it; tap **more** to open the full picker, or anywhere outside
  the bar to dismiss it without sending
- Реакции появляются под пузырьком сообщения
- Несколько пользователей могут отреагировать на одно и то же сообщение
- Реагируйте на свои сообщения или сообщения других

> ℹ️ **Note:** Opening the bar sends nothing. A reaction is a real mesh packet, so it only goes
> out when you pick an emoji.

![Значки реакций-эмодзи, отображаемые под сообщением](../../assets/screenshots/messages_reaction.png)

> 💡 **Совет:** Реакции очень лёгкие — они потребляют гораздо меньше трафика mesh-сети по сравнению с полными текстовыми сообщениями.

### Replying

**Swipe a message to the right** to reply to it — the composer opens with that message quoted.
Swiping past the reply threshold arms the action; releasing before it springs back with nothing sent.
Reply is also in the actions menu, reached by long-pressing and then tapping **More**.

### Day Separators

Messages are grouped by day. The separator above the first message of each day reads **Today**
or **Yesterday** for the two most recent days, and the date itself for older ones.

### Jump to Latest

Scrolling back through a conversation raises a jump-to-latest control. When messages arrive
while you are scrolled up, it names the most recent sender and adds a count of the other unread
messages. That count is messages, not people — five unread from one person reads as their name
**+4**.

### Действия с сообщениями

Long-press or double-tap a message to open the quick reaction bar, then tap **More** (the
overflow icon on that bar) to reach:

- **Копировать** — скопировать текст сообщения в буфер обмена
- **Ответить** — процитировать сообщение в твоём ответе
- **Реагировать** — добавить реакцию-эмодзи
- **Перевести** — перевести полученное сообщение на язык твоего устройства и переключаться между оригиналом и переводом (только в сборке Google Play; используется встроенный перевод)
- **Удалить** — удалить отправленное тобою сообщение (локальное удаление)

### Приоритет сообщений

The app sends every message you compose at the same, default priority — there is no
emergency or alert tier to choose, and nothing in the app raises a direct message above a
channel broadcast. Any prioritising between them happens in firmware, not here. (The app
does mark some of its own internal traffic, such as admin and traceroute packets, as
reliable or background, but that is not something you control from the message composer.)

### Ограничения сообщений

- **Максимальная длина**: 200 байт (примерно 200 символов для текста в ASCII)
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions) may arrive slightly longer
- **Ограничение скорости**: Mesh-сеть обеспечивает справедливое распределение эфирного времени; большой объем сообщений может быть ограничен
- **Доставка**: Сообщения автоматически повторяются при отсутствии подтверждения

## Рекомендации

- Используйте каналы для координации в группах
- Используйте личные сообщения для приватного общения один на один
- Пишите короткие сообщения — пропускная способность mesh-сети ограничена
- Настройте шифрование для конфиденциальной связи

## Связанные темы

- [Ноды](nodes) — нажмите на ноду, чтобы начать личный чат
- [Настройки — Радио и пользователь](settings-radio-user) — настройка шифрования каналов и пресетов
- [MQTT](mqtt) — мост для передачи сообщений канала в интернет
- [Конфигурация каналов](https://meshtastic.org/docs/configuration/radio/channels) — подробные параметры каналов на meshtastic.org

---

