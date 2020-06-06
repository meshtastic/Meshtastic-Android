// IRadioInterfaceService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

interface IRadioInterfaceService {

    void sendToRadio(in byte [] a);

    /// If a macaddress we will try to talk to our device, if null we will be idle.
    /// Any current connection will be dropped (even if the device address is the same) before reconnecting.
    /// Users should not call this directly, called only by MeshService
    void setDeviceAddress(String deviceAddr);
}
