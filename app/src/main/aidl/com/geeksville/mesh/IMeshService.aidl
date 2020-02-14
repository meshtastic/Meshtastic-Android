// com.geeksville.mesh.IMeshService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

parcelable NodeInfo;

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

    /*
    Send an opaque packet to a specified node name

    typ is defined in mesh.proto Data.Type.  For now juse use 0 to mean opaque bytes.
    */
    void sendData(String destId, in byte[] payload, int typ);

    /**
    Get the IDs of everyone on the mesh.  You should also subscribe for NODE_CHANGE broadcasts.
    */
    NodeInfo[] getNodes();

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It returns a RadioConfig protobuf.
    byte []getRadioConfig();

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It sets a RadioConfig protobuf
    void setRadioConfig(in byte []payload);

    /**
    Is the packet radio currently connected to the phone?
    */
    boolean isConnected();

    // see com.geeksville.com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
}
