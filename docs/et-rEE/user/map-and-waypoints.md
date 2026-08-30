---
title: Kaart ja teekonnapunktid
parent: Kasutusjuhend
nav_order: 6
last_updated: 2026-08-29
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

Ujuv tööriistariba pakub kiiret juurdepääsu kompassile, kihtide vahetamisele, sõlmefiltritele, värskendamisele ja asukoha jälgimisele. Põhjasuuna muutmiseks puuduta kompassi või praeguse asukoha keskpunkti seadmiseks asukohanuppu.

![Map screen with the floating toolbar open, showing compass, layers, and location controls](../../assets/screenshots/map_controls_overlay.png)

## Teekonnapunkt

Waypoints are shared points of interest, visible to everyone the waypoint is sent to.

### Loo teekonnapunkt

1. Touch & hold the map at the desired location.
2. Sisestage nimi ja valikuline kirjeldus.
3. Vali teekonnapunktile ikoon/emoji.
4. Puuduta **Saada** jagamiseks kärgvõrku.

Teekonnapunkte adresseeritakse nagu sõnumeid: vaikimisi edastatakse neid põhikanalil, kuid teekonnapunkti saab saata ka kindlal kanalil või otsesõnumina üksikule sõlmele.

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

Kuna teekonnapunktid (ja nende geopiirded) edastatakse kogu kärgvõrgule, teavitatakse vaikimisi ainult **loojat**. Kui keegi teine ​​jagab sinuga geopiirdega teekonnapunkti, pakub selle detailvaade valikut **„Teavita mind ületamisest”**, et saaksid selle kohta ka sisenemis-/väljumishoiatusi.

### Managing Waypoints

- Puuduta kaardil teekonnapunkti, et vaadata selle üksikasju ja koordinaate
- Edit or delete waypoints you created
- **Locked waypoints** cannot be modified or deleted by other mesh members — only the creator can change them
- Unlocked waypoints can be edited by any mesh member

## Kaardikihid

Tap the layers icon on the map to open **Manage Map Layers**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format — including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Add one by picking a file with **Add Layer**, opening a file with Meshtastic, or sharing it into the app from another app. Imporditud kihid on loetletud koos lülitiga iga kihi kuvamiseks/peitmiseks ja valikuga selle eemaldamiseks. This works on the Google Play build, the F-Droid build, and **Desktop**, which shares the same layer store and file picker.

### Saidi planeerija

**Asukoha planeerija** hindab saatja raadiosageduslikku leviala ja joonistab selle kaardile värvikoodiga kihina. Ava see kaardihalduselemendist või sõlme detaillehelt valiku **Hinnatud katvus** kaudu (kuvatakse ainult teadaoleva asukohaga sõlmede puhul). Konfi saatja (asukoht, sagedus, saatja võimsus, antenni võimendus ja kõrgus), vastuvõtja (tundlikkus, kõrgus) ja simulatsioonivalikud (maksimaalne ulatus, kõrge eraldusvõimega maastik, värvipalett) ning seejärel käivita hinnang. Like map layers, Site Planner works on both the Google Play and F-Droid builds, where the finished estimate is drawn on the map as a coverage overlay. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, use the planner's **Export › GeoJSON** and add the downloaded file under **Manage Map Layers**.

## Position Sharing

### Enabling Position Sharing

Sõlm jagab oma GPS asukohta järgmise alusel:

- **Fikseeritud intervall** – levitamine regulaarsete intervallidega
- **Nutikas asukoht** – levitatakse, kui liikumine ületab lävendi
- **Manual** — only share when explicitly requested

Asukoha käitumist saab seadistada menüüs **Seaded → Asukoht**.

### Privacy Considerations

> 🔒 **Privaatsus:** asukoha andmed levitatakse kõigile sinu kanali sõlmedele. Kui sa ei soovi, et sinu asukohta jagataks, keela GPS asukohta seadetes või kasuta fikseeritud/võltsasukohta.

## Kaardi allikad

Every build offers a base map picker from the map toolbar. **Google Play** builds open on Google's own
map types; **F-Droid** and **Desktop** builds open on MapLibre's vector styles. Further down the base map
picker, all three offer the same raster base maps:

| Base map                              | Sõnumid                                                           |
| ------------------------------------- | ----------------------------------------------------------------- |
| Normal / Satellite / Terrain / Hybrid | Google Play only — Google's own map types                         |
| Liberty                               | Default on F-Droid and Desktop. Vector street map |
| Positron                              | Low-contrast vector map; keeps node markers legible over it       |
| Tume                                  | Vector map suited to dark themes                                  |
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

## Seotud teemad

- [Sõlmed](nodes) — vaata ja filtreeri oma sõlmede loendit
- [Node Metrics](node-metrics) — signal quality and position history for individual nodes
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [Ühikud ja lokaat](units-and-locale) — kauguse ja koordinaatide kuvamise ühikud
