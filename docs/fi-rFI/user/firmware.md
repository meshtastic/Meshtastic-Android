---
title: Laiteohjelmiston päivitykset
parent: Käyttöopas
nav_order: 13
last_updated: 2026-05-13
description: Päivitä radion laiteohjelmisto Bluetoothin kautta — OTA-päivitys, versiohaarat, esitarkistukset ja palautus.
aliases:
  - firmware
  - päivitä
  - ota
  - ohjelmoi
---

# Laiteohjelmiston päivitykset

Pidä Meshtastic-radiosi ajan tasalla uusimmalla firmwarella, jossa on uusia ominaisuuksia, virhekorjauksia ja tietoturvaparannuksia.

## Päivitysten tarkistaminen

1. Siirry kohtaan **Asetukset — Firmware-päivitys** tai napauta päivitysilmoitusta, jos sellainen näkyy.
2. Sovellus tarkistaa saatavilla olevat firmware-versiot.
3. Saatavilla olevat päivitykset näyttävät versionumeron ja muutoslokin yhteenvedon.

## Päivitysmenetelmät

### OTA (Over-The-Air) Bluetooth-yhteyden kautta

Yleisin päivitystapa Android-käyttäjille:

1. Varmista, että radiosi on yhdistetty Bluetoothilla.
2. Siirry Firmware-päivitys -näkymään.
3. Valitse haluamasi firmware-versio.
4. Napauta **Päivitä** aloittaaksesi OTA-päivityksen.
5. Odota, että päivitys valmistuu — älä katkaise yhteyttä päivityksen aikana.

![Päivitysten tarkistaminen](../../assets/screenshots/firmware_checking.png)

> ⚠️ Varoitus: firmware-päivityksen keskeyttäminen voi rikkoa laitteen. Varmista, että radiossa on riittävästi akkua (>50 % suositus) ja pidä Bluetooth-yhteys lähellä koko prosessin ajan.

![Laiteohjelmiston vastuuvapauslauseke](../../assets/screenshots/firmware_disclaimer.png)

### USB-ohjelmointi

Palautusta varten tai jos OTA ei ole käytettävissä:

- Käytä [Meshtastic Web Flasheriä](https://flasher.meshtastic.org)
- Tai [Meshtastic CLI -työkalua](https://meshtastic.org/docs/getting-started/flashing-firmware) työpöytäympäristössä

## Versiokanavat

| Kanava | Kuvaus                                                                |
| ------ | --------------------------------------------------------------------- |
| Vakaa  | Suositeltu useimmille käyttäjille: testatut julkaisut |
| Alpha  | Esijulkaisut voivat sisältää virheitä                                 |

## Päivitystä edeltävä tarkistuslista

Ennen päivityksen aloitamista:

- [ ] Akku > 50 %
- [ ] Vakaa Bluetooth-yhteys
- [ ] Kirjaa ylös nykyiset asetuksesi (ne voivat nollautua suurissa versiomuutoksissa)
- [ ] Tarkista julkaisumuistiinpanot mahdollista yhteensopivuutta rikkovien muutosten varalta

## Päivityksen jälkeen

Onnistuneen päivityksen jälkeen:

- Radio käynnistyy uudelleen automaattisesti
- Bluetooth-yhteys muodostuu uudelleen
- Varmista, että asetuksesi ovat säilyneet
- Tarkista firmware-versio kohdasta **Asetukset — Tietoja**

![Firmware-päivitys onnistui](../../assets/screenshots/firmware_success.png)

## Vianetsintä

### Päivitys jumissa

Jos päivitys näyttää jumiutuneen:

- Odota vähintään 5 minuuttia ennen toimenpiteitä
- Jos laite on edelleen jumissa, käynnistä radio uudelleen virrankatkaisulla
- Yritä päivitystä uudelleen

![Laiteohjelmiston päivitysvirhe](../../assets/screenshots/firmware_error.png)

### Laite ei käynnisty päivityksen jälkeen

Jos laite ei käynnisty:

1. Kokeile yhdistää USB:llä tietokoneeseen
2. Käytä web-flasheria palautus tai DFU-tilassa
3. Asenna tunnetusti toimiva firmware-versio
4. Tarkista Meshtastic Discordista laitekohtaiset palautusohjeet

### Yhteensopivuutta koskevat varoitukset

Sovellus voi näyttää varoituksia, kun:

- Yhdistetyn radion laiteohjelmisto on alle tuetun vähimmäisversion
- Sovelluksen ja laiteohjelmiston version välillä on ristiriita
- Vanhentuneet ominaisuudet vaativat siirtymistä uuteen versioon

> ⚠️ **Tärkeää:** Päivitä Meshtastic-sovellus aina ennen firmware-päivitystä tai sen yhteydessä varmistaaksesi yhteensopivuuden.

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhdistetään uudelleen laiteohjelmiston päivityksen jälkeen
- [Laiteohjelmiston päivitysopas](https://meshtastic.org/docs/getting-started/flashing-firmware) — täydellinen firmware-päivityksen ohjeistus meshtastic.org -sivustolla
- [Tuetut laitteet](https://meshtastic.org/docs/hardware/devices) — tarkista firmware-yhteensopivuus laitekohtaisesti
- [Usein kysytyt kysymykset](https://meshtastic.org/docs/about/faq) — yleisiä kysymyksiä meshtastic.org -sivustolla

---

