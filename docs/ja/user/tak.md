---
title: TAK 連携
nav_order: 10
last_updated: 2026-10-21
description: ATAK および WinTAK と相互運用します。CoT による位置共有、TAK の役割、プラグインの設定を説明します。
aliases:
  - tak
  - atak
  - team-awareness-kit
---

# TAK 連携

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic nodes and TAK applications like ATAK and WinTAK.

## 概要

TAK モジュールを使うと、Meshtastic のノードは次のことができます：

- TAK 互換の CoT（Cursor on Target）形式で位置データを共有する
- TAK のマップ表示上にチームメンバーとして表示される
- TAK の PLI（位置情報）メッセージを受信する

## セットアップ

### 前提条件

- ATAK (Android Team Awareness Kit), iTAK, or WinTAK installed
- Your node's **Device Role** (Device configuration) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module configuration at all
- Firmware 2.8.0 or newer on the node. Earlier firmware accepts a TAK config write and never
  stores it, so the app hides the TAK module entry below that version even when the role is set
  correctly. See [Firmware Updates](firmware)

> ⚠️ **Warning:** The old **Meshtastic ATAK Plugin** is no longer part of this path and cannot
> work. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service runs in-process only. Do not install it. Interop runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described in the following sections, with stock ATAK/iTAK/WinTAK.

### 設定

Navigate to **Settings → Module configuration → TAK (ATAK)**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Device Role** setting in Device configuration is what
turns TAK on. Your node broadcasts this identity, which appears on TAK maps.

| 設定項目 | 説明                                                                                                                             | デフォルト                                    |
| ---- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| 役割   | Your operational role within the team: Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, or K9 | Default (Team Member) |
| Team | Your team color on the TAK map, one of the 14 standard TAK team colors such as Blue, Red, Cyan, or Green                       | Default (Cyan)        |

Your TAK callsign isn't a separate setting. The firmware uses your node's long name.

Team colors are the standard TAK team colors, so coordinate with your TAK team to use consistent team assignments.

### ローカル TAK サーバー

The app can also run a **local TAK server** so ATAK on the **same phone**, or WinTAK on the same computer running the desktop app, can connect directly without a remote TAK server. サーバーは localhost のみ（`127.0.0.1:8089`）にバインドし、相互証明書認証（mTLS）による TLS を使用するため、ネットワーク上の他のデバイスからは到達できません。

Open **Settings → Advanced → TAK Server**. These are app settings stored on this phone, which is why they sit next to **Firmware Update** and **Debug Panel** rather than under the TAK module config — and why they need neither a TAK role nor firmware 2.8.0. On Android 17 and newer, turning the server on asks for the local network permission it needs to open its socket. If that permission is denied for good, the app turns the server back off and offers to open app settings:

![有効化トグルとエクスポートオプションを備えたローカル TAK サーバーの設定](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK connections from the same device.
- **Status** — under the toggle: _Off_, _Starting TAK Server_, _Listening on 127.0.0.1:8089 — waiting for ATAK_, the count of local client connections, or _Unable to start TAK Server. Turn it off and on to retry._
- **TAK Mesh Channel** — selects which Meshtastic channel outgoing TAK traffic is sent on (default: the primary channel, index 0). Incoming TAK traffic is accepted from any channel. Matches the equivalent setting on iOS and in the legacy ATAK plugin.
- **Mesh to CoT Converter** — off by default, and shown under the server toggle. With the server
  running, this synthesizes a CoT contact for every node in your node database, so ordinary
  Meshtastic nodes appear on the ATAK map as contacts. **This is what replaced the old plugin's
  node visibility** — without it, only TAK-role nodes show up. When the connected node runs
  firmware older than 2.8.0, a notice under the switch says that only location and chat messages
  bridge to ATAK.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK can import to connect to this server. The share icon in the top bar does the same.

## TAK の役割

TAK 関連の役割を設定したノードは、標準のクライアントとは動作が異なります：

| 役割              | 説明                                                                                                                                                                                                                                |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**         | For a node paired with an ATAK device. Turns off many routine broadcasts to favor the CoT packet stream.                                                                                          |
| **TAK Tracker** | Broadcasts TAK PLI automatically, at the interval set by the position configuration, and turns off many routine broadcasts. Suited to unattended position beacons such as vehicles and equipment. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a node mounted in a vehicle). ユーザーが TAK の運用に積極的に参加するデバイスには **TAK** を使用します。

### CoT（Cursor on Target）形式

TAK のメッセージは Cursor on Target の XML 形式を使用します。これは状況認識データを共有するための軍事標準です。 Meshtastic は、TAK システムに橋渡しする際に内部の protobuf メッセージを CoT 形式に変換するため、手動での形式変換は必要ありません。

## ワイヤ形式（V1／V2）

Meshtastic supports two TAK wire formats, chosen automatically based on the connected node's firmware — no manual configuration needed:

| 形式       | 互換性                                              | 機能                                                                                          |
| -------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| V1（レガシー） | ファームウェア 2.7.x 以前 | ポート 72 での素の protobuf エンコード。位置共有（PLI）とチャット（GeoChat）のみに対応。図形、マーカー、ルート、その他の型付き CoT イベントは破棄されます |
| V2（現行）   | ファームウェア 2.8.0 以降 | ポート 78 でのコンパクトな zstd 圧縮エンコード。 V1 が対応するすべてに加えて、図形、マーカー、ルート、航空機、casevac、緊急、タスクの CoT タイプを追加します |

ノードは V2 を実行していても、古いノードからのレガシーな V1 パケットを引き続き中継するため、ファームウェアが混在したメッシュでも動作し続けます。

## ATAK での使用

設定が完了すると：

- Meshtastic のノードが、コールサインのラベル付きで ATAK のマップにマーカーとして表示されます
- チャットメッセージを、メッシュと TAK ネットワークの間で橋渡しできます
- 位置の更新が、Meshtastic と TAK の間で双方向に流れます
- TAK Tracker のノードは自動的に PLI をブロードキャストします。ATAK 側の設定なしで、その位置が ATAK のマップに表示されます
- On Android, routes received from the mesh are also saved as a data package (`.zip`) in **Downloads**; import it in ATAK to add the route. An update to the same route overwrites its file rather than adding a copy. After the app is reinstalled, the first update to a route saves one renamed copy, and later updates overwrite that. On Android 9 and older the file goes to the app's own folder under `Android/data` instead. The desktop app doesn't save route files

> ℹ️ **Note:** TAK integration requires specific node roles. Standard client nodes don't automatically participate in TAK operations — though with **Mesh to CoT Converter** enabled they still appear on the ATAK map as contacts.

## トラブルシューティング

| 問題                        | 原因                                                                    | 解決策                                                                                                                                                                                                                    |
| ------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ノードが ATAK のマップに表示されない     | Wrong Device Role setting, or Mesh to CoT Converter off               | Set the node's **Device Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under **Settings → Advanced → TAK Server** |
| 位置の更新が古い                  | GPS 測位が失われた、または間隔が長すぎる                                                | GPS の状態を確認し、位置情報設定で位置のブロードキャスト間隔を短くする                                                                                                                                                                                  |
| ATAK shows "disconnected" | The local TAK server is off, or ATAK is pointed elsewhere             | Check **Enable Local TAK Server** is on, and that ATAK is connecting to `127.0.0.1:8089` — re-import the exported data package if unsure                                                                               |
| 図形、マーカー、ルートが橋渡しされない       | 送信側のノードがレガシーな V1（ファームウェア 2.7.x 以前）である | V2 ワイヤ形式にするため、送信側のノードのファームウェアを 2.8.0 以降に更新する                                                                                                                                           |
| CoT データが流れない              | チャンネルの不一致                                                             | すべての TAK ノードが、暗号化が一致した同じチャンネル上にある必要がある                                                                                                                                                                                 |

## セキュリティに関する注意

> 🔒 **Privacy:** TAK data shares your position and callsign information. The TAK module respects
> the same channel encryption as other Meshtastic messages — in sensitive environments, use a
> channel with a non-default key.

## 関連トピック

- [設定：モジュールと管理](settings-module-admin)：TAK モジュールの設定
- [ノード](nodes)：ノードリストの TAK と TAK Tracker の役割
- [マップとウェイポイント](map-and-waypoints)：マップ上のノードの位置
