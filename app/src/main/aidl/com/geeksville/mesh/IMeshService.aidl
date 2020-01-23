// com.geeksville.com.geeeksville.mesh.IMeshService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

interface IMeshService {
    /**
    * Set the ID info for this node
    */
    void setOwner(String myId, String longName, String shortName);

    /*
    Send an opaque packet to a specified node name
    */
    void sendOpaque(String destId, in byte[] payload);

    /**
    Get the IDs of everyone on the mesh.  You should also subscribe for NODE_CHANGE broadcasts.
    */
    void getOnline(out String[] ids);

    /**
    Is the packet radio currently connected to the phone?
    */
    boolean isConnected();

    // see com.geeksville.com.geeeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
}
