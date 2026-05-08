---
title: MQTT
nav_order: 11
aliases:
  - mqtt
  - internet-bridge
  - broker
---

# MQTT

MQTT bridges your Meshtastic mesh network to the internet, enabling long-range communication beyond radio range.

## Overview

The MQTT module connects your node to an MQTT broker, allowing:
- Messages to reach nodes on different physical meshes via the internet
- Integration with home automation and monitoring systems
- Publishing node positions to the public Meshtastic map
- Custom data pipelines for logging and alerting

## How It Works

```
[Your Node] → Radio → [Gateway Node with WiFi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (WiFi or Ethernet) publishes mesh messages to an MQTT topic. Remote gateways subscribed to the same topic inject those messages into their local mesh.

## Configuration

### Enabling MQTT

1. Navigate to **Settings → Module Config → MQTT**.
2. Enable the MQTT module.
3. Configure the broker connection:

| Setting | Description | Default |
|---------|-------------|---------|
| Server Address | MQTT broker hostname | mqtt.meshtastic.org |
| Username | Broker authentication | meshdev |
| Password | Broker authentication | large4cats |
| Root Topic | Base topic for messages | msh |
| Encryption | Encrypt MQTT payload | Enabled |
| JSON Output | Publish JSON alongside protobuf | Disabled |
| TLS | Secure connection to broker | Disabled |
| Map Reporting | Report position to public map | Disabled |

### Default Meshtastic Broker

The community maintains a public broker at `mqtt.meshtastic.org`. This is intended for general use and testing.

> 🔒 **Privacy:** Messages on the public broker are readable by anyone subscribed. Always use channel encryption for private communications.

### Private Broker

For better privacy and control, you can run your own MQTT broker:
- Mosquitto (lightweight, open-source)
- HiveMQ
- EMQX

Configure your node to point to your private broker with appropriate credentials.

## Map Reporting

When Map Reporting is enabled, your node publishes its position to the Meshtastic community map:
- Visible at [meshmap.net](https://meshmap.net) and similar community map services
- Only position and node info are shared
- Disable this if you don't want your location publicly visible

## Uplink vs Downlink

| Direction | Description |
|-----------|-------------|
| Uplink | Messages from mesh → MQTT broker |
| Downlink | Messages from MQTT broker → mesh |

Configure per-channel which directions are active to control message flow.

## Best Practices

- Use encryption on channels that bridge to MQTT
- Don't enable MQTT on nodes without internet access (it will buffer and waste memory)
- Use a private broker for sensitive deployments
- Be mindful of airtime when downlinking messages from busy MQTT topics

---

*Screenshots will be added when the screenshot automation pipeline is operational.*

