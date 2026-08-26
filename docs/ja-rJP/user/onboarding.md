---
title: はじめに
parent: User Guide
nav_order: 1
last_updated: 2026-08-25
description: 初回起動時のセットアップ：権限、オンボーディングの流れ、無線機を接続した後の次のステップ。
aliases:
  - 初回起動
  - セットアップ
  - 概要
---

# はじめに

Meshtastic へようこそ！ このガイドでは、Meshtastic Android アプリの初期設定を順を追って説明します。

## 初回起動

アプリを初めて開くと、必要な権限と設定を構成するための導入フローが案内されます。 Each step can be completed in order, or skipped — nothing here is a one-time offer. Every permission can be reviewed and granted later from **Settings → Permissions** inside the app.

### ようこそ画面

ようこそ画面では、Meshtastic とその主な機能を紹介します：

- オフグリッドのメッシュ通信
- 携帯電話やインターネットは不要
- エンドツーエンドで暗号化されたメッセージング

**開始する**をタップして、セットアップフローを進めます。

![ようこそ画面](../../assets/screenshots/onboarding_welcome.png)

## 権限

アプリはセットアップ中にいくつかの権限を要求します。 それぞれに特定の目的があり、一部は主要な機能に必要です。

### Bluetooth の権限

Bluetooth は、スマートフォンと Meshtastic 無線機の主な接続方法です：

- **Bluetooth スキャン**：近くの Meshtastic 無線機を検出します
- **Bluetooth 接続**：ペアリング済みの無線機との接続を確立して維持します

プロンプトが表示されたら、両方の権限を許可してください。 Bluetooth がない場合は、代わりに USB または TCP 接続を使用する必要があります。

### 位置情報の権限

> ⚠️ **Is location required for Bluetooth?** On **Android 11 and older**, yes — those releases treat a Bluetooth scan as a location capability, so the app asks for Location instead of "Nearby devices", and system **Location Services** must also be switched on for a scan to return anything. There you will see **one** location step rather than two, on the Bluetooth screen, because it is a single system permission — and asking twice for it would push you toward the point where Android stops offering the dialog at all (a second denial on Android 11; the "Don't ask again" checkbox on Android 10 and older). On **Android 12 and newer** the two are separate: "Nearby devices" is declared `neverForLocation`, and declining Location does not stop you finding or connecting to a radio.

Meshtastic は、次の目的でも位置情報を使用します：

- メッシュマップ上に自分の位置を表示する
- 他のノードまでの距離を計算する
- 他のメッシュメンバーと GPS 座標を共有する（有効な場合）

お好みに応じて、\*\*「アプリの使用中のみ」**または**「常に許可」\*\*を選択してください：

- **アプリの使用中のみ**：アプリが開いているときだけ位置情報を更新します
- **常に許可**：常時メッシュに存在するために、バックグラウンドでの位置情報更新を有効にします

Declining leaves the rest of the app working: on Android 12 and newer, Bluetooth is unaffected and only the map position and position sharing are disabled. On Android 11 and older, Bluetooth scanning also stops, because that is the permission Android gates it behind.

### 通知の権限

通知は、次のことをお知らせします：

- チャンネルおよびダイレクトメッセージの受信
- メッシュに参加する新しいノード
- リモートノードのバッテリー低下

> 💡 **ヒント：** 通知の設定は、後で Android のシステム設定で細かく調整できます。アプリはカテゴリごとに個別の通知チャンネルを作成する（さらに、バックグラウンドサービスなどの内部用のものもいくつか作成する）ため、個別に有効化したり消音したりできます。

### 重要なアラートの権限

対応デバイスでは、アプリが重要なアラートの権限を要求することがあります：

- これらは、サイレントモードを突破できる高優先度の通知です
- 緊急のメッシュアラートや至急のメッセージに役立ちます
- 突破通知が不要な場合は、このステップを**スキップ**できます
- 後で Android の通知設定で構成または取り消しができます

### Reviewing permissions later

**Settings → Permissions** summarizes where every runtime permission stands. It reads _All allowed_ when nothing needs you, and names the count when something does — opening itself automatically in that case. Tap the row to see the full list at any time:

| 状態                                          | What tapping the row does                                                                    |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Allowed**                                 | Opens the system page, so you can review or revoke it                                        |
| **Not asked yet**                           | Requests it                                                                                  |
| **Denied — tap to allow**                   | Explains what the permission is for, then asks again if you agree                            |
| **Blocked — tap to open system settings**   | Android will no longer show its dialog, so this opens the page where you can turn it back on |
| **Not required on this version of Android** | Nothing — the permission does not exist on your device                                       |

This matters most for notifications. The app used to ask for them in exactly one place — the setup flow — so declining there meant no message, new-node, or low-battery alerts, with nothing in the app that could ask again. Android itself stops showing the dialog once you have declined firmly (a second denial on Android 11 and newer), at which point this row switches to **Blocked** and sends you to the system settings page instead.

## セットアップ後

権限を許可すると、アプリはメイン画面に移行します。 最初に行うべきことは、Meshtastic 無線機への接続です。詳しい手順は[接続](connections)を参照してください。

> 💡 **Tip:** If you skipped any permissions during setup, open **Settings → Permissions** in the app. Every runtime permission is listed there with its current state and a way back to it — including notifications, which the system will not prompt for a second time on its own.

Features also ask in context. Tapping **Scan** on the Connections screen with Bluetooth permission missing explains what it is for and offers to request it; once Android stops prompting, the same control opens the system settings page instead of doing nothing.

## 次のステップ

無線機に接続したら、次を確認してみましょう：

- [接続](connections)：最初の無線機デバイスをペアリングする
- [メッセージとチャンネル](messages-and-channels)：最初のメッセージを送信する
- [ノード](nodes)：メッシュに参加しているノードを確認する
- [マップとウェイポイント](map-and-waypoints)：ノードの位置を表示する
- [設定](settings-radio-user)：無線機とユーザープロフィールを構成する

Meshtastic は初めてですか？ meshtastic.org の[入門ガイド](https://meshtastic.org/docs/getting-started)では、ハードウェアの選択、無線機の初期設定、最初のメッシュのセットアップについて説明しています。

---
