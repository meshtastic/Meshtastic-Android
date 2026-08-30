---
title: Meshtastic の信号メーターの仕組み
parent: User Guide
nav_order: 15
last_updated: 2026-08-29
description: 信号メーターが、LoRa モデムプリセットに対する SNR から品質をどう評価するかを説明します。スペクトラム拡散、プリセット、バーが実際に意味するもの。
aliases:
  - signal
  - signal-meter
  - snr
  - rssi
---

# Meshtastic の信号メーターの仕組み

The Meshtastic signal meter — the bars or status color next to a node — is calculated differently from the bars on a cell phone or Wi-Fi router. This page explains what it measures and why the same reading can mean something different on another preset.

## RSSI and SNR

Every time the LoRa radio receives a message, it reports two measurements:

- **RSSI (Received Signal Strength Indicator)** — the raw power hitting the antenna.
- **SNR (Signal-to-Noise Ratio)** — how far the signal stands above the background noise.

> 💡 **Tip:** Think of RSSI as how loud a friend is talking and SNR as how easily you can pick their voice out of the noise in the room. A friend shouting at a rock concert can be loud (high RSSI) yet unintelligible (bad SNR), while a whisper in a quiet library is faint (low RSSI) yet perfectly clear (great SNR).

## Decoding Below the Noise Floor

Standard radios such as FM or Wi-Fi lose a signal to static once the background noise is louder than it (a negative SNR). LoRa's spread spectrum modulation lets the radio pull a signal out of the noise even when the noise is louder, so negative SNR values are common and expected in Meshtastic — for example, −10 dB means the signal is 10 decibels weaker than the background noise.

Each modem preset has an SNR limit: the lowest SNR at which that preset can still decode a message. Slower presets tolerate a weaker, noisier signal (a more negative limit, longer range); faster presets need a stronger signal (a less negative limit, shorter range).

## Rating Signal Quality

The app rates signal quality (None, Bad, Fair, or Good) from SNR alone, measured against the active preset's SNR limit. It does not factor in RSSI: without knowing the local noise floor, RSSI alone cannot say whether a signal is decodable. RSSI is still available — on the node detail screen and in the metrics charts.

Because the rating is relative to the preset, the same SNR rates differently on different presets. An SNR of −16 dB rates Good on Long Fast (SNR limit −17.5 dB) but None on Short Fast (SNR limit −7.5 dB). Let `limit` be the active preset's SNR limit:

| レベル | バー | 基準                                                             | 意味                                                                                                  |
| --- | -- | -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 良   | 3  | SNR above `limit`                                              | Comfortably above the demodulation floor — a healthy connection.                    |
| 普通  | 2  | less than 5.5 dB below `limit`                 | Decodable, but approaching the floor.                                               |
| 不良  | 1  | 5.5 dB to 7.5 dB below `limit` | At the edge of what the preset can recover.                                         |
| なし  | 0  | more than 7.5 dB below `limit`                 | Far below the preset's floor; further packets from this node are likely to be lost. |

> ℹ️ **Note:** Traceroute hop colors use fixed thresholds (−7 dB / −15 dB); the per-node signal meter uses the preset-relative rating instead.

## Diagnosing Local Interference

A great RSSI paired with only one bar (Bad) points to local interference, not distance. A cheap power supply, a noisy computer, or a nearby transmitter can create enough static to drown out an otherwise strong signal.

## 信号情報が表示される場所

In the app, signal data appears in several places:

- **Node list** — a signal-bars icon next to each node
- **ノードの詳細**：デバイスメトリクスのセクションにある SNR、RSSI、信号品質
- **ルート追跡**：各中継ノードの、ホップごとの信号品質
- **信号メトリクス**：メトリクスのグラフにある SNR と RSSI の履歴データ

![Node list entry showing a Good signal rating: 12.5 dB SNR, −42 dBm RSSI, and the green signal-strength icon](../../assets/screenshots/nodes_signal_info.png)

## 関連トピック

- [ノード](nodes)：ノードリストで信号バーが表示される場所
- [ノードメトリクス](node-metrics)：SNR／RSSI の履歴と、ノードごとの信号品質の目安
- [設定：無線機とユーザー](settings-radio-user)：モデムプリセットとその SNR 限界
