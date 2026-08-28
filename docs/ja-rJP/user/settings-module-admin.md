---
title: 設定：モジュールと管理
parent: User Guide
nav_order: 8
last_updated: 2026-08-27
description: オプションの機能モジュール（MQTT、テレメトリ、定型メッセージ、TAK など）を設定し、デバイスの管理を行います。
aliases:
  - modules
  - module-config
  - administration
---

# 設定：モジュールと管理

オプションの機能モジュールを設定し、デバイスの管理を行います。 モジュールは、専用の機能で Meshtastic を拡張します。それぞれ個別に有効・無効を切り替えられます。

> 💡 **ヒント：** 実際に使うモジュールだけを有効にすれば十分です。 使わないモジュールを無効にすると、電波利用時間が減り、バッテリーを節約でき、設定もシンプルになります。

モジュールの設定は、トグルスイッチ、ドロップダウン、テキストフィールド、スライダーを備えたカード形式のレイアウトを使用します：

![トグルスイッチ](../../assets/screenshots/settings_switch.png)

![ドロップダウンセレクター](../../assets/screenshots/settings_dropdown.png)

![テキストフィールド](../../assets/screenshots/settings_text_field.png)

![設定のカードレイアウト](../../assets/screenshots/settings_titled_card.png)

## モジュールの設定

### MQTT モジュール

インターネット接続のために、メッシュのメッセージを MQTT サーバーとの間で橋渡しします。 これにより、無線の到達範囲を超えてメッシュを拡張したり、ホームオートメーションシステムと連携したりできます。

| 設定項目                 | 説明                                                                                                                                                                                        |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 有効                   | MQTT ブリッジを切り替え                                                                                                                                                                            |
| サーバー                 | MQTT サーバーのアドレス                                                                                                                                                                            |
| ユーザー名                | 認証用のユーザー名                                                                                                                                                                                 |
| パスワード                | 認証用のパスワード                                                                                                                                                                                 |
| 暗号化                  | MQTT ペイロードを暗号化                                                                                                                                                                            |
| JSON Output          | Publish and consume MQTT messages as JSON. Marked deprecated in the protobuf schema, but it is still the only toggle for this behaviour and the firmware still honours it |
| TLS                  | セキュアな接続を使用                                                                                                                                                                                |
| ルートトピック              | MQTT のベーストピックパス                                                                                                                                                                           |
| クライアントへのプロキシの有効化     | Let a connected phone carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                       |
| このスマートフォンの MQTT プロキシ | The phone-side half of the above: whether _this_ phone is currently acting as that relay. See [MQTT](mqtt)                                                |
| マップ報告                | Publish position to the public map — see below                                                                                                                                            |

**Map Report** expands into its own group:

| 設定項目               | 説明                                                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| 有効                 | Publish to the public map at all                                                                                                |
| Share location     | Explicit consent to include your position. Map reporting will not save without it                               |
| Position precision | How coarsely your position is published                                                                                         |
| Publish interval   | How often to report. Must be **at least 3600 s (1 hour)** — the app blocks saving below that |

暗号化、プライバシー、サーバーの設定を含む詳しい使い方は、[MQTT](mqtt) を参照してください。

### シリアルモジュール

外部デバイスとの連携（GPS モジュール、センサー、カスタムハードウェア）のために、シリアルポート通信を有効にします。 有効にすると、ノードのシリアルポートで protobuf またはテキストデータを送受信でき、外部のマイコンやコンピューターがメッシュとやり取りできるようになります。

| 設定項目                         | 説明                                                                                                                                                                                                  |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| シリアル通信を有効化                   | シリアル通信を有効化                                                                                                                                                                                          |
| Echoを有効化                     | 受信したシリアルデータをそのまま返す                                                                                                                                                                                  |
| シリアルモード                      | Which protocol the port speaks — Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, VE.Direct, MeshSolar config, Log, or Log (text only) |
| RX / TX                      | GPIO pins for the serial connection                                                                                                                                                                 |
| シリアルボーレイト                    | Port speed                                                                                                                                                                                          |
| Timeout                      | How long to wait before considering an incoming message complete                                                                                                                                    |
| Override console serial port | Take over the port the debug console normally uses                                                                                                                                                  |

### 外部通知モジュール

無線機ハードウェアのブザー、LED、振動によるアラートを制御します。 メッセージ到着時に物理的に知らせる必要があるデバイスに便利です。特に無人設置や屋外設置で役立ちます。

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles.

| 設定項目                                              | 説明                                                                                                  |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| External notification enabled                     | Master toggle for the module                                                                        |
| Alert message LED / buzzer / vibra                | Which outputs fire on an incoming message                                                           |
| Alert bell LED / buzzer / vibra                   | Which outputs fire on a received bell character                                                     |
| Output LED (GPIO)              | Pin the LED is wired to                                                                             |
| Output LED active high                            | Whether the LED pin is active high or low                                                           |
| Output buzzer (GPIO)           | Pin the buzzer is wired to                                                                          |
| Output vibra (GPIO)            | Pin the vibration motor is wired to                                                                 |
| Use PWM buzzer                                    | Drive the buzzer with PWM, which allows tones rather than a single pitch                            |
| Use I2S as buzzer                                 | Send the alert through an I2S audio output instead                                                  |
| Output duration (milliseconds) | How long a single alert lasts                                                                       |
| Nag timeout (seconds)          | Keep repeating the alert for this long until it is acknowledged. 0 disables nagging |
| Ringtone                                          | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file              |

### Store & Forward モジュール

一時的にオフラインだったノードのためにメッセージをバッファリングし、それらのノードが再接続したときに再送します。 ノードが頻繁に圏内・圏外を行き来するメッシュに不可欠です。短時間の切断中にメッセージが失われないようにします。

| 設定項目      | 説明                                                     |
| --------- | ------------------------------------------------------ |
| 有効        | ストア＆フォワードを有効化                                          |
| ハートビート    | このノードのストア＆フォワード機能を定期的に告知する                             |
| レコード数     | 保存するメッセージの最大数                                          |
| 履歴の返送（最大） | 再送するメッセージの最大数                                          |
| 履歴の返送（期間） | 再送する時間の範囲                                              |
| サーバー      | メッシュのストア＆フォワードサーバーとして動作する（十分なメモリが必要、例：PSRAM 搭載の ESP32） |

> 💡 **ヒント：** ストア＆フォワードは、十分なメモリを持つノード（PSRAM 搭載の ESP32）で最も良く機能します。 ルーターノードは通常は常時起動しているため、理想的な候補です。

### レンジテストモジュール

> ⚠️ **Warning:** Range Test only works on a secured primary channel. While your primary channel
> still uses the default public key, the Enabled, Interval and Save-CSV controls stay disabled, and
> saving force-disables the module if the channel has reverted to public.

ノード間のリンク品質を評価するための、自動レンジテストツールです。 有効にすると、ノードはカウンターを増やしながらテストメッセージを定期的に送信します。 受信側のノードがこれらのメッセージを記録するため、歩いたり車で移動したりして、後からどの距離でメッセージが届かなくなったかを分析できます。

| 設定項目    | 説明                    |
| ------- | --------------------- |
| 有効      | レンジテストを有効化            |
| 送信間隔（秒） | テスト送信の間隔              |
| CSV を保存 | 受信したテストデータを SD カードに記録 |

### テレメトリモジュール

ノードがメッシュと共有するテレメトリデータを制御します。 テレメトリには、デバイスの状態（バッテリー、連続稼働時間）と環境センサーのデータ（温度、湿度、気圧）が含まれます。

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely.

| 設定項目                                  | 説明                                                                                                                                                                                |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Send Device Telemetry                 | Master toggle for device metrics. Only shown on firmware 2.7.12 and newer                                                         |
| Device metrics update interval        | How often to report battery, uptime and channel utilisation                                                                                                                       |
| Environment metrics module enabled    | Report the attached environment sensors                                                                                                                                           |
| Environment metrics update interval   | How often to report them                                                                                                                                                          |
| Environment metrics on-screen enabled | Also show these readings on the device's own display                                                                                                                              |
| Environment metrics use Fahrenheit    | Use °F on the device's display. This is the radio's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale) |
| Air quality metrics module enabled    | Report particulate and CO₂ sensor data                                                                                                                                            |
| Air quality metrics update interval   | How often to report them                                                                                                                                                          |
| Power metrics module enabled          | Report the per-channel voltage and current readings                                                                                                                               |
| Power metrics update interval         | How often to report them                                                                                                                                                          |
| Power metrics on-screen enabled       | Also show power readings on the device's display                                                                                                                                  |

対応センサーと設定の推奨事項については、[テレメトリとセンサー](telemetry-and-sensors) を参照してください。

### 定型メッセージモジュール

デバイスの物理ボタンからアクセスできる、あらかじめ設定されたメッセージです（ロータリーエンコーダー、キーパッド、または同様の入力ハードウェアを備えた無線機向け）。 スマートフォンを接続していなくても送信できる、クイック送信メッセージのリストを定義します。フィールドでの使用に最適です。

| 設定項目                                      | 説明                                                                                                        |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| ~~Canned message enabled~~                | ⚠️ **Deprecated** in the protobuf schema                                                                  |
| メッセージ                                     | 改行で区切ったメッセージのリスト                                                                                          |
| Send bell                                 | Send a bell character alongside the message, so a receiving node's External Notification module can sound |
| Rotary encoder enabled                    | Use a rotary encoder as the input device                                                                  |
| GPIO pin for rotary encoder A / B / press | The three pins the encoder is wired to                                                                    |
| Generate input event on press / CW / CCW  | Which key event each encoder action produces                                                              |
| Up/Down/Select input enabled              | A separate, simpler input scheme using up/down/select buttons rather than an encoder                      |
| ~~Allow input source~~                    | ⚠️ **Deprecated** in the protobuf schema                                                                  |

### オーディオモジュール

メッシュ上での低帯域幅の音声通信のための、Codec2 音声サポートです。 これは、Codec2 コーデックを使って音声を非常に小さなデータパケットにエンコードする、**実験的**な機能です。

| 設定項目                               | 説明                                   |
| ---------------------------------- | ------------------------------------ |
| 有効                                 | オーディオモジュールを有効化                       |
| Codec2 レート                         | 音質と帯域幅のトレードオフ                        |
| PTT Pin                            | GPIO pin for the push-to-talk button |
| I2S ワードセレクト                        | I2S WS 用の GPIO ピン                    |
| I2S データ入力                          | I2S DIN 用の GPIO ピン                   |
| I2S データ出力                          | I2S DOUT 用の GPIO ピン                  |
| I2S Clock (SCK) | GPIO pin for the I2S bit clock       |

> ℹ️ **Note:** Audio requires specific hardware (I2S microphone and speaker). 音質は非常に低帯域です。「聞き取れる無線の声」程度で、電話並みの品質ではないと考えてください。

### リモートハードウェアモジュール

メッシュネットワーク経由での GPIO 制御です。 リモートのノードが、別のノードの GPIO ピンを読み書きできるようにします。リレーの作動、スイッチの読み取り、離れた場所からの外部ハードウェア制御に便利です。

| 設定項目     | 説明                                    |
| -------- | ------------------------------------- |
| 有効       | リモート GPIO アクセスを有効化                    |
| 未定義ピンを許可 | 任意の GPIO ピンへのアクセスを許可（セキュリティリスク）       |
| 利用可能なピン  | このノードがリモートの読み書き用に公開する GPIO ピン（最大 4 個） |

> ⚠️ **警告：** 「未定義ピンを許可」を有効にすると、リモートのノードがすべての GPIO ピンにアクセスでき、無線機自体のハードウェアに干渉するおそれがあります。 GPIO 専用のノードでのみ有効にしてください。

### 隣接ノード情報モジュール

直接受信した隣接ノードの情報をブロードキャストし、メッシュのトポロジーマッピングを可能にします。 有効にした各ノードは、受信できる他のノードとその信号品質のリストを定期的に共有します。

| 設定項目     | 説明                                                                              |
| -------- | ------------------------------------------------------------------------------- |
| 有効       | 隣接ノードのブロードキャストを有効化                                                              |
| 更新間隔（秒）  | 隣接ノードのリストをブロードキャストする頻度                                                          |
| LoRa で送信 | MQTT／スマートフォンだけでなく、LoRa 経由でも隣接ノード情報をブロードキャストします。 デフォルトの鍵と名前を使用しているチャンネルでは利用できません |

隣接ノードのデータをメッシュのトポロジー探索に使う方法については、[探索](discovery) を参照してください。

### アンビエントライティングモジュール

対応ハードウェア上の、オンボードの NeoPixel やその他のアドレサブル RGB LED を制御します。 視覚的なステータス表示、通知ライト、装飾的な演出に使用できます。

| 設定項目    | 説明               |
| ------- | ---------------- |
| LED の状態 | LED のオン／オフを切り替え  |
| 電流      | LED の電流制限（0〜31）  |
| 赤／緑／青   | 各色チャンネルの値（0〜255） |

### 検知センサーモジュール

ノードを、動きやドアを検知するセンサーアラートシステムに変えます。 GPIO ピンが状態の変化（動きの検知、ドアの開放）を検出すると、ノードはメッシュ経由でアラートメッセージをブロードキャストします。

| 設定項目            | 説明                                          |
| --------------- | ------------------------------------------- |
| 有効              | 検知センサーを有効化                                  |
| 監視ピン            | センサーに接続された GPIO ピン                          |
| 検知トリガーの種類       | ピンの状態を検知イベントにどう対応させるか（例：アクティブハイ／ロー、エッジトリガー） |
| 入力プルアップモードを使用   | ピンの内蔵プルアップ抵抗を有効にする                          |
| 最小ブロードキャスト間隔（秒） | アラートのブロードキャスト間の最小時間                         |
| 状態ブロードキャスト（秒）   | 定期的な状態ブロードキャストの間隔                           |
| ベルを送信           | アラートにベル文字を含める                               |
| 分かりやすい名前        | このセンサーのカスタム名                                |

### Paxcounter モジュール

WiFi と BLE のプローブ要求を使った人数カウンターです。 スマートフォンやノートパソコンがネットワークを探すときに発するプローブ要求を受動的に受信して、近くのデバイスを数えます。 ESP32 デバイスでのみ利用できます。

| Setting             | Description                                                                                                      |
| ------------------- | ---------------------------------------------------------------------------------------------------------------- |
| 有効                  | 人数カウントを有効化                                                                                                       |
| 更新間隔（秒）             | カウントを報告する頻度                                                                                                      |
| WiFi RSSI threshold | Ignore WiFi probes weaker than this, so distant devices are not counted (defaults to −80 dBm) |
| BLE RSSI threshold  | The same cut-off for BLE advertisements (defaults to −80 dBm)                                 |

> 💡 **ヒント：** Paxcounter は、登山口やイベント会場などの人出を推定するのに便利です。 カウントはおおよその値です。1 人が複数のデバイスを持っていることがあります。

### Status Message Module

Publishes a short free-text status line for your node, which other nodes can display alongside it.

| Setting                  | Description                                                                                                                                                                      |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| The actual status string | Up to 80 characters. The **✕** in the field clears it. (That is the app's own label for the field, verbatim.) |

Saving takes effect immediately — this is one of the few module settings that never asks the
node to reboot.

> ℹ️ **Note:** The screen only appears for firmware that reports support for the status-message
> module. If you do not see it in the module list, your node's firmware does not have it.

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. See
[Discovery](discovery) for the full walkthrough.

### TAK モジュール

> ℹ️ **Note:** This module only appears in the list once the node's **Device Role** (Device Config)
> is set to **TAK** or **TAK Tracker**. Change the role first, or the entry will not be there.

ATAK および WinTAK と相互運用するための、Team Awareness Kit 連携です。 詳しい設定と使い方は、[TAK 連携](tak) を参照してください。

## 管理

### リモート管理

管理者鍵を共有しているノードをリモートで設定します：

1. ノードリストで対象のノードを選択します。
2. そのノードの「**設定**」に移動します。
3. 設定を変更します。
4. 「**保存**」をタップします。変更がメッシュ経由で送信されます。

> ⚠️ **必要条件：** 自分のノードと対象のノードの両方に管理者鍵が設定されていること。

### ノードデータベースの整理

Prunes your local node database. Two independent controls:

- An **age slider** — remove nodes not heard from within that window.
- **Clean unknown nodes only** — restrict the purge to nodes that never sent their user info,
  leaving named nodes alone regardless of age.

### 工場出荷時リセット

すべての設定を工場出荷時のデフォルトにリセットします。 **この操作は取り消せません。**

### 再起動

接続中または管理中のノードをリモートで再起動します。

### デバッグ

診断出力の表示・絞り込み・エクスポートを行う「**パケット**」タブと「**アプリログ**」タブを開きます。 詳しい手順は、[デバッグログ](debug-logs) を参照してください。

### About

**Settings → About** carries the app's own identity rather than the radio's:

Three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one, then the GitHub repository, the running app version, and
  **Acknowledgements** (below).
- **Project information** — links to the website and to this documentation.

### Acknowledgements

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries. It was previously called the license screen.

### リモート管理のトラブルシューティング

- **「対象ノードから応答がありません」**：対象が圏外、オフライン、または管理者鍵が一致していない可能性があります。 両方のノードで管理者鍵が一致しているか確認してください。
- **変更が適用されない**：一部の設定は、反映に再起動が必要です。 保存後に「再起動」を実行してみてください。
- **リモートの設定が表示されない**：自分のノードに、対象ノードの管理者鍵があることを確認してください。 管理チャンネルは、管理者鍵を設定すると自動的に構成されます。

## 関連トピック

- [設定：無線機とユーザー](settings-radio-user)：基本の無線機とユーザープロファイルの設定
- [モジュール設定リファレンス](https://meshtastic.org/docs/configuration/module)：meshtastic.org にある詳細なモジュールのドキュメント
- [FAQ](https://meshtastic.org/docs/faq/)：meshtastic.org のよくある質問

---

