
# High priority

* watch for ntofies of numread changing
* implement android side of mesh radio bluetooth link
* investigate the Signal SMS message flow path, see if I could just make Mesh a third peer to signal & sms?
* make signal work when there is no internet up
* make Signal rx path work
* send Signal message type.  It seems to be? "  public static final int WHISPER_TYPE                = 2;
  public static final int PREKEY_TYPE                 = 3;
  public static final int SENDERKEY_TYPE              = 4;
  public static final int SENDERKEY_DISTRIBUTION_TYPE = 5;"
* don't do mesh based algoritm for node id assignment (initially) - instead just store in flash - possibly even in the initial alpha release do this hack
* use the lora net code on my current protoboard
* investigate a 16 bit node number.  If possible it would make collisions super rare.  Much easier to just pick a nodenum and go.
* add large packet reassembly?
* optionally turn off crypto in signal
* change signal package ID
* good tips on which bands might be more free https://github.com/TheThingsNetwork/ttn/issues/119
* make my android app show mesh state
* use https://codelabs.developers.google.com/codelabs/jetpack-compose-basics/#4 to show service state
* connect to bluetooth device automatically using minimum power
* fix BT device scanning 
* call crashlytics from exceptionReporter!!!  currently not logging failures caught there

protobuf notes
protoc -I=. --java_out /tmp mesh.proto

to generate nanopb c code
/home/kevinh/packages/nanopb-0.4.0-linux-x86/generator-bin/protoc --nanopb_out=/tmp -I=app/src/main/proto mesh.proto 
https://jpa.kapsi.fi/nanopb/docs/

nanopb binaries available here: https://jpa.kapsi.fi/nanopb/download/ use nanopb 0.4.0

# Medium priority

* change info() log strings to debug()
* use platform theme (dark or light)
* remove mixpanel analytics
* require user auth to pair with the device (i.e. press button on device to allow a new phone to pair with it).
Don't leave device discoverable.  Don't let unpaired users do things with device
* remove example code boilerplate from the service
* switch from protobuf-java to protobuf-javalite - much faster and smaller, just no JSON debug printing

# Low priority

* make analytics optional
* possibly use finotes for analytics https://finotes.com/
* also add a receiver that fires after a new update was installed from the play stoe

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
