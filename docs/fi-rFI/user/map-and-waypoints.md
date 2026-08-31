---
title: Kartta ja reittipisteet
parent: Käyttöopas
nav_order: 6
last_updated: 2026-08-30
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

Jokainen sijaintinsa raportoiva radio näytetään **radiotunnisteena**, jossa näkyy radion lyhyt nimi. Tunniste väritetään radion oman tunnistevärin mukaan (pysyvä väri, joka muodostetaan radion numerosta) — sama radiotunniste näkyy myös radioluettelossa, joten radio näyttää kaikkialla samalta. Merkin väri **ei** ilmaise, onko radio verkossa vai poissa verkosta. Kun radion sijainti päivittyy reaaliajassa, sen merkki sykkii hetken. Lähekkäiset merkit ryhmitellään, kun loitonnat karttaa.

### Kartan hallinta

- **Zoomaus** — nipistä tai käytä +/- -painikkeita
- **Panorointi** — vedä karttaa
- **Keskitä** — napauta sijaintipainiketta keskittääksesi kartan omaan sijaintiisi
- **Radion napautus** — avaa radion tiedot napauttamalla merkkiä

The floating toolbar provides quick access to the compass, the map type and layers pickers, node filters, Site Planner, and location tracking. Napauta kompassia suunnan palauttamiseksi pohjoiseen tai sijaintipainiketta keskittääksesi oman sijaintisi. On **Google Play** builds a refresh button joins them while a network layer is showing; on **F-Droid** and **Desktop**, refresh a network layer from its own row in the layers sheet instead.

![Map floating toolbar with compass, filter, refresh, and location controls](../../assets/screenshots/map_controls_overlay.png)

### Filtering the Map

Tap the filter button in the floating toolbar to open **Filter map**. **Display** controls what is drawn: **Only Favorites**, **Show Waypoints**, **Show Precision Circles**, and a slider that hides nodes not heard from recently. **Node roles** is a chip per device role, plus **All** to show every role; a selected chip means that role is shown. **Nodes** narrows the set further with **Hide offline nodes**, **Only show direct nodes**, **Exclude MQTT**, **Show ignored nodes**, and **Include unknown**.

A dot on the filter button means at least one filter is hiding something — check it before concluding the mesh is quiet. Turning **Show Waypoints** off hides every waypoint, including your own. **Show ignored nodes** adds them to the map rather than showing only them — unlike the node list's **Only show ignored Nodes**.

## Reittipisteet

Waypoints are shared points of interest, visible to everyone on your mesh.

### Reittipisteen luominen

Your radio must be connected — the map ignores a touch & hold while it is not, because saving a waypoint means broadcasting it.

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

Reittipisteet voidaan asettaa vanhenemaan automaattisesti:

- Ei koskaan (oletus) — reittipiste pysyy voimassa kunnes se poistetaan manuaalisesti
- **Ajastettu** — valitse tietty päivämäärä ja kellonaika. Reittipiste poistetaan automaattisesti, kun kyseinen ajankohta on ohitettu. Hyödyllinen tilapäisille merkinnöille kuten kokoontumispaikat, vaarat tai tapaamispaikat.

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
- Unlocked waypoints can be edited by any mesh member while connected to a radio — saving re-broadcasts the waypoint
- Confirming a delete removes your own copy. To remove it from everyone else's map too, select **Delete for everyone** in the delete dialog; that box appears only for a waypoint you may change (unlocked, or locked by you) and only while you are connected

## Karttatasot

Napauta kartan tasokuvaketta avataksesi **Hallitse karttatasoja**. Tuo omia peitekuvia `.kml`-, `.kmz`- tai GeoJSON-muodossa, mukaan lukien KMZ-maanpeitekuvat (georeferoidut kuvat, kuten viedyt topografiset tai ilmakuvat), jotka sijoitetaan kartalle niiden määritettyjen rajojen mukaisesti. Lisää sellainen valitsemalla tiedosto **Lisää taso** -toiminnolla, avaamalla tiedosto Meshtasticissa tai jakamalla se sovellukseen toisesta sovelluksesta. **Add Network Layer** instead takes a name and an `http://` or `https://` URL pointing at a KML or GeoJSON file; that layer then carries its own refresh button in the sheet. On **Google Play** builds the toolbar's refresh button re-fetches every visible network layer at once.

Tuodut karttatasot näkyvät luettelossa, jossa voit näyttää tai piilottaa ne sekä poistaa ne. Each layer — imported or built-in overlay — carries its own opacity slider while it is switched on, so an overlay can be faded back rather than only switched off. Tämä toimii Google Play -versiossa, F-Droid-versiossa ja **Desktopissa**, joissa käytetään samaa karttatasojen tallennusta ja tiedostonvalitsinta.

### Site Planner

**Site Planner** arvioi lähettimen kuuluvuusalueen ja piirtää sen kartalle värikoodattuna peittoalueena. Avaa se kartan ohjaimista tai radion tiedoista **Arvioi peittoalue** -toiminnolla (näkyy vain radioille, joilla on tunnettu sijainti). Määritä lähettimen asetukset (sijainti, taajuus, lähetysteho, antennin vahvistus ja korkeus), vastaanottimen asetukset (herkkyys ja korkeus) sekä simuloinnin asetukset (enimmäisetäisyys, korkean tarkkuuden maastomalli ja väripaletti), ja käynnistä sitten arviointi. Kuten karttatasotkin, Site Planner toimii sekä Google Play- että F-Droid-versioissa, joissa valmis arvio piirretään kartalle peitekuvana. On **Desktop** the same form is shown but the planner opens in your browser; to bring the estimate onto the map, click the transmitter pin in the browser, choose the planner's GeoJSON export, then add the downloaded file under **Manage Map Layers** with **Add Layer**. Use the GeoJSON export, not the KML one — the KML is a ground-overlay image this map cannot draw.

## Sijainnin jakaminen

### Sijainnin jakamisen käyttöönotto

Radiosi jakaa GPS-sijaintinsa seuraavilla tavoilla:

- **Broadcast Interval** — share the position on a fixed timer
- **Smart Position** — share only once you have moved far enough; **Smart Interval** sets the shortest gap between broadcasts and **Smart Distance** how far you must move
- **Fixed Position** — publish a latitude, longitude, and altitude you enter by hand instead of the GPS reading
- **GPS Mode (Physical Hardware)** — GPS enabled, disabled, or not present on this hardware; offered only while **Fixed Position** is off

Configure position behavior in **Settings → Device configuration → Position**. The screen is only reachable while your radio is connected, and saving it reboots the radio. For the full field list, see [Settings — Radio & User](settings-radio-user).

### Tietosuoja

> 🔒 **Yksityisyys:** sijaintitiedot lähetetään kaikille saman kanavan radioille. Jos et halua sijaintiasi jaettavan, poista GPS käytöstä asetuksista tai käytä kiinteää tai valesijaintia. To keep sharing a position without pinpointing yourself, edit the channel in **Settings → Channels**, turn **Precise location** off, and set the slider beneath it — the channel then publishes an approximate area, shown as ± a distance, instead of an exact point.

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
- **Varjostus** — maaston korkeuserot, vain **F-Droid**- ja **työpöytä**-versioissa. Hyödyllinen, kun halutaan ymmärtää, miksi LoRa-yhteys katkeaa maaston vuoksi

### Oman karttatiilipalvelun lisääminen

Mikä tahansa XYZ-karttatiilipalvelu voidaan lisätä karttapohjaksi kaikkiin versioihin, myös työpöytiin. Open **Manage Custom
Tile Sources** at the foot of the base map picker and paste a URL template using `{z}`, `{x}` and `{y}`
— plus `{s}` if the provider uses rotating subdomains. Esimerkiksi kansallinen karttapalvelu:

```
https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg
```

Karttatiilet tallennetaan välimuistiin levylle, joten kartan siirtäminen ei lataa juuri katsottua aluetta uudelleen.

**Androidissa** tällä samalla näkymällä voidaan tuoda myös paikallinen `.mbtiles`-arkisto täysin offline-käyttöä varten.

Offline area downloads are **F-Droid only**. Select a vector base map first — Liberty, Positron, or Dark —
since a download is defined against a vector style and **Start Download** stays disabled over a raster one.
Frame the area you want on screen, then tap **Start Download** in the layers sheet: that creates a paused
pack covering the current zoom plus two levels deeper. Press play on the pack's row to actually download it.
**Google Play** -versiot tuovat sen sijaan valmiita MBTiles-tiedostoja, eikä **työpöytä** tue kumpaakaan toimintoa.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — tarkastele ja suodata radiolistaa
- [Radion mittarit](node-metrics) — signaalin laatu ja sijaintihistoria yksittäisille radioille
- [Paikallinen mesh-verkon etsintä](discovery) — reitiselvitys ja naapuritiedot mesh-verkon rakenteen hahmottamiseen
- [Yksiköt ja kieliasetukset](units-and-locale) — etäisyys- ja koordinaattien näyttömuodot
