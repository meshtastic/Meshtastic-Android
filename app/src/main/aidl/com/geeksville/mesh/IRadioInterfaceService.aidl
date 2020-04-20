// IRadioInterfaceService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

interface IRadioInterfaceService {

    void sendToRadio(in byte [] a);

    /// mynode - read/write this to access a MyNodeInfo protobuf
    byte []readMyNode();

    /// nodeinfo - read this to get a series of node infos (ending with a null empty record), write to this to restart the read statemachine that returns all the node infos
    byte []readNodeInfo();
    void restartNodeInfo();

    /// radio - read/write this to access a RadioConfig protobuf
    byte []readRadioConfig();
    void writeRadioConfig(in byte [] config);

    /// owner - read/write this to access a User protobuf
    byte []readOwner();
    void writeOwner(in byte [] owner);

    /// If a macaddress we will try to talk to our device, if null we will be idle.
    /// Users should not call this directly, called only by MeshService
    void setDeviceAddress(String deviceAddr);
}
