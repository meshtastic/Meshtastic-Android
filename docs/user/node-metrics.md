---
title: Node Metrics
nav_order: 5
aliases:
  - metrics
  - telemetry
  - device-metrics
  - signal
---

# Node Metrics

The node detail screen provides comprehensive telemetry and metrics for each node on your mesh.

## Device Metrics

Basic operating information reported by each node:

| Metric | Description |
|--------|-------------|
| Battery Level | Current battery percentage |
| Voltage | Battery voltage reading |
| Channel Utilization | Percentage of airtime consumed |
| Airtime | Transmission time used by this node |
| Uptime | Time since last reboot |

## Environment Metrics

Environmental sensor data (requires compatible hardware):

| Metric | Sensor Examples |
|--------|-----------------|
| Temperature | BME280, BME680, SHT31 |
| Humidity | BME280, BME680, SHT31 |
| Barometric Pressure | BME280, BMP280 |
| Gas Resistance | BME680 |
| IAQ (Air Quality) | BME680 |

> 💡 **Tip:** Environment metrics require a sensor connected to the remote node. Not all nodes report environmental data.

## Signal Metrics

Radio signal quality information:

| Metric | Description |
|--------|-------------|
| SNR | Signal-to-Noise Ratio (higher is better) |
| RSSI | Received Signal Strength Indicator (closer to 0 is better) |
| Hop Count | Number of mesh hops for last message |

### Signal Quality Reference

| SNR Range | Quality |
|-----------|---------|
| > 10 dB | Excellent |
| 0 to 10 dB | Good |
| -10 to 0 dB | Fair |
| < -10 dB | Poor |

## Power Metrics

Power management telemetry (requires INA sensor or compatible hardware):

| Metric | Description |
|--------|-------------|
| Bus Voltage | Supply voltage |
| Current | Power draw in milliamps |
| Power | Calculated wattage |

## Traceroute

Traceroute shows the path a message takes through the mesh:

1. From the node detail screen, tap **Traceroute**.
2. The app sends a traceroute request to the target node.
3. Results show each hop with SNR/RSSI values.

### Reading Traceroute Results

```
You → Node A (SNR: 8.5) → Node B (SNR: 5.2) → Target
```

Each hop represents a relay node that forwarded the message.

## Position Log

Historical position data for nodes that share their location:
- GPS coordinates
- Altitude
- Speed (if moving)
- Timestamp for each position report

## Neighbor Info

Shows which nodes a given node can directly hear, useful for understanding mesh topology.

## Viewing Metrics

1. Navigate to **Nodes**.
2. Tap the node you want to inspect.
3. Select the metric category from the detail tabs.

> ⚠️ **Note:** Metrics are only available when they have been reported by the remote node. Metrics update at intervals configured on each node's telemetry settings.

---

*Screenshots will be added when the screenshot automation pipeline is operational.*

