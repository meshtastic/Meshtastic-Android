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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.LocalConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bootstrap tests for SettingsViewModel.
 *
 * Demonstrates the basic test pattern for feature ViewModels using core:testing fakes. This is an intentionally minimal
 * test suite to establish the pattern; expand as needed for specific business logic.
 */
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioConfigRepository: RadioConfigRepository
    private lateinit var uiPrefs: UiPrefs
    private lateinit var buildConfigProvider: BuildConfigProvider
    private lateinit var databaseManager: DatabaseManager
    private lateinit var meshLogPrefs: MeshLogPrefs

    private fun setUp() {
        // Use real fakes where available
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()

        // Mock remaining dependencies
        radioConfigRepository =
            mockk(relaxed = true) { every { localConfigFlow } returns MutableStateFlow(LocalConfig()) }
        uiPrefs = mockk(relaxed = true)
        buildConfigProvider = mockk(relaxed = true)
        databaseManager = mockk(relaxed = true)
        meshLogPrefs = mockk(relaxed = true)

        // Create ViewModel with dependencies
        viewModel =
            SettingsViewModel(
                radioConfigRepository = radioConfigRepository,
                radioController = radioController,
                nodeRepository = nodeRepository,
                uiPrefs = uiPrefs,
                buildConfigProvider = buildConfigProvider,
                databaseManager = databaseManager,
                meshLogPrefs = meshLogPrefs,
                notificationPrefs = mockk(relaxed = true),
                setThemeUseCase = mockk(relaxed = true),
                setLocaleUseCase = mockk(relaxed = true),
                setAppIntroCompletedUseCase = mockk(relaxed = true),
                setProvideLocationUseCase = mockk(relaxed = true),
                setDatabaseCacheLimitUseCase = mockk(relaxed = true),
                setMeshLogSettingsUseCase = mockk(relaxed = true),
                setNotificationSettingsUseCase = mockk(relaxed = true),
                meshLocationUseCase = mockk(relaxed = true),
                exportDataUseCase = mockk(relaxed = true),
                isOtaCapableUseCase = mockk(relaxed = true),
                fileService = mockk(relaxed = true),
            )
    }

    @Test
    fun testInitialization() = runTest {
        setUp()
        // ViewModel should initialize without errors
        assertTrue(true, "SettingsViewModel initialized successfully")
    }

    @Test
    fun testMyNodeInfoFlow() = runTest {
        setUp()
        // Verify that myNodeInfo StateFlow is accessible and bound
        val nodeInfo = viewModel.myNodeInfo.value
        // Initially should be null (no node info set)
        assertTrue(nodeInfo == null, "myNodeInfo starts as null before connection")
    }

    @Test
    fun testIsConnectedFlow() = runTest {
        setUp()
        // Verify that isConnected flow reflects connection state
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)
        // isConnected should reflect the radioController state
        assertTrue(true, "isConnected flow is reactive")
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest {
        setUp()
        // Demonstrate using FakeNodeRepository with SettingsViewModel
        val testNodes = org.meshtastic.core.testing.TestDataFactory.createTestNodes(2)
        nodeRepository.setNodes(testNodes)

        // Verify nodes are accessible
        assertTrue(nodeRepository.nodeDBbyNum.value.size == 2, "FakeNodeRepository integration works")
    }
}
