/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.data.manager

import okio.ByteString
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User

private const val GENERATED_NODE_NAME_SUFFIX_LENGTH = 4

internal fun shouldPreserveExistingUser(existing: User, incoming: User): Boolean {
    val isDefaultName = incoming.long_name.matches(DEFAULT_NODE_NAME_REGEX)
    val isDefaultHwModel = incoming.hw_model == HardwareModel.UNSET
    val hasCustomIdentity =
        existing.id != incoming.id ||
            existing.long_name != incoming.long_name ||
            existing.short_name != incoming.short_name
    val hasExistingUser = existing.id.isNotEmpty() && (existing.hw_model != HardwareModel.UNSET || hasCustomIdentity)
    return hasExistingUser && isDefaultName && isDefaultHwModel
}

/**
 * Classification of one received-user reduction. Returned by [reduceReceivedUser] so callers can emit a single
 * identity-routing log line per committed transition.
 */
internal enum class ReceivedUserDecision {
    LOCAL_UPDATE,
    KNOWN_SAME_NUMBER,
    GENUINE_NEW_NODE,
    RETIRED_NO_KEY_SUPPRESSED,
    RETIRED_WITHOUT_HINT_SUPPRESSED,
    RETIRED_SAME_KEY_SUPPRESSED,
    RETIRED_KEY_ALREADY_REPRESENTED,
    RETIRED_DIFFERENT_KEY_REACTIVATED,
    CANONICAL_DUPLICATE_RECONCILED,
    STALE_PRESENTATION_REMOVED,
    AMBIGUOUS_DUPLICATE_UPDATED,
    IDENTITY_CONFLICT_IGNORED,
}

/**
 * Result of classifying one [User] packet against an in-memory [NodeIndex] snapshot. The reducer is pure: it only reads
 * [before] and the packet fields, and produces the next index plus the side effects to apply once the CAS commits. Safe
 * to re-evaluate on every retry.
 */
internal data class ReceivedUserTransition(
    val after: NodeIndex,
    /** Ordinary same-number upsert to apply after CAS; duplicate correlation never supplies this value. */
    val upsertNode: Node?,
    /** Node to fire a "new node seen" notification for (null if not a genuine new node). */
    val notifyNode: Node?,
    /** Retired number to reactivate after a validated, genuinely new identity claims the vacant slot. */
    val unretireNodeNum: Int? = null,
    val decision: ReceivedUserDecision,
)

/**
 * Pure reducer that classifies an incoming [User] packet against the [before] snapshot and returns the next index plus
 * queued side effects. No logging, no DB calls, no coroutine launches — deterministic and safe to re-run on CAS retry.
 *
 * A validated public key is only an in-memory correlation hint. It can choose/suppress duplicate presentations but
 * cannot authorize repository deletion, metadata removal, DM remapping, or durable node-number migration. Those
 * operations remain exclusively in the trusted config-install DAO path.
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
internal fun reduceReceivedUser(
    before: NodeIndex,
    fromNum: Int,
    p: User,
    channel: Int,
    manuallyVerified: Boolean,
    myNum: Int?,
    retiredAbsent: Boolean,
    allowNotificationAtArrival: Boolean,
    retiredKeyHint: ByteString? = null,
): ReceivedUserTransition {
    val resolvedKey = resolveValidatedPublicKeyHint(p.public_key)
    val existing = before.byNum[fromNum]

    if (retiredAbsent) {
        // Suppress same-key replay even when canonical representative hasn't yet appeared in
        // the in-memory index. The retiredKeyHint was captured from the node as it was retired.
        if (resolvedKey != null && retiredKeyHint != null && resolvedKey == retiredKeyHint) {
            return ReceivedUserTransition(
                after = before,
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.RETIRED_SAME_KEY_SUPPRESSED,
            )
        }
        // A packet with no valid key is suppressed for a retired number.
        if (resolvedKey == null) {
            return ReceivedUserTransition(
                after = before,
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.RETIRED_NO_KEY_SUPPRESSED,
            )
        }
        // A retired slot without a trusted old-key hint has no evidence that a different-looking packet is a
        // legitimate reuse rather than the stale identity. Keep it retired for this process session.
        if (retiredKeyHint == null) {
            return ReceivedUserTransition(
                after = before,
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.RETIRED_WITHOUT_HINT_SUPPRESSED,
            )
        }
        // Key is already represented elsewhere — stale replay, suppress.
        val keyAlreadyRepresented = resolvedKey.let { key ->
            before.candidateNumsByPublicKey[key].orEmpty().isNotEmpty()
        }
        if (keyAlreadyRepresented) {
            return ReceivedUserTransition(
                after = before,
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.RETIRED_KEY_ALREADY_REPRESENTED,
            )
        }
        // Different valid, unrepresented key — legitimate number reuse.
        return sameNumberUserTransition(
            before = before,
            fromNum = fromNum,
            existing = createDefaultNode(fromNum, channel),
            incoming = p,
            channel = channel,
            manuallyVerified = manuallyVerified,
            persist = true,
            allowNotification = allowNotificationAtArrival,
            unretireNodeNum = fromNum,
        )
            .copy(decision = ReceivedUserDecision.RETIRED_DIFFERENT_KEY_REACTIVATED)
    }

    if (fromNum == myNum) {
        // Local-link data may persist an ordinary update at its current number. Duplicate cleanup is still
        // in-memory only; trusted installConfig migration owns durable renumbering and app-local state transfer.
        val otherSameKeyNums =
            resolvedKey
                ?.let { key -> before.candidateNumsByPublicKey[key].orEmpty().filter { it != fromNum } }
                .orEmpty()
        val afterRemovals = otherSameKeyNums.fold(before) { idx, num -> idx.remove(num) }
        val localNode = afterRemovals.byNum[fromNum] ?: createDefaultNode(fromNum, channel)
        val transformed = transformUserNode(localNode, p, channel, manuallyVerified)
        return ReceivedUserTransition(
            after = afterRemovals.put(fromNum, transformed, preferredNum = fromNum),
            upsertNode = transformed,
            notifyNode = null,
            decision = ReceivedUserDecision.LOCAL_UPDATE,
        )
    }

    if (resolvedKey != null) {
        val canonicalNum = resolvedKey.crc32().toInt()
        val existingKey = existing?.let(::nodePublicKeyHint)
        val otherSameKeyNums = before.candidateNumsByPublicKey[resolvedKey].orEmpty().filter { it != fromNum }

        // Fast path: a known same-number presentation needs no candidate reconciliation when it is already at the
        // canonical number or has no duplicate. A canonical duplicate remains in-memory-only because an ordinary
        // DAO upsert could remap the row through same-key verification.
        if (existingKey == resolvedKey && (fromNum == canonicalNum || otherSameKeyNums.isEmpty())) {
            return sameNumberUserTransition(
                before,
                fromNum,
                checkNotNull(existing),
                p,
                channel,
                manuallyVerified,
                persist = otherSameKeyNums.isEmpty(),
                allowNotification = false,
            )
        }

        if (fromNum == canonicalNum && otherSameKeyNums.isNotEmpty()) {
            val slotAcceptsCanonical =
                existing == null || existingKey == resolvedKey || NodeIndex.isDefaultIdentityPlaceholder(existing)
            if (!slotAcceptsCanonical) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.IDENTITY_CONFLICT_IGNORED,
                )
            }

            // Canonical presentation may replace its own default placeholder while preserving all non-user fields.
            // Same-key noncanonical representatives are removed from memory only.
            val afterRemovals = otherSameKeyNums.fold(before) { index, num -> index.remove(num) }
            val baseNode = afterRemovals.byNum[fromNum] ?: createDefaultNode(fromNum, channel)
            val transformed =
                transformUserNode(baseNode, p, channel, manuallyVerified).let { updated ->
                    if (existing != null && NodeIndex.isDefaultIdentityPlaceholder(existing)) {
                        updated.copy(channel = existing.channel)
                    } else {
                        updated
                    }
                }
            return ReceivedUserTransition(
                after = afterRemovals.put(fromNum, transformed, preferredNum = fromNum),
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.CANONICAL_DUPLICATE_RECONCILED,
            )
        }

        if (canonicalNum in otherSameKeyNums) {
            // A stale remote presentation yields only when its own slot already carries the same validated hint.
            // An absent slot, no-key telemetry/position placeholder, or different identity is preserved.
            val after = if (existingKey == resolvedKey) before.remove(fromNum, preferredNum = canonicalNum) else before
            return ReceivedUserTransition(
                after = after,
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.STALE_PRESENTATION_REMOVED,
            )
        }

        if (otherSameKeyNums.isNotEmpty()) {
            // Direction is ambiguous among noncanonical presentations. Accept an empty/default fromNum slot so a
            // legitimate pre-2.8 or fixed-number peer can move between noncanonical numbers without becoming
            // locked out behind a telemetry/position placeholder. Preserve established different-key identities.
            val slotAcceptsAmbiguous =
                existing == null || existingKey == resolvedKey || NodeIndex.isDefaultIdentityPlaceholder(existing)
            if (!slotAcceptsAmbiguous) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.IDENTITY_CONFLICT_IGNORED,
                )
            }

            // Public keys are broadcast, unauthenticated correlation hints. Installing the presentation here is
            // deliberately in-memory-only: it must never authorize persistence, migration, or deletion. Trusted
            // config installation remains the sole authority for durable identity changes.
            val baseNode = existing ?: createDefaultNode(fromNum, channel)
            val transformed = transformUserNode(baseNode, p, channel, manuallyVerified)
            return ReceivedUserTransition(
                after = before.put(fromNum, transformed, preferredNum = fromNum),
                upsertNode = null,
                notifyNode = null,
                decision = ReceivedUserDecision.AMBIGUOUS_DUPLICATE_UPDATED,
            )
        }
    }

    return sameNumberUserTransition(
        before,
        fromNum,
        existing ?: createDefaultNode(fromNum, channel),
        p,
        channel,
        manuallyVerified,
        persist = true,
        allowNotification = allowNotificationAtArrival,
    )
}

internal fun sameNumberUserTransition(
    before: NodeIndex,
    fromNum: Int,
    existing: Node,
    incoming: User,
    channel: Int,
    manuallyVerified: Boolean,
    persist: Boolean,
    allowNotification: Boolean,
    unretireNodeNum: Int? = null,
): ReceivedUserTransition {
    val isNewNode = existing.isUnknownUser && incoming.hw_model != HardwareModel.UNSET
    val shouldPreserve = shouldPreserveExistingUser(existing.user, incoming)
    val transformed = transformUserNode(existing, incoming, channel, manuallyVerified)
    val notify = if (allowNotification && isNewNode && !shouldPreserve) transformed else null
    val decision =
        when {
            allowNotification && isNewNode && !shouldPreserve -> ReceivedUserDecision.GENUINE_NEW_NODE
            else -> ReceivedUserDecision.KNOWN_SAME_NUMBER
        }
    return ReceivedUserTransition(
        after = before.put(fromNum, transformed),
        upsertNode = transformed.takeIf { persist },
        notifyNode = notify,
        unretireNodeNum = unretireNodeNum,
        decision = decision,
    )
}

/** Pure factory for a placeholder [Node] at [num]. Kept side-effect-free so the reducer can call it safely. */
internal fun createDefaultNode(num: Int, channel: Int): Node {
    val userId = NodeAddress.numToDefaultId(num)
    return Node(
        num = num,
        user =
        User.Builder()
            .also { wb ->
                wb.id = userId
                wb.long_name = "Meshtastic ${userId.takeLast(GENERATED_NODE_NAME_SUFFIX_LENGTH)}"
                wb.short_name = userId.takeLast(GENERATED_NODE_NAME_SUFFIX_LENGTH)
                wb.hw_model = HardwareModel.UNSET
            }
            .build(),
        channel = channel,
    )
}

/** Pure transform of an existing [node] under an incoming [User] packet (extracted from the legacy updateNode path). */
internal fun transformUserNode(node: Node, p: User, channel: Int, manuallyVerified: Boolean): Node {
    val shouldPreserve = shouldPreserveExistingUser(node.user, p)
    // A mesh NodeInfo carries no verification, so it never clears one an import set.
    val verified = node.manuallyVerified || manuallyVerified
    return if (shouldPreserve) {
        node.copy(channel = channel, manuallyVerified = verified)
    } else {
        val incomingKey = resolveValidatedPublicKeyHint(p.public_key)
        // Prefer node.publicKey when valid (the authoritative stored key); fall back to node.user.public_key.
        val existingKey = nodePublicKeyHint(node)
        // Only two valid, different keys are a mismatch. A packet with no usable key says nothing about the one
        // on file, so it neither flags nor clears anything, and the stored key stays.
        val keyMismatch = existingKey != null && incomingKey != null && existingKey != incomingKey
        // First-wins, matching the DAO and the firmware: a different key for a node we already hold one for is
        // refused and recorded, never applied. Clearing the stored key here would break PKC direct messages to
        // that contact on the word of whoever sent the substitute.
        val keptKey = if (incomingKey == null || keyMismatch) existingKey else incomingKey
        val newUser = p.newBuilder().also { wb -> wb.public_key = keptKey ?: ByteString.EMPTY }.build()
        node.copy(
            user = newUser,
            publicKey = newUser.public_key,
            keyMatch = node.keyMatch && !keyMismatch,
            newPublicKey = if (keyMismatch) incomingKey else node.newPublicKey,
            channel = channel,
            manuallyVerified = verified,
        )
    }
}
