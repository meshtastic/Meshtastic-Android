# Meshtastic-Android

This is a tool for using Android with mesh radios.  You probably don't want it yet ;-).

Questions? kevinh@geeksville.com

## Analytics setup

Once this project is public, I'll happily let collaborators have access to the crash logs/analytics.

* analytics is currently on, before beta is over I'll make it optional
* on dev devices "adb shell setprop debug.firebase.analytics.app com.geeksville.mesh"
* To see analytics: https://console.firebase.google.com/u/0/project/meshutil/analytics/app/android:com.geeksville.mesh/overview
* To see crash logs: https://console.firebase.google.com/u/0/project/meshutil/crashlytics/app/android:com.geeksville.mesh/issues?state=open&time=last-seven-days&type=crash

for verbose logging
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE


