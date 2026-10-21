---
title: ノードメトリクス
nav_order: 5
last_updated: 2026-10-21
description: 各メッシュノードのテレメトリダッシュボード。デバイスの状態、環境センサー、大気質、信号品質、電力、ルート追跡、位置履歴を表示します。
aliases:
  - metrics
  - telemetry
  - device-metrics
  - signal
---

# ノードメトリクス

ノードの詳細画面では、メッシュ上の各ノードについて、包括的なテレメトリとメトリクスを確認できます。

## メトリクスを表示する

1. 「**ノード**」に移動します。
2. 確認したいノードをタップします。
3. Scroll to the **Telemetry** section and find the category you want — **Signal Quality**, **Device Metrics**, **Environment Metrics**, **Air-Quality Metrics**, **Power Metrics**, **Position**, and the rest.
4. Tap the refresh button on a row to ask the node for a fresh reading. The chart button beside it opens that category's history, and appears once the node has reported that kind of telemetry.

![ノードの詳細：ローカルデバイス](../../assets/screenshots/nodes_detail_local.png)

The **Position** row expands to show location data for nodes that share GPS:

![位置のインラインコンテンツ](../../assets/screenshots/nodes_position.png)

> ℹ️ **Note:** Metrics are only available when they have been reported by the remote node. メトリクスは、各ノードのテレメトリ設定で構成された間隔で更新されます。

## デバイスメトリクス

各ノードが報告する基本的な動作情報です：

| メトリクス   | 説明                                                       |
| ------- | -------------------------------------------------------- |
| バッテリー残量 | 現在のバッテリー残量（％）                                            |
| 電圧      | バッテリー電圧の測定値                                              |
| ChUtil  | Percentage of local airtime in use                       |
| AirUtil | Percentage of the last hour this node spent transmitting |
| 連続稼働時間  | 前回の再起動からの経過時間                                            |

Device Metrics has no cards on the node detail screen. Use the chart button on its row to open the Device Metrics screen, where battery level, voltage, ChUtil, and AirUtil are plotted over time and every reading — uptime included — is listed with its timestamp underneath. Pick a time frame at the top of the screen, and use the save icon in the app bar to export the visible history as CSV.

> 💡 **Tip:** Where a category does show cards — Environment, Air Quality, and Power — touch & hold a card to copy its value to the clipboard. On a chart screen, pinch to zoom the time axis.

## 環境メトリクス

環境センサーのデータです（対応ハードウェアが必要）：

| メトリクス    | センサーの例                |
| -------- | --------------------- |
| 温度       | BME280, BME680, SHT31 |
| 湿度       | BME280, BME680, SHT31 |
| 大気圧      | BME280, BMP280        |
| ガス抵抗     | BME680                |
| IAQ（大気質） | BME680                |

Environment metrics are charted over time — temperature, humidity, and pressure each get their own line chart with the measurement unit displayed on the Y axis.

BME680 の \*\*IAQ（室内空気質）\*\*指数は、ガス抵抗から算出される 0〜500+ の単一の値で、_非常に良い_ から _危険なほど汚染_ までの色分けされたスケールで表示されます：

![「非常に良い」から「危険なほど汚染」までの IAQ 指数スケール](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **ヒント：** 環境メトリクスには、リモートノードに接続されたセンサーが必要です。すべてのノードが環境データを報告するわけではありません。対応センサーの一覧については、[テレメトリとセンサー](telemetry-and-sensors) を参照してください。

## 大気質メトリクス

大気質は、粒子状物質センサーや CO₂ センサーを搭載したノード向けの専用メトリクスビューです。これは、環境メトリクスに記載されている **BME680 の IAQ の測定値とは別のもの**です。IAQ はガス抵抗から算出される単一の指数であるのに対し、大気質ビューはその基となる粒子状物質と CO₂ の測定値をグラフ化します。

| メトリクス                 | 単位      | 説明                                                                                                                                                                                   |
| --------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| PM1.0 | µg/m³   | 1.0 ミクロンまでの粒子状物質                                                                                                                                                     |
| PM2.5 | µg/m³   | 2.5 ミクロンまでの粒子状物質                                                                                                                                                     |
| PM10                  | µg/m³   | 10 ミクロンまでの粒子状物質                                                                                                                                                                      |
| AQI                   | EPA 指数  | EPA **NowCast** AQI computed from the node's recent PM2.5 history, with a color-coded severity label. 十分な測定値が蓄積されると、PM2.5 の横に表示されます。 |
| CO₂                   | ppm     | 二酸化炭素の濃度                                                                                                                                                                             |
| CO₂ 温度                | °C / °F | CO₂ センサー自体が報告する温度（例：SCD4x）                                                                                                                                                           |
| CO₂ 湿度                | %       | CO₂ センサーが報告する相対湿度                                                                                                                                                                    |

CO₂ readings are color-coded by severity so you can read air quality at a glance:

| 区分     | CO₂ の範囲（ppm）                     | 色    |
| ------ | -------------------------------- | ---- |
| 良      | < 1000  | 緑    |
| 空気がこもる | < 2000  | 黄色   |
| 不良     | < 5000  | オレンジ |
| 危険     | < 30000 | 赤    |
| 退避     | ≥ 30000                          | 濃い赤  |

![CO₂ の深刻度が色分けされた大気質の測定値](../../assets/screenshots/node-metrics_air_quality.png)

The **Air-Quality Metrics** row is always listed on the node detail screen; its chart button appears once the node has reported air-quality telemetry. 大気質ビューでは、次のことができます：

- グラフの**期間**を選択します。
- **メトリクスチップ**で絞り込みます。データがあるメトリクスのみが表示されます。
- 最新の大気質テレメトリを**更新／要求**します。
- 表計算ソフトで分析できるよう、**CSV にエクスポート**します。

See [Telemetry & Sensors](telemetry-and-sensors) for supported air-quality hardware.

## 信号品質

無線信号の品質に関する情報です：

| メトリクス  | 説明                                   |
| ------ | ------------------------------------ |
| SN比    | 信号対ノイズ比（高いほど良い）                      |
| RSSI   | 受信信号強度インジケーター（0 に近いほど良い）             |
| ノイズフロア | ローカルの背景 RF ノイズ（dBm、値が小さい（負が大きい）ほど静か） |

### 信号品質の目安

Signal quality is rated from **SNR relative to the active LoRa modem preset's demodulation floor**, not from fixed thresholds — a given SNR means different things on different presets (for example, −16 dB rates Good on Long Fast but None on Short Fast). When RSSI and a noise-floor reading are both available, the app also rates their difference against the preset limit and uses the worse rating. Otherwise, RSSI is display-only. In the table, _limit_ is the preset's SNR limit.

| 品質 | 基準                                                    |
| -- | ----------------------------------------------------- |
| 良  | SNR がプリセットの限界を上回る                                     |
| 普通 | 限界より 5.5 dB 未満低い                      |
| 不良 | 限界より 5.5 dB〜7.5 dB 低い |
| なし | 限界より 7.5 dB を超えて低い                    |

詳しい説明は、[信号メーターを理解する](signal-meter) を参照してください。

Local Stats from your connected node are also shown in Signal Quality when available. These logs include noise floor, traffic counters, relay counters, online node counts, and uptime. The noise floor chart uses a dashed reference line at −85 dBm to help identify a busy RF environment.

- **Request** — ask the connected node for a fresh Local Stats telemetry report
- **Clear** — remove Local Stats logs for that node
- **Save** — export the visible Local Stats history as CSV

## 電力メトリクス

電力管理のテレメトリです（INA センサーまたは対応ハードウェアが必要）：

| メトリクス | 説明                             |
| ----- | ------------------------------ |
| 電圧    | Per-channel voltage reading    |
| 電流    | Per-channel draw, in milliamps |

The node detail screen shows cards for channels 1 to 3. Use the chart button on the **Power Metrics** row to open the chart screen, which lists a chip for every channel that reported data — up to eight — and charts the one you select. Use the label field under the chips to give a channel a name of your own, such as Solar or Battery. The app does not derive a wattage figure from voltage and current.

## ルート追跡

ルート追跡は、メッセージがメッシュ内を通る経路を表示します：

1. From the node detail screen's **Telemetry** section, tap the refresh button on the **Traceroute** row. You cannot traceroute your own node. The app sends at most one traceroute every 30 seconds, to any node, and **Trace Route** in the node list's touch & hold menu shares that cooldown.
2. アプリが対象のノードにルート追跡の要求を送信します。
3. Results show each hop with its SNR.

### ルート追跡の結果の見方

A traceroute is a round trip, so each saved result carries a hop count in each direction, **Forward Hops** and **Return Hops**, plus the **Round Trip** time in seconds. A result marked **Direct** reached the target with no relay in between. Tap a result to read the route traced toward the destination and the route traced back to you, with the SNR of every hop. That view offers **View on map**, which draws the same path, as long as the start and destination nodes have both shared a position.

A relay that didn't record itself in the route shows as **Unknown hop**, and a relay your node has no record of shows as its node ID, such as `!a1b2c3d4`.

The history also keeps traceroutes this app didn't request:

- **Observed** marks a response from this node to a request made elsewhere, such as one started from your node's own screen. It has hop counts but no round-trip time, and its points on the chart are hollow rings.
- **Traced by** _node_ marks a traceroute that node ran to yours. It shows the inbound route with the SNR of each hop, but no return route, no round-trip time, and no map, and it stays off the chart.

A result marked **No Response** means the target never answered. It may be out of range, asleep, or configured not to reply. Wait for the 30-second cooldown to clear and try again; if it keeps failing, send a direct message first to confirm the node is reachable at all.

## 位置ログ

The chart button on the **Position** row opens the node's position history: its track on a map, and a list of every position report with latitude, longitude, satellites in view, altitude, speed, heading, and timestamp. From the app bar you can:

- **Export GPX** to save the track as a GPX file. Reports without a GPS fix are left out.
- Save the list as CSV.
- **Clear** the recorded track, after a confirmation. This can't be undone.
- Ask a remote node for a fresh position.

## 隣接ノード情報

あるノードが直接受信できるノードを表示します。メッシュのトポロジーを把握するのに役立ちます。 The **Neighbor Info** row appears once that node's neighbor list has reached the app. See [Local Mesh Discovery](discovery#neighbor-info) for turning the module on.

## ホストのメトリクス

Nodes that run Meshtastic on a Linux host, such as a Raspberry Pi, report the host's own health — free memory, free disk space, one-, five-, and fifteen-minute load averages, and how long the host has been up. The **Host Metrics** row is always listed; its chart button appears once a node has reported them.

## PAX メトリクス

A node running the PAX counter module reports how many Wi-Fi and Bluetooth devices it saw nearby, as a crowd-size estimate, and charts the two counts alongside their total. The **PAX Metrics** row is always listed; its chart button appears once a node has reported them. The counts are of devices, not people.

## 関連トピック

- [ノード](nodes)：ノードリスト、絞り込み、並べ替え
- [テレメトリとセンサー](telemetry-and-sensors)：対応センサーと設定
- [信号メーター](signal-meter)：SNR と RSSI から信号品質を計算する方法
- [Local Mesh Discovery](discovery) — traceroute details and neighbor info
- [単位とロケール](units-and-locale)：温度・距離・速度の表示形式
