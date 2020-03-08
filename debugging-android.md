# Found a problem with our android app?

Oops sorry about that ;-).  Android sends us automated crash reports for some types of failures but not all failures. 

It would be super useful if you could help us by capturing a "logcat" file of the app while it was doing the bad thing and you attach that file to a github [issue](https://github.com/meshtastic/Meshtastic-Android/issues).

Here's how to do that...

Setup your phone & PC to allow debugging:

* Install "adb" (android debug bridge).  [Here's](https://lifehacker.com/the-easiest-way-to-install-androids-adb-and-fastboot-to-1586992378)
a tutorial I found on the web.  Please let me know if it works for you.
* [Enable](https://www.howtogeek.com/129728/how-to-access-the-developer-options-menu-and-enable-usb-debugging-on-android-4.2/) 
developer mode on your phone.  The procedure might be slightly different for some phones, if necessary google for "enable developer mode YOURPHONENAME"
* Connect your phone to your PC USB port.  A dialog on the phone will say "do you want to allow this PC to access debug mode on your phone?"  
Say yes and also click the checkbox to always allow your PC access.
* type "adb devices" at your computer shell prompt, you should see your phone listed.  If you see that ADB is working fine.

* Long press on the meshtastic app and choose "force stop", to ensure that we are starting from scratch for this log (it will make it easier to understand it)
* If you have a Mac or Linux type:
```
adb shell 'logcat --pid=$(pidof -s com.geeksville.mesh)' | tee newlogfile.txt
```

* If you have a PC type:
```
adb shell "logcat --pid=$(pidof -s com.geeksville.mesh)" >newlogfile.txt (I don't know the equivalent of TEE for windows?)
```

This will capture a bunch of logging information as you use the app.  Please go through the app to the part that was giving you troubles (No device listed on the settings screen etc).  And then press
ctrl-c in the adb window to stop logging.  Please open a github [issue](https://github.com/meshtastic/Meshtastic-Android/issues) describing the problem and attach the log file.  We'll get back to you with what we find (possibly with some extra questions).

```
kevinh@kevin-server:~/development$ adb shell 'logcat --pid=$(pidof -s com.geeksville.mesh)' | 
--------- beginning of main
03-07 17:10:05.669 13452 13452 W ActivityThread: handleWindowVisibility: no activity for token android.os.BinderProxy@fbf5fa0
03-07 17:10:05.927 13452 13452 D com.geeksville.mesh.MainActivity: Checking permissions
03-07 17:10:06.033 13452 13452 W geeksville.mes: Accessing hidden method Landroid/view/View;->computeFitSystemWindows(Landroid/graphics/Rect;Landroid/graphics/Rect;)Z (greylist, reflection, allowed)
03-07 17:10:06.034 13452 13452 W geeksville.mes: Accessing hidden method Landroid/view/ViewGroup;->makeOptionalFitsSystemWindows()V (greylist, reflection, allowed)
03-07 17:10:06.179 13452 13452 D com.geeksville.mesh.MainActivity: Binding to mesh service!
03-07 17:10:06.267 13452 13484 I geeksville.mes: Background concurrent copying GC freed 18374(1149KB) AllocSpace objects, 5(164KB) LOS objects, 49% free, 2384KB/4769KB, paused 329us total 124.902ms
03-07 17:10:06.267 13452 13484 W geeksville.mes: Reducing the number of considered missed Gc histogram windows from 1692 to 100
03-07 17:10:06.288 13452 24599 I FA      : Tag Manager is not found and thus will not be used
03-07 17:10:06.500 13452 24593 I Adreno  : QUALCOMM build                   : 4a00b69, I4e7e888065
03-07 17:10:06.500 13452 24593 I Adreno  : Build Date                       : 04/09/19
03-07 17:10:06.500 13452 24593 I Adreno  : OpenGL ES Shader Compiler Version: EV031.26.06.00
03-07 17:10:06.500 13452 24593 I Adreno  : Local Branch                     : mybranche95a5ea3-cf05-f19a-a0e7-5cb90179c3d8
03-07 17:10:06.500 13452 24593 I Adreno  : Remote Branch                    : quic/gfx-adreno.lnx.1.0
03-07 17:10:06.500 13452 24593 I Adreno  : Remote Branch                    : NONE
03-07 17:10:06.500 13452 24593 I Adreno  : Reconstruct Branch               : NOTHING
03-07 17:10:06.500 13452 24593 I Adreno  : Build Config                     : S P 8.0.6 AArch64
03-07 17:10:06.508 13452 24593 I Adreno  : PFP: 0x016ee183, ME: 0x00000000
03-07 17:10:06.546 13452 24593 W Gralloc3: mapper 3.x is not supported
03-07 17:10:06.548 13452 24593 E libc    : Access denied finding property "vendor.gralloc.disable_ahardware_buffer"
03-07 17:10:06.544 13452 13452 W RenderThread: type=1400 audit(0.0:7134): avc: denied { read } for name="u:object_r:vendor_default_prop:s0" dev="tmpfs" ino=24620 scontext=u:r:untrusted_app:s0:c157,c257,c512,c768 tcontext=u:object_r:vendor_default_prop:s0 tclass=file permissive=0
03-07 17:10:06.607 13452 13452 I com.geeksville.mesh.service.MeshService: in isConnected=false
03-07 17:10:06.608 13452 13452 D com.geeksville.mesh.MainActivity: connchange false
03-07 17:10:06.608 13452 13452 D com.geeksville.mesh.MainActivity$mesh$1: connected to mesh service, isConnected=false
03-07 17:10:06.609 13452 13452 D com.geeksville.mesh.ui.AnalyticsLog: logging screen view messages

```