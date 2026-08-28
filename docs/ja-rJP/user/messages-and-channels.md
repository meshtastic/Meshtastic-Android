---
title: メッセージとチャンネル
parent: User Guide
nav_order: 3
last_updated: 2026-08-27
description: メッセージの送受信、チャンネルの管理、暗号化の設定、会話の検索、クイックチャット・リアクション・メッセージ操作の使い方を説明します。
aliases:
  - channels
  - direct-messages
  - messaging
  - conversations
---

# メッセージとチャンネル

Meshtastic は、**チャンネルブロードキャスト**と**ダイレクトメッセージ**の 2 つの通信モードをサポートしています。

## チャンネル

チャンネルは、共有された通信グループです。 同じチャンネル鍵を設定したすべてのノードが、そのチャンネルでメッセージを読み書きできます。

### デフォルトチャンネル

すべての Meshtastic デバイスには、デフォルトの **LongFast** チャンネルが用意されています。 これは、一般的なメッシュ通信に使われる暗号化されていないチャンネルです。

### チャンネルのセキュリティ

チャンネルは複数の暗号化レベルに対応しています：

| アイコン | セキュリティレベル        | 説明                                                                     |
| ---- | ---------------- | ---------------------------------------------------------------------- |
| 🔒   | PSK（256 ビット AES） | 強力な事前共有鍵で完全に暗号化されます。 一致する鍵を持つノードだけがメッセージを読めます。                         |
| 🔐   | PSK（128 ビット AES） | より短い鍵で暗号化されます。 ほとんどの用途で安全ですが、機密性の高いデータには 256 ビットが推奨されます。               |
| 🔓   | デフォルト／オープン       | よく知られたデフォルトの鍵を使用します。 同じプリセットの**あらゆる Meshtastic デバイス**が、これらのメッセージを読めます。 |
| ⚠️   | 非セキュア＋位置情報       | GPS 位置情報も一斉送信するオープンチャンネルです。 公開メッシュでは注意して使用してください。                      |

> 🔒 **Security:** Always configure a unique PSK for private communications. デフォルトチャンネルは、新規ユーザーがメッシュを見つけられるよう意図的にオープンになっています。ただし、機密性の高い内容には別途、暗号化されたチャンネルを作成してください。

### チャンネルを追加する

1. 「**設定 → チャンネル**」に移動します。
2. Tap the **+** button to add a channel, or import one by scanning a channel QR code.
3. チャンネル名と暗号化鍵を設定します。
4. アクセスが必要な相手に、チャンネルの URL／QR コードを共有します。

チャンネルをタップすると、その詳細と共有オプションが表示されます。

## ダイレクトメッセージ

ダイレクトメッセージ（DM）は、特定の 2 つのノード間で行われる、ポイントツーポイントの暗号化通信です。

### ダイレクトメッセージを送信する

1. 「**メッセージ**」タブを開きます。
2. 連絡先リストからノードを選択するか、ノードリストでノードをタップします。
3. メッセージを入力し、「**送信**」をタップします。

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

### メッセージの状態

ステータスラベルは、**自分が**送信したメッセージの下にのみ表示されます（他のユーザーからの受信メッセージにはステータスラベルは表示されません）：

| 状態                   | 意味                                                                                                                                                                                                                                        |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 送信中…                 | Queued or already handed to the radio, not yet resolved either way. Both stages share this text, but the icon and colour change as it progresses — a yellow upload cloud while queued, a blue arrow once the radio has it |
| 受信者に配信済み             | ダイレクトメッセージで最も確実な確認です。受信確認が返ってきています                                                                                                                                                                                                        |
| メッシュに配信済み            | チャンネルブロードキャストでは、メッセージがメッシュに届いたことを示します（ブロードキャストには受信者ごとの確認応答はありません）                                                                                                                                                                         |
| 中継済み、受信者による確認なし      | ダイレクトメッセージでは警告色で表示されます。メッセージは中継されましたが、まだ受信確認が返ってきていません                                                                                                                                                                                    |
| SF++ チェーン経由でルーティング中… | Store & Forward Plus Plus チェーンによってルーティング／バッファリングされています                                                                                                                                                                |
| SF++ チェーンで確認済み       | SF++ チェーン経由で配信が確認されました                                                                                                                                                                                                                    |
| エラー                  | 配信に失敗しました。具体的な理由はステータスをタップして確認してください（下記の「配信エラー」を参照）                                                                                                                                                                                       |

### 配信エラー

メッセージの配信に失敗すると、エラーインジケーターが何が問題だったかを示します：

| エラー                      | 意味                                                                                                                                                                            | 対処方法                                                                                                                                  |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| ルートなし                    | 宛先ノードへの経路が存在しません                                                                                                                                                              | 受信者がオフラインか、メッシュの範囲外の可能性があります。 時間をおくか、距離を縮めてください。                                                                                      |
| No radio interface       | 送信に使える無線インターフェースがありません                                                                                                                                                        | Check that your radio is connected and available.                                                                     |
| メッシュへの配信に失敗しました          | Retries exhausted. The same label covers three underlying causes — a relay refusing (NAK), a plain timeout, and running out of retransmits | Move closer, improve signal, or wait for conditions to improve. Tap the error for the specific cause. |
| Rate limited             | The mesh is throttling you for sending too fast                                                                                                                               | Wait before sending again.                                                                                            |
| Not authorized           | The destination refused the request                                                                                                                                           | Check you have the right channel and keys for that node.                                                              |
| 受信者にあなたの鍵が必要です           | Direct-message encryption could not complete because the other node does not have your public key yet                                                                         | Exchange node info — the key travels with it. Common on a first DM to a new contact.                  |
| 受信者の鍵を利用できません            | You do not have the recipient's public key                                                                                                                                    | Wait for their node info to arrive, or ask them to broadcast it.                                                      |
| 暗号化メッセージを送信できませんでした      | Encryption failed for this direct message                                                                                                                                     | Verify both nodes have exchanged keys and are on compatible firmware.                                                 |
| Admin session expired    | A remote-admin session timed out                                                                                                                                              | Reopen the remote node's settings to start a new session.                                                             |
| Admin key not authorized | The target node does not accept your admin key                                                                                                                                | 両方のノードで管理者鍵が一致しているか確認してください。                                                                                                          |
| Channel/key mismatch     | Destination channel/key does not match                                                                                                                                        | Verify both nodes share the same channel and PSK.                                                                     |
| メッセージが大きすぎて送信できません       | メッセージが最大ペイロードサイズを超えています                                                                                                                                                       | Shorten the message and try again.                                                                                    |
| No app response          | App or plugin did not respond to the request                                                                                                                                  | Retry or check the destination app or module state.                                                                   |
| Duty cycle limit         | 地域ごとの電波利用時間の上限に達しました                                                                                                                                                          | Wait for the duty cycle window to reset.                                                                              |
| Invalid request          | Malformed or invalid request                                                                                                                                                  | Retry after updating or restarting the app if this persists.                                                          |

> 💡 **ヒント：** ほとんどの配信エラーは自然に解消します。 ノードに断続的に到達できる場合、メッシュは再試行します。 「ルートなし」エラーが続く場合は、中間のルーターノードがオンラインになっているか確認してください。

## メッセージの機能

### クイックチャット

すばやくやり取りするための、あらかじめ設定されたメッセージです：

- メッセージ入力欄のクイックチャットボタンから利用できます
- 組み込みの定型文またはカスタムメッセージから選べます
- クイックチャットのメッセージは「**設定 → クイックチャット**」でカスタマイズできます
- 入力が難しい場面（手袋の着用、小さな画面、急いでいるとき）に便利です

![クイックチャットのオプション](../../assets/screenshots/messages_quick_chat.png)

各クイックチャット項目には、短い**名前**（ボタンのラベル）、挿入される**メッセージ**、そして**即時送信**トグルがあります。有効にすると、ボタンをタップした時点で、メッセージが入力欄に置かれて編集できる状態になるのではなく、すぐに送信されます：

![名前・メッセージ・即時送信トグルを備えた新規クイックチャットのダイアログ](../../assets/screenshots/messages_edit_quick_chat.png)

チャンネルリストには、各チャンネルが最新メッセージのプレビューとともに表示されます。

### メッセージを検索する

チャット画面から、どの会話でもその全履歴を直接検索できます：

1. 会話（チャンネルまたはダイレクトメッセージ）を開きます。
2. 上部バーの**検索アイコン**をタップします。
3. 「**メッセージを検索…**」欄に入力します。 検索は入力に応じて、その会話に保存されているすべてのメッセージを対象に実行されます。
4. **N／M** の結果カウンターと**前へ／次への矢印**を使って、一致箇所間を移動できます。一致箇所は会話内でハイライト表示されます。

![結果カウンターと前へ／次への矢印を備えたメッセージ検索バー](../../assets/screenshots/messages_search_bar.png)

> 💡 **ヒント：** 検索は全文検索で、開いた会話の中だけを対象とします。他のチャンネルや連絡先をまたいで検索することはありません。 デバイスにすでに保存されているメッセージを対象に照合するため、完全にオフラインで動作します。

### メッセージの吹き出し

メッセージはチャットの吹き出しとして表示され、送信メッセージは右側、受信メッセージは左側に並びます。 各吹き出しには、送信者・タイムスタンプ・配信状況が表示されます。 返信付きのメッセージでは、応答の上に元メッセージの引用プレビューが表示されます。

### テキストの書式

メッセージは、軽量なインライン **Markdown** に対応しています。 受信したメッセージは、記法の文字が取り除かれた状態でスタイルが適用されて表示されます：

| 種別       | 記法                             | 表示結果          |
| -------- | ------------------------------ | ------------- |
| 太字       | `**bold**`                     | **太字**        |
| 斜体       | `*italic*`                     | _斜体_          |
| 取り消し線    | `~~strike~~`                   | ~~取り消し線~~     |
| インラインコード | `` `code` ``                   | 等幅の `code`    |
| リンク      | `[label](https://example.com)` | タップできる**ラベル** |

メッセージを作成するときは、入力欄にフォーカスして 3 文字以上入力すると、入力欄の下に**書式ツールバー**が表示されます。 テキストを選択してスタイルをタップすると、そのテキストが囲まれます（もう一度タップすると解除されます）。選択していない場合は、空のペアが挿入され、カーソルがマーカーの間に置かれます。 リンクボタンをタップすると、URL を入力するダイアログが開きます。 入力中は下書きに書式が適用されて表示されますが、内部のテキストは Markdown の文字を保持しています。

> 💡 **ヒント：** 書式はメッシュ上ではそのままの文字として送られます。iOS が送信するのと同じバイト列です。 Markdown に対応していないクライアント（古いアプリや、素のファームウェアのクライアント）では、`**` や `~~` の文字がそのまま表示されます。 URL、メールアドレス、電話番号は、Markdown を使うかどうかにかかわらず、引き続き自動的にリンクになります。

### メンション

メッセージ作成中に `@` を入力するとノードにメンションできます。入力に応じて、一致する連絡先がピッカーに提案されます。 受信メッセージでは、メンションはノード名を表示したハイライト付きのチップとして現れます。タップすると、そのノードの詳細ページに直接移動できます。

### リアクション

絵文字でメッセージにリアクションできます：

- **Double-tap** a message — or long-press it — to raise a quick reaction bar above the bubble
- Tap an emoji in the bar to send it; tap **more** to open the full picker, or anywhere outside
  the bar to dismiss it without sending
- リアクションはメッセージの吹き出しの下に表示されます
- 複数のユーザーが同じメッセージにリアクションできます
- 自分のメッセージにも、他の人のメッセージにもリアクションできます

> ℹ️ **Note:** Opening the bar sends nothing. A reaction is a real mesh packet, so it only goes
> out when you pick an emoji.

![メッセージの下に表示された絵文字リアクションのバッジ](../../assets/screenshots/messages_reaction.png)

> 💡 **ヒント：** リアクションは軽量で、通常のテキストメッセージに比べてメッシュの帯域をほとんど消費しません。

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

### メッセージの操作

Long-press or double-tap a message to open the quick reaction bar, then tap **More** (the
overflow icon on that bar) to reach:

- **コピー：** メッセージのテキストをクリップボードにコピーします
- **返信：** そのメッセージを引用して返信します
- **リアクション：** 絵文字のリアクションを追加します
- **翻訳：** 受信メッセージをデバイスの言語に翻訳し、原文と翻訳を切り替えます（Google Play 版のみ。オンデバイス翻訳を使用します）
- **削除：** 自分が送信したメッセージを削除します（端末内での削除）

### メッセージの優先度

The app sends every message you compose at the same, default priority — there is no
emergency or alert tier to choose, and nothing in the app raises a direct message above a
channel broadcast. Any prioritising between them happens in firmware, not here. (The app
does mark some of its own internal traffic, such as admin and traceroute packets, as
reliable or background, but that is not something you control from the message composer.)

### メッセージの制限

- **最大長：** 200 バイト（ASCII テキストで約 200 文字）
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions) may arrive slightly longer
- **レート制限：** メッシュは電波利用時間の公平性を確保するため、大量のメッセージは制限されることがあります
- **配信：** 受信確認がない場合、メッセージは自動的に再送されます

## ベストプラクティス

- グループでの連携にはチャンネルを使います
- 個人間のプライベートな通信にはダイレクトメッセージを使います
- メッセージは短くしてください。メッシュの帯域は限られています
- 機密性の高い通信には暗号化を設定します

## 関連トピック

- [ノード](nodes)：ノードをタップするとダイレクトメッセージを開始できます
- [設定：無線機とユーザー](settings-radio-user)：チャンネルの暗号化とプリセットを設定します
- [MQTT](mqtt)：チャンネルのメッセージをインターネットに橋渡しします
- [チャンネル設定](https://meshtastic.org/docs/configuration/radio/channels)：meshtastic.org にある詳細なチャンネル設定

---

