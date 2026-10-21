---
title: Справка и встроенная документация
nav_order: 21
last_updated: 2026-10-21
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

Браузер документации отображает список всех страниц руководства пользователя. Tap a page to read it; images and cross-links work as they do here.

![Оглавление встроенного браузера документации](../../assets/screenshots/docs-browser_toc.png)

### Поиск

Tap the search field at the top of the docs browser to open a full-screen search view. Type to filter pages by title and keywords — results update as you type, under the same field. The back arrow, or the keyboard's search action, closes the view. The field hides while you scroll down the page list and comes back when you scroll up.

![Поиск во встроенной документации](../../assets/screenshots/docs-browser_search.png)

Страница, открытая в браузере:

![Страница документации, отображаемая в приложении](../../assets/screenshots/docs-browser_page.png)

### Pages in your language

With the app set to a language other than English, a page that has a community translation opens in that language, labeled **Community translated** under its title. On the Google-flavor Android build, a page with no community translation opens in English and is then machine-translated on the phone, labeled **Auto-translated**. F-Droid and desktop builds show such a page in English.

## Chirpy — ИИ-ассистент

**Chirpy** отвечает на вопросы о Meshtastic на естественном языке, используя в качестве источника эту встроенную документацию. Нажмите кнопку Chirpy в браузере документации, введите вопрос — и он ответит, предложив ответ и ссылки на соответствующие страницы.

![ИИ-ассистент Chirpy отвечает на вопрос, показывая ссылки на страницы](../../assets/screenshots/docs-browser_chirpy.png)

Chirpy is Google-flavor Android only. On F-Droid and desktop builds the assistant button does not appear at all — the same is true on a phone whose hardware cannot run the on-device model. Browsing and the docs browser's own search work normally on every platform.

> 🔒 **Privacy:** On supported phones running the Google-flavor build, Chirpy runs **on-device** using Gemini Nano — your questions never leave your phone. Небольшая модель загружается при первом использовании.

## Связанные темы

- [Перевод приложения](translate) — как эти страницы локализуются на другие языки
- [Функции приложения](app-functions) — отдельная интеграция с системным ИИ (отличается от Chirpy)
