# Meshtastic-Android

![Android CI](https://github.com/meshtastic/Meshtastic-Android/workflows/Android%20CI/badge.svg?branch=master)

This is a tool for using Android with open-source mesh radios.  For more information see our webpage: [meshtastic.org](https://www.meshtastic.org).  If you are looking for the the device side code, see [here](https://github.com/meshtastic/Meshtastic-esp32).

This project is currently early-alpha, if you have questions or feedback please [Join our discussion forum](https://meshtastic.discourse.group/).  We would love to hear from you.

The production version of our application is here:

[![Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dgithub-android-readme)

But if you want the beta-test app now, we'd love to have your help testing.  Three steps to opt-in to the test:

1. Join [this Google group](https://groups.google.com/forum/#!forum/meshtastic-alpha-testers) with the account you use in Google Play.  **Optional** - if you just want 'beta builds' 
not bleeding edge alpha test builds skip to the next step.
2. Go to this [URL](https://play.google.com/apps/testing/com.geeksville.mesh) to opt-in to the alpha/beta test.
3. If you encounter any problems or have questions, [post in the forum](https://meshtastic.discourse.group/)t and we'll help.

## Build instructions

If you would like to develop this application we'd love your help!  These build instructions are brief 
and should be improved, please send a PR if you can.

* Use Android Studio 4.0 RC 1 to build/debug (other versions might work but no promises)
* Use "git submodule update --init --recursive" to pull in the various submodules we depend on
* There are a few config files which you'll need to copy from templates included in the project.
Run the following commands to do so:

```
        rm ./app/google-services.json
        cp ./app/google-services-example.json ./app/google-services.json
        rm ./app/src/main/res/values/mapbox-token.xml
        cp ./app/special/mapbox-token.xml ./app/src/main/res/values/
        rm ./app/src/main/res/values/curfirmwareversion.xml
        cp ./app/special/curfirmwareversion.xml ./app/src/main/res/values/
```

* Now you should be able to select "Run / Run" in the IDE and it will happily start running on your phone
or the emulator.  Note: The emulators don't support bluetooth, so some features can not be used in
that environment.

## Analytics setup

* analytics are included but can be disabled by the user on the settings screen
* on dev devices "adb shell setprop debug.firebase.analytics.app com.geeksville.mesh"
adb shell setprop log.tag.FirebaseCrashlytics DEBUG

for verbose logging:
```aidl
adb shell setprop log.tag.FA VERBOSE
```

# Credits

This project is the work of volunteers:

* @artemisoftnian: Contributed Spanish translations.
* @CycloMies: Contributed Swedish, Finnish and German translations.
* @eriktheV-king: Contributed Dutch and French translations.
* @Lgoix: Contributed tooltip for radio mode
* @Eugene: Contributed Russian translation.
* @Jinx17: Contributed Slovenian translation.
* @Konradrundfunk: Contributed German fixes.
* @Mango-sauce: Contributed Chinese translation.
* @NEKLAN: Contributed Japanese translation.
* @Ohcdh: Contributed Irish and Italian translations.
* @Slavino: Contributed Slovak translation.
* @Zone: Contributed Turkish translation.

Copyright 2019, Geeksville Industries, LLC. GPL V3 license


