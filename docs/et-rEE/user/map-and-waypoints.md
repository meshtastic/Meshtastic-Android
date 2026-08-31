---
title: Kaart ja teekonnapunktid
parent: Kasutusjuhend
nav_order: 6
last_updated: 2026-08-30
description: Vaata sõlmede asukohti kaardil, loo ja jaga teekonnapunkte ning halda asukoha jagamist ja privaatsust.
aliases:
  - kaart
  - teekonnapunkt
  - gps
  - asukoht
  - saidi planeerija
  - kaardi-kihid
  - geojson
  - kml
---

# Kaart ja teekonnapunktid

Kaardiekraan näitab kärgvõrgu sõlmede geograafilisi asukohti koos jagatud teekonnapunktidega.

## Kaardi vaade

Kaardil kuvatakse:

- **Sõlmede asukohad** — värvilised markerid iga sõlme asukoha kohta
- **Waypoints** — shared points of interest
- **Teie asukoht** — teie praegune GPS asukoht

### Node Markers

Iga asukohta teavitav sõlm kuvatakse **sõlme kiibi** markerina, mis kuvab sõlme lühinime. Kiip on värvitud sõlme enda identiteedivärviga (stabiilne värv, mis on tuletatud sõlme numbrist) – sama kiip, mida kasutatakse sõlmede loendis, seega näeb sõlm kõikjal ühesugune välja. Markeri värv **ei kodeeri** võrguühenduseta/võrguühenduseta olekut. Kui sõlme asukoht reaalajas uueneb, pulseerib selle marker lühidalt. Nearby markers are clustered as you zoom out.

### Kaardi juhtnupud

- **Suumi** – näpista või +/- nuppude kasutamine
- **Pan** — drag to explore
- **Center** — tap the location button to center on your position
- **Sõlme puudutamine** – üksikasjade kuvamiseks puuduta sõlmel

The floating toolbar provides quick access to the compass, the map type and layers pickers, node filters, Site Planner, and location tracking. Põhjasuuna muutmiseks puuduta kompassi või praeguse asukoha keskpunkti seadmiseks asukohanuppu. On **Google Play** builds a refresh button joins them while a network layer is showing; on **F-Droid** and **Desktop**, refresh a network layer from its own row in the layers sheet instead.

![Map floating toolbar with compass, filter, refresh, and location controls](../../assets/screenshots/map_controls_overlay.png)

### Filtering the Map

Tap the filter button in the floating toolbar to open **Filter map**. **Display** controls what is drawn: **Only Favorites**, **Show Waypoints**, **Show Precision Circles**, and a slider that hides nodes not heard from recently. **Node roles** is a chip per device role, plus **All** to show every role; a selected chip means that role is shown. **Nodes** narrows the set further with **Hide offline nodes**, **Only show direct nodes**, **Exclude MQTT**, **Show ignored nodes**, and **Include unknown**.

A dot on the filter button means at least one filter is hiding something — check it before concluding the mesh is quiet. Turning **Show Waypoints** off hides every waypoint, including your own. **Show ignored nodes** adds them to the map rather than showing only them — unlike the node list's **Only show ignored Nodes**.

## Teekonnapunkt

Waypoints are shared points of interest, visible to everyone on your mesh.

### Loo teekonnapunkt

Your radio must be connected — the map ignores a touch & hold while it is not, because saving a waypoint means broadcasting it.

1. Touch & hold the map at the desired location.
2. Sisestage nimi ja valikuline kirjeldus.
3. Vali teekonnapunktile ikoon/emoji.
4. Puuduta **Saada** jagamiseks kärgvõrku.

Waypoints always broadcast to the whole mesh on the primary channel. Unlike a message, a waypoint cannot be addressed to one channel or sent as a direct message.

### Waypoint Properties

| Property   | Kirjeldus                                                                      |
| ---------- | ------------------------------------------------------------------------------ |
| Nimi       | Lühike identifikaator (max 29 tähemärki)                    |
| Kirjeldus  | Optional longer description                                                    |
| Ikoon      | Visuaalse markeri emotikon kaardil                                             |
| Lukustatud | If locked, only the creator can edit or delete                                 |
| Expiration | Optional auto-remove date and time                                             |
| Geopiire   | Optional enter/exit alert area — see [Waypoint Geofences](#waypoint-geofences) |

### Waypoint Expiration

Waypoints can be set to expire automatically:

- **Never** (default) — waypoint remains until manually deleted
- **Timed** — pick a specific date and time; the waypoint is automatically removed once that time passes. Kasulik ajutiste märkide, näiteks kogunemispunktide, ohtude või kohtumispaikade jaoks.

Aegunud teekonnapunktid peidetakse kaardilt automaatselt, et need ekraani ei risustaks. The expiration countdown is based on the absolute time you picked, not a duration from when the waypoint was created or received.

### Teekonnapunktide geopiirded

Iga teekonnapunkt saab määratleda ka **geopiirde** – hoiatusala –, et teid või teisi teavitataks, kui sõlm sinna siseneb või sealt lahkub:

1. Määra **geopiirde raadius** eelmääratletud kiipide hulgast (või keelamiseks **Väljas**) või puuduta kohandatud ristkülikukujulise ala joonistamiseks **Määra kaardile ala**.
2. Kui piirkond on määratud, lülita sisse **Teavita sisenemisel** ja/või **Teavita väljumisel**.
3. Soovi korral luba **Ainult lemmikud**, et piirata märguandeid oma lemmiksõlmedega.

Kuna teekonnapunktid (ja nende geopiirded) edastatakse kogu kärgvõrgule, teavitatakse vaikimisi ainult **loojat**. If someone else shares a geofenced waypoint with you, its detail view offers a **Notify me of crossings** opt-in so you can also receive enter/exit alerts for it.

### Managing Waypoints

- Tap a waypoint to see its name, description, and geofence radius. On **Google Play** builds the first tap opens the marker's info bubble — tap the bubble to open the waypoint itself
- **Locked waypoints** can only be changed on the mesh by the node that locked them
- Unlocked waypoints can be edited by any mesh member while connected to a radio — saving re-broadcasts the waypoint
- Confirming a delete removes your own copy. To remove it from everyone else's map too, select **Delete for everyone** in the delete dialog; that box appears only for a waypoint you may change (unlocked, or locked by you) and only while you are connected

## Kaardikihid

Tap the layers icon on the map to open **Manage Map Layers**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format — including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Add one by picking a file with **Add Layer**, opening a file with Meshtastic, or sharing it into the app from another app. **Add Network Layer** instead takes a name and an `http://` or `https://` URL pointing at a KML or GeoJSON file; that layer then carries its own refresh button in the sheet. On **Google Play** builds the toolbar's refresh button re-fetches every visible network layer at once.

Imporditud kihid on loetletud koos lülitiga iga kihi kuvamiseks/peitmiseks ja valikuga selle eemaldamiseks. Each layer — imported or built-in overlay — carries its own opacity slider while it is switched on, so an overlay can be faded back rather than only switched off. This works on the Google Play build, the F-Droid build, and **Desktop**, which shares the same layer store and file picker.

### Saidi planeerija

**Asukoha planeerija** hindab saatja raadiosageduslikku leviala ja joonistab selle kaardile värvikoodiga kihina. Ava see kaardihalduselemendist või sõlme detaillehelt valiku **Hinnatud katvus** kaudu (kuvatakse ainult teadaoleva asukohaga sõlmede puhul). Konfi saatja (asukoht, sagedus, saatja võimsus, antenni võimendus ja kõrgus), vastuvõtja (tundlikkus, kõrgus) ja simulatsioonivalikud (maksimaalne ulatus, kõrge eraldusvõimega maastik, värvipalett) ning seejärel käivita hinnang. Like map layers, Site Planner works on both the Google Play and F-Droid builds, where the finished estimate is drawn on the map as a coverage overlay. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, click the transmitter pin in the browser, choose the planner's GeoJSON export, then add the downloaded file under **Manage Map Layers** with **Add Layer**. Use the GeoJSON export, not the KML one — the KML is a ground-overlay image this map cannot draw.

## Position Sharing

### Enabling Position Sharing

Sõlm jagab oma GPS asukohta järgmise alusel:

- **Broadcast Interval** — share the position on a fixed timer
- **Smart Position** — share only once you have moved far enough; **Smart Interval** sets the shortest gap between broadcasts and **Smart Distance** how far you must move
- **Fixed Position** — publish a latitude, longitude, and altitude you enter by hand instead of the GPS reading
- **GPS Mode (Physical Hardware)** — GPS enabled, disabled, or not present on this hardware; offered only while **Fixed Position** is off

Configure position behavior in **Settings → Device configuration → Position**. The screen is only reachable while your radio is connected, and saving it reboots the radio. For the full field list, see [Settings — Radio & User](settings-radio-user).

### Privacy Considerations

> 🔒 **Privaatsus:** asukoha andmed levitatakse kõigile sinu kanali sõlmedele. Kui sa ei soovi, et sinu asukohta jagataks, keela GPS asukohta seadetes või kasuta fikseeritud/võltsasukohta. To keep sharing a position without pinpointing yourself, edit the channel in **Settings → Channels**, turn **Precise location** off, and set the slider beneath it — the channel then publishes an approximate area, shown as ± a distance, instead of an exact point.

## Kaardi allikad

Every build offers a base map picker from the map toolbar. **Google Play** builds open on Google's own
map types; **F-Droid** and **Desktop** builds open on MapLibre's vector styles. Further down the base map
picker, all three offer the same raster base maps:

| Base map                              | Sõnumid                                                                                               |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Normal / Satellite / Terrain / Hybrid | Google Play only — Google's own map types                                                             |
| Liberty                               | Default on F-Droid and Desktop. Vector street map                                     |
| Positron                              | F-Droid and Desktop only. Low-contrast vector map; keeps node markers legible over it |
| Tume                                  | F-Droid and Desktop only. Vector map suited to dark themes                            |
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

Offline area downloads are **F-Droid only**. Select a vector base map first — Liberty, Positron, or Dark —
since a download is defined against a vector style and **Start Download** stays disabled over a raster one.
Frame the area you want on screen, then tap **Start Download** in the layers sheet: that creates a paused
pack covering the current zoom plus two levels deeper. Press play on the pack's row to actually download it.
**Google Play** builds import pre-made MBTiles files instead, and **Desktop** has neither.

## Seotud teemad

- [Sõlmed](nodes) — vaata ja filtreeri oma sõlmede loendit
- [Node Metrics](node-metrics) — signal quality and position history for individual nodes
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [Ühikud ja lokaat](units-and-locale) — kauguse ja koordinaatide kuvamise ühikud
