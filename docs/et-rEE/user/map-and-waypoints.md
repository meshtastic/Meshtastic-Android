---
title: Map & Waypoints
parent: User Guide
nav_order: 6
last_updated: 2026-07-08
description: View node positions on the map, create and share waypoints, manage map layers and Site Planner, and control position sharing and privacy.
aliases:
  - kaart
  - teekonnapunkt
  - gps
  - asukoht
  - site-planner
  - map-layers
  - geojson
  - kml
---

# Map & Waypoints

The Map screen shows the geographic positions of nodes on your mesh, along with shared waypoints.

## Map View

The map displays:

- **Node positions** — colored markers for each node reporting location
- **Waypoints** — shared points of interest
- **Your position** — your current GPS location

### Node Markers

Each node that reports a position is shown as a **node chip** marker displaying the node's short name. The chip is colored by the node's own identity color (a stable color derived from its node number) — the same chip used in the node list, so a node looks the same everywhere. Marker color does **not** encode online/offline status. Kui sõlme asukoht reaalajas uueneb, pulseerib selle marker lühidalt. Nearby markers are clustered as you zoom out.

### Map Controls

- **Suumi** – näpista või +/- nuppude kasutamine
- **Pan** — drag to explore
- **Center** — select the location button to center on your position
- **Sõlme puudutamine** – üksikasjade kuvamiseks puuduta sõlmel

Ujuv tööriistariba pakub kiiret juurdepääsu kompassile, kihtide vahetamisele, sõlmefiltritele, värskendamisele ja asukoha jälgimisele. Põhjasuuna muutmiseks puuduta kompassi või praeguse asukoha keskpunkti seadmiseks asukohanuppu.

![Map controls overlay](../../assets/screenshots/map_controls_overlay.png)

## Teekonnapunkt

Waypoints are shared geographic points of interest that all mesh members can see.

### Loo teekonnapunkt

1. Long-press on the map at the desired location.
2. Enter a name and optional description.
3. Choose an icon/emoji for the waypoint.
4. Puuduta **Saada** jagamiseks kärgvõrku.

### Waypoint Properties

| Property   | Kirjeldus                                               |
| ---------- | ------------------------------------------------------- |
| Nimi       | Short identifier (max 29 characters) |
| Kirjeldus  | Optional longer description                             |
| Icon       | Visual marker emoji on the map                          |
| Lukustatud | If locked, only the creator can edit or delete          |
| Expiration | Optional auto-remove date and time                      |
| Geopiire   | Optional enter/exit alert area — see below              |

### Waypoint Expiration

Waypoints can be set to expire automatically:

- **Never** (default) — waypoint remains until manually deleted
- **Timed** — pick a specific date and time; the waypoint is automatically removed once that time passes. Useful for temporary markers like rally points, hazards, or meeting locations.

Expired waypoints are automatically hidden from the map so they don't clutter the display. The expiration countdown is based on the absolute time you picked, not a duration from when the waypoint was created or received.

### Teekonnapunktide geopiirded

Iga teekonnapunkt saab määratleda ka **geopiirde** – hoiatusala –, et teid või teisi teavitataks, kui sõlm sinna siseneb või sealt lahkub:

1. Määra **geopiirde raadius** eelmääratletud kiipide hulgast (või keelamiseks **Väljas**) või puuduta kohandatud ristkülikukujulise ala joonistamiseks **Määra kaardile ala**.
2. Once a region is set, toggle **Notify on enter** and/or **Notify on exit**.
3. Optionally enable **Favorites only** to limit alerts to your favorited nodes.

Kuna teekonnapunktid (ja nende geopiirded) edastatakse kogu kärgvõrgule, teavitatakse vaikimisi ainult **loojat**. Kui keegi teine ​​jagab sinuga geopiirdega teekonnapunkti, pakub selle detailvaade valikut **„Teavita mind ületamisest”**, et saaksid selle kohta ka sisenemis-/väljumishoiatusi.

### Managing Waypoints

- Puuduta kaardil teekonnapunkti, et vaadata selle üksikasju ja koordinaate
- Edit or delete waypoints you created
- **Locked waypoints** cannot be modified or deleted by other nodes — only the original creator can change them
- Unlocked waypoints can be edited by any mesh member

## Kaardikihid

Tap the layers icon on the map to open **Manage Map Layers**, where you can import your own overlays in `.kml`, `.kmz`, or GeoJSON format — either by opening a file with Meshtastic or sharing it into the app from another app. Imported layers are listed with a toggle to show/hide each one and an option to remove it. This is available on both the Google Play and F-Droid builds.

### Site Planner

**Site Planner** estimates RF coverage for a transmitter and draws it on the map as a color-coded overlay. Open it from a map control, or from a node's detail page via **Estimate coverage** (shown only for nodes with a known position). Configure the transmitter (location, frequency, TX power, antenna gain and height), the receiver (sensitivity, height), and simulation options (max range, high-resolution terrain, color palette), then run the estimate. Like map layers, Site Planner works on both the Google Play and F-Droid builds.

## Position Sharing

### Enabling Position Sharing

Your node shares its GPS position based on:

- **Fikseeritud intervall** – levitamine regulaarsete intervallidega
- **Nutikas asukoht** – levitatakse, kui liikumine ületab lävendi
- **Manual** — only share when explicitly requested

Configure position behavior in **Settings → Position**.

### Privacy Considerations

> 🔒 **Privaatsus:** asukoha andmed levitatakse kõigile sinu kanali sõlmedele. If you don't want your location shared, disable GPS position in settings or use a fixed/fake position.

## Map Sources

The app supports multiple map tile sources:

- OpenStreetMap (default)
- Satellite imagery (where available)
- Võrguühenduseta paanid (lae kaardialad alla võrguühenduseta kasutamiseks)

## Related Topics

- [Nodes](nodes) — view and filter your node list
- [Node Metrics](node-metrics) — signal quality and position history for individual nodes
- [Avasta](Discovery) - traceroute'i ja naabri info kärgvõrgu topoloogia mõistmiseks
- [Units & Locale](units-and-locale) — distance and coordinate display formats

---

