// com.geeksville.mesh.IMeshService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements
parcelable DataPacket;
parcelable NodeInfo;
parcelable MyNodeInfo;

/**
* Note - these calls might throw RemoteException to indicate mesh error states
*/
interface IMeshService {
    /// Tell the service where to send its broadcasts of received packets
    /// This call is only required for manifest declared receivers.  If your receiver is context-registered
    /// you don't need this.
    void subscribeReceiver(String packageName, String receiverName);

    /**
    * Set the ID info for this node

    If myId is null, then the existing unique node ID is preserved, only the human visible longName/shortName is changed
    */
    void setOwner(String myId, String longName, String shortName);

    /// Return my unique user ID string
    String getMyId();

    /*
    Send an opaque packet to a specified node name

    typ is defined in mesh.proto Data.Type.  For now juse use 0 to mean opaque bytes.

    destId can be null to indicate "broadcast message"

    Returns true if the packet has been sent into the mesh, or false if it was merely queued
    inside the service - and will be delivered to mesh the next time we hear from our radio.
    */
    boolean sendData(String destId, in byte[] payload, int typ);

    /**
    Get the IDs of everyone on the mesh.  You should also subscribe for NODE_CHANGE broadcasts.
    */
    List<NodeInfo> getNodes();

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It returns a RadioConfig protobuf.
    byte []getRadioConfig();

    /// Return an list of MeshPacket protobuf (byte arrays) which were received while your client app was offline (recent messages only)
    List<DataPacket> getOldMessages();

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It sets a RadioConfig protobuf
    void setRadioConfig(in byte []payload);

    /**
    Is the packet radio currently connected to the phone?  Returns a ConnectionState string.
    */
    String connectionState();

    /// If a macaddress we will try to talk to our device, if null we will be idle.
    /// Any current connection will be dropped (even if the device address is the same) before reconnecting.
    /// Users should not call this directly, only used internally by the MeshUtil activity
    void setDeviceAddress(String deviceAddr);

    /// Get basic device hardware info about our connected radio
    MyNodeInfo getMyNodeInfo();

    /// Start updating the radios firmware
    void startFirmwareUpdate();

    /**
    Return a number 0-100 for progress. -1 for completed and success, -2 for failure
    */
    int getUpdateStatus();

    // see com.geeksville.com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes.  payload will contain a DataPacket
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio


}
