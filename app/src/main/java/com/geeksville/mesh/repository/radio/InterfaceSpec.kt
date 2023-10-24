package com.geeksville.mesh.repository.radio

/**
 * This interface defines the contract that all radio backend implementations must adhere to.
 */
interface InterfaceSpec<T : IRadioInterface> {
    fun createInterface(rest: String): T

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    fun addressValid(rest: String): Boolean = true
}