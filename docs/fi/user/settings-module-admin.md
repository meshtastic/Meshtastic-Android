---
title: Asetukset — Moduulit ja ylläpito
nav_order: 8
last_updated: 2026-10-21
description: Määritä valinnaiset ominaisuusmoduulit (MQTT, telemetria, valmiit viestit, TAK ja muut) sekä suorita laitteen ylläpitotoimia.
aliases:
  - moduulit
  - moduulin asetukset
  - ylläpito
---

# Asetukset — Moduulit ja ylläpito

Määritä valinnaiset ominaisuusmoduulit ja suorita laitteen ylläpitotoimia. Moduulit laajentavat Meshtasticia erikoisominaisuuksilla — jokainen voidaan ottaa käyttöön tai poistaa käytöstä erikseen.

Enable only the modules you use. Disabling unused modules reduces airtime, saves battery, and simplifies your configuration. A module you expect can be missing for three reasons: your node's role doesn't enable it, your firmware is older than the release that added it, or the firmware build excludes it for this hardware.

Moduuliasetukset käyttävät korttipohjaista asettelua, jossa on kytkimiä, pudotusvalikoita, tekstikenttiä ja liukusäätimiä:

![Kytkinasetus käytössä-asennossa](../../assets/screenshots/settings_switch.png)

![Pudotusvalikkoasetus avattuna näyttämään vaihtoehtoluettelonsa](../../assets/screenshots/settings_dropdown.png)

![A text field setting with a value entered](../../assets/screenshots/settings_text_field.png)

![A module settings card with its title and grouped controls](../../assets/screenshots/settings_titled_card.png)

## Moduulin asetukset

Every module lives under **Settings → Module configuration**.

Saving a module screen restarts the node: the button reads **Save & restart**, and the node is unreachable for a few seconds afterwards. External Notification and Mesh Beacon are the exceptions: their button reads **Save**. External Notification may still restart the node for some changes, while a Mesh Beacon change applies without a restart.

### MQTT module

Yhdistää verkon viestejä MQTT-välityspalvelimeen ja sieltä takaisin internet-yhteyksiä varten. This is how you extend your mesh beyond LoRa range or integrate with home automation systems.

| Asetus                 | Kuvaus                                                                                                                                                                                                                                                                             | Default               |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| Osoite                 | MQTT-välityspalvelimen osoite                                                                                                                                                                                                                                                      | `mqtt.meshtastic.org` |
| Encryption Enabled     | Publish packets still encrypted with the channel key                                                                                                                                                                                                                               | On                    |
| JSON ulostulo käytössä | Julkaise ja vastaanota MQTT-viestejä JSON-muodossa. Shown only for a node on firmware older than 2.8, which removed JSON support, or while the setting is still on                                                                                 | Off                   |
| Map Reporting          | Publish position to the public map — see the map reporting card that follows                                                                                                                                                                                                       | Off                   |
| MQTT Client Proxy      | Let the connected app carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                                                                                                                | Off                   |
| MQTT Enabled           | Turn the MQTT bridge on                                                                                                                                                                                                                                                            | Off                   |
| MQTT proxy in this app | The app-side half of **MQTT Client Proxy**: whether this app acts as that relay. It shows whether the relay is running, and can be turned on only while **MQTT Enabled** and **MQTT Client Proxy** are both on. Katso [MQTT](mqtt) | —                     |
| Salasana               | Todennuksen salasana                                                                                                                                                                                                                                                               | The public broker's   |
| Root Topic             | MQTT:n perusaihepolku                                                                                                                                                                                                                                              | `msh`                 |
| TLS Enabled            | Use a secure connection                                                                                                                                                                                                                                                            | Off                   |
| Käyttäjänimi           | Todennuksen käyttäjätunnus                                                                                                                                                                                                                                                         | The public broker's   |

Turning **Map Reporting** on reveals a consent card headed _Consent to Share Unencrypted Node Data
via MQTT_, with an **I agree.** switch under it. The rest of the card doesn't exist on screen
until you agree:

| Asetus                    | Kuvaus                                                                                                                                                                                   |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hyväksyn. | Explicit consent to transmit your node data, including an approximate position, unencrypted. Map reporting doesn't start without it                                      |
| Map Publish Interval      | Kuinka usein sijainti julkaistaan. A dropdown of fixed intervals from 1 hour to 72 hours — nothing shorter is offered                                                    |
| Precision slider          | The slider has no label of its own. It sets how coarsely your position is published, from 12 to 15; the line beneath reads _±_ the resulting distance, in your own units |

Katso [MQTT](mqtt) saadaksesi yksityiskohtaisen käyttöoppaan, joka sisältää salauksen, tietosuojan ja välityspalvelimen määrityksen.

### Serial module

Mahdollistaa sarjaporttiviestinnän ulkoisten laiteintegraatioiden kanssa (GPS-moduulit, anturit tai mukautettu laitteisto). Kun tämä on käytössä, radion sarjaportti voi lähettää ja vastaanottaa protobuf- tai tekstimuotoista dataa, jolloin ulkoiset mikrokontrollerit tai tietokoneet voivat olla vuorovaikutuksessa verkon kanssa.

| Asetus                                                                                           | Kuvaus                                                                                                                                                                                                                              | Default |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| Baud                                                                                             | Port speed, from **110 Baud** to **921600 Baud**                                                                                                                                                                                    | Default |
| Echo                                                                                             | Echo packets you send back over the serial port                                                                                                                                                                                     | Off     |
| Mode                                                                                             | Which protocol the port speaks: **Default**, **Simple**, **Protobufs**, **Text Message**, **NMEA Positions**, or **CalTopo**, plus `WS85`, `VE_DIRECT`, `MS_CONFIG`, `LOG` and `LOGTEXT` under their firmware names | Default |
| Korvaa konsolin sarjaportti                                                                      | Ota käyttöön portti, jota virheenkorjauskonsoli normaalisti käyttää                                                                                                                                                                 | Off     |
| Receive data (rxd) GPIO pin / Transmit data (txd) GPIO pin | Sarjayhteyden GPIO-nastat                                                                                                                                                                                                           | 0       |
| Serial Enabled                                                                                   | Turn serial communication on                                                                                                                                                                                                        | Off     |
| Aikakatkaisu                                                                                     | How long to wait before treating received data as a complete packet. 0 uses 250 ms                                                                                                                                  | 0       |

### External Notification module

Controls buzzer, LED, or vibration alerts on your node hardware. Hyödyllinen laitteille, joiden täytyy ilmoittaa fyysisesti viestin saapumisesta — erityisen hyödyllinen valvomattomissa tai ulkokäyttöön asennetuissa laitteissa.

Käynnistimiä on kaksi – saapuva **viesti** ja vastaanotettu **BEL**-ohjausmerkki – ja kumpikin voi ohjata LED-valoa, summeria ja värinämoottoria erikseen, joten käytettävissä on kuusi kytkintä. The
firmware turns the module on, and picks the outputs, on hardware that has a buzzer, vibration motor
or notification LED; elsewhere everything starts off.

| Asetus                                                                                                               | Kuvaus                                                                                                                                 |
| -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Active                                                                                                               | Whether the LED pin is active high or low. Shown once **Output pin GPIO** is set                                       |
| Alert when receiving a bell / Alert GPIO buzzer when receiving a bell / Alert GPIO vibra motor when receiving a bell | Mitkä lähdöt aktivoituvat vastaanotetusta BEL-ohjausmerkistä                                                                           |
| Alert when receiving a message / Alert GPIO buzzer when receiving a message / Vibra Motor Alert                      | Mitkä lähdöt aktivoituvat saapuvasta viestistä                                                                                         |
| External Notification Enabled                                                                                        | Moduulin pääkytkin                                                                                                                     |
| GPIO Output Duration                                                                                                 | Kuinka kauan yksittäinen hälytys kestää                                                                                                |
| Toistokehotuksen aikakatkaisu                                                                                        | Toista hälytystä tämän ajan, kunnes se kuitataan. **Unset** disables nagging                                           |
| Output pin GPIO                                                                                                      | LED on kytketty nastaan                                                                                                                |
| Output pin buzzer GPIO                                                                                               | Summeri on kytketty nastaan                                                                                                            |
| Output pin vibra GPIO                                                                                                | Värinämoottori on kytketty nastaan                                                                                                     |
| Soittoääni                                                                                                           | PWM-summerilla toistettava RTTTL-soittoääni. Voidaan tuoda tiedostosta                                                 |
| Use I2S As Buzzer                                                                                                    | Lähetä hälytys sen sijaan I2S-äänilähdön kautta                                                                                        |
| Use PWM Buzzer                                                                                                       | Drive the buzzer with PWM, which allows tones rather than a single pitch. Shown once **Output pin buzzer GPIO** is set |

### Store & Forward module

Puskuroi viestejä radioille, jotka ovat tilapäisesti poissa verkosta, ja toimittaa ne, kun nämä radiot yhdistyvät uudelleen. Välttämätön verkoissa, joissa radiot siirtyvät säännöllisesti kuuluvuusalueelle ja sen ulkopuolelle — varmistaa, etteivät viestit katoa lyhyiden yhteyskatkosten aikana. A server needs an ESP32 with PSRAM or a Linux `meshtasticd` node. A node in the Router or Router Late role acts as a server even with the server setting off.

| Asetus                    | Kuvaus                                                                             | Default |
| ------------------------- | ---------------------------------------------------------------------------------- | ------- |
| History Return Max        | Most stored messages returned per request. 0 uses 25               | 0       |
| History Return Window     | How far back, in minutes, stored messages are returned. 0 uses 240 | 0       |
| Kirjausten määrä          | Most messages to store. 0 uses three quarters of the free PSRAM    | 0       |
| Send Heartbeat            | Periodically announce that this node is a store-and-forward server                 | Off     |
| Palvelin                  | Act as a store-and-forward server for the mesh                                     | Off     |
| Store and Forward Enabled | Turn store and forward on                                                          | Off     |

### Range Test module

Automaattinen kuuluvuustestityökalu radioiden välisen yhteyden laadun arviointiin. Kun toiminto on käytössä, radio lähettää säännöllisesti testiviestejä kasvavilla laskuriarvoilla. Vastaanottava radio kirjaa nämä viestit, jolloin voit myöhemmin kävellä tai ajaa pois ja analysoida, millä etäisyydellä viestien saapuminen loppui.

Range Test only works on a secured primary channel. As long as your primary channel still uses the
default channel key, the interval and CSV controls stay disabled — you can still switch an
already-enabled module off — and saving force-disables the module if the channel has reverted to
public.

| Asetus             | Kuvaus                                                                                                              | Default |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- | ------- |
| Range Test Enabled | Turn range testing on                                                                                               | Off     |
| Save               | Log received test data as a CSV on the node's own filesystem. ESP32 nodes only                      | Off     |
| Sender Interval    | Time between test transmissions, chosen from a dropdown of fixed intervals. **Unset** stops sending | Unset   |

### Telemetry module

Määrittää, mitä telemetriatietoja radiosi jakaa verkkoon. Telemetria sisältää laitteen kuntoon liittyviä tietoja (akun varaustaso, käyttöaika) sekä ympäristöanturien tietoja (lämpötila, kosteus, ilmanpaine).

Jokaisella neljällä mittausryhmällä on oma käyttöönottokytkin ja oma mittausväli, joten esimerkiksi akun tila voidaan raportoida usein ja anturitiedot harvemmin. The intervals run from 30 minutes to 72 hours.

| Asetus                       | Kuvaus                                                                                                                                                                                      | Default |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| Air Quality Metrics Enabled  | Raportoi hiukkas- ja CO₂-anturin tiedot                                                                                                                                                     | Off     |
| Air Quality Metrics Interval | Kuinka usein tiedot raportoidaan                                                                                                                                                            | —       |
| Broadcast Device Metrics     | Send device metrics to the mesh. When it is off, they go only to connected clients. Only shown on firmware 2.7.13 and newer | Off     |
| Device Metrics Interval      | How often to report battery, uptime and channel utilization                                                                                                                                 | —       |
| Display Fahrenheit           | Käytä radion näytössä Fahrenheit-asteita. This is the node's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale)  | Off     |
| Environment Metrics Enabled  | Raportoi liitettyjen ympäristöanturien tiedot                                                                                                                                               | Off     |
| Environment Metrics Interval | Kuinka usein tiedot raportoidaan                                                                                                                                                            | —       |
| Power Measurement Enabled    | Raportoi kanavakohtaiset jännite- ja virtamittaukset                                                                                                                                        | Off     |
| Power Metrics Interval       | Kuinka usein tiedot raportoidaan                                                                                                                                                            | —       |
| Power Screen                 | Näytä virtamittaukset myös radion omalla näytöllä                                                                                                                                           | Off     |
| Show on device screen        | Also show environment readings on the device's display                                                                                                                                      | Off     |

Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi tietoa tuetuista antureista ja määrityssuosituksista.

### Canned Message module

Pre-configured messages accessible from the node's physical buttons (for nodes with rotary encoders, keypads, or similar input hardware). Määritä luettelo pikaviesteistä, jotka voidaan lähettää ilman yhdistettyä puhelinta — ihanteellinen kenttäkäyttöön.

| Asetus                                                                       | Kuvaus                                                                                                                                             | Default |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| Clockwise Rotary Event / Counterclockwise Rotary Event / Encoder Press Event | Which key event each encoder action produces: **None**, **Up**, **Down**, **Left**, **Right**, **Select**, **Back**, or **Cancel** | None    |
| Viestit                                                                      | Rivinvaihdoilla eroteltu viestiluettelo                                                                                                            | Empty   |
| Pin A / Pin B / Press Pin                                                    | The three pins the rotary encoder is wired to                                                                                                      | 0       |
| Rotary Encoder                                                               | Käytä kiertokoodainta syöttölaitteena                                                                                                              | Off     |
| Send Bell                                                                    | Lähetä viestin mukana BEL-ohjausmerkki, jotta vastaanottavan radion Ulkoiset ilmoitukset -moduuli voi antaa hälytyksen                             | Off     |
| Up/Down/Select Input                                                         | Erillinen, yksinkertaisempi syöttötapa, jossa käytetään ylös-/alas-/valitse-painikkeita kiertokoodaimen sijaan                                     | Off     |

Some boards with built-in encoders or keys come with the input settings filled in. Two older
fields, **Canned message enabled** and **Allow input source**, appear only for a node on firmware
older than 2.7, or while they still hold a value.

### Audio module

Codec2-äänituki matalan kaistanleveyden puheviestintään verkossa. Tämä on **kokeellinen** ominaisuus, joka koodaa puheen erittäin pieniksi datapaketeiksi käyttäen Codec2-koodekkia. It needs specific hardware, an I2S microphone and speaker, and the voice quality is low.

| Asetus              | Kuvaus                                                                                                                 | Default |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------- |
| Bitrate             | Codec2 bitrate, from **450 bps** to **3200 bps**. Lower rates use less bandwidth and give poorer audio | Default |
| Codec2 Enabled      | Turn the audio module on                                                                                               | Off     |
| I2S Clock Pin       | I2S-bittikellon GPIO-nasta                                                                                             | 0       |
| I2S Data In Pin     | GPIO pin for I2S data in                                                                                               | 0       |
| I2S Data Out Pin    | GPIO pin for I2S data out                                                                                              | 0       |
| I2S Word Select Pin | GPIO pin for I2S word select                                                                                           | 0       |
| PTT Pin             | GPIO pin for the push-to-talk button. 0 uses GPIO 39                                                   | 0       |

### Remote Hardware module

GPIO-ohjaus mesh-verkon kautta. Mahdollistaa etäradion lukea tai kirjoittaa GPIO-nastoja toisessa radiossa — hyödyllinen releiden aktivointiin, kytkimien lukemiseen tai ulkoisen laitteiston ohjaamiseen etäältä.

> ⚠️ **Warning:** Turning on **Allow undefined pin access** gives remote nodes access to all GPIO pins, which could interfere with the node's own hardware. Turn it on only on dedicated GPIO nodes.

| Asetus                               | Kuvaus                                                                      | Default |
| ------------------------------------ | --------------------------------------------------------------------------- | ------- |
| Salli määrittämättömän pinnin käyttö | Salli pääsy mihin tahansa GPIO-nastaan (tietoturvariski) | Off     |
| Käytettävissä olevat pinnit          | Enintään 4 tämän radion etälukuun tai kirjoitukseen tarjoamaa GPIO-pinniä   | None    |
| Etälaitteen ohjaus käytössä          | Turn remote GPIO access on                                                  | Off     |

### Neighbor Info module

Lähettää tietoa suoraan kuulluista naapureista mahdollistaen verkon topologian kartoituksen. Jokainen käyttöön otettu radio jakaa säännöllisesti luettelon muista radioista, jotka se kuulee, sekä niiden signaalin laadun.

| Asetus                               | Kuvaus                                                                                                                                                       | Default                                 |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------- |
| Neighbor Info Enabled                | Turn neighbor broadcasting on                                                                                                                                | Off                                     |
| Lähetä LoRa:n kautta | Also broadcast neighbor info over LoRa, not just MQTT and the connected app. Ei käytettävissä kanavalla, joka käyttää oletusavainta ja nimeä | Off                                     |
| GPS-kyselyn aikaväli                 | How often to broadcast the neighbor list, in seconds. Minimum 4 hours                                                                        | 0 (firmware default) |

Katso [Paikallinen mesh-haku](discovery), miten naapuritietoja käytetään mesh-verkon rakenteen tutkimiseen.

### Ambient Lighting module

Ohjaa tuetuissa laitteissa olevaa NeoPixeliä tai muita osoitteellisia RGB-LEDejä. Voidaan käyttää visuaalisina tilailmaisimina, ilmoitusvaloina tai koriste-efekteinä.

Pick the color with the swatch's **Preset colors** row or the **Red**, **Green** and **Blue** sliders.
**Advanced** opens number fields for the same three values.

| Asetus                      | Kuvaus                                    | Default                      |
| --------------------------- | ----------------------------------------- | ---------------------------- |
| Virta                       | LED drive current, 0 to 31                | 10                           |
| LED State                   | Kytke LED päälle tai pois päältä          | Off                          |
| Punainen / vihreä / sininen | Individual color channel values, 0 to 255 | Derived from the node number |

### Detection Sensor module

Muuttaa radiosi liike- tai ovitunnistimeen perustuvaksi hälytysjärjestelmäksi. Kun GPIO-pinni havaitsee tilamuutoksen (liike havaittu, ovi avattu), radio lähettää hälytysviestin verkkoon.

| Asetus                                    | Kuvaus                                                                                                                                                            | Default                                                                              |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Detection Sensor Enabled                  | Turn the detection sensor on. It also has to be on for nodes that receive its alerts or chart its log                                             | Off                                                                                  |
| GPIO Pin to monitor                       | GPIO pin connected to the sensor. 0 disables the module                                                                                           | 0                                                                                    |
| Minimum time between detection broadcasts | Hälytyslähetysten vähimmäisaika                                                                                                                                   | 0, which the firmware treats as 1 hour (12 hours on Router roles) |
| Name                                      | Custom name for this sensor, used in its messages                                                                                                                 | Empty                                                                                |
| Send Bell                                 | Include a bell character in alerts                                                                                                                                | Off                                                                                  |
| State Broadcast Interval                  | How often to send the sensor's state whether or not anything was detected. **Unset** sends only on detection                                      | Unset                                                                                |
| Trigger Type                              | How the pin's state maps to a detection event: **High**, **Low**, **Rising Edge**, **Falling Edge**, **Either Edge High**, or **Either Edge Low** | High                                                                                 |
| Use Internal Pull-up                      | Ota pinnin sisäinen ylösvetovastus käyttöön                                                                                                                       | Off                                                                                  |

### Paxcounter module

Henkilöt, jotka lasketaan WiFi- ja BLE-hakukyselyiden perusteella. Laskee lähellä olevia laitteita kuuntelemalla passiivisesti koepyyntöjä, joita puhelimet ja kannettavat tietokoneet lähettävät etsiessään verkkoja. Saatavilla vain ESP32-laitteissa. Counts are approximate, since one person may carry several devices, so the module suits estimating foot traffic at trailheads or event venues.

| Asetus                                  | Kuvaus                                                                                                   | Default                                 |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| BLE Threshold (dBm)  | The same cut-off for BLE advertisements                                                                  | 0, which the firmware treats as −80 dBm |
| PAX Counter Enabled                     | Turn people counting on. Wi-Fi and Bluetooth must both be off on the node for it to work | Off                                     |
| GPS-kyselyn aikaväli                    | Kuinka usein laskentatiedot raportoidaan                                                                 | —                                       |
| WiFi Threshold (dBm) | Ignore Wi-Fi probes weaker than this, so distant devices aren't counted                                  | 0, which the firmware treats as −80 dBm |

### Status Message module

Tilaviestillä ei ole omaa moduulinäkymää. Sitä muokataan yhdessä muiden radion tunnistetietojen kanssa kohdassa [Asetukset → Radio ja käyttäjä](settings-radio-user#user-profile).

### Mesh Beacon module

Lähettää kutsun mesh-verkkoosi ja vastaanottaa muiden lähettämiä kutsuja. The entry appears only
on nodes running firmware 2.8.0 or newer. See [Local Mesh Discovery](discovery) for the full
walkthrough.

### TAK module

Team Awareness Kit -integraatio yhteensopivuutta varten ATAK- ja WinTAK-järjestelmien kanssa. The entry is labeled
**TAK (ATAK)**. Two things have to be true before it appears in the module list: the node runs
firmware 2.8.0 or newer, and its **Device Role** on **Settings → Device configuration → Device** is
set to **TAK** or **TAK Tracker**. Katso [TAK-integraatio](tak) saadaksesi tarkemmat määritys- ja käyttöohjeet.

## Ylläpito

### Etähallinta

Remote administration changes another node's settings over the mesh. The target node must list your node's public key as one of its **Admin Key** entries, set on its **Settings → Security** screen.

1. Tap the target node in the node list to open its detail screen.
2. In the **Administration** card, tap **Remote Administration**. The app first establishes a session with the node, showing _Establishing remote session…_, then opens its **Settings**.
3. Muokkaa määrityksiä.
4. Tap **Save** or **Save & restart**. The changes are sent over the mesh.

### Device Actions

**Settings → Administration** holds one-shot actions, each behind a confirmation dialog:

| Toiminto                   | Mitä se tekee                                                                                                                                                    |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Aseta aika                 | Sends your phone's clock to the node                                                                                                                             |
| Käynnistä uudelleen        | Restarts the node                                                                                                                                                |
| Reboot into DFU mode       | Restarts an nRF52 node into its bootloader, where it stays off the mesh until new firmware is flashed or it is reset. Shown only for nRF52 nodes |
| Sammuta                    | Powers the node down                                                                                                                                             |
| Palauta tehdasasetukset    | Returns every setting to its factory default                                                                                                                     |
| Tyhjennä NodeDB-tietokanta | Clears the node database. This dialog carries a **Preserve Favorites?** switch                                                                   |

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys, and cannot be undone. Before you reset, use **Export configuration** to save the node's settings and **Backup Keys** on the Security screen to save its keys, so you can put both back afterwards.

### Varmuuskopiointi ja palautus

**Settings → Backup & Restore** writes the connected node's whole configuration to a file with
**Export configuration**, and reads a saved file back in with **Import configuration**.
Traffic Management settings are exported but not applied on import. Export before a factory
reset, or to copy one node's setup onto another. The section is shown for your
own node only, not over remote admin.

The same section backs up the app's own data for a node: its messages and nodes as stored in the
app, not the node's settings.

- **Back up app data** saves that data to a file named after the node and the date.
- **Restore app data** replaces the app's messages and nodes for that node with a backup, after a
  confirmation. It accepts only an undamaged backup made by an app with the same database version.
  The restore takes effect after you restart the app and reconnect to that node.

Both disconnect the node first, after asking, and act on the data of the node that was connected.
With no node connected they act on the most recently connected node. They stay disabled, with
the line _Connect to a radio first. There is no app data to back up or restore yet._, until the
app has stored data for some node.

### Lisäasetukset

**Settings → Advanced** collects the tools that read or rewrite local state, and is likewise shown
for your own node only: **Firmware Update** on OTA-capable hardware, **Clean Node Database**,
**TAK Server**, **Local Mesh Discovery**, and the **Debug Panel**.

#### Tyhjennä NodeDB-tietokanta

Prunes nodes from your node database — from the app's copy _and_ from the node's own, so this is
not a display-only cleanup. The two filters combine rather than acting separately; the screen puts
it as _Selections are additive_.

- **Clean up nodes last seen older than N days** — always applied. The slider runs from 7 days to
  365 and starts at 30; with **Clean up only unknown nodes** turned on, its floor drops to 0.
- **Clean up only unknown nodes** — narrows the same purge to nodes that never sent their user
  info. The age limit still applies on top of it.

The screen lists the nodes queued for deletion as you move the filters. **Clean Now** carries the
purge out, after one more confirmation, and it can't be undone. Favorited nodes, ignored nodes,
and nodes with a public key heard in the last seven days are never removed, whatever the filters
say — that is why the queued list can be shorter than you expect.

#### Vianetsintäpaneeli

Avaa **Paketit**- ja **Sovelluslokit**-välilehdet diagnostiikkatietojen tarkastelua, suodatusta ja vientiä varten. Katso [Virheenjäljityslokit](debug-logs), jossa on täydellinen käyttöohje.

### App Settings

The **App** section of the **Settings** screen configures the app rather than the node, and
appears only when your own node is selected. It holds, in order:

- **Allow analytics and crash reporting.** — opt in or out of diagnostics. Google Play build only.
- **Provide phone location to mesh** — share this phone's position when the node has no GPS fix.
  It needs precise location: with approximate location allowed, turning it on asks for precise.
- **Compact encoding for Cyrillic** — how look-alike characters in names are handled.
- **Language**, **Theme**, and **Units**. **Units** is metric, imperial, or follow the system, and
  is the entry [Units & Locale](units-and-locale) sends you to.
- **Show full message timestamps**.
- **Device DB cache limit**, which sets how much history is kept.
- **Export rangetest packets**, **Export all packets** and **Export node database**.
- **Node Layout** — how much detail each row of the node list shows.
- **Wi-Fi Provisioning for mPWRD-OS** — see [Connections](connections).
- **Message Filter** — hides incoming messages that contain words you list. With no words
  configured it does nothing.
- **System AI** — appears only where app functions are available; see
  [App Functions](app-functions).

A **Permissions** section sits above **App**, so a user who skipped a permission during
onboarding can get back to it. **Information** sits below it, with **Show Introduction**,
**App Notifications**, **System Settings**, **About**, and the app version. **Help &
Documentation** closes the screen.

### Tietoja

**Settings → About** carries the app's own identity rather than the node's, in three sections:

- **Mikä on Meshtastic?** – lyhyt kuvaus projektista.
- **Sovellukset** — avaa **Tarvitsetko laitteiston?** -näkymän, jossa on suosittujen laitteiden karuselli ja linkit niiden ostopaikkoihin. Siellä näytetään myös GitHub-tietovarasto, käytössä oleva sovellusversio ja **Tekijätiedot** (katso seuraava osio).
- **Project information** — links to the website, to this documentation, and to the GNU GPL v3
  license the app is released under.

The footer states the copyright and that the app is free software with no warranty, which you
may redistribute under the same license.

### Kiitokset

**Tietoja**-kohdasta avattava näkymä, jossa luetellaan kaikki sovelluksen käyttämät avoimen lähdekoodin kirjastot lisensseineen. Luettelo luodaan käännöshetkellä AboutLibraries-kirjastolla.

### Troubleshooting remote admin

- **"Could not reach node — try again or move closer."** The target may be out of range or offline, or it doesn't list your node's public key as an admin key. Check the **Admin Key** entries on the target node.
- **"Connect to a radio to administer remote nodes."** Your own node isn't connected. Reconnect it from the [Connections](connections) screen.
- **Muutokset eivät tule voimaan** — jotkin asetukset edellyttävät uudelleenkäynnistystä ennen kuin ne astuvat voimaan. Kokeile Uudelleenkäynnistystä tallennuksen jälkeen.

## Aiheeseen liittyvät aiheet

- [Settings — Radio & User](settings-radio-user) — core node and user profile settings
- [Moduulien määritysviite](https://meshtastic.org/docs/configuration/module) — yksityiskohtainen moduulidokumentaatio meshtastic.org-sivustolla
- [UKK](https://meshtastic.org/docs/faq/) – usein kysytyt kysymykset meshtastic.org -sivustolla
