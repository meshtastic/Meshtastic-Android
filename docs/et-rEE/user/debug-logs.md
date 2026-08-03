---
title: Arendaja logid
parent: Kasutaja juhis
nav_order: 22
last_updated: 2026-07-08
description: Vaata ja ekspordi rakenduse arendajalogi rakenduse seest ning lisa GitHubi probleemile jäädvustus vigade diagnoosimiseks – adb-d pole vaja.
aliases:
  - arendaja-logid
  - logcat
  - app-logs
  - bug-report
---

# Arendaja logid

Kui midagi töötab valesti, on rakenduse arendajalogid kõige kasulikum asi, mida saad veateatele lisada. Meshtastic saab need **sinu eest, rakenduse seest** jäädvustada – sa ei vaja enam nende kogumiseks `adb`-d ega muid töölaua tööriistu.

Ava **Arendajapaneel**, valides **Seaded → Täpsemad → Arendajapaneel**.

> 📎 **Esitad probleemi?** Eksporti oma logid (vt allpool) ja lisa `.txt`-fail oma aruandele aadressil [github.com/meshtastic/Meshtastic-Android/issues](https://github.com/meshtastic/Meshtastic-Android/issues). A log capture that covers the moment the problem happened turns "it doesn't work" into something a developer can actually track down.

## The two tabs

Arendajapaneelil on kaks vahekaarti:

- **Paketid** – dekodeeritud võrguliiklus, mida raadio on saatnud ja vastu võtnud (protokollitasemel sõnumid). Useful for diagnosing mesh and routing behavior.
- **App logs** — the app's own diagnostic log (Android _logcat_), including warnings, errors, and stack traces from the app itself. This is usually what a bug report needs.

Each tab has its own **export** button and produces its own file, so you can grab whichever is relevant — or both.

## Viewing app logs

Rakenduse logide vahekaart kuvab ainult selle rakenduse uusimaid logisid – mitte kunagi teiste seadmes olevate rakenduste logisid.

- **Search** — type in the search box to filter to matching lines.
- **Tasemefilter** — **V / D / I / W / E** kiibid lülitavad sisse üksikasjaliku, arendaja-, teabe-, hoiatus- ja vearea. Tap a level to hide it; tap again to bring it back. Fataalseid jooni näidatakse alati.
- **Refresh** — the refresh icon re-reads the latest logs.

Error and warning lines are tinted so problems stand out.

## Exporting

Praeguste logide faili salvestamiseks puuduta ikooni **allalaadimine**. Süsteemi failivalijast valite, kuhu see liigub ja failile lisatakse ajatempliga nimi (näiteks `meshtastic_logcat_20260701_143312.txt`), nii, et korduvad ekspordid ei kirjuta üksteist kunagi üle.

Attach that file to your GitHub issue.

> 🔒 **Privaatsus:** Ekspordib automaatselt **redigeeri** privaatvõtmed, administraatori võtmed ja seansi paroolid enne faili kirjutamist. Kanali PSKid **ei** redigeerita ja logid võivad sisaldada ka sõlmede nimesid, asukohti ja muid tuvastavaid üksikasju – enne avalikult jagamist vaadake fail üle ja jaga seda privaatselt, kui teil on kahtlusi.

## Töölaud

Töölauarakendusel puudub süsteemi logcat, seega kuvatakse vahekaardil **Rakenduse logid** rakenduse enda jäädvustatud logide väljundit. Search, filtering, and export work the same way.

## Related Topics

- [Help & In-App Docs](help-and-docs) — reading this documentation offline inside the app
- [Connections](connections) — if the problem is getting connected to your radio in the first place

---
