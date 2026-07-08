---
title: Как работает измеритель сигнала Meshtastic
parent: Руководство пользователя
nav_order: 15
last_updated: 2026-07-08
description: How the signal meter rates quality from SNR relative to the LoRa modem preset — spread spectrum, presets, and what the bars really mean.
aliases:
  - signal
  - signal-meter
  - snr
  - rssi
---

# Как работает измеритель сигнала Meshtastic

The Meshtastic signal meter — the familiar bars or status color in the app — is calculated very differently than the "bars" on a traditional cell phone or WiFi router.

Большинство потребительских устройств просто измеряют, насколько "громкий" сигнал. Однако, поскольку Meshtastic использует технологию **LoRa (Long Range)**, его измеритель сигнала оценивает, насколько **чистый** сигнал, относительно конкретных настроек вашей mesh-сети.

---

## 1. Две метрики: "Громкость" против "Чёткости"

Каждый раз, когда радиочип LoRa получает сообщение, он сообщает два измерения:

- **RSSI (Индикатор уровня принятого сигнала):** **громкость** необработанного сигнала, поступающего на твою антенну.
- **SNR (Соотношение сигнал/шум):** **Чёткость** сигнала по сравнению с фоновым шумом.

> 💡 **Tip:** Here's an analogy — imagine you are trying to hear a friend talking to you.
>
> - **RSSI** — это громкость его голоса.
> - **Минимальный уровень шума** — это фоновый шум в комнате (кондиционер, разговоры других людей, движение транспорта).
> - **SNR** — это то, насколько легко ты можешь различить голос твоего друга на фоне шума.

Если твой друг кричит тебе на оглушительном рок-концерте, сигнал невероятно громкий (высокий RSSI), но ты всё равно не можешь его понять, потому что фоновый шум громче (плохое SNR). И наоборот, если твой друг шепчет тебе в абсолютно тихой библиотеке, сигнал очень слабый (низкий RSSI), но ты можешь его прекрасно понять (отличный SNR).

---

## 2. Магия LoRa: слышит "ниже уровня шума"

For standard radios (like FM or WiFi), if the background noise is louder than the signal (a negative SNR), the receiver just hears static.

LoRa особенная. Она использует модуляцию **"широкополосного спектра"**, которая позволяет радио математически извлекать сигнал из воздуха даже когда он глубоко _под_ фоновым шумом. Вот почему ты часто будете видеть **отрицательные значения SNR** в Meshtastic (например, -10 дБ, что означает, что сигнал на 10 децибел слабее фонового шума).

В зависимости от того, какой пресет Meshtastic ты используешь (например, `LongFast` или `ShortFast`), у радиоприемника есть определённый **предел SNR** — абсолютное максимальное количество шума, которое он может выдержать, прежде чем сообщение полностью потеряется в помехах.

---

## 3. Как измеритель сигнала рассчитывает качество

The app rates your signal quality (None, Bad, Fair, or Good) from **SNR alone, measured relative to the preset's SNR Limit** — the demodulation floor described above. It deliberately does **not** factor RSSI into the rating: without the local noise floor, RSSI cannot tell you whether a signal is actually decodable, so SNR-versus-the-preset-limit is the meaningful measure. (RSSI is still displayed to you elsewhere.)

Because the rating is relative to the preset limit, the _same_ SNR can rate differently on different presets — `-15 dB` is healthy on `LongSlow` but unusable on `ShortFast`. Letting `limit` be the active preset's SNR Limit, here is how the app picks the bars (or color):

| Уровень     | Деления | Критерии                               | Значение                                                                                 |
| ----------- | ------- | -------------------------------------- | ---------------------------------------------------------------------------------------- |
| Хороший     | 3       | SNR **above** the preset's `limit`     | Signal is comfortably above the demodulation floor — healthy connection. |
| Средний     | 2       | less than `5.5 dB` below the `limit`   | Decodable, but getting close to the floor.                               |
| Плохой      | 1       | `5.5 dB` to `7.5 dB` below the `limit` | At the very edge of what the preset can recover.                         |
| Отсутствует | 0       | more than `7.5 dB` below the `limit`   | Below the floor — transmission lost to noise.                            |

> **Note:** The fixed SNR thresholds you may have seen elsewhere (`-7 dB` / `-15 dB`) are now only used for coloring individual hops in traceroute results — not for the per-node signal meter described here.

---

## 4. Что это значит для тебя

Поскольку измеритель Meshtastic действует как **"Измеритель чёткости"**, он ведёт себя иначе, чем ожидает большинство людей:

> 💡 **Tip:** Don't panic over low RSSI. You might see a seemingly terrible RSSI value like `-118 dBm`. На сотовом телефоне у тебя было бы ноль делений. Но если отношение сигнал/шум (SNR) `+2 дБ`, Meshtastic все равно покажет сильный сигнал! _Библиотека тихая, поэтому шепот слышен идеально._

> ⚠️ **Warning:** Watch out for local noise. If you hook up a massive antenna and see a great RSSI (e.g., `-90 dBm`) but your signal meter is only showing **1 Bar (Bad)**, you have a problem. Это означает, что у тебя есть местные помехи — возможно, дешевый источник питания, шумный компьютер или расположенная рядом радиовышка — создающие столько статического шума, что он заглушает сеть.

## Где появляется информация о сигнале

В приложении данные сигнала отображаются в нескольких местах:

- **Список нод** — значок полос сигнала рядом с каждой нодой
- **Детали ноды** — SNR, RSSI и качество сигнала в разделе метрик устройства
- **Трассировка** — качество сигнала на каждой промежуточной ноде
- **Метрики сигнала** — история данных SNR и RSSI на графиках метрик

![Запись ноды, показывающая значения SNR, RSSI и цветные индикаторы сигнала](../../assets/screenshots/nodes_signal_info.png)

## Связанные темы

- [Nodes](nodes) — where signal bars appear in the node list
- [Node Metrics](node-metrics) — SNR/RSSI history and the per-node signal quality reference
- [Settings — Radio & User](settings-radio-user) — modem presets and their SNR limits

---

