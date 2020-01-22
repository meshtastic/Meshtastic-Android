
* assert() is apparently a noop - change to use my version of assert
* make frontend using https://developer.android.com/jetpack/compose/tutorial

# Medium priority

* remove secret google settings json before open sourcing
* require user auth to pair with the device (i.e. press button on device to allow a new phone to pair with it).
Don't leave device discoverable.  Don't let unpaired users do thing with device
* add crash reporting
* remove example code boilerplate from the service
* add analytics (make them optional)

# Low priority

* possibly use finotes for analytics https://finotes.com/
 * also add a receiver that fires after a new update was installed from the play stoe