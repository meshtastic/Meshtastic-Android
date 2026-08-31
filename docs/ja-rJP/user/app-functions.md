---
title: アプリ機能
parent: User Guide
nav_order: 19
last_updated: 2026-08-30
description: メッシュの機能を Android システムやオンデバイスの AI アシスタント（例：Gemini）に公開し、アプリを開かずにメッシュのワークフローを実行できるようにします。
aliases:
  - app-functions
  - system-ai
  - gemini
  - assistant
---

# アプリ機能

アプリ機能は、Android App Functions API を通じて、Meshtastic の機能を Android システムやオンデバイスの AI アシスタント（Gemini など）に公開します。 有効にすると、アシスタントがあなたに代わってメッシュのワークフロー（例：メッセージの送信やメッシュ状態の確認）を見つけて実行でき、アプリを開く必要がありません。 App Functions are available on **Google-flavor Android builds only**.

> ℹ️ **Note:** This is separate from the in-app **Chirpy** assistant. アプリ機能&#x306F;_&#x30B7;ステ&#x30E0;_&#x306E; AI アシスタントがあなたのメッシュを操作できるようにするもので、Chirpy は Meshtastic アプリ内の対話型アシスタントです。

## アプリ機能を有効にする

Control App Functions from **Settings → System AI**. この画面には次があります：

- 「**AI のアクセスを許可**」というラベルの**マスタートグル**。サブタイトルは _「システムの AI アシスタント（例：Gemini）がメッシュ機能を見つけて使えるようにする」_ です。 オフの場合、システムには機能が一切公開されません。
- **各機能ごとの個別トグル**。公開したい機能だけを公開できます。

> ⚠️ **Important:** App Functions ship switched on. On a Google-flavor build the master toggle and every individual function, **Send message** included, start enabled — so an assistant can read your mesh data and send messages to your mesh until you turn **Allow AI access** off.

機能は、**書き込み**セクション（何かを変更したり、メッシュにデータを送信したりする機能）と、**読み取り**セクション（情報を返すだけの機能）に分かれています。

![マスタートグルと機能ごとのトグルを備えたアプリ機能の画面](../../assets/screenshots/app-functions_settings.png)

The screenshot has **Send message** and **Get recent messages** switched off to illustrate per-function control; a fresh install shows every switch on.

### 書き込み機能

| 機能               | 内容                                                                                                                                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Send message** | Sends a text message to a contact (direct message) or to a channel. The mesh carries at most 233 bytes of text, so keep assistant-composed messages short. |

### 読み取り機能

| 機能                      | 返す内容                                                                                |
| ----------------------- | ----------------------------------------------------------------------------------- |
| **Get mesh status**     | Whether you're connected to a radio, and how many nodes are online. |
| **Get node list**       | メッシュ上のノードのリスト。                                                                      |
| **Get channel info**    | チャンネルに関する情報。                                                                        |
| **Get device status**   | 接続中の無線機の状態。                                                                         |
| **Get node details**    | 特定のノードに関する詳細情報。                                                                     |
| **Get mesh metrics**    | メッシュからのテレメトリとメトリクス。                                                                 |
| **Get recent messages** | 会話からの最近のメッセージ。                                                                      |
| **Get unread summary**  | 未読メッセージの概要。                                                                         |

## プライバシー

> 🔒 **Privacy:** The **Send message** function lets an assistant send messages to your mesh on your behalf, and the read functions expose node, message, and metric data to it. Because all of them start enabled, the choice you make here is what to turn off rather than what to turn on. Each function has its own toggle, and **Allow AI access** turns all of them off at once.

## 関連トピック

- [メッセージとチャンネル](messages-and-channels)：アプリで直接メッセージを送信する
- [ノード](nodes)：読み取り機能が参照するノードリスト
- [Node Metrics](node-metrics) — the telemetry behind Get mesh metrics
