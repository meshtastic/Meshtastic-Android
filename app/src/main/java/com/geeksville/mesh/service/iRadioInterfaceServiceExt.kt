package com.geeksville.mesh.service

import com.geeksville.mesh.IRadioInterfaceService
import com.geeksville.mesh.MeshProtos

/// Send a command/packet to our radio.  But cope with the possiblity that we might start up
/// before we are fully bound to the RadioInterfaceService
fun IRadioInterfaceService.sendToRadio(builder: MeshProtos.ToRadio.Builder) {
    val bytes = builder.build().toByteArray()
    sendToRadio(bytes)
}

/**
 * Send a mesh packet to the radio, if the radio is not currently connected this function will throw NotConnectedException
 */
fun IRadioInterfaceService.sendToRadio(packet: MeshProtos.MeshPacket) {
    sendToRadio(MeshProtos.ToRadio.newBuilder().apply {
        this.packet = packet
    })
}
