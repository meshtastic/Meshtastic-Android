---
title: Sätted - moodulid & admin
nav_order: 8
last_updated: 2026-10-21
description: Muuda valikulisi funktsioonimooduleid (MQTT, telemeetria, salvestatud sõnumid, TAK ja palju muud) ja teosta seadme haldamist.
aliases:
  - moodul
  - mooduli sätted
  - administratsioon
---

# Sätted - moodulid & admin

Konfi valikulisi funktsioonimooduleid ja teosta seadme haldamist. Moodulid laiendavad Meshtasticut spetsiaalsete võimalustega – igaüht saab eraldi lubada või keelata.

Enable only the modules you use. Kasutamata moodulite keelamine vähendab eetriaega, säästab akut ja lihtsustab seadistamist. A module you expect can be missing for three reasons: your node's role doesn't enable it, your firmware is older than the release that added it, or the firmware build excludes it for this hardware.

Mooduli seaded kasutavad kaardipõhist paigutust koos lülitite, rippmenüüde, tekstiväljade ja liuguritega:

![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)

![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png)

![A text field setting with a value entered](../../assets/screenshots/settings_text_field.png)

![A module settings card with its title and grouped controls](../../assets/screenshots/settings_titled_card.png)

## Mooduli sätted

Every module lives under **Settings → Module configuration**.

Saving a module screen restarts the node: the button reads **Save & restart**, and the node is unreachable for a few seconds afterwards. External Notification and Mesh Beacon are the exceptions: their button reads **Save**. External Notification may still restart the node for some changes, while a Mesh Beacon change applies without a restart.

### MQTT module

Sildab võrgusõnumeid MQTT vahendajasse ja sealt internetiühenduse loomiseks. This is how you extend your mesh beyond LoRa range or integrate with home automation systems.

| Sätted                     | Kirjeldus                                                                                                                                                                                                                                                                        | Vaikimisi             |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| Aadress                    | MQTT vahendaja aadress                                                                                                                                                                                                                                                           | `mqtt.meshtastic.org` |
| Krüpteerimine lubatud      | Publish packets still encrypted with the channel key                                                                                                                                                                                                                             | On                    |
| JSON väljund lubatud       | Publish and consume MQTT messages as JSON. Shown only for a node on firmware older than 2.8, which removed JSON support, or while the setting is still on                                                                                        | Väljas                |
| Kaardiaruannete koostamine | Publish position to the public map — see the map reporting card that follows                                                                                                                                                                                                     | Väljas                |
| Kliendi proksi lubatud     | Let the connected app carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                                                                                                              | Väljas                |
| MQTT lubatud               | Turn the MQTT bridge on                                                                                                                                                                                                                                                          | Väljas                |
| MQTT proxy in this app     | The app-side half of **MQTT Client Proxy**: whether this app acts as that relay. It shows whether the relay is running, and can be turned on only while **MQTT Enabled** and **MQTT Client Proxy** are both on. See [MQTT](mqtt) | —                     |
| Parool                     | Authentication password                                                                                                                                                                                                                                                          | The public broker's   |
| Juurteema                  | Baas MQTT teema teekond                                                                                                                                                                                                                                                          | `msh`                 |
| TLS Enabled                | Use a secure connection                                                                                                                                                                                                                                                          | Väljas                |
| Kasutajatunnus             | Authentication username                                                                                                                                                                                                                                                          | The public broker's   |

Turning **Map Reporting** on reveals a consent card headed _Consent to Share Unencrypted Node Data
via MQTT_, with an **I agree.** switch under it. The rest of the card doesn't exist on screen
until you agree:

| Sätted                   | Kirjeldus                                                                                                                                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Nõustun. | Explicit consent to transmit your node data, including an approximate position, unencrypted. Map reporting doesn't start without it                                      |
| Map Publish Interval     | How often to report. A dropdown of fixed intervals from 1 hour to 72 hours — nothing shorter is offered                                                                  |
| Precision slider         | The slider has no label of its own. It sets how coarsely your position is published, from 12 to 15; the line beneath reads _±_ the resulting distance, in your own units |

Vaata [MQTT](mqtt) üksikasjalikumat kasutusjuhendit, mis sisaldab teavet krüpteerimise, privaatsuse ja vahendaja seadistamise kohta,.

### Serial module

Võimaldab jadapordi sidet väliste seadmete integreerimiseks (GPS-moodulid, andurid või kohandatud riistvara). Kui lubatud, saab sõlme jadaühendus saata ja vastu võtta protobuf- või tekstiandmeid, võimaldades välistel mikrokontrolleritel või arvutitel võrguga suhelda.

| Sätted                                                                                           | Kirjeldus                                                                                                                                                                                                                           | Vaikimisi |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Jadapordi kiirus                                                                                 | Port speed, from **110 Baud** to **921600 Baud**                                                                                                                                                                                    | Vaikimisi |
| Kaja lubatud                                                                                     | Echo packets you send back over the serial port                                                                                                                                                                                     | Väljas    |
| Jadapordi režiim                                                                                 | Which protocol the port speaks: **Default**, **Simple**, **Protobufs**, **Text Message**, **NMEA Positions**, or **CalTopo**, plus `WS85`, `VE_DIRECT`, `MS_CONFIG`, `LOG` and `LOGTEXT` under their firmware names | Vaikimisi |
| Konsooli jadapordi alistamine                                                                    | Take over the port the debug console normally uses                                                                                                                                                                                  | Väljas    |
| Receive data (rxd) GPIO pin / Transmit data (txd) GPIO pin | GPIO pins for the serial connection                                                                                                                                                                                                 | 0         |
| Jadaport lubatud                                                                                 | Turn serial communication on                                                                                                                                                                                                        | Väljas    |
| Aegunud                                                                                          | How long to wait before treating received data as a complete packet. 0 uses 250 ms                                                                                                                                  | 0         |

### External Notification module

Controls buzzer, LED, or vibration alerts on your node hardware. Kasulik seadmetele, mis peavad sõnumi saabumisest füüsiliselt märku andma – eriti kasulik järelevalveta või välistingimustes paigaldamise korral.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles. The
firmware turns the module on, and picks the outputs, on hardware that has a buzzer, vibration motor
or notification LED; elsewhere everything starts off.

| Sätted                                                                                                               | Kirjeldus                                                                                                                              |
| -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Väljund LED aktiivne                                                                                                 | Whether the LED pin is active high or low. Shown once **Output pin GPIO** is set                                       |
| Alert when receiving a bell / Alert GPIO buzzer when receiving a bell / Alert GPIO vibra motor when receiving a bell | Which outputs fire on a received bell character                                                                                        |
| Alert when receiving a message / Alert GPIO buzzer when receiving a message / Vibra Motor Alert                      | Which outputs fire on an incoming message                                                                                              |
| Luba Välised teated                                                                                                  | Master toggle for the module                                                                                                           |
| GPIO Output Duration                                                                                                 | How long a single alert lasts                                                                                                          |
| Nag Timeout                                                                                                          | Keep repeating the alert for this long until it is acknowledged. **Unset** disables nagging                            |
| Väljund LED (GPIO)                                                                                | Pin the LED is wired to                                                                                                                |
| Väljund summer (GPIO)                                                                             | Pin the buzzer is wired to                                                                                                             |
| Väljund värin (GPIO)                                                                              | Pin the vibration motor is wired to                                                                                                    |
| Helin                                                                                                                | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file                                                 |
| Kasuta I2S summerina                                                                                                 | Send the alert through an I2S audio output instead                                                                                     |
| Kasuta PWM summerit                                                                                                  | Drive the buzzer with PWM, which allows tones rather than a single pitch. Shown once **Output pin buzzer GPIO** is set |

### Store & Forward module

Puhverdab ajutiselt võrguühenduseta olnud sõlmede sõnumeid ja esitab need uuesti, kui need sõlmed taasühenduvad. Hädavajalik kärgvõrgu jaoks, kus sõlmed regulaarselt levialasse lähevad ja levialast välja lähevad — tagab, et lühikeste katkestuste ajal sõnumid kaotsi ei lähe. A server needs an ESP32 with PSRAM or a Linux `meshtasticd` node. A node in the Router or Router Late role acts as a server even with the server setting off.

| Sätted                                                | Kirjeldus                                                                          | Vaikimisi |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------- | --------- |
| Ajalookirjete maksimaalne arv                         | Most stored messages returned per request. 0 uses 25               | 0         |
| Ajalookirjete aken                                    | How far back, in minutes, stored messages are returned. 0 uses 240 | 0         |
| Kirjete arv                                           | Most messages to store. 0 uses three quarters of the free PSRAM    | 0         |
| Südamelöögid                                          | Periodically announce that this node is a store-and-forward server                 | Väljas    |
| Server                                                | Act as a store-and-forward server for the mesh                                     | Väljas    |
| Salvesta &amp; edastamine lubatud | Turn store and forward on                                                          | Väljas    |

### Range Test module

Automatiseeritud vahemiku testimise tööriist sõlmede vahelise ühenduse kvaliteedi hindamiseks. Kui lubatud, edastab sõlm perioodiliselt testsõnumeid kasvavate loenduritega. Vastuvõtusõlm logib need sõnumid, võimaldades kõndida või minema sõita ning hiljem analüüsida, millisel kaugusel sõnumite saabumine lakkas.

Range Test only works on a secured primary channel. As long as your primary channel still uses the
default channel key, the interval and CSV controls stay disabled — you can still switch an
already-enabled module off — and saving force-disables the module if the channel has reverted to
public.

| Sätted                                                                | Kirjeldus                                                                                                           | Vaikimisi  |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ---------- |
| Ulatustest lubatud                                                    | Turn range testing on                                                                                               | Väljas     |
| Salvesta .CSV faili (ainult ESP32) | Log received test data as a CSV on the node's own filesystem. ESP32 nodes only                      | Väljas     |
| Sender Interval                                                       | Time between test transmissions, chosen from a dropdown of fixed intervals. **Unset** stops sending | Määramatta |

### Telemetry module

Juhib, milliseid telemeetriaandmeid sõlm võrguga jagab. Telemeetria sisaldab seadme tervist (aku, tööaeg) ja keskkonnaandurite andmeid (temperatuur, niiskus, rõhk).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely. The intervals run from 30 minutes to 72 hours.

| Sätted                                          | Kirjeldus                                                                                                                                                                                   | Vaikimisi |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Õhukvaliteedi moodul on lubatud                 | Report particulate and CO₂ sensor data                                                                                                                                                      | Väljas    |
| Õhukvaliteedi näidikute värskendamise intervall | How often to report them                                                                                                                                                                    | —         |
| Saada seadme telemeetria                        | Send device metrics to the mesh. When it is off, they go only to connected clients. Only shown on firmware 2.7.13 and newer | Väljas    |
| Seadme mõõdikute värskendamise intervall        | How often to report battery, uptime and channel utilization                                                                                                                                 | —         |
| Keskkonnamõõdikud kasutavad Fahrenheiti         | Use °F on the device's display. This is the node's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale)            | Väljas    |
| Keskkonnamõõdikute lubamine                     | Report the attached environment sensors                                                                                                                                                     | Väljas    |
| Keskkonnamõõdikute värskendamise intervall      | How often to report them                                                                                                                                                                    | —         |
| Toitemõõdiku moodul on lubatud                  | Report the per-channel voltage and current readings                                                                                                                                         | Väljas    |
| Toitemõõdikute värskendamise intervall          | How often to report them                                                                                                                                                                    | —         |
| Toitemõõdiku ekraanil kuvamine lubatud          | Also show power readings on the device's display                                                                                                                                            | Väljas    |
| Keskkonnamõõdikute ekraanil kuvamine lubatud    | Also show environment readings on the device's display                                                                                                                                      | Väljas    |

Vaata [Telemeetria & Sensorid](telemetry-and-sensors) toetatud andurite ja sätete soovituste kohta.

### Canned Message module

Pre-configured messages accessible from the node's physical buttons (for nodes with rotary encoders, keypads, or similar input hardware). Määra nimekiri kiirsõnumitest, mida saab edastada ilma telefoni ühendamata – ideaalne välitöödeks.

| Sätted                                                                       | Kirjeldus                                                                                                                                          | Vaikimisi |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Clockwise Rotary Event / Counterclockwise Rotary Event / Encoder Press Event | Which key event each encoder action produces: **None**, **Up**, **Down**, **Left**, **Right**, **Select**, **Back**, or **Cancel** | Puudub    |
| Sõnumid                                                                      | Reavahetusega eraldatud sõnumite loend                                                                                                             | Empty     |
| Pin A / Pin B / Press Pin                                                    | The three pins the rotary encoder is wired to                                                                                                      | 0         |
| Pöördkooder                                                                  | Use a rotary encoder as the input device                                                                                                           | Väljas    |
| Saada Kõll                                                                   | Send a bell character alongside the message, so a receiving node's External Notification module can sound                                          | Väljas    |
| Up/Down/Select Input                                                         | A separate, simpler input scheme using up/down/select buttons rather than an encoder                                                               | Väljas    |

Some boards with built-in encoders or keys come with the input settings filled in. Two older
fields, **Canned message enabled** and **Allow input source**, appear only for a node on firmware
older than 2.7, or while they still hold a value.

### Audio module

Codec2 audio support for low-bandwidth voice communication over the mesh. See on **eksperimentaalne** funktsioon, mis kodeerib hääle Codec2 koodeki abil väga väikesteks andmepakettideks. It needs specific hardware, an I2S microphone and speaker, and the voice quality is low.

| Sätted              | Kirjeldus                                                                                                              | Vaikimisi |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------- | --------- |
| CODEC2 test sagedus | Codec2 bitrate, from **450 bps** to **3200 bps**. Lower rates use less bandwidth and give poorer audio | Vaikimisi |
| CODEC 2 lubatud     | Turn the audio module on                                                                                               | Väljas    |
| I2S Clock Pin       | GPIO pin for the I2S bit clock                                                                                         | 0         |
| I2S Data In Pin     | GPIO pin for I2S data in                                                                                               | 0         |
| I2S Data Out Pin    | GPIO pin for I2S data out                                                                                              | 0         |
| I2S Word Select Pin | GPIO pin for I2S word select                                                                                           | 0         |
| PTT klemm           | GPIO pin for the push-to-talk button. 0 uses GPIO 39                                                   | 0         |

### Remote Hardware module

GPIO juhtimine kärgvõrgu kaudu. Võimaldab kaugsõlmel lugeda või kirjutada GPIO sisendkontakte teisel sõlmel – kasulik releede aktiveerimiseks, lülitite lugemiseks või välise riistvara kaugjuhtimiseks.

> ⚠️ **Warning:** Turning on **Allow undefined pin access** gives remote nodes access to all GPIO pins, which could interfere with the node's own hardware. Turn it on only on dedicated GPIO nodes.

| Sätted                              | Kirjeldus                                                               | Vaikimisi |
| ----------------------------------- | ----------------------------------------------------------------------- | --------- |
| Luba määratlemata klemmi juurdepääs | Luba juurdepääs mis tahes GPIO sisendile (turvarisk) | Väljas    |
| Saadaval klemmid                    | Kuni 4 GPIO sisendit, mida see sõlm kauglugemiseks/-kirjutamiseks avab  | Puudub    |
| Kaug riistvara lubatud              | Turn remote GPIO access on                                              | Väljas    |

### Neighbor Info module

Levitab teavet otse kuuldud naabrite kohta, võimaldades kärgvõrgu topoloogia kaardistamist. Iga lubatud sõlm jagab perioodiliselt nimekirja teistest sõlmedest, mida ta kuuleb ja nende signaali kvaliteedist.

| Sätted                     | Kirjeldus                                                                                                                                            | Vaikimisi                               |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| Naabruskonna teave lubatud | Turn neighbor broadcasting on                                                                                                                        | Väljas                                  |
| Saada LoRa kaudu           | Also broadcast neighbor info over LoRa, not just MQTT and the connected app. Vaikimisi võtit ja nime kasutavat kanalit pole saadaval | Väljas                                  |
| GPS-i küsimise intervall   | How often to broadcast the neighbor list, in seconds. Minimum 4 hours                                                                | 0 (firmware default) |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambient Lighting module

Juhib toetatud riistvaral NeoPixeli või muid adresseeritavaid RGB LEDe. Saab kasutada visuaalsete olekuindikaatorite, märgutulede või dekoratiivsete efektide jaoks.

Pick the color with the swatch's **Preset colors** row or the **Red**, **Green** and **Blue** sliders.
**Advanced** opens number fields for the same three values.

| Sätted                     | Kirjeldus                                 | Vaikimisi                    |
| -------------------------- | ----------------------------------------- | ---------------------------- |
| Pinge                      | LED drive current, 0 to 31                | 10                           |
| LED olek                   | Turn the LED on or off                    | Väljas                       |
| Punane / Roheline / Sinine | Individual color channel values, 0 to 255 | Derived from the node number |

### Detection Sensor module

Muudab sõlme liikumis- või ukseanduri hoiatussüsteemiks. Kui GPIO sisend tuvastab oleku muutuse (liikumine tuvastatud, uks avatud), levitab sõlm kärgvõrgu kaudu hoiatusteate.

| Sätted                                    | Kirjeldus                                                                                                                                                         | Vaikimisi                                                                            |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Tuvastusandur lubatud                     | Turn the detection sensor on. It also has to be on for nodes that receive its alerts or chart its log                                             | Väljas                                                                               |
| GPIO klemmi jälgimine                     | GPIO pin connected to the sensor. 0 disables the module                                                                                           | 0                                                                                    |
| Minimum time between detection broadcasts | Minimaalne aeg hoiatusteadete levitamisel                                                                                                                         | 0, which the firmware treats as 1 hour (12 hours on Router roles) |
| Kasutajasõbralik nimi                     | Custom name for this sensor, used in its messages                                                                                                                 | Empty                                                                                |
| Saada Kõll                                | Include a bell character in alerts                                                                                                                                | Väljas                                                                               |
| State Broadcast Interval                  | How often to send the sensor's state whether or not anything was detected. **Unset** sends only on detection                                      | Määramatta                                                                           |
| Trigger Type                              | How the pin's state maps to a detection event: **High**, **Low**, **Rising Edge**, **Falling Edge**, **Either Edge High**, or **Either Edge Low** | Kõrge                                                                                |
| Use Internal Pull-up                      | Enable the pin's internal pull-up resistor                                                                                                                        | Väljas                                                                               |

### Paxcounter module

People counter using Wi-Fi and BLE probe requests. Loendab lähedalasuvaid seadmeid, kuulates passiivselt sondimistaotlusi, mida telefonid ja sülearvutid võrkude skannimisel edastavad. Available only on ESP32 devices. Counts are approximate, since one person may carry several devices, so the module suits estimating foot traffic at trailheads or event venues.

| Sätted                                  | Kirjeldus                                                                                                | Vaikimisi                               |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| BLE Threshold (dBm)  | The same cut-off for BLE advertisements                                                                  | 0, which the firmware treats as −80 dBm |
| Paxcounter lubatud                      | Turn people counting on. Wi-Fi and Bluetooth must both be off on the node for it to work | Väljas                                  |
| GPS-i küsimise intervall                | Kui tihti loendeid esitada                                                                               | —                                       |
| WiFi Threshold (dBm) | Ignore Wi-Fi probes weaker than this, so distant devices aren't counted                                  | 0, which the firmware treats as −80 dBm |

### Status Message module

The status message has no module screen. It is edited with the rest of the node's identity, on
[Settings — Radio & User](settings-radio-user#user-profile).

### Mesh Beacon module

Broadcasts an invitation to your mesh, and receives invitations from others. The entry appears only
on nodes running firmware 2.8.0 or newer. See [Local Mesh Discovery](discovery) for the full
walkthrough.

### TAK module

Meeskonna teadlikkuse komplekti integratsioon ATAKi ja WinTAKi koostalitlusvõime tagamiseks. The entry is labeled
**TAK (ATAK)**. Two things have to be true before it appears in the module list: the node runs
firmware 2.8.0 or newer, and its **Device Role** on **Settings → Device configuration → Device** is
set to **TAK** or **TAK Tracker**. Vaata [TAK Integration](tak) täpsema seadistamise ja kasutamise kohta.

## Haldus

### Kaughaldus

Remote administration changes another node's settings over the mesh. The target node must list your node's public key as one of its **Admin Key** entries, set on its **Settings → Security** screen.

1. Tap the target node in the node list to open its detail screen.
2. In the **Administration** card, tap **Remote Administration**. The app first establishes a session with the node, showing _Establishing remote session…_, then opens its **Settings**.
3. Muuda seadistust.
4. Tap **Save** or **Save & restart**. The changes are sent over the mesh.

### Device Actions

**Settings → Administration** holds one-shot actions, each behind a confirmation dialog:

| Action               | What it does                                                                                                                                                     |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Määra aeg            | Sends your phone's clock to the node                                                                                                                             |
| Taaskäivita          | Restarts the node                                                                                                                                                |
| Reboot into DFU mode | Restarts an nRF52 node into its bootloader, where it stays off the mesh until new firmware is flashed or it is reset. Shown only for nRF52 nodes |
| Lülita välja         | Powers the node down                                                                                                                                             |
| Tehasesätted         | Returns every setting to its factory default                                                                                                                     |
| NodeDB lähtestamine  | Clears the node database. This dialog carries a **Preserve Favorites?** switch                                                                   |

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys, and cannot be undone. Before you reset, use **Export configuration** to save the node's settings and **Backup Keys** on the Security screen to save its keys, so you can put both back afterwards.

### Varunda ja taasta

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

### Täpsem

**Settings → Advanced** collects the tools that read or rewrite local state, and is likewise shown
for your own node only: **Firmware Update** on OTA-capable hardware, **Clean Node Database**,
**TAK Server**, **Local Mesh Discovery**, and the **Debug Panel**.

#### Tühjenda sõlmede andmebaas

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

#### Arendaja paneel

Avab vahekaardid **Paketid** ja **Rakenduse logid** diagnostilise väljundi vaatamiseks, filtreerimiseks ja eksportimiseks. See [Debug Logs](debug-logs) for the full walkthrough.

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

### Teave

**Settings → About** carries the app's own identity rather than the node's, in three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one. It also lists the GitHub repository, the running app version, and
  **Acknowledgements** (see the next section).
- **Project information** — links to the website, to this documentation, and to the GNU GPL v3
  license the app is released under.

The footer states the copyright and that the app is free software with no warranty, which you
may redistribute under the same license.

### Tänusõnad

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries.

### Troubleshooting remote admin

- **"Could not reach node — try again or move closer."** The target may be out of range or offline, or it doesn't list your node's public key as an admin key. Check the **Admin Key** entries on the target node.
- **"Connect to a radio to administer remote nodes."** Your own node isn't connected. Reconnect it from the [Connections](connections) screen.
- **Muudatused ei rakendu** — mõnede sätete jõustumiseks on vaja taaskäivitada. Pärast salvestamist proovi taaskäivitust.

## Seotud teemad

- [Settings — Radio & User](settings-radio-user) — core node and user profile settings
- [Mooduli konfiguratsiooni viide](https://meshtastic.org/docs/configuration/module) — üksikasjalik mooduli dokumentatsioon aadressil meshtastic.org
- [KKK](https://meshtastic.org/docs/faq/) — meshtastic.org sageli esitatavad küsimused
