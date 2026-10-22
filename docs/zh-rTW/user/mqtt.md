---
title: MQTT
nav_order: 11
last_updated: 2026-10-21
description: 將您的 mesh 網路橋接至網際網路 — MQTT 代理伺服器設定、加密層級與地圖回報。
aliases:
  - MQTT
  - internet-bridge
  - broker
---

# MQTT

MQTT 將您的 Meshtastic mesh 網路橋接至網際網路，實現超越無線電範圍的長距離通訊。

## 概觀

MQTT 模組可將您的節點連接至 MQTT 代理伺服器，實現以下功能：

- 透過網際網路傳送訊息至不同實體 mesh 網路上的節點
- 與智慧家庭自動化及監控系統整合
- 將節點位置發布至公開的 Meshtastic 地圖
- 自訂資料管線，用於記錄與警示通知

## 運作方式

```text
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. 訂閱相同主題的遠端閘道，將這些訊息注入其本地 mesh 網路。

## 設定

### 啟用 MQTT

1. Navigate to **Settings → Module configuration → MQTT**.
2. Turn on **MQTT Enabled**.
3. 設定代理伺服器連線：

| 設定                      | 描述說明                                                                                                                                                        | 默認                                                                                                     |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Address**             | MQTT broker hostname. Empty means the public broker                                                                                         | mqtt.meshtastic.org                                                    |
| **Username**            | 代理伺服器驗證                                                                                                                                                     | meshdev                                                                                                |
| **Password**            | 代理伺服器驗證                                                                                                                                                     | large4cats                                                                                             |
| **Encryption Enabled**  | Publish packets still encrypted with the channel key                                                                                                        | 已啟用                                                                                                    |
| **JSON output enabled** | Shown only for a node on firmware older than 2.8, or while the setting is still on. See [Message Formats](#message-formats) | 已停用                                                                                                    |
| **TLS Enabled**         | 與代理伺服器的安全連線                                                                                                                                                 | 已停用                                                                                                    |
| **Root Topic**          | 訊息的基礎主題                                                                                                                                                     | `msh`, which the node rewrites to `msh/<REGION>` when you set a region while it uses the public broker |
| **MQTT Client Proxy**   | Relay MQTT through the connected app                                                                                                                        | 已停用                                                                                                    |
| **Map Reporting**       | 將位置回報至公開地圖                                                                                                                                                  | 已停用                                                                                                    |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the status of the relay this app runs:
**Connected**, **Connecting…**, **Reconnecting…**, **Disconnected**, or **Inactive**. It reads
**Inactive** whenever the app is not relaying, which includes the normal case of a node
reaching the broker over its own Wi-Fi or Ethernet. The node's own connection to the broker is
not reported here.

When the broker accepts the connection but refuses a subscription, the status names the refused
topics and the broker's reason: _Connected, but the broker refused every topic_ when nothing was
granted, or _Connected, but the broker refused_ followed by the topics when only some were. A
broker's access-control list is the usual cause.

**Test connection** probes the broker before you commit the settings to the node, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### Find Topics Near You

The region topic (`msh/US`) carries every gateway in the region. **Find topics near you**, under
the root topic field, listens to the broker for one minute and lists the narrower roots in use
around you, with the number of nodes heard on each.

- **Near you** lists the region, then the country when the broker shows it in use (`msh/EU_868/DE`),
  then your state or province and city, named from the node's position or, failing that, the
  phone's location. The phone's geocoder turns the location into place names; the location itself
  never reaches the broker. Community roots whose names don't say where they are (`msh/US/DMV`)
  follow when they serve your area, from a directory the app refreshes from the Meshtastic API.
- **Active topics** lists every other root heard. Roots under your state or city come first, then
  roots in neighboring states, then the rest.
- **Custom topic** takes any root the node accepts: up to 31 ASCII characters, no leading or
  trailing slash, no `+` or `#`.

The finder only subscribes and publishes nothing. Picking a topic fills in the root topic field,
and nothing reaches the node until you save. It needs the LoRa region to be set. Without a
location, or on desktop, it still lists the region and every root it hears.

### MQTT Proxy in This App

If your node has no internet access of its own, it can use the app as its MQTT gateway. Turn on **MQTT Enabled** and **MQTT Client Proxy** in the module config. The app then relays MQTT traffic between the node and the broker over your phone's or computer's internet connection.

The **MQTT proxy in this app** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately, without editing and re-saving the node's MQTT configuration.

### 預設 Meshtastic 代理伺服器

社群在 mqtt.meshtastic.org 維護一個公開的代理伺服器。此伺服器供一般使用與測試之用。

When this app relays MQTT for the node, connections to that broker always use TLS on port 8883 even if **TLS Enabled** is off. That upgrade is the app's own, and the switch says so. A node that reaches the broker over its own Wi-Fi or Ethernet forces nothing: it uses **TLS Enabled** as stored, so turn it on yourself or the node connects in the clear on port 1883. For any other broker the toggle decides in both cases. A port written in the address, as in `broker.example.com:8884`, is kept; without one, TLS uses port 8883 and plain connections use 1883.

> 🔒 隱私：公開代理伺服器上的訊息，任何訂閱者均可讀取。私人通訊請務必啟用頻道加密。

### 私有代理伺服器

為了獲得更好的隱私保護與控制權，您可以自行架設 MQTT 代理伺服器：

- Mosquitto（輕量、開放原始碼）
- HiveMQ
- EMQX

請將您的節點設定為指向私有代理伺服器，並輸入相應的登入憑證。

## 地圖回報

When **Map Reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map Publish Interval** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map Reporting** off if you do not want your approximate location published.

## 上行 vs 下行

| 方向 | 描述說明                     |
| -- | ------------------------ |
| 上行 | 訊息從 mesh 網路 → MQTT 代理伺服器 |
| 下行 | 訊息從 MQTT 代理伺服器 → mesh 網路 |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## 訊息格式

Gateways publish packets as binary Meshtastic protobufs on the `/2/e/` topic, which is what node-to-node mesh bridging uses.

Firmware 2.7 and older can also publish human-readable JSON on the `/2/json/` topic for consumers outside the mesh, such as dashboards and home automation, when `json_enabled` is on. Firmware 2.8 removed JSON support and ignores the field. The app shows **JSON output enabled** only for a node on older firmware, or while the setting is still on, and while it is on the app's own MQTT proxy also subscribes to the `/2/json/` topic and decodes those payloads.

## 加密與隱私

了解分層加密模型：

1. 頻道加密在 mesh 網路上進行，發生於 MQTT 傳輸之前。若您的頻道已設定 PSK，MQTT 承載內容將已加密——代理伺服器及任何訂閱者只能看到密文。
2. **Encryption Enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. TLS 對連接至代理伺服器的 TCP 連線進行加密，防止網路層級的竊聽。

> 🔒 **Security:** The default public channel has a well-known key. 透過 MQTT 傳送的預設頻道訊息實際上等同於未加密 — 任何人均可解碼。私人通訊請務必使用自訂 PSK。

## 最佳實踐

- 在橋接至 MQTT 的頻道上使用頻道層級加密（PSK）
- Don't enable MQTT on a node with no internet access of its own unless **MQTT Client Proxy** is on (the node queues messages it can't send and wastes memory)
- 敏感部署環境請使用私有代理伺服器
- 從繁忙的 MQTT 主題下行訊息時，請留意無線電佔用時間——每則下行訊息都會消耗本地 mesh 網路的無線電佔用時間
- 若僅需遠端監控 mesh 網路而不需注入訊息，建議僅啟用上行模式

## 故障排除

### MQTT 無法連線

- **Check Wi-Fi** — the gateway node must have an active internet connection (Wi-Fi or Ethernet). MQTT 無法直接透過 LoRa 無線電連結運作。
- **Verify credentials** — with incorrect credentials, most brokers fail silently — double-check for trailing spaces.
- **Firewall** — port 1883 (MQTT) or 8883 (MQTT over TLS) must be reachable. Some networks allow only web traffic (ports 80 and 443).
- DNS 解析 — 若使用自訂代理伺服器主機名稱，請確認節點能夠正確解析該名稱。請嘗試直接使用代理伺服器的 IP 位址進行連線。

### 訊息未正常橋接

- 檢查上行／下行設定 — 若僅啟用上行，訊息只會從 mesh 網路流向 MQTT，不會反向傳送。請在接收端閘道上啟用下行功能。
- 頻道不符 — 兩個閘道必須使用相同頻道且具備相同的 PSK。不符時，訊息將以不同金鑰加密，導致對方收到的內容為亂碼。
- **Topic mismatch** — both gateways must use exactly the same root topic. On the public broker, setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root. Firmware 2.8.0 and older also rewrite a root starting with `msh` on a private broker.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the node turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **OK to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **OK to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## 相關主題

- 〔設定 — 模組與管理〕(settings-module-admin) — MQTT 模組設定參考
- 〔訊息與頻道〕(messages-and-channels) — 頻道加密與 PSK 設定
- 〔MQTT 整合指南〕(https://meshtastic.org/docs/software/integrations/mqtt) — meshtastic.org 上的完整 MQTT 說明文件
