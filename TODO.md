# Remaining tasks before declaring 1.0

- disable software update button after update finishes
- first message sent is still doubled for some people
- Android frontend should refetch the android messages from backend service on Resume
- let users set arbitrary params in android
* add a low level settings screen (let user change any of the RadioConfig parameters)

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

