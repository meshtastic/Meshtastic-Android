# iOS 公司簽章設定與驗證 — 2026-09-10

## 最新狀態：公司憑證與兩個商店記錄已完成（2026-09-10）

- 使用者已明確授權建立發行憑證，Xcode 建立成功；本機 Keychain 顯示
  `Apple Distribution: LiberaNt LLC (D524H699HW)`，SHA-1 指紋
  `095F01E2577905EAB9CFBB866DD51F6F22B22E0E`。未匯出私鑰。
- App Store Connect 條款經使用者確認後接受。兩個商店記錄已建立，均為 Prepare for Submission：
  NTsocial `6810478003`，NTsocial MeshLink `6810479958`；Bundle ID 維持原識別碼。
- NTsocial Release device build 與 MeshLink Release archive 已通過公司 Development 簽章驗證：
  Team `D524H699HW`，實際 App Groups 只有 `group.com.ntsocial.gateway`，兩者共享
  `D524H699HW.com.ntsocial.meshlink.gateway`。`get-task-allow=true`，因此只是簽章預檢，
  尚未完成 App Store 發行 export、上傳或送審。
- 使用者明確要求官網隱私政策由其稍後修正，不以官網文字修正阻塞本次上傳與送審。
  App Store Privacy 欄位仍須依實際資料處理填寫。
- 既有八項 Detekt 問題已進行限定範圍修復與測試，完整 gate 於本次重跑通過（2,025 tasks；103 executed、1,922 up-to-date；exit 0）。先前 archive
  僅作簽章預檢，不能宣稱為目前修復後固定來源的最終 candidate。

以下段落保留各階段歷史，以本節最新證據為準。

## 改用公司群組（2026-09-10）

使用者已放棄舊個人團隊及原 App Group，並要求 NTsocial 與 MeshLink 都推進 App Store 上架。
兩個產品只有同一間公司 LiberaNt LLC、同一 Team `D524H699HW`；Bundle IDs 仍為
`com.ntsocial.ios` 與 `com.ntsocial.meshlink.ios`。目前正式採用的新 App Group 是
`group.com.ntsocial.gateway`。中途帶 Team ID 的名稱已依使用者要求淘汰，產品權限只選新名稱。

- 舊個人 Apple Account 已從 Xcode Sign Out；公司 Apple Account 保留。兩個產品的 Debug／Release
  公司簽章設定保持不變；主 App 的 Personal entitlement override 檔案、SwiftPM 排除項與操作文件已移除。
- Xcode 自動佈建已產生兩個公司專用 Development profiles，均包含 `group.com.ntsocial.gateway`。
  MeshLink profile 建立於 2026-09-10 01:43:30 UTC，主 App 為 01:42:52 UTC，均一年有效。
  主 App profile 同時授權既有 Wi-Fi Aware／Hotspot。主 App profile 尚列有過渡群組；來源 entitlements
  只宣告新的簡潔群組。不能將 profile 可授權的全集當成 App 實際簽章權限。
- 兩個來源合約、Info.plist、所有有效 Debug／Release entitlements、主 App 的 archive／baseline
  檢查均已同步。共享 Keychain suffix 仍是 `com.ntsocial.meshlink.gateway`，主 App 私有群組仍排第一。
- 新群組是新的共享容器。兩個產品需一起更新，不遷移舊 App Group mailbox，不回復舊身份／歷史測試資料。
- 新名稱來源的完整 MeshLink 檢查已跑完 2,097 tasks，128 executed／1,969 up-to-date，exit 1
  仍只有既有八項 Detekt findings；其他 build/test/KMP/lint 通過。主 App release baseline 與
  45 項 AppleGateway／MeshLink focused Swift tests 通過。
- 目前正在進行真正公司簽章的 MeshLink Release archive 及 NTsocial Release device build。
  Apple Distribution 憑證建立的當下確認尚待回覆；目前 profiles 是 Development，不能宣稱 App Store IPA。
- 原 App Group 的 Apple Support 草稿不再是上架依赖，不發送此已被替代的調查請求。

下方保留早先調查與失敗紀錄，遇到狀態差異以上述最新狀態為準。


## 結果

原 Bundle ID `com.ntsocial.meshlink.ios` 已註冊到 LiberaNt LLC。
MeshLink 與 NTsocial 主 App 的 Debug／Release 有效設定均使用公司 Team ID
`D524H699HW`，但公司 App Group 尚未取得，尚無可宣稱完成驗證的正式簽章 archive／IPA。

| 項目 | 本次結果 |
| --- | --- |
| MeshLink Bundle ID | `com.ntsocial.meshlink.ios`，公司 Portal 已確認 |
| 主 App Bundle ID | `com.ntsocial.ios`，公司 Portal 已確認 |
| Xcode Team | 兩個 App 的 Debug／Release 均解析為 `D524H699HW`，Automatic signing |
| App Group | 公司註冊遭 Apple 拒絕；舊 Personal Team 的 Xcode 清單重新整理後仍列出原群組，公司尚未綁定 |
| Keychain Sharing | 兩個 App 的來源設定一致；實際公司簽章權限尚未驗證 |
| 發行憑證 | Xcode 已準備 Apple Distribution 建立選單，等待操作確認 |
| provisioning profile | 本機只有不含 App Group 的公司萬用開發 profile；所需 profile 尚未產生 |
| Release archive | 實際嘗試失敗，原因為 App Group/profile 權限缺失 |
| 真機 Gateway | 未安裝或啟動任何實機 App；需先解決群組與 profile |
| Apple Support | 已填妥支援表單，等待授權送出；尚無 case ID |

## 本機變更

- MeshLink 新增 `iosApp/Config/Signing.xcconfig`，由 Debug／Release 共同引用。
  它選擇性載入忽略於 Git 的 `Signing.local.xcconfig`；本機公司 Team ID 放在該檔。
- 保留 `PRODUCT_BUNDLE_IDENTIFIER`、既有 entitlement 檔與自動簽章模式。
- NTsocial 主 App 位於 `/Users/curry_tw/Documents/GitHub/NTsocial_release`。
  它既有、尚未提交的 Config／project／README／gitignore 變更已保留，本次只讀核對。
- 無 Android、Windows、KMP 執行邏輯或 radio protocol 變更。未提交、推送、上傳或發布。

## App Group 阻礙

在 LiberaNt LLC 的 App Groups 清單為空時，以描述 `NTsocial MeshLink Gateway`
註冊 `group.com.ntsocial.meshlink.gateway`，Apple Portal 回覆：

> An Application Group with Identifier 'group.com.ntsocial.meshlink.gateway' is not available.

Xcode 同時顯示相同的識別碼不可用問題。初次查驗時尚未確認原持有團隊；下方後續查驗
已找到舊 Personal Team 的 profile 與重新整理後的 Xcode 群組清單證據，不能將 Bundle ID
註冊成功或 profile 到期解讀為 App Group 已釋出。
未改用其他群組、移除 entitlement、撤銷憑證或刪除任何識別碼。

### 後續舊帳號查驗

本機舊 Apple Development 憑證的組織欄位確認舊 Personal Team ID 為 `HN23VK3A6R`。
進一步在 Xcode DerivedData 保留的 `NTSocialApp.app/embedded.mobileprovision` 中，找到
`iOS Team Provisioning Profile: com.ntsocial.ios`，其 `TeamIdentifier` 是 `HN23VK3A6R`、
`application-identifier` 是 `HN23VK3A6R.com.ntsocial.ios`，而 `com.apple.security.application-groups`
確實包含 `group.com.ntsocial.meshlink.gateway`。該 profile 的有效期為台北時間
2026-09-03 07:54:03 至 2026-09-10 07:54:03。

這是舊團隊曾獲授權使用原群組的直接歷史證據；profile 本身不能單獨證明當前歸屬。
metadata 摘要與原 profile SHA-256 已保存在忽略於 Git 的證據目錄，未複製憑證私鑰或裝置名單。

使用者隨後完成舊 Apple Account 登入。2026-09-10 約 09:33（台北時間）透過獨立
`GroupOwnershipProbe` Xcode 專案查驗：`DEVELOPMENT_TEAM=HN23VK3A6R`、manual signing、
`CODE_SIGNING_ALLOWED=NO`、空 Bundle ID、空 App Groups entitlement 陣列。Signing &
Capabilities 顯示舊 Personal Team，App Groups 清單列出未勾選的
`group.com.ntsocial.meshlink.gateway`。點擊 refresh 後，按鈕先停用再恢復可用，清單仍保留
該群組，未顯示帳號／更新錯誤。原識別碼未預填進檢查專案，亦未勾選、建立或註冊群組。

因此可確認：舊團隊曾具有該群組授權，且登入後重新整理的 Xcode 團隊清單仍列出它。
這強烈支持原群組仍保留在舊 Personal Team 的判斷，與公司端拒絕註冊一致。證據界線：
本次讀取的是 Xcode UI，未取得 Apple Portal 的原群組詳情頁或 Apple Support 的後台
歸屬確認；不宣稱可保證直接刪除後立即重新註冊。產品專案的公司 Team、Bundle ID 及權限
未因本次查验變更，未刪除舊群組、發送支援訊息、建立憑證或操作真機。

Apple 的[App 轉移說明](https://developer.apple.com/help/app-store-connect/transfer-an-app/overview-of-app-transfer)
說明，在符合條件的 App 轉移後，App Group 可由原團隊刪除再由接收團隊註冊；本案尚未
建立符合該流程的證據，已準備請 Apple Support 查明並提供適用處理方式。

## 驗證紀錄與限制

- MeshLink 來源 HEAD：`8c14f536c407095ac0c7d9865712a597020b11e4`，加上本次本機設定變更。
- 主 App 來源 HEAD：`c1655f541e29a94eec55307c37645742f72b2f7f`，保留既有未提交簽章設定。
- `xcodebuild -showBuildSettings`：兩個 App、Debug／Release 的 Bundle ID、Team、Automatic
  signing 與 entitlement 路徑均符合預期。
- entitlement plist 比對：兩個 App 宣告相同 App Group；共享 Keychain suffix 均為
  `com.ntsocial.meshlink.gateway`。主 App 的私有 `com.ntsocial.ios` 群組仍保留。
- 主 App `swift test --package-path ios --filter 'AppleGateway|MeshLink'`：45 項通過。
- MeshLink 完整命令執行 2,097 個 actionable tasks；formatting、Debug builds、tests、KMP、
  iOS compilation/framework 與 Android lints 完成。整體 exit 1，只有既有 8 項 Detekt finding：
  BLE 3、domain 1、model 1、network 1、UI 2。沒有抑制或宣稱 root gate 全綠。
- 本次 JVM 結果：`ios:runtime:jvmTest` 26 項，`core:gateway:jvmTest` 39 項，均零失敗。
  Kotlin/Native executable tests 仍受既有 convention 停用，不能計為執行。
- Xcode signing-disabled Simulator Debug build 成功；在全新 iOS 26.5/iPhone 17 模擬器完成安裝
  並啟動 `com.ntsocial.meshlink.ios`。建置仍有 AppIntents metadata extraction skipped 警告。
  此結果不證明公司 profile、App Group 或實機 Gateway 可用。
- 真正的 Release archive 嘗試 exit 65：公司 wildcard profile 缺少 App Groups capability、
  `group.com.ntsocial.meshlink.gateway` 與 `com.apple.security.application-groups` entitlement。
- 主 App 的 Release 另宣告 Hotspot 與 Wi-Fi Aware Subscribe；Xcode 目前也回報 profile
  缺少這些能力，後續公司 Release profile 必須包含它們，不能以移除權限冒充正式驗證。

可重現的 MeshLink 驗證命令（需先依 AGENTS.md 完成 JDK 21／Android SDK／submodule bootstrap）：

```sh
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile \
  :app:lintFdroidDebug :app:lintGoogleDebug :ios:runtime:jvmTest \
  :ios:runtime:compileKotlinIosArm64 :ios:runtime:compileKotlinIosSimulatorArm64 \
  :ios:runtime:compileTestKotlinIosSimulatorArm64 \
  :ios:runtime:linkDebugFrameworkIosSimulatorArm64 --continue
```

本機詳細 log 保存在 Git 忽略的 `.agent_artifacts/ios-company-signing-2026-09-10/`。

## 待完成

1. 經執行當下確認後，由 Xcode 建立公司 Apple Distribution 憑證，私鑰保留於本機 Keychain。
2. 經授權送出 Apple Support 請求，查明原 App Group 歸屬；原群組可用後再綁定兩個 App。
3. 重新產生符合各 App entitlement 的 Development／App Store Connect profile。
4. 產生並檢查實際 archive／exported IPA 的 Team、Bundle ID、App Group、Keychain、發行簽章
   及 `get-task-allow=false`；區分開發簽章 archive 與真正 App Store Connect 發行 IPA。
5. 遵守主 App 的空白資料測試規則，使用公司簽章的兩個 App 進行實機 Gateway 驗證；原有
   未提交工作與正式發布來源 clean-state gate 另行保留。不得把此次本機建置宣稱為
   TestFlight、RF/remote delivery、App Store 核准或完整 release closure。
