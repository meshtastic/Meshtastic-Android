# NTsocial／MeshLink 五機整合修復與實機驗證 — 2026-09-16

## 結論與範圍

這輪已定位並修正五項功能問題：Android 第二端點不能發送、iOS 已接受訊息無法排出、Android 將正常 token 更新誤判為換路由、Android 第二端點漏匯入原生歷史，以及 iOS 母程式缺少原生頻道入口／發送介面。另修正 Android 原生頻道誤用疊加訊息說明。

主要證據是實機的母程式提交、MeshLink 持久佇列、相同封包 ID 與完整 payload SHA-256 的 LoRa 接收，以及接收端母程式正式歷史；`ACCEPTED_LOCAL`、清空輸入框或 `ENROUTE` 均未單獨當作遠端送達證據。

五支既有手機皆保留帳號、歷史及使用者配對並完成兩個 App 的升級。實際 RF 拓撲為 S24 Ultra 的 0809＋5d6e，以及第一支 iPhone 的 1407，共三台 radio、兩支手機。OPPO、S22 Ultra、第二支 iPhone 沒有已選定 radio，因此未宣稱五支手機皆完成 LoRa 收發。C 的 MeshLink Bluetooth SCAN／CONNECT 權限仍是原有 USER_FIXED denied 狀態，未自行變更。

測試只使用既有 NTsocial 測試 Channel 與其原生 NTsocial 無線頻道，沒有 PUBLIC 發文、帳號重設、歷史清除、radio 金鑰／頻道設定變更或 Worker 部署。

## 真因與修正

| 問題 | 真因／可重現證據 | 修正 |
| --- | --- | --- |
| Android v3 第二端點發送失敗 | 已公告 READY/native/overlay，但實際注入 `SecondaryGatewayRepository`，兩個 durable send 都直接拋出例外。 | 第二端點改用自己 scope 的 `NtsocialGatewayRepositoryImpl`、Room、session gate 與 `SessionMessageQueue`；不借用主要端點的資料庫或 WorkManager。 |
| iOS QUEUED 永遠不排出 | 精密診斷顯示 repository activation 的 gate 已啟用，queue 卻持續 `configured=true ingressActive=false`。Koin 產生碼跳過有預設值的 constructor 參數，產生第二個 gate。 | 移除 gate 的預設建構值，強制注入共同實例；production composition regression 舊版會失敗，新版通過。既有四個 O03 packet ID 自動排出，沒有手動重送。 |
| Android 分包偶發中斷 | O08 第一輪在第二遍第 3 個分片出現 `radio route changed`；前置檢查比較 token 字串，但背景 catalog refresh 會換發同一有效路由的新 token。 | 比較 captured/current descriptor、連線／能力及 uplink/downlink；命令仍攜帶原 captured token，Provider 繼續驗證 TTL、呼叫者、來源、端點與 radio/fleet generation。 |
| 第二端點原生歷史遺漏 | N07 在兩台 radio 都收到，母程式主要投影有一筆、第二投影為零；importer 用全域 Seen ID 提前回報 DUPLICATE，並推進第二端點游標。 | 嚴格 provenance 驗證後改為投影內去重；舊私有 endpoint cursor 以 `projectionDedupeVersion=1` 一次性重讀仍保留的原生 rows，補回漏項，不刪除或重建社群歷史。N07 實機已補回兩個投影，各一筆。 |
| iOS 母程式原生功能無入口 | 原生 projection 已持久化，但頻道 UI 只列 joined social channels；原生 send adapter 沒有正式 UI 呼叫者。 | 新增獨立 native section／local canonical board／180 UTF-8 byte composer；只走 Apple Gateway native command，保留相同草稿 retry ID，own echo 由既有 insertion feed 匯入。不得加入社群 membership 或 BLE／Cloud 歷史同步。 |

共用 durable queue 保留 admission 與 RF dispatch 分離、native lane 優先、exact-session/source 檢查及 cancellation；暫態失敗在 30 秒後仍可重新排出。BLE recovery 加上階段診斷，未另造 Swift BLE stack。iOS 診斷輸出僅在 Debug 且明確設定 `MESHLINK_GATEWAY_DIAGNOSTICS=1` 時啟用，只記錄封包 ID、session epoch、階段與結果，不輸出 payload、PSK、HMAC key 或 route token。

## 實機結果

| 案例 | 實際結果與界線 |
| --- | --- |
| N06：Android 母程式 → 第二端點 5d6e → iPhone | native packet `1071336318`，iPhone 收到同一封包，母程式 native board 顯示；source message ID `75DE3EDBFB36D9BEE0B16FE45B3D8083`。 |
| 舊 O03 復原 | 原來永久 QUEUED 的四個相同 packet ID 全部 DELIVERED；B 的兩個 radio 都有同 ID／payload 的 port-256 LoRa 接收。該訊息此前已經由其他 bearer 到過母程式，因此此案例只聲稱待送復原與 RF，不聲稱首次遠端社群匯入。 |
| N07：iPhone 母程式 → Android | native packet `1711983053`，兩個 Android radio 均 RECEIVED。發現並修正全域去重後，source message ID `131EF434E823C028DFAF80315D0163AE` 在兩個母程式原生投影各一筆；iPhone 升級、重啟後自己的原生歷史仍存在。 |
| O08：Android 第二端點 → iPhone 社群 Channel | 曾發生 token 更新誤判並自動重試，兩次 transfer 計 32 個 admitted packets，快照匹配 31 個 RF receipts；兩次 transfer 的十個邏輯分片均集齊，iPhone 回傳重組完成。母程式重啟後正式儲存只有一筆且明文 SHA-256 正確。保留此失敗／重試，不當作初次無錯誤成功。 |
| O09：iPhone → Android 社群 Channel | 四個分片均 DELIVERED，B 兩台 radio 共八個對應 RF 紀錄。母程式 `CHANNEL_FLOW stage=rx_commit transport=meshtastic result=ok`，正式儲存恰一筆且解密 SHA-256 相符。 |
| O10：修正 token race 後再測 Android 第二端點 → iPhone | 一次 UI Send、一個 transfer、十個邏輯分片的兩遍發送共 20 次 admission，全數成功，無 route-changed 中斷；iPhone 匹配 **20/20** 個同 ID、完整 payload hash 的 RF 封包，`viaMqtt=false`、transport=LoRa；收到 complete receipt，重啟後母程式正式儲存仍恰一筆。 |
| N11：最後 native 去重修正版 | iPhone 母程式新發一則原生訊息；Android 兩個端點的母程式投影各匯入一筆相同 source message ID `B5D7962563D9E11D62B0AA307204FC4F`。實際 native packet `1751315345` 在 iPhone 為 DELIVERED、B 兩台 radio 為 RECEIVED，完整 payload hash 相符且 transport=LoRa／viaMqtt=false。此案例亦經過 iOS 過期 route 的前景 MeshLink 喚醒。 |

O09 的 Android 母程式 Internet 暫時關閉，且實際 commit 明確標示 Meshtastic。原生訊息本身沒有社群 BLE／Cloud fallback。O08/O10 的社群訊息仍可能也走 BLE，因此它們的 RF 匹配、重組回執與母程式正式儲存分開呈現；不將一般畫面顯示偷換成唯一 bearer 歸因。

## 原始碼與安裝 artifact

- MeshLink：`3c5cb824ddafd11cfe4611a4a7eb0e30e72f3d81` 加 working-tree 修正；branch `codex/gateway-device-repair-20260916`。
- 母程式：`be7299ef2f2dc4611fddc90f334214d33dce369c` 加 working-tree 修正；branch `codex/meshlink-native-integration-20260916`。尚未 commit／push。
- Android MeshLink：Google Debug 1.0.8 (9)，APK SHA-256 `0561f9a0039d2c25906afa1ddcc2e487186351e54d434bef6be031486852f69a`。
- Android 母程式：Debug 1.6.1 (41)，最終 APK SHA-256 `19ce2fbbc23c35822b08aa79c9431fc159e5098149a28000c9ead03a064d88ac`；remote-file build feature 保持 ON。O10 使用前一個 token 修正版，最後一次 Android 改動只涉及原生投影去重／舊 cursor 修復，並以 N07/N11 另行驗證。
- iOS MeshLink：Debug 1.0.0 (2)，dylib SHA-256 `4576403007dba0b99e21a49340691ff0080e170598f736d24db278f9acc54fd4`。
- iOS 母程式：Debug 1.0.0 (1)，dylib SHA-256 `2f239167638311ec5c6f6dea707f308e3add836d5e1ed1a4c870fb3c389dd177`，remote-file build feature ON。
- 兩個 iOS App 實際簽章 Team `D524H699HW`，均含 App Group `group.com.ntsocial.gateway` 與所需 shared Keychain group。這是已安裝 Debug entitlement 證據，不是 Store acceptance。

完整 artifact manifests、metadata-only 摘錄、單元測試／安裝輸出放在本機忽略目錄 `.agent_artifacts/gateway-repair-20260916/`；原始 UI、DB/WAL 與 logcat 留在其 `private/`，不加入 Git。母程式 build/gate 輸出在 `../NTsocial_release/work/meshlink-repair-20260916/`。`logs/product-source-sha256.json` 記錄受影響 source bytes；不得用 HEAD 單獨代表未提交 artifact。

## 自動驗證與收尾

- MeshLink 最後完整 gate：**1,947 tasks SUCCESS**，含 Spotless、root Detekt、Debug build/lint、JVM tests、KMP smoke、iOS runtime JVM tests、Arm64／simulator framework 編譯。另完成未簽章 simulator Xcode build/install/launch 與兩台公司簽署實機安裝。Kotlin/Native test executable 被既有 convention 停用，未聲稱執行。
- Android 母程式：**1,148 app tests，0 failures/errors、3 既有 skipped；UTFind 19 passed**，Debug APK 編譯成功。含正常 token refresh 放行、真正 context 改變拒絕、全域 Seen 不取代投影內持久化、舊 endpoint cursor 重讀一次的回歸。
- iOS 母程式：Apple Gateway focused **46 passed**；最終完整本機 gate **14/14 steps passed**，於臺灣時間 2026-09-16 11:51:39 完成。包含 **897 Swift tests，3 skipped、0 failures**、跨平台 exchange、Release simulator build、**11 UI tests passed** 及 `source-state-unchanged`。結果位於母程式 `work/meshlink-repair-20260916/ios-parity-frozen/summary.json`。`parity-evidence` 為 `local_passed_external_required`，`completion-audit` 為 `incomplete`，因外部完整硬體矩陣、TestFlight 與 Store sign-off 尚未完成；本機流程完成不等於完整發布驗收。UI xcresult 仍有兩筆 `Invalid frame dimension (negative or non-finite)` runtime warning，未宣稱此警告已修復。
- 前兩輪 Release UI 各有 11 passed；第一輪卡在 Xcode diagnostics collection 600 秒後被中止，第二輪正確被 source-state-unchanged gate 擋下，因仍在修復中。最後重跑保持 source 不變，僅關閉 Xcode 額外 sysdiagnose 收集，不略過測試或 assertion。Gate 結束後僅補充文件結果；31 個受影響產品／測試來源檔雜湊與已記錄版本一致。
- 初期並行 Gradle 任務曾碰到 generated classes／test outputs 的工具競態，修正為序列重跑後通過；原失敗 log 保留。Xcode 27 對既有過大的 Cloud challenge Data 串接表達式無法 type-check，改用 byte-for-byte 相同的 append 建構，沒有變更簽署格式。
- 已移除 B／I1 本輪臨時社群 LoRa 綁定，原生 projection 與 radio 配對保留。B Internet 已恢復。最終五機即時狀態以 `logs/final-five-phone-handoff.json` 為準：Production、Internet、Bluetooth、高速同步及可用 remote-file feature；兩支 iPhone 必須是新 request 的 exact response 與前進中的 heartbeat，而非舊檔／PID。

## 未宣稱完成的情境

四 radio 並行、iPhone 多 radio、長時間背景／OS 終止、Android C 權限復原、沒有 radio 的三支手機 RF、Windows 實機及 signed Store release 均未被本次證明。為保留現有 radio 設定，未實機執行重配頻道／PSK 的 provisioning 或 reconcile mutation，因此不宣稱所有控制命令均完整驗收。既有 MeshLink live whole-history-clear epoch 問題與 iOS retired-scanner callback review candidate 仍需另行處理；本輪未刪除使用者歷史來測試它們。新 iOS native board 提供本輪需要的本機歷史／文字收發，不宣稱完整社群 topic／unread UI parity。

結果是主要母程式↔MeshLink↔LoRa↔遠端母程式路徑已大幅改善，並有上述界定明確的雙向實機成功證據；不是所有裝置、radio、背景狀態與錯誤情境的零缺陷保證。
