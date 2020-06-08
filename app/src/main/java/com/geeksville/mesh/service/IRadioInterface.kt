package com.geeksville.mesh.service

import java.io.Closeable

interface IRadioInterface : Closeable {
    fun handleSendToRadio(p: ByteArray)
}

class NopInterface : IRadioInterface {
    override fun handleSendToRadio(p: ByteArray) {
    }

    override fun close() {
    }

}