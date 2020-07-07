// IRadioInterfaceService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

interface IRadioInterfaceService {

    /** If the service is not currently connected to the radio, try to connect now.  At boot the radio interface service will
     * not connect to a radio until this call is received. */
    void connect();

    void sendToRadio(in byte [] a);

    /// If a macaddress we will try to talk to our device, if null we will be idle.
    /// Any current connection will be dropped (even if the device address is the same) before reconnecting.
    /// Users should not call this directly, called only by MeshService
    /// Returns true if the device address actually changed, or false if no change was needed
    boolean setDeviceAddress(String deviceAddr);
}
