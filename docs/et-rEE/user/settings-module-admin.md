---
title: Sätted - moodulid & admin
parent: Kasutusjuhend
nav_order: 8
last_updated: 2026-08-29
description: Muuda valikulisi funktsioonimooduleid (MQTT, telemeetria, salvestatud sõnumid, TAK ja palju muud) ja teosta seadme haldamist.
aliases:
  - moodul
  - mooduli sätted
  - administratsioon
---

# Sätted - moodulid & admin

Konfi valikulisi funktsioonimooduleid ja teosta seadme haldamist. Moodulid laiendavad Meshtasticut spetsiaalsete võimalustega – igaüht saab eraldi lubada või keelata.

> 💡 **Vihje:** Pead lubama ainult need moodulid, mida sa tegelikult kasutad. Kasutamata moodulite keelamine vähendab eetriaega, säästab akut ja lihtsustab seadistamist.

Mooduli seaded kasutavad kaardipõhist paigutust koos lülitite, rippmenüüde, tekstiväljade ja liuguritega:

![Lülituslüliti](../../assets/screenshots/settings_switch.png)

![Rippmenüü valija](../../assets/screenshots/settings_dropdown.png)

![Teksti väli](../../assets/screenshots/settings_text_field.png)

![Kaardi paigutuse seaded](../../assets/screenshots/settings_titled_card.png)

## Mooduli konf

### MQTT moodul

Sildab võrgusõnumeid MQTT vahendajasse ja sealt internetiühenduse loomiseks. This is how you extend your mesh beyond radio range or integrate with home automation systems.

| Sätted                           | Kirjeldus                                                                                                                                                                               |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Lubatud                          | Lükka MQTT sild sisse                                                                                                                                                                   |
| Server                           | MQTT vahendaja aadress                                                                                                                                                                  |
| Kasutajatunnus                   | Authentication username                                                                                                                                                                 |
| Parool                           | Authentication password                                                                                                                                                                 |
| Encryption                       | Krüpteeri MQTT kasutus                                                                                                                                                                  |
| JSON Output                      | Publish and consume MQTT messages as JSON. Marked deprecated in the protobuf schema, but it is still the only toggle for this behavior and the firmware still honors it |
| TLS                              | Use secure connection                                                                                                                                                                   |
| Root Topic                       | Baas MQTT teema teekond                                                                                                                                                                 |
| Kliendi proksi lubatud           | Let a connected phone carry the node's MQTT traffic, instead of the node reaching the broker itself                                                                                     |
| Selle telefoni MQTT puhverserver | The phone-side half of **Proxy to client enabled**: whether this phone acts as that relay. See [MQTT](mqtt)                                             |
| Kaardiaruanne                    | Publish position to the public map — see below                                                                                                                                          |

**Map Report** expands into its own group:

| Sätted             | Kirjeldus                                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| Lubatud            | Publish to the public map at all                                                                                                |
| Share location     | Explicit consent to include your position. Map reporting does not save without it                               |
| Position precision | How coarsely your position is published                                                                                         |
| Publish interval   | How often to report. Must be **at least 3600 s (1 hour)** — the app blocks saving below that |

Vaata [MQTT](mqtt) üksikasjalikumat kasutusjuhendit, mis sisaldab teavet krüpteerimise, privaatsuse ja vahendaja seadistamise kohta,.

### Jadapordi moodul

Võimaldab jadapordi sidet väliste seadmete integreerimiseks (GPS-moodulid, andurid või kohandatud riistvara). Kui lubatud, saab sõlme jadaühendus saata ja vastu võtta protobuf- või tekstiandmeid, võimaldades välistel mikrokontrolleritel või arvutitel võrguga suhelda.

| Sätted                        | Kirjeldus                                                                                                                                                                                           |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Jadaport lubatud              | Aktiveeri jadapordi ühendus                                                                                                                                                                         |
| Kaja lubatud                  | Kaja sai jadaandmed tagasi                                                                                                                                                                          |
| Jadapordi režiim              | Which protocol the port speaks — Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, VE.Direct, MeshSolar config, Log, or Log (text only) |
| RX / TX                       | GPIO pins for the serial connection                                                                                                                                                                 |
| Jadapordi kiirus              | Port speed                                                                                                                                                                                          |
| Aegunud                       | How long to wait before considering an incoming message complete                                                                                                                                    |
| Konsooli jadapordi alistamine | Take over the port the debug console normally uses                                                                                                                                                  |

### Välise teavitusmoodul

Juhib raadio riistvara summeri-, LED- või vibratsioonihoiatusi. Kasulik seadmetele, mis peavad sõnumi saabumisest füüsiliselt märku andma – eriti kasulik järelevalveta või välistingimustes paigaldamise korral.

There are two independent triggers — an incoming **message**, and a received **bell** character —
and each can drive the LED, the buzzer and the vibration motor separately, giving six toggles.

| Sätted                                              | Kirjeldus                                                                                           |
| --------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Luba Välised teated                                 | Master toggle for the module                                                                        |
| Alert message LED / buzzer / vibra                  | Which outputs fire on an incoming message                                                           |
| Alert bell LED / buzzer / vibra                     | Which outputs fire on a received bell character                                                     |
| Väljund LED (GPIO)               | Pin the LED is wired to                                                                             |
| Väljund LED aktiivne                                | Whether the LED pin is active high or low                                                           |
| Väljund summer (GPIO)            | Pin the buzzer is wired to                                                                          |
| Väljund värin (GPIO)             | Pin the vibration motor is wired to                                                                 |
| Kasuta PWM summerit                                 | Drive the buzzer with PWM, which allows tones rather than a single pitch                            |
| Kasuta I2S summerina                                | Send the alert through an I2S audio output instead                                                  |
| Väljundi kestvus (millisekundit) | How long a single alert lasts                                                                       |
| Häire ajalõpp (sekundit)         | Keep repeating the alert for this long until it is acknowledged. 0 disables nagging |
| Helin                                               | The tone played on a PWM buzzer, in RTTTL. Can be imported from a file              |

### Salvesta & edasta moodul

Puhverdab ajutiselt võrguühenduseta olnud sõlmede sõnumeid ja esitab need uuesti, kui need sõlmed taasühenduvad. Hädavajalik kärgvõrgu jaoks, kus sõlmed regulaarselt levialasse lähevad ja levialast välja lähevad — tagab, et lühikeste katkestuste ajal sõnumid kaotsi ei lähe.

| Sätted                                     | Kirjeldus                                                                                                                                        |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Lubatud                                    | Aktiveeri salvesta ja saada                                                                                                                      |
| Südamelöögid                               | Periodically announce this node's store-and-forward capability                                                                                   |
| Records                                    | Maximum stored messages                                                                                                                          |
| History Return (max)    | Max messages to replay                                                                                                                           |
| History Return (window) | Time window for replay                                                                                                                           |
| Server                                     | Act as a store-and-forward server for the mesh (requires ample memory, e.g. ESP32 with PSRAM) |

> 💡 **Vihje:** Salvesta ja edasta töötab kõige paremini rohke mäluga sõlmedes (ESP32 koos PSRAM-iga). Router nodes are ideal candidates since they're typically always-on.

### Kaugustesti moodul

> ⚠️ **Warning:** Range Test only works on a secured primary channel. As long as your primary channel
> still uses the default channel key, the Enabled, Interval and Save-CSV controls stay disabled, and
> saving force-disables the module if the channel has reverted to public.

Automatiseeritud vahemiku testimise tööriist sõlmede vahelise ühenduse kvaliteedi hindamiseks. Kui lubatud, edastab sõlm perioodiliselt testsõnumeid kasvavate loenduritega. Vastuvõtusõlm logib need sõnumid, võimaldades kõndida või minema sõita ning hiljem analüüsida, millisel kaugusel sõnumite saabumine lakkas.

| Sätted                                 | Kirjeldus                         |
| -------------------------------------- | --------------------------------- |
| Lubatud                                | Aktiveeri levi test               |
| Sender Interval (s) | Time between test transmissions   |
| Salvesta CSV                           | Log received test data to SD card |

### Telemeetria moodul

Juhib, milliseid telemeetriaandmeid sõlm võrguga jagab. Telemeetria sisaldab seadme tervist (aku, tööaeg) ja keskkonnaandurite andmeid (temperatuur, niiskus, rõhk).

Each of the four metric groups has its own enable toggle and its own interval, so you can report
battery health often and sensors rarely.

| Sätted                                          | Kirjeldus                                                                                                                                                                         |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Saada seadme telemeetria                        | Master toggle for device metrics. Only shown on firmware 2.7.12 and newer                                                         |
| Seadme mõõdikute värskendamise intervall        | How often to report battery, uptime and channel utilisation                                                                                                                       |
| Keskkonnamõõdikute lubamine                     | Report the attached environment sensors                                                                                                                                           |
| Keskkonnamõõdikute värskendamise intervall      | How often to report them                                                                                                                                                          |
| Keskkonnamõõdikute ekraanil kuvamine lubatud    | Also show these readings on the device's own display                                                                                                                              |
| Keskkonnamõõdikud kasutavad Fahrenheiti         | Use °F on the device's display. This is the radio's screen only — the app follows your phone's locale, see [Units & Locale](units-and-locale) |
| Õhukvaliteedi moodul on lubatud                 | Report particulate and CO₂ sensor data                                                                                                                                            |
| Õhukvaliteedi näidikute värskendamise intervall | How often to report them                                                                                                                                                          |
| Toitemõõdiku moodul on lubatud                  | Report the per-channel voltage and current readings                                                                                                                               |
| Toitemõõdikute värskendamise intervall          | How often to report them                                                                                                                                                          |
| Toitemõõdiku ekraanil kuvamine lubatud          | Also show power readings on the device's display                                                                                                                                  |

Vaata [Telemeetria & Sensorid](telemetry-and-sensors) toetatud andurite ja sätete soovituste kohta.

### Eelsalvestatud sõnumi moodul

Pre-configured messages accessible from the radio's physical buttons (for radios with rotary encoders, keypads, or similar input hardware). Määra nimekiri kiirsõnumitest, mida saab edastada ilma telefoni ühendamata – ideaalne välitöödeks.

| Sätted                                    | Kirjeldus                                                                                                 |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| ~~Canned message enabled~~                | ⚠️ **Deprecated** in the protobuf schema                                                                  |
| Sõnumid                                   | Reavahetusega eraldatud sõnumite loend                                                                    |
| Saada Kõll                                | Send a bell character alongside the message, so a receiving node's External Notification module can sound |
| Rotary encoder enabled                    | Use a rotary encoder as the input device                                                                  |
| GPIO pin for rotary encoder A / B / press | The three pins the encoder is wired to                                                                    |
| Generate input event on press / CW / CCW  | Which key event each encoder action produces                                                              |
| Üles/Alla/Vali sisend lubatud             | A separate, simpler input scheme using up/down/select buttons rather than an encoder                      |
| ~~Allow input source~~                    | ⚠️ **Deprecated** in the protobuf schema                                                                  |

### Audio moodul

Codec2 audio support for low-bandwidth voice communication over the mesh. See on **eksperimentaalne** funktsioon, mis kodeerib hääle Codec2 koodeki abil väga väikesteks andmepakettideks.

| Sätted                             | Kirjeldus                            |
| ---------------------------------- | ------------------------------------ |
| Lubatud                            | Aktiveeri audio moodul               |
| Codec2 Rate                        | Audio quality/bandwidth tradeoff     |
| PTT Pin                            | GPIO pin for the push-to-talk button |
| I2S sõna valimine                  | GPIO sisend I2S WS jaoks             |
| I2S Data In                        | GPIO sisend I2S DIN jaoks            |
| I2S Data Out                       | GPIO sisend I2S DOUT jaoks           |
| I2S Clock (SCK) | GPIO pin for the I2S bit clock       |

> ℹ️ **Note:** Audio requires specific hardware (I2S microphone and speaker). Voice quality is very low-bandwidth — think "understandable radio voice," not phone-call quality.

### Kaugriistvara moodul

GPIO juhtimine kärgvõrgu kaudu. Võimaldab kaugsõlmel lugeda või kirjutada GPIO sisendkontakte teisel sõlmel – kasulik releede aktiveerimiseks, lülitite lugemiseks või välise riistvara kaugjuhtimiseks.

> ⚠️ **Warning:** Enabling **Allow Undefined Pins** gives remote nodes access to all GPIO pins, which could interfere with the radio's own hardware. Luba ainult spetsiaalsetel GPIO sõlmedel.

| Sätted                     | Kirjeldus                                                               |
| -------------------------- | ----------------------------------------------------------------------- |
| Lubatud                    | Aktiveeri kaugjuurdepääs GPIO-le                                        |
| Luba määratlemata sisendid | Luba juurdepääs mis tahes GPIO sisendile (turvarisk) |
| Available Pins             | Kuni 4 GPIO sisendit, mida see sõlm kauglugemiseks/-kirjutamiseks avab  |

### Naabriinfo moodul

Levitab teavet otse kuuldud naabrite kohta, võimaldades kärgvõrgu topoloogia kaardistamist. Iga lubatud sõlm jagab perioodiliselt nimekirja teistest sõlmedest, mida ta kuuleb ja nende signaali kvaliteedist.

| Sätted                                     | Kirjeldus                                                                                                                                   |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Lubatud                                    | Aktiveeri naabrite leviring                                                                                                                 |
| Värskendusintervall(id) | Kui tihti naabrite nimekirja levitada                                                                                                       |
| Transmit Over LoRa                         | Edasta naabriinfot ka LoRa kaudu, mitte ainult MQTT/telefoni kaudu. Vaikimisi võtit ja nime kasutavat kanalit pole saadaval |

See [Local Mesh Discovery](discovery) for how to use neighbor data for mesh topology exploration.

### Ambientvalguse moodul

Juhib toetatud riistvaral NeoPixeli või muid adresseeritavaid RGB LEDe. Saab kasutada visuaalsete olekuindikaatorite, märgutulede või dekoratiivsete efektide jaoks.

| Sätted                     | Kirjeldus                                                          |
| -------------------------- | ------------------------------------------------------------------ |
| LED olek                   | Turn the LED on or off                                             |
| Pinge                      | LED current limit (0–31)                        |
| Punane / Roheline / Sinine | Individuaalsete värvikanalite väärtused (0–255) |

### Tuvastusanduri moodul

Muudab sõlme liikumis- või ukseanduri hoiatussüsteemiks. Kui GPIO sisend tuvastab oleku muutuse (liikumine tuvastatud, uks avatud), levitab sõlm kärgvõrgu kaudu hoiatusteate.

| Sätted                                     | Kirjeldus                                                                                                         |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Lubatud                                    | Aktiveeri tuvastusandur                                                                                           |
| Ekraani sisend                             | GPIO sisend on anduriga ühendatud                                                                                 |
| Detection Trigger Type                     | Kuidas klemmi olek vastab tuvastussündmusele (nt aktiivne kõrge/madal, serva poolt käivitatav) |
| Use Input Pullup Mode                      | Enable the pin's internal pull-up resistor                                                                        |
| Minimaalne leviring(id) | Minimaalne aeg hoiatusteadete levitamisel                                                                         |
| Riiklik ringhääling(ud) | Perioodilise oleku levitamise intervall                                                                           |
| Saada kelluke                              | Lisa märguannetesse hoiatuskella sümbol                                                                           |
| Sõbralik nimi                              | Selle anduri kohandatud nimi                                                                                      |

### Paxloenduri moodul

People counter using Wi-Fi and BLE probe requests. Loendab lähedalasuvaid seadmeid, kuulates passiivselt sondimistaotlusi, mida telefonid ja sülearvutid võrkude skannimisel edastavad. Available only on ESP32 devices.

| Sätted                                     | Kirjeldus                                                                                                         |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Lubatud                                    | Aktiveeri inimeste loendamine                                                                                     |
| Värskendusintervall(id) | Kui tihti loendeid esitada                                                                                        |
| Wi-Fi RSSI threshold                       | Ignore Wi-Fi probes weaker than this, so distant devices are not counted (defaults to −80 dBm) |
| BLE RSSI threshold                         | The same cut-off for BLE advertisements (defaults to −80 dBm)                                  |

> 💡 **Vihje:** Paxloendur on kasulik jalakäijate liikluse hindamiseks matkaradade alguses, ürituste toimumiskohtades või muudes kohtades. Arvud on ligikaudsed – üks inimene võib kaasas kanda mitut seadet.

### Status Message Module

The status message has no module screen. It is edited with the rest of the node's identity, on
[Settings — Radio & User](settings-radio-user#user-profile).

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. See
[Local Mesh Discovery](discovery) for the full walkthrough.

### TAK moodul

Meeskonna teadlikkuse komplekti integratsioon ATAKi ja WinTAKi koostalitlusvõime tagamiseks. This module only appears
in the list once the node's **Device Role** (Device Config) is set to **TAK** or **TAK Tracker** —
change the role first, otherwise the entry does not appear. See [TAK Integration](tak) for detailed
setup and usage.

## Haldus

### Kaughaldus

Administraatori võtit jagavate sõlmede kaugkonfigureerimine:

1. Vali sõlmede loendist sihtsõlm.
2. Mine selle sõlme **Seadetesse**.
3. Muuda seadistust.
4. Puuduta **Salvesta** – muudatused saadetakse kärgvõrgu kaudu.

> ⚠️ **Important:** Requires an admin key configured on both your node and the target node.

### Tühjenda sõlmede andmebaas

Prunes your local node database. Two independent controls:

- An **age slider** — remove nodes not heard from within that window.
- **Clean unknown nodes only** — restrict the purge to nodes that never sent their user info,
  leaving named nodes alone regardless of age.

### Factory Reset

> ⚠️ **Warning:** Factory reset erases all settings, channels, and keys. This cannot be undone.

Taastab kõik seaded tehase vaikeväärtustele.

### Taaskäivita

Ühendatud või hallatava sõlme kaugkäivitamine.

### Arendaja paneel

Avab vahekaardid **Paketid** ja **Rakenduse logid** diagnostilise väljundi vaatamiseks, filtreerimiseks ja eksportimiseks. See [Debug Logs](debug-logs) for the full walkthrough.

### Teave

**Settings → About** carries the app's own identity rather than the radio's:

Three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one. It also lists the GitHub repository, the running app version, and
  **Acknowledgements** (see the next section).
- **Project information** — links to the website and to this documentation.

### Tänusõnad

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries. It was previously called the license screen.

### Kaug-admin tõrkeotsing

- **"Sihtsõlmelt ei ole vastust"** — sihtsõlm võib olla leviulatusest väljas, võrguühenduseta või sellel võib olla sobimatu administraatori võti. Veendu, et administraatori võti sobiks mõlemas sõlmele.
- **Muudatused ei rakendu** — mõnede sätete jõustumiseks on vaja taaskäivitada. Pärast salvestamist proovi taaskäivitust.
- **Ei näe kaugseadeid** — veendu, et sõlmel oleks sihtsõlme administraatori võti ja et administraatori kanal oleks turbekonfiguratsioonis lubatud. Administraatori kanal seadistatakse automaatselt, kui administraatori võti on määratud.

## Seotud teemad

- [Seaded — Raadio ja kasutaja](settings-radio-user) — raadio ja kasutajaprofiili põhiseaded
- [Mooduli konfiguratsiooni viide](https://meshtastic.org/docs/configuration/module) — üksikasjalik mooduli dokumentatsioon aadressil meshtastic.org
- [KKK](https://meshtastic.org/docs/faq/) — meshtastic.org sageli esitatavad küsimused
