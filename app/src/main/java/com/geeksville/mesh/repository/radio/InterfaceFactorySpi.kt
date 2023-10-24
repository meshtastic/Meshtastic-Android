package com.geeksville.mesh.repository.radio

/**
 * Radio interface factory service provider interface.  Each radio backend implementation needs
 * to have a factory to create new instances.  These instances are specific to a particular
 * address.  This interface defines a common API across all radio interfaces for obtaining
 * implementation instances.
 *
 * This is primarily used in conjunction with Dagger assisted injection for each backend
 * interface type.
 */
interface InterfaceFactorySpi<T: IRadioInterface> {
    fun create(rest: String): T
}