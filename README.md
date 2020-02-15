# Meshtastic-Android

This is a tool for using Android with mesh radios.  

This project is currently pre-alpha, but if you have questions please join our chat [![Join the chat at https://gitter.im/Meshtastic/community](https://badges.gitter.im/Meshtastic/community.svg)](https://gitter.im/Meshtastic/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge).

Soon our first alpha release of will be released here:
[![Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dappgit%26anid%3Dadmob)

## Analytics setup

Once this project is public, I'll happily let collaborators have access to the crash logs/analytics.

* analytics is currently on, before beta is over I'll make it optional
* on dev devices "adb shell setprop debug.firebase.analytics.app com.geeksville.mesh"
* To see analytics: https://console.firebase.google.com/u/0/project/meshutil/analytics/app/android:com.geeksville.mesh/overview
* To see crash logs: https://console.firebase.google.com/u/0/project/meshutil/crashlytics/app/android:com.geeksville.mesh/issues?state=open&time=last-seven-days&type=crash

for verbose logging:
```aidl
adb shell setprop log.tag.FA VERBOSE
```



