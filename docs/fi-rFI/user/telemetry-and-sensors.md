---
title: Telemetria ja anturit
parent: Käyttöopas
nav_order: 9
last_updated: 2026-08-27
description: Anturitiedot verkossa — tuetut ympäristö-, ilmanlaatu- ja virta-anturit sekä määritys- ja katseluohjeet.
aliases:
  - sensorit
  - ympäristö
  - sää
  - virtamittarit
---

# Telemetria ja anturit

Meshtastic-radiot voivat kerätä ja jakaa anturitietoja koko verkon laajuisesti.

## Yleiskatsaus

Telemetria mahdollistaa antureilla varustettujen radioiden ympäristö-, virta- ja laitteen kuntotietojen lähettämisen verkkoon. Nämä tiedot näkyvät radion tietonäytössä ja niitä voidaan tallentaa sekä seurata ajan kuluessa.

## Laitteen telemetriatiedot

Kaikki Meshtastic-radiot raportoivat peruslaitetelemetrian:

| Metrijärjestelmä                               | Kuvaus                                             | Tyypillinen vaihteluväli                                           |
| ---------------------------------------------- | -------------------------------------------------- | ------------------------------------------------------------------ |
| Akun varaustaso                                | Varausprosentti                                    | 0–100%                                                             |
| Jännite                                        | Akun jännite                                       | 3.0–4.2V (LiPo) |
| Kanavan Käyttö                                 | Paikallisesti käytetyn käyttöasteen prosenttiosuus | 0–100%                                                             |
| Lähetysajan käyttöaste (TX) | Tämän radion käyttämän lähetysajan prosenttiosuus  | 0–100%                                                             |
| Käyttöaika                                     | Sekuntia viimeisestä käynnistyksestä               | Vaihtelee                                                          |

## Ympäristöanturit

Tuetut ympäristöanturit:

### Lämpötila ja kosteus

| Sensor  | Lämpötila | Kosteus | Ilmanpaine | Viestit                             |
| ------- | --------- | ------- | ---------- | ----------------------------------- |
| BME280  | ✓         | ✓       | ✓          | Suositeltu all-in-one-anturi        |
| BME680  | ✓         | ✓       | ✓          | Lisää kaasuvastus ja IAQ-mittaukset |
| SHT31   | ✓         | ✓       | —          | Korkea tarkkuus                     |
| MCP9808 | ✓         | —       | —          | Tarkka lämpötilamittaus             |
| LPS22   | —         | —       | ✓          | Vain ilmanpaine                     |

### Ilmanlaatu

| Sensor   | Metrijärjestelmä                                   | Viestit                                                                                                                                   |
| -------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| BME680   | Kaasuvastus ja IAQ                                 | Haihtuvat orgaaniset yhdisteet                                                                                                            |
| PMSA003I | PM1.0, PM2.5, PM10 | Hiukkaset                                                                                                                                 |
| SEN55    | PM, Temp, Humidity                                 | Multi-sensor. Its NOx and VOC indices are recorded and included in a CSV export, but are not yet shown as cards or charts |

### Soil

| Metrijärjestelmä   | Yksikkö | Viestit                                         |
| ------------------ | ------- | ----------------------------------------------- |
| Maaperän lämpötila | °C / °F | Reported alongside soil moisture by soil probes |
| Maaperän kosteus   | %       | Volumetric water content                        |

Both appear as info cards on the node detail screen, next to the other environment readings.

### Valo ja UV

| Sensor   | Metrijärjestelmä                              |
| -------- | --------------------------------------------- |
| OPT3001  | Ympäristön valoisuus (lux) |
| VEML7700 | Ympäristön valoisuus (lux) |
| LTR390   | UV-indeksi                                    |

## Virranhallinnan arvot

INA-sarjan virta-antureilla varustetut radiot voivat raportoida:

| Metrijärjestelmä | Kuvaus                          |
| ---------------- | ------------------------------- |
| Jännite          | Per-channel voltage reading     |
| Virta            | Per-channel current draw, in mA |

Up to three channels are reported (ch1–ch3), and each can be given its own label — Solar or Battery, say — from the node detail screen. There is no separate wattage reading; the app charts voltage and current, and does not compute power from them.

Hyödyllinen aurinkolatauksen tai etäradioiden akun kunnon seurantaan.

## Telemetrian määrittäminen

1. Siirry kohtaan **Asetukset → Moduuliasetukset → Telemetria**
2. Each metric group has its own enable toggle and its own interval:

   - **Device Metrics** — battery, channel and airtime utilisation
   - **Environment Metrics** — temperature, humidity, pressure and the other sensor readings
   - **Air Quality Metrics** — particulate and CO₂ readings
   - **Power Metrics** — the per-channel voltage and current readings

   Environment metrics additionally have an on-screen toggle and a Fahrenheit toggle for the
   device's own display.

### Choosing an Interval

> 💡 **Tip:** These are nominal values, not hard schedules. On a congested mesh the firmware
> automatically backs off to longer intervals based on how many nodes are online, so you do not
> need to hand-tune them for mesh size. Lengthen them deliberately only to save battery.

## Ilmanlaatumittarit

Hiukkas- tai CO₂-antureilla varustetut radiot raportoivat ilmanlaatutietoja:

| Metrijärjestelmä      | Yksikkö | Kuvaus                    |
| --------------------- | ------- | ------------------------- |
| PM1.0 | µg/m³   | Erittäin pienet hiukkaset |
| PM2.5 | µg/m³   | Pienhiukkaset             |
| PM10                  | µg/m³   | Karkeat hiukkaset         |
| CO₂                   | ppm     | Hiilidioksidipitoisuus    |

Myös CO₂ anturit, kuten SCD4x, ilmoittavat oman lämpötilansa ja ilmankosteutensa, jotka näytetään edellä olevien mittausten yhteydessä. PM2.5-historiasta sovellus laskee lisäksi **EPA NowCast AQI** -arvon.

CO₂-arvo on värikoodattu vakavuuden mukaan (Hyvä → Tunkkainen → Huono → Epäturvallinen → Evakuoi). Katso [Radion mittarit — Ilmanlaatu](node-metrics#air-quality-metrics), josta löytyvät tarkat ppm-arvot, värit ja AQI-luokituksen tiedot.

Ilmanlaatutiedot voidaan näyttää tietokortteina radion tietonäytössä, esittää kaavioina ajan kuluessa ja viedä CSV-tiedostoon.

## Telemetrian tarkastelu

1. Siirry kohtaan **Radiot** ja valitse radio.
2. Telemetriaosiot näkyvät radion tietonäytössä:
   - Laitemittarit (aina käytettävissä)
   - Ympäristömittarit (jos antureita on saatavilla)
   - Virtamittarit (jos INA-anturi on käytettävissä)
   - Ilmanlaatumittarit (jos PM-/CO₂-anturi on käytettävissä)
3. Historiakaaviot näyttävät mittaustietojen kehittymisen ajan kuluessa.

![Telemetriatoiminnot](../../assets/screenshots/node-metrics_telemetric_actions.png)

## Vianetsintä

- **Ympäristötiedot eivät näy?** Etäradio tarvitsee fyysisen anturin (esim. BME280 I²C-väylässä). Laitetelemetria (akun varaustaso, käyttöaika) on aina käytettävissä, mutta ympäristömittarit edellyttävät laitteistoa.
- **Vanhentuneita lukemia?** Tarkista raportointiväli — erittäin pitkät välit (7200 s tai enemmän) tarkoittavat, että tiedot päivittyvät harvoin. Varmista myös, että etäradio on edelleen verkossa.
- **Anturiristiriita I²C-väylässä?** Jotkin anturit käyttävät samoja I²C-osoitteita. Jos samalla väylällä on useita antureita, tarkista osoiteristiriidat radion sarjaportin virheenkorjaustulosteesta.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — tarkastele telemetriatietoja radion tietonäytössä
- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — telemetriamoduulin määritys
- [Yksiköt ja aluekohtaiset asetukset](units-and-locale) — lämpötilan ja ilmanpaineen näyttöyksiköt

---

