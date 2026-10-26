---
title: Kartta ja reittipisteet
nav_order: 6
last_updated: 2026-10-24
description: Näytä radioiden sijainnit kartalla, luo ja jaa reittipisteitä, hallitse karttatasoja ja Site Planneria sekä säädä sijainnin jakamista ja tietosuoja-asetuksia.
aliases:
  - kartta
  - reittipisteet
  - gps
  - sijainti
  - site-planner
  - karttatasot
  - geojson
  - kml
---

# Kartta ja reittipisteet

Karttanäkymä näyttää mesh-verkkosi radioiden maantieteelliset sijainnit sekä jaetut reittipisteet.

## Karttanäkymä

Kartta näyttää:

- **Radion sijainnit** — kunkin sijaintia lähettävän radion värilliset merkit
- **Reittipisteet** — jaetut kiinnostavat kohteet
- **Oma sijaintisi** — nykyinen GPS-sijaintisi

### Radioiden merkinnät

Jokainen sijaintinsa raportoiva radio näytetään **radiotunnisteena**, jossa näkyy radion lyhyt nimi. Tunniste väritetään radion oman tunnistevärin mukaan (pysyvä väri, joka muodostetaan radion numerosta) — sama radiotunniste näkyy myös radioluettelossa, joten radio näyttää kaikkialla samalta. Merkin väri **ei** ilmaise, onko radio verkossa vai poissa verkosta. Kun radion sijainti päivittyy reaaliajassa, sen merkki sykkii hetken. Tapping a chip opens that node's details.

Nodes within 5 m of each other, which is common when they share a reduced position precision or sit at one site, are drawn as one chip with a count on its corner, showing a favorite or your own node when one is there. Tapping it lists every node at that spot, favorites first. Lähekkäiset merkit ryhmitellään, kun loitonnat karttaa. Tapping a cluster zooms in to fit its nodes, and a cluster whose nodes all share one spot lists them instead. With **Show Precision Circles** on, each spot's circle stays visible inside a cluster too.

### Kartan hallinta

- **Zoomaus** — nipistä tai käytä +/- -painikkeita
- **Panorointi** — vedä karttaa
- **Center** — tap the location button to follow your position
- **Radion napautus** — avaa radion tiedot napauttamalla merkkiä

The floating toolbar provides quick access to the compass, the map type and layers pickers, node filters, Site Planner, and location tracking. Tap the compass to reorient north-up, or tap the location button to follow your current position. While the map follows you, the compass instead turns heading lock on and off, so the map rotates with the phone. On **Google Play** builds a refresh button joins them while a network layer is showing; on **F-Droid** and **Desktop**, refresh a network layer from its own row in the layers sheet instead.

![Map floating toolbar with compass, filter, refresh, and location controls](../../assets/screenshots/map_controls_overlay.png)

### Filtering the Map

Tap the filter button in the floating toolbar to open **Filter map**. **Display** controls what is drawn: **Only Favorites**, **Show Waypoints**, **Show Precision Circles**, and a slider that hides nodes not heard from recently. **Node roles** is a chip per device role, plus **All** to show every role; a selected chip means that role is shown. **Nodes** narrows the set further with **Hide offline nodes**, **Only show direct nodes**, **Signed only**, **Encrypted only**, **Exclude MQTT**, **Show ignored nodes**, and **Include unknown** — the same rules as the node list's [filter toggles](nodes).

A dot on the filter button means at least one filter is hiding nodes — check it before concluding the mesh is quiet. Turning **Show Waypoints** off hides every waypoint, including your own. **Show ignored nodes** adds them to the map rather than showing only them — unlike the node list's **Only show ignored Nodes**.

## AR mesh view

The mesh view draws nodes and waypoints over the phone's camera, each in the direction and at the height where it stands, with its distance and the age of its position. It needs a back camera and an orientation sensor, so it appears only on Android phones that have both, on Google Play and F-Droid builds alike. It isn't available on Desktop. On opening, it asks for camera access and then location.

Tap the mesh view button (**Open mesh view**) in the map's top bar to see the whole mesh, filtered by the same **Filter map** settings as the map. The node list's touch & hold menu, a node's detail page, its compass sheet, its position log, and a traceroute map open the view too. Opened from a node, it shows only that node, plus the track or route you came from.

- **Tap** a node to open its detail page, or a waypoint to open its info.
- **Touch & hold** a node or waypoint to find it. The view says how far to turn and tilt, the phone ticks as you turn closer, and it pulses once you are on target.
- A node whose position uncertainty covers your own spot has no meaningful direction, so it goes on the radar at the bottom instead. Point the phone down to enlarge the radar.
- Waypoint geofences are outlined, and the view says when you are inside one.

When your phone's position has no altitude, targets sit level with you and the view says elevation is approximate. Without camera access, or while the phone is too hot, the view carries on as a compass.

## Reittipisteet

Waypoints are shared points of interest, visible to everyone on your mesh.

### Reittipisteen luominen

Your node must be connected — the map ignores a touch & hold while it is not, because saving a waypoint means broadcasting it.

1. Kosketa karttaa halutusta kohdasta ja pidä sormi paikallaan.
2. Anna nimi ja valinnainen kuvaus.
3. Valitse reittipisteelle kuvake tai emoji.
4. Napauta **Lähetä** jakaaksesi sen verkkoon.

Waypoints always broadcast to the whole mesh on the primary channel. Unlike a message, a waypoint cannot be addressed to one channel or sent as a direct message.

### Reittipisteen ominaisuudet

| Ominaisuus      | Kuvaus                                                                                             |
| --------------- | -------------------------------------------------------------------------------------------------- |
| Nimi            | Lyhyt tunniste (enintään 29 merkkiä)                                            |
| Kuvaus          | Valinnainen pidempi kuvaus                                                                         |
| Kuvake          | Visuaalinen merkkiemoji kartalla                                                                   |
| Lukittu         | Jos lukittu, vain merkin luonut voi muokata tai poistaa sen                                        |
| Voimassaoloaika | Valinnainen automaattinen poistopäivä ja -aika                                                     |
| Aluerajaus      | Valinnainen saapumis-/poistumishälytysalue — katso [Reittipisteiden geoaidat](#waypoint-geofences) |

### Reittipisteen vanheneminen

A waypoint stays until it is deleted unless you turn on **Expires** in the waypoint editor. The switch starts the expiry eight hours out; change it with the **Date** and **Time** pickers. Once that time passes, the waypoint is removed, which suits temporary markers such as rally points, hazards, or meeting locations.

Vanhentuneet reittipisteet piilotetaan automaattisesti kartalta, jotta näkymä pysyy selkeänä. Poistumisen ajastus perustuu valitsemaasi päivämäärään ja kellonaikaan, ei siihen, kuinka kauan reittipiste on ollut olemassa tai vastaanotettuna.

### Reittipisteiden aluerajaukset

Mikä tahansa reittipiste voidaan määrittää myös **aluerajaukseksi** eli ilmoitusalueeksi, jolloin sinä tai muut käyttäjät saatte ilmoituksen, kun radio saapuu alueelle tai poistuu sieltä:

1. Määritä **aluerajauksen säde** valmiista vaihtoehdoista (tai valitse **Pois** poistaaksesi toiminnon käytöstä), tai napauta **Määritä alue kartalla** piirtääksesi mukautetun suorakulmaisen alueen.
2. Kun alue on määritetty, ota käyttöön **Ilmoita saapuessa** ja/tai **Ilmoita poistuttaessa**.
3. Voit halutessasi ottaa käyttöön **Vain suosikit** -asetuksen, jolloin ilmoituksia näytetään vain suosikkiradioistasi.

Koska reittipisteet (ja niiden aluerajaukset) lähetetään koko mesh-verkkoon, oletusarvoisesti ilmoitukset saa vain niiden **luoja**. If someone else shares a geofenced waypoint with you, its detail view offers a **Notify me of crossings** opt-in so you can also receive enter/exit alerts for it.

### Reittipisteiden hallinta

- Tap a waypoint to see its name, description, and geofence radius. On **Google Play** builds the first tap opens the marker's info bubble — tap the bubble to open the waypoint itself
- **Locked waypoints** can only be changed on the mesh by the node that locked them
- Unlocked waypoints can be edited by any mesh member while connected to a node — saving re-broadcasts the waypoint
- Confirming a delete removes your own copy. To remove it from everyone else's map too, select **Delete for everyone** in the delete dialog; that box appears only for a waypoint you may change (unlocked, or locked by you) and only while you are connected

## Karttatasot

Napauta kartan tasokuvaketta avataksesi **Hallitse karttatasoja**. It imports your own overlays in `.kml`, `.kmz`, or GeoJSON format, including KMZ ground overlays (georeferenced images, such as exported topo or aerial tiles), which drape at their stated bounds. Lisää sellainen valitsemalla tiedosto **Lisää taso** -toiminnolla, avaamalla tiedosto Meshtasticissa tai jakamalla se sovellukseen toisesta sovelluksesta. A file opened or shared this way is refused if it is over 50 MB. **Add Network Layer** instead takes a name and an `https://` URL pointing at a KML or GeoJSON file (`http://` also works on Desktop, but on Android only for `localhost`); that layer then carries its own refresh button in the sheet. On **Google Play** builds the toolbar's refresh button re-fetches every visible network layer at once.

Tuodut karttatasot näkyvät luettelossa, jossa voit näyttää tai piilottaa ne sekä poistaa ne. Each layer — imported or built-in overlay — carries its own opacity slider while it is switched on, so an overlay can be faded back rather than only switched off. Tämä toimii Google Play -versiossa, F-Droid-versiossa ja **Desktopissa**, joissa käytetään samaa karttatasojen tallennusta ja tiedostonvalitsinta.

### Site Planner

**Site Planner** arvioi lähettimen kuuluvuusalueen ja piirtää sen kartalle värikoodattuna peittoalueena. Open it from the Site Planner button in the map toolbar, or from a node's detail page with **Estimate coverage** (shown only for nodes with a known position). The form has four sections:

- **Site / Transmitter**: name, latitude and longitude, **TX power (W)**, **Frequency (MHz)**, **Antenna height (m)**, and **Antenna gain (dBi)**. Chips such as **Use current node location** and **Use map center** fill in the coordinates.
- **Receiver**: **Receiver sensitivity (dBm)** and **Receiver height (m)**.
- **Simulation Options**: **Max range (km)**, from 1 to 150.
- **Display**: **Color palette**.

Tap **Estimate coverage** to run it. The app computes the estimate itself, using the ITU-R P.1812 propagation model over Mapterhorn terrain, on Google Play, F-Droid, and Desktop alike. The terrain downloads the first time an area is estimated, so that step needs a network connection; if it fails, the form comes back with a note to check your connection. The finished estimate is added under **Manage Map Layers** as a coverage estimate layer, and the map moves to it.

## Sijainnin jakaminen

### Sijainnin jakamisen käyttöönotto

Radiosi jakaa GPS-sijaintinsa seuraavilla tavoilla:

- **Broadcast Interval** — share the position on a fixed timer
- **Smart Position** — share only once you have moved far enough; **Minimum Interval** sets the shortest gap between broadcasts and **Minimum Distance** how far you must move
- **Fixed Position** — publish a latitude, longitude, and altitude you enter by hand instead of the GPS reading
- **GPS Mode** — **Enabled**, **Disabled**, or **Not Present** on this hardware; offered only while **Fixed Position** is off

Configure position behavior in **Settings → Device configuration → Position**, and saving it reboots the node. Without a connection the screen opens read-only, with a prompt to connect before changing anything. For the full field list, see [Settings — Radio & User](settings-radio-user).

### Tietosuoja

> 🔒 **Yksityisyys:** sijaintitiedot lähetetään kaikille saman kanavan radioille. If you don't want your location shared, set **GPS Mode** to **Disabled**, or use a **Fixed Position** somewhere else. To keep sharing a position without pinpointing yourself, edit the channel in **Settings → Channels**, turn **Precise location** off, and set the slider beneath it — the channel then publishes an approximate area, shown as ± a distance, instead of an exact point.

## Karttalähteet

Jokaisessa versiossa kartan työkalurivillä on karttapohjan valitsin. **Google Play** -versioissa käytetään Googlen omia karttatyyppejä. **F-Droid**- ja **Työpöytä**-versioissa käytetään MapLibren vektorityylejä. Alempana karttapohjan valitsimessa kaikki tarjoavat samat rasterikarttapohjat:

| Karttapohja                               | Viestit                                                                                                            |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Normaali / Satelliitti / Maasto / Hybridi | Vain Google Play – Googlen omat karttatyypit                                                                       |
| Liberty                                   | Oletus F-Droid- ja Desktop-versioissa. Vektorinen tiekartta                                        |
| Positron                                  | F-Droid and Desktop only. Vähäkontrastinen vektorikartta, jonka päällä radiomerkit erottuvat hyvin |
| Tumma                                     | F-Droid and Desktop only. Tummiin teemoihin sopiva vektorikartta                                   |
| OpenStreetMap                             | Perinteiset rasterimuotoiset tiekarttaruudut                                                                       |
| OpenTopoMap                               | Rasterimuotoinen topografinen kartta                                                                               |
| USGS Topo / USGS Imagery                  | Vain Yhdysvaltojen kattavuus                                                                                       |
| Esri Topo / Esri Imagery                  | Topografinen kartta ja satelliittikuvat                                                                            |

Peitekuvat voidaan ottaa käyttöön minkä tahansa karttapohjan päällä karttatasovalikosta:

- **Säätutka** — NOAA NEXRAD -heijastavuus (Yhdysvaltojen kattavuus)
- **Hillshade** — terrain relief from an online tile source, on **F-Droid** and **Desktop** only; Google
  Play draws hillshade from downloaded offline terrain instead. Useful for understanding why a link
  fails, since LoRa range is limited by terrain

### Oman karttatiilipalvelun lisääminen

Mikä tahansa XYZ-karttatiilipalvelu voidaan lisätä karttapohjaksi kaikkiin versioihin, myös työpöytiin. Open **Manage Custom
Tile Sources** at the foot of the base map picker and paste a URL template using `{z}`, `{x}` and `{y}`
— plus `{s}` if the provider uses rotating subdomains. Esimerkiksi kansallinen karttapalvelu:

```text
https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg
```

On **Android** the template must use `https://`; plain `http://` works only for `localhost`.

Karttatiilet tallennetaan välimuistiin levylle, joten kartan siirtäminen ei lataa juuri katsottua aluetta uudelleen.

**Androidissa** tällä samalla näkymällä voidaan tuoda myös paikallinen `.mbtiles`-arkisto täysin offline-käyttöä varten.

On **F-Droid**, select a vector base map first — Liberty, Positron, or Dark — since a download is defined
against a vector style and **Start Download** stays disabled over a raster one. Frame the area you want on
screen, then tap **Start Download** in the layers sheet's **Cache Manager** section: that creates a paused pack
covering the current zoom plus two levels deeper. Press play on the pack's row to download it. While the offline
database opens, the section shows a progress bar. If it cannot be opened, the section reads **Offline maps are
unavailable on this device** and **Start Download** stays disabled. If the area cannot be saved, the section
reads **Couldn't save this area for offline use**.

On **Google Play**, the layers sheet's **Offline Manager** section downloads its own offline regions instead
— water, roads and administrative boundaries extracted from the public Protomaps basemap dataset, drawn
directly as map shapes rather than raster tiles. Frame the area you want, tap **Start Download**, and once
it finishes turn on **Show on map** to draw it. The switch is off by default, so a download never covers up the
live map unasked. **Desktop** has no offline base map downloads, though the offline terrain described next
does work there.

### Offline terrain

Hillshade and elevation contours can be downloaded for offline use as well, so a downloaded area still
shows why a link fails when there is no network. The terrain data comes from Mapterhorn and is credited on
the map. On **Google Play**, each downloaded region in the **Offline Manager** list has a **Download Terrain**
button. Terrain attaches to that region and shares its storage limit, and an area too large for terrain asks
you to zoom in or pick a smaller region. Once the terrain is downloaded, the button gives way to **Hillshade**
and **Contours** switches, both off until you turn them on. On **F-Droid** and **Desktop**, the layers sheet
has an **Offline Terrain** section of its own: frame the area, tap **Start Download**, and the hillshade and
contours draw automatically from then on, whenever the downloaded area is in view.

### Going offline

A pill reading **Offline — showing cached map data** appears over the map whenever the device has no network connection. On **Google Play**
builds, if you've imported a local `.mbtiles` archive, the map switches to it automatically the moment the
network drops — no toggle to remember — and switches back once you're reconnected, as long as you haven't
picked a different base map yourself in between. On **F-Droid**, a downloaded offline area keeps rendering on
its own; the pill is purely informational there. **Desktop** downloads offline terrain but no base-map
packs, so the pill is informational there too.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — tarkastele ja suodata radiolistaa
- [Radion mittarit](node-metrics) — signaalin laatu ja sijaintihistoria yksittäisille radioille
- [Paikallinen mesh-verkon etsintä](discovery) — reitiselvitys ja naapuritiedot mesh-verkon rakenteen hahmottamiseen
- [Yksiköt ja kieliasetukset](units-and-locale) — etäisyys- ja koordinaattien näyttömuodot
