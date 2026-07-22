---
title: MQTT
parent: 使用者指南
nav_order: 11
last_updated: 2026-05-13
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

```
〔您的節點〕→ 無線電 → 〔具備 Wi-Fi 的閘道節點〕→ MQTT 代理伺服器 → 〔遠端閘道〕→ 無線電 → 〔遠端節點〕
```

具備網際網路連線（Wi-Fi 或乙太網路）的閘道節點，將 mesh 訊息發布至 MQTT 主題。 訂閱相同主題的遠端閘道，將這些訊息注入其本地 mesh 網路。

## 設定

### 啟用 MQTT

1. 前往「設定 → 模組設定 → MQTT」。
2. 啟用 MQTT 模組。
3. 設定代理伺服器連線：

![MQTT toggle switch](../../assets/screenshots/settings_switch.png)

| 設定          | 描述說明                            | 默認                                                  |
| ----------- | ------------------------------- | --------------------------------------------------- |
| 伺服器位址       | MQTT 代理伺服器主機名稱                  | mqtt.meshtastic.org |
| 使用者名稱       | 代理伺服器驗證                         | meshdev                                             |
| 密碼          | 代理伺服器驗證                         | large4cats                                          |
| 根主題         | 訊息的基礎主題                         | msh                                                 |
| 加密          | 加密 MQTT 承載內容                    | 已啟用                                                 |
| ~~JSON 輸出~~ | ⚠️ 已棄用——韌體已移除 JSON 封包支援；此欄位將被忽略 | 已停用                                                 |
| TLS         | 與代理伺服器的安全連線                     | 已停用                                                 |
| 地圖回報        | 將位置回報至公開地圖                      | 已停用                                                 |

### MQTT Proxy on This Phone

If your node has no internet access of its own, it can use the connected phone as its MQTT gateway: enable **MQTT** and **Proxy to client enabled** in the module config, and the app relays MQTT traffic between the radio and the broker over your phone's internet connection.

The **MQTT proxy on this phone** toggle at the top of the MQTT settings screen shows whether this relay is currently running and lets you cut it off (or restart it) immediately — without editing and re-saving the device's MQTT configuration.

### 預設 Meshtastic 代理伺服器

社群在 mqtt.meshtastic.org 維護一個公開的代理伺服器。 此伺服器供一般使用與測試之用。

> ℹ️ **Note:** Connections to `mqtt.meshtastic.org` always use TLS (port 8883), even if the TLS toggle is off. For any other broker, TLS is used only when you enable it (port 8883 with TLS, 1883 without).

> 🔒 隱私：公開代理伺服器上的訊息，任何訂閱者均可讀取。 私人通訊請務必啟用頻道加密。

### 私有代理伺服器

為了獲得更好的隱私保護與控制權，您可以自行架設 MQTT 代理伺服器：

- Mosquitto（輕量、開放原始碼）
- HiveMQ
- EMQX

請將您的節點設定為指向私有代理伺服器，並輸入相應的登入憑證。

## 地圖回報

啟用地圖回報後，您的節點將把位置發布至 Meshtastic 社群地圖：

- 可在〔meshmap.net〕(https://meshmap.net) 及類似的社群地圖服務上查看
- 僅分享位置與節點資訊
- 若不希望位置公開顯示，請停用此功能

## 上行 vs 下行

| 方向 | 描述說明                     |
| -- | ------------------------ |
| 上行 | 訊息從 mesh 網路 → MQTT 代理伺服器 |
| 下行 | 訊息從 MQTT 代理伺服器 → mesh 網路 |

可依頻道設定哪些方向為啟用狀態，以控制訊息流向與無線電佔用時間。

## 訊息格式

MQTT 使用 protobuf 訊息格式：

| 格式           | 描述說明                       | 使用情境        |
| ------------ | -------------------------- | ----------- |
| **Protobuf** | 二進位 Meshtastic protobuf 編碼 | 節點間 mesh 橋接 |

> ⚠️ 注意：韌體已移除 JSON 輸出支援。 Json_enabled 設定在應用程式中仍會顯示，以維持舊版相容性，但對目前的韌體版本不會產生任何效果。

## 加密與隱私

了解分層加密模型：

1. 頻道加密在 mesh 網路上進行，發生於 MQTT 傳輸之前。 若您的頻道已設定 PSK，MQTT 承載內容將已加密——代理伺服器及任何訂閱者只能看到密文。
2. MQTT 加密（模組設定）在傳輸至代理伺服器的過程中新增額外的加密層。 此設定可保護中繼資料與路由資訊。
3. TLS 對連接至代理伺服器的 TCP 連線進行加密，防止網路層級的竊聽。

> 🔒 重要：預設公開頻道使用眾所周知的金鑰。 透過 MQTT 傳送的預設頻道訊息實際上等同於未加密 — 任何人均可解碼。 私人通訊請務必使用自訂 PSK。

## 最佳實踐

- 在橋接至 MQTT 的頻道上使用頻道層級加密（PSK）
- 請勿在無法連接網際網路的節點上啟用 MQTT（這將導致緩衝堆積並浪費記憶體）
- 敏感部署環境請使用私有代理伺服器
- 從繁忙的 MQTT 主題下行訊息時，請留意無線電佔用時間——每則下行訊息都會消耗本地 mesh 網路的無線電佔用時間
- 若僅需遠端監控 mesh 網路而不需注入訊息，建議僅啟用上行模式

## 故障排除

### MQTT 無法連線

- 檢查 Wi-Fi——閘道節點必須具備有效的網際網路連線（Wi-Fi 或乙太網路）。 MQTT 無法直接透過 LoRa 無線電連結運作。
- 確認登入憑證 — 帳號或密碼錯誤在大多數代理伺服器上會靜默失敗，不顯示錯誤訊息。 請仔細確認是否有多餘的尾端空白字元。
- 防火牆 — 連接埠 1883（MQTT）或 8883（MQTT+TLS）必須開放。 部分網路會封鎖非標準連接埠。
- DNS 解析 — 若使用自訂代理伺服器主機名稱，請確認節點能夠正確解析該名稱。 請嘗試直接使用代理伺服器的 IP 位址進行連線。

### 訊息未正常橋接

- 檢查上行／下行設定 — 若僅啟用上行，訊息只會從 mesh 網路流向 MQTT，不會反向傳送。 請在接收端閘道上啟用下行功能。
- 頻道不符 — 兩個閘道必須使用相同頻道且具備相同的 PSK。 不符時，訊息將以不同金鑰加密，導致對方收到的內容為亂碼。
- 主題不符 — 請確認兩個閘道使用相同的根主題。 預設的 msh 適用於公開代理伺服器。

## 相關主題

- 〔設定 — 模組與管理〕(settings-module-admin) — MQTT 模組設定參考
- 〔訊息與頻道〕(messages-and-channels) — 頻道加密與 PSK 設定
- 〔MQTT 整合指南〕(https://meshtastic.org/docs/software/integrations/mqtt) — meshtastic.org 上的完整 MQTT 說明文件

---

