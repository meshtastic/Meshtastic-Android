---
title: マップとウェイポイント
parent: User Guide
nav_order: 6
last_updated: 2026-09-01
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

The floating toolbar provides quick access to the compass, the map type and layers pickers, node filters, Site Planner, and location tracking. コンパスをタップすると北を上に向け直し、位置ボタンをタップすると現在の位置を中央に表示します。 On **Google Play** builds a refresh button joins them while a network layer is showing; on **F-Droid** and **Desktop**, refresh a network layer from its own row in the layers sheet instead.

![Map floating toolbar with compass, filter, refresh, and location controls](../../assets/screenshots/map_controls_overlay.png)

### Filtering the Map

Tap the filter button in the floating toolbar to open **Filter map**. **Display** controls what is drawn: **Only Favorites**, **Show Waypoints**, **Show Precision Circles**, and a slider that hides nodes not heard from recently. **Node roles** is a chip per device role, plus **All** to show every role; a selected chip means that role is shown. **Nodes** narrows the set further with **Hide offline nodes**, **Only show direct nodes**, **Exclude MQTT**, **Show ignored nodes**, and **Include unknown**.

A dot on the filter button means at least one filter is hiding something — check it before concluding the mesh is quiet. Turning **Show Waypoints** off hides every waypoint, including your own. **Show ignored nodes** adds them to the map rather than showing only them — unlike the node list's **Only show ignored Nodes**.

## ウェイポイント

Waypoints are shared points of interest, visible to everyone on your mesh.

### ウェイポイントを作成する

Your radio must be connected — the map ignores a touch & hold while it is not, because saving a waypoint means broadcasting it.

1. Touch & hold the map at the desired location.
2. 名前と、任意で説明を入力します。
3. ウェイポイントのアイコン／絵文字を選びます。
4. 「**送信**」をタップしてメッシュに共有します。

Waypoints always broadcast to the whole mesh on the primary channel. Unlike a message, a waypoint cannot be addressed to one channel or sent as a direct message.

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

ウェイポイント（およびそのジオフェンス）はメッシュ全体にブロードキャストされるため、デフォルトでは**作成者**だけに通知されます。 If someone else shares a geofenced waypoint with you, its detail view offers a **Notify me of crossings** opt-in so you can also receive enter/exit alerts for it.

### ウェイポイントを管理する

- Tap a waypoint to see its name, description, and geofence radius. On **Google Play** builds the first tap opens the marker's info bubble — tap the bubble to open the waypoint itself
- **Locked waypoints** can only be changed on the mesh by the node that locked them
- Unlocked waypoints can be edited by any mesh member while connected to a radio — saving re-broadcasts the waypoint
- Confirming a delete removes your own copy. To remove it from everyone else's map too, select **Delete for everyone** in the delete dialog; that box appears only for a waypoint you may change (unlocked, or locked by you) and only while you are connected

## マップレイヤー

Tap the layers icon on the map to open **Manage Map Layers**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format — including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Add one by picking a file with **Add Layer**, opening a file with Meshtastic, or sharing it into the app from another app. **Add Network Layer** instead takes a name and an `http://` or `https://` URL pointing at a KML or GeoJSON file; that layer then carries its own refresh button in the sheet. On **Google Play** builds the toolbar's refresh button re-fetches every visible network layer at once.

インポートしたレイヤーは、それぞれ表示／非表示を切り替えるトグルと、削除するオプションとともに一覧表示されます。 Each layer — imported or built-in overlay — carries its own opacity slider while it is switched on, so an overlay can be faded back rather than only switched off. This works on the Google Play build, the F-Droid build, and **Desktop**, which shares the same layer store and file picker.

### サイトプランナー

**サイトプランナー**は、送信機の RF カバレッジを推定し、色分けされたオーバーレイとしてマップに描画します。 マップの操作から開くか、ノードの詳細ページから「**カバレッジを推定**」で開きます（位置が判明しているノードでのみ表示されます）。 送信機（位置、周波数、送信出力、アンテナ利得と高さ）、受信機（感度、高さ）、シミュレーションのオプション（最大範囲、高解像度の地形、カラーパレット）を設定してから、推定を実行します。 Like map layers, Site Planner works on both the Google Play and F-Droid builds, where the finished estimate is drawn on the map as a coverage overlay. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, click the transmitter pin in the browser, choose the planner's GeoJSON export, then add the downloaded file under **Manage Map Layers** with **Add Layer**. Use the GeoJSON export, not the KML one — the KML is a ground-overlay image this map cannot draw.

## 位置の共有

### 位置の共有を有効にする

ノードは、次のいずれかに基づいて GPS 位置を共有します：

- **Broadcast Interval** — share the position on a fixed timer
- **Smart Position** — share only once you have moved far enough; **Smart Interval** sets the shortest gap between broadcasts and **Smart Distance** how far you must move
- **Fixed Position** — publish a latitude, longitude, and altitude you enter by hand instead of the GPS reading
- **GPS Mode (Physical Hardware)** — GPS enabled, disabled, or not present on this hardware; offered only while **Fixed Position** is off

Configure position behavior in **Settings → Device configuration → Position**. The screen is only reachable while your radio is connected, and saving it reboots the radio. For the full field list, see [Settings — Radio & User](settings-radio-user).

### プライバシーに関する注意

> 🔒 **プライバシー：** 位置データは、チャンネル上のすべてのノードにブロードキャストされます。 位置を共有したくない場合は、設定で GPS 位置を無効にするか、固定／ダミーの位置を使用してください。 To keep sharing a position without pinpointing yourself, edit the channel in **Settings → Channels**, turn **Precise location** off, and set the slider beneath it — the channel then publishes an approximate area, shown as ± a distance, instead of an exact point.

## マップソース

Every build offers a base map picker from the map toolbar. **Google Play** builds open on Google's own
map types; **F-Droid** and **Desktop** builds open on MapLibre's vector styles. Further down the base map
picker, all three offer the same raster base maps:

| Base map                              | 備考                                                                                                    |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Normal / Satellite / Terrain / Hybrid | Google Play only — Google's own map types                                                             |
| Liberty                               | Default on F-Droid and Desktop. Vector street map                                     |
| Positron                              | F-Droid and Desktop only. Low-contrast vector map; keeps node markers legible over it |
| ダーク                                   | F-Droid and Desktop only. Vector map suited to dark themes                            |
| OpenStreetMap                         | Classic raster street tiles                                                                           |
| OpenTopoMap                           | Raster topographic                                                                                    |
| USGS Topo / USGS Imagery              | US coverage only                                                                                      |
| Esri Topo / Esri Imagery              | Topographic and satellite imagery                                                                     |

Overlays can be toggled on top of any base map, from the layers sheet:

- **Weather radar** — NOAA NEXRAD reflectivity (US coverage)
- **Hillshade** — terrain relief, on **F-Droid** and **Desktop** only. Useful for understanding why a
  link fails, since LoRa range is limited by terrain

### Adding your own tile source

Any XYZ tile endpoint can be added as a base map, on every flavor and on desktop. Open **Manage Custom
Tile Sources** at the foot of the base map picker and paste a URL template using `{z}`, `{x}` and `{y}`
— plus `{s}` if the provider uses rotating subdomains. A national mapping service, for example:

```
https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg
```

Tiles are cached on disk, so panning does not re-download what you were just looking at.

On **Android**, the same screen also imports a local `.mbtiles` archive for fully offline use.

On **F-Droid**, select a vector base map first — Liberty, Positron, or Dark — since a download is defined
against a vector style and **Start Download** stays disabled over a raster one. Frame the area you want on
screen, then tap **Start Download** in the layers sheet: that creates a paused pack covering the current zoom
plus two levels deeper. Press play on the pack's row to actually download it.

On **Google Play**, the layers sheet's **Offline Manager** section downloads its own offline regions instead
— water, roads and administrative boundaries extracted from the public Protomaps basemap dataset, drawn
directly as map shapes rather than raster tiles. Frame the area you want, tap **Start Download**, and once
it finishes flip **Show on map** to draw it (it's off by default so it never surprises you by covering up the
live map). You can still import a pre-made `.mbtiles` archive here too, as before. **Desktop** has neither.

### Going offline

An **Offline** pill appears over the map whenever the device has no network connection. On **Google Play**
builds, if you've imported a local `.mbtiles` archive, the map switches to it automatically the moment the
network drops — no toggle to remember — and switches back once you're reconnected, as long as you haven't
picked a different base map yourself in between. On **F-Droid**, a downloaded offline area keeps rendering on
its own; the pill is purely informational there. **Desktop** has no offline downloads at all yet, so the pill
is informational there too.

## 関連トピック

- [ノード](nodes)：ノードリストの表示と絞り込み
- [ノードメトリクス](node-metrics)：各ノードの信号品質と位置履歴
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [単位とロケール](units-and-locale)：距離と座標の表示形式
