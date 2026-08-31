---
title: ホーム画面ウィジェット
parent: User Guide
nav_order: 20
last_updated: 2026-08-30
description: Meshtastic のホーム画面ウィジェットを追加すると、アプリを開かずに、接続中の無線機のローカル統計をひと目で確認できます。
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# ホーム画面ウィジェット

Android では、Meshtastic はホーム画面の**ウィジェット**を提供しており、接続中の無線機のライブなローカル統計をひと目で確認できます。アプリを開く必要はありません。

## 表示される内容

ウィジェットは、**接続中の無線機**の現在のローカル統計を表示します：

- A **node chip** across the top, carrying the radio's short name in its own colors
- **Battery**：無線機のバッテリー残量。外部電源で動作している場合は _Powered_（給電中）
- **ChUtil**：チャンネル利用率（LoRa チャンネルがどれだけ混雑しているかを割合で表示）
- **AirUtil**：電波利用率（無線機がデューティサイクルのうちどれだけ送信しているか）
- **Traffic**：送受信したパケットと、検出した重複
- **Relays**：中継したパケットと、中継のキャンセル（無線機が中継しているときに表示）
- **Diagnostics** — a combined line carrying **Noise** (the background noise level in dBm), **Bad** (corrupt packets received), and **Dropped** (packets the radio discarded). Bad and Dropped appear only once they are above zero, so a quiet radio may show the noise reading alone
- **Heap** — free versus total memory on the radio, drawn as a bar
- **Nodes** — how many nodes are online, out of the total known
- **Uptime** — how long the radio has been running since its last reboot, shown beside Nodes
- **Updated** — the time the stats last refreshed, along the foot of the widget

ウィジェットをタップするとアプリが開きます。更新コントロールを使うと、最新の統計を要求できます。

> ℹ️ **Note:** The values reflect the connected radio. If the radio disconnects, the widget replaces the stats with a status line — **Disconnected**, **Connecting**, or **Device sleeping**. It does not keep the last-known numbers on screen.

## ウィジェットを追加する

1. Touch & hold an empty area of your Android home screen.
2. 「**ウィジェット**」をタップします。
3. Drag the **Meshtastic** widget to your home screen. The app ships one widget, so the picker entry is just the app name.
4. 必要に応じてサイズを変更します。レイアウトは利用可能なスペースに合わせて調整されます。

> ℹ️ **Note:** The widget is Android-only. デスクトップ版や iOS 版では利用できません。

## 関連トピック

- [ノードメトリクス](node-metrics)：アプリ内の完全な信号品質とローカル統計の履歴
- [コネクション](connections)：ウィジェットに表示する統計が得られるよう、無線機に接続する
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
