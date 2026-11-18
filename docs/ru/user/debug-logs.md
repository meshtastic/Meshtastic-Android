---
title: Отладочные журналы
nav_order: 22
last_updated: 2026-11-15
description: Просматривайте и экспортируйте отладочные журналы приложения из самого приложения, а также прикрепляйте их к задаче на GitHub для помощи в диагностике ошибок — без необходимости в adb.
aliases:
  - debug-logs
  - logcat
  - app-logs
  - bug-report
---

# Отладочные журналы

Когда что-то работает неправильно, отладочные журналы приложения — это самое полезное, что вы можете приложить к отчёту об ошибке. Meshtastic может захватить их **для тебя, внутри приложения** — тебе не нужно `adb` или каких-либо десктопных инструментов, чтобы собрать их.

Откройте **Панель отладки** через **Настройки → Дополнительно → Панель отладки**.

If you're filing an issue, export your logs (see [Exporting](#exporting)) and attach the `.txt` file to your report on the [Meshtastic-Android issue tracker](https://github.com/meshtastic/Meshtastic-Android/issues). Захват журнала, охватывающий момент возникновения проблемы, превращает «это не работает» в то, что разработчик действительно может отследить.

## Две вкладки

Панель отладки имеет три вкладки:

- **Пакеты** — декодированный сетевой трафик, который твоя нода отправила и получила (сообщения уровня протокола). Полезно для диагностики поведения mesh-сети и маршрутизации.
- **Журналы приложения** — собственный диагностический журнал приложения (Android _logcat_), включая предупреждения, ошибки и трассировки стека самого приложения. Обычно именно это требуется для отчёта об ошибке.
- **Зеркало** — живой вид экрана подключенной ноды с пультом дистанционного управления; см. [Mirror]#mirror).

The **Packets** and **App logs** tabs each have their own **export** button and produce their own file, so you can grab whichever is relevant — or both.

## Просмотр пакетов

The **Packets** tab lists each logged packet; tap one to expand it, including its decoded payload where there is one.

- **Search** — type in **Search in logs…** to highlight matches, and step between them with the arrows.
- **Filters** — add your own text filters, or pick from **Preset Filters**: your own node ID, the broadcast address `!ffffffff`, `decoded`, today's date, and each port name. The match toggle switches between matching any filter and matching all of them.
- **Settings** — the gear icon shows **Store mesh logs**, which turns writing packets to the log on or off, and **MeshLog retention period**, from one hour to a year, or **Never delete logs**.
- **Clear Logs** — the delete icon removes every stored packet, after you confirm.

## Просмотр журналов приложения

The **App logs** tab shows the most recent log lines from **this app only** — never other apps on your phone.

- **Поиск** — введите текст в поле поиска, чтобы отфильтровать строки по совпадению.
- **Фильтр по уровню** — кнопки V / D / I / W / E переключают отображение строк с уровнями Verbose (Подробный), Debug (Отладка), Info (Информация), Warn (Предупреждение) и Error (Ошибка). Нажмите на уровень, чтобы скрыть его; нажмите ещё раз, чтобы вернуть обратно. Строки с фатальными ошибками отображаются всегда.
- **Обновить** — значок обновления заново считывает последние записи журнала.

Строки с ошибками и предупреждениями подсвечиваются цветом, чтобы проблемы выделялись.

## Зеркало

The **Mirror** tab shows the screen of the connected node. It reads **Not connected to a device.** with no connection, and **Screen mirroring needs newer firmware** when the node does not report a display; the D-pad still sends keys in that case.

- **Mirror off / Mirroring** — the switch streams the screen continuously, and **Refresh** fetches one frame. On an e-ink display prefer **Refresh**, since the screen updates slowly.
- **D-pad** — arrow keys, **OK**, and **Back** press the node's buttons.
- **Keyboard** — the **Keyboard** chip captures keys: arrows navigate, Enter is OK, Esc is back, and typed text reaches the node. On a node without a touch panel, tapping the mirrored screen toggles it too. On a touch-capable node, a tap or touch & hold on the image sends a touch, and a swipe sends one direction.

Mirroring stops when you leave the tab, and the controls are off on a managed node.

Mirroring needs firmware newer than 2.8.2 on a node running BaseUI. InkHUD nodes can't be mirrored, and Meshtastic UI (MUI) nodes only on firmware built with MUI mirroring, which no release enables, and only on boards that can also run BaseUI. An MUI node streams to one connection at a time, so a second app gets no picture until the first stops.

## Экспорт

Нажмите на значок **загрузки**, чтобы сохранить текущие журналы в файл. The app first shows a warning about what the file contains — confirm it, then choose where the file goes through the system file picker. The file is named with a timestamp (for example `meshtastic_logcat_20260701_143312.txt`) so repeated exports never overwrite each other. The same warning guards the **Packets** tab export.

Прикрепите этот файл к своей задаче на GitHub.

> 🔒 **Privacy:** Exports automatically **redact** private keys, admin keys, session passkeys, and channel PSKs, and suppress raw packet bytes. Everything else stays — the file can contain your message text, precise locations, and node details. Read it before sharing it publicly, and share privately if you have any doubt.

## Desktop

У компьютерного приложения нет системного logcat, поэтому вкладка **Журналы приложения** вместо этого показывает собственный захваченный вывод журнала приложения. Поиск, фильтрация и экспорт работают точно так же. Release builds capture Info, Warn, and Error lines; Verbose and Debug lines appear only in development builds.

## Связанные темы

- [Справка и встроенная документация](help-and-docs) — чтение этой документации офлайн внутри приложения
- [Connections](connections) — if the problem is getting connected to your node in the first place
