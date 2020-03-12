# High priority
Work items for soon alpha builds

* let channel be editited
* make link sharing work
* finish map view
* run services in sim mode on emulator
* track radio brands/regions as a user property (include no-radio as an option)
* show offline nodes as greyed out
* show time since last contact on the node info card
* show pointer arrow on the outside of the user icons, always pointing towoards them
* fix app icon in title bar
* show direction on the nodeinfo cards
* take video of the app
* register app link for our URLs https://developer.android.com/studio/write/app-link-indexing.html
* let users change & share channels (but no saving them yet)
* treat macaddrs as the unique id, not the app layer user id
* only publish gps positions once every 5 mins while we are connected to our radio _and_ someone else is in the mesh

# Medium priority
Features for future builds

* fix notification setSmallIcon parameter - change it to use the meshtastic icon
* ditch compose and use https://github.com/zsmb13/MaterialDrawerKt + https://github.com/Kotlin/anko/wiki/Anko-Layouts?
* describe user experience: devices always point to each other and show distance, you can send texts between nodes
the channel is encrypted, you can share the the channel key with others by qr code or by sharing a special link
* be smarter about sharing GPS location with the device (to save power), integrate with new network scheduler 
* stop scan when we start the service - I think this is done, but check
* set the radio macaddr by using the service (not by slamming bytes into the Preferences)
* show bt scan progress centered and towards the bottom of the screen
* when a text arrives, move that node info card to the bottom on the window - put the text to the left of the card.  with a small arrow/distance/shortname
* let the user type texts somewhere
* use this for preferences? https://developer.android.com/guide/topics/ui/settings/
* at connect we might receive messages before finished downloading the nodeinfo.  In that case, process those messages later
* test with oldest compatible android in emulator (see below for testing with hardware)
* add play store link with https://developers.google.com/analytics/devguides/collection/android/v4/campaigns#google-play-url-builder and the play icon

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
  
# Medium priority
Things for the betaish period.

* Use setLargeIcon to show user icons in the notification: file:///home/kevinh/packages/android-sdk-linux/docs/design/patterns/notifications.html
* Our notification about messages should use VISIBLITY_PRIVATE + setPublicVersion per file:///home/kevinh/packages/android-sdk-linux/docs/guide/topics/ui/notifiers/notifications.html
* let users save old channels
* make sw update work while node is connected to mesh - the problem is there are _two_ instances of SafeBluetooth at that moment and therefore we violate undocumented android mutex 
rules at the BluetoothDevice level.  Either make SafeBluetooth lock at the device level or (not as good) make SafeBluetooth a singleton.
* Use LocationRequest.setSmallestDisplacement to save battery and decrease net activity
* MeshService.reinitFromRadio can take 300 ms, run it in a worker thread instead
* show user icons in chat
* keep past messages in db, one db per channel
* spend some quality power consumption tuning with https://developer.android.com/studio/profile/energy-profiler and https://developer.android.com/topic/performance/power/battery-historian
* Do PRIORITY_BALANCED_POWER_ACCURACY for our gps updates when no one in the mesh is nearer than 200 meters
* fix slow rendering warnings in play console
* use google signin to get user name 
* use Firebase Test Lab
* let user pick/specify a name through ways other than google signin (for the privacy concerned, or devices without Play API)
* make my android app show mesh state
* show qr code for each channel https://medium.com/@aanandshekharroy/generate-barcode-in-android-app-using-zxing-64c076a5d83a
* let user change radio params and share radio join info via QR code or text message (use an encoded app specific URL - to autoprompt for app installation as needed)
* test with an oldish android release using real hardware
* stop using a foreground service
* use platform theme (dark or light)
* remove mixpanel analytics
* require user auth to pair with the device (i.e. press button on device to allow a new phone to pair with it).
Don't leave device discoverable.  Don't let unpaired users do things with device
* if the rxpacket queue on the device overflows (because android hasn't connected in a while) send a special packet to android which means 'X packets have been dropped because you were offline' -drop oldest packets first

# Low priority

** make analytics optional
* also add a receiver that fires after a new update was installed from the play store

# Done

* DONE fix bluetooth update
* DONE refactor sw update code to share with my other bluetooth service
* DONE don't let sw update got to sleep during the update
* assert() is apparently a noop - change to use my version of assert
* DONE add crash reporting
* DONE add analytics (make them optional)
* make frontend using https://developer.android.com/jetpack/compose/tutorial
* change bluetooth mtu length to 512 (default is only 20)
* DONE get signal running under debugger
* Find good Signal hooks
* receive fake packets at power on to built initial state (for debugging, pretend there are a couple of nodes out there)
* learn our node number
* test mesh service from activity
* DONE handle failures in onCharWrite, instead of logAssert - because they can happen if device goes away
* DONE explictly broadcast towards signal https://developer.android.com/guide/components/broadcasts
* make test implementation of android service (doesn't use bluetooth)
* undo base64
* use android service from Signal
* send signal message type over wire
* DONE add broadcasters for use by signal (node changes and packet received)
* DONE have signal declare receivers: https://developer.android.com/guide/components/broadcasts#manifest-declared-receivers
* fix // FIXME hack for now -  throw IdNotFoundException(id) in MeshService
* clean up sw update code in device side
* add real messaging code/protobufs
* implement android side of mesh radio bluetooth link
* use the lora net code on my current protoboard
* investigate a 16 bit node number.  If possible it would make collisions super rare.  Much easier to just pick a nodenum and go.
* remove example code boilerplate from the service
* switch from protobuf-java to protobuf-javalite - much faster and smaller, just no JSON debug printing
* have phone use our local node number as its node number (instead of hardwired)
* if radio disconnects, we need to requeue a new connect attempt in RadioService
* don't do mesh based algoritm for node id assignment (initially) - instead just store in flash - possibly even in the initial alpha release do this hack
* show connection state on gui
* parcels are busted - something wrong with the Parcelize kotlin magic
* add app icon
* when notified phone should automatically download messages
* use https://codelabs.developers.google.com/codelabs/jetpack-compose-basics/#4 to show service state
* all chat in the app defaults to group chat
* start bt receive on boot
* warn user to bt pair
* suppress logging output if running a release build (required for play store)
* provide gps location for devices that don't have it
* prompt user to turnon bluetooth and bind
* show real ID of me when I sent texts
* keep text entry box at bottom of screen
* when I enter texts, send them to the device
* let user set name and shortname
* let user send texts
* get rid of green bar at top
* change titlebar based off which screen we are showing
* on onStop somehow stop the BT scan (to prevent burning battery)
* "new bluetooth connection state 0, status 22" loss of connection
* fix BT device scanning - make a setup screen
* tell Compose geeks 
* call crashlytics from exceptionReporter!!!  currently not logging failures caught there
* do setOwner every time we connect to the radio, use our settings, radio should ignore if unchanged
* send location data for devices that don't have a GPS - https://developer.android.com/training/location/change-location-settings
* if necessary restart entire BT adapter with this tip from Michael https://stackoverflow.com/questions/35103701/ble-android-onconnectionstatechange-not-being-called
* don't show test texts when not under emulator
* make node list view not look like ass
* test bt boot behavior
* include tent on cloud graphics, so redraws work properly
* record analytics events when radio connects/disconnects, include # of nodes in mesh
* make a boot screen explaining this is an early alpha, tell user to go to settings if they have a radio, otherwise go to website
* when we connect to radio, distances to nodes in the chat log should automatically redraw
* add screenshots and text to play store entry
* if no radio is selected, launch app on the radio select screen
* connect to bluetooth device automatically using minimum power - start looking at phone boot
* tell various vendors & post in forums
* change info() log strings to debug()
* have the foreground service's notification show a summary of network status "connected/disconnected, 5 of 6 nodes, nearest: kevin 5km", 
* have notification (individually maskable) notifications for received texts - use file:///home/kevinh/packages/android-sdk-linux/docs/reference/android/support/v4/app/NotificationCompat.BigTextStyle.html
* startforegroundservice only if we have a valid radio
* when we select a new radio, restart the service
* make channel button look like a button
* generate real channel QR codes
* Have play store entry ask users to report if their android version is too old to allow install
* use git submodule for androidlib
* update play store listing for public beta

Rare bug reproduced:

D/com.geeksville.mesh.service.SafeBluetooth: work readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5 is completed, resuming status=0, res=android.bluetooth.BluetoothGattCharacteristic@f6eb84e
D/com.geeksville.mesh.service.RadioInterfaceService: Received 9 bytes from radio
D/com.geeksville.mesh.service.SafeBluetooth: Enqueuing work: readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5
D/com.geeksville.mesh.service.SafeBluetooth$BluetoothContinuation: Starting work: readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5
D/com.geeksville.mesh.service.MeshService: Received broadcast com.geeksville.mesh.RECEIVE_FROMRADIO
E/com.geeksville.util.Exceptions: exceptionReporter Uncaught Exception
    com.google.protobuf.InvalidProtocolBufferException: Protocol message contained an invalid tag (zero).
        at com.google.protobuf.GeneratedMessageLite.parsePartialFrom(GeneratedMessageLite.java:1566)
        at com.google.protobuf.GeneratedMessageLite.parseFrom(GeneratedMessageLite.java:1655)
        at com.geeksville.mesh.MeshProtos$FromRadio.parseFrom(MeshProtos.java:9097)
        at com.geeksville.mesh.service.MeshService$radioInterfaceReceiver$1$onReceive$1.invoke(MeshService.kt:742)
        at com.geeksville.mesh.service.MeshService$radioInterfaceReceiver$1$onReceive$1.invoke(Unknown Source:0)
        at com.geeksville.util.ExceptionsKt.exceptionReporter(Exceptions.kt:31)
        at com.geeksville.mesh.service.MeshService$radioInterfaceReceiver$1.onReceive(MeshService.kt:722)
        at android.app.LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$ReceiverDispatcher$Args(LoadedApk.java:1550)
        at android.app.-$$Lambda$LoadedApk$ReceiverDispatcher$Args$_BumDX2UKsnxLVrE6UJsJZkotuA.run(Unknown Source:2)
        at android.os.Handler.handleCallback(Handler.java:883)
        at android.os.Handler.dispatchMessage(Handler.java:100)
        at android.os.Looper.loop(Looper.java:214)
        at android.app.ActivityThread.main(ActivityThread.java:7356)
        at java.lang.reflect.Method.invoke(Native Method)
        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:492)
        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:930)
D/com.geeksville.mesh.service.SafeBluetooth: work readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5 is completed, resuming status=0, res=android.bluetooth.BluetoothGattCharacteristic@f6eb84e
D/com.geeksville.mesh.service.RadioInterfaceService: Received 9 bytes from radio
D/com.geeksville.mesh.service.SafeBluetooth: Enqueuing work: readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5
D/com.geeksville.mesh.service.SafeBluetooth$BluetoothContinuation: Starting work: readC 8ba2bcc2-ee02-4a55-a531-c525c5e454d5
D/com.geeksville.mesh.service.MeshService: Received broadcast com.geeksville.mesh.RECEIVE_FROMRADIO


Transition powerFSM transition=Press, from=DARK to=ON
pressing
sending owner !246f28b5367c/Bob use/Bu
Update DB node 0x7c for variant 4, rx_time=0
old user !246f28b5367c/Bob use/Bu
updating changed=0 user !246f28b5367c/Bob use/Bu
immedate send on mesh (txGood=32,rxGood=0,rxBad=0)
Trigger powerFSM 1
Transition powerFSM transition=Press, from=ON to=ON
Setting fast framerate
Setting idle framerate
Transition powerFSM transition=Screen-on timeout, from=ON to=DARK

NOTE: no debug messages on device, though we see in radio interface service we are repeatedly reading FromRadio and getting
the same seven bytes.  It sure seems like the old service is still sort of alive...

