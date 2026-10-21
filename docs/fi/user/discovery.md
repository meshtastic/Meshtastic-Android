---
title: Paikallisen mesh-verkon skannaus
nav_order: 12
last_updated: 2026-10-21
description: Tutki mesh-verkkoasi — paikallinen verkon haku, reitinselvitykset, naapurikartat ja radion hakuun liittyvät työkalut.
aliases:
  - haku
  - paikallisen mesh-verkon haku
  - mesh-verkon haku
  - paikallinen haku
  - verkkohaku
  - reitinselvitys
  - naapuritieto
---

# Paikallisen mesh-verkon skannaus

Hakutyökalut auttavat ymmärtämään **miten** mesh-verkko on yhteydessä — mitkä radiot kuulevat toisensa, mitä reittejä viestit kulkevat ja missä on pullonkauloja tai heikkoja yhteyksiä.

Sovellus tarjoaa kaksi toisiaan täydentävää lähestymistapaa:

- **Local Mesh Discovery (Scanner)** — an automated mode that cycles your connected node through different LoRa presets, listens on each, and ranks which preset performs best at your location.
- Manuaalinen etsiminen — reitinselvityksen reitit, naapuritiedot ja radiolista, joita voit käyttää milloin tahansa yksittäisten reittien ja topologian tarkasteluun.

## Paikallinen verkon haku (Scanner)

Paikallinen verkon haku on erillinen skannaustila, joka auttaa löytämään parhaan LoRa-modeemiesiasetuksen sijaintiisi ja näkemään, mitkä radiot ovat aktiivisia kullakin esiasetuksella. It cycles your connected node through one or more presets you choose, dwells on each one — listens for a set time — to collect packets, then analyzes and ranks the results.

Connect your node, then open **Settings → Advanced → Local Mesh Discovery**. On Android the **Advanced** section appears only for a locally connected node, never over remote admin, and stays grayed out until the app has finished reading the node's configuration. On a managed device its entries are disabled, except **Debug Panel**, which reads app-local logs and stays available. On desktop, Local Mesh Discovery has its own entry on the Settings screen, with no such gate.

Discovery changes your node's LoRa settings while it scans, then restores your original configuration when it finishes.

Each preset is scanned on the public mesh: the default frequency slot for that preset, decoded with the default public channel. If your node uses a custom frequency slot, the scan uses the default slot and puts your slot back when it finishes. If your primary channel has a custom name or key, the scan switches it to the default public channel before the first preset change and restores your channel when the scan ends, whether it completes, you stop it, or it fails. Channel changes don't reboot the node, so this happens at once at the start and end of the scan.

### Setting up a scan

Ennen aloittamista määritä nämä asetukset:

| Säädin                 | Kuvaus                                                                                                                                                                                                                                                   |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LoRa Presets**       | Valitse yksi tai useampi esiasetus skannattavaksi. Haku pysähtyy jokaisessa valitussa esiasetuksessa vuorollaan kuuntelemaan liikennettä.                                                                                |
| **Dwell Time**         | Kunkin esiasetuksen kuunteluaika. Valitse 1, 5, 15, 30, 45, 60, 90, 120 tai 180 minuuttia. Pidempi kuunteluaika kerää enemmän paketteja ja antaa tarkemman kuvan, mutta kestää pidempään.                |
| **Pidä näyttö päällä** | Keeps the display on for the scan. The scan itself holds a CPU wake lock for its whole run and posts a **Scanning LoRa presets…** notification, so it keeps collecting with the screen off or the app in the background. |

The **Start Scan** button stays disabled — with an explanation of why — until the scan can run. Yleisiä syitä, miksi se on pois käytöstä:

- The node is **not connected**.
- **Esiasetuksia ei ole valittu** skannattavaksi.
- Valittu esiasetus käyttää **2,4 GHz -taajuutta**, jota laitteistosi ei tue.

### Live progress

Skannauksen aikana haku näyttää sen nykyisen vaiheen:

| Tila                                                                   | Mitä tapahtuu                                                                                                                                                                                                                     |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Preparing scan**                                                     | Saving your current LoRa configuration and primary channel and getting ready to scan.                                                                                                                             |
| **Vaihdetaan kohteeseen <preset\>**          | Switching the node to the next preset to test.                                                                                                                                                                    |
| **Reconnecting on \<preset\>**              | Yhteys muodostetaan uudelleen esiasetuksen vaihdon jälkeen.                                                                                                                                                       |
| **Dwelling on \<preset\>**                  | Nykyisen esiasetuksen kuuntelu pakettien keräämiseksi, seuraavaan vaiheeseen siirtymisen laskuri käynnissä.                                                                                                       |
| **Analyzing results**                                                  | Kerättyjen pakettien käsittely ja esiasetusten vertailu ja pisteytys.                                                                                                                                             |
| **Restoring home preset**                                              | Palautetaan alkuperäinen LoRa-konfiguraatio takaisin.                                                                                                                                                             |
| **Cancelling scan**                                                    | You tapped **Stop Scan**; partial results are saved before the original preset is restored.                                                                                                                       |
| **Scan failed: \<reason\>** | The scan could not continue — most often the node didn't come back within a minute of a preset change. The results collected so far are saved, and the original preset is restored automatically. |

![Kuuntelun laskuri näyttää jäljellä olevan ajan nykyisessä esiasetuksessa](../../assets/screenshots/discovery_dwell_progress.png)

If a scan is interrupted — the app is closed, or the node goes away — the app restores your original preset and primary channel the next time it reconnects to that node, and tells you it has done so. Reconnect the same node to let that happen; until you do, the node stays on whichever preset and channel the scan left it on.

### Reading the results

Kun skannaus valmistuu, haku näyttää jokaiselle testatulle esiasetukselle oman tuloskortin sekä yleisen yhteenvedon.

![Esiasetuksen tuloskortti, jossa näkyy sijoitus ja kerätyt mittarit](../../assets/screenshots/discovery_preset_result.png)

Mittarit sisältävät:

| Metrijärjestelmä                     | Mitä ne kertovat sinulle                                                                             |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| Radiolaatu (RF)   | Kyseisen esiasetuksen radioympäristön kokonaislaatu                                                  |
| Kanavan käyttöaste                   | Kuinka kuormitetut radiotaajuudet olivat kuuntelun aikana.                           |
| Lähetysaika                          | Havaittu lähetysaika.                                                                |
| Suorat ja välitetyt radiot           | Kuinka monta mesh-radiota kuultiin suoraan verrattuna välitettyihin.                 |
| Virheelliset / päällekkäiset paketit | Vioittuneiden ja toistettujen pakettien määrä, joka kertoo ruuhkasta tai häiriöistä. |

Tuloksista saatavilla olevat lisätoiminnot:

- **Skannaus­historia** — tallennetut istunnot, joita voit tarkastella myöhemmin; katsele tai poista aiempia skannauksia.
- **Haun kartta** — kartta skannauksessa löydetyistä radioista.
- **Raportin vienti** — vie raportti PDF-tiedostona Androidilla tai tekstinä muilla alustoilla.

On **Google Play** builds, Discovery can generate an on-device AI summary (Gemini Nano) of your results. F-Droid builds always use the algorithmic summary, because that flavor leaves out the proprietary ML Kit dependency, so every build gives a readable interpretation of the scan.

## Verkkokutsu

Mesh Beacon antaa radioille mahdollisuuden kutsua muita liittymään mesh-verkkoon. Majakkatilassa oleva radio lähettää säännöllisesti kutsun, jossa voidaan haluttaessa ilmoittaa kanava, alue ja modeemiesiasetus — lähellä olevat radiot voivat havaita sen jo ennen asetusten jakamista.

Configure it under **Settings → Module configuration → Mesh Beacon**. The entry appears only on nodes running firmware 2.8.0 or newer. A read-only **Region** row at the top of the screen shows the region the beacon advertises: that region, and the preset, are always the ones the node itself uses, so a beacon can't invite anyone onto settings your node isn't running.

- **Kuuntele majakoita** – vastaanota muiden radioiden lähettämiä liittymiskutsuja.
- **Broadcast a beacon** — periodically advertise this mesh to nearby nodes, with an optional **Message** of up to 60 bytes, a **Broadcast interval** picked from fixed intervals between 1 hour and 72 hours, and an **Offered channel** chosen from your node's own channels. The offered channel is required, and defaults to your primary channel. Over remote admin the picker offers the primary channel only.
- **Broadcast targets** — where the beacon actually transmits. The list always holds at least one row: the first is the beacon's own transmission, not an extra. Each row picks a **Channel** and a **Transmit preset**. **Add target** appends a row, and **Remove target** deletes one — removing the last row replaces it with a fresh default rather than emptying the list.

Two conditions block beacon setup:

- **The node has no region set.** The screen shows nothing but _Set your radio's region before setting up a beacon._ Set the region on **Settings → LoRa** first.
- **The node uses custom LoRa settings.** A beacon advertises a modem preset for others to join, so a node with **Use Preset** turned off has no standard preset to offer. In that state **Broadcast a beacon** can be turned off but not on, and the broadcast settings are read-only. Listening for beacons is unaffected.

Vastaanotetut kutsut näkyvät **Mesh-kutsut** -korteissa haku-näytössä. Jokainen kortti näyttää lähettäjän viestin sekä tarjotun kanavan, alueen, esiasetuksen ja signaalin laadun. Käytettävissä ovat seuraavat toiminnot:

- **Join** — switch to the offered channel and preset (retunes the node and reboots). Jos tarjous vastaa nykyistä taajuuspaikkaasi, **Lisää kanava** lisää sen ilman uudelleenkäynnistystä.
- **Tutki** – aloita Haku tarjotulla esiasetuksella, jotta voit tutkia mesh-verkkoa ennen liittymistä (näkyy vain, jos majakka tarjoaa esiasetuksen).
- **Hylkää** – ohita kutsu.

Majakoiden ilmoittamat kanavat näkyvät myös Haku-toiminnon asetuksissa kohdassa **Majakkakanavat**. Valitse kanava lisätäksesi sen hakukohteeksi.

An invitation to a mesh your node is already on is suppressed: no card, no notification, and no **Beacon channels** entry. A channel counts as one you already have only when both its name and its key match a channel on your node — the same name with a different key is a different mesh, so that invitation still reaches you.

## Manual exploration

Seuraavat työkalut ovat aina käytettävissä Radiot- ja Radion tiedot -näkymissä. Käytä niitä yksittäisten reittien tutkimiseen ja topologian muodostamiseen, joko osana skannausta tai sen sijaan.

### Reitinselvitys

Traceroute reveals the exact path a message takes from your node to any other node on the mesh, which makes it the first tool to reach for when debugging connectivity problems.

#### Reitinselvityksen suorittaminen

1. Siirry kohtaan **Radiot** ja napauta radiota, jota haluat jäljittää.
2. On the node detail screen, find **Traceroute** in the **Telemetry** section and tap its request button. Once a result arrives, a second button on the same row opens the traceroute log, where each hop is listed with its signal quality.

#### Reading the results

Reitinselvityksen tulos näyttää tältä:

```text
Route traced toward destination:

■ Your Node (YOUR)
⇊ 8.5 dB
■ Relay Node (RLAY)
⇊ -8.75 dB
■ Target Node (TGT1)
```

Each `⇊` line between two nodes is one relay hop, and the SNR on that line is the quality of that segment alone. The app colors it against the demodulation floor of the preset in use, not a fixed number: green above the floor, yellow within 5.5 dB below it, orange within 7.5 dB, and red beyond that. The floor is −7.5 dB on Short Fast and improves 2.5 dB per spreading-factor step, so it is −17.5 dB on Long Fast — the same SNR reads differently on different presets. See [Signal Meter](signal-meter). A request that also gets a reply adds a second block under **Route traced back to us:**.

| Mitä kannattaa tarkkailla                                   | Mitä se tarkoittaa                                             |
| ----------------------------------------------------------- | -------------------------------------------------------------- |
| All hops show Good SNR (green)           | Hyvä reitti — viestit kulkevat luotettavasti                   |
| One hop shows a poor SNR (orange or red) | Heikko yhteys — tämä välityssegmentti on haavoittuva           |
| Useita hyppyjä (4+)                      | Pitkä reitti — harkitse radion siirtämistä sen lyhentämiseksi  |
| Eri reitti uudelleenyrityksellä                             | Mesh is adapting — multiple routes exist, a sign of redundancy |

Run traceroute several times over a few minutes, waiting out the 30-second cooldown between requests. If the path changes, your mesh has redundant routes, a sign of a well-connected network. A relay that didn't record itself in the route shows as **Unknown hop**.

#### Reitinselvityksen vianmääritys

- **No Response** — The traceroute got nothing back. The target node may be offline, out of range, or on a different channel. Varmista, että molemmat radiot jakavat vähintään yhden kanavan samalla salausavaimella.
- **Reitinselvitys aikakatkaistu** — reitti voi olla liian pitkä (ylittää hyppymäärärajan) tai välittäjä-radio on ruuhkautunut. Try increasing the hop limit in **Settings → LoRa**.
- **Cannot show traceroute map because the start or destination node has no position information** — The path was traced, but one end has never shared a position.
- **Epäsymmetriset reitit** — reitinselvitys A→B voi kulkea eri reittiä kuin B→A. This is normal — radio propagation isn't always symmetric.

### Naapuritieto

Naapuritieto-moduuli antaa jokaisen radion lähettää listan radioista, jotka se voi **kuulla suoraan** (yhden hypyn päässä). Kun useat radiot jakavat naapurilistansa, voit muodostaa koko verkon topologian kartan.

#### Naapuritiedon käyttöönotto

1. Navigate to **Settings → Module configuration → Neighbor Info**.
2. Ota moduuli käyttöön.
3. Set **Update Interval**. The app accepts whatever you type; the firmware enforces its own minimum and resets a value below it.
4. Turn on **Transmit over LoRa**. Without it, your neighbor list goes only to MQTT and to this app, never over the air.

Once enabled and transmitting over LoRa, your node periodically broadcasts its neighbor list. Myös muut radiot, joissa naapuritieto on käytössä, toimivat samalla tavalla.

#### Viewing neighbor data

- Open a node's detail screen and find **Neighbor Info** in the **Telemetry** section. The row appears once the app has received that node's neighbor list. Its log button opens everything that node has reported, and its request button asks the node for a fresh list.
- Jokainen naapurimerkintä näyttää radion, joka on kuultu suoraan, sekä sen signaalilaadun.
- Yhdistä naapuritiedot useista radioista ymmärtääksesi koko mesh-verkon topologian.

> ℹ️ **Huomautus:** Naapuritieto lisää lähetysaikaa, koska jokainen toiminnon ottanut radio lähettää säännöllisesti naapuriluettelonsa. The firmware doesn't accept an interval shorter than 14400 seconds (4 hours) for this reason; on busy meshes, leave it at the 21600-second default or raise it further.

### Node list as a discovery tool

The node list's filtering and sorting make it a discovery tool of its own.

#### Finding new nodes

- Lajittele **Viimeksi kuultu** nähdäksesi viimeksi aktiiviset radiot ylimpänä.
- Enable **Include unknown** to see nodes that have appeared on the mesh but haven't sent user info yet — these are often newly powered-on nodes.

#### Assessing connectivity

- Lajittele **Hyppyjen määrä** nähdäksesi mitkä radiot ovat suoraan tavoitettavissa (0 hyppyä) ja mitkä välitettyinä.
- Lajittele **Etäisyys** löytääksesi lähellä olevat radiot ja varmistaaksesi niiden tavoitettavuuden.
- Use **Exclude MQTT** to focus on nodes reachable over LoRa (not via internet bridge).

#### Infrastructure audit

- Poista **Jätä infrastruktuuri pois** käytöstä, jos haluat nähdä Router-, Router Late- ja Client Base -radiot.
- Tarkista niiden signaalin laatu ja viimeksi kuultu -ajat varmistaaksesi, että infrastruktuuriradiot ovat kunnossa.

Katso [Radiot](nodes) saadaksesi lisätietoa suodatus- ja lajitteluasetuksista.

## Tips for Mesh exploration

- Aloita reitinselvityksellä — se antaa välittömän ja käytännöllisen tiedon yksittäisestä reitistä.
- **Enable Neighbor Info on key nodes** — especially routers, to build a picture of the backbone.
- Tarkista kartta — [Kartta](map-and-waypoints) yhdessä signaalitiedon kanssa auttaa ymmärtämään, miksi jotkin yhteydet ovat vahvoja ja toiset heikkoja.
- Seuraa signaalin muutoksia ajan kuluessa — käytä [Signaalimittari](signal-meter) -opasta tulkitaksesi SNR- ja RSSI-arvot oikein.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — luettelo radioista, jotka nämä skannaukset löytävät
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele löydettyjä radioita kartalla
- [Signaalimittari](signal-meter) — tulkitse skannauksen raportoimat SNR- ja RSSI-arvot
- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — määritä Mesh Beacon- ja Naapuritieto-moduulit
- [Viestit ja kanavat](messages-and-channels) — liity löytämääsi mesh-verkkoon ja aloita viestintä
