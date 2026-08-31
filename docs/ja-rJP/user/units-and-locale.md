---
title: 単位・計測・ロケール
parent: User Guide
nav_order: 16
last_updated: 2026-08-30
description: アプリが、デバイスのロケールに基づいて温度・距離・速度などの計測値をどう表示するかを説明します。
aliases:
  - measurement
  - units
  - locale
  - metric
  - imperial
---

# 単位・計測・ロケール

The Meshtastic app automatically displays temperatures, distances, speeds, and times in the units your device is configured to use. If your device's settings can't express the units you want, an in-app **Units** setting overrides them.

## 仕組み

Meshtastic の無線機は、常に**メートル法の単位**（メートル、°C、m/s、hPa など）でデータを送信します。 アプリはこのデータを受信すると、デバイスのロケールで指定された単位系に変換して値を表示します。

Android では、計測の設定はシステムの**言語と地域**の設定によって決まります。 デスクトップ（JVM）では、アプリは JVM のデフォルトの `Locale` を使用します。

Units follow your device's **region**, not the display language. Plain languages — like **English** in the app's own Language setting or Android's per-app language — keep the region your device is set to. A choice that names a region of its own, like **English (Canada)**, overrides it and brings that region's units with it. On Android 16+, the system-wide **Measurement system** preference overrides the region for distance, speed, and the other measurements — but not for temperature, which keeps following the region.

> 💡 **Tip:** By default there is nothing to configure — change your system measurement preferences and every screen in Meshtastic updates automatically. If your device offers no working region or measurement setting (some manufacturer builds don't), set **Settings → Units** in the app instead.

## The Radio's Own Screen Is Separate

**Settings → Device configuration → Display → Display units** configures the screen on the radio, not the app. The **Use 12h clock format** and **Always point north** settings do too — all three apply to the radio's display only. Temperature on that screen has its own setting, **Environment metrics use Fahrenheit**, on the radio's Telemetry module — see the [Telemetry module reference](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit) on meshtastic.org.

If your node list shows miles while the radio's screen shows kilometers, this is why: the two are set in different places. Changing the radio's setting never alters what the app displays. See the [Display Config](https://meshtastic.org/docs/configuration/radio/display) guide on meshtastic.org for the device-side options.

## 温度

環境センサーの温度値は **°C** で送信され、デバイスの温度単位の設定に基づいて表示されます。

![温度を含む環境メトリクス](../../assets/screenshots/nodes_environment_metrics.png)

| あなたの設定 | 表示   |
| ------ | ---- |
| 摂氏     | 22°C |
| 華氏     | 72°F |

これは、アプリ全体のすべての温度表示に影響します：ノードの環境テレメトリ、土壌温度、露点、テレメトリグラフの軸。

Temperature follows your locale's **temperature preference**, independent of the distance system. Locales that mix systems work correctly — a UK phone shows miles for distance but **°C** for temperature. On Android 14+, the **Temperature** regional preference (Settings → System → Languages → Regional preferences) overrides the locale default.

## 距離と標高

ノード間の距離と GPS の標高は**メートル**で送信され、自動的にスケーリングされて変換されます。

![距離情報の表示](../../assets/screenshots/nodes_distance_info.png)

| あなたの設定       | 短い距離     | 長い距離                   | 標高       |
| ------------ | -------- | ---------------------- | -------- |
| メートル法        | 350 m    | 2.5 km | 1,200 m  |
| ヤード・ポンド法（米国） | 1,148 ft | 1.6 mi | 3,937 ft |

The app uses natural scaling — short distances stay in meters or feet, while longer distances switch to kilometers or miles automatically.

### 表示される場所

- **ノードリスト**：各ノードまでの距離と方位
- **ノードの詳細**：標高、自分の位置からの距離
- **マップ**：ウェイポイントまでの距離、ルート追跡のホップ距離
- **コンパス**：選択したノードまでの距離

## 速度

GPS の対地速度は、ロケールで優先される速度単位で表示されます。

| あなたの設定       | 表示      |
| ------------ | ------- |
| メートル法        | 12 km/h |
| ヤード・ポンド法（米国） | 7 mph   |

## 風力

Wind speed, gust and lull are transmitted by the sensor as **m/s** and converted for display — the app shows the unit weather forecasts use in your region, not the raw sensor unit.

| あなたの設定       | 表示                        |
| ------------ | ------------------------- |
| メートル法        | 18.0 km/h |
| ヤード・ポンド法（米国） | 11.2 mph  |

All three read in the same unit wherever they appear: the Node Detail environment section, the Environment Telemetry log, and the charts.

## 重さ

Readings from a connected scale are transmitted in **kg** and converted for display.

| あなたの設定       | 表示                      |
| ------------ | ----------------------- |
| メートル法        | 1.50 kg |
| ヤード・ポンド法（米国） | 3.31 lb |

## 降水量

降水量の測定値（1 時間および 24 時間の合計）は **mm** で送信され、表示用に変換されます。

| あなたの設定       | 表示                      |
| ------------ | ----------------------- |
| メートル法        | 12.0 mm |
| ヤード・ポンド法（米国） | 0.47 in |

## 変わらない単位

一部の単位は国際標準であり、ロケールに関係なく同じように表示されます：

| 計測項目          | 単位    | 理由          |
| ------------- | ----- | ----------- |
| 大気圧           | hPa   | 国際的な気象標準    |
| 方位／進行方向       | °（度）  | 普遍的な航法の慣例   |
| 放射線           | µR/h  | 標準的な線量測定の単位 |
| GPS 座標        | 十進法の度 | 普遍的な地理標準    |
| 湿度、バッテリー、土壌水分 | %     | 普遍的         |

## 日付と時刻

アプリ全体のすべてのタイムスタンプ（最後の通信、メッセージの時刻、テレメトリのログ、グラフの軸）は、デバイスの日付と時刻の設定に従います。

| 設定          | 制御する内容 | 例                                                |
| ----------- | ------ | ------------------------------------------------ |
| **24 時間表示** | 時刻の形式  | 14:30 vs 2:30 PM |
| **日付形式**    | 日付の並び順 | 09/05/2026 vs 05/09/2026                         |

The app also uses **relative time** where it makes sense — for example, "5 min ago" or "2 hours ago" in the node list — which is automatically localized into your device language.

## Changing Your Measurement System

By default the app follows your device, and your measurement system (metric vs imperial) is tied to your region setting:

1. 「**Android の設定 → システム → 言語と地域**」を開きます
2. Change your **Region**
3. Meshtastic に戻ります。値がすぐに更新されます

On Android 16+, the system-wide **Measurement system** preference overrides the region for distance, speed, and the other measurements — but not for temperature. Temperature is resolved separately, and on Android 14+ you override it on its own under **Regional preferences → Temperature**.

Not every English region is fully metric. **English (United Kingdom)** uses miles and feet for distance, so the node list shows miles and altitude in feet. For metric distances, set the app's **Units** setting to Metric (see [Overriding the Units in the App](#overriding-the-units-in-the-app)), or choose a fully metric region such as English (Canada), English (Ireland), or English (New Zealand).

Some phones do not offer the **Regional preferences** menu at all and list only English (United States). On those devices, use the app's **Units** setting (see [Overriding the Units in the App](#overriding-the-units-in-the-app)).

### Overriding the units in the app

Not every device can express every preference — some manufacturer builds ship no regional preferences at all, some
offer only one English variant, and UK regions are imperial for distance even if you'd rather read altitude in
meters. For those cases the app has its own switch:

1. Open **Meshtastic Settings → Units**
2. Choose **System default**, **Metric**, or **Imperial**
3. Every screen updates immediately — no restart needed

**System default** follows your phone's or computer's region and measurement settings. Forcing **Metric** or **Imperial** applies to
everything, temperature included (metric → °C, imperial → °F), even where the system's own regional preferences say
otherwise. The setting exists on Android and Desktop alike.

## 関連トピック

- [ノードメトリクス](node-metrics)：温度・距離・センサー値が表示される場所
- [テレメトリとセンサー](telemetry-and-sensors)：これらの計測値を生成するセンサー
- [計測と書式](../developer/measurement)：書式ユーティリティの開発者向けリファレンス
- [設定：無線機とユーザー](settings-radio-user)：単位の選択を決める地域設定
- [Display Config](https://meshtastic.org/docs/configuration/radio/display) — units, clock, and compass settings for the radio's own screen, on meshtastic.org
