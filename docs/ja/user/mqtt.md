---
title: MQTT
nav_order: 11
last_updated: 2026-10-21
description: メッシュをインターネットに橋渡しします。MQTT サーバーの設定、暗号化の各レイヤー、マップ報告について説明します。
aliases:
  - mqtt
  - internet-bridge
  - broker
---

# MQTT

MQTT は、Meshtastic のメッシュネットワークをインターネットに橋渡しし、無線の到達範囲を超えた長距離通信を可能にします。

## 概要

MQTT モジュールは、ノードを MQTT サーバーに接続し、次のことを可能にします：

- インターネット経由で、物理的に異なるメッシュ上のノードにメッセージを届ける
- ホームオートメーションや監視システムとの連携
- ノードの位置を公開の Meshtastic マップに公開する
- ログ記録や通知のためのカスタムデータパイプライン

## 仕組み

```text
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. 同じトピックを購読しているリモートのゲートウェイが、それらのメッセージを自分のローカルメッシュに取り込みます。

## 設定

### MQTT を有効にする

1. Navigate to **Settings → Module configuration → MQTT**.
2. Turn on **MQTT Enabled**.
3. サーバーへの接続を設定します：

| 設定項目                    | 説明                                                                                                                                                          | デフォルト                                                                                                  |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Address**             | MQTT broker hostname. Empty means the public broker                                                                                         | mqtt.meshtastic.org                                                    |
| **Username**            | サーバーの認証                                                                                                                                                     | meshdev                                                                                                |
| **Password**            | サーバーの認証                                                                                                                                                     | large4cats                                                                                             |
| **Encryption Enabled**  | Publish packets still encrypted with the channel key                                                                                                        | 有効                                                                                                     |
| **JSON output enabled** | Shown only for a node on firmware older than 2.8, or while the setting is still on. See [Message Formats](#message-formats) | 無効                                                                                                     |
| **TLS Enabled**         | サーバーへのセキュアな接続                                                                                                                                               | 無効                                                                                                     |
| **Root Topic**          | メッセージのベーストピック                                                                                                                                               | `msh`, which the node rewrites to `msh/<REGION>` when you set a region while it uses the public broker |
| **MQTT Client Proxy**   | Relay MQTT through the connected app                                                                                                                        | 無効                                                                                                     |
| **Map Reporting**       | 公開マップに位置を報告                                                                                                                                                 | 無効                                                                                                     |

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

### デフォルトの Meshtastic サーバー

コミュニティが `mqtt.meshtastic.org` で公開サーバーを運用しています。これは一般的な利用やテストを目的としています。

When this app relays MQTT for the node, connections to that broker always use TLS on port 8883 even if **TLS Enabled** is off. That upgrade is the app's own, and the switch says so. A node that reaches the broker over its own Wi-Fi or Ethernet forces nothing: it uses **TLS Enabled** as stored, so turn it on yourself or the node connects in the clear on port 1883. For any other broker the toggle decides in both cases. A port written in the address, as in `broker.example.com:8884`, is kept; without one, TLS uses port 8883 and plain connections use 1883.

> 🔒 **プライバシー：** 公開サーバー上のメッセージは、購読している誰もが読めます。プライベートな通信には、必ずチャンネルの暗号化を使用してください。

### プライベートサーバー

プライバシーと制御を高めるために、独自の MQTT サーバーを運用できます：

- Mosquitto（軽量、オープンソース）
- HiveMQ
- EMQX

適切な認証情報を設定して、ノードがプライベートサーバーに接続するよう構成します。

## マップ報告

When **Map Reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map Publish Interval** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map Reporting** off if you do not want your approximate location published.

## アップリンクとダウンリンク

| 方向         | 説明                      |
| ---------- | ----------------------- |
| **アップリンク** | メッシュ → MQTT サーバーへのメッセージ |
| **ダウンリンク** | MQTT サーバー → メッシュへのメッセージ |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## メッセージ形式

Gateways publish packets as binary Meshtastic protobufs on the `/2/e/` topic, which is what node-to-node mesh bridging uses.

Firmware 2.7 and older can also publish human-readable JSON on the `/2/json/` topic for consumers outside the mesh, such as dashboards and home automation, when `json_enabled` is on. Firmware 2.8 removed JSON support and ignores the field. The app shows **JSON output enabled** only for a node on older firmware, or while the setting is still on, and while it is on the app's own MQTT proxy also subscribes to the `/2/json/` topic and decodes those payloads.

## 暗号化とプライバシー

階層化された暗号化モデルを理解する：

1. **チャンネルの暗号化**は、MQTT &#x306E;_&#x524D;&#x306B;_&#x30E1;ッシュ上で行われます。チャンネルに PSK が設定されている場合、MQTT ペイロードはすでに暗号化されています。サーバーや購読者には暗号文しか見えません。
2. **Encryption Enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** はサーバーへの TCP 接続自体を暗号化し、ネットワークレベルの盗聴を防ぎます。

> 🔒 **Security:** The default public channel has a well-known key. デフォルトチャンネルで MQTT 経由で送信されるメッセージは、実質的に**暗号化されていません**。誰でも解読できます。プライベートな通信には、必ずカスタムの PSK を使用してください。

## ベストプラクティス

- MQTT に橋渡しするチャンネルでは、チャンネルレベルの暗号化（PSK）を使用します
- Don't enable MQTT on a node with no internet access of its own unless **MQTT Client Proxy** is on (the node queues messages it can't send and wastes memory)
- 機密性の高い運用にはプライベートサーバーを使用します
- 混雑した MQTT トピックからメッセージをダウンリンクする際は、電波利用時間に注意してください。ダウンリンクされたメッセージはすべて、ローカルメッシュの無線の電波利用時間を消費します
- メッセージを送り返さず、リモートでメッシュを監視するだけでよい場合は、アップリンクのみを有効にすることを検討してください

## トラブルシューティング

### MQTT が接続できない

- **Check Wi-Fi** — the gateway node must have an active internet connection (Wi-Fi or Ethernet). MQTT は LoRa の無線リンク自体では動作しません。
- **Verify credentials** — with incorrect credentials, most brokers fail silently — double-check for trailing spaces.
- **Firewall** — port 1883 (MQTT) or 8883 (MQTT over TLS) must be reachable. Some networks allow only web traffic (ports 80 and 443).
- **DNS 解決：** カスタムのサーバーホスト名を使う場合は、ノードがそれを解決できるか確認してください。サーバーの IP アドレスを直接試してみてください。

### メッセージが橋渡しされない

- **アップリンク／ダウンリンクの設定を確認：** アップリンクのみが有効な場合、メッセージはメッシュから MQTT へ流れますが、戻ってきません。受信側のゲートウェイでダウンリンクを有効にしてください。
- **チャンネルの不一致：** 両方のゲートウェイが、同じ PSK を持つ同じチャンネルを共有している必要があります。不一致の場合、メッセージは異なる鍵で暗号化され、判読できないデータとして表示されます。
- **Topic mismatch** — both gateways must use exactly the same root topic. On the public broker, setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root. Firmware 2.8.0 and older also rewrite a root starting with `msh` on a private broker.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the node turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **OK to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **OK to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## 関連トピック

- [設定：モジュールと管理](settings-module-admin)：MQTT モジュールの設定リファレンス
- [メッセージとチャンネル](messages-and-channels)：チャンネルの暗号化と PSK の設定
- [MQTT 連携ガイド](https://meshtastic.org/docs/software/integrations/mqtt)：meshtastic.org にある詳細な MQTT ドキュメント
