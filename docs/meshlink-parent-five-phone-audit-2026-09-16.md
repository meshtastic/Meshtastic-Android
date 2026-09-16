# 五支手機 NTsocial／MeshLink 整合實測 — 2026-09-16

> 後續使用者已授權修復與升級；本報告保留修復前證據，最新結果見 [整合修復報告](meshlink-parent-gateway-repair-2026-09-16.md)。
本輪結論：**目前不能判定整合完整。Android 主 radio 已有原生文字與 PRIVATE_APP 的實際 RF 證據；Android 第二端點有明確派送缺口；iOS 原生頻道 UI 尚未接完整，iOS 疊加訊息則停在本機接受／待送，跨平台雙向母程式收訖未通過。**

這是現有安裝版本的實機稽核，沒有修改或重裝兩個產品 App。使用者手動配對後明確表示「可以接手」，才恢復控制。Windows 不在本輪實機範圍內。

## 裝置與版本

| 代號 | 手機／控制方式 | 接手後的 radio 配置 | 本輪可驗證範圍 |
| --- | --- | --- | --- |
| A | OPPO CPH2695，Android 16，USB ADB | 未選擇 radio | App 啟動、母程式新指令回覆、無 radio 狀態 |
| B | S24 Ultra SM-S9280，Android 16，Wi-Fi ADB | 0809 主端點、5d6e 第二端點 | 雙 radio 目錄、原生發送、第二端點發送、疊加備援與 RF |
| C | S22 Ultra SM-S9080，Android 16，Wi-Fi ADB | MeshLink 尚在首次啟用流程 | 母程式控制、未連線狀態；未建立 RF 測試條件 |
| I1 | iPhone 15，Xcode Wi-Fi | 1407，Apple Gateway 主端點 | 目錄、原生接收、社群綁定、Apple Gateway 接受、重連 |
| I2 | iPhone 15，Xcode Wi-Fi | 未選擇 radio | App 啟動、母程式新指令回覆、無 radio 狀態 |

- 三支 Android：NTsocial `com.ntsocial.android.debug` **1.6.1 (41)**；MeshLink `com.ntsocial.meshlink.google.debug` **1.0.8 (9)**。
- 兩支 iPhone：NTsocial `com.ntsocial.ios` **1.0.0 (1)**；MeshLink `com.ntsocial.meshlink.ios` **1.0.0 (2)**。CoreDevice 列示兩個 App 使用同一 `group.com.ntsocial.gateway`。
- Android 母程式安裝更新時間為 9 月 15 日，MeshLink 為 9 月 11 日。這些版本資訊不是完整的安裝二進位檔與 Git revision 對應證明。
- 原始碼交叉檢查：MeshLink `3c5cb824ddafd11cfe4611a4a7eb0e30e72f3d81`；外部母程式 `../NTsocial_release` 的 `be7299ef2f2dc4611fddc90f334214d33dce369c`。兩者產品原始碼均未修改。

五支手機可控制，不等於五支都有可用 radio。本輪實際是兩支手機上的三顆 radio；不能把另外三支手機列為 RF 通過。

## 實測結果

| 測項 | 結果 | 證據及限制 |
| --- | --- | --- |
| 五支母程式控制 | 通過 | Android A/B/C 有本輪新 `GET_NODE_INFO` 回覆；I1/I2 有新 request ID 的 `command_applied` 與遞增 heartbeat |
| Android 雙端點目錄 | 通過 | B 母程式顯示兩個端點及 13 個原生目錄列；端點及 slot 分開保存 |
| Android 主端點原生文字 → iPhone MeshLink | RF 通過 | 相同文字、packet ID、stable source-message ID；iPhone 記錄 `RECEIVED`，`viaMqtt=false` |
| Android 第二端點原生發送 | 未通過 | 母程式留下待送項目，MeshLink 無該文字的 durable packet；source 的實際注入 repository 會拒絕發送 |
| iOS 原生頻道正常 UI 入口 | 未通過 | 有 8 個投影，但正常清單只列已加入社群頻道；native send adapter 沒有產品 UI 呼叫點 |
| iOS 社群頻道綁定 | 通過 | 1407 恢復後，母程式由離線變為 `Gateway READY`，可選 NTsocial slot 3 並保存綁定 |
| iOS 母程式 → Apple Gateway 本機接受 | 通過 | MeshLink 私有 ledger 有 4 筆 `ACCEPTED`，對應 Room 中 4 筆 port 256 packet |
| iOS 疊加訊息 → 遠端 RF／母程式 | 未通過 | 4 筆持續 `QUEUED`；B 兩個 radio 的 FromRadio 紀錄沒有這 4 個 ID。其後 1407 重連為 `Error`，軟硬體原因尚未分離 |
| Android 社群頻道，Internet 正常 | 依設計走雲端 | 沒有立即產生 LoRa packet，符合目前 cloud-first 路徑，不能據此判錯 |
| Android 社群頻道，Internet 關閉後備援 | 本機派送與 RF 通過 | B 主端點新增 20 筆 port 256 `ENROUTE`；5d6e 的 FromRadio 收到全部 20 個 ID，payload SHA-256 全數一致，transport=LoRa、非 MQTT |
| A/C/I2 無 radio | 狀態符合條件 | A/C 母程式顯示未連線；I2 目錄／綁定不可用。沒有把這些狀態當成 RF 成功 |

### 1. Android 主 radio 的原生 RF 路徑確實可用

唯一一次 UI 傳送的測試文字為 `NTS0907-0916-B-I1-N01`。packet ID 為 **21997241**，stable source-message ID 為 **F80F5235385013E5D486604B54CFD768**。

| 位置 | port／slot | 狀態 |
| --- | --- | --- |
| B 主端點 Room | port 1，slot 7 | `DELIVERED`，routing error 0，具有 origin client ID |
| I1 的 1407 Room | port 1，slot 3 | `RECEIVED`，相同 packet ID／文字／stable ID，`viaMqtt=false` |
| B 第二端點 Room | port 1，slot 4 | `RECEIVED`，相同 packet ID／文字／stable ID，`viaMqtt=false` |

母程式 B 的原生出口 ledger 也已完成。此項證明到達接收端 MeshLink；**不等於證明 iOS 母程式已正確匯入並可從正常 UI 閱讀。** I1 的原生投影 board 檔案有建立／更新，但檔案加密，未解密，也未把檔案存在當作確切訊息匯入證明。

### 2. Android 第二端點：能力宣告與實際派送不一致

在 B 的 5d6e 原生 NTsocial 頻道傳送 `NTS0907-0916-B2-I1-N02`。母程式顯示等待送達，出口 outbox 保存第二端點／slot 4 的項目，訊息 ID **C36BF61E3C7A15AA43286D101DD57B73**。多次讀取 B 與 I1 的 MeshLink 資料庫，均沒有該測試文字的 durable packet；最後仍未完成。

目前原始碼的實際路徑與症狀一致：

- `app/.../radio/RadioEndpointKoinModule.kt:179` 注入 `SecondaryGatewayRepository`。
- `app/.../radio/EndpointHostAdapters.kt:217`、`:227` 的 durable overlay／native send 都會拋出例外。
- `app/.../radio/MeshtasticEndpointGatewaySource.kt:113`、`:114` 在 Ready 時仍宣告 native／overlay send 可用，派送時再委派給上述 repository。
- 同一個第二端點 repository 的 `activateInboundSession`、`isInboundSessionActive`、`cacheInbound` 也固定回傳 false（`:190`–`:198`）。因此 **第二 radio 能收到 Meshtastic native text，不代表第二 radio 的 NTsocial overlay Gateway 已接通**。這一點是 source finding，不能冒充母程式入站實測。

判定：優先修正的整合缺口。先使能力宣告符合實際能力，再補齊隔離的第二端點 Gateway；不能把 legacy-primary repository 直接分享出去。

### 3. iOS 原生頻道的產品入口不完整

I1 保存 8 個原生頻道投影，NTsocial 位於 slot 3；但正常頻道清單只有既有的已加入社群頻道。對應外部母程式 source：

- `ios/Sources/NTSocialUI/AppShellView.swift:4604` 起的清單以 `joinedChannels` 建立社群入口。
- `ios/Sources/NTSocialMeshLinkGateway/MeshLinkGatewayAdapter.swift:498` 實作 `enqueueNativeBroadcastText`，本輪搜尋到的呼叫點只有測試，沒有產品 UI caller。
- native import 與投影資料的存在，沒有自動補上清單、composer 及正常開啟路徑。

判定：功能入口缺漏。不能宣稱 iOS 母程式已有與 Android 相同的原生頻道收發體驗；也不能從本輪資料斷言原生歷史一定沒有匯入。

### 4. iOS 本機已接受，但佇列與重連尚未走通

在既有測試社群頻道綁定 1407 的 NTsocial 後，透過 UI 傳送 `NTS0907-0916-I1-B-O03`。母程式確切 canonical row **FBA6A046A05D26C9C6D7B97FD368D529** 已由新控制指令確認：committed store 一筆、可見一筆、caption SHA-256 符合測試文字。

同一測試窗口的 MeshLink ledger／Room 對應如下：

| client message ID | packet ID | 私有 ledger | 最後 Room 狀態 |
| --- | --- | --- | --- |
| 034B2BB5AF1AB6BB71A58868441815A5 | 1903220361 | ACCEPTED | QUEUED |
| 07CF31B65E1C317C95A3FAE790AB5322 | 516578448 | ACCEPTED | QUEUED |
| 48F1054A5D7B3D502787BB889EEA5903 | 257389697 | ACCEPTED | QUEUED |
| 999F48F2268A47694947F45130505950 | 277276486 | ACCEPTED | QUEUED |

MeshLink 回前景且 Connection 曾顯示 Connected，仍未觀察到這些項目完成派送。之後對同一 1407 執行 Disconnect → Connect → Continue，並重啟 MeshLink 一次，佇列資料保留，但最後連線顯示 Error／Not connected。已詢問使用者確認 radio 電源、距離及是否被其他手機占用；本輪結束前沒有收到確認。

判定：本機跨 App 接受流程有證據；持續派送與重連驗收未通過。根因尚不能直接歸咎於 HMAC、App Group、radio 硬體或單一函式。`IosDurableMessageQueue.kt:107` 起的 gate、QueueStatus 與 transient retry 是後續診斷範圍。接受紀錄不代表 RF。

### 5. Android cloud-first 與 LoRa 備援要分開看

Internet 開啟時傳送 `NTS0907-0916-B-I1-O04`，母程式有文字顯示，但沒有立即出現 port 256 出口，不能據此判定 Gateway 壞掉。外部母程式 `RealtimeMessagingRuntime.kt:748` 起的 mesh fallback 路徑才會通知 LoRa coordinator。

暫時關閉 B **母程式內**的 Internet 開關，確認 runtime 為 `stopped`，再從同一已綁定的測試社群 UI 傳送 `NTS0907-0916-B-FALLBACK-O05`。此後主端點新增 20 筆 port 256 `ENROUTE`，第二端點 FromRadio 紀錄全數匹配：

- 20／20 packet ID 相同。
- 20／20 完整 decoded payload 的 SHA-256 相同。
- 接收端 `transport_mechanism=1`（LoRa）、`via_mqtt=false`。
- 比對來自 Room `log.from_radio` 中的實際 protobuf，沒有把 UI 文字或 Internet 收件當作 RF 證據。

這證明 Android 主 Gateway 可送出 PRIVATE_APP，第二 radio 可實際接收該 RF。備援啟用也會觸發現有 due work；此處不把所有 20 筆一概說成單一文字的分片數，也不把同一手機的第二 radio 收包當作另一支母程式成功匯入。

**儲存路徑注意：** `MeshDataHandlerImpl.kt:176` 把 PRIVATE_APP 交給 Gateway cache；它不是一般 native text 的 Packet history。單憑接收端 `packet` 表沒有 port 256，不能斷定沒有 RF 收件。本輪已改以 FromRadio log 核對疊加 RF；最後 B 的兩個 radio log 仍未找到前述 4 個 iOS-origin packet ID。

### 6. Android 原生頻道的說明文案混用

原生 NTsocial 頻道顯示「LoRa 原生」，但同時套用「此路徑傳送的是 NTsocial 疊加訊息，不會以一般 Meshtastic 原生文字顯示」的說明。本輪 N01 已實際證明該路徑送的是 port 1。外部母程式 `ChannelNavGraph.kt:2770` 對 `isLoRaRouted` 共用 `channel_lora_gateway_accepted_notice`，而繁體中文 `strings.xml:603` 的文案僅適用 overlay。這是已觀察並與 source 對照的 UI 錯誤；「本機接受不代表 RF」的提醒本身應保留。

## 還不能宣稱通過的項目

- iOS ↔ Android 母程式之間，排除 Internet／手機 Mesh 路徑後的完整 LoRa 雙向 canonical import。
- Android 第二端點的完整 Gateway send／overlay ingress，以及四 radio 操作。
- iOS 多 radio、長時間鎖屏／背景／系統終止後的恢復保證。
- 真正相同請求的重播、跨重啟去重、route expiry 的完整邊界矩陣、斷電時的 ledger commit 邊界。
- 清除歷史後的 live epoch、slot／PSK 重配、QR fresh readback；沒有為測試而刪除使用者歷史或改寫 radio 頻道。
- C 的永久拒絕藍牙權限恢復 UX 尚未完整重現。初始有 SCAN／CONNECT `USER_FIXED`，也有使用者同時手動操作，不能把所有畫面變化歸因於 App 缺陷。
- 所有手機、所有情境「沒有任何錯誤」、三平台 root gate、商店上架或 Release readiness。

## 收尾狀態與證據

- B Internet 已恢復 ON，新的 `GET_REALTIME_STATE` 為 `ready`；A/C 也為 `ready`。
- B／I1 本輪新增的測試社群 LoRa 綁定已移除，原有 native projections、帳號、訊息、radio 配對資料保留。radio 頻道／PSK／韌體未改寫。
- 兩支 iPhone 原有 wirelessprobe 輔助 App／XCUITest runner 已恢復。產品 App 沒有重新安裝。
- I1／I2 母程式已用原有控制模式啟動，auto-send／自動建群／test relay／frame capture 關閉。新回覆 ID 分別為 `FEA4CDC4-BB74-40E7-873D-E7F1AD761E03`、`6418C4EA-DBFC-41AA-9DFC-E8C65E27F578`；兩者 heartbeat 都有前進，Internet ready、BLE Running、高速同步開啟、`usingTestRelay=false`。
- I1 的 1407 最後仍為 Error，尚未恢復 RF；不能寫成「全部連線已恢復」。
- 第二端點的測試 outbox 與 iOS 的 4 筆待送 packet 刻意保留作為證據，沒有刪除；連線／實作修復後可能續送。所有主動測試文字只在 NTsocial 測試頻道，沒有 PUBLIC 測試發文。
- 早期 iOS status 檔案時間戳曾停住；那些讀取不算即時控制通過。最後使用新 request ID 與遞增 heartbeat 才完成控制驗證。
- 私有原始證據位於本機 `.agent_artifacts/meshlink-integration-20260916/`，包含唯一 UI action 記錄、UI tree／截圖、資料庫及 WAL、測試 runner 結果。`evidence/native1-transport.json`、`B-fallback-RF-summary.json`、`I1-overlay-accepted-ledger.json`、`final-packets.json`、`ios-restored-heartbeats-final.json` 為精簡核對資料。這些 git-ignored 原始資料含私人內容，不作公開附件。
- 驗證使用 Android ADB、iPhone XCUITest／CoreDevice、SQLite 精準查詢及 protobuf 欄位／雜湊比對。輔助 UI harness 有建置及實機執行；沒有修改產品程式，因此未重跑產品 Gradle／Xcode release gate。

建議下一步先修正 Android 第二端點的 Gateway 能力與實作，再補 iOS 原生頻道 UI；1407 恢復可重現的連線後，針對 4 筆既有 accepted packet 診斷 drain／QueueStatus，最後重跑跨平台母程式的 LoRa 專用雙向收訖。
