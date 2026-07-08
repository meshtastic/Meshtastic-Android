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

Kui midagi töötab valesti, on rakenduse arendajalogid kõige kasulikum asi, mida saad veateatele lisada. Meshtastic can capture them **for you, from inside the app** — you no longer need `adb` or any desktop tooling to collect them.

Ava **Arendajapaneel**, valides **Seaded → Täpsemad → Arendajapaneel**.

> 📎 **Filing an issue?** Export your logs (see below) and attach the `.txt` file to your report at [github.com/meshtastic/Meshtastic-Android/issues](https://github.com/meshtastic/Meshtastic-Android/issues). A log capture that covers the moment the problem happened turns "it doesn't work" into something a developer can actually track down.

## The two tabs

Arendajapaneelil on kaks vahekaarti:

- **Packets** — the decoded mesh traffic your radio has sent and received (protocol-level messages). Useful for diagnosing mesh and routing behavior.
- **App logs** — the app's own diagnostic log (Android _logcat_), including warnings, errors, and stack traces from the app itself. This is usually what a bug report needs.

Each tab has its own **export** button and produces its own file, so you can grab whichever is relevant — or both.

## Viewing app logs

The **App logs** tab shows the most recent log lines from **this app only** — never other apps on your device.

- **Search** — type in the search box to filter to matching lines.
- **Tasemefilter** — **V / D / I / W / E** kiibid lülitavad sisse üksikasjaliku, arendaja-, teabe-, hoiatus- ja vearea. Tap a level to hide it; tap again to bring it back. Fatal lines are always shown.
- **Refresh** — the refresh icon re-reads the latest logs.

Error and warning lines are tinted so problems stand out.

## Exporting

Tap the **download** icon to save the current logs to a file. You choose where it goes through the system file picker, and the file is named with a timestamp (for example `meshtastic_logcat_20260701_143312.txt`) so repeated exports never overwrite each other.

Attach that file to your GitHub issue.

> 🔒 **Privacy:** Exports automatically **redact** private keys, admin keys, and session passkeys before writing the file. Kanali PSKid **ei** redigeerita ja logid võivad sisaldada ka sõlmede nimesid, asukohti ja muid tuvastavaid üksikasju – enne avalikult jagamist vaadake fail üle ja jagage seda privaatselt, kui teil on kahtlusi.

## Töölaud

The desktop app has no system logcat, so the **App logs** tab shows the app's own captured log output instead. Search, filtering, and export work the same way.

## Related Topics

- [Help & In-App Docs](help-and-docs) — reading this documentation offline inside the app
- [Connections](connections) — if the problem is getting connected to your radio in the first place

---
