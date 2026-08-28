---
title: 訊息與頻道
parent: 使用者指南
nav_order: 3
last_updated: 2026-08-27
description: Send and receive messages, manage channels, configure encryption, search conversations, and use quick chat, reactions, and message actions.
aliases:
  - 頻道
  - direct-messages
  - messaging
  - conversations
---

# 訊息與頻道

Meshtastic 支援兩種通訊模式：頻道廣播與私訊。

## 頻道

頻道是共享的通訊群組。 所有設定相同頻道金鑰的節點均可在該頻道上讀取與傳送訊息。

### 預設頻道

每台 Meshtastic 裝置均內建預設的 LongFast 頻道。 此為未加密頻道，供一般 mesh 網路通訊使用。

### 頻道安全性

頻道支援多種加密等級：

| 圖示 | 安全等級            | 描述說明                                           |
| -- | --------------- | ---------------------------------------------- |
| 🔒 | PSK（256 位元 AES） | 使用強力預共享金鑰進行完整加密。 僅持有相符金鑰的節點可讀取訊息。              |
| 🔐 | PSK（128 位元 AES） | 使用較短的金鑰進行加密。 適用於大多數情境，但敏感資料建議使用 256 位元加密。      |
| 🔓 | 預設／開放           | 使用眾所周知的預設金鑰。 使用相同預設值的任何 Meshtastic 裝置均可讀取這些訊息。 |
| ⚠️ | 不安全 + 位置        | 開放頻道，同時廣播您的 GPS 位置。 在公開 mesh 網路中使用時請謹慎。        |

> 🔒 **Security:** Always configure a unique PSK for private communications. 預設頻道刻意設計為開放，以便新使用者能探索 mesh 網路 — 但對於任何敏感內容，請另行建立獨立的加密頻道。

### 新增頻道

1. 前往「設定 → 頻道」。
2. Tap the **+** button to add a channel, or import one by scanning a channel QR code.
3. 設定頻道名稱與加密金鑰。設定頻道名稱與加密金鑰。
4. 將頻道網址或 QR Code 分享給需要加入的人。

點選頻道可查看其詳細資訊與分享選項。

## 私訊

私訊（DM）是兩個特定節點之間的點對點加密通訊。

### 傳送私訊

1. 開啟「訊息」頁籤。
2. 從聯絡人清單中選取節點，或在節點清單中點選節點。
3. 輸入訊息後點選「傳送」。

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

### 訊息狀態

A status label appears under **your own** outgoing messages only (incoming messages from others show no status label):

| 狀態                                  | 含義                                                                                                                                                                                                                                        |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sending…                            | Queued or already handed to the radio, not yet resolved either way. Both stages share this text, but the icon and colour change as it progresses — a yellow upload cloud while queued, a blue arrow once the radio has it |
| Delivered to recipient              | The strongest confirmation for a direct message — an acknowledgment came back                                                                                                                                                             |
| 已傳送至 Mesh                           | For a channel broadcast, the message reached the mesh (broadcasts have no per-recipient ack)                                                                                                                           |
| Relayed, not confirmed by recipient | For a direct message, shown in a warning color — the message was relayed but no acknowledgment has come back yet                                                                                                                          |
| 透過 SF++ 鏈路由…                        | Being routed/buffered by the Store & Forward Plus Plus chain                                                                                                                                                          |
| 已在 SF++ 鏈上確認                        | Confirmed delivered via the SF++ chain                                                                                                                                                                                                    |
| 錯誤                                  | Delivery failed — tap the status for the specific reason (see Delivery Errors below)                                                                                                                                   |

### 傳遞錯誤

當訊息傳遞失敗時，錯誤指示器將顯示問題原因：

| 錯誤                               | 含義                                                                                                                                                                            | 處理方式                                                                                                                                  |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| 無路由                              | 無法找到通往目標節點的路徑                                                                                                                                                                 | 收件者可能已離線或超出 mesh 網路範圍。 請稍後再試，或靠近對方後重新傳送。                                                                                              |
| No radio interface               | 無可用的無線電介面進行傳送                                                                                                                                                                 | Check that your radio is connected and available.                                                                     |
| Failed to deliver to mesh        | Retries exhausted. The same label covers three underlying causes — a relay refusing (NAK), a plain timeout, and running out of retransmits | Move closer, improve signal, or wait for conditions to improve. Tap the error for the specific cause. |
| Rate limited                     | The mesh is throttling you for sending too fast                                                                                                                               | Wait before sending again.                                                                                            |
| Not authorized                   | The destination refused the request                                                                                                                                           | Check you have the right channel and keys for that node.                                                              |
| Recipient needs your key         | Direct-message encryption could not complete because the other node does not have your public key yet                                                                         | Exchange node info — the key travels with it. Common on a first DM to a new contact.                  |
| Recipient key unavailable        | You do not have the recipient's public key                                                                                                                                    | Wait for their node info to arrive, or ask them to broadcast it.                                                      |
| Could not send encrypted message | Encryption failed for this direct message                                                                                                                                     | Verify both nodes have exchanged keys and are on compatible firmware.                                                 |
| Admin session expired            | A remote-admin session timed out                                                                                                                                              | Reopen the remote node's settings to start a new session.                                                             |
| Admin key not authorized         | The target node does not accept your admin key                                                                                                                                | Verify the admin key matches on both nodes.                                                                           |
| Channel/key mismatch             | Destination channel/key does not match                                                                                                                                        | Verify both nodes share the same channel and PSK.                                                                     |
| Message is too large to send     | 訊息超過最大承載大小                                                                                                                                                                    | Shorten the message and try again.                                                                                    |
| No app response                  | App or plugin did not respond to the request                                                                                                                                  | Retry or check the destination app or module state.                                                                   |
| Duty cycle limit                 | 已達地區無線電佔用時間上限                                                                                                                                                                 | Wait for the duty cycle window to reset.                                                                              |
| Invalid request                  | Malformed or invalid request                                                                                                                                                  | Retry after updating or restarting the app if this persists.                                                          |

> 💡 提示：大多數傳遞錯誤會自動解決。 若節點間歇性可到達，mesh 網路將自動重試。 若持續出現「無路由」錯誤，請確認中間的路由器節點是否在線。

## 訊息功能

### 快速聊天

預先設定的訊息，可快速進行通訊：

- 透過訊息輸入區的快速聊天按鈕開啟
- 從內建短語或自訂訊息中選取
- 在「設定 → 快速聊天」中自訂快速聊天訊息
- 適用於不便打字的情況（戴手套、螢幕過小、緊急狀況）

![Quick chat option](../../assets/screenshots/messages_quick_chat.png)

Each quick chat entry has a short **Name** (the button label), the **Message** it inserts, and an **Instantly send** toggle — when enabled, tapping the button sends the message immediately instead of placing it in the input field for editing:

![New quick chat dialog with name, message, and instantly-send toggle](../../assets/screenshots/messages_edit_quick_chat.png)

頻道清單會顯示每個頻道及其最新訊息預覽。

### Searching Messages

You can search the full history of any conversation directly from the chat screen:

1. Open a conversation (a channel or a direct message).
2. Tap the **search icon** in the top bar.
3. Type into the **Search messages…** field. The search runs as you type, across all stored messages in that conversation.
4. Use the **N / M** result counter and the **previous / next arrows** to jump between matches, which are highlighted in the conversation.

![Message search bar with result counter and previous/next arrows](../../assets/screenshots/messages_search_bar.png)

> 💡 **Tip:** Search is full-text and stays within the conversation you opened it from — it doesn't search across other channels or contacts. It matches against the messages already stored on your device, so it works fully offline.

### 訊息泡泡

訊息以對話泡泡的形式顯示 — 已傳送的訊息在右側，收到的訊息在左側。 每個泡泡顯示傳送者、時間戳記及傳遞狀態。 含有回覆的訊息，會在回覆內容上方顯示原始訊息的引用預覽。

### Text Formatting

Messages support lightweight inline **Markdown**. Received messages render the styling with the syntax characters removed:

| 類型            | Syntax                         | Renders as           |
| ------------- | ------------------------------ | -------------------- |
| Bold          | `**bold**`                     | **bold**             |
| Italic        | `*italic*`                     | _italic_             |
| Strikethrough | `~~strike~~`                   | ~~strike~~           |
| Inline code   | `` `code` ``                   | monospace `code`     |
| Link          | `[label](https://example.com)` | a tappable **label** |

When composing, focus the message field and type at least three characters to reveal a **formatting toolbar** below the input. Select text and tap a style to wrap it (tap again to remove it); with no selection, a style inserts an empty pair with the cursor between the markers. The link button opens a dialog to enter a URL. As you type, the draft styles live in the field while the underlying text keeps its Markdown characters.

> 💡 **Tip:** Formatting is carried as literal characters on the mesh — the same bytes iOS sends. Clients that don't support Markdown (older apps, plain firmware clients) will show the raw `**`/`~~` characters. URLs, email addresses, and phone numbers are still auto-linked whether or not you use Markdown.

### Mentions

Type `@` while composing to mention a node — a picker suggests matching contacts as you type. In a received message, a mention appears as a highlighted chip showing the node's name; tap it to jump straight to that node's detail page.

### 訊息回應

以表情符號對訊息作出回應：

- **Double-tap** a message — or long-press it — to raise a quick reaction bar above the bubble
- Tap an emoji in the bar to send it; tap **more** to open the full picker, or anywhere outside
  the bar to dismiss it without sending
- 訊息回應顯示於訊息泡泡下方
- 多位使用者可對同一則訊息作出回應
- 可對自己或他人的訊息作出回應

> ℹ️ **Note:** Opening the bar sends nothing. A reaction is a real mesh packet, so it only goes
> out when you pick an emoji.

![Emoji reaction badges displayed beneath a message](../../assets/screenshots/messages_reaction.png)

> 💡 提示：訊息回應非常輕量 — 相較於完整文字訊息，佔用極少的 mesh 網路頻寬。

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

### 訊息動作

Long-press or double-tap a message to open the quick reaction bar, then tap **More** (the
overflow icon on that bar) to reach:

- 複製 — 將訊息文字複製至剪貼簿
- 回覆 — 在回覆中引用該訊息
- 回應 — 新增表情符號回應
- **Translate** — translate a received message into your device language and toggle between the original and translated text (Google Play build only; uses on-device translation)
- 刪除 — 移除您傳送的訊息（僅限本機刪除）

### 訊息優先順序

The app sends every message you compose at the same, default priority — there is no
emergency or alert tier to choose, and nothing in the app raises a direct message above a
channel broadcast. Any prioritising between them happens in firmware, not here. (The app
does mark some of its own internal traffic, such as admin and traceroute packets, as
reliable or background, but that is not something you control from the message composer.)

### 訊息限制

- **Maximum length:** 200 bytes (approximately 200 characters for ASCII text)
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions) may arrive slightly longer
- 速率限制：mesh 網路會執行無線電佔用時間公平性管制；大量訊息可能會被節流
- 傳遞：若未收到確認回應，訊息將自動重試

## 最佳實踐

- 群組協調請使用頻道
- 個人私下通訊請使用私訊
- 訊息請盡量簡短 — mesh 網路頻寬有限
- 敏感通訊請設定加密

## 相關主題

- 〔節點〕(nodes) — 點選節點以開始傳送私訊
- 〔設定——無線電與使用者〕(settings-radio-user) — 設定頻道加密與預設值
- 〔MQTT〕(mqtt) — 將頻道訊息橋接至網際網路
- [Channel configuration](https://meshtastic.org/docs/configuration/radio/channels) — detailed channel settings on meshtastic.org

---

