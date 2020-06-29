# Remaining tasks before declaring 1.0

- turning off Bluetooth on oneplus causes a harmless exception - devwork is null


06-29 08:47:10.007 29788-29812/com.geeksville.mesh D/BluetoothGatt: configureMTU() - device: 24:62:AB:F8:40:9A mtu: 512
06-29 08:47:10.007 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: Starting failsafe timer 5000
06-29 08:47:10.014 29788-29812/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: work reconnect is completed, resuming status=0, res=kotlin.Unit
06-29 08:47:10.016 29788-31466/com.geeksville.mesh I/com.geeksville.mesh.service.BluetoothInterface: Connected to radio!
06-29 08:47:10.016 29788-31466/com.geeksville.mesh D/BluetoothGatt: refresh() - device: 24:62:AB:F8:40:9A
06-29 08:47:10.220 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: Enqueuing work: reqMtu
06-29 08:47:15.009 29788-31466/com.geeksville.mesh E/com.geeksville.mesh.service.SafeBluetooth: Failsafe BLE timer expired! (exception none
06-29 08:47:15.011 29788-29859/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: Starting failsafe timer 5000
06-29 08:47:15.015 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth$BluetoothContinuation: Starting work: reqMtu
06-29 08:47:15.016 29788-31466/com.geeksville.mesh D/BluetoothGatt: configureMTU() - device: 24:62:AB:F8:40:9A mtu: 512
06-29 08:47:15.028 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: work reqMtu is completed, resuming status=4404, res=kotlin.Unit
06-29 08:47:15.029 29788-31466/com.geeksville.mesh W/com.geeksville.mesh.service.BluetoothInterface: Scheduling reconnect because Giving up on setting MTUs, forcing disconnect com.geeksville.mesh.service.SafeBluetooth$BLEStatusException: Bluetooth status=4404 while doing reqMtu
06-29 08:47:15.034 29788-30155/com.geeksville.mesh W/com.geeksville.mesh.service.BluetoothInterface: Forcing disconnect and hopefully device will comeback (disabling forced refresh)
06-29 08:47:15.034 29788-30155/com.geeksville.mesh I/com.geeksville.mesh.service.SafeBluetooth: Closing our GATT connection
06-29 08:47:15.035 29788-30155/com.geeksville.mesh D/BluetoothGatt: cancelOpen() - device: 24:62:AB:F8:40:9A
06-29 08:47:15.036 29788-30155/com.geeksville.mesh D/BluetoothGatt: close()
06-29 08:47:15.037 29788-30155/com.geeksville.mesh D/BluetoothGatt: unregisterApp() - mClientIf=5
06-29 08:47:15.037 29788-29813/com.geeksville.mesh D/BluetoothGatt: onClientConnectionState() - status=0 clientIf=5 device=24:62:AB:F8:40:9A
06-29 08:47:15.037 29788-29813/com.geeksville.mesh W/BluetoothGatt: Unhandled exception in callback
    java.lang.NullPointerException: Attempt to invoke virtual method 'void android.bluetooth.BluetoothGattCallback.onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)' on a null object reference
        at android.bluetooth.BluetoothGatt$1.onClientConnectionState(BluetoothGatt.java:182)
        at android.bluetooth.IBluetoothGattCallback$Stub.onTransact(IBluetoothGattCallback.java:70)
        at android.os.Binder.execTransact(Binder.java:446)
06-29 08:47:16.040 29788-31466/com.geeksville.mesh W/com.geeksville.mesh.service.BluetoothInterface: Attempting reconnect
06-29 08:47:16.041 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth: Enqueuing work: connect
06-29 08:47:16.041 29788-31466/com.geeksville.mesh D/com.geeksville.mesh.service.SafeBluetooth$BluetoothContinuation: Starting work: connect
06-29 08:47:16.043 29788-31466/com.geeksville.mesh D/BluetoothGatt: connect() - device: 24:62:AB:F8:40:9A, auto: false
06-29 08:47:16.043 29788-31466/com.geeksville.mesh D/BluetoothGatt: registerApp()

- soyes sx ble problems (again)
- Android frontend should refetch the android messages from backend service on Resume
- disable software update button after update finishes
- first message sent is still doubled for some people
- let users set arbitrary params in android
* add a low level settings screen (let user change any of the RadioConfig parameters)
- optionally include firmware files in debug builds - currently they are release only.  per https://developer.android.com/studio/build/build-variants

Things for the betaish period.

* let user change more channel parameters
* really great notes about importance of clean BLE disconnects: https://blog.classycode.com/a-short-story-about-android-ble-connection-timeouts-and-gatt-internal-errors-fa89e3f6a456

# Documentation tasks

Mostly for geeksville 

Document the following in application behavior
*change ls_secs is 1 hr normally, which is fine because if there are other nodes in the mesh and they send us a packet we will wake any time during ls_secs and update app state
* use states for meshservice: disconnected -> connected-> devsleep -> disconnected (3 states)
* when device enters LS state radiointerfaceservice publishes "Broadcasting connection=false", meshservice should then enter devicesleepstate for ls_secs + 30s (to allow for some margin)

* describe user experience: devices always point to each other and show distance, you can send texts between nodes
the channel is encrypted, you can share the the channel key with others by qr code or by sharing a special link

* take video of the app

# Post 1.0

* show pointer arrow on the outside of the user icons, always pointing towards them
* Use setLargeIcon to show user icons in the notification: file:///home/kevinh/packages/android-sdk-linux/docs/design/patterns/notifications.html
* Our notification about messages should use VISIBLITY_PRIVATE + setPublicVersion per file:///home/kevinh/packages/android-sdk-linux/docs/guide/topics/ui/notifiers/notifications.html
* Use LocationRequest.setSmallestDisplacement to save battery and decrease net activity
* use platform theme (dark or light)
* Do PRIORITY_BALANCED_POWER_ACCURACY for our gps updates when no one in the mesh is nearer than 200 meters
* spend some quality power consumption tuning with https://developer.android.com/studio/profile/energy-profiler and https://developer.android.com/topic/performance/power/battery-historian
* use google signin to get user name (make optional)
* keep past messages in db, one db per channel (currently we just keep an array in saved preferences)
* show user avatars in chat (use the google user info api)
* let users save old channels (i.e. have a menu of channels the user can use)
* also add a receiver that fires after a new update was installed from the play store
* if the rxpacket queue on the device overflows (because android hasn't connected in a while) send a special packet to android which means 'X packets have been dropped because you were offline' -drop oldest packets first
* make sw update work over BLE

# Signal alpha release
Do this "Signal app compatible" release relatively soon after the alpha release of the android app.

* call onNodeDBChanged after we haven't heard a packet from the mesh in a while - because that's how we decide we have less than 2 nodes in the mesh and should stop listening to the local GPS
* add large packet reassembly?
* optionally turn off crypto in signal - preferably though see if there is a nice way to be a peer of signal/sms and now mesh.
* change signal package ID - if distributing modified binary
* investigate the Signal SMS message flow path, see if I could just make Mesh a third peer to signal & sms?
* make signal work when there is no internet up
* make Signal rx path work
* send Signal message type.  It seems to be? "  public static final int WHISPER_TYPE                = 2;
  public static final int PREKEY_TYPE                 = 3;
  public static final int SENDERKEY_TYPE              = 4;
  public static final int SENDERKEY_DISTRIBUTION_TYPE = 5;"
  
# Done

