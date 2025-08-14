<p align="center">
  <a href=""><img width="200" height="200" src="https://raw.githubusercontent.com/meshtastic/Meshtastic-Android/refs/heads/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher2.png"></a>
</p>
<h1 align="center">Meshtastic-Android</h1>

![GitHub all releases](https://img.shields.io/github/downloads/meshtastic/meshtastic-android/total)
[![Android CI](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/android.yml/badge.svg)](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/android.yml)
[![Crowdin](https://badges.crowdin.net/e/f440f1a5e094a5858dd86deb1adfe83d/localized.svg)](https://crowdin.meshtastic.org/android)
[![CLA assistant](https://cla-assistant.io/readme/badge/meshtastic/Meshtastic-Android)](https://cla-assistant.io/meshtastic/Meshtastic-Android)
[![Fiscal Contributors](https://opencollective.com/meshtastic/tiers/badge.svg?label=Fiscal%20Contributors&color=deeppink)](https://opencollective.com/meshtastic/)
[![Vercel](https://img.shields.io/static/v1?label=Powered%20by&message=Vercel&style=flat&logo=vercel&color=000000)](https://vercel.com?utm_source=meshtastic&utm_campaign=oss)

This is a tool for using Android with open-source mesh radios. For more information see our webpage: [meshtastic.org](https://www.meshtastic.org). If you are looking for the the device side code, see [here](https://github.com/meshtastic/Meshtastic-device).

This project is currently beta testing across various providers. If you have questions or feedback please [Join our discussion forum](https://github.com/orgs/meshtastic/discussions) or the [Discord Group](https://discord.gg/meshtastic) . We would love to hear from you!



## Get Meshtastic

The easiest, and fastest way to get the latest beta releases is to use our [github releases](https://github.com/meshtastic/Meshtastic-Android/releases). It is recommend to use these with [Obtainum](https://github.com/ImranR98/Obtainium) to get the latest updates.

Alternatively, these other providers are also available, but may be slower to update. 

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
width="24%">](https://f-droid.org/packages/com.geeksville.mesh/)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid"
width="24%">](https://apt.izzysoft.de/fdroid/index/apk/com.geeksville.mesh)
[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png"
alt="Get it on GitHub"
width="24%">](https://github.com/meshtastic/Meshtastic-Android/releases)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
alt="Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh]"
width="24%">](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dgithub-android-readme)

The play store is the last to update of these options, but if you want to join the Play Store testing program go to [this URL](https://play.google.com/apps/testing/com.geeksville.mesh) and opt-in to become a tester.
If you encounter any problems or have questions, [post in the forum](https://github.com/orgs/meshtastic/discussions) and we'll help.

## Translations

You can help translate the app into your native language using [Crowdin](https://crowdin.meshtastic.org/android).

## Building the Android App

https://meshtastic.org/docs/development/android/

Note: when building the `google` flavor locally you will need to supply your own [Google Maps Android SDK api key](https://developers.google.com/maps/documentation/android-sdk/get-api-key) `MAPS_API_KEY` in `local.properties` in order to use Google Maps.
e.g.
```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

## Contributing guidelines

For detailed instructions on how to contribute, please see our [CONTRIBUTING.md](CONTRIBUTING.md) file.

## Repository Statistics

![Alt](https://repobeats.axiom.co/api/embed/1d75239069a6d671fe0b8f80b2e1bf590a98f0eb.svg "Repobeats analytics image")

Copyright 2025, Meshtastic LLC. GPL-3.0 license
