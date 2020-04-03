# Meshtastic-Android

![Android CI](https://github.com/meshtastic/Meshtastic-Android/workflows/Android%20CI/badge.svg?branch=master)

This is a tool for using Android with open-source mesh radios.  For more information see our webpage: [meshtastic.org](https://www.meshtastic.org).  If you are looking for the the device side code, see [here](https://github.com/meshtastic/Meshtastic-esp32).

This project is currently early-alpha, if you have questions or feedback please [Join our discussion forum](https://meshtastic.discourse.group/).  We would love to hear from you.

Once out of alpha the companion Android application will be released here:

[![Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dhomepage%26anid%3Dadmob)

But if you want the bleeding edge app now, we'd love to have your help testing.  Three steps to opt-in to the alpha- test:

1. Join [this Google group](https://groups.google.com/forum/#!forum/meshtastic-alpha-testers) with the account you use in Google Play.
2. Go to this [URL](https://play.google.com/apps/testing/com.geeksville.mesh) to opt-in to the alpha test.
3. If you encounter any problems or have questions, post in our gitter chat and we'll help.

## Analytics setup

Once this project is public, I'll happily let collaborators have access to the crash logs/analytics.

* analytics is currently on, before beta is over I'll make it optional
* on dev devices "adb shell setprop debug.firebase.analytics.app com.geeksville.mesh"
adb shell setprop log.tag.FirebaseCrashlytics DEBUG
* To see analytics: https://console.firebase.google.com/u/0/project/meshutil/analytics/app/android:com.geeksville.mesh/overview
* To see crash logs: https://console.firebase.google.com/u/0/project/meshutil/crashlytics/app/android:com.geeksville.mesh/issues?state=open&time=last-seven-days&type=crash

for verbose logging:
```aidl
adb shell setprop log.tag.FA VERBOSE
```

Copyright 2019, Geeksville Industries, LLC. GPL V3 license


