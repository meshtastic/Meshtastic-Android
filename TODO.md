

* make frontend using https://developer.android.com/jetpack/compose/tutorial
*fix bluetooth

# Medium priority

* remove secret google settings json before open sourcing
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
