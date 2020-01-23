
* test reg reading/writing directly via bt to device
* fix bluetooth update
* get signal running under debugger
* DONE add broadcasters for use by signal (node changes and packet received)
* make test implementation of server (doesn't use bluetooth)
* make a test client of the android service
* add real messaging code/protobufs
* use https://codelabs.developers.google.com/codelabs/jetpack-compose-basics/#4 to show service state
* connect to bluetooth device automatically using minimum power
* have signal declare receivers: https://developer.android.com/guide/components/broadcasts#manifest-declared-receivers

protobuf notes
protoc -I=. --java_out /tmp mesh.proto

to generate nanopb c code
/home/kevinh/packages/nanopb-0.4.0-linux-x86/generator-bin/protoc --nanopb_out=/tmp -I=app/src/main/proto mesh.proto 
https://jpa.kapsi.fi/nanopb/docs/

nanopb binaries available here: https://jpa.kapsi.fi/nanopb/download/ use nanopb 0.4.0
# Medium priority

* remove mixpanel analytics
* require user auth to pair with the device (i.e. press button on device to allow a new phone to pair with it).
Don't leave device discoverable.  Don't let unpaired users do thing with device
* remove example code boilerplate from the service


# Low priority

* make analytics optional
* possibly use finotes for analytics https://finotes.com/
 * also add a receiver that fires after a new update was installed from the play stoe

# Done

* assert() is apparently a noop - change to use my version of assert
* DONE add crash reporting
* DONE add analytics (make them optional)
* make frontend using https://developer.android.com/jetpack/compose/tutorial

