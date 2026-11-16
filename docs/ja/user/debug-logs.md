---
title: デバッグログ
nav_order: 22
last_updated: 2026-11-15
description: アプリのデバッグログをアプリ内で表示・エクスポートし、バグの診断に役立つよう GitHub の issue にキャプチャを添付できます。adb は不要です。
aliases:
  - debug-logs
  - logcat
  - app-logs
  - bug-report
---

# デバッグログ

何か不具合が起きたとき、アプリのデバッグログは、バグ報告に添付できる最も役立つものです。 Meshtastic can capture them **for you, from inside the app** — you don't need `adb` or any desktop tooling to collect them.

「**設定 → 詳細設定 → デバッグパネル**」から「**デバッグパネル**」を開きます。

If you're filing an issue, export your logs (see [Exporting](#exporting)) and attach the `.txt` file to your report on the [Meshtastic-Android issue tracker](https://github.com/meshtastic/Meshtastic-Android/issues). 問題が起きた瞬間を含むログのキャプチャは、「動かない」という報告を、開発者が実際に追跡できるものに変えます。

## 2 つのタブ

The Debug Panel has three tabs:

- **Packets** — the decoded mesh traffic your node has sent and received (protocol-level messages). メッシュやルーティングの動作を診断するのに役立ちます。
- **アプリログ**：アプリ自身の診断ログ（Android の _logcat_）。アプリからの警告、エラー、スタックトレースを含みます。通常、バグ報告に必要なのはこちらです。
- **Mirror** — a live view of the connected node's screen with remote controls; see [Mirror](#mirror).

The **Packets** and **App logs** tabs each have their own **export** button and produce their own file, so you can grab whichever is relevant — or both.

## Viewing packets

The **Packets** tab lists each logged packet; tap one to expand it, including its decoded payload where there is one.

- **Search** — type in **Search in logs…** to highlight matches, and step between them with the arrows.
- **Filters** — add your own text filters, or pick from **Preset Filters**: your own node ID, the broadcast address `!ffffffff`, `decoded`, today's date, and each port name. The match toggle switches between matching any filter and matching all of them.
- **Settings** — the gear icon shows **Store mesh logs**, which turns writing packets to the log on or off, and **MeshLog retention period**, from one hour to a year, or **Never delete logs**.
- **Clear Logs** — the delete icon removes every stored packet, after you confirm.

## アプリログを表示する

The **App logs** tab shows the most recent log lines from **this app only** — never other apps on your phone.

- **検索**：検索ボックスに入力すると、一致する行に絞り込めます。
- **レベルフィルター**：**V／D／I／W／E** のチップで、Verbose、Debug、Info、Warn、Error の行を切り替えます。レベルをタップすると非表示になり、もう一度タップすると再表示されます。 Fatal の行は常に表示されます。
- **更新**：更新アイコンで最新のログを読み直します。

エラーと警告の行は色付けされ、問題が目立つようになっています。

## Mirror

The **Mirror** tab shows the screen of the connected node. It reads **Not connected to a device.** with no connection, and **Screen mirroring needs newer firmware** when the node does not report a display; the D-pad still sends keys in that case.

- **Mirror off / Mirroring** — the switch streams the screen continuously, and **Refresh** fetches one frame. On an e-ink display prefer **Refresh**, since the screen updates slowly.
- **D-pad** — arrow keys, **OK**, and **Back** press the node's buttons.
- **Keyboard** — the **Keyboard** chip captures keys: arrows navigate, Enter is OK, Esc is back, and typed text reaches the node. On a node without a touch panel, tapping the mirrored screen toggles it too. On a touch-capable node, a tap or touch & hold on the image sends a touch, and a swipe sends one direction.

Mirroring stops when you leave the tab, and the controls are off on a managed node.

Mirroring needs firmware newer than 2.8.2 on a node running BaseUI. InkHUD nodes can't be mirrored, and Meshtastic UI (MUI) nodes only on firmware built with MUI mirroring, which no release enables, and only on boards that can also run BaseUI. An MUI node streams to one connection at a time, so a second app gets no picture until the first stops.

## エクスポート

**ダウンロード**アイコンをタップすると、現在のログをファイルに保存できます。 The app first shows a warning about what the file contains — confirm it, then choose where the file goes through the system file picker. The file is named with a timestamp (for example `meshtastic_logcat_20260701_143312.txt`) so repeated exports never overwrite each other. The same warning guards the **Packets** tab export.

そのファイルを GitHub の issue に添付してください。

> 🔒 **Privacy:** Exports automatically **redact** private keys, admin keys, session passkeys, and channel PSKs, and suppress raw packet bytes. Everything else stays — the file can contain your message text, precise locations, and node details. Read it before sharing it publicly, and share privately if you have any doubt.

## デスクトップ

デスクトップアプリにはシステムの logcat がないため、「**アプリログ**」タブは代わりに、アプリ自身がキャプチャしたログ出力を表示します。検索、絞り込み、エクスポートは同じように機能します。 Release builds capture Info, Warn, and Error lines; Verbose and Debug lines appear only in development builds.

## 関連トピック

- [ヘルプとアプリ内ドキュメント](help-and-docs)：このドキュメントをアプリ内でオフラインで読む
- [Connections](connections) — if the problem is getting connected to your node in the first place
