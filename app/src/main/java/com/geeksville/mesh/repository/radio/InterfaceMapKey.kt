package com.geeksville.mesh.repository.radio

import dagger.MapKey

/**
 * Dagger `MapKey` implementation allowing for `InterfaceId` to be used as a map key.
 */
@MapKey
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class InterfaceMapKey(val value: InterfaceId)
