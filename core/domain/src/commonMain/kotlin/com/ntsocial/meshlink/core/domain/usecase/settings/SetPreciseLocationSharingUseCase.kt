/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.domain.usecase.settings

import com.ntsocial.meshlink.core.model.PreciseLocationChannelSetPlanner
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelWriteOrder
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.UiPrefs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

/** Owns the fail-closed transition between user consent, one exact-position channel, and the phone GPS feed gate. */
@Single
open class SetPreciseLocationSharingUseCase(
    private val radioConfigRepository: RadioConfigRepository,
    private val channelReliabilityManager: ChannelReliabilityManager,
    private val uiPrefs: UiPrefs,
    private val nodeRepository: NodeRepository,
    private val channelMutationLock: ChannelMutationLock,
) {
    /** Pauses any current feed, applies one p32 channel, and admits the feed only after exact radio readback. */
    open suspend fun enable(
        nodeNum: Int,
        channelIndex: Int,
        expectedChannelIdentity: String,
    ): ChannelReliabilityResult = channelMutationLock.withLock mutation@{ mutationLease ->
        val sourceReadbackGeneration = radioConfigRepository.channelReadbackGeneration.value
        val previousAdmission = uiPrefs.readPreciseLocationAdmission(nodeNum)
        uiPrefs.setPreciseLocationSharing(
            nodeNum = nodeNum,
            provide = false,
            channelIndex = previousAdmission.channelIndex,
            channelIdentity = previousAdmission.channelIdentity,
            cleanupPending = false,
        )
        if (nodeRepository.myNodeInfo.value?.myNodeNum != nodeNum) {
            return@mutation ChannelReliabilityResult.SESSION_UNAVAILABLE
        }
        val current = radioConfigRepository.channelSetFlow.first()
        if (
            expectedChannelIdentity.isBlank() ||
            PreciseLocationChannelSetPlanner.channelIdentity(current, channelIndex) != expectedChannelIdentity
        ) {
            return@mutation ChannelReliabilityResult.INVALID_CHANNEL_SET
        }
        val result =
            if (PreciseLocationChannelSetPlanner.matchesPolicy(current, channelIndex, expectedChannelIdentity)) {
                ChannelReliabilityResult.VERIFIED
            } else {
                channelReliabilityManager.applyCurrentAndVerify(
                    expectedNodeNum = nodeNum,
                    mutationLease = mutationLease,
                    requireStableSlots = true,
                    writeOrder = ChannelWriteOrder.PRECISE_POSITION_TARGET_LAST,
                ) { latest ->
                    require(
                        PreciseLocationChannelSetPlanner.channelIdentity(latest, channelIndex) ==
                            expectedChannelIdentity,
                    ) {
                        "Selected precise-location channel changed before admission"
                    }
                    PreciseLocationChannelSetPlanner.plan(latest, channelIndex)
                }
            }
        val policyVerified =
            result == ChannelReliabilityResult.VERIFIED ||
                (
                    result.isReconnectVerificationCandidate() &&
                        awaitReconnectVerification(
                            nodeNum = nodeNum,
                            channelIndex = channelIndex,
                            expectedChannelIdentity = expectedChannelIdentity,
                            sourceReadbackGeneration = sourceReadbackGeneration,
                        )
                    )
        if (!policyVerified) return@mutation result
        enableVerifiedFeed(nodeNum, channelIndex, expectedChannelIdentity)
    }

    /** Called while the original channel mutation lease is still held. */
    private suspend fun enableVerifiedFeed(
        nodeNum: Int,
        channelIndex: Int,
        expectedChannelIdentity: String,
    ): ChannelReliabilityResult = when {
        nodeRepository.myNodeInfo.value?.myNodeNum != nodeNum -> ChannelReliabilityResult.SESSION_UNAVAILABLE

        !PreciseLocationChannelSetPlanner.matchesPolicy(
            radioConfigRepository.channelSetFlow.first(),
            channelIndex,
            expectedChannelIdentity,
        ) -> ChannelReliabilityResult.READBACK_FAILED

        else -> {
            uiPrefs.setPreciseLocationSharing(
                nodeNum = nodeNum,
                provide = true,
                channelIndex = channelIndex,
                channelIdentity = expectedChannelIdentity,
                cleanupPending = false,
            )
            ChannelReliabilityResult.VERIFIED
        }
    }

    private suspend fun awaitReconnectVerification(
        nodeNum: Int,
        channelIndex: Int,
        expectedChannelIdentity: String,
        sourceReadbackGeneration: Long,
    ): Boolean = withTimeoutOrNull(RECONNECT_VERIFICATION_TIMEOUT) {
        combine(
            radioConfigRepository.channelReadbackGeneration,
            radioConfigRepository.channelSetFlow,
            nodeRepository.myNodeInfo,
        ) { generation, channelSet, nodeInfo ->
            generation > sourceReadbackGeneration &&
                nodeInfo?.myNodeNum == nodeNum &&
                PreciseLocationChannelSetPlanner.matchesPolicy(
                    channelSet,
                    channelIndex,
                    expectedChannelIdentity,
                )
        }
            .first { it }
    } ?: false

    /** Revokes the phone feed immediately, then verifies that no radio channel remains position-enabled. */
    open suspend fun disable(nodeNum: Int): ChannelReliabilityResult =
        channelMutationLock.withLock mutation@{ mutationLease ->
            val admission = uiPrefs.readPreciseLocationAdmission(nodeNum)
            uiPrefs.setPreciseLocationSharing(
                nodeNum = nodeNum,
                provide = false,
                channelIndex = admission.channelIndex,
                channelIdentity = admission.channelIdentity,
                cleanupPending = true,
            )
            if (nodeRepository.myNodeInfo.value?.myNodeNum != nodeNum) {
                return@mutation ChannelReliabilityResult.SESSION_UNAVAILABLE
            }
            val result =
                channelReliabilityManager.applyCurrentAndVerify(
                    expectedNodeNum = nodeNum,
                    mutationLease = mutationLease,
                    requireStableSlots = true,
                    writeOrder = ChannelWriteOrder.PRECISE_POSITION_TARGET_LAST,
                    transform = PreciseLocationChannelSetPlanner::disable,
                )
            if (result != ChannelReliabilityResult.VERIFIED) return@mutation result
            if (nodeRepository.myNodeInfo.value?.myNodeNum != nodeNum) {
                return@mutation ChannelReliabilityResult.SESSION_UNAVAILABLE
            }
            uiPrefs.clearPreciseLocationCleanupPending(nodeNum)
            ChannelReliabilityResult.VERIFIED
        }

    private companion object {
        val RECONNECT_VERIFICATION_TIMEOUT = 45.seconds
    }
}

private fun ChannelReliabilityResult.isReconnectVerificationCandidate(): Boolean =
    this == ChannelReliabilityResult.VERIFICATION_PENDING || this == ChannelReliabilityResult.SESSION_UNAVAILABLE
