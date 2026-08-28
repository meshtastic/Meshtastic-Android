---
title: ファームウェア更新
parent: User Guide
nav_order: 13
last_updated: 2026-08-27
description: 無線機のファームウェアを Bluetooth または USB で更新します。OTA の手順、バージョンチャンネル、事前チェック、復旧について説明します。
aliases:
  - firmware
  - update
  - ota
  - flash
---

# ファームウェア更新

新機能、バグ修正、セキュリティ改善のために、Meshtastic 無線機を最新のファームウェアに保ちましょう。

## 更新を確認する

1. 接続中の無線機の設定を開き、「**詳細設定**」で「**ファームウェア更新**」をタップします。 この項目は、OTA に対応したデバイスでのみ表示されます。
2. アプリが、利用可能なファームウェアバージョンを確認します。
3. 利用可能な更新には、バージョン番号と変更履歴の概要が表示されます。

## 更新方法

### Bluetooth 経由の OTA（無線更新）

Android ユーザーにとって最も一般的な更新方法です：

1. 無線機が Bluetooth で接続されていることを確認します。
2. ファームウェア更新画面に移動します。
3. 希望するファームウェアバージョンを選択します。
4. 「**更新**」をタップして OTA 処理を開始します。
5. 更新が完了するまで待ちます。更新中は**接続を切断しないでください**。

![ファームウェアの更新を確認中](../../assets/screenshots/firmware_checking.png)

> ⚠️ **警告：** ファームウェア更新を中断すると、デバイスが起動不能になることがあります。 Keep the radio charged and stay in Bluetooth range for the whole update. The app itself only blocks the update below **10%** battery; 50% or more is the safe habit, not an enforced limit.

#### Erase device during update

Where the app offers it, an **Erase device during update** checkbox appears next to the update button. It is a per-update opt-in and is never remembered.

| Method         | What erasing does                                                                                                                      |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| BLE / WiFi OTA | Factory-resets the device once the update is verified. All settings and Bluetooth pairing are removed. |
| USB            | Wipes the device's flash completely, then installs the selected firmware from scratch.                                 |

It is not offered for a local firmware file, during a recovery update, or on USB devices whose board does not support the erase step. Afterwards the device needs setting up — and pairing — again.

### OTA via WiFi (network-connected ESP32)

When an ESP32 radio is connected over the network rather than Bluetooth, the app offers **WiFi OTA**, which pushes the same update over TCP:

1. Connect to the radio over the network (see [Connections](connections)).
2. Open the Firmware Update screen and pick a version.
3. Tap **Update**. Keep the radio and phone on the same network for the whole transfer.

WiFi OTA takes the ESP32 `-update.bin` image rather than the `.uf2` a USB update uses; the app selects the right artifact for you.

![ファームウェアの免責事項](../../assets/screenshots/firmware_disclaimer.png)

### アプリ内での USB 更新

無線機が（Bluetooth ではなく）**USB／シリアル**で接続されている場合、ファームウェア更新画面に「**USB ファイル転送**」が表示されます。 アプリはデバイスを DFU モードで再起動し、システムのファイル選択画面を使って `.uf2` ファイルをデバイスの DFU ドライブに保存するよう促します。 このオプションは USB／シリアル接続でのみ表示され、Bluetooth では利用できません。

> ℹ️ **nRF bootloader note:** A vendor bootloader supplied as a `.zip` (e.g. RAK WisBlock RAK4631) has to be flashed with a serial DFU tool such as `adafruit-nrfutil` — copying that `.zip` to the drive won't work. A bootloader supplied as an `update-....uf2` **can** be installed by copying it to the drive; that is how the app's own bootloader upgrade works. The app surfaces a hint when the serial-only route applies.

### Factory Erase and Bootloader Upgrade

On a **USB/serial** connection, nRF52 and RP2040 devices also offer **Erase and reinstall** and, where an upgraded bootloader is published for the board, **Upgrade bootloader**.

Erasing wipes everything on the device — channels, keys and all settings — and there is no backup, so the app asks for confirmation first. Both operations write two files in turn, so you will be asked to select the device's update drive twice: once for the erase or bootloader image, then again for the firmware.

The app reads `INFO_UF2.TXT` from the drive you select to confirm it really is the device's update drive and to identify the board before writing anything. If it can't confirm which Bluetooth stack your device uses it refuses to erase and points you at the [Web Flasher](https://flasher.meshtastic.org) instead — picking wrong there can leave the device needing a hardware programmer to recover.

### その他の書き込み方法

復旧時、または OTA とアプリ内 USB のどちらも使えない場合：

- [Meshtastic Web Flasher](https://flasher.meshtastic.org) を使用する
- または、デスクトップで [Meshtastic CLI ツール](https://meshtastic.org/docs/getting-started/flashing-firmware) を使用する

## バージョンチャンネル

| チャンネル    | 説明                                      |
| -------- | --------------------------------------- |
| 安定版      | ほとんどのユーザーに推奨。テスト済みのリリース                 |
| アルファ版    | プレビューリリース。バグを含む場合があります                  |
| ローカルファイル | ダウンロードしたリリースではなく、自分で選んだファームウェアファイルを書き込む |

## 更新前のチェックリスト

更新する前に：

- [ ] バッテリー 50% 以上
- [ ] 安定した Bluetooth 接続
- [ ] 現在の設定を控えておく（メジャーバージョンの変更時にリセットされることがあります）
- [ ] 破壊的変更がないか、リリースノートを確認する

## 更新後

ファームウェアの書き込み後、アプリは更新を検証し、デバイスが再びオンラインになるのを待ちます：

![更新を検証し、デバイスの再接続を待っている様子](../../assets/screenshots/firmware_verifying.png)

更新が成功すると：

- 無線機が自動的に再起動します
- Bluetooth 接続が再確立されます
- 設定が保持されているか確認します
- ファームウェア更新画面の「**現在インストール済み**」で新しいバージョンを確認します。バージョンは、ノードの詳細ページや接続画面にも表示されます

![ファームウェア更新の成功](../../assets/screenshots/firmware_success.png)

## トラブルシューティング

### 更新が止まる

更新が固まったように見える場合：

- Give it a minute. After writing the image the app waits up to **60 seconds** for the radio to come back and report its new version, so a pause at the verify step is expected.
- If it is still stuck after that, power-cycle the radio.
- Attempt the update again.

![ファームウェア更新のエラー](../../assets/screenshots/firmware_error.png)

### 更新後にデバイスが起動しない

デバイスが起動しない場合：

1. USB でコンピューターに接続してみる
2. リカバリー／DFU モードで Web Flasher を使用する
3. 動作確認済みのファームウェアバージョンを書き込む
4. デバイス固有の復旧手順については、Meshtastic の Discord を確認する

### 互換性の警告

On connecting, the app compares the radio's firmware against two thresholds and reacts differently to each:

| Firmware version                                                                                                | What you see                                     | What happens                                                                                                         |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Below **2.3.15**                                                                | **Firmware update required.**    | The app disconnects from the radio. It will not operate against firmware this old.   |
| **2.3.15** up to, but not including, **2.5.14** | **Firmware Update Recommended.** | Advisory only — dismiss it and carry on. The dialog names the latest stable release. |
| **2.5.14** or newer                                                             | Nothing                                          | —                                                                                                                    |

A version string the app cannot parse is ignored rather than treated as too old, so a transient read never disconnects a working radio.

> ⚠️ **重要：** 互換性を確保するため、ファームウェア更新の前、または同時に、必ず Meshtastic アプリを更新してください。

## 関連トピック

- [コネクション](connections)：ファームウェア更新後の再接続
- [ファームウェア書き込みガイド](https://meshtastic.org/docs/getting-started/flashing-firmware)：meshtastic.org にあるファームウェア書き込みの完全な手順
- [対応デバイス](https://meshtastic.org/docs/hardware/devices)：デバイスごとのファームウェア互換性を確認
- [FAQ](https://meshtastic.org/docs/faq/)：meshtastic.org のよくある質問

---

