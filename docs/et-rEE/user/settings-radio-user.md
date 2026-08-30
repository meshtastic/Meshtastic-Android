---
title: Seaded — raadio ja kasutaja
parent: Kasutusjuhend
nav_order: 7
last_updated: 2026-08-29
description: Configure your radio hardware, LoRa presets, user profile, position sharing, power management, and security.
aliases:
  - sätted
  - raadio-sätted
  - kasutaja-sätted
  - lora
---

# Seaded — raadio ja kasutaja

Configure your radio's user identity, region and LoRa parameters, position and power behavior, network and Bluetooth connectivity, and security settings.

## Kasutaja seaded

### Kasutajaprofiil

| Sätted                    | Kirjeldus                                                                                                                                                                                                                                    |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Täis nimi                 | Your display name (up to 39 characters)                                                                                                                                                                                   |
| Lühi nimi                 | 4-character abbreviated name                                                                                                                                                                                                                 |
| Oleku teavitus            | A short free-text status other nodes display alongside your node — up to 80 bytes, cleared with the **✕** in the field. Needs firmware 2.8 or newer, and is absent otherwise                                 |
| Ei võta sõnumeid vastu    | Marks the node as one nobody should try to message — for an unmonitored or infrastructure node. Other clients hide it from the contact list. Needs supporting firmware                                       |
| Litsentseeritud operaator | Enable if you hold an amateur radio license (permits higher power). Turning it on relabels **Long Name** as **Call Sign** and adds a separate Long Name field, and is staged behind a confirmation dialog |

### Applying Changes

Pärast sätete muutmist puuduta nuppu **Salvesta**, et konfiguratsioon raadiosse salvestada. The radio may reboot to apply changes.

The status message is saved with the same **Save**, but it never reboots the node — and, like the
rest of this screen, it can be edited on a remote node you administer.

## Sätted

### Seadme sätted

| Sätted                                        | Kirjeldus                                                                                                                                                                              | Vaikimisi |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Roll                                          | Node behavior (Client, Router, etc.) — each option carries its own description in the picker. Choosing Router asks for confirmation | Klient    |
| Kordusülekannete režiim                       | How the node retransmits messages; each mode is described in the picker                                                                                                                | Kõik      |
| Sõlme(de) teabe levitamine | Sõlme teabe levitamise intervall                                                                                                                                                       | 10800     |
| Topeltpuudutusnupp                            | Treat a double tap as a button press                                                                                                                                                   | Keelatud  |
| Kolmekordne klõps Ad Hoc Ping                 | Send an ad-hoc position ping on a triple click                                                                                                                                         | Keelatud  |
| Südamelöögi LED                               | Blink the status LED periodically                                                                                                                                                      | Lubatud   |
| Ajavöönd                                      | POSIX time-zone string for the device clock, with buttons to copy your phone's zone or clear it                                                                                        | —         |
| Button / Buzzer GPIO                          | Advanced: which pins the button and buzzer are wired to                                                                                                                | —         |

### LoRa sätted

| Sätted                   | Kirjeldus                                                                                                                                                                                        | Vaikimisi                                       |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| Regioon                  | Regulatory region for frequency bands. You must set this before transmitting                                                                                                     | Määramata (tuleb seadistada) |
| Modemi vaikesäte         | Speed/range tradeoff                                                                                                                                                                             | LongFast                                        |
| Hüppete limiit           | Maks uuesti saadetud hüpet                                                                                                                                                                       | 3                                               |
| TX võimsus               | Transmission power (dBm); 0 = max allowed for region                                                                                                                          | 0 (regiooni maks)            |
| Tühista sagedus          | Overrides the computed operating frequency outright (MHz). It does not offset the calculated value — leave at 0 unless you know you need a specific frequency | 0 (use calculated)           |
| Kanali ribalaius         | Ribalaiuse säte                                                                                                                                                                                  | Default for preset                              |
| Kasuta eelseadistust     | On by default. Turn it off to set Spread Factor, Coding Rate and Bandwidth by hand instead of taking them from the modem preset                                                  | On                                              |
| Levitustegur             | Manual mode only: 7–12. Higher spreads further but slower                                                                                                        | From preset                                     |
| Kodeerimiskiirus         | Manual mode only: 5–8. More redundancy costs airtime                                                                                                             | From preset                                     |
| Sageduspesa              | Which slot within the region's band to use. 0 derives it from the primary channel name                                                                                           | 0 (automatic)                |
| Edastus lubatud          | Turning this off makes the node receive-only                                                                                                                                                     | On                                              |
| Töötsükli tühistamine    | Ignores the region's duty-cycle limit. Illegal in most regions; turn it on only where your license permits                                                                       | Väljas                                          |
| Keela MQTT               | Drop packets that arrived from MQTT rather than over the air                                                                                                                                     | Väljas                                          |
| MQTT kasutuses           | Allow your packets to be forwarded to MQTT by gateways                                                                                                                                           | Väljas                                          |
| RX võimendatud võimendus | Extra receive gain on SX126x radios; costs a little current                                                                                                                                      | Väljas                                          |
| PA ventilaator keelatud  | Turn off the power-amplifier fan on hardware that has one                                                                                                                                        | Väljas                                          |

> ⚠️ **Important:** Operating without the correct region may violate local radio regulations. Lisateabe saamiseks vaadake [regiooni seadistamise juhendit](https://meshtastic.org/docs/getting-started/initial-config) aadressil meshtastic.org.

### Modem Presets

> 💡 **Vihje:** **SNR-i piirväärtused** on meelega negatiivsed. LoRa can decode signals _below_ the noise floor, so a more-negative limit means the preset tolerates a weaker, noisier signal (more range). See [How the Signal Meter Works](signal-meter) for the full explanation.

| Preset             | Range                   | Kiirus                   | SNR limiit | Parim                                                                                             |
| ------------------ | ----------------------- | ------------------------ | ---------- | ------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 km   | 21,9 kbps                | −7,5 dB    | Dense urban with line-of-sight; data-heavy applications                                           |
| Short Fast         | ~3 km   | 10,9 kbps                | −7,5 dB    | Linnaosad; hooned mõne kvartali raadiuses                                                         |
| Short Slow         | ~5 km   | 5.5 kbps | −10 dB     | Äärelinna lühimaa; mõõdukas hoonestustihedus                                                      |
| Medium Fast        | ~5 km   | 5.5 kbps | −12,5 dB   | Suburban areas; moderate building density                                                         |
| Medium Slow        | ~8 km   | 1,1 kbps                 | −15 dB     | Suburban/rural; moderate range with slower speed                                                  |
| Long Turbo         | ~10 km  | 4,4 kbps                 | −12,5 dB   | Sarnane ulatus kui Pikk Kauge, aga 500 kHz ribalaiusega; kiirem läbilaskevõime                    |
| Long Fast          | ~10 km  | 1,1 kbps                 | −17,5 dB   | **General use (default)** — balanced range and speed                           |
| Long Moderate      | ~20 km  | 0,34 kbps                | −17,5 dB   | Maapiirkond, mõningase maastikuga; aeg-ajalt kasutatav                                            |
| Lite Fast          | ~5 km   | 5,5 kbps                 | −12,5 dB   | EL 866 MHz SRD sagedusala (125 kHz ribalaius); võrreldav Medium Fast           |
| Lite Slow          | ~10 km  | 1,1 kbps                 | −15 dB     | EL 866 MHz SRD sagedusala (125 kHz ribalaius); võrreldav Long Fast             |
| Narrow Fast        | ~5 km   | 2,7 kbps                 | −10 dB     | EL 868 MHz sagedusala (62,5 kHz sagedusriba); väldib häireid teiste seadmetega |
| Narrow Slow        | ~10 km  | 1,1 kbps                 | −12,5 dB   | EL 868 MHz sagedusala (62,5 kHz ribalaius); võrreldav Long Fast                |
| ~~Long Slow~~      | ~30 km  | 0,18 kbps                | −20 dB     | ⚠️ **Vananenud** — endiselt valitav, kuid võidakse tulevases püsivara versioonis eemaldada        |
| ~~Very Long Slow~~ | ~40+ km | 0,09 kbps                | −20 dB     | ⚠️ **Vananenud** — endiselt valitav, kuid võidakse tulevases püsivara versioonis eemaldada        |

> ℹ️ **Märkus:** Selles tabelis kasutatakse üldlevinud lühinimesid. Rakenduse eelseadistatud rippmenüüs on need järgmised: **Short Range - Fast**, **Long Range - Fast**, **Lite - Fast**, **Narrow - Fast**, jne.

#### Choosing a Modem Preset

The modem preset controls the fundamental tradeoff between **range** and **data rate**:

- **Slower presets** use more spreading, making signals decodable at weaker signal levels (lower SNR limit). See tähendab pikemat ulatust, aga vähem baite sekundis.
- **Faster presets** pack more data per transmission but require a stronger signal to decode.

**Practical guidance:**

- **Linnavõrk (palju sõlmi, lühikesed vahemaad):** Kasutage valikut **Long Fast** (vaikimisi) või **Short Fast**. Suurem kiirus tähendab väiksemat eetriaega, kui kanalit jagavad paljud sõlmed.
- **Rural/sparse mesh (few nodes, long distances):** Use **Long Moderate**. Range matters more than speed when nodes are far apart.
- **Vastavus EL 866/868 MHz regulatsioonidele:** Kasuta **Lite Fast**, **Lite Slow**, **Narrow Fast** või **Narrow Slow** – need on optimeeritud kitsama ribalaiusega EL SRD/868 MHz sagedusaladele.
- **Fikseeritud taristuühendused:** Kasutage **Short Turbo** või **Long Turbo** spetsiaalsete punkt-punkti ühenduste jaoks, millel on head antennid ja otsenähtavus.
- **Mixed environments:** Stick with **Long Fast** — it's the community default and ensures compatibility with others in your area.

All nodes on the same channel must use the same modem preset. Erinevate eelseadetega sõlmed ei saa suhelda isegi siis, kui neil on sama sagedus ja krüpteerimisvõti.

The range estimates in the [Modem Presets](#modem-presets) table assume flat terrain and modest antennas. Kõrguse eelis (mäetipp, katus) suurendab märgatavalt efektiivset ulatust. A well-placed Router with Long Fast can often outperform a ground-level node with Long Slow.

### Ekraani sätted

These control the **radio's own screen**, not the app's.

| Sätted                            | Kirjeldus                                                                                                                                                 |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ekraan sisse lülitatud            | How long the display stays lit before sleeping                                                                                                            |
| Karusselli intervall              | How often the radio cycles between screens on its own                                                                                                     |
| Ekraani režiim                    | Screen layout/density used by the firmware                                                                                                                |
| Ekraani ühikud                    | Metric or Imperial on the radio's screen                                                                                                                  |
| Kasuta 12 tunni formaati          | Show the radio's clock as 12-hour rather than 24-hour                                                                                                     |
| Bold heading                      | Draw the screen's heading text in bold                                                                                                                    |
| Keera ekraani                     | Rotate the display 180° for an inverted mounting                                                                                                          |
| OLED tüüp                         | Auto, SSD1306, SH1106, SH1107                                                                                                                             |
| Ärata puudutusega või liigutusega | Light the screen when the radio is tapped or moved                                                                                                        |
| Kompassi suund                    | Rotation offset for the compass rose (0°, 90°, 180°, 270°)                                                                             |
| Suund alati põhi                  | Locks the compass rose north-up instead of rotating it with your heading. Independent of Compass orientation — neither replaces the other |

### Asukoha sätted

> ⚠️ **Important:** Saving this screen always reboots the radio.

| Sätted                                    | Kirjeldus                                                                                                                                             |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| GPS-režiim (riistvara) | Three-state: GPS enabled, disabled, or not present. Not a simple on/off                                               |
| GPS-i küsimise intervall                  | How often the radio asks its GPS for a fix                                                                                                            |
| Levitamise inteintervall                  | How often the position is shared with the mesh                                                                                                        |
| Nutikas asukoht                           | Broadcast based on movement rather than purely on the clock                                                                                           |
| Nutikas intervall                         | With Smart Position on, the shortest gap between broadcasts                                                                                           |
| Nutikas kaugus                            | With Smart Position on, how far you must move before broadcasting                                                                                     |
| Määratud asukoht                          | Use a manually entered latitude, longitude and altitude instead of the GPS                                                                            |
| Asukoha lipp                              | A group of toggles choosing which fields ride along with a position — altitude, its reference and precision, satellites in view, timestamp, and so on |
| GPS EN / Receive / Transmit GPIO          | Advanced: the pins the GPS module is wired to                                                                                         |

### Toite sätted

| Sätted                                       | Kirjeldus                                                       |
| -------------------------------------------- | --------------------------------------------------------------- |
| Luba energiasäästurežiim                     | Let the radio sleep aggressively between activity               |
| Väljalülitamine voolukatkestuse korral       | Power the device down after external power disappears           |
| Super sügava une kestus                      | How long the deepest sleep state lasts                          |
| Minimaalne ärkveloleku aeg                   | The shortest time the radio stays awake once woken              |
| Oota Bluetoothi ​​kestust                    | How long to wait for a phone to connect before sleeping         |
| ADC kordaja tühistamine                      | Turn on a manual correction for battery-voltage readings        |
| Asenda ADC kordistaja suhe                   | The correction factor itself, used only when the override is on |
| Aku INA_2XX I2C aadress | Address of an external INA-series power sensor, if fitted       |

### Võrgu sätted

> ⚠️ **Important:** Saving this screen always reboots the radio.

| Sätted                            | Kirjeldus                                                                                                                    |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Wi-Fi enabled                     | Enable the Wi-Fi radio (ESP32 radios)                                                                     |
| SSID                              | Network name to connect to. **Scan Wi-Fi QR code** fills this and the password from a standard Wi-Fi QR code |
| Parool                            | Network password                                                                                                             |
| Ethernet lubatud                  | Use a wired connection on hardware that has one                                                                              |
| IPv4 režiim                       | DHCP, or a static address configured with the four fields below                                                              |
| Wi-Fi IP / Subnet / Gateway / DNS | The static address, only used when IPv4 mode is static                                                                       |
| UDP levitamine                    | Share mesh traffic with other nodes over the local network                                                                   |
| NTP server                        | Time synchronization server                                                                                                  |
| rsyslog server                    | Kauglogimise server                                                                                                          |

![Network Config with a static IPv4 address entered](../../assets/screenshots/settings_ipv4_field.png)

### Sinihamba sätted

| Sätted             | Kirjeldus                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| Sinihammas lubatud | Enable/disable BLE radio                                                                               |
| Sidumisreziim      | Määratud PIN kood, juhuslik PIN kood või PIN koodi pole                                                |
| Fikseeritud PIN    | PIN code for pairing. Must be **exactly six digits** — the field rejects anything else |

### Turva sätted

| Sätted                  | Kirjeldus                                                                                                                                                                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Avalik võti             | Sinu sõlme avalik võti (kirjutuskaitstud)                                                                                                                                                                           |
| Administraatori võti    | Keys permitted to administer this node remotely — up to three                                                                                                                                                                          |
| Salajane võti           | Your node's private key (handle securely). Shown redacted when you are viewing another node over remote admin — the firmware does not send it                                                       |
| Loo uus privaatvõti     | Issues a new keypair for this node, behind a confirmation. Every peer that knew your old key must learn the new one                                                                                                    |
| Otsesõnumi võti         | The key used for direct-message encryption                                                                                                                                                                                             |
| ~~Admin kanal lubatud~~ | ⚠️ Eemaldatud — nüüd seadistatakse automaatselt, kui administraatori võti on määratud                                                                                                                                                  |
| Arendaja logi           | Edasta reaalajas arendajalogi jadapordi/sinihamba ​​kaudu                                                                                                                                                                              |
| Jadaühendus lubatud     | Luba jadapordi konsoolile juurdepääs (teisaldatud seadme konfist)                                                                                                                                                   |
| Hallatud režiim         | Restrict non-admin channel changes. Only selectable once an Admin Key is set                                                                                                                                           |
| Taastevõtmed            | Salvesta sõlme võtmete krüpteeritud varukoopia sellesse seadmesse (ainult Android)                                                                                                                                  |
| Taasta võtmed           | Kirjuta varundatud võtmed tagasi sõlme (saadaval siis, kui varukoopia on olemas)                                                                                                                                    |
| Kustuta taastevõtmed    | Eemalda salvestatud võtme varukoopia sellest seadmest                                                                                                                                                                                  |
| Protection Level        | Pakettide autentsus – kuidas käsitletakse allkirjastamata või vahendatud pakette: **range**, **tasakaalustatud** või **ühilduv** (nõuab toetavat püsivara; range režiimi puhul küsitakse kinnitust) |

#### Lockdown Mode

Lockdown encrypts the device's storage and requires a passphrase for each connection. It needs
supporting firmware; the row does not appear otherwise.

Enabling it asks you to set and confirm a passphrase, and to acknowledge that **it locks the debug
(SWD) port on hardware that supports locking**. You can turn lockdown off again at any time with
the passphrase, and a full device erase restores the hardware regardless.

Alongside the passphrase you set the limits that end a session automatically:

| Field                                      | What it does                                      |
| ------------------------------------------ | ------------------------------------------------- |
| Käivitusi alles                            | How many device boots the unlocked state survives |
| Tunde kuni kehtivusaja lõpuni              | Wall-clock lifetime of the unlocked state         |
| Seansi limiit (minutid) | Maximum length of a single unlocked connection    |

Once active, the row reads _Active — storage encrypted, this connection authenticated_ when
unlocked, or _Active — enter your passphrase to unlock this connection_ when not. **Lock Now**
ends the current session immediately. Repeated wrong passphrases are rate-limited with a
back-off before you can try again.

> ⚠️ **Warning:** There is no passphrase recovery. Losing it means erasing the device to get it
> back, which destroys its keys, channels and settings.

![Parooli väli](../../assets/screenshots/settings_password_field.png)

Seaded kasutavad standardseid eelistuste juhtelemente – rippmenüüsid, lülitid ja liugurid:

| Control  | Screenshot                                                                                                  |
| -------- | ----------------------------------------------------------------------------------------------------------- |
| Dropdown | ![A dropdown setting, expanded to show its list of options](../../assets/screenshots/settings_dropdown.png) |
| Toggle   | ![A toggle setting in the on position](../../assets/screenshots/settings_switch.png)                        |
| Slider   | ![A slider setting with its current numeric value shown](../../assets/screenshots/settings_slider.png)      |

## Seotud teemad

- [Seaded — moodulid ja admin](settings-module-admin) — valikulised funktsioonimoodulid ja seadme haldamine
- [Signal Meter](signal-meter) — how modem presets affect signal quality thresholds
- [LoRa konfiguratsioon](https://meshtastic.org/docs/configuration/radio/lora) — üksikasjalik LoRa sätete juhend aadressil meshtastic.org
- [Esialgne konfiguratsioon](https://meshtastic.org/docs/getting-started/initial-config) — piirkonna seadistamise juhend meshtastic.org lehel
