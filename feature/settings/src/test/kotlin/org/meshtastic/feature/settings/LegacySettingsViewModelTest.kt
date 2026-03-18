/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings

import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.MeshLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetAppIntroCompletedUseCase
import org.meshtastic.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetProvideLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetThemeUseCase
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [34])
class LegacySettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val radioController: RadioController = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val buildConfigProvider: BuildConfigProvider = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val meshLogPrefs: MeshLogPrefs = mock(MockMode.autofill)

    private lateinit var setThemeUseCase: SetThemeUseCase
    private lateinit var setAppIntroCompletedUseCase: SetAppIntroCompletedUseCase
    private lateinit var setProvideLocationUseCase: SetProvideLocationUseCase
    private lateinit var setDatabaseCacheLimitUseCase: SetDatabaseCacheLimitUseCase
    private lateinit var setMeshLogSettingsUseCase: SetMeshLogSettingsUseCase
    private lateinit var meshLocationUseCase: MeshLocationUseCase
    private lateinit var exportDataUseCase: ExportDataUseCase
    private lateinit var isOtaCapableUseCase: IsOtaCapableUseCase

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        setThemeUseCase = mock(MockMode.autofill)
        setAppIntroCompletedUseCase = mock(MockMode.autofill)
        setProvideLocationUseCase = mock(MockMode.autofill)
        setDatabaseCacheLimitUseCase = mock(MockMode.autofill)
        setMeshLogSettingsUseCase = mock(MockMode.autofill)
        meshLocationUseCase = mock(MockMode.autofill)
        exportDataUseCase = mock(MockMode.autofill)
        isOtaCapableUseCase = mock(MockMode.autofill)

        // Return real StateFlows to avoid ClassCastException
        every { databaseManager.cacheLimit } returns MutableStateFlow(100)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(org.meshtastic.proto.LocalConfig())
        every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        every { isOtaCapableUseCase() } returns flowOf(false)

        viewModel =
            SettingsViewModel(
                app = mock(),
                radioConfigRepository = radioConfigRepository,
                radioController = radioController,
                nodeRepository = nodeRepository,
                uiPrefs = uiPrefs,
                buildConfigProvider = buildConfigProvider,
                databaseManager = databaseManager,
                meshLogPrefs = meshLogPrefs,
                setThemeUseCase = setThemeUseCase,
                setAppIntroCompletedUseCase = setAppIntroCompletedUseCase,
                setProvideLocationUseCase = setProvideLocationUseCase,
                setDatabaseCacheLimitUseCase = setDatabaseCacheLimitUseCase,
                setMeshLogSettingsUseCase = setMeshLogSettingsUseCase,
                meshLocationUseCase = meshLocationUseCase,
                exportDataUseCase = exportDataUseCase,
                isOtaCapableUseCase = isOtaCapableUseCase,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setTheme calls useCase`() {
        viewModel.setTheme(1)
        verify { setThemeUseCase(1) }
    }

    @Test
    fun `setDbCacheLimit calls useCase`() {
        viewModel.setDbCacheLimit(50)
        verify { setDatabaseCacheLimitUseCase(50) }
    }

    @Test
    fun `startProvidingLocation calls useCase`() {
        viewModel.startProvidingLocation()
        verify { meshLocationUseCase.startProvidingLocation() }
    }
}
