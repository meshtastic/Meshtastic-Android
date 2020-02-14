# High priority
MVP features required for first public alpha 

* if no radio is selected, launch app on the radio select screen
* when we select a new radio, restart the service
* show bt scan progress centered and towards the bottom of the screen
* get rid of green bar at top
* change titlebar based off which screen we are showing
* fix app icon in title bar
* add alphatest screen at boot
* prompt user to turnon bluetooth and bind
* test bt boot behavior
* fix BT device scanning - make a setup screen
* when a text arrives, move that node info card to the bottom on the window - put the text to the left of the card.  with a small arrow/distance/shortname
* let the user type texts somewhere
* include a background behind our cloud graphics, so redraws work properly
* show direction and distance on the nodeinfo cards
* show radio config screen, it shows past channels (and the current one)
* use this for preferences? https://developer.android.com/guide/topics/ui/settings/
* do setOwner every time we connect to the radio, use our settings, radio should ignore if unchanged
* send location data for devices that don't have a GPS - https://developer.android.com/training/location/change-location-settings
* make nodeinfo card not look like ass
* at connect we might receive messages before finished downloading the nodeinfo.  In that case, process those messages later
* connect to bluetooth device automatically using minimum power - start looking at phone boot
* call crashlytics from exceptionReporter!!!  currently not logging failures caught there
* test with oldest compatible android in emulator (see below for testing with hardware)
* make playstore entry, first public alpha
* tell Compose geeks 
* tell various vendors & post in forum

# Signal alpha release
Do this "Signal app compatible" release relatively soon after the alpha release of the android app.

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

* make my android app show mesh state
* show qr code for each channel https://medium.com/@aanandshekharroy/generate-barcode-in-android-app-using-zxing-64c076a5d83a
* register app link for our URLs https://developer.android.com/studio/write/app-link-indexing.html
* let user change radio params and share radio join info via QR code or text message (use an encoded app specific URL - to autoprompt for app installation as needed)
* test with an oldish android release using real hardware
* if necessary restart entire BT adapter with this tip from Michael https://stackoverflow.com/questions/35103701/ble-android-onconnectionstatechange-not-being-called
* stop using a foreground service
* change info() log strings to debug()
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