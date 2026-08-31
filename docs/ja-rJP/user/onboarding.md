---
title: はじめに
parent: User Guide
nav_order: 1
last_updated: 2026-08-30
description: 初回起動時のセットアップ：権限、オンボーディングの流れ、無線機を接続した後の次のステップ。
aliases:
  - 初回起動
  - セットアップ
  - 概要
---

# はじめに

This page covers the first-launch flow of the Meshtastic Android app, what each permission is for, and how to revisit them later.

## 初回起動

When you open the app for the first time, the app guides you through an introductory flow that configures essential permissions and settings. Complete each step in order or skip it — nothing here is a one-time offer. Every permission can be reviewed and granted later from the **Permissions** section of **Settings** inside the app.

### ようこそ画面

The welcome screen introduces Meshtastic with three feature rows:

|                               |                                                      |
| ----------------------------- | ---------------------------------------------------- |
| **Stay Connected Anywhere**   | 携帯電話の電波がなくても、友人やコミュニティとオフグリッドで通信できます。                |
| **Create Your Own Networks**  | 遠隔地での安全で信頼性の高い通信のために、プライベートメッシュネットワークを簡単にセットアップできます。 |
| **Track and Share Locations** | 統合された GPS 機能で位置情報をリアルタイムに共有し、グループの連携を保ちます。           |

Tap **Get started** to proceed through the setup flow.

![ようこそ画面](../../assets/screenshots/onboarding_welcome.png)

## 権限

アプリはセットアップ中にいくつかの権限を要求します。 それぞれに特定の目的があり、一部は主要な機能に必要です。

### Bluetooth の権限

Bluetooth is the primary connection method between your phone and Meshtastic radio. The **Bluetooth** screen shows what the permission buys you:

- **Discovery** — find and identify Meshtastic devices near you.
- **Configuration** — wirelessly manage your device settings and channels.

On Android 12 and newer, Android asks once, for **Nearby devices**, and that one grant covers both scanning and connecting. Without it, you'll need to use USB or TCP connections instead.

### 位置情報の権限

> ℹ️ **Note:** Location is not required for Bluetooth on Android 12 and newer. **Android 11 and older** show one location step, on the Bluetooth screen, rather than two — those releases treat a Bluetooth scan as a location capability, so the app asks for Location instead of "Nearby devices". Asking twice would push you toward the point where Android stops offering the dialog at all (a second denial on Android 11; the "Don't ask again" checkbox on Android 10 and older). On **Android 12 and newer** the two are separate: "Nearby devices" is declared `neverForLocation`, and declining Location does not stop you finding or connecting to a radio.

Meshtastic は、次の目的でも位置情報を使用します：

- メッシュマップ上に自分の位置を表示する
- 他のノードまでの距離を計算する
- 他のメッシュメンバーと GPS 座標を共有する（有効な場合）

Grant **"While using the app"**. The app does not request background location — `ACCESS_BACKGROUND_LOCATION` is not in its manifest — so Android will not offer an "Always" option, and position updates happen while the app is in the foreground or running its foreground service.

Declining leaves the rest of the app working: on Android 12 and newer, Bluetooth is unaffected and only the map position and position sharing are disabled. On Android 11 and older, Bluetooth scanning also stops, because that is the permission Android gates it behind — and system **Location Services** must also be switched on for a scan to return anything.

### 通知の権限

通知は、次のことをお知らせします：

- チャンネルおよびダイレクトメッセージの受信
- メッシュに参加する新しいノード
- リモートノードのバッテリー低下

> 💡 **ヒント：** 通知の設定は、後で Android のシステム設定で細かく調整できます。アプリはカテゴリごとに個別の通知チャンネルを作成する（さらに、バックグラウンドサービスなどの内部用のものもいくつか作成する）ため、個別に有効化したり消音したりできます。

### 重要なアラートの権限

Critical alerts are high-priority notifications that break through Do Not Disturb — for emergency mesh alerts and urgent messages.

This step is not a runtime permission prompt. There is no grant/deny dialog: the button opens the Android system settings page for the app's **Alerts** notification channel, where you turn the breakthrough behavior on yourself. Tap **Configure Critical Alerts** to open that page, or **Skip** to move on — you can reach the same page later from Android's notification settings for Meshtastic. This step appears only if you granted notifications on the previous screen — skip or decline them and setup ends there.

### Reviewing permissions later

The **Permissions** section of **Settings** summarizes where every runtime permission stands. On Android 12 and newer it lists five: **Nearby devices permission** (Bluetooth), **Location permission**, **App Notifications**, **Camera permission** (scanning channel and contact QR codes) and **Local network permission** (finding radios over Wi-Fi by mDNS). On Android 11 and older a single **Location permission** row covers both Bluetooth and location, so there are four. The last two are never asked for during setup, only when a feature first needs them.

The section reads _All allowed_ when every permission is granted, _Nothing needs your attention_ when some have simply never been asked for, and names a count when one is denied — in which case it expands itself. Tap the row to expand or collapse it at any time:

| 状態                                          | What tapping the row does                                                                    |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Allowed**                                 | Opens the system page, so you can review or revoke it                                        |
| **Not asked yet**                           | Requests it                                                                                  |
| **Denied — tap to allow**                   | Explains what the permission is for, then asks again if you agree                            |
| **Blocked — tap to open system settings**   | Android will no longer show its dialog, so this opens the page where you can turn it back on |
| **Not required on this version of Android** | Nothing — the permission does not exist on your device                                       |

This matters most for notifications. If you decline them during setup, this row is the way back: Android stops showing the dialog once you have declined firmly (a second denial), at which point this row switches to **Blocked** and sends you to the system settings page instead. The notification prompt exists only on Android 13 and newer — on older versions notifications are on by default and managed from Android's own settings.

## セットアップ後

After you grant permissions, the app opens the main interface. 最初に行うべきことは、Meshtastic 無線機への接続です。詳しい手順は[接続](connections)を参照してください。

> 💡 **Tip:** If you skipped any permissions during setup, open the **Permissions** section of **Settings** in the app. Every runtime permission is listed there with its current state and a way back to it — including notifications, which the system will not prompt for a second time on its own.

Features also ask in context. On the **Connect** tab, a card above the device list explains what the Bluetooth permission is for and offers **Grant permission**; once Android stops prompting, that button becomes **Open settings**.

Meshtastic は初めてですか？ meshtastic.org の[入門ガイド](https://meshtastic.org/docs/getting-started)では、ハードウェアの選択、無線機の初期設定、最初のメッシュのセットアップについて説明しています。

## 関連トピック

- [Connections](connections) — pair your first radio
- [メッセージとチャンネル](messages-and-channels)：最初のメッセージを送信する
- [Nodes](nodes) — see who else is on your mesh
- [マップとウェイポイント](map-and-waypoints)：ノードの位置を表示する
- [Settings — Radio & User](settings-radio-user) — configure your radio and user profile
