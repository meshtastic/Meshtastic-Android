---
title: Kartta ja reittipisteet
parent: Käyttöopas
nav_order: 6
last_updated: 2026-05-13
description: Näytä radion sijainnit kartalla, luo ja jaa reittipisteitä sekä hallitse sijainnin jakamista ja yksityisyyttä.
aliases:
  - kartta
  - reittipisteet
  - gps
  - sijainti
---

# Kartta ja reittipisteet

Karttanäkymä näyttää mesh-verkkosi radioiden maantieteelliset sijainnit sekä jaetut reittipisteet.

## Karttanäkymä

Kartta näyttää:

- **Radion sijainnit** — kunkin sijaintia lähettävän radion värilliset merkit
- **Reittipisteet** — jaetut kiinnostavat kohteet
- **Oma sijaintisi** — nykyinen GPS-sijaintisi

### Radioiden merkinnät

Karttamerkkien värit kertovat:

| Väri      | Merkitys                                            |
| --------- | --------------------------------------------------- |
| Vihreä    | Online (kuultu hiljattain)       |
| Keltainen | Poissa (kuultu 2 tunnin sisällä) |
| Harmaa    | Offline (vanhentunut sijainti)   |
| Sininen   | Oma radio                                           |

### Kartan hallinta

- **Zoomaus** — nipistä tai käytä +/- -painikkeita
- **Panorointi** — vedä karttaa
- **Keskitä** — valitse sijaintipainike keskittääksesi oman sijaintisi
- **Radion napautus** — avaa radion tiedot napauttamalla merkkiä

Kelluva työkalupalkki tarjoaa nopean pääsyn kompassiin, tasojen vaihtoon, suodattimiin, päivitykseen ja sijainnin seurantaan. Napauta kompassia suunnan palauttamiseksi pohjoiseen tai sijaintipainiketta keskittääksesi oman sijaintisi.

![Kartan hallintapainikkeet](../../assets/screenshots/map_controls_overlay.png)

## Reittipisteet

Reittipisteet ovat jaettuja maantieteellisiä kiinnostavia kohteita, jotka kaikki mesh-verkon jäsenet voivat nähdä.

### Reittipisteen luominen

1. Paina karttaa pitkään haluamassasi sijainnissa.
2. Anna nimi ja valinnainen kuvaus.
3. Valitse reittipisteelle kuvake tai emoji.
4. Napauta **Lähetä** jakaaksesi sen verkkoon.

### Reittipisteen ominaisuudet

| Ominaisuus      | Kuvaus                                                      |
| --------------- | ----------------------------------------------------------- |
| Nimi            | Lyhyt tunniste (enintään 30 merkkiä)     |
| Kuvaus          | Valinnainen pidempi kuvaus                                  |
| Kuvake          | Visuaalinen merkkiemoji kartalla                            |
| Lukittu         | Jos lukittu, vain merkin luonut voi muokata tai poistaa sen |
| Voimassaoloaika | Valinnainen automaattinen poistumisaika                     |

### Reittipisteen vanheneminen

Reittipisteet voidaan asettaa vanhenemaan automaattisesti:

- Ei koskaan (oletus) — reittipiste pysyy voimassa kunnes se poistetaan manuaalisesti
- **Aikaperusteinen** — reittipiste poistetaan automaattisesti määritellyn ajan jälkeen (esim. “poista 2 tunnin kuluttua”). Hyödyllinen tilapäisille merkinnöille kuten kokoontumispaikat, vaarat tai tapaamispaikat.

Vanhentuneet reittipisteet piilotetaan automaattisesti kartalta, jotta näkymä pysyy selkeänä. Vanhenemislaskuri alkaa, kun reittipiste luodaan, ei silloin kun muut radiot vastaanottavat sen.

### Reittipisteiden hallinta

- Napauta reittipistettä kartalla nähdäksesi sen tiedot ja koordinaatit
- Muokkaa tai poista luomiasi reittipisteitä
- **Lukitut reittipisteet** eivät ole muiden radioiden muokattavissa tai poistettavissa — vain alkuperäisen merkin luonut voi muuttaa niitä
- Lukitsemattomia reittipisteitä voi muokata kuka tahansa mesh-verkon jäsen

## Sijainnin jakaminen

### Sijainnin jakamisen käyttöönotto

Radiosi jakaa GPS-sijaintinsa seuraavilla tavoilla:

- **Kiinteä väli** — sijainti lähetetään säännöllisin väliajoin
- **Älykäs sijainti** — sijainti lähetetään, kun liike ylittää kynnyksen
- **Manuaalinen** — sijainti jaetaan vain erikseen pyydettäessä

Määritä sijaintikäyttäytyminen kohdassa **Asetukset → Sijainti**.

### Tietosuoja

> 🔒 **Yksityisyys:** sijaintitiedot lähetetään kaikille saman kanavan radioille. Jos et halua sijaintiasi jaettavan, poista GPS käytöstä asetuksista tai käytä kiinteää tai valesijaintia.

## Karttalähteet

Sovellus tukee useita karttatiililähteitä:

- OpenStreetMap (oletus)
- Satelliittikuvat (jos saatavilla)
- Offline-kartat (lataa alueet offline-käyttöä varten)

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — tarkastele ja suodata radiolistaa
- [Radion mittarit](node-metrics) — signaalin laatu ja sijaintihistoria yksittäisille radioille
- [Haku](discovery) — reitinselvitys ja naapuritiedot mesh-verkon ymmärtämiseen
- [Yksiköt ja kieliasetukset](units-and-locale) — etäisyys- ja koordinaattien näyttömuodot

---

