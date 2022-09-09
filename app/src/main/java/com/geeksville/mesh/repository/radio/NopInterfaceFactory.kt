package com.geeksville.mesh.repository.radio

import dagger.assisted.AssistedFactory

/**
 * Factory for creating `NopInterface` instances.
 */
@AssistedFactory
interface NopInterfaceFactory : InterfaceFactorySpi<NopInterface>