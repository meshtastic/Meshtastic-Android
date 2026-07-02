---
title: Virheenjäljityslokitiedot
parent: Käyttöopas
nav_order: 22
last_updated: 2026-07-01
description: Tarkastele ja vie sovelluksen omat virheenjäljityslokitiedot suoraan sovelluksesta ja liitä lokitiedot GitHub-vikaraporttiin ongelmien selvittämisen helpottamiseksi — adb:tä ei tarvita.
aliases:
  - debug-lokitiedot
  - logcat
  - sovelluslokitiedot
  - vikailmoitus
---

# Virheenjäljityslokitiedot

Kun jokin toimii odottamattomasti, sovelluksen virheenjäljityslokitiedot ovat hyödyllisin liite vikaraporttiin. Meshtastic voi kerätä ne **puolestasi suoraan sovelluksessa** — et enää tarvitse `adb`:tä tai työpöytätyökaluja niiden keräämiseen.

Avaa **Virheenjäljityspaneeli** kohdasta **Asetukset → Lisäasetukset → Virheenjäljityspaneeli**.

> 💡 **Ilmoitatko ongelmasta?** Vie lokitiedot (katso alla) ja liitä `.txt`-tiedosto raporttiisi osoitteessa [github.com/meshtastic/Meshtastic-Android/issues](https://github.com/meshtastic/Meshtastic-Android/issues). Lokitietotallenne, joka kattaa hetken jolloin ongelma ilmeni, auttaa muuttamaan "ei toimi" -kuvauksen sellaiseksi, jonka kehittäjä pystyy oikeasti selvittämään.

## Kaksi välilehteä

Virheenjäljityspaneelissa on kaksi välilehteä:

- **Paketit** — radion lähettämä ja vastaanottama purettu mesh-liikenne (protokollatason viestit). Hyödyllinen mesh-verkon ja reitityksen toiminnan diagnosointiin.
- **Sovelluslokitiedot** — sovelluksen oma diagnostiikkaloki (Androidin _logcat_), joka sisältää varoitukset, virheet ja pinoseurannat itse sovelluksesta. Tätä tarvitaan yleensä vikaraportissa.

Jokaisella välilehdellä on oma **Vie**-painikkeensa, ja kumpikin tuottaa oman tiedostonsa. Voit siis liittää raporttiin vain tarvittavan tiedoston tai molemmat.

## Sovelluslokitietojen tarkastelu

**Sovelluslokitiedot**-välilehti näyttää viimeisimmät lokirivit **vain tästä sovelluksesta** — ei koskaan muista laitteesi sovelluksista.

- **Haku** — kirjoita hakukenttään suodattaaksesi vastaavat lokirivit.
- **Tasosuodatin** — **V / D / I / W / E** -painikkeilla voit näyttää tai piilottaa Verbose-, Debug-, Info-, Warn- ja Error-lokirivit. Piilota taso napauttamalla sitä. Napauta uudelleen, niin se tulee takaisin näkyviin. Vakavat virheet näytetään aina.
- **Päivitä** — päivityskuvake lukee uusimmat lokitiedot uudelleen.

Virhe- ja varoitusrivit on korostettu, jotta ongelmat erottuvat paremmin.

## Lokien vienti

Napauta **latauskuvaketta** tallentaaksesi nykyiset lokitiedot tiedostoon. Valitset tallennuspaikan järjestelmän tiedostonvalitsimessa, ja tiedosto nimetään aikaleimalla (esimerkiksi `meshtastic_logcat_20260701_143312.txt`), jotta aiemmat viennit eivät ylikirjoitu.

Liitä tämä tiedosto GitHub-vikaraporttiisi.

> 🔒 **Tietosuoja:** Viedyt lokitiedot **peittävät automaattisesti** arkaluonteiset tiedot, kuten kanava- sekä ylläpito ja istuntoavaimet, ennen tiedoston kirjoittamista. Siitä huolimatta lokitiedot voivat sisältää radion nimiä, sijainteja ja muita tunnistetietoja. Tarkista tiedosto ennen sen julkista jakamista, ja jaa se yksityisesti, jos olet epävarma.

## Työpöytä

Työpöytäsovelluksessa ei ole järjestelmän logcat-lokitietoa, joten **Sovelluslokit**-välilehti näyttää sen sijaan sovelluksen itse keräämät lokit. Haku, suodatus ja vienti toimivat samalla tavalla.

## Aiheeseen liittyvät aiheet

- [Ohjeet ja sovelluksen sisäinen dokumentaatio](help-and-docs) — tämän dokumentaation lukeminen offline-tilassa sovelluksessa
- [Yhteydet](connections) — jos ongelma liittyy radion yhdistämiseen

---
