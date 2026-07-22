---
title: Kartta ja reittipisteet
parent: Käyttöopas
nav_order: 6
last_updated: 2026-07-08
description: Näytä radioiden sijainnit kartalla, luo ja jaa reittipisteitä, hallitse karttatasoja ja Site Planneria sekä säädä sijainnin jakamista ja tietosuoja-asetuksia.
aliases:
  - kartta
  - reittipisteet
  - gps
  - sijainti
  - site-planner
  - karttatasot
  - geojson
  - kml
---

# Kartta ja reittipisteet

Karttanäkymä näyttää mesh-verkkosi radioiden maantieteelliset sijainnit sekä jaetut reittipisteet.

## Karttanäkymä

Kartta näyttää:

- **Radion sijainnit** — kunkin sijaintia lähettävän radion värilliset merkit
- **Reittipisteet** — jaetut kiinnostavat kohteet
- **Oma sijaintisi** — nykyinen GPS-sijaintisi

### Radioiden merkinnät

Jokainen sijaintinsa raportoiva radio näytetään **radiotunnisteena**, jossa näkyy radion lyhyt nimi. Tunniste väritetään radion oman tunnistevärin mukaan (pysyvä väri, joka muodostetaan radion numerosta) — sama radiotunniste näkyy myös radioluettelossa, joten radio näyttää kaikkialla samalta. Merkin väri **ei** ilmaise, onko radio verkossa vai poissa verkosta. Kun radion sijainti päivittyy reaaliajassa, sen merkki sykkii hetken. Lähekkäiset merkit ryhmitellään, kun loitonnat karttaa.

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

Waypoints are addressed like messages: by default they broadcast on the primary channel, but a waypoint can also be sent on a specific channel or as a direct message to a single node.

### Reittipisteen ominaisuudet

| Ominaisuus      | Kuvaus                                                                  |
| --------------- | ----------------------------------------------------------------------- |
| Nimi            | Lyhyt tunniste (enintään 29 merkkiä)                 |
| Kuvaus          | Valinnainen pidempi kuvaus                                              |
| Kuvake          | Visuaalinen merkkiemoji kartalla                                        |
| Lukittu         | Jos lukittu, vain merkin luonut voi muokata tai poistaa sen             |
| Voimassaoloaika | Valinnainen automaattinen poistopäivä ja -aika                          |
| Aluerajaus      | Valinnainen aluerajaus saapumis- ja poistumisilmoituksille — katso alla |

### Reittipisteen vanheneminen

Reittipisteet voidaan asettaa vanhenemaan automaattisesti:

- Ei koskaan (oletus) — reittipiste pysyy voimassa kunnes se poistetaan manuaalisesti
- **Ajastettu** — valitse tietty päivämäärä ja kellonaika. Reittipiste poistetaan automaattisesti, kun kyseinen ajankohta on ohitettu. Hyödyllinen tilapäisille merkinnöille kuten kokoontumispaikat, vaarat tai tapaamispaikat.

Vanhentuneet reittipisteet piilotetaan automaattisesti kartalta, jotta näkymä pysyy selkeänä. Poistumisen ajastus perustuu valitsemaasi päivämäärään ja kellonaikaan, ei siihen, kuinka kauan reittipiste on ollut olemassa tai vastaanotettuna.

### Reittipisteiden aluerajaukset

Mikä tahansa reittipiste voidaan määrittää myös **aluerajaukseksi** eli ilmoitusalueeksi, jolloin sinä tai muut käyttäjät saatte ilmoituksen, kun radio saapuu alueelle tai poistuu sieltä:

1. Määritä **aluerajauksen säde** valmiista vaihtoehdoista (tai valitse **Pois** poistaaksesi toiminnon käytöstä), tai napauta **Määritä alue kartalla** piirtääksesi mukautetun suorakulmaisen alueen.
2. Kun alue on määritetty, ota käyttöön **Ilmoita saapuessa** ja/tai **Ilmoita poistuttaessa**.
3. Voit halutessasi ottaa käyttöön **Vain suosikit** -asetuksen, jolloin ilmoituksia näytetään vain suosikkiradioistasi.

Koska reittipisteet (ja niiden aluerajaukset) lähetetään koko mesh-verkkoon, oletusarvoisesti ilmoitukset saa vain niiden **luoja**. Jos joku jakaa kanssasi aluerajauksen sisältävän reittipisteen, sen tiedoissa voit ottaa käyttöön **Ilmoita aluerajan ylityksistä** -asetuksen, jolloin saat myös ilmoitukset alueelle saapumisesta ja sieltä poistumisesta.

### Reittipisteiden hallinta

- Napauta reittipistettä kartalla nähdäksesi sen tiedot ja koordinaatit
- Muokkaa tai poista luomiasi reittipisteitä
- **Lukitut reittipisteet** eivät ole muiden radioiden muokattavissa tai poistettavissa — vain alkuperäisen merkin luonut voi muuttaa niitä
- Lukitsemattomia reittipisteitä voi muokata kuka tahansa mesh-verkon jäsen

## Karttatasot

Napauta kartan karttatasokuvaketta avataksesi **Hallitse karttatasoja** -näkymän, jossa voit tuoda omia karttatasojasi `.kml`-, `.kmz`- tai GeoJSON-muodossa joko avaamalla tiedoston Meshtasticissa tai jakamalla sen sovellukseen toisesta sovelluksesta. Tuodut karttatasot näkyvät luettelossa, jossa voit näyttää tai piilottaa ne sekä poistaa ne. Tämä on käytettävissä sekä Google Play- että F-Droid-versioissa.

### Site Planner

**Site Planner** arvioi lähettimen kuuluvuusalueen ja piirtää sen kartalle värikoodattuna peittoalueena. Avaa se kartan ohjaimista tai radion tiedoista **Arvioi peittoalue** -toiminnolla (näkyy vain radioille, joilla on tunnettu sijainti). Määritä lähettimen asetukset (sijainti, taajuus, lähetysteho, antennin vahvistus ja korkeus), vastaanottimen asetukset (herkkyys ja korkeus) sekä simuloinnin asetukset (enimmäisetäisyys, korkean tarkkuuden maastomalli ja väripaletti), ja käynnistä sitten arviointi. Karttatasojen tavoin myös Site Planner on käytettävissä sekä Google Play- että F-Droid-versioissa.

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

The base map depends on your app flavor: **Google Play** builds use Google Maps, while **F-Droid** and Desktop builds use OpenStreetMap. On top of the base map, additional tile sources are available as overlays or alternatives:

- Satelliittikuvat (jos saatavilla)
- Offline-kartat (lataa alueet offline-käyttöä varten)

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — tarkastele ja suodata radiolistaa
- [Radion mittarit](node-metrics) — signaalin laatu ja sijaintihistoria yksittäisille radioille
- [Haku](discovery) — reitinselvitys ja naapuritiedot mesh-verkon ymmärtämiseen
- [Yksiköt ja kieliasetukset](units-and-locale) — etäisyys- ja koordinaattien näyttömuodot

---

