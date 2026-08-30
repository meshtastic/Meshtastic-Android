---
title: マップとウェイポイント
parent: User Guide
nav_order: 6
last_updated: 2026-08-29
description: マップ上でノードの位置を確認し、ウェイポイントの作成・共有、マップレイヤーとサイトプランナーの管理、位置共有とプライバシーの制御を行います。
aliases:
  - map
  - waypoints
  - gps
  - location
  - site-planner
  - map-layers
  - geojson
  - kml
---

# マップとウェイポイント

マップ画面には、メッシュ上のノードの地理的な位置が、共有されたウェイポイントとともに表示されます。

## マップビュー

マップには次のものが表示されます：

- **ノードの位置：** 位置を報告している各ノードの色付きマーカー
- **ウェイポイント：** 共有された注目地点
- **自分の位置：** 現在の GPS 位置

### ノードのマーカー

位置を報告している各ノードは、そのノードの短縮名を表示する**ノードチップ**マーカーとして表示されます。 チップは、そのノード固有のアイデンティティカラー（ノード番号から導かれる一定の色）で色付けされます。ノードリストで使われるのと同じチップなので、どこでも同じ見た目になります。 マーカーの色は、オンライン／オフラインの状態を**表しません**。 ノードの位置がライブで更新されると、そのマーカーが短く脈打つように点滅します。 縮小すると、近くのマーカーはまとめて表示（クラスタリング）されます。

### マップの操作

- **ズーム：** ピンチ操作、または +/− ボタンを使います
- **移動：** ドラッグして見て回ります
- **Center** — tap the location button to center on your position
- **ノードのタップ：** ノードのマーカーをタップすると詳細を表示します

フローティングツールバーから、コンパス、レイヤーの切り替え、ノードの絞り込み、更新、位置追跡にすばやくアクセスできます。 コンパスをタップすると北を上に向け直し、位置ボタンをタップすると現在の位置を中央に表示します。

![Map screen with the floating toolbar open, showing compass, layers, and location controls](../../assets/screenshots/map_controls_overlay.png)

## ウェイポイント

Waypoints are shared points of interest, visible to everyone the waypoint is sent to.

### ウェイポイントを作成する

1. Touch & hold the map at the desired location.
2. 名前と、任意で説明を入力します。
3. ウェイポイントのアイコン／絵文字を選びます。
4. 「**送信**」をタップしてメッシュに共有します。

ウェイポイントはメッセージと同じように宛先を指定します。既定ではプライマリチャンネルにブロードキャストされますが、特定のチャンネルに送ったり、単一のノードへのダイレクトメッセージとして送ったりすることもできます。

### ウェイポイントのプロパティ

| プロパティ  | 説明                                                                             |
| ------ | ------------------------------------------------------------------------------ |
| 名前     | 短い識別子（最大 29 文字）                                                                |
| 説明     | 任意の、より詳しい説明                                                                    |
| アイコン   | マップ上の視覚的なマーカー絵文字                                                               |
| ロック済み  | ロックすると、作成者だけが編集・削除できます                                                         |
| 有効期限   | 任意の、自動削除する日時                                                                   |
| ジオフェンス | Optional enter/exit alert area — see [Waypoint Geofences](#waypoint-geofences) |

### ウェイポイントの有効期限

ウェイポイントは、自動的に期限切れになるよう設定できます：

- **なし**（デフォルト）：手動で削除するまでウェイポイントは残ります
- **期限付き**：特定の日時を指定します。その時刻を過ぎると、ウェイポイントは自動的に削除されます。 集合地点、危険箇所、待ち合わせ場所などの一時的なマーカーに便利です。

期限切れのウェイポイントは、表示が煩雑にならないよう、自動的にマップから隠されます。 有効期限のカウントダウンは、指定した絶対時刻を基準とし、ウェイポイントが作成または受信されてからの経過時間ではありません。

### ウェイポイントのジオフェンス

どのウェイポイントにも**ジオフェンス**（通知エリア）を設定できます。これにより、ノードがそのエリアに進入または退出したときに、あなたや他のメンバーに通知が届きます：

1. プリセットのチップから**ジオフェンスの半径**を設定する（無効にするには**オフ**）か、「**マップ上でエリアを設定**」をタップして、任意の四角形のエリアを描画します。
2. エリアを設定したら、「**進入時に通知**」や「**退出時に通知**」を切り替えます。
3. 任意で「**お気に入りのみ**」を有効にすると、通知をお気に入りのノードに限定できます。

ウェイポイント（およびそのジオフェンス）はメッシュ全体にブロードキャストされるため、デフォルトでは**作成者**だけに通知されます。 他の人がジオフェンス付きのウェイポイントを共有した場合、その詳細ビューに「**通過を通知する**」というオプトインが表示され、あなたも進入／退出の通知を受け取れます。

### ウェイポイントを管理する

- マップ上のウェイポイントをタップすると、その詳細と座標を表示します
- 自分が作成したウェイポイントを編集または削除できます
- **Locked waypoints** cannot be modified or deleted by other mesh members — only the creator can change them
- ロックされていないウェイポイントは、どのメッシュメンバーでも編集できます

## マップレイヤー

Tap the layers icon on the map to open **Manage Map Layers**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format — including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Add one by picking a file with **Add Layer**, opening a file with Meshtastic, or sharing it into the app from another app. インポートしたレイヤーは、それぞれ表示／非表示を切り替えるトグルと、削除するオプションとともに一覧表示されます。 This works on the Google Play build, the F-Droid build, and **Desktop**, which shares the same layer store and file picker.

### サイトプランナー

**サイトプランナー**は、送信機の RF カバレッジを推定し、色分けされたオーバーレイとしてマップに描画します。 マップの操作から開くか、ノードの詳細ページから「**カバレッジを推定**」で開きます（位置が判明しているノードでのみ表示されます）。 送信機（位置、周波数、送信出力、アンテナ利得と高さ）、受信機（感度、高さ）、シミュレーションのオプション（最大範囲、高解像度の地形、カラーパレット）を設定してから、推定を実行します。 Like map layers, Site Planner works on both the Google Play and F-Droid builds, where the finished estimate is drawn on the map as a coverage overlay. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, use the planner's **Export › GeoJSON** and add the downloaded file under **Manage Map Layers**.

## 位置の共有

### 位置の共有を有効にする

ノードは、次のいずれかに基づいて GPS 位置を共有します：

- **固定間隔：** 一定間隔で位置をブロードキャストします
- **スマート位置：** 移動がしきい値を超えたときにブロードキャストします
- **手動：** 明示的に要求されたときのみ共有します

位置の動作は「**設定 → 位置**」で設定します。

### プライバシーに関する注意

> 🔒 **プライバシー：** 位置データは、チャンネル上のすべてのノードにブロードキャストされます。 位置を共有したくない場合は、設定で GPS 位置を無効にするか、固定／ダミーの位置を使用してください。

## マップソース

Every build offers a base map picker from the map toolbar. **Google Play** builds open on Google's own
map types; **F-Droid** and **Desktop** builds open on MapLibre's vector styles. Further down the base map
picker, all three offer the same raster base maps:

| Base map                              | 備考                                                                |
| ------------------------------------- | ----------------------------------------------------------------- |
| Normal / Satellite / Terrain / Hybrid | Google Play only — Google's own map types                         |
| Liberty                               | Default on F-Droid and Desktop. Vector street map |
| Positron                              | Low-contrast vector map; keeps node markers legible over it       |
| ダーク                                   | Vector map suited to dark themes                                  |
| OpenStreetMap                         | Classic raster street tiles                                       |
| OpenTopoMap                           | Raster topographic                                                |
| USGS Topo / USGS Imagery              | US coverage only                                                  |
| Esri Topo / Esri Imagery              | Topographic and satellite imagery                                 |

Overlays can be toggled on top of any base map, from the layers sheet:

- **Weather radar** — NOAA NEXRAD reflectivity (US coverage)
- **Hillshade** — terrain relief, on **F-Droid** and **Desktop** only. Useful for understanding why a
  link fails, since LoRa range is limited by terrain

### Adding your own tile source

Any XYZ tile endpoint can be added as a base map, on every flavor and on desktop. Open **Manage custom
tile sources** at the foot of the base map picker and paste a URL template using `{z}`, `{x}` and `{y}`
— plus `{s}` if the provider uses rotating subdomains. A national mapping service, for example:

```
https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg
```

Tiles are cached on disk, so panning does not re-download what you were just looking at.

On **Android**, the same screen also imports a local `.mbtiles` archive for fully offline use.

Offline area downloads are **F-Droid only**: cache the visible region from the layers sheet.
**Google Play** builds import pre-made MBTiles files instead, and **Desktop** has neither.

## 関連トピック

- [ノード](nodes)：ノードリストの表示と絞り込み
- [ノードメトリクス](node-metrics)：各ノードの信号品質と位置履歴
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [単位とロケール](units-and-locale)：距離と座標の表示形式
