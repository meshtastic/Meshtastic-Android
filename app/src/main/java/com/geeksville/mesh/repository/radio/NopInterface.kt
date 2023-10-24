package com.geeksville.mesh.repository.radio

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

class NopInterface @AssistedInject constructor(@Assisted val address: String) : IRadioInterface {
    override fun handleSendToRadio(p: ByteArray) {
    }

    override fun close() {
    }

}