---
title: Node Metrics
parent: User Guide
nav_order: 5
last_updated: 2026-05-13
description: Telemetry dashboards for each mesh node — device health, environment sensors, signal quality, power, traceroute, and position history.
aliases:
  - metrics
  - 遙測
  - device-metrics
  - signal
---

# Node Metrics

The node detail screen provides comprehensive telemetry and metrics for each node on your mesh.

## 裝置計量資料

Basic operating information reported by each node:

| 公制（公里/公尺）     | 描述說明                                |
| ------------- | ----------------------------------- |
| Battery Level | Current battery percentage          |
| 電壓            | Battery voltage reading             |
| 頻道使用量         | Percentage of airtime consumed      |
| Airtime       | Transmission time used by this node |
| 運行時間          | Time since last reboot              |

Device metrics are displayed as individual cards with trend sparklines showing battery level, voltage, channel utilization, airtime, and uptime over time.

> 💡 **Tip:** Tap any metric card to expand it into a full chart with historical data points. Pinch to zoom the time axis.

## 環境計量資料

Environmental sensor data (requires compatible hardware):

| 公制（公里/公尺）                            | Sensor Examples       |
| ------------------------------------ | --------------------- |
| 溫度                                   | BME280, BME680, SHT31 |
| 濕度                                   | BME280, BME680, SHT31 |
| 大氣壓力                                 | BME280, BMP280        |
| 氣體感測器                                | BME680                |
| IAQ (Air Quality) | BME680                |

Environment metrics are charted over time for easy trend analysis — temperature, humidity, and pressure each get their own line chart with the measurement unit displayed on the Y axis.

> 💡 **Tip:** Environment metrics require a sensor connected to the remote node. Not all nodes report environmental data. See [Telemetry & Sensors](telemetry-and-sensors) for a full list of supported sensors.

## Signal Metrics

Radio signal quality information:

| 公制（公里/公尺） | 描述說明                                                                          |
| --------- | ----------------------------------------------------------------------------- |
| SNR       | Signal-to-Noise Ratio (higher is better)                   |
| RSSI      | Received Signal Strength Indicator (closer to 0 is better) |
| 跳躍次數      | Number of mesh hops for last message                                          |

### Signal Quality Reference

| SNR Range                         | Quality   |
| --------------------------------- | --------- |
| > 10 dB                           | Excellent |
| 0 to 10 dB                        | 良好        |
| -10 to 0 dB                       | 普通        |
| < -10 dB | Poor      |

## 電源計量資料

Power management telemetry (requires INA sensor or compatible hardware):

| 公制（公里/公尺）   | 描述說明                    |
| ----------- | ----------------------- |
| Bus Voltage | Supply voltage          |
| 目前          | Power draw in milliamps |
| 電力          | Calculated wattage      |

## 路由追蹤

Traceroute shows the path a message takes through the mesh:

1. From the node detail screen, tap **Traceroute**.
2. The app sends a traceroute request to the target node.
3. Results show each hop with SNR/RSSI values.

### Reading Traceroute Results

```
You → Node A (SNR: 8.5) → Node B (SNR: 5.2) → Target
```

Each hop represents a relay node that forwarded the message.

## 定位日誌

Historical position data for nodes that share their location:

- GPS coordinates
- 海拔高度
- Speed (if moving)
- Timestamp for each position report

## 鄰近節點資訊

Shows which nodes a given node can directly hear, useful for understanding mesh topology.

## Viewing Metrics

1. Navigate to **Nodes**.
2. Tap the node you want to inspect.
3. Select the metric category from the detail tabs.

![Node detail — local device](../../assets/screenshots/nodes_detail_local.png)

The position tab shows location data for nodes that share GPS:

![Position inline content](../../assets/screenshots/nodes_position.png)

> ⚠️ **Note:** Metrics are only available when they have been reported by the remote node. Metrics update at intervals configured on each node's telemetry settings.

## Related Topics

- [Nodes](nodes) — node list, filtering, and sorting
- [Telemetry & Sensors](telemetry-and-sensors) — supported sensors and configuration
- [Signal Meter](signal-meter) — how signal quality is calculated from SNR and RSSI
- [Discovery](discovery) — traceroute details and neighbor info
- [Units & Locale](units-and-locale) — temperature, distance, and speed display formats

---

