---
title: Справка и встроенная документация
parent: Руководство пользователя
nav_order: 21
last_updated: 2026-08-29
description: Просматривайте эту документацию внутри приложения, выполняйте по ней поиск и задавайте вопросы о Meshtastic ассистенту Chirpy — встроенному ИИ-помощнику на устройстве.
aliases:
  - help
  - docs-browser
  - chirpy
  - assistant
---

# Справка и встроенная документация

Эта же пользовательская документация поставляется **внутри приложения**, поэтому вы можете читать её офлайн, не покидая Meshtastic. Откройте её через **Настройки → Справка и документация**.

## Просмотр

Браузер документации отображает список всех страниц руководства пользователя. Нажмите на страницу, чтобы прочитать её; изображения и перекрёстные ссылки работают точно так же, как здесь.

![Оглавление встроенного браузера документации](../../assets/screenshots/docs-browser_toc.png)

### Поиск

Нажмите на значок поиска и введите текст, чтобы отфильтровать страницы по названию и ключевым словам — результаты обновляются по мере твоего ввода.

![Поиск во встроенной документации](../../assets/screenshots/docs-browser_search.png)

Страница, открытая в браузере:

![Страница документации, отображаемая в приложении](../../assets/screenshots/docs-browser_page.png)

## Chirpy — ИИ-ассистент

**Chirpy** отвечает на вопросы о Meshtastic на естественном языке, используя в качестве источника эту встроенную документацию. Нажмите кнопку Chirpy в браузере документации, введите вопрос — и он ответит, предложив ответ и ссылки на соответствующие страницы.

![ИИ-ассистент Chirpy отвечает на вопрос, показывая ссылки на страницы](../../assets/screenshots/docs-browser_chirpy.png)

Chirpy is Google-flavor Android only. On F-Droid, desktop and iOS builds the assistant button does not appear at all — the same is true on a phone whose hardware cannot run the on-device model. Browsing and the docs browser's own search work normally on every platform.

> 🔒 **Privacy:** On supported phones running the Google-flavor build, Chirpy runs **on-device** using Gemini Nano — your questions never leave your phone. Небольшая модель загружается при первом использовании.

## Связанные темы

- [Перевод приложения](translate) — как эти страницы локализуются на другие языки
- [Функции приложения](app-functions) — отдельная интеграция с системным ИИ (отличается от Chirpy)
