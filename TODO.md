

* handle failures in onCharWrite, instead of logAssert - because they can happen if device goes away
* make test implementation of android service (doesn't use bluetooth)
* clean up sw update code in device side
* DONE add broadcasters for use by signal (node changes and packet received)
* make compose based access show mesh state
* use android service from Signal
* add real messaging code/protobufs
* use https://codelabs.developers.google.com/codelabs/jetpack-compose-basics/#4 to show service state
* connect to bluetooth device automatically using minimum power
* have signal declare receivers: https://developer.android.com/guide/components/broadcasts#manifest-declared-receivers
* fix BT device scanning 

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
Don't leave device discoverable.  Don't let unpaired users do thing with device
* remove example code boilerplate from the service


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

