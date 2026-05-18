---
title: Translations
layout: default
nav_order: 99
---

# Translations

This documentation is translated by the community via [Crowdin](https://crowdin.com/project/meshtastic-android). Translations appear here automatically as volunteers contribute them.

## Available Languages

{% for locale in site.data.locales %}
{% assign locale_code = locale[0] %}
{% assign locale_info = locale[1] %}
{% assign locale_index = locale_code | append: "/index.md" %}
{% assign has_content = false %}
{% for p in site.pages %}
  {% if p.path contains locale_code %}
    {% assign has_content = true %}
    {% break %}
  {% endif %}
{% endfor %}

{% if has_content %}
- [{{ locale_info.name }}]({{ locale_code }}/) ({{ locale_code }})
{% endif %}
{% endfor %}

{% comment %} Show notice if no translations exist yet {% endcomment %}
{% assign any_locale_exists = false %}
{% for locale in site.data.locales %}
  {% assign locale_code = locale[0] %}
  {% for p in site.pages %}
    {% if p.path contains locale_code %}
      {% assign any_locale_exists = true %}
      {% break %}
    {% endif %}
  {% endfor %}
  {% if any_locale_exists %}{% break %}{% endif %}
{% endfor %}

{% unless any_locale_exists %}
> No translations available yet. Want to help? [Join our Crowdin project →](https://crowdin.com/project/meshtastic-android)
{% endunless %}

---

## Contributing Translations

1. Visit [Crowdin](https://crowdin.com/project/meshtastic-android)
2. Select a language
3. Translate the User Guide documentation files
4. Translations are automatically synced to this site via PR

Translation coverage and quality are tracked per-language. Pages without full translation show the English original for untranslated sections.
