# High priority

* fix startup race conditions in services, allow reads to block as needed
* if radio disconnects, we need to requeue a new connect attempt in RadioService
* when notified phone should download messages
* have phone use our local node number as its node number (instead of hardwired)
* investigate the Signal SMS message flow path, see if I could just make Mesh a third peer to signal & sms?
* make signal work when there is no internet up
* make Signal rx path work
* send Signal message type.  It seems to be? "  public static final int WHISPER_TYPE                = 2;
  public static final int PREKEY_TYPE                 = 3;
  public static final int SENDERKEY_TYPE              = 4;
  public static final int SENDERKEY_DISTRIBUTION_TYPE = 5;"
* don't do mesh based algoritm for node id assignment (initially) - instead just store in flash - possibly even in the initial alpha release do this hack
* add large packet reassembly?
* optionally turn off crypto in signal - preferably though see if there is a nice way to be a peer of signal/sms and now mesh.
* change signal package ID - if distributing modified binary
* good tips on which bands might be more free https://github.com/TheThingsNetwork/ttn/issues/119
* make my android app show mesh state
* use https://codelabs.developers.google.com/codelabs/jetpack-compose-basics/#4 to show service state
* connect to bluetooth device automatically using minimum power, start looking at phone boot
* fix BT device scanning
* call crashlytics from exceptionReporter!!!  currently not logging failures caught there

# Medium priority

* test with oldest android
* stop using a foreground service
* change info() log strings to debug()
* use platform theme (dark or light)
* remove mixpanel analytics
* require user auth to pair with the device (i.e. press button on device to allow a new phone to pair with it).
Don't leave device discoverable.  Don't let unpaired users do things with device
* remove example code boilerplate from the service
* switch from protobuf-java to protobuf-javalite - much faster and smaller, just no JSON debug printing
* if the rxpacket queue on the device overflows (because android hasn't connected in a while) send a special packet to android which means 'X packets have been dropped because you were offline' -drop oldest packets first

# Low priority

* make analytics optional
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
