---
title: Local Mesh Discovery
nav_order: 12
last_updated: 2026-10-21
description: Explore your mesh network — the Local Mesh Discovery scanner, traceroute paths, neighbor maps, and node discovery tools.
aliases:
  - discovery
  - local-mesh-discovery
  - mesh-探索
  - 本地-探索
  - 網路-掃描
  - traceroute
  - 鄰居資訊
---

# Local Mesh Discovery

探索工具可協助您了解 mesh 網路的連線方式——哪些節點彼此可以收到訊號、訊息所經過的路徑，以及哪裡存在瓶頸或訊號薄弱的連結。

The app offers two complementary approaches:

- **Local Mesh Discovery (Scanner)** — an automated mode that cycles your connected node through different LoRa presets, listens on each, and ranks which preset performs best at your location.
- **Manual exploration** — traceroute, Neighbor Info, and the node list, which you can use at any time to investigate specific paths and topology.

## Local Mesh Discovery (Scanner)

Local Mesh Discovery is a dedicated scanning mode that helps you find the best LoRa modem preset for your location and see which nodes are active on each preset. It cycles your connected node through one or more presets you choose, dwells on each one — listens for a set time — to collect packets, then analyzes and ranks the results.

Connect your node, then open **Settings → Advanced → Local Mesh Discovery**. On Android the **Advanced** section appears only for a locally connected node, never over remote admin, and stays grayed out until the app has finished reading the node's configuration. On a managed device its entries are disabled, except **Debug Panel**, which reads app-local logs and stays available. On desktop, Local Mesh Discovery has its own entry on the Settings screen, with no such gate.

Discovery changes your node's LoRa settings while it scans, then restores your original configuration when it finishes.

Each preset is scanned on the public mesh: the default frequency slot for that preset, decoded with the default public channel. If your node uses a custom frequency slot, the scan uses the default slot and puts your slot back when it finishes. If your primary channel has a custom name or key, the scan switches it to the default public channel before the first preset change and restores your channel when the scan ends, whether it completes, you stop it, or it fails. Channel changes don't reboot the node, so this happens at once at the start and end of the scan.

### Setting up a scan

Before starting, configure these controls:

| Control               | 描述說明                                                                                                                                                                                                                                                     |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LoRa Presets**      | Select one or more presets to scan. Discovery dwells on each selected preset in turn.                                                                                                                                    |
| **Dwell Time**        | Time to listen on each preset. Choose from 1, 5, 15, 30, 45, 60, 90, 120, or 180 minutes. Longer dwell times collect more packets and give a clearer picture, but take longer.                           |
| **Keep screen awake** | Keeps the display on for the scan. The scan itself holds a CPU wake lock for its whole run and posts a **Scanning LoRa presets…** notification, so it keeps collecting with the screen off or the app in the background. |

The **Start Scan** button stays disabled — with an explanation of why — until the scan can run. Common reasons it's disabled:

- The node is **not connected**.
- **No presets** have been selected to scan.
- The selected preset uses **2.4 GHz**, which your hardware doesn't support.

### Live progress

While a scan runs, Discovery shows its current stage:

| Stage                                                                  | What's happening                                                                                                                                                                                                                  |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Preparing scan**                                                     | Saving your current LoRa configuration and primary channel and getting ready to scan.                                                                                                                             |
| **Shifting to \<preset\>**                  | Switching the node to the next preset to test.                                                                                                                                                                    |
| **Reconnecting on \<preset\>**              | Re-establishing the connection after the preset change.                                                                                                                                                           |
| **Dwelling on \<preset\>**                  | Listening on the current preset to collect packets, with a countdown to the next step.                                                                                                                            |
| **Analyzing results**                                                  | Processing the collected packets and ranking the presets.                                                                                                                                                         |
| **Restoring home preset**                                              | Putting your original LoRa configuration back.                                                                                                                                                                    |
| **Cancelling scan**                                                    | You tapped **Stop Scan**; partial results are saved before the original preset is restored.                                                                                                                       |
| **Scan failed: \<reason\>** | The scan could not continue — most often the node didn't come back within a minute of a preset change. The results collected so far are saved, and the original preset is restored automatically. |

![Dwell countdown showing time remaining on the current preset](../../assets/screenshots/discovery_dwell_progress.png)

If a scan is interrupted — the app is closed, or the node goes away — the app restores your original preset and primary channel the next time it reconnects to that node, and tells you it has done so. Reconnect the same node to let that happen; until you do, the node stays on whichever preset and channel the scan left it on.

### Reading the results

When the scan completes, Discovery presents a per-preset result card for each preset it tested, plus an overall summary.

![Per-preset result card with ranking and collected metrics](../../assets/screenshots/discovery_preset_result.png)

Metrics include:

| 公制（公里/公尺）                                | What it tells you                                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------- |
| RF health                                | Overall quality of the radio environment on that preset.                       |
| 頻道使用率                                    | How busy the airwaves were during the dwell.                                   |
| Airtime                                  | Transmission time observed.                                                    |
| Direct vs. relayed nodes | How many mesh nodes were heard directly versus via a relay.                    |
| Bad / duplicate packets                  | Counts of corrupt and repeated packets, indicating congestion or interference. |

Additional features available from the results:

- **Scan History** — saved sessions you can revisit; view or delete past scans.
- **Discovery Map** — a map of the nodes found during the scan.
- **Report export** — export a report as a PDF on Android, or as text on other platforms.

On **Google Play** builds, Discovery can generate an on-device AI summary (Gemini Nano) of your results. F-Droid builds always use the algorithmic summary, because that flavor leaves out the proprietary ML Kit dependency, so every build gives a readable interpretation of the scan.

## Mesh Beacon

Mesh Beacon lets nodes invite others to join their mesh. A beaconing node periodically broadcasts an invitation — optionally advertising a channel, region, and modem preset — that nearby nodes can hear even before they share a configuration.

Configure it under **Settings → Module configuration → Mesh Beacon**. The entry appears only on nodes running firmware 2.8.0 or newer. A read-only **Region** row at the top of the screen shows the region the beacon advertises: that region, and the preset, are always the ones the node itself uses, so a beacon can't invite anyone onto settings your node isn't running.

- **Listen for beacons** — receive invitations broadcast by other nodes.
- **Broadcast a beacon** — periodically advertise this mesh to nearby nodes, with an optional **Message** of up to 60 bytes, a **Broadcast interval** picked from fixed intervals between 1 hour and 72 hours, and an **Offered channel** chosen from your node's own channels. The offered channel is required, and defaults to your primary channel. Over remote admin the picker offers the primary channel only.
- **Broadcast targets** — where the beacon actually transmits. The list always holds at least one row: the first is the beacon's own transmission, not an extra. Each row picks a **Channel** and a **Transmit preset**. **Add target** appends a row, and **Remove target** deletes one — removing the last row replaces it with a fresh default rather than emptying the list.

Two conditions block beacon setup:

- **The node has no region set.** The screen shows nothing but _Set your radio's region before setting up a beacon._ Set the region on **Settings → LoRa** first.
- **The node uses custom LoRa settings.** A beacon advertises a modem preset for others to join, so a node with **Use Preset** turned off has no standard preset to offer. In that state **Broadcast a beacon** can be turned off but not on, and the broadcast settings are read-only. Listening for beacons is unaffected.

Received invitations appear as **Mesh invitations** cards on the Discovery screen. Each card shows the sender's message plus the offered channel, region, preset, and signal quality, with these actions:

- **Join** — switch to the offered channel and preset (retunes the node and reboots). When the offer matches your current frequency slot, an **Add channel** action adds it without a reboot.
- **Discover** — seed a Discovery scan with the offered preset so you can survey that mesh before joining (shown only when the beacon offers a preset).
- **Dismiss** — ignore the invitation.

Channels advertised by beacons also show up in the scan setup as **Beacon channels** — select one to include it as a scan target.

An invitation to a mesh your node is already on is suppressed: no card, no notification, and no **Beacon channels** entry. A channel counts as one you already have only when both its name and its key match a channel on your node — the same name with a different key is a different mesh, so that invitation still reaches you.

## Manual exploration

The following tools are available at any time from the node list and node detail screens. Use them to investigate specific paths and build a topology picture, alongside or instead of a full scan.

### 路由追蹤

Traceroute reveals the exact path a message takes from your node to any other node on the mesh, which makes it the first tool to reach for when debugging connectivity problems.

#### 執行路由追蹤

1. 前往「節點」，並點選您要追蹤的節點。
2. On the node detail screen, find **Traceroute** in the **Telemetry** section and tap its request button. Once a result arrives, a second button on the same row opens the traceroute log, where each hop is listed with its signal quality.

#### Reading the results

路由追蹤結果的顯示方式如下：

```text
Route traced toward destination:

■ Your Node (YOUR)
⇊ 8.5 dB
■ Relay Node (RLAY)
⇊ -8.75 dB
■ Target Node (TGT1)
```

Each `⇊` line between two nodes is one relay hop, and the SNR on that line is the quality of that segment alone. The app colors it against the demodulation floor of the preset in use, not a fixed number: green above the floor, yellow within 5.5 dB below it, orange within 7.5 dB, and red beyond that. The floor is −7.5 dB on Short Fast and improves 2.5 dB per spreading-factor step, so it is −17.5 dB on Long Fast — the same SNR reads differently on different presets. See [Signal Meter](signal-meter). A request that also gets a reply adds a second block under **Route traced back to us:**.

| 判讀重點                                                        | 代表意義                                                           |
| ----------------------------------------------------------- | -------------------------------------------------------------- |
| All hops show Good SNR (green)           | 路徑狀況良好 — 訊息可穩定傳送                                               |
| One hop shows a poor SNR (orange or red) | 訊號薄弱 — 此中繼路段不穩定                                                |
| 跳躍點過多（4 個以上）                                                | 路徑過長 — 建議調整節點位置以縮短路徑                                           |
| 重試時走不同路徑                                                    | Mesh is adapting — multiple routes exist, a sign of redundancy |

Run traceroute several times over a few minutes, waiting out the 30-second cooldown between requests. If the path changes, your mesh has redundant routes, a sign of a well-connected network. A relay that didn't record itself in the route shows as **Unknown hop**.

#### 使用路由追蹤進行疑難排解

- **No Response** — The traceroute got nothing back. The target node may be offline, out of range, or on a different channel. 請確認兩個節點至少共用一個使用相同加密金鑰的頻道。
- 「路由追蹤逾時」— 路徑可能過長（超過跳躍限制），或某中繼節點發生壅塞。 Try increasing the hop limit in **Settings → LoRa**.
- **Cannot show traceroute map because the start or destination node has no position information** — The path was traced, but one end has never shared a position.
- 非對稱路徑 — 從 A → B 的路由追蹤路徑，可能與 B → A 不同。 This is normal — radio propagation isn't always symmetric.

### 鄰近節點資訊

鄰近節點資訊模組可讓每個節點廣播其可直接收到訊號的節點清單（單跳躍）。當多個節點共享各自的鄰近節點清單時，您便可拼湊出整個 mesh 網路的拓撲圖。

#### 啟用鄰近節點資訊

1. Navigate to **Settings → Module configuration → Neighbor Info**.
2. 啟用此模組。
3. Set **Update Interval**. The app accepts whatever you type; the firmware enforces its own minimum and resets a value below it.
4. Turn on **Transmit over LoRa**. Without it, your neighbor list goes only to MQTT and to this app, never over the air.

Once enabled and transmitting over LoRa, your node periodically broadcasts its neighbor list. 其他已啟用鄰近節點資訊的節點也會執行相同動作。

#### Viewing neighbor data

- Open a node's detail screen and find **Neighbor Info** in the **Telemetry** section. The row appears once the app has received that node's neighbor list. Its log button opens everything that node has reported, and its request button asks the node for a fresh list.
- 每筆鄰近節點記錄會顯示可直接收到訊號的節點及其訊號品質。
- 結合多個節點的鄰近節點資料，以了解完整的 mesh 網路拓撲。

> ℹ️ **Note:** Neighbor Info increases airtime usage because every enabled node periodically broadcasts its neighbor list. The firmware doesn't accept an interval shorter than 14400 seconds (4 hours) for this reason; on busy meshes, leave it at the 21600-second default or raise it further.

### Node list as a discovery tool

The node list's filtering and sorting make it a discovery tool of its own.

#### Finding new nodes

- 依「最後收到訊號」排序，可將最近有活動的節點顯示於頂部。
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on nodes.

#### Assessing connectivity

- 依「跳躍距離」排序，可區分可直接到達的節點（0 個跳躍點）與需中繼轉送的節點。
- 依「距離」排序，可找出附近的節點並確認是否可到達。
- Use **Exclude MQTT** to focus on nodes reachable over LoRa (not via internet bridge).

#### Infrastructure audit

- Disable **Exclude infrastructure** to see Router, Router Late, and Client Base nodes.
- 檢查其訊號品質與最後收到訊號的時間，以確認基礎架構節點運作正常。

請參閱〔節點〕(nodes) 以了解完整的篩選與排序選項說明。

## Tips for Mesh exploration

- 從路由追蹤開始 — 可立即取得特定路徑的具體可行資訊。
- **Enable Neighbor Info on key nodes** — especially routers, to build a picture of the backbone.
- 查看地圖 —〔地圖〕(map-and-waypoints) 上的節點位置結合訊號資料，有助於了解為何某些連結訊號強，而其他連結訊號弱。
- 比較訊號變化趨勢 — 請參閱〔訊號儀表〕(signal-meter) 指南，以正確解讀 SNR 與 RSSI 數值。

## 相關主題

- [Nodes](nodes) — the node list these scans populate
- [Map & Waypoints](map-and-waypoints) — see discovered nodes geographically
- [Signal Meter](signal-meter) — interpret the SNR and RSSI a scan reports
- [Settings — Modules & Admin](settings-module-admin) — configure the Mesh Beacon and Neighbor Info modules
- [Messages & Channels](messages-and-channels) — join a mesh you found and start talking
