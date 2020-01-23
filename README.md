# MeshUtil Android

This is a tool for using Android with mesh radios.  You probably don't want it yet ;-).

Questions? kevinh@geeksville.com

## Analytics setup

on dev devices
adb shell setprop debug.firebase.analytics.app com.geeeksville.mesh

then go here: https://console.firebase.google.com/u/0/project/meshutil/analytics/app/android:com.geeeksville.mesh/debugview~2F%3Ft=1579727535152&fpn=484268767777&swu=1&sgu=1&sus=upgraded&cs=app.m.debugview.overview&g=1

for verbose logging
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE

To see crash logs:
https://console.firebase.google.com/u/0/project/
meshutil/crashlytics/app/android:com.geeeksville.mesh/issues?state=open&time=last-seven-days&type=crash
