---
title: Local Mesh Discovery
parent: 使用者指南
nav_order: 12
last_updated: 2026-08-30
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

- **Local Mesh Discovery (Scanner)** — an automated mode that cycles your connected radio through different LoRa presets, listens on each, and ranks which preset performs best at your location.
- **Manual exploration** — traceroute, Neighbor Info, and the node list, which you can use at any time to investigate specific paths and topology.

## Local Mesh Discovery (Scanner)

Local Mesh Discovery is a dedicated scanning mode that helps you find the best LoRa modem preset for your location and see which nodes are active on each preset. It cycles your connected radio through one or more presets you choose, dwells on each one — listens for a set time — to collect packets, then analyzes and ranks the results.

Connect your radio, then open **Settings → Advanced → Local Mesh Discovery**. On Android the **Advanced** section stays grayed out until a radio is connected and the app has finished reading its configuration, and every entry in it is disabled on a managed device. On desktop, Local Mesh Discovery has its own entry on the Settings screen, with no such gate.

> ℹ️ **Note:** Discovery temporarily changes your radio's LoRa settings while it scans, then restores your original configuration when it finishes.

### Setting Up a Scan

Before starting, configure these controls:

| Control                | 描述說明                                                                                                                                                                                                                           |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **LoRa preset picker** | Select one or more presets to scan. Discovery dwells on each selected preset in turn.                                                                                                          |
| **Dwell time**         | Time to listen on each preset. Choose from 1, 5, 15, 30, 45, 60, 90, 120, or 180 minutes. Longer dwell times collect more packets and give a clearer picture, but take longer. |
| **Keep screen awake**  | Keeps the phone out of Android Doze mode, which would otherwise drop radio packets during a long scan. Recommended — a scan run with it off can under-count what the radio heard.              |

The **Start Scan** button stays disabled — with an explanation of why — until the scan can run. Common reasons it's disabled:

- The radio is **not connected**.
- **No presets** have been selected to scan.
- The selected preset uses **2.4 GHz**, which your hardware doesn't support.

### Live Progress

While a scan runs, Discovery shows its current stage:

| Stage                                                                  | What's happening                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Preparing scan**                                                     | Saving your current configuration and getting ready to scan.                                                                                                                                                        |
| **Shifting to \<preset\>**                  | Switching the radio to the next preset to test.                                                                                                                                                                     |
| **Reconnecting on \<preset\>**              | Re-establishing the connection after the preset change.                                                                                                                                                             |
| **Dwelling on \<preset\>**                  | Listening on the current preset to collect packets, with a countdown to the next step.                                                                                                                              |
| **Analyzing results**                                                  | Processing the collected packets and ranking the presets.                                                                                                                                                           |
| **Restoring home preset**                                              | Putting your original LoRa configuration back.                                                                                                                                                                      |
| **Cancelling scan**                                                    | You tapped **Stop Scan**; partial results are saved before the original preset is restored.                                                                                                                         |
| **Scan failed: \<reason\>** | The scan could not continue — most often the radio did not come back within a minute of a preset change. The results collected so far are saved, and the original preset is restored automatically. |

![Dwell countdown showing time remaining on the current preset](../../assets/screenshots/discovery_dwell_progress.png)

If a scan is interrupted — the app is closed, or the radio goes away — the app restores your original preset the next time it reconnects to that radio, and tells you it has done so. Reconnect the same radio to let that happen; until you do, the radio stays on whichever preset the scan left it on.

### 解讀結果

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

> 💡 **Tip:** On **Google Play** builds, Discovery can generate an on-device AI summary (Gemini Nano) of your results. F-Droid builds always use the algorithmic summary — the proprietary ML Kit dependency is deliberately excluded from that flavor — so you get a readable interpretation of the scan either way.

## Mesh Beacon

Mesh Beacon lets nodes invite others to join their mesh. A beaconing node periodically broadcasts an invitation — optionally advertising a channel, region, and modem preset — that nearby nodes can hear even before they share a configuration.

Configure it under **Settings → Module configuration → Mesh Beacon**. The entry appears only on radios running firmware 2.8.0 or newer. A read-only **Region** row at the top of the screen shows the region the beacon advertises: that region, and the preset, are always the ones the radio itself uses, so a beacon cannot invite anyone onto settings your radio is not running.

- **Listen for beacons** — receive invitations broadcast by other nodes.
- **Broadcast a beacon** — periodically advertise this mesh to nearby nodes, with an optional **Beacon message** of up to 100 bytes, a **Broadcast interval** picked from fixed intervals between 1 hour and 72 hours, and an **Offered channel** chosen from your radio's own channels. The offered channel is required, and defaults to your primary channel.
- **Broadcast targets** — optional extra destinations beyond the offered channel. **Add target** appends a row; each row picks a **Channel** and a **Transmit preset**, and **Remove target** deletes it. With no targets, the beacon goes out on the offered channel alone.

Two conditions block beacon setup:

- **The radio has no region set.** The screen shows nothing but _Set your radio's region before setting up a beacon._ Set the region on **Settings → LoRa** first.
- **The radio uses custom LoRa settings.** A beacon advertises a modem preset for others to join, so a radio with **Use Preset** turned off has no standard preset to offer. In that state **Broadcast a beacon** can be turned off but not on, and the broadcast settings are read-only. Listening for beacons is unaffected.

Received invitations appear as **Mesh invitations** cards on the Discovery screen. Each card shows the sender's message plus the offered channel, region, preset, and signal quality, with these actions:

- **Join** — switch to the offered channel and preset (retunes the radio and reboots). When the offer matches your current frequency slot, an **Add channel** action adds it without a reboot.
- **Discover** — seed a Discovery scan with the offered preset so you can survey that mesh before joining (shown only when the beacon offers a preset).
- **Dismiss** — ignore the invitation.

Channels advertised by beacons also show up in the scan setup as **Beacon channels** — select one to include it as a scan target.

An invitation to a mesh your radio is already on is suppressed: no card, no notification, and no **Beacon channels** entry. A channel counts as one you already have only when both its name and its key match a channel on your radio — the same name with a different key is a different mesh, so that invitation still reaches you.

## Manual Exploration

The following tools are available at any time from the node list and node detail screens. Use them to investigate specific paths and build a topology picture, alongside or instead of a full scan.

### 路由追蹤

路由追蹤可顯示訊息從您的節點到 mesh 網路上任一節點所經過的確切路徑。 這是診斷連線問題最有效的工具。

#### 執行路由追蹤

1. 前往「節點」，並點選您要追蹤的節點。
2. On the node detail screen, find **Traceroute** in the **Telemetry** section and tap its request button. Once a result arrives, a second button on the same row opens the traceroute log, where each hop is listed with its signal quality.

#### 解讀結果

路由追蹤結果的顯示方式如下：

```text
Route traced toward destination:

■ Your Node (YOUR)
⇊ 8.5 dB
■ Relay Node (RLAY)
⇊ -8.75 dB
■ Target Node (TGT1)
```

Each `⇊` line between two nodes is one relay hop, and the SNR on that line is the quality of that segment alone. The app colors it green at or above −7 dB, yellow at or above −15 dB, and orange below that. A request that also gets a reply adds a second block under **Route traced back to us:**.

| 判讀重點                                                               | 代表意義                           |
| ------------------------------------------------------------------ | ------------------------------ |
| All hops show Good SNR (≥ −7 dB, green)         | 路徑狀況良好 — 訊息可穩定傳送               |
| One hop shows a poor SNR (below −15 dB, orange) | 訊號薄弱 — 此中繼路段不穩定                |
| 跳躍點過多（4 個以上）                                                       | 路徑過長 — 建議調整節點位置以縮短路徑           |
| 重試時走不同路徑                                                           | Mesh 網路正在自動調整 — 存在多條路由（這是好現象！） |

> 💡 提示：請在幾分鐘內多次執行路由追蹤。 若路徑發生變化，代表您的 mesh 網路具備備援路由 — 這是網路連線良好的象徵。

#### 使用路由追蹤進行疑難排解

- **No Response** — The traceroute got nothing back. The target node may be offline, out of range, or on a different channel. 請確認兩個節點至少共用一個使用相同加密金鑰的頻道。
- 「路由追蹤逾時」— 路徑可能過長（超過跳躍限制），或某中繼節點發生壅塞。 Try increasing the hop limit in **Settings → LoRa**.
- **Cannot show traceroute map because the start or destination node has no position information** — The path was traced, but one end has never shared a position.
- 非對稱路徑 — 從 A → B 的路由追蹤路徑，可能與 B → A 不同。 這屬於正常現象 — 無線電訊號的傳播並不總是對稱的。

### 鄰近節點資訊

鄰近節點資訊模組可讓每個節點廣播其可直接收到訊號的節點清單（單跳躍）。 當多個節點共享各自的鄰近節點清單時，您便可拼湊出整個 mesh 網路的拓撲圖。

#### 啟用鄰近節點資訊

1. Navigate to **Settings → Module configuration → Neighbor Info**.
2. 啟用此模組。
3. Set **Update interval (seconds)**. The default is 21600 seconds (6 hours), and the firmware minimum is 14400 seconds (4 hours) — a smaller value is rejected and reset to the default.
4. Turn on **Transmit over LoRa**. Without it, your neighbor list goes only to MQTT and to this app, never over the air. It is unavailable on a channel that still uses the default name and key, so set up your own channel first — see [Messages & Channels](messages-and-channels).

Once enabled and transmitting over LoRa, your node periodically broadcasts its neighbor list. 其他已啟用鄰近節點資訊的節點也會執行相同動作。

#### 檢視鄰近節點資料

- Open a node's detail screen and find **Neighbor Info** in the **Telemetry** section. The request button asks the node for its current neighbor list; once the app has received one, a second button on the same row opens the log of everything that node has reported. The row appears only on nodes that can answer a neighbor request, or that have already reported neighbors.
- 每筆鄰近節點記錄會顯示可直接收到訊號的節點及其訊號品質。
- 結合多個節點的鄰近節點資料，以了解完整的 mesh 網路拓撲。

> ℹ️ **Note:** Neighbor Info increases airtime usage because every enabled node periodically broadcasts its neighbor list. The firmware does not accept an interval shorter than 14400 seconds (4 hours) for this reason; on busy meshes, leave it at the 21600-second default or raise it further.

### 將節點清單作為探索工具

善用節點清單的篩選與排序功能，即可將其作為強大的探索工具。

#### 尋找新節點

- 依「最後收到訊號」排序，可將最近有活動的節點顯示於頂部。
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on radios.

#### 評估連線狀況

- 依「跳躍距離」排序，可區分可直接到達的節點（0 個跳躍點）與需中繼轉送的節點。
- 依「距離」排序，可找出附近的節點並確認是否可到達。
- 使用「排除 MQTT」，可專注於透過無線電（而非網際網路橋接）可到達的節點。

#### 基礎架構稽核

- Disable **Exclude infrastructure** to see Router, Router Late, and Client Base nodes.
- 檢查其訊號品質與最後收到訊號的時間，以確認基礎架構節點運作正常。

請參閱〔節點〕(nodes) 以了解完整的篩選與排序選項說明。

## Mesh 網路探索技巧

- 從路由追蹤開始 — 可立即取得特定路徑的具體可行資訊。
- 在關鍵節點上啟用鄰近節點資訊 — 尤其是路由器與中繼器，以建立骨幹網路的整體概況。
- 查看地圖 —〔地圖〕(map-and-waypoints) 上的節點位置結合訊號資料，有助於了解為何某些連結訊號強，而其他連結訊號弱。
- 比較訊號變化趨勢 — 請參閱〔訊號儀表〕(signal-meter) 指南，以正確解讀 SNR 與 RSSI 數值。

## 相關主題

- [Nodes](nodes) — the node list these scans populate
- [Map & Waypoints](map-and-waypoints) — see discovered nodes geographically
- [Signal Meter](signal-meter) — interpret the SNR and RSSI a scan reports
- [Settings — Modules & Admin](settings-module-admin) — configure the Mesh Beacon and Neighbor Info modules
- [Messages & Channels](messages-and-channels) — join a mesh you found and start talking
