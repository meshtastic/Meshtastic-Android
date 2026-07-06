---
title: Fehlersuchprotokolle
parent: Benutzerhandbuch
nav_order: 22
last_updated: 2026-07-01
description: View and export the app's own debug logs from inside the app, and attach a capture to a GitHub issue to help diagnose bugs — no adb required.
aliases:
  - debug-logs
  - logcat
  - app-logs
  - bug-report
---

# Fehlersuchprotokolle

When something misbehaves, the app's debug logs are the single most useful thing you can attach to a bug report. Meshtastic can capture them **for you, from inside the app** — you no longer need `adb` or any desktop tooling to collect them.

Open the **Debug Panel** from **Settings → Advanced → Debug Panel**.

> 📎 **Möchtest du ein Problem melden?** Exportiere deine Logs (siehe unten) und hänge die `.txt`-Datei an deinen Bericht unter [github.com/meshtastic/Meshtastic-Android/issues](https://github.com/meshtastic/Meshtastic-Android/issues) an. A log capture that covers the moment the problem happened turns "it doesn't work" into something a developer can actually track down.

## The two tabs

The Debug Panel has two tabs:

- **Packets** — the decoded mesh traffic your radio has sent and received (protocol-level messages). Useful for diagnosing mesh and routing behavior.
- **App logs** — the app's own diagnostic log (Android _logcat_), including warnings, errors, and stack traces from the app itself. This is usually what a bug report needs.

Each tab has its own **export** button and produces its own file, so you can grab whichever is relevant — or both.

## Viewing app logs

The **App logs** tab shows the most recent log lines from **this app only** — never other apps on your device.

- **Search** — type in the search box to filter to matching lines.
- **Level filter** — the **V / D / I / W / E** chips toggle Verbose, Debug, Info, Warn, and Error lines. Tap a level to hide it; tap again to bring it back. Fatal lines are always shown.
- **Refresh** — the refresh icon re-reads the latest logs.

Error and warning lines are tinted so problems stand out.

## Exporting

Tap the **download** icon to save the current logs to a file. Sie wählen den Speicherort über die Dateiauswahl des Systems aus, und die Datei wird mit einem Zeitstempel versehen (zum Beispiel `meshtastic_logcat_20260701_143312.txt`), sodass sich wiederholte Exporte nicht gegenseitig überschreiben.

Attach that file to your GitHub issue.

> 🔒 **Privacy:** Exports automatically **redact** sensitive values such as channel keys and admin/session keys before writing the file. Dennoch können Protokolldateien Knotennamen, Positionen und andere identifizierende Angaben enthalten – werfen Sie einen Blick in die Datei, bevor Sie sie öffentlich machen, und geben Sie sie nur privat weiter, falls Sie unsicher sind.

## Desktop

The desktop app has no system logcat, so the **App logs** tab shows the app's own captured log output instead. Search, filtering, and export work the same way.

## Related Topics

- [Help & In-App Docs](help-and-docs) — reading this documentation offline inside the app
- [Connections](connections) — if the problem is getting connected to your radio in the first place

---
