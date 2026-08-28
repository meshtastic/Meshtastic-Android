---
title: テレメトリとセンサー
parent: User Guide
nav_order: 9
last_updated: 2026-08-27
description: メッシュ上のセンサーデータ。対応する環境・大気質・電力センサーと、設定・表示のガイドを説明します。
aliases:
  - sensors
  - environment
  - weather
  - power-metrics
---

# テレメトリとセンサー

Meshtastic のノードは、メッシュネットワーク全体でセンサーデータを収集・共有できます。

## 概要

テレメトリを使うと、センサーを搭載したノードが、環境・電力・デバイスの状態の情報をブロードキャストできます。 このデータはノードの詳細画面で確認でき、時系列で記録することもできます。

## デバイステレメトリ

すべての Meshtastic ノードは、基本的なデバイステレメトリを報告します：

| メトリクス       | 説明                  | 標準的な範囲                                                             |
| ----------- | ------------------- | ------------------------------------------------------------------ |
| バッテリー残量     | 充電の割合               | 0–100%                                                             |
| 電圧          | バッテリー電圧             | 3.0–4.2V (LiPo) |
| チャンネル全体の利用率 | ローカルで使用された電波利用時間の割合 | 0–100%                                                             |
| 送信の電波利用率    | このノードが使用した電波利用時間の割合 | 0–100%                                                             |
| 連続稼働時間      | 前回の起動からの経過秒数        | 可変                                                                 |

## 環境センサー

対応する環境センサー：

### 温度と湿度

| センサー    | 温度 | 湿度 | 気圧 | 備考           |
| ------- | -- | -- | -- | ------------ |
| BME280  | ✓  | ✓  | ✓  | おすすめのオールインワン |
| BME680  | ✓  | ✓  | ✓  | ガス抵抗／IAQ を追加 |
| SHT31   | ✓  | ✓  | —  | 高精度          |
| MCP9808 | ✓  | —  | —  | 高精度な温度       |
| LPS22   | —  | —  | ✓  | 気圧のみ         |

### 大気質

| センサー     | メトリクス                                              | 備考                                                                                                                                        |
| -------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| BME680   | ガス抵抗／IAQ                                           | 揮発性有機化合物                                                                                                                                  |
| PMSA003I | PM1.0, PM2.5, PM10 | 粒子状物質                                                                                                                                     |
| SEN55    | PM, Temp, Humidity                                 | Multi-sensor. Its NOx and VOC indices are recorded and included in a CSV export, but are not yet shown as cards or charts |

### Soil

| メートル法 | 単位      | 備考                                              |
| ----- | ------- | ----------------------------------------------- |
| 土壌温度  | °C / °F | Reported alongside soil moisture by soil probes |
| 土壌水分  | %       | Volumetric water content                        |

Both appear as info cards on the node detail screen, next to the other environment readings.

### 光と UV

| センサー     | メトリクス       |
| -------- | ----------- |
| OPT3001  | 周囲の明るさ（ルクス） |
| VEML7700 | 周囲の明るさ（ルクス） |
| LTR390   | UV 指数       |

## 電力メトリクス

INA シリーズの電力センサーを搭載したノードは、次を報告できます：

| メトリクス | 説明                              |
| ----- | ------------------------------- |
| 電圧    | Per-channel voltage reading     |
| 電流    | Per-channel current draw, in mA |

Up to three channels are reported (ch1–ch3), and each can be given its own label — Solar or Battery, say — from the node detail screen. There is no separate wattage reading; the app charts voltage and current, and does not compute power from them.

リモートノードの太陽光充電やバッテリーの状態を監視するのに便利です。

## テレメトリを設定する

1. 「**設定 → モジュール設定 → テレメトリ**」に移動します。
2. Each metric group has its own enable toggle and its own interval:

   - **Device Metrics** — battery, channel and airtime utilisation
   - **Environment Metrics** — temperature, humidity, pressure and the other sensor readings
   - **Air Quality Metrics** — particulate and CO₂ readings
   - **Power Metrics** — the per-channel voltage and current readings

   Environment metrics additionally have an on-screen toggle and a Fahrenheit toggle for the
   device's own display.

### Choosing an Interval

> 💡 **Tip:** These are nominal values, not hard schedules. On a congested mesh the firmware
> automatically backs off to longer intervals based on how many nodes are online, so you do not
> need to hand-tune them for mesh size. Lengthen them deliberately only to save battery.

## 大気質メトリクス

粒子状物質センサーまたは CO₂ センサーを搭載したノードは、大気質データを報告します：

| Metric                | Unit  | 説明       |
| --------------------- | ----- | -------- |
| PM1.0 | µg/m³ | 超微小粒子状物質 |
| PM2.5 | µg/m³ | 微小粒子状物質  |
| PM10                  | µg/m³ | 粗大粒子状物質  |
| CO₂                   | ppm   | 二酸化炭素の濃度 |

SCD4x などの CO₂ センサーは、自身の温度と湿度も報告し、上記の測定値とともに表示されます。 アプリは、PM2.5 の履歴から **EPA NowCast AQI** の値も算出します。

CO₂ の測定値は、深刻度に応じて色分けされます（良好 → 空気がこもる → 悪い → 危険 → 退避）。 正確な ppm の区分、色、AQI の詳細については、[ノードメトリクス：大気質](node-metrics#air-quality-metrics) を参照してください。

大気質データは、ノードの詳細画面で情報カードとして表示したり、時系列でグラフ化したり、CSV にエクスポートしたりできます。

## テレメトリを表示する

1. 「**ノード**」に移動して、ノードを選択します。
2. 詳細画面にテレメトリのセクションが表示されます：
   - デバイスメトリクス（常に利用可能）
   - 環境メトリクス（センサーがある場合）
   - 電力メトリクス（INA センサーがある場合）
   - 大気質メトリクス（PM／CO₂ センサーがある場合）
3. 履歴グラフで、時系列の傾向を確認できます。

![テレメトリの操作](../../assets/screenshots/node-metrics_telemetric_actions.png)

## トラブルシューティング

- **環境データが表示されない？** リモートノードに物理センサーが接続されている必要があります（例：I2C の BME280）。 デバイステレメトリ（バッテリー、連続稼働時間）は常に利用できますが、環境メトリクスにはハードウェアが必要です。
- **測定値が古い？** 報告間隔を確認してください。非常に長い間隔（7200 秒以上）では、データの更新頻度が低くなります。 リモートノードがまだオンラインであるかも確認してください。
- **I2C バスでセンサーが競合している？** 一部のセンサーは I2C アドレスを共有しています。 同じバスに複数のセンサーがある場合は、無線機のシリアルデバッグ出力でアドレスの衝突がないか確認してください。

## 関連トピック

- [ノードメトリクス](node-metrics)：ノードの詳細画面でテレメトリデータを表示
- [設定：モジュールと管理](settings-module-admin)：テレメトリモジュールの設定
- [単位とロケール](units-and-locale)：温度と気圧の表示単位

---

