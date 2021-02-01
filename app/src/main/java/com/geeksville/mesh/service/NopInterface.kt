package com.geeksville.mesh.service

class NopInterface : IRadioInterface {
    override fun handleSendToRadio(p: ByteArray) {
    }

    override fun close() {
    }

}