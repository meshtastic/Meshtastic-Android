---
title: Карта и путевые точки
parent: Руководство пользователя
nav_order: 6
last_updated: 2026-08-29
description: Просматривайте расположение нод на карте, создавайте и делитесь путевыми точками, управляйте слоями карты и планировщиком участков, а также контролируйте передачу геоданных и приватность.
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

# Карта и путевые точки

Экран карты показывает географическое положение нод твоей mesh-сети, а также общие путевые точки.

## Вид карты

На карте отображаются:

- **Расположение нод** — цветные маркеры для каждой ноды, передающей свои координаты
- **Путевые точки** — общие точки интереса
- **Твоё местоположение** — твоё текущее местоположение по GPS

### Маркеры нод

Каждая нода, передающая своё местоположение, отображается в виде **маркера-бирки** с коротким именем ноды. Цвет бирки определяется собственным цветом идентификации ноды (постоянным цветом, получаемым из её номера) — тем же самым, что используется в списке нод, поэтому нода везде выглядит одинаково. Цвет маркера **не** отображает статус в сети/не в сети. Когда местоположение ноды обновляется в реальном времени, её маркер ненадолго подсвечивается пульсацией. Близко расположенные маркеры группируются в кластеры при уменьшении масштаба.

### Управление картой

- **Масштаб** — используйте щипок или кнопки +/-
- **Перемещение** — перетаскивайте карту для исследования
- **Center** — tap the location button to center on your position
- **Нажатие на ноду** — коснитесь маркера ноды для просмотра подробностей

Плавающая панель инструментов предоставляет быстрый доступ к компасу, переключению слоёв, фильтрам нод, обновлению и отслеживанию местоположения. Нажмите на компас, чтобы сориентировать карту на север, или нажмите кнопку местоположения, чтобы отцентрироваться на твоей текущей позиции.

![Map screen with the floating toolbar open, showing compass, layers, and location controls](../../assets/screenshots/map_controls_overlay.png)

## Путевые точки

Waypoints are shared points of interest, visible to everyone the waypoint is sent to.

### Создание путевой точки

1. Touch & hold the map at the desired location.
2. Введите имя и, при желании, описанье.
3. Выберите значок/эмодзи для путевой точки.
4. Нажмите **"Отправить"**, чтобы поделиться с mesh-сетью.

Путевые точки адресуются так же, как сообщения: по умолчанию они передаются в основном канале, но путевую точку также можно отправить на конкретном канале или как личное сообщение одной ноде.

### Свойства путевой точки

| Свойство        | Описание                                                                       |
| --------------- | ------------------------------------------------------------------------------ |
| Имя             | Короткий идентификатор (не более 29 символов)               |
| Описание        | Необязательное более длинное описание                                          |
| Значок          | Визуальный эмодзи маркер на карте                                              |
| Заблокировано   | Если заблокировано, только создатель может изменить или удалить                |
| Истечение срока | Необязательная дата и время автоматического удаления                           |
| Геозона         | Optional enter/exit alert area — see [Waypoint Geofences](#waypoint-geofences) |

### Истечение срока путевой точки

Для путевых точек можно настроить автоматическое истечение срока:

- **Никогда** (по умолчанию) — путевая точка остаётся, пока не будет удалена вручную
- **По времени** — выберите конкретные дату и время; путевая точка будет автоматически удалена, как только это время наступит. Полезно для временных меток, таких как точки сбора, опасности или места встреч.

Истёкшие путевые точки автоматически скрываются с карты, чтобы не загромождать отображение. Обратный отсчёт до истечения срока основан на заданном тобою абсолютном времени, а не на промежутке с момента создания или получения путевой точки.

### Геозоны путевых точек

Любая путевая точка может также задавать **геозону** — область оповещения, — чтобы ты или другие участники получали уведомления, когда нода входит в неё или покидает её:

1. Задайте **радиус геозоны** с помощью готовых вариантов (или **"Выкл"**, чтобы отключить), либо нажмите **"Задать область на карте"**, чтобы нарисовать собственную прямоугольную область.
2. После задания области включите переключатели **"Оповещать при входе"** и/или **"Оповещать при выходе"**.
3. При необходимости включите **"Только избранные"**, чтобы ограничить оповещения только избранными нодами.

Поскольку путевые точки (и их геозоны) передаются всей mesh-сети, по умолчанию оповещается только **создатель**. Если кто-то другой поделился с вами путевой точкой с геозоной, в её подробностях отображается опция **"Оповещать меня о пересечениях"**, позволяющая тебе также получать оповещения о входе/выходе.

### Управление путевыми точками

- Нажмите на путевую точку на карте, чтобы просмотреть её сведения и координаты
- Редактируйте или удаляйте созданные тобою путевые точки
- **Locked waypoints** cannot be modified or deleted by other mesh members — only the creator can change them
- Незаблокированные путевые точки может редактировать любой участник mesh-сети

## Слои карты

Tap the layers icon on the map to open **Manage Map Layers**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format — including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Add one by picking a file with **Add Layer**, opening a file with Meshtastic, or sharing it into the app from another app. Импортированные слои отображаются в списке с переключателем для показа/скрытия каждого и возможностью удалить слой. This works on the Google Play build, the F-Droid build, and **Desktop**, which shares the same layer store and file picker.

### Планировщик участков

**Планировщик участков** оценивает радиочастотное покрытие для передатчика и отображает его на карте в виде цветового наложения. Откройте его с панели управления картой или со страницы сведений о ноде через **"Оценку покрытия"** (отображается только для нод с известным местоположением). Настройте передатчик (местоположение, частота, мощность передачи, усиление и высота антенны), приёмник (чувствительность, высота) и параметры симуляции (максимальная дальность, рельеф высокого разрешения, цветовая палитра), затем запустите расчёт. Like map layers, Site Planner works on both the Google Play and F-Droid builds, where the finished estimate is drawn on the map as a coverage overlay. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, use the planner's **Export › GeoJSON** and add the downloaded file under **Manage Map Layers**.

## Передача геоданных

### Включение передачи геоданных

Твоя нода передаёт свои GPS-координаты на основе:

- **Фиксированный интервал** — передача координат через равные промежутки времени
- **Адаптивная передача** — передача при превышении порога движения
- **Вручную** — делиться только по явному запросу

Настройте поведение передачи геоданных в **Настройки → Геоданные**.

### Вопросы приватности

> 🔒 **Приватность:** Данные о местоположении передаются всем нодам на твоём канале. Если ты не хочешь раскрывать своё местоположение, отключите GPS в настройках или используйте фиксированное/фиктивное местоположение.

## Источники карт

Every build offers a base map picker from the map toolbar. **Google Play** builds open on Google's own
map types; **F-Droid** and **Desktop** builds open on MapLibre's vector styles. Further down the base map
picker, all three offer the same raster base maps:

| Base map                              | Заметки                                                           |
| ------------------------------------- | ----------------------------------------------------------------- |
| Normal / Satellite / Terrain / Hybrid | Google Play only — Google's own map types                         |
| Liberty                               | Default on F-Droid and Desktop. Vector street map |
| Positron                              | Low-contrast vector map; keeps node markers legible over it       |
| Темная                                | Vector map suited to dark themes                                  |
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

## Связанные темы

- [Ноды](nodes) — просмотр и фильтрация списка нод
- [Метрики нод](node-metrics) — качество сигнала и история местоположения для отдельных нод
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [Единицы измерения и регион](units-and-locale) — форматы отображения расстояний и координат
