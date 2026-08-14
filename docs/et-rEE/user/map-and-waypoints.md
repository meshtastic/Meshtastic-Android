---
title: Kaart ja teekonnapunktid
parent: User Guide
nav_order: 6
last_updated: 2026-07-08
description: Vaata sõlmede asukohti kaardil, loo ja jaga teekonnapunkte ning halda asukoha jagamist ja privaatsust.
aliases:
  - kaart
  - teekonnapunkt
  - gps
  - asukoht
  - site-planner
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
- **Keskpunkt** – asukoha tsentreerimiseks valige asukohanupp
- **Sõlme puudutamine** – üksikasjade kuvamiseks puuduta sõlmel

Ujuv tööriistariba pakub kiiret juurdepääsu kompassile, kihtide vahetamisele, sõlmefiltritele, värskendamisele ja asukoha jälgimisele. Põhjasuuna muutmiseks puuduta kompassi või praeguse asukoha keskpunkti seadmiseks asukohanuppu.

![Kaardi juhtelementide pealiskiht](/assets/screenshots/map_controls_overlay.png)

## Teekonnapunkt

Waypoints are shared geographic points of interest that all mesh members can see.

### Loo teekonnapunkt

1. Vajuta pikalt kaardil soovitud asukohas.
2. Sisestage nimi ja valikuline kirjeldus.
3. Choose an icon/emoji for the waypoint.
4. Puuduta **Saada** jagamiseks kärgvõrku.

Waypoints are addressed like messages: by default they broadcast on the primary channel, but a waypoint can also be sent on a specific channel or as a direct message to a single node.

### Waypoint Properties

| Property   | Kirjeldus                                                   |
| ---------- | ----------------------------------------------------------- |
| Nimi       | Lühike identifikaator (max 29 tähemärki) |
| Kirjeldus  | Optional longer description                                 |
| Icon       | Visuaalse markeri emotikon kaardil                          |
| Lukustatud | If locked, only the creator can edit or delete              |
| Expiration | Optional auto-remove date and time                          |
| Geopiire   | Valikuline sisenemis-/väljumishoiatusala – vt allpool       |

### Waypoint Expiration

Waypoints can be set to expire automatically:

- **Never** (default) — waypoint remains until manually deleted
- **Timed** — pick a specific date and time; the waypoint is automatically removed once that time passes. Kasulik ajutiste märkide, näiteks kogunemispunktide, ohtude või kohtumispaikade jaoks.

Aegunud teekonnapunktid peidetakse kaardilt automaatselt, et need ekraani ei risustaks. The expiration countdown is based on the absolute time you picked, not a duration from when the waypoint was created or received.

### Teekonnapunktide geopiirded

Iga teekonnapunkt saab määratleda ka **geopiirde** – hoiatusala –, et teid või teisi teavitataks, kui sõlm sinna siseneb või sealt lahkub:

1. Määra **geopiirde raadius** eelmääratletud kiipide hulgast (või keelamiseks **Väljas**) või puuduta kohandatud ristkülikukujulise ala joonistamiseks **Määra kaardile ala**.
2. Kui piirkond on määratud, lülita sisse **Teavita sisenemisel** ja/või **Teavita väljumisel**.
3. Optionally enable **Favorites only** to limit alerts to your favorited nodes.

Kuna teekonnapunktid (ja nende geopiirded) edastatakse kogu kärgvõrgule, teavitatakse vaikimisi ainult **loojat**. Kui keegi teine ​​jagab sinuga geopiirdega teekonnapunkti, pakub selle detailvaade valikut **„Teavita mind ületamisest”**, et saaksid selle kohta ka sisenemis-/väljumishoiatusi.

### Managing Waypoints

- Puuduta kaardil teekonnapunkti, et vaadata selle üksikasju ja koordinaate
- Edit or delete waypoints you created
- **Locked waypoints** cannot be modified or deleted by other nodes — only the original creator can change them
- Unlocked waypoints can be edited by any mesh member

## Kaardikihid

Puuduta kaardil kihtide ikooni, et avada **Kaardikihtide haldamine**, kus saad importida oma kihte `.kml`, `.kmz` või GeoJSON-vormingus – avades faili Meshtasticuga või jagades seda rakendusse teisest rakendusest. Imporditud kihid on loetletud koos lülitiga iga kihi kuvamiseks/peitmiseks ja valikuga selle eemaldamiseks. This is available on both the Google Play and F-Droid builds.

### Site Planner

**Asukoha planeerija** hindab saatja raadiosageduslikku leviala ja joonistab selle kaardile värvikoodiga kihina. Ava see kaardihalduselemendist või sõlme detaillehelt valiku **Hinnatud katvus** kaudu (kuvatakse ainult teadaoleva asukohaga sõlmede puhul). Konfi saatja (asukoht, sagedus, saatja võimsus, antenni võimendus ja kõrgus), vastuvõtja (tundlikkus, kõrgus) ja simulatsioonivalikud (maksimaalne ulatus, kõrge eraldusvõimega maastik, värvipalett) ning seejärel käivita hinnang. Nagu kaardikihid, töötab ka Site Planner nii Google Play kui ka F-Droid versioonides.

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

Baaskaart sõltub rakenduse stiilist: **Google Play** versioonid kasutavad Google Mapsi, **F-Droid** ja töölaua versioonid aga OpenStreetMapi. Põhikaardi peal on saadaval täiendavad paaniallikad pealiskihtide või alternatiividena:

- Satellite imagery (where available)
- Võrguühenduseta paanid (lae kaardialad alla võrguühenduseta kasutamiseks)

## Related Topics

- [Nodes](nodes) — view and filter your node list
- [Node Metrics](node-metrics) — signal quality and position history for individual nodes
- [Avasta](Discovery) - traceroute'i ja naabri info kärgvõrgu topoloogia mõistmiseks
- [Ühikud ja lokaat](units-and-locale) — kauguse ja koordinaatide kuvamise ühikud

---

