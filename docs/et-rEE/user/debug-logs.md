---
title: Arendaja logid
parent: Kasutaja juhis
nav_order: 22
last_updated: 2026-08-30
description: Vaata ja ekspordi rakenduse arendajalogi rakenduse seest ning lisa GitHubi probleemile jäädvustus vigade diagnoosimiseks – adb-d pole vaja.
aliases:
  - arendaja-logid
  - logcat
  - app-logs
  - bug-report
---

# Arendaja logid

Kui midagi töötab valesti, on rakenduse arendajalogid kõige kasulikum asi, mida saad veateatele lisada. Meshtastic can capture them **for you, from inside the app** — you don't need `adb` or any desktop tooling to collect them.

Ava **Arendajapaneel**, valides **Seaded → Täpsemad → Arendajapaneel**.

If you're filing an issue, export your logs (see [Exporting](#exporting)) and attach the `.txt` file to your report on the [Meshtastic-Android issue tracker](https://github.com/meshtastic/Meshtastic-Android/issues). A log capture that covers the moment the problem happened turns "it doesn't work" into something a developer can actually track down.

## The two tabs

Arendajapaneelil on kaks vahekaarti:

- **Paketid** – dekodeeritud võrguliiklus, mida raadio on saatnud ja vastu võtnud (protokollitasemel sõnumid). Useful for diagnosing mesh and routing behavior.
- **App logs** — the app's own diagnostic log (Android _logcat_), including warnings, errors, and stack traces from the app itself. This is usually what a bug report needs.

Each tab has its own **export** button and produces its own file, so you can grab whichever is relevant — or both.

## Viewing app logs

The **App logs** tab shows the most recent log lines from **this app only** — never other apps on your phone.

- **Otsi** – sisesta otsingukasti otsing, et filtreerida sobivate ridade hulgast.
- **Tasemefilter** — **V / D / I / W / E** kiibid lülitavad sisse üksikasjaliku, arendaja-, teabe-, hoiatus- ja vearea. Tap a level to hide it; tap again to bring it back. Fataalseid jooni näidatakse alati.
- **Värskenda** – värskendamise ikoon loeb uuesti viimased logid.

Error and warning lines are tinted so problems stand out.

## Eksportimine

Praeguste logide faili salvestamiseks puuduta ikooni **allalaadimine**. The app first shows a warning about what the file contains — confirm it, then choose where the file goes through the system file picker. The file is named with a timestamp (for example `meshtastic_logcat_20260701_143312.txt`) so repeated exports never overwrite each other. The same warning guards the **Packets** tab export.

Attach that file to your GitHub issue.

> 🔒 **Privacy:** Exports automatically **redact** private keys, admin keys, session passkeys, and channel PSKs, and suppress raw packet bytes. Everything else stays — the file can contain your message text, precise locations, and node details. Read it before sharing it publicly, and share privately if you have any doubt.

## Töölaud

Töölauarakendusel puudub süsteemi logcat, seega kuvatakse vahekaardil **Rakenduse logid** rakenduse enda jäädvustatud logide väljundit. Otsimine, filtreerimine ja eksportimine toimivad samamoodi.

## Seotud teemad

- [Abi ja rakendusesisesed dokumendid](help-and-docs) — selle dokumentatsiooni lugemine rakenduses võrguühenduseta
- [Connections](connections) — if the problem is getting connected to your radio in the first place
