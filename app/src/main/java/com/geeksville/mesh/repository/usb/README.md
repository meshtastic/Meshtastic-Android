# USB Module

This module provides a repository for acessing USB devices.

## Device Support

In order to be picked up, devices need to be supported by two different mechanisms:
- Android needs to be supplied with a device filter so that it knows what devices to inform
  the app about.  These are expressed as vendor and device IDs in `src/res/xml/device_filter.xml`.
- The USB driver library also needs to have a mapping between the vendor + device IDs and the
  driver to use for communications.  Many mappings are already natively supported by the driver
  but unknown devices can have manual mappings added via `ProbeTableProvider`.
  
The [Serial USB Terminal](https://play.google.com/store/apps/details?id=de.kai_morich.serial_usb_terminal)
app in the Google Play Store seems to be a good app for determining both the vendor and
device IDs as well as testing different underlying drivers.


## Testing

When granting permissions to a USB device, the Android platform remembers the user's decision.
In order to test the permission granting logic, re-install the app.  This will cause Android
to forget previously granted permissions and will re-trigger the permission acquisition logic.