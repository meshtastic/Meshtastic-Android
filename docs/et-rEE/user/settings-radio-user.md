---
title: Settings — Radio & User
parent: User Guide
nav_order: 7
last_updated: 2026-07-08
description: Configure your radio hardware, LoRa presets, user profile, position sharing, power management, and security.
aliases:
  - sätted
  - radio-config
  - user-config
  - lora
---

# Settings — Radio & User

Configure your radio hardware and user identity parameters.

## User Settings

### User Profile

| Setting           | Kirjeldus                                                                             |
| ----------------- | ------------------------------------------------------------------------------------- |
| Täis nimi         | Your display name (up to 39 characters)                            |
| Lühi nimi         | 4-character abbreviated name                                                          |
| Licensed Operator | Enable if you hold an amateur radio license (enables higher power) |

### Applying Changes

Pärast sätete muutmist puuduta nuppu **Salvesta**, et konfiguratsioon raadiosse salvestada. The device may reboot to apply changes.

## Raadio seadistus

### Seadme sätted

| Setting                                       | Kirjeldus                                                               | Vaikimisi |
| --------------------------------------------- | ----------------------------------------------------------------------- | --------- |
| Roll                                          | Node behavior (Client, Router, etc.) | Klient    |
| Kordusülekannete režiim                       | How the node retransmits messages                                       | Kõik      |
| Sõlme(de) teabe levitamine | Sõlme teabe levitamise intervall                                        | 10800     |
| Topeltpuudutusnupp                            | Toiming topeltpuudutuse nupu korral                                     | Keelatud  |

### LoRa sätted

| Setting          | Kirjeldus                                                               | Vaikimisi                                 |
| ---------------- | ----------------------------------------------------------------------- | ----------------------------------------- |
| Regioon          | Regulatory region for frequency bands                                   | Unset (must configure) |
| Modemi vaikesäte | Speed/range tradeoff                                                    | PikkKauge                                 |
| Hüppete limiit   | Maks uuesti saadetud hüpet                                              | 3                                         |
| TX võimsus       | Transmission power (dBm); 0 = max allowed for region | 0 (region max)         |
| Frequency Offset | Fine-tune frequency (MHz)                            | 0                                         |
| Kanali ribalaius | Bandwidth setting                                                       | Default for preset                        |

> ⚠️ **Tähtis:** Enne edastamist **peate** oma piirkonna määrama. Operating without the correct region may violate local radio regulations. Lisateabe saamiseks vaadake [regiooni seadistamise juhendit](https://meshtastic.org/docs/getting-started/initial-config) aadressil meshtastic.org.

### Modem Presets

> 💡 **Tip:** The **SNR Limit** values are negative on purpose. LoRa can decode signals _below_ the noise floor, so a more-negative limit means the preset tolerates a weaker, noisier signal (more range). See [How the Signal Meter Works](signal-meter) for the full explanation.

| Preset             | Range                   | Kiirus                   | SNR Limit | Best For                                                                                          |
| ------------------ | ----------------------- | ------------------------ | --------- | ------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 km   | 21,9 kbps                | −7,5 dB   | Dense urban with line-of-sight; data-heavy applications                                           |
| Short Fast         | ~3 km   | 10,9 kbps                | −7,5 dB   | Urban neighborhoods; buildings within a few blocks                                                |
| Short Slow         | ~5 km   | 5.5 kbps | −10 dB    | Suburban short-range; moderate building density                                                   |
| Medium Fast        | ~5 km   | 5.5 kbps | −12,5 dB  | Suburban areas; moderate building density                                                         |
| Medium Slow        | ~8 km   | 1,1 kbps                 | −15 dB    | Suburban/rural; moderate range with slower speed                                                  |
| Long Turbo         | ~10 km  | 4,4 kbps                 | −12,5 dB  | Sarnane ulatus kui Pikk Kauge, aga 500 kHz ribalaiusega; kiirem läbilaskevõime                    |
| Long Fast          | ~10 km  | 1,1 kbps                 | −17,5 dB  | **General use (default)** — balanced range and speed                           |
| Long Moderate      | ~20 km  | 0,34 kbps                | −17,5 dB  | Rural with some terrain; occasional use                                                           |
| Lite Fast          | ~5 km   | 5,5 kbps                 | −12,5 dB  | EL 866 MHz SRD sagedusala (125 kHz ribalaius); võrreldav Medium Fast           |
| Lite Slow          | ~10 km  | 1,1 kbps                 | −15 dB    | EL 866 MHz SRD sagedusala (125 kHz ribalaius); võrreldav Long Fast             |
| Narrow Fast        | ~5 km   | 2,7 kbps                 | −10 dB    | EL 868 MHz sagedusala (62,5 kHz sagedusriba); väldib häireid teiste seadmetega |
| Narrow Slow        | ~10 km  | 1,1 kbps                 | −12,5 dB  | EL 868 MHz sagedusala (62,5 kHz ribalaius); võrreldav Long Fast                |
| ~~Long Slow~~      | ~30 km  | 0,18 kbps                | −20 dB    | ⚠️ **Vananenud** — endiselt valitav, kuid võidakse tulevases püsivara versioonis eemaldada        |
| ~~Very Long Slow~~ | ~40+ km | 0,09 kbps                | −20 dB    | ⚠️ **Vananenud** — endiselt valitav, kuid võidakse tulevases püsivara versioonis eemaldada        |

> ℹ️ **Märkus:** Selles tabelis kasutatakse üldlevinud lühinimesid. In the app's preset dropdown they read as **Short Range - Fast**, **Long Range - Fast**, **Lite - Fast**, **Narrow - Fast**, and so on.

#### Choosing a Modem Preset

The modem preset controls the fundamental tradeoff between **range** and **data rate**:

- **Slower presets** use more spreading, making signals decodable at weaker signal levels (lower SNR limit). See tähendab pikemat ulatust, aga vähem baite sekundis.
- **Faster presets** pack more data per transmission but require a stronger signal to decode.

**Practical guidance:**

- **Urban mesh (many nodes, short distances):** Use **Long Fast** (default) or **Short Fast**. Suurem kiirus tähendab väiksemat eetriaega, kui kanalit jagavad paljud sõlmed.
- **Rural/sparse mesh (few nodes, long distances):** Use **Long Moderate**. Range matters more than speed when nodes are far apart.
- **EU 866/868 MHz regulatory compliance:** Use **Lite Fast**, **Lite Slow**, **Narrow Fast**, or **Narrow Slow** — these are optimized for the EU SRD/868 MHz bands with narrower bandwidths.
- **Fixed infrastructure links:** Use **Short Turbo** or **Long Turbo** for dedicated point-to-point links with good antennas and line-of-sight.
- **Mixed environments:** Stick with **Long Fast** — it's the community default and ensures compatibility with others in your area.

> ⚠️ **Tähtis:** Kõik samal kanalil olevad sõlmed **peavad** kasutama sama modemi eelseadistust. Erinevate eelseadetega sõlmed ei saa suhelda isegi siis, kui neil on sama sagedus ja krüpteerimisvõti.

> 💡 **Vihje:** ulatuse hinnangud eeldavad tasast maastikku ja mõõdukaid antenne. Elevation advantage (hilltop, rooftop) dramatically increases effective range. A well-placed Router with Long Fast can often outperform a ground-level node with Long Slow.

### Ekraani sätted

| Setting             | Kirjeldus                                                                        |
| ------------------- | -------------------------------------------------------------------------------- |
| Screen Timeout      | Time before display sleeps                                                       |
| Display Units       | Meetriline või Imperial                                                          |
| OLED Type           | Auto, SSD1306, SH1106, SH1107                                                    |
| Compass Orientation | Rotation offset for compass display (0°, 90°, 180°, 270°)     |
| ~~Compass North~~   | ⚠️ **Vananenud** — asendatud kompassi suunaga; nähtav endiselt vanemas püsivaras |

### Asukoha sätted

| Setting                                   | Kirjeldus                          |
| ----------------------------------------- | ---------------------------------- |
| GPS Enabled                               | Enable/disable GPS                 |
| GPS värskendamise intervall               | How often to acquire GPS fix       |
| Asukoha(de) levitamine | How often to share position        |
| Nutikas asukoht                           | Liikumispõhise levitamise lubamine |
| Määratud asukoht                          | Use a manually set position        |

### Toite sätted

| Setting                                 | Kirjeldus                               |
| --------------------------------------- | --------------------------------------- |
| Power Saving                            | Enable low-power sleep mode             |
| Shutdown After (s)   | Auto-shutdown idle timer                |
| ADC Multiplier                          | Aku pinge kalibreerimistegur            |
| Oota sinihammast(id) | Time to wait for BLE connection at boot |
| Mesh SDS Timeout (s) | Super-deep-sleep timeout                |

### Võrgu sätted

| Setting       | Kirjeldus                                  |
| ------------- | ------------------------------------------ |
| WiFi lubatud  | Luba WiFi (ESP32 seade) |
| WiFi SSID     | Network name to connect to                 |
| WiFi PSK      | Network password                           |
| NTP server    | Time synchronization server                |
| Syslog Server | Kauglogimise server                        |

![IP address field](../../assets/screenshots/settings_ipv4_field.png)

### Sinihamba sätted

| Setting            | Kirjeldus                                                           |
| ------------------ | ------------------------------------------------------------------- |
| Sinihammas lubatud | Enable/disable BLE radio                                            |
| Sidumisreziim      | Määratud PIN kood, juhuslik PIN kood või PIN koodi pole             |
| Fikseeritud PIN    | Sidumise PIN (vaikimisi: 123456) |

### Turva sätted

| Setting                 | Kirjeldus                                                                                                                                                                                                      |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Avalik võti             | Your node's public key (read-only)                                                                                                                                                          |
| Administraatori võti    | Kaughalduse võti                                                                                                                                                                                               |
| Salajane võti           | Your node's private key (handle securely)                                                                                                                                                   |
| ~~Admin kanal lubatud~~ | ⚠️ Eemaldatud — nüüd seadistatakse automaatselt, kui administraatori võti on määratud                                                                                                                          |
| Arendaja logi           | Edasta reaalajas arendajalogi jadapordi/sinihamba ​​kaudu                                                                                                                                                      |
| Jadaühendus lubatud     | Luba jadapordi konsoolile juurdepääs (teisaldatud seadme konfist)                                                                                                                           |
| Hallatud režiim         | Piira mitte-administraatori kanali muudatusi                                                                                                                                                                   |
| Taastevõtmed            | Save an encrypted backup of the node's keys on this device (Android only)                                                                                                                   |
| Restore Keys            | Write the backed-up keys back to the node (available once a backup exists)                                                                                                                  |
| Kustuta taastevõtmed    | Remove the stored key backup from this device                                                                                                                                                                  |
| Protection Level        | Packet authenticity — how unsigned or relayed packets are treated: **Strict**, **Balanced**, or **Compatible** (requires supporting firmware; Strict asks for confirmation) |

![Parooli väli](../../assets/screenshots/settings_password_field.png)

Settings use standard preference controls — dropdowns, toggles, and sliders:

| Control  | Screenshot                                                  |
| -------- | ----------------------------------------------------------- |
| Dropdown | ![Dropdown](../../assets/screenshots/settings_dropdown.png) |
| Toggle   | ![Toggle](../../assets/screenshots/settings_switch.png)     |
| Slider   | ![Slider](../../assets/screenshots/settings_slider.png)     |

## Related Topics

- [Seaded — moodulid ja admin](settings-module-admin) — valikulised funktsioonimoodulid ja seadme haldamine
- [Signal Meter](signal-meter) — how modem presets affect signal quality thresholds
- [LoRa konfiguratsioon](https://meshtastic.org/docs/configuration/radio/lora) — üksikasjalik LoRa sätete juhend aadressil meshtastic.org
- [Esialgne konfiguratsioon](https://meshtastic.org/docs/getting-started/initial-config) — piirkonna seadistamise juhend meshtastic.org lehel

---

