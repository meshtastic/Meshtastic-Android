---
title: Android Auto
parent: Käyttöopas
nav_order: 18
last_updated: 2026-07-07
description: Käytä Meshtasticia hands-free-tilassa Android Auto -laitteessa — lue viestit ääneen, vastaa puheella ja tarkista radiot sekä mesh-verkon tila ajon aikana.
aliases:
  - android-auto
  - auto
  - päälaite
  - auto
---

# Android Auto

Meshtastic integroituu Android Auton kanssa, joten voit pysyä yhteydessä mesh-verkkoosi ajon aikana ilman, että otat käsiä pois ratista tai katsetta tiestä.

> ⚠️ **Huom:** Android Auto -tuki on saatavilla vain **Google-version Android-laitteissa**. Sitä ei ole mukana F-Droid-versiossa, eikä se ole saatavilla työpöydällä tai iOS:ssä.

> ℹ️ **Mitä on saatavilla tällä hetkellä:** Google Play -versio tarjoaa tällä hetkellä **vain ilmoituksiin perustuvan** viestituen autoon – saapuvat viestit luetaan auton näytöllä, ja voit vastata niihin sen ilmoitusohjaimilla. Alla kuvattu välilehdellinen **Viestit / Radiot / Tila** -kokemus on Android Car App Libraryyn perustuva beetaversio (Googlen Android Car -mallit ovat toistaiseksi rajoitettuja suljettuihin/sisäisiin Play-jakeluihin), joten se näkyy vain versioissa, jotka on käännetty asetuksella `-PenableCarTemplates=true`. Tämän sivun loppuosa kuvaa tätä beetakokemusta.

## Yleiskatsaus

Kun puhelimesi on yhdistetty Android Auto -laitteeseen (tai kehityksessä käytettävään Desktop Head Unit -emulaattoriin), beetaversio näyttää Meshtasticin Android Car App Libraryyn perustuvana viestisovelluksena, jossa on ajon aikaiseen käyttöön optimoitu välilehdellinen aloitusnäyttö:

- **Viestit** — viimeisimmät keskustelut, ääneen luettava ja äänellä vastattava.
- **Radiot** — mesh-verkon radiolista, sisältää radion yksityiskohtaisen näkymän.
- **Tila** — nykyinen yhteys- ja mesh-verkon tila.

Autosovellus ei luo omaa yhteyttä. Se käyttää Meshtastic-sovelluksen olemassa olevaa yhteyttä, radioa ja viestitilaa, joten se heijastaa sitä, mihin puhelimesi on jo yhdistetty.

> ⚠️ **Huom:** Puhelimen täytyy olla yhdistetty Meshtastic-radioon, jotta autosovellus näyttää reaaliaikaista dataa. Jos sovellus ei ole yhdistetty, auton näyttö näyttää katkaistun yhteyden tilan.

## Viestit

Viestit-välilehti näyttää viimeisimmät keskustelusi. Ajon aikana voit:

- **Kuunnella viestit ääneen**, jotta sinun ei tarvitse katsoa näyttöä.
- **Vastata äänellä tai tekstillä** käyttämällä pääyksikön vastaustoimintoa ja sanella vastauksesi hands-free-tilassa.

## Laitteet

Radiot-välilehti näyttää mesh-verkon radiolistan ajamiseen sopivassa näkymässä. Radion valinta avaa yksityiskohtaisen näkymän, jossa näkyy tärkeimmät tiedot kyseisestä radiosta. Katso [Radiot](nodes) saadaksesi täydellisen selityksen näytettävistä tiedoista.

## Tila

Tila-välilehti näyttää nykyisen yhteyden ja mesh-verkon tilan yhdellä silmäyksellä — hyödyllinen varmistamaan, että olet yhä yhteydessä radioon ilman puhelimen avaamista.

## Aiheeseen liittyvät aiheet

- [Viestit ja kanavat](messages-and-channels) — kaikki viestitoiminnot puhelimessa
- [Radiot](nodes) — yksityiskohtainen radiolista ja radion tietonäkymä
- [Yhteydet](connections) — miten sovellus yhdistyy radioon

---

